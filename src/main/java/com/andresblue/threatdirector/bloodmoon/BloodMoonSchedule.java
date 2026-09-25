package com.andresblue.threatdirector.bloodmoon;

/**
 * Pure calendar policy for Blood Moons. Keeping calendar arithmetic here makes
 * time commands, save reloads and long-running worlds straightforward to test.
 */
public final class BloodMoonSchedule {
    public static final long TICKS_PER_DAY = 24_000L;
    public static final long NIGHT_START_TICK = 12_000L;
    public static final int MINIMUM_INTERVAL_NIGHTS = 4;
    public static final int MAXIMUM_INTERVAL_NIGHTS = 6;

    private static final long INTERVAL_SALT = 0x6A09E667F3BCC909L;
    private static final long MESSAGE_SALT = 0xBB67AE8584CAA73BL;

    private BloodMoonSchedule() {
    }

    public static long dayIndex(long timeOfDay) {
        return Math.floorDiv(timeOfDay, TICKS_PER_DAY);
    }

    public static long tickWithinDay(long timeOfDay) {
        return Math.floorMod(timeOfDay, TICKS_PER_DAY);
    }

    public static boolean isActive(long scheduledNight, long timeOfDay) {
        return isActive(scheduledNight, timeOfDay, NIGHT_START_TICK);
    }

    public static boolean isActive(long scheduledNight, long timeOfDay, long nightStartTick) {
        return dayIndex(timeOfDay) == scheduledNight
                && tickWithinDay(timeOfDay) >= nightStartTick;
    }

    public static boolean isAnnouncementWindow(long scheduledNight, long timeOfDay) {
        return isAnnouncementWindow(scheduledNight, timeOfDay, NIGHT_START_TICK);
    }

    public static boolean isAnnouncementWindow(long scheduledNight, long timeOfDay,
                                                long nightStartTick) {
        return dayIndex(timeOfDay) == scheduledNight
                && tickWithinDay(timeOfDay) < nightStartTick;
    }

    /** Produces a deterministic 4..6-night interval from the world seed and event ordinal. */
    public static int interval(long worldSeed, long eventOrdinal) {
        return interval(worldSeed, eventOrdinal, MINIMUM_INTERVAL_NIGHTS, MAXIMUM_INTERVAL_NIGHTS);
    }

    public static int interval(long worldSeed, long eventOrdinal,
                               int minimumNights, int maximumNights) {
        long mixed = mix64(worldSeed + INTERVAL_SALT * (eventOrdinal + 1L));
        int minimum = Math.max(1, minimumNights);
        int maximum = Math.max(minimum, maximumNights);
        return minimum + (int) Math.floorMod(mixed, (long) maximum - minimum + 1L);
    }

    public static int messageIndex(long worldSeed, long eventOrdinal, int messageCount) {
        if (messageCount <= 0) {
            throw new IllegalArgumentException("messageCount must be positive");
        }
        long mixed = mix64(worldSeed ^ MESSAGE_SALT * (eventOrdinal + 1L));
        return (int) Math.floorMod(mixed, messageCount);
    }

    static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
