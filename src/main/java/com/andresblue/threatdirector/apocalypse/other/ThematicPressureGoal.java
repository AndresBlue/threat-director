package com.andresblue.threatdirector.apocalypse.other;

import com.andresblue.threatdirector.ThreatDirectorMod;
import com.andresblue.threatdirector.apocalypse.other.OtherHostilePolicy.AbilityRules;
import com.andresblue.threatdirector.apocalypse.other.OtherHostilePolicy.Family;
import com.andresblue.threatdirector.apocalypse.other.OtherHostilePolicy.HorizontalVelocity;
import com.andresblue.threatdirector.apocalypse.other.OtherHostilePolicy.Tier;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

final class ThematicPressureGoal extends Goal {
    private final MobEntity mob;
    private final Family family;
    private int cooldownTicks;

    ThematicPressureGoal(MobEntity mob, Family family) {
        this.mob = mob;
        this.family = family;
        AbilityRules hardRules = OtherHostilePolicy.ability(Tier.HARD, family);
        int staggerRange = Math.max(1, hardRules.cooldownTicks());
        this.cooldownTicks = Math.floorMod(mob.getUuid().hashCode(), staggerRange);
    }

    @Override
    public boolean canStart() {
        return rules().enabled() && hasLivingTarget();
    }

    @Override
    public boolean shouldContinue() {
        return rules().enabled() && hasLivingTarget();
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        AbilityRules rules = rules();
        if (!rules.enabled()) {
            return;
        }
        cooldownTicks = Math.min(cooldownTicks, rules.cooldownTicks());
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        LivingEntity target = mob.getTarget();
        boolean used = target != null && target.isAlive() && useAbility(target, rules);
        cooldownTicks = used ? rules.cooldownTicks() : rules.retryTicks();
    }

    private boolean useAbility(LivingEntity target, AbilityRules rules) {
        return switch (family) {
            case WITCH_ILLAGER -> useRaiderPressure(target, rules);
            case AERIAL_FIRE -> useAerialPressure(target, rules);
            case GUARDIAN -> useGuardianPressure(target, rules);
            case DROWNED_HUSK -> useUndeadPressure(target, rules);
            case SWARM -> rallySwarm(target, rules);
            case BRUTE -> useBruteCharge(target, rules);
            case OTHER -> false;
        };
    }

    private boolean useRaiderPressure(LivingEntity target, AbilityRules rules) {
        boolean visible = mob.getVisibilityCache().canSee(target);
        double distanceSquared = mob.squaredDistanceTo(target);
        if (!OtherHostilePolicy.canUseClosePressure(rules, true, visible, distanceSquared)) {
            return false;
        }
        if (mob instanceof WitchEntity) {
            return target.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.WEAKNESS,
                    rules.statusDurationTicks(),
                    rules.statusAmplifier(),
                    false,
                    true
            ), mob);
        }
        if (!(mob.getWorld() instanceof ServerWorld world)) {
            return false;
        }

        List<RaiderEntity> allies = world.getEntitiesByClass(
                RaiderEntity.class,
                mob.getBoundingBox().expand(rules.allyRadius()),
                ally -> ally.isAlive() && ally != mob
        );
        int rallied = 0;
        for (RaiderEntity ally : allies) {
            if (rallied >= rules.maximumAllies()) {
                break;
            }
            LivingEntity currentTarget = ally.getTarget();
            if (currentTarget != null && currentTarget.isAlive() && currentTarget != target) {
                continue;
            }
            ally.setTarget(target);
            ally.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SPEED,
                    rules.statusDurationTicks(),
                    0,
                    false,
                    false
            ), mob);
            rallied++;
        }
        return rallied > 0;
    }

    private boolean useAerialPressure(LivingEntity target, AbilityRules rules) {
        boolean visible = mob.getVisibilityCache().canSee(target);
        double distanceSquared = mob.squaredDistanceTo(target);
        if (!OtherHostilePolicy.canUseVisibleRangedAbility(rules, true, visible, distanceSquared)) {
            return false;
        }
        applyApproachVelocity(target, rules, 0.12D);
        return true;
    }

    private boolean useGuardianPressure(LivingEntity target, AbilityRules rules) {
        boolean visible = mob.getVisibilityCache().canSee(target);
        double distanceSquared = mob.squaredDistanceTo(target);
        if (!target.isTouchingWater()
                || !OtherHostilePolicy.canUseClosePressure(rules, true, visible, distanceSquared)) {
            return false;
        }
        return target.addStatusEffect(new StatusEffectInstance(
                StatusEffects.MINING_FATIGUE,
                rules.statusDurationTicks(),
                rules.statusAmplifier(),
                false,
                true
        ), mob);
    }

    private boolean useUndeadPressure(LivingEntity target, AbilityRules rules) {
        boolean visible = mob.getVisibilityCache().canSee(target);
        double distanceSquared = mob.squaredDistanceTo(target);
        if (!OtherHostilePolicy.canUseClosePressure(rules, true, visible, distanceSquared)) {
            return false;
        }
        if (mob instanceof DrownedEntity && !target.isTouchingWater()) {
            return false;
        }
        return target.addStatusEffect(new StatusEffectInstance(
                mob instanceof DrownedEntity ? StatusEffects.MINING_FATIGUE : StatusEffects.HUNGER,
                rules.statusDurationTicks(),
                rules.statusAmplifier(),
                false,
                true
        ), mob);
    }

    private boolean rallySwarm(LivingEntity target, AbilityRules rules) {
        if (!(mob.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        List<MobEntity> allies = world.getEntitiesByClass(
                MobEntity.class,
                mob.getBoundingBox().expand(rules.allyRadius()),
                ally -> ally != mob
                        && ally.isAlive()
                        && OtherHostileFamilies.familyOf(ally) == Family.SWARM
        );
        int rallied = 0;
        for (MobEntity ally : allies) {
            if (rallied >= rules.maximumAllies()) {
                break;
            }
            LivingEntity currentTarget = ally.getTarget();
            if (currentTarget != null && currentTarget.isAlive()) {
                continue;
            }
            ally.setTarget(target);
            rallied++;
        }
        return rallied > 0;
    }

    private boolean useBruteCharge(LivingEntity target, AbilityRules rules) {
        boolean visible = mob.getVisibilityCache().canSee(target);
        double distanceSquared = mob.squaredDistanceTo(target);
        if (!mob.isOnGround()
                || !OtherHostilePolicy.canUseVisibleRangedAbility(rules, true, visible, distanceSquared)) {
            return false;
        }
        applyApproachVelocity(target, rules, 0.06D);
        return true;
    }

    private void applyApproachVelocity(LivingEntity target, AbilityRules rules, double verticalLimit) {
        Vec3d velocity = mob.getVelocity();
        Vec3d offset = target.getPos().subtract(mob.getPos());
        HorizontalVelocity horizontal = OtherHostilePolicy.boundedApproachVelocity(
                rules,
                velocity.x,
                velocity.z,
                offset.x,
                offset.z
        );
        double vertical = velocity.y + MathHelper.clamp(offset.y * 0.03D, -verticalLimit, verticalLimit);
        mob.setVelocity(horizontal.x(), vertical, horizontal.z());
        mob.velocityModified = true;
    }

    private boolean hasLivingTarget() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    private AbilityRules rules() {
        return OtherHostilePolicy.ability(tier(), family);
    }

    private Tier tier() {
        if (!ThreatDirectorMod.isEnabled()) {
            return Tier.EASY;
        }
        return switch (mob.getWorld().getDifficulty()) {
            case NORMAL -> Tier.NORMAL;
            case HARD -> Tier.HARD;
            default -> Tier.EASY;
        };
    }
}
