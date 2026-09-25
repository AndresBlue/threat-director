package com.andresblue.threatdirector.difficulty;

/**
 * Pure tuning policy for the vanilla-mob abilities. Minecraft-facing code only
 * adapts the current world difficulty to one of these immutable profiles.
 */
public final class DifficultyAbilityPolicy {
    private static final AbilityProfile EASY = new AbilityProfile(
            Tier.EASY,
            new ZombieRules(0.0, 0, false, 0.0, 0.0, 0.0,
                    0.0, 0, 0, 0, 0),
            new SpiderRules(-1, -1, -1, -1, false, 0, 0, 0.0),
            new EndermanRules(0.0, 0, 0.0, false, 0, 0)
    );
    private static final AbilityProfile NORMAL = new AbilityProfile(
            Tier.NORMAL,
            new ZombieRules(0.40, 3, false, 0.0, 0.0, 0.0,
                    0.0, 0, 0, 0, 0),
            new SpiderRules(0, 0, -1, -1, false, 0, 0, 0.0),
            new EndermanRules(12.0, 2, 0.50, false, 0, 0)
    );
    private static final AbilityProfile HARD = new AbilityProfile(
            Tier.HARD,
            // Pickaxes and shields are specialist rolls, rather than equally common tools.
            new ZombieRules(0.65, 4, true, 0.06, 0.10, 0.42,
                    0.10, 1, 5, 300, 50),
            new SpiderRules(0, 0, -1, -1, true, 600, 1, 0.72),
            new EndermanRules(18.0, 3, 0.65, true, 45, 120)
    );
    /** The intentionally brutal pre-2.0 profile, reserved for Blood Moons. */
    private static final AbilityProfile BLOOD_MOON_HARD = new AbilityProfile(
            Tier.HARD,
            new ZombieRules(0.85, 4, true, 0.10, 0.16, 0.55,
                    0.20, 2, 8, 200, 30),
            // A web is a rare punctuation mark, not a permanent stun loop.
            new SpiderRules(1, 1, 0, 0, true, 400, 2, 0.85),
            new EndermanRules(24.0, 6, 1.0, true, 30, 80)
    );

    private DifficultyAbilityPolicy() {
    }

    public static AbilityProfile profile(Tier tier) {
        return profile(tier, false);
    }

    public static AbilityProfile profile(Tier tier, boolean bloodMoon) {
        return switch (tier) {
            case EASY -> EASY;
            case NORMAL -> NORMAL;
            case HARD -> bloodMoon ? BLOOD_MOON_HARD : HARD;
        };
    }

    /** Maps Minecraft's stable difficulty ids: peaceful=0, easy=1, normal=2, hard=3. */
    public static Tier hostileTierFromDifficultyId(int difficultyId) {
        if (difficultyId >= 3) {
            return Tier.HARD;
        }
        if (difficultyId == 2) {
            return Tier.NORMAL;
        }
        return Tier.EASY;
    }

    public static boolean acceptsRoll(double unitSample, double chance) {
        if (!Double.isFinite(unitSample) || unitSample < 0.0 || unitSample >= 1.0) {
            throw new IllegalArgumentException("unitSample must be in [0, 1)");
        }
        return unitSample < chance;
    }

    public static int boundedSpawnCount(int requested, int maximum) {
        return Math.max(0, Math.min(requested, Math.max(0, maximum)));
    }

    /** Pure geometric part of the anti-pillar detector. */
    public static boolean hasPillarAdvantage(double horizontalSquared, double verticalDelta,
                                             boolean targetOnGround) {
        return targetOnGround && horizontalSquared <= 16.0 && verticalDelta >= 2.25;
    }

    public enum Tier {
        EASY,
        NORMAL,
        HARD
    }

    public record AbilityProfile(Tier tier, ZombieRules zombie, SpiderRules spider,
                                 EndermanRules enderman) {
    }

    public record ZombieRules(double equipmentChance, int meleeToolChoices, boolean shieldAndPickaxe,
                              double pickaxeChance, double shieldChance, double shieldBlockChance,
                              double reinforcementChance, int maxReinforcements,
                              int nearbyZombieCap, int reinforcementCooldownTicks,
                              int blockBreakCooldownTicks) {
    }

    /** A negative amplifier means that the corresponding effect is disabled. */
    public record SpiderRules(int speedAmplifier, int jumpAmplifier, int regenerationAmplifier,
                              int strengthAmplifier, boolean cobwebOnHit,
                              int cobwebCooldownTicks, int hatchlingCount,
                              double hatchlingSpawnChance) {
    }

    public record EndermanRules(double allyAlertRadius, int maxAlliesAlerted, double allyAlertChance,
                                boolean twoBlockCombatPosture, int pillarTriggerTicks,
                                int pillarTeleportCooldownTicks) {
    }
}
