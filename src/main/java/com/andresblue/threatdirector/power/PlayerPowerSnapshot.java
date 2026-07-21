package com.andresblue.threatdirector.power;

/**
 * Loader-independent, immutable view of the combat-relevant player state.
 * Values use vanilla units (health points, blocks, attacks/second).
 */
public record PlayerPowerSnapshot(
        double armor,
        double armorToughness,
        double baseAttackDamage,
        double attackSpeed,
        double projectileDamage,
        double projectileChargeSeconds,
        double effectiveRange,
        int projectileCount,
        boolean infiniteProjectiles,
        boolean projectileLoaded,
        int genericProtectionEpf,
        double expectedEnchantmentDamage,
        boolean shieldUsable,
        boolean totemUsable,
        double maxHealth,
        double portableHealing,
        double regenerationSeconds,
        double absorptionReserve,
        double foodAvailability,
        double movementSpeed,
        double reach,
        double utilityPotential
) {}
