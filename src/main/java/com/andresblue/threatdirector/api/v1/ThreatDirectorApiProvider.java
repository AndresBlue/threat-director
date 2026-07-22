package com.andresblue.threatdirector.api.v1;

import com.andresblue.threatdirector.director.EncounterState;
import com.andresblue.threatdirector.director.EncounterTransition;
import com.andresblue.threatdirector.director.TransitionCause;
import com.andresblue.threatdirector.encounter.EncounterController;
import com.andresblue.threatdirector.encounter.EncounterManager;
import com.andresblue.threatdirector.server.PlayerDirectorState;
import com.andresblue.threatdirector.server.ThreatDirectorRuntime;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fabric entrypoint implementation for Integration API v1. */
public final class ThreatDirectorApiProvider implements ThreatDirectorApi {
    private static final Map<UUID, Binding> BINDINGS = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<ThreatDirectorEncounterListener> LISTENERS =
            new CopyOnWriteArrayList<>();
    private static volatile ThreatDirectorRuntime runtime;
    private static volatile AutoCloseable transitionSubscription;
    private static volatile String lastError = "server runtime has not started";

    public void bind(ThreatDirectorRuntime newRuntime) {
        unbind(runtime);
        runtime = newRuntime;
        transitionSubscription = newRuntime.encounters().addTransitionListener(
                ThreatDirectorApiProvider::onTransition);
        lastError = "";
    }

    public void unbind(ThreatDirectorRuntime expectedRuntime) {
        if (expectedRuntime != null && runtime != null && runtime != expectedRuntime) return;
        AutoCloseable subscription = transitionSubscription;
        transitionSubscription = null;
        if (subscription != null) {
            try {
                subscription.close();
            } catch (Exception ignored) {
                // Removing an in-memory listener is best effort during server shutdown.
            }
        }
        runtime = null;
        BINDINGS.clear();
        lastError = "server runtime is stopped";
    }

    public void afterDeath(LivingEntity entity) {
        if (entity == null) return;
        BINDINGS.values().stream()
                .filter(binding -> binding.request().captainEntityId()
                        .map(entity.getUuid()::equals).orElse(false))
                .forEach(binding -> emit(binding, EncounterEventType.CAPTAIN_KILLED,
                        currentTick(), "captain entity died"));
    }

    @Override
    public int apiVersion() {
        return API_VERSION;
    }

    @Override
    public boolean isAvailable() {
        return runtime != null;
    }

    @Override
    public Optional<String> lastError() {
        return lastError == null || lastError.isBlank() ? Optional.empty() : Optional.of(lastError);
    }

    @Override
    public Optional<ThreatSnapshot> getThreat(ServerPlayerEntity player) {
        ThreatDirectorRuntime current = runtime;
        if (current == null || player == null || player.getServer() != current.server()) {
            return Optional.empty();
        }
        PlayerDirectorState state = current.observations().state(player.getUuid());
        return Optional.of(new ThreatSnapshot(
                player.getUuid(), state.intensity(), state.band().name(), state.lastSampleTick()));
    }

    @Override
    public Optional<EncounterHandle> requestEncounter(ServerWorld world, EncounterRequest request) {
        ThreatDirectorRuntime current = runtime;
        if (current == null || world == null || request == null || world.getServer() != current.server()) {
            reject(request, "Threat Director runtime or world is unavailable", false);
            return Optional.empty();
        }
        ServerPlayerEntity target = request.targetPlayers().stream()
                .sorted(Comparator.comparing(UUID::toString))
                .map(current.server().getPlayerManager()::getPlayer)
                .filter(player -> player != null && player.getServerWorld() == world)
                .findFirst().orElse(null);
        if (target == null) {
            reject(request, "no online target player in the requested world", false);
            return Optional.empty();
        }
        EncounterManager.StartResult result = current.encounters().forceStartExternal(
                target,
                current.config(),
                current.governor().limits(),
                request.requiredParticipants());
        if (!result.started()) {
            boolean loadRejection = result.reason().contains("performance")
                    || result.reason().contains("safety cap")
                    || result.reason().contains("budget");
            reject(request, result.reason(), loadRejection);
            return Optional.empty();
        }
        EncounterHandle handle = new EncounterHandle(result.encounterId(), request.sourceId());
        Binding binding = new Binding(handle, request, target.getUuid());
        BINDINGS.put(handle.encounterId(), binding);
        lastError = "";
        emit(binding, EncounterEventType.ENCOUNTER_ACCEPTED, current.server().getTicks(),
                "members=" + result.members() + ", threatBudget=" + result.threatBudget());
        return Optional.of(handle);
    }

    @Override
    public boolean updateEncounter(EncounterHandle handle, EncounterUpdate update) {
        ThreatDirectorRuntime current = runtime;
        Binding binding = binding(handle).orElse(null);
        EncounterController controller = current == null || binding == null
                ? null : current.encounters().byId(handle.encounterId()).orElse(null);
        if (controller == null || update == null) return false;
        if (update.authorizeRetreat().orElse(false)) {
            boolean changed = controller.forceState(
                    EncounterState.RETREATING,
                    current.server().getTicks(),
                    current.observations().state(binding.targetPlayerId()).intensity());
            if (changed) {
                emit(binding, EncounterEventType.CAPTAIN_RETREATED,
                        current.server().getTicks(), update.note());
            }
            return changed;
        }
        // Pressure and reinforcement fields are advisory in v1; the tactical governor remains final.
        return true;
    }

    @Override
    public boolean cancelEncounter(EncounterHandle handle, EncounterEndReason reason) {
        ThreatDirectorRuntime current = runtime;
        Binding binding = binding(handle).orElse(null);
        if (current == null || binding == null) return false;
        boolean cancelled = current.encounters().cancel(
                binding.targetPlayerId(), current.server().getTicks());
        if (cancelled) {
            emit(binding, EncounterEventType.ENCOUNTER_ABORTED,
                    current.server().getTicks(), reason == null ? "cancelled" : reason.name());
        }
        BINDINGS.remove(handle.encounterId());
        return cancelled;
    }

    @Override
    public Optional<EncounterSnapshot> inspectEncounter(EncounterHandle handle) {
        ThreatDirectorRuntime current = runtime;
        Binding binding = binding(handle).orElse(null);
        if (current == null || binding == null) return Optional.empty();
        return current.encounters().byId(handle.encounterId()).map(controller ->
                new EncounterSnapshot(
                        handle,
                        controller.state().name(),
                        controller.livingCount(),
                        controller.budget().remaining(),
                        controller.budget().total(),
                        !controller.finished(),
                        binding.request().factionId(),
                        binding.request().operationType(),
                        binding.request().regionId()));
    }

    @Override
    public void registerEncounterListener(ThreatDirectorEncounterListener listener) {
        if (listener != null) LISTENERS.addIfAbsent(listener);
    }

    @Override
    public void unregisterEncounterListener(ThreatDirectorEncounterListener listener) {
        LISTENERS.remove(listener);
    }

    private static Optional<Binding> binding(EncounterHandle handle) {
        if (handle == null) return Optional.empty();
        Binding binding = BINDINGS.get(handle.encounterId());
        return binding != null && binding.handle().equals(handle)
                ? Optional.of(binding) : Optional.empty();
    }

    private static void reject(EncounterRequest request, String reason, boolean loadRejection) {
        lastError = reason;
        if (request == null) return;
        UUID rejectionId = UUID.nameUUIDFromBytes(
                (request.sourceId() + ":rejected").getBytes(StandardCharsets.UTF_8));
        Binding rejected = new Binding(
                new EncounterHandle(rejectionId, request.sourceId()), request, new UUID(0L, 0L));
        emit(rejected,
                loadRejection ? EncounterEventType.SERVER_LOAD_REJECTED
                        : EncounterEventType.ENCOUNTER_REJECTED,
                currentTick(), reason);
    }

    private static void onTransition(EncounterTransition transition) {
        Binding binding = BINDINGS.get(transition.encounterId());
        if (binding == null) return;
        if (transition.cause() == TransitionCause.GROUP_DEFEATED) {
            emit(binding, EncounterEventType.GROUP_DEFEATED,
                    transition.serverTick(), transition.cause().name());
        }
        EncounterEventType type = switch (transition.to()) {
            case STALKING -> EncounterEventType.STALKING_STARTED;
            case POSITIONING -> EncounterEventType.POSITIONING_STARTED;
            case ENGAGING -> EncounterEventType.ENGAGEMENT_STARTED;
            case CLIMAX -> EncounterEventType.CLIMAX_STARTED;
            case SEARCHING -> EncounterEventType.SEARCH_STARTED;
            case RELIEF -> EncounterEventType.RELIEF_STARTED;
            case RETREATING -> EncounterEventType.RETREAT_STARTED;
            case COOLDOWN -> EncounterEventType.ENCOUNTER_COMPLETED;
            case ABORTED -> transition.cause() == TransitionCause.CONTACT_LOST
                    ? EncounterEventType.TARGET_ESCAPED : EncounterEventType.ENCOUNTER_ABORTED;
            default -> null;
        };
        if (type != null) emit(binding, type, transition.serverTick(), transition.cause().name());
        if (transition.to() == EncounterState.COOLDOWN || transition.to() == EncounterState.ABORTED) {
            BINDINGS.remove(transition.encounterId());
        }
    }

    private static void emit(
            Binding binding, EncounterEventType type, long tick, String detail) {
        EncounterEvent event = new EncounterEvent(binding.handle(), type, tick, detail);
        for (ThreatDirectorEncounterListener listener : LISTENERS) {
            try {
                listener.onEncounterEvent(event);
            } catch (RuntimeException ignored) {
                // An optional integration cannot destabilize encounter execution.
            }
        }
    }

    private static long currentTick() {
        ThreatDirectorRuntime current = runtime;
        return current == null ? -1L : current.server().getTicks();
    }

    private record Binding(
            EncounterHandle handle, EncounterRequest request, UUID targetPlayerId) {}
}
