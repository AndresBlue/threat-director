package com.andresblue.threatdirector.bloodmoon;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.PersistentState;

/** Save-backed calendar data. Runtime-only caches deliberately live elsewhere. */
final class BloodMoonPersistentState extends PersistentState {
    static final String STORAGE_KEY = "threatdirector_blood_moon";

    private boolean initialized;
    private long worldSeed;
    private long nextNight;
    private long announcedNight = -1L;
    private long eventOrdinal;
    private int messageIndex;

    BloodMoonPersistentState() {
    }

    static BloodMoonPersistentState fromNbt(NbtCompound nbt) {
        BloodMoonPersistentState state = new BloodMoonPersistentState();
        state.initialized = nbt.getBoolean("Initialized");
        state.worldSeed = nbt.getLong("WorldSeed");
        state.nextNight = nbt.getLong("NextNight");
        state.announcedNight = nbt.contains("AnnouncedNight")
                ? nbt.getLong("AnnouncedNight")
                : -1L;
        state.eventOrdinal = nbt.getLong("EventOrdinal");
        state.messageIndex = nbt.getInt("MessageIndex");
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putBoolean("Initialized", initialized);
        nbt.putLong("WorldSeed", worldSeed);
        nbt.putLong("NextNight", nextNight);
        nbt.putLong("AnnouncedNight", announcedNight);
        nbt.putLong("EventOrdinal", eventOrdinal);
        nbt.putInt("MessageIndex", messageIndex);
        return nbt;
    }

    void initialize(long seed, long currentDay, int messageCount,
                    int minimumInterval, int maximumInterval) {
        if (initialized) {
            // Config reloads may shrink the omen list. Re-select deterministically instead of
            // indexing stale save data and crashing the world tick.
            if (messageIndex < 0 || messageIndex >= messageCount) {
                messageIndex = BloodMoonSchedule.messageIndex(worldSeed, eventOrdinal, messageCount);
                markDirty();
            }
            return;
        }
        initialized = true;
        worldSeed = seed;
        eventOrdinal = 0L;
        nextNight = currentDay + BloodMoonSchedule.interval(
                worldSeed, eventOrdinal, minimumInterval, maximumInterval);
        messageIndex = BloodMoonSchedule.messageIndex(worldSeed, eventOrdinal, messageCount);
        announcedNight = -1L;
        markDirty();
    }

    void initialize(long seed, long currentDay, int messageCount) {
        initialize(seed, currentDay, messageCount,
                BloodMoonSchedule.MINIMUM_INTERVAL_NIGHTS,
                BloodMoonSchedule.MAXIMUM_INTERVAL_NIGHTS);
    }

    /**
     * Advances missed events without changing the already-selected event when a
     * server restarts during its Blood Moon.
     */
    void advancePast(long currentDay, int messageCount,
                     int minimumInterval, int maximumInterval) {
        boolean changed = false;
        while (nextNight < currentDay) {
            eventOrdinal++;
            nextNight += BloodMoonSchedule.interval(
                    worldSeed, eventOrdinal, minimumInterval, maximumInterval);
            messageIndex = BloodMoonSchedule.messageIndex(worldSeed, eventOrdinal, messageCount);
            announcedNight = -1L;
            changed = true;
        }
        if (changed) {
            markDirty();
        }
    }

    void advancePast(long currentDay, int messageCount) {
        advancePast(currentDay, messageCount,
                BloodMoonSchedule.MINIMUM_INTERVAL_NIGHTS,
                BloodMoonSchedule.MAXIMUM_INTERVAL_NIGHTS);
    }

    long nextNight() {
        return nextNight;
    }

    int messageIndex() {
        return messageIndex;
    }

    boolean wasAnnounced() {
        return announcedNight == nextNight;
    }

    void markAnnounced() {
        if (!wasAnnounced()) {
            announcedNight = nextNight;
            markDirty();
        }
    }
}
