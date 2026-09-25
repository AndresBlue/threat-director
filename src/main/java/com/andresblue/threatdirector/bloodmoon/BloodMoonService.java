package com.andresblue.threatdirector.bloodmoon;

import com.andresblue.threatdirector.ThreatDirectorMod;
import com.andresblue.threatdirector.config.ConfigAccess;
import com.andresblue.threatdirector.localization.LocalizedText;
import com.andresblue.threatdirector.server.ModdedClientAccess;
import com.andresblue.threatdirector.telemetry.GameplayDebugEvents;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

/** Server-authoritative Blood Moon calendar and public query surface. */
public final class BloodMoonService {
    private static final Map<MinecraftServer, Long> STARTED_NIGHTS = new WeakHashMap<>();

    private BloodMoonService() {
    }

    /**
     * Returns the global Overworld Blood Moon state from any server-world context.
     * The query survives restarts because only save-backed calendar data is used.
     */
    public static boolean isActive(ServerWorld context) {
        if (context == null || !isFeatureEnabled()) {
            return false;
        }
        ServerWorld overworld = context.getServer().getOverworld();
        if (overworld == null) {
            return false;
        }
        BloodMoonPersistentState state = synchronizedState(overworld);
        return BloodMoonSchedule.isActive(
                state.nextNight(), overworld.getTimeOfDay(), settings().nightStartTick());
    }

    public static BloodMoonStatus status(ServerWorld context) {
        if (context == null) {
            return BloodMoonStatus.unavailable();
        }
        ServerWorld overworld = context.getServer().getOverworld();
        if (overworld == null) {
            return BloodMoonStatus.unavailable();
        }
        BloodMoonPersistentState state = synchronizedState(overworld);
        long time = overworld.getTimeOfDay();
        long currentDay = BloodMoonSchedule.dayIndex(time);
        long startTick = state.nextNight() * BloodMoonSchedule.TICKS_PER_DAY
                + settings().nightStartTick();
        return new BloodMoonStatus(
                true,
                isFeatureEnabled()
                        && BloodMoonSchedule.isActive(
                        state.nextNight(), time, settings().nightStartTick()),
                state.nextNight(),
                currentDay,
                state.wasAnnounced(),
                Math.max(0L, startTick - time)
        );
    }

    /** Called by the Fabric world-tick hook; only the Overworld performs calendar work. */
    static void tick(ServerWorld world) {
        if (!World.OVERWORLD.equals(world.getRegistryKey()) || !isFeatureEnabled()) {
            return;
        }
        BloodMoonPersistentState state = synchronizedState(world);
        long time = world.getTimeOfDay();
        long currentDay = BloodMoonSchedule.dayIndex(time);
        boolean active = BloodMoonSchedule.isActive(
                state.nextNight(), time, settings().nightStartTick());
        Long startedNight = STARTED_NIGHTS.get(world.getServer());
        if (active && !Long.valueOf(state.nextNight()).equals(startedNight)) {
            STARTED_NIGHTS.put(world.getServer(), state.nextNight());
            GameplayDebugEvents.record("blood_moon.started", Map.of(
                    "night", state.nextNight(),
                    "day", currentDay));
            ThreatDirectorMod.LOGGER.info("Blood Moon started on night {}", state.nextNight());
        } else if (!active && startedNight != null && currentDay > startedNight) {
            STARTED_NIGHTS.remove(world.getServer());
            GameplayDebugEvents.record("blood_moon.ended", Map.of(
                    "night", startedNight,
                    "day", currentDay));
        }
        if (settings().announceOmens()
                && !state.wasAnnounced()
                && BloodMoonSchedule.isAnnouncementWindow(
                state.nextNight(), time, settings().nightStartTick())
                && !world.getServer().getPlayerManager().getPlayerList().isEmpty()) {
            announce(world.getServer(), settings().omenMessages().get(state.messageIndex()));
            state.markAnnounced();
            GameplayDebugEvents.record("blood_moon.omen", Map.of(
                    "night", state.nextNight(),
                    "message", state.messageIndex()));
            ThreatDirectorMod.LOGGER.info(
                    "Blood Moon omen announced for night {} (message={})",
                    state.nextNight(), state.messageIndex());
        }
    }

    static int omenCount() {
        return settings().omenMessages().size();
    }

    static void clearSession(MinecraftServer server) {
        STARTED_NIGHTS.remove(server);
    }

    private static BloodMoonPersistentState synchronizedState(ServerWorld overworld) {
        BloodMoonPersistentState state = overworld.getPersistentStateManager().getOrCreate(
                BloodMoonPersistentState::fromNbt,
                BloodMoonPersistentState::new,
                BloodMoonPersistentState.STORAGE_KEY
        );
        long currentDay = BloodMoonSchedule.dayIndex(overworld.getTimeOfDay());
        var config = settings();
        state.initialize(overworld.getSeed(), currentDay, config.omenMessages().size(),
                config.minimumIntervalNights(), config.maximumIntervalNights());
        state.advancePast(currentDay, config.omenMessages().size(),
                config.minimumIntervalNights(), config.maximumIntervalNights());
        return state;
    }

    private static boolean isFeatureEnabled() {
        return ConfigAccess.current().moddedEnabled()
                && ConfigAccess.current().modded().bloodMoon().enabled();
    }

    private static com.andresblue.threatdirector.config.ThreatDirectorConfig.BloodMoonSection settings() {
        return ConfigAccess.current().modded().bloodMoon();
    }

    private static void announce(MinecraftServer server, String omen) {
        Text message = localizedOmen(omen).copy()
                .formatted(Formatting.DARK_RED, Formatting.ITALIC);
        server.getPlayerManager().getPlayerList().stream()
                .filter(ModdedClientAccess::hasMod)
                .forEach(player -> player.sendMessage(message, false));
    }

    /** Keeps configured custom messages literal while localizing every built-in omen per client. */
    private static Text localizedOmen(String omen) {
        String key = switch (omen) {
            case "The air tastes of iron, though no blade has been drawn." ->
                    "message.threatdirector.blood_moon.omen.1";
            case "The animals stare toward the horizon and refuse to make a sound." ->
                    "message.threatdirector.blood_moon.omen.2";
            case "Today, the shadows seem to arrive before the things that cast them." ->
                    "message.threatdirector.blood_moon.omen.3";
            case "Beneath the earth, something has begun to count the hours." ->
                    "message.threatdirector.blood_moon.omen.4";
            case "Dusk carries a pulse that does not belong to the sun." ->
                    "message.threatdirector.blood_moon.omen.5";
            case "The waters are restless, as if the moon were pulling from below." ->
                    "message.threatdirector.blood_moon.omen.6";
            default -> null;
        };
        return key == null ? Text.literal(omen) : LocalizedText.of(key);
    }

    public record BloodMoonStatus(
            boolean available,
            boolean active,
            long scheduledNight,
            long currentDay,
            boolean announced,
            long ticksUntilStart
    ) {
        static BloodMoonStatus unavailable() {
            return new BloodMoonStatus(false, false, -1L, -1L, false, Long.MAX_VALUE);
        }
    }
}
