package com.andresblue.threatdirector.traits;

import com.andresblue.threatdirector.ThreatDirectorMod;
import com.andresblue.threatdirector.encounter.EncounterController;
import com.andresblue.threatdirector.telemetry.GameplayDebugEvents;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;

/**
 * Sparse maintenance for passive traits. It owns no Goal controls and therefore
 * can coexist with vanilla goals and director movement/target orders.
 */
final class TraitMaintenanceGoal extends Goal {
    private static final UUID BERSERKER_DAMAGE_ID = UUID.fromString("6f1d8893-04fd-40e8-b06d-bec4ac1f7641");
    private static final UUID BERSERKER_SPEED_ID = UUID.fromString("937813f2-f41d-4dcc-9ee5-3af8d0f37972");
    private static final UUID SWIFT_SPEED_ID = UUID.fromString("12ac8f08-1195-4f65-97e0-910124053ad0");
    private static final UUID RESILIENT_ARMOR_ID = UUID.fromString("716260f4-497a-4fd9-b34a-5f131a00a98e");
    private static final UUID RESILIENT_KNOCKBACK_ID = UUID.fromString("33fa2f4d-ca32-44eb-a30d-e89369af0667");
    private static final int ATTRIBUTE_REFRESH_INTERVAL = 20;

    private final MobEntity mob;
    private final Set<MobTrait> traits;
    private final int scheduleOffset;
    private boolean wasBerserking;

    TraitMaintenanceGoal(MobEntity mob, Set<MobTrait> traits) {
        this.mob = mob;
        this.traits = Set.copyOf(traits);
        this.scheduleOffset = Math.floorMod(mob.getUuid().hashCode(), 1200);
    }

    @Override
    public boolean canStart() {
        return ThreatDirectorMod.isTraitsEnabled() && mob.isAlive() && !traits.isEmpty();
    }

    @Override
    public boolean shouldContinue() {
        return canStart();
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    @Override
    public void start() {
        reconcileAttributes(profile());
    }

    @Override
    public void tick() {
        TraitBehaviorPolicy.BehaviorProfile profile = profile();
        if (due(ATTRIBUTE_REFRESH_INTERVAL)) {
            reconcileAttributes(profile);
        }
        if (traits.contains(MobTrait.REGENERATIVE)
                && due(profile.regenerationIntervalTicks())
                && mob.getHealth() < mob.getMaxHealth()) {
            mob.heal(profile.regenerationAmount());
        }
        if (traits.contains(MobTrait.LEADER) && due(profile.leaderIntervalTicks())) {
            rallyNearby(profile);
        }
        if (due(profile.particleIntervalTicks()) && mob.getWorld() instanceof ServerWorld world) {
            // One shared, subtle visual vocabulary; combinations remain primarily systemic.
            world.spawnParticles(
                    ParticleTypes.ENCHANT,
                    mob.getX(), mob.getBodyY(0.55D), mob.getZ(),
                    1, 0.12D, 0.18D, 0.12D, 0.0D);
        }
    }

    @Override
    public void stop() {
        clearManagedModifiers();
    }

    private void reconcileAttributes(TraitBehaviorPolicy.BehaviorProfile profile) {
        boolean berserking = traits.contains(MobTrait.BERSERKER)
                && TraitBehaviorPolicy.berserkerActive(healthFraction(), profile);
        if (berserking && !wasBerserking) {
            GameplayDebugEvents.record("traits.berserker_activated", java.util.Map.of(
                    "entity", mob.getType().toString(),
                    "entityUuid", mob.getUuid().toString(),
                    "health", mob.getHealth()));
        }
        wasBerserking = berserking;
        reconcileModifier(
                EntityAttributes.GENERIC_ATTACK_DAMAGE,
                BERSERKER_DAMAGE_ID,
                "Threat Director berserker damage",
                profile.berserkerDamageMultiplier(),
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL,
                berserking);
        reconcileModifier(
                EntityAttributes.GENERIC_MOVEMENT_SPEED,
                BERSERKER_SPEED_ID,
                "Threat Director berserker speed",
                profile.berserkerSpeedMultiplier(),
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL,
                berserking);
        reconcileModifier(
                EntityAttributes.GENERIC_MOVEMENT_SPEED,
                SWIFT_SPEED_ID,
                "Threat Director swift speed",
                profile.swiftSpeedMultiplier(),
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL,
                traits.contains(MobTrait.SWIFT));
        reconcileModifier(
                EntityAttributes.GENERIC_ARMOR,
                RESILIENT_ARMOR_ID,
                "Threat Director resilient armor",
                profile.resilientArmorBonus(),
                EntityAttributeModifier.Operation.ADDITION,
                traits.contains(MobTrait.RESILIENT));
        reconcileModifier(
                EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE,
                RESILIENT_KNOCKBACK_ID,
                "Threat Director resilient knockback resistance",
                profile.resilientKnockbackResistance(),
                EntityAttributeModifier.Operation.ADDITION,
                traits.contains(MobTrait.RESILIENT));
    }

    private void reconcileModifier(EntityAttribute attribute, UUID id, String name, double amount,
                                   EntityAttributeModifier.Operation operation, boolean enabled) {
        EntityAttributeInstance instance = mob.getAttributeInstance(attribute);
        if (instance == null) {
            return;
        }
        EntityAttributeModifier current = instance.getModifier(id);
        if (!enabled) {
            if (current != null) {
                instance.removeModifier(id);
            }
            return;
        }
        if (current != null
                && Double.compare(current.getValue(), amount) == 0
                && current.getOperation() == operation) {
            return;
        }
        if (current != null) {
            instance.removeModifier(id);
        }
        instance.addTemporaryModifier(new EntityAttributeModifier(id, name, amount, operation));
    }

    private void rallyNearby(TraitBehaviorPolicy.BehaviorProfile profile) {
        if (!(mob.getWorld() instanceof ServerWorld world)
                || directorControlled(mob)) {
            return;
        }
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }

        List<MobEntity> nearby = world.getEntitiesByClass(
                MobEntity.class,
                mob.getBoundingBox().expand(profile.leaderRadius()),
                ally -> ally != mob
                        && ally.isAlive()
                        && ally.getType().getSpawnGroup() == SpawnGroup.MONSTER
                        && !directorControlled(ally)
                        && (ally.getTarget() == null || !ally.getTarget().isAlive()));
        int rallied = 0;
        for (MobEntity ally : nearby) {
            if (rallied >= profile.leaderMaximumAllies()) {
                break;
            }
            // Never replace a living target and never touch a director-owned mob.
            ally.setTarget(target);
            ally.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.RESISTANCE, 50, 0, false, false), mob);
            rallied++;
        }
        if (rallied > 0) {
            GameplayDebugEvents.record("traits.leader_rally", java.util.Map.of(
                    "entity", mob.getType().toString(),
                    "entityUuid", mob.getUuid().toString(),
                    "allies", rallied));
        }
    }

    private void clearManagedModifiers() {
        removeModifier(EntityAttributes.GENERIC_ATTACK_DAMAGE, BERSERKER_DAMAGE_ID);
        removeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED, BERSERKER_SPEED_ID);
        removeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED, SWIFT_SPEED_ID);
        removeModifier(EntityAttributes.GENERIC_ARMOR, RESILIENT_ARMOR_ID);
        removeModifier(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, RESILIENT_KNOCKBACK_ID);
    }

    private void removeModifier(EntityAttribute attribute, UUID id) {
        EntityAttributeInstance instance = mob.getAttributeInstance(attribute);
        if (instance != null && instance.getModifier(id) != null) {
            instance.removeModifier(id);
        }
    }

    private boolean directorControlled(MobEntity entity) {
        return entity.getCommandTags().contains(EncounterController.MANAGED_TAG);
    }

    private boolean due(int interval) {
        return Math.floorMod(mob.age + scheduleOffset, interval) == 0;
    }

    private double healthFraction() {
        return mob.getMaxHealth() <= 0.0F ? 1.0D : mob.getHealth() / mob.getMaxHealth();
    }

    private TraitBehaviorPolicy.BehaviorProfile profile() {
        return TraitBehaviorPolicy.profile(
                TraitDifficulty.fromDifficultyId(mob.getWorld().getDifficulty().getId()));
    }
}
