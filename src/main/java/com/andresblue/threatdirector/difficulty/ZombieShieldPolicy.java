package com.andresblue.threatdirector.difficulty;

/** Pure combat policy for the player-like shields carried by some zombies. */
public final class ZombieShieldPolicy {
    public static final int AXE_DISABLE_TICKS = 20 * 10;

    private ZombieShieldPolicy() {
    }

    public static boolean isAttackInFront(
            double facingX,
            double facingZ,
            double sourceOffsetX,
            double sourceOffsetZ
    ) {
        double facingLength = Math.hypot(facingX, facingZ);
        double sourceLength = Math.hypot(sourceOffsetX, sourceOffsetZ);
        if (facingLength < 1.0E-6 || sourceLength < 1.0E-6) {
            return false;
        }
        return (facingX * sourceOffsetX + facingZ * sourceOffsetZ)
                / (facingLength * sourceLength) >= 0.0;
    }

    public static boolean shouldBlock(
            boolean hasShield,
            boolean shieldDisabled,
            boolean bypassesShield,
            boolean attackInFront,
            boolean axeMelee,
            double chance,
            double unitSample
    ) {
        return hasShield
                && !shieldDisabled
                && !bypassesShield
                && attackInFront
                && !axeMelee
                && DifficultyAbilityPolicy.acceptsRoll(unitSample, chance);
    }
}
