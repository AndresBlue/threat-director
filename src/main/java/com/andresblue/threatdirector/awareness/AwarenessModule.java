package com.andresblue.threatdirector.awareness;

import com.andresblue.threatdirector.mixin.MobEntityAccessor;
import com.andresblue.threatdirector.telemetry.GameplayDebugEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.GameEvent;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/** Server-side lifecycle, persistence and GameEvent bridge for additional awareness senses. */
public final class AwarenessModule {
    private static final String ENCOUNTER_MANAGED_TAG = "threatdirector_managed";
    private static final Set<MobEntity> INSTALLED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<ServerWorld, AwarenessSoundBuffer> SOUND_BUFFERS = new WeakHashMap<>();

    private static volatile BooleanSupplier enabled = () -> true;
    private static volatile boolean registered;

    private AwarenessModule() {
    }

    /**
     * Registers once for the process. The supplied switch should reflect the mod's global
     * gameplay-enabled state so a config disable immediately makes every goal inert.
     */
    public static synchronized void register(BooleanSupplier enabledSupplier) {
        if (registered) {
            return;
        }
        enabled = java.util.Objects.requireNonNull(enabledSupplier, "enabledSupplier");
        registered = true;
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> install(entity, world));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clearSession());
    }

    public static boolean isRegistered() {
        return registered;
    }

    /** Called by the ServerWorld game-event mixin; intentionally ignores unrelated events. */
    public static void onGameEvent(ServerWorld world, GameEvent event,
                                   Vec3d position, GameEvent.Emitter emitter) {
        if (!registered || !isEnabled()) {
            return;
        }
        AwarenessPolicy.SoundRules rules = AwarenessPolicy
                .classifyGameEvent(event.getId())
                .orElse(null);
        if (rules == null) {
            return;
        }
        UUID sourcePlayerId = sourcePlayerId(emitter.sourceEntity(), rules.type());
        soundBuffer(world).record(
                rules,
                net.minecraft.util.math.BlockPos.ofFloored(position),
                sourcePlayerId,
                world.getTime()
        );
    }

    public static AwarenessProfile profileOf(MobEntity mob) {
        return AwarenessTags.decode(mob.getCommandTags());
    }

    static boolean canUseAwareness(MobEntity mob) {
        return isEnabled()
                && mob.isAlive()
                && !mob.getCommandTags().contains(ENCOUNTER_MANAGED_TAG);
    }

    private static void install(Entity entity, net.minecraft.world.World world) {
        if (!(entity instanceof MobEntity mob)
                || !(mob instanceof Monster)
                || !(world instanceof ServerWorld serverWorld)
                || !INSTALLED.add(mob)) {
            return;
        }

        AwarenessProfile profile;
        Set<String> tags = mob.getCommandTags();
        if (tags.contains(AwarenessTags.ASSIGNED)) {
            profile = AwarenessTags.decode(tags);
        } else {
            long seed = serverWorld.getSeed()
                    ^ mob.getUuid().getMostSignificantBits()
                    ^ Long.rotateLeft(mob.getUuid().getLeastSignificantBits(), 23);
            profile = AwarenessPolicy.roll(seed, serverWorld.getDifficulty().getId());
            AwarenessTags.encode(profile).forEach(mob::addCommandTag);
        }
        if (profile.isEmpty()) {
            return;
        }

        GameplayDebugEvents.record("awareness.assigned", Map.of(
                "entity", mob.getType().toString(),
                "entityUuid", mob.getUuid().toString(),
                "senses", profile.senses().stream()
                        .map(Enum::name).sorted().toList().toString()));

        AwarenessMemory memory = new AwarenessMemory();
        MobEntityAccessor accessor = (MobEntityAccessor) mob;
        accessor.threatdirector$getTargetSelector().add(
                3,
                new AwarenessTargetGoal(mob, profile, soundBuffer(serverWorld), memory)
        );
        if (profile.has(AwarenessSense.SOUND)) {
            accessor.threatdirector$getGoalSelector().add(
                    6,
                    new AwarenessInvestigateGoal(mob, memory)
            );
        }
    }

    private static AwarenessSoundBuffer soundBuffer(ServerWorld world) {
        return SOUND_BUFFERS.computeIfAbsent(world, ignored -> new AwarenessSoundBuffer());
    }

    private static UUID sourcePlayerId(Entity source, AwarenessSoundType type) {
        if (source instanceof ServerPlayerEntity player) {
            return player.getUuid();
        }
        if (source instanceof ProjectileEntity projectile
                && projectile.getOwner() instanceof ServerPlayerEntity player) {
            return player.getUuid();
        }
        if (type == AwarenessSoundType.COMBAT
                && source instanceof LivingEntity living
                && living.getAttacker() instanceof ServerPlayerEntity player
                && living.age - living.getLastAttackedTime() <= 40) {
            return player.getUuid();
        }
        if (type == AwarenessSoundType.EXPLOSION && source != null) {
            Entity rootVehicle = source.getRootVehicle();
            if (rootVehicle instanceof ServerPlayerEntity player) {
                return player.getUuid();
            }
        }
        return null;
    }

    private static boolean isEnabled() {
        try {
            return enabled.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static synchronized void clearSession() {
        SOUND_BUFFERS.values().forEach(AwarenessSoundBuffer::clear);
        SOUND_BUFFERS.clear();
        INSTALLED.clear();
    }
}
