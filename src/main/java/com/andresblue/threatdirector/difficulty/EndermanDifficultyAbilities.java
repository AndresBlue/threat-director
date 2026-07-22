package com.andresblue.threatdirector.difficulty;

import com.andresblue.threatdirector.server.ThreatScaling;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Comparator;
import java.util.List;

public final class EndermanDifficultyAbilities {
    private EndermanDifficultyAbilities() {
    }

    public static void alertNearbyAllies(HostileEntity hurtHostile, LivingEntity attacker) {
        if (!(hurtHostile.getWorld() instanceof ServerWorld world)
                || !(attacker instanceof ServerPlayerEntity player)
                || player.isCreative() || player.isSpectator()) {
            return;
        }
        if (ThreatScaling.playerThreat(player) < ThreatScaling.ACTIVATION_THREAT) {
            return;
        }
        DifficultyAbilityPolicy.EndermanRules rules = MinecraftDifficultyProfiles.forWorld(world).enderman();
        if (rules.maxAlliesAlerted() == 0) {
            return;
        }

        List<EndermanEntity> candidates = world.getEntitiesByClass(
                EndermanEntity.class,
                hurtHostile.getBoundingBox().expand(rules.allyAlertRadius()),
                enderman -> enderman.isAlive() && enderman != hurtHostile
        );
        candidates.sort(Comparator.comparingDouble(hurtHostile::squaredDistanceTo));
        int alerted = 0;
        for (EndermanEntity enderman : candidates) {
            if (alerted >= rules.maxAlliesAlerted()) {
                break;
            }
            if (!DifficultyAbilityPolicy.acceptsRoll(enderman.getRandom().nextDouble(), rules.allyAlertChance())) {
                continue;
            }
            enderman.setAngryAt(player.getUuid());
            enderman.chooseRandomAngerTime();
            enderman.setProvoked();
            enderman.setTarget(player);
            alerted++;
        }
    }

    public static boolean hasPillarAdvantage(EndermanEntity enderman, LivingEntity target) {
        double x = target.getX() - enderman.getX();
        double z = target.getZ() - enderman.getZ();
        double vertical = target.getY() - enderman.getY();
        if (!DifficultyAbilityPolicy.hasPillarAdvantage(x * x + z * z, vertical, target.isOnGround())) {
            return false;
        }

        BlockPos support = target.getBlockPos().down();
        if (!enderman.getWorld().getBlockState(support).isSolidBlock(enderman.getWorld(), support)) {
            return false;
        }
        int openSides = 0;
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos side = support.offset(direction);
            if (!enderman.getWorld().getBlockState(side).isSolidBlock(enderman.getWorld(), side)) {
                openSides++;
            }
        }
        return openSides >= 3;
    }
}
