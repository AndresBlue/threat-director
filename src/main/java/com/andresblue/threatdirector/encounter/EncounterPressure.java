package com.andresblue.threatdirector.encounter;

import com.andresblue.threatdirector.config.ThreatDirectorConfig;
import com.andresblue.threatdirector.server.PlayerDirectorState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;

import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Immutable, server-authoritative pressure policy for one player context.
 *
 * <p>The normalized {@code hazard} value is the common input for encounter frequency, population,
 * tactical cadence and recovery. Minecraft difficulty is strictly ordered for an otherwise equal
 * context ({@code EASY < NORMAL < HARD}); night, darkness and cave exposure add bounded pressure.
 */
public record EncounterPressure(
        double hazard,
        String zoneLabel,
        double effectiveIntensity,
        double encounterChance,
        int mobCap,
        int minimumMobs,
        int meleeAttackers,
        int replanIntervalTicks,
        int reinforcementSize,
        int maximumReinforcementWaves,
        int reinforcementIntervalTicks,
        double cooldownMultiplier) {
    private static final double NIGHT_HAZARD = 0.08;
    private static final double DARKNESS_HAZARD = 0.10;
    private static final double CAVE_HAZARD = 0.12;
    private static final double INTENSITY_CONTEXT_SPAN = 20.0;

    public EncounterPressure {
        if (!Double.isFinite(hazard) || hazard < 0.0 || hazard > 1.0) {
            throw new IllegalArgumentException("hazard must be finite and between 0 and 1");
        }
        Objects.requireNonNull(zoneLabel, "zoneLabel");
        if (zoneLabel.isBlank()) {
            throw new IllegalArgumentException("zoneLabel must not be blank");
        }
        if (!Double.isFinite(effectiveIntensity)
                || effectiveIntensity < 0.0
                || effectiveIntensity > 100.0) {
            throw new IllegalArgumentException(
                    "effectiveIntensity must be finite and between 0 and 100");
        }
        if (!Double.isFinite(encounterChance)
                || encounterChance < 0.0
                || encounterChance > 1.0) {
            throw new IllegalArgumentException(
                    "encounterChance must be finite and between 0 and 1");
        }
        if (mobCap < 0 || minimumMobs < 0 || minimumMobs > mobCap) {
            throw new IllegalArgumentException("minimumMobs must be between 0 and mobCap");
        }
        if (meleeAttackers < 0 || meleeAttackers > mobCap) {
            throw new IllegalArgumentException("meleeAttackers must be between 0 and mobCap");
        }
        if (replanIntervalTicks <= 0) {
            throw new IllegalArgumentException("replanIntervalTicks must be positive");
        }
        if (reinforcementSize < 0 || reinforcementSize > mobCap) {
            throw new IllegalArgumentException("reinforcementSize must be between 0 and mobCap");
        }
        if (maximumReinforcementWaves < 0
                || (reinforcementSize == 0 && maximumReinforcementWaves != 0)
                || (reinforcementSize > 0 && maximumReinforcementWaves == 0)) {
            throw new IllegalArgumentException(
                    "maximumReinforcementWaves must be positive exactly when waves are enabled");
        }
        if ((reinforcementSize == 0 && reinforcementIntervalTicks != 0)
                || (reinforcementSize > 0 && reinforcementIntervalTicks <= 0)) {
            throw new IllegalArgumentException(
                    "reinforcementIntervalTicks must be zero exactly when reinforcements are disabled");
        }
        if (!Double.isFinite(cooldownMultiplier) || cooldownMultiplier <= 0.0) {
            throw new IllegalArgumentException("cooldownMultiplier must be finite and positive");
        }
    }

    /** Pure policy entrypoint, independent of live Minecraft entities. */
    public static EncounterPressure calculate(
            ThreatDirectorConfig config,
            double intensity,
            boolean intensityOverride,
            Context context) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(context, "context");
        ThreatDirectorConfig.DirectorSection director =
                Objects.requireNonNull(config.director(), "config.director");
        ThreatDirectorConfig.IntensitySection intensityConfig =
                Objects.requireNonNull(config.intensity(), "config.intensity");
        ThreatDirectorConfig.EncounterSection encounter =
                Objects.requireNonNull(config.encounter(), "config.encounter");
        ThreatDirectorConfig.TacticalSection tactical =
                Objects.requireNonNull(config.tactical(), "config.tactical");

        if (!Double.isFinite(intensity) || intensity < 0.0 || intensity > 100.0) {
            throw new IllegalArgumentException("intensity must be finite and between 0 and 100");
        }

        double hazard = computeHazard(context);
        boolean peaceful = context.difficulty() == Difficulty.PEACEFUL;
        double effectiveIntensity = intensityOverride
                ? intensity
                : adaptiveIntensity(intensity, intensityConfig.maxIntensity(), hazard);
        double encounterChance = peaceful
                ? 0.0
                : encounterChance(director.encounterChancePerScan(), context);

        int hardMax = Math.max(0, encounter.hardMaxMobs());
        int normalCap = clamp(encounter.maxMobs(), 0, hardMax);
        int configuredMinimum = clamp(encounter.minMobs(), 0, normalCap);
        int mobCap = peaceful
                ? 0
                : apocalypseMobCap(configuredMinimum, normalCap, hardMax, context);
        int minimumMobs = peaceful
                ? 0
                : apocalypseMinimum(configuredMinimum, mobCap, context.difficulty());

        int configuredMelee = Math.max(0, tactical.maxSimultaneousMeleeAttackers());
        int meleeAttackers = peaceful
                ? 0
                : apocalypseMelee(configuredMelee, mobCap, context.difficulty());
        int replanIntervalTicks = Math.max(1, (int) Math.round(
                Math.max(1, tactical.replanIntervalTicks()) * cadenceMultiplier(hazard)));

        int desiredReinforcements = peaceful
                ? 0
                : reinforcementSizeFor(mobCap, context.difficulty());
        int reinforcementSize = Math.min(
                desiredReinforcements,
                Math.max(0, mobCap - minimumMobs));
        int maximumReinforcementWaves = reinforcementSize == 0
                ? 0
                : maximumReinforcementWaves(context.difficulty());
        int reinforcementIntervalTicks = reinforcementSize == 0
                ? 0
                : Math.max(
                        replanIntervalTicks,
                        (int) Math.round(200.0 * clamp(1.25 - hazard, 0.40, 1.0)));

        return new EncounterPressure(
                hazard,
                zoneLabel(context),
                effectiveIntensity,
                encounterChance,
                mobCap,
                minimumMobs,
                meleeAttackers,
                replanIntervalTicks,
                reinforcementSize,
                maximumReinforcementWaves,
                reinforcementIntervalTicks,
                cooldownMultiplier(context));
    }

    /** Uses the effective value and explicit-override state already maintained for a player. */
    public static EncounterPressure calculate(
            ThreatDirectorConfig config,
            PlayerDirectorState state,
            Context context) {
        Objects.requireNonNull(state, "state");
        return calculate(config, state.intensity(), state.hasIntensityOverride(), context);
    }

    /** Convenience overload that captures the authoritative context from a server player. */
    public static EncounterPressure calculate(
            ThreatDirectorConfig config,
            PlayerDirectorState state,
            ServerPlayerEntity player) {
        Objects.requireNonNull(player, "player");
        return calculate(config, state, Context.from(player));
    }

    private static double computeHazard(Context context) {
        if (context.difficulty() == Difficulty.PEACEFUL) {
            return 0.0;
        }
        double difficultyHazard = switch (context.difficulty()) {
            case EASY -> 0.25;
            case NORMAL -> 0.50;
            case HARD -> 0.75;
            case PEACEFUL -> 0.0;
        };
        double environmentalHazard = (context.night() ? NIGHT_HAZARD : 0.0)
                + (context.darkness() ? DARKNESS_HAZARD : 0.0)
                + (context.cave() ? CAVE_HAZARD : 0.0);
        return clamp(difficultyHazard + environmentalHazard, 0.0, 1.0);
    }

    private static double adaptiveIntensity(
            double intensity, double configuredMaximum, double hazard) {
        double maximum = Double.isFinite(configuredMaximum)
                ? clamp(configuredMaximum, 0.0, 100.0)
                : 100.0;
        double contextual = intensity + (hazard - 0.50) * INTENSITY_CONTEXT_SPAN;
        return clamp(contextual, 0.0, maximum);
    }

    private static double encounterChance(double configuredChance, Context context) {
        double environmental = environmentalFraction(context);
        double multiplier = switch (context.difficulty()) {
            case PEACEFUL -> 0.0;
            // Easy is the vanilla-compatible tier. Administrators can still force a scene,
            // but the director never starts an automatic horde on this difficulty.
            case EASY -> 0.0;
            case NORMAL -> 1.50 + environmental * 2.0;
            case HARD -> 5.0 + environmental * 5.0;
        };
        return clamp(configuredChance * multiplier, 0.0, 1.0);
    }

    private static int apocalypseMobCap(
            int minimum, int normalCap, int hardMax, Context context) {
        double environmental = environmentalFraction(context);
        int easyCap = clamp(Math.max(minimum, Math.min(6, normalCap)), minimum, hardMax);
        return switch (context.difficulty()) {
            case PEACEFUL -> 0;
            case EASY -> easyCap;
            case NORMAL -> clamp(
                    (int) Math.round(normalCap + (hardMax - normalCap) * 0.35 * environmental),
                    minimum,
                    hardMax);
            case HARD -> clamp(
                    (int) Math.round(normalCap
                            + (hardMax - normalCap) * (0.50 + 0.50 * environmental)),
                    minimum,
                    hardMax);
        };
    }

    private static int apocalypseMinimum(int configuredMinimum, int mobCap, Difficulty difficulty) {
        return Math.min(mobCap, switch (difficulty) {
            case PEACEFUL -> 0;
            case EASY -> configuredMinimum;
            case NORMAL -> Math.max(configuredMinimum, (int) Math.ceil(mobCap * 0.33));
            case HARD -> Math.max(configuredMinimum, (int) Math.ceil(mobCap * 0.35));
        });
    }

    private static int apocalypseMelee(int configuredMelee, int mobCap, Difficulty difficulty) {
        return Math.min(mobCap, switch (difficulty) {
            case PEACEFUL -> 0;
            case EASY -> Math.min(2, configuredMelee);
            case NORMAL -> Math.max(configuredMelee, (int) Math.ceil(mobCap * 0.25));
            case HARD -> Math.max(configuredMelee, (int) Math.ceil(mobCap * 0.35));
        });
    }

    private static int reinforcementSizeFor(int mobCap, Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL, EASY -> 0;
            case NORMAL -> Math.max(4, (int) Math.ceil(mobCap * 0.12));
            case HARD -> Math.max(12, (int) Math.ceil(mobCap * 0.20));
        };
    }

    private static int maximumReinforcementWaves(Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL, EASY -> 0;
            case NORMAL -> 6;
            case HARD -> 10;
        };
    }

    private static double cadenceMultiplier(double hazard) {
        return clamp(1.50 - hazard, 0.50, 1.50);
    }

    private static double cooldownMultiplier(Context context) {
        double environmental = environmentalFraction(context);
        return switch (context.difficulty()) {
            case PEACEFUL -> 1.0;
            case EASY -> 1.50;
            case NORMAL -> clamp(1.0 - environmental * 0.40, 0.60, 1.0);
            case HARD -> clamp(0.35 - environmental * 0.20, 0.15, 0.35);
        };
    }

    private static double environmentalFraction(Context context) {
        double environmental = (context.night() ? NIGHT_HAZARD : 0.0)
                + (context.darkness() ? DARKNESS_HAZARD : 0.0)
                + (context.cave() ? CAVE_HAZARD : 0.0);
        return clamp(environmental / (NIGHT_HAZARD + DARKNESS_HAZARD + CAVE_HAZARD), 0.0, 1.0);
    }

    private static String zoneLabel(Context context) {
        StringJoiner zones = new StringJoiner("+");
        if (context.night()) {
            zones.add("night");
        }
        if (context.darkness()) {
            zones.add("dark");
        }
        if (context.cave()) {
            zones.add("cave");
        }
        String zone = zones.length() == 0 ? "surface" : zones.toString();
        return context.difficulty().name().toLowerCase(Locale.ROOT) + ":" + zone;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** Minimal immutable context, suitable for deterministic unit tests and telemetry. */
    public record Context(Difficulty difficulty, boolean night, boolean darkness, boolean cave) {
        public Context {
            Objects.requireNonNull(difficulty, "difficulty");
        }

        public static Context from(ServerPlayerEntity player) {
            Objects.requireNonNull(player, "player");
            ServerWorld world = player.getServerWorld();
            BlockPos position = player.getBlockPos();
            boolean darkness = world.getLightLevel(position) <= 7;
            boolean cave = !world.isSkyVisible(position) && position.getY() < world.getSeaLevel();
            return new Context(world.getDifficulty(), world.isNight(), darkness, cave);
        }
    }
}
