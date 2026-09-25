package com.andresblue.threatdirector.awareness;

import com.andresblue.threatdirector.telemetry.GameplayDebugEvents;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

import java.util.EnumSet;
import java.util.List;

/** Acquires a target from additional senses while leaving ordinary targeting intact. */
final class AwarenessTargetGoal extends Goal {
    private static final int PLAYER_SCAN_CAP = 16;
    private static final int MIN_SCAN_INTERVAL = 12;

    private final MobEntity mob;
    private final AwarenessProfile profile;
    private final AwarenessSoundBuffer sounds;
    private final AwarenessMemory memory;
    private ServerPlayerEntity selectedTarget;
    private long nextScanTick;
    private long lastSoundTick = Long.MIN_VALUE;
    private int ownershipTicks;

    AwarenessTargetGoal(MobEntity mob, AwarenessProfile profile,
                        AwarenessSoundBuffer sounds, AwarenessMemory memory) {
        this.mob = mob;
        this.profile = profile;
        this.sounds = sounds;
        this.memory = memory;
        setControls(EnumSet.of(Control.TARGET));
    }

    @Override
    public boolean canStart() {
        if (!AwarenessModule.canUseAwareness(mob)
                || mob.getTarget() != null
                || !(mob.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        long now = world.getTime();
        if (now < nextScanTick) {
            return false;
        }
        nextScanTick = now + MIN_SCAN_INTERVAL + Math.floorMod(mob.getId(), 9);
        selectedTarget = targetFromMovementOrLight(world);
        if (selectedTarget == null) {
            selectedTarget = targetFromSound(world, now);
        }
        return selectedTarget != null;
    }

    @Override
    public void start() {
        if (selectedTarget != null && AwarenessModule.canUseAwareness(mob)) {
            mob.setTarget(selectedTarget);
            memory.clear();
            ownershipTicks = 10;
        }
    }

    @Override
    public boolean shouldContinue() {
        return ownershipTicks-- > 0
                && selectedTarget != null
                && selectedTarget.isAlive()
                && mob.getTarget() == selectedTarget
                && AwarenessModule.canUseAwareness(mob);
    }

    @Override
    public void stop() {
        selectedTarget = null;
        ownershipTicks = 0;
        // The target itself is intentionally retained for the mob's vanilla attack goals.
    }

    private ServerPlayerEntity targetFromSound(ServerWorld world, long now) {
        if (!profile.has(AwarenessSense.SOUND)) {
            return null;
        }
        AwarenessSoundPulse pulse = sounds.latestHeard(
                profile,
                mob.getBlockPos(),
                lastSoundTick,
                now
        ).orElse(null);
        if (pulse == null) {
            return null;
        }
        lastSoundTick = pulse.tick();
        ServerPlayerEntity source = playerById(world, pulse.sourcePlayerId());
        if (isEligible(source) && source.squaredDistanceTo(
                pulse.position().getX() + 0.5,
                pulse.position().getY() + 0.5,
                pulse.position().getZ() + 0.5
        ) <= pulse.rules().attributionRadius() * pulse.rules().attributionRadius()) {
            recordDetection(source, "sound:"
                    + pulse.rules().type().name().toLowerCase(java.util.Locale.ROOT));
            return source;
        }

        double attributionSquared = pulse.rules().attributionRadius()
                * pulse.rules().attributionRadius();
        ServerPlayerEntity nearby = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        int examined = 0;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (++examined > PLAYER_SCAN_CAP || !isEligible(player)) {
                continue;
            }
            double distance = player.squaredDistanceTo(
                    pulse.position().getX() + 0.5,
                    pulse.position().getY() + 0.5,
                    pulse.position().getZ() + 0.5
            );
            if (distance <= attributionSquared && distance < bestDistance) {
                bestDistance = distance;
                nearby = player;
            }
        }
        if (nearby != null) {
            recordDetection(nearby, "sound_nearby:"
                    + pulse.rules().type().name().toLowerCase(java.util.Locale.ROOT));
            return nearby;
        }

        memory.remember(
                pulse.position(),
                now + Math.min(80, pulse.rules().lifetimeTicks())
        );
        GameplayDebugEvents.record("awareness.investigate", java.util.Map.of(
                "entity", mob.getType().toString(),
                "entityUuid", mob.getUuid().toString(),
                "sound", pulse.rules().type().name(),
                "position", pulse.position().toShortString()));
        return null;
    }

    private ServerPlayerEntity targetFromMovementOrLight(ServerWorld world) {
        if (!profile.has(AwarenessSense.MOVEMENT) && !profile.has(AwarenessSense.LIGHT)) {
            return null;
        }
        ServerPlayerEntity best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        int examined = 0;
        List<ServerPlayerEntity> players = world.getPlayers();
        for (ServerPlayerEntity player : players) {
            if (++examined > PLAYER_SCAN_CAP || !isEligible(player)) {
                continue;
            }
            double distanceSquared = mob.squaredDistanceTo(player);
            if (distanceSquared > 32.0 * 32.0 || distanceSquared >= bestDistance) {
                continue;
            }
            Vec3d velocity = player.getVelocity();
            double speedSquared = velocity.x * velocity.x
                    + velocity.y * velocity.y
                    + velocity.z * velocity.z;
            BlockPos position = player.getBlockPos();
            int blockLight = player.isOnFire()
                    ? 15
                    : world.getLightLevel(LightType.BLOCK, position);
            boolean lineOfSight = mob.canSee(player);
            AwarenessPolicy.PlayerStimulus stimulus = new AwarenessPolicy.PlayerStimulus(
                    distanceSquared,
                    speedSquared,
                    player.isSprinting(),
                    player.isSneaking(),
                    blockLight,
                    lineOfSight
            );
            if (AwarenessPolicy.detectsMovement(profile, stimulus)
                    || AwarenessPolicy.detectsLight(profile, stimulus)) {
                best = player;
                bestDistance = distanceSquared;
            }
        }
        if (best != null) {
            recordDetection(best, "movement_or_light");
        }
        return best;
    }

    private void recordDetection(ServerPlayerEntity player, String stimulus) {
        GameplayDebugEvents.record("awareness.detected", java.util.Map.of(
                "entity", mob.getType().toString(),
                "entityUuid", mob.getUuid().toString(),
                "player", player.getUuid().toString(),
                "stimulus", stimulus));
    }

    private static ServerPlayerEntity playerById(ServerWorld world, java.util.UUID playerId) {
        if (playerId == null) {
            return null;
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getUuid().equals(playerId)) {
                return player;
            }
        }
        return null;
    }

    private static boolean isEligible(ServerPlayerEntity player) {
        return player != null
                && player.isAlive()
                && !player.isSpectator()
                && !player.isCreative();
    }
}
