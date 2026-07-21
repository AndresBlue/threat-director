package com.andresblue.threatdirector.server;

import com.andresblue.threatdirector.config.ThreatDirectorConfig;
import com.andresblue.threatdirector.power.AdventureIntensityBreakdown;
import com.andresblue.threatdirector.power.AdventureIntensityCalculator;
import com.andresblue.threatdirector.power.AdventureSignals;
import com.andresblue.threatdirector.power.PowerEvaluationPipeline;
import com.andresblue.threatdirector.power.PowerAxis;
import com.andresblue.threatdirector.power.PowerReport;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PlayerObservationService {
    private final PowerEvaluationPipeline powerPipeline;
    private final Map<UUID, PlayerDirectorState> states = new HashMap<>();
    private final Map<UUID, Long> lastSeenTicks = new HashMap<>();

    public PlayerObservationService(PowerEvaluationPipeline powerPipeline) {
        this.powerPipeline = powerPipeline;
    }

    public void sampleAll(MinecraftServer server, ThreatDirectorConfig config, double planningLoad) {
        long tick = server.getTicks();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            lastSeenTicks.put(player.getUuid(), tick);
            if (player.isSpectator()) {
                continue;
            }
            PlayerDirectorState state = state(player.getUuid());
            if (state.lastSampleTick() >= 0
                    && tick - state.lastSampleTick() < config.intensity().sampleIntervalTicks()) {
                continue;
            }
            sample(server, player, state, config, planningLoad, tick);
        }
        long retentionTicks = Math.max(
                24_000L,
                Math.max(config.director().cooldownMaxTicks(),
                        config.intensity().recentDeathCooldownTicks()));
        states.keySet().removeIf(playerId ->
                server.getPlayerManager().getPlayer(playerId) == null
                        && tick - lastSeenTicks.getOrDefault(playerId, 0L) > retentionTicks);
        lastSeenTicks.keySet().removeIf(playerId -> !states.containsKey(playerId));
    }

    private void sample(
            MinecraftServer server,
            ServerPlayerEntity player,
            PlayerDirectorState state,
            ThreatDirectorConfig config,
            double planningLoad,
            long tick) {
        ServerWorld world = player.getServerWorld();
        double effectiveHealth = player.getHealth() + player.getAbsorptionAmount();
        state.observeHealth(tick, effectiveHealth);

        Map<PowerAxis, Integer> powerCaps = powerCaps(config);
        PowerReport targetPower = powerPipeline.evaluate(
                VanillaPlayerSnapshotFactory.capture(player), powerCaps);
        Collection<ServerPlayerEntity> nearbyPlayers = server.getPlayerManager().getPlayerList().stream()
                .filter(other -> other.getServerWorld() == world)
                .filter(other -> other.isAlive()
                        && other.interactionManager.getGameMode() == GameMode.SURVIVAL)
                .filter(other -> other.squaredDistanceTo(player)
                        <= square(config.multiplayer().groupRadius()))
                .toList();
        ArrayList<Double> groupPowers = new ArrayList<>(nearbyPlayers.size());
        for (ServerPlayerEntity member : nearbyPlayers) {
            PowerReport memberPower = member == player
                    ? targetPower
                    : powerPipeline.evaluate(VanillaPlayerSnapshotFactory.capture(member), powerCaps);
            groupPowers.add(memberPower.score());
        }

        BlockPos position = player.getBlockPos();
        double darkness = 1.0 - world.getLightLevel(position) / 15.0;
        double dimensionThreat = dimensionThreat(world.getRegistryKey());
        double depth = clamp01((world.getSeaLevel() - player.getY()) / 64.0);
        double outsideSafeArea = outsideSpawnProtection(world, position, config) ? 1.0 : 0.0;
        int hostileCount = world.getEntitiesByClass(
                HostileEntity.class,
                player.getBoundingBox().expand(config.encounter().recruitRadius()),
                Entity::isAlive).size();
        double entityLoad = clamp01(hostileCount / (double) config.encounter().maxNearbyHostiles());

        long sinceEncounter = elapsedOrCap(tick, state.lastDangerousEncounterTick(), 12_000L);
        long sinceCombat = elapsedOrCap(tick, state.lastCombatTick(), 2_400L);
        boolean recentDeath = state.lastDeathTick() != Long.MIN_VALUE;
        long sinceDeath = recentDeath ? Math.max(0L, tick - state.lastDeathTick()) : 12_000L;

        int recentWindow = config.intensity().recentDamageWindowTicks();
        AdventureSignals signals = new AdventureSignals(
                groupPowers,
                world.isNight() ? 1.0 : 0.0,
                darkness,
                dimensionThreat,
                0.5,
                depth,
                darkness,
                outsideSafeArea,
                sinceEncounter,
                sinceCombat,
                state.intensity(),
                state.killsWithin(tick, recentWindow),
                state.damageWithin(tick, recentWindow),
                state.lastCombatTick() == Long.MIN_VALUE ? 0.0 : 1.0,
                player.getMaxHealth(),
                player.getHealth(),
                player.getAbsorptionAmount(),
                state.damageWithin(tick, Math.min(200L, recentWindow)),
                recentDeath,
                sinceDeath,
                entityLoad,
                planningLoad);
        AdventureIntensityBreakdown breakdown = AdventureIntensityCalculator.calculate(
                signals,
                config.intensity().groupCap(),
                config.intensity().contextCap(),
                config.intensity().stressPenaltyCap());
        int elapsedTicks = state.lastSampleTick() < 0
                ? config.intensity().sampleIntervalTicks()
                : (int) Math.min(200L, tick - state.lastSampleTick());
        double smoothed = state.lastSampleTick() < 0
                ? breakdown.targetIntensity()
                : AdventureIntensityCalculator.smooth(
                        state.intensity(),
                        breakdown.targetIntensity(),
                        elapsedTicks,
                        config.intensity().sampleIntervalTicks(),
                        config.intensity().smoothingAlpha());
        smoothed = Math.min(config.intensity().maxIntensity(), smoothed);
        state.updateEvaluation(
                targetPower,
                breakdown,
                smoothed,
                tick,
                elapsedTicks,
                config.intensity().hysteresisPoints());
    }

    public PlayerDirectorState state(UUID playerId) {
        return states.computeIfAbsent(playerId, PlayerDirectorState::new);
    }

    public Optional<PlayerDirectorState> find(UUID playerId) {
        return Optional.ofNullable(states.get(playerId));
    }

    public Collection<PlayerDirectorState> states() {
        return states.values();
    }

    public void recordKill(ServerPlayerEntity player, long tick) {
        state(player.getUuid()).recordKill(tick);
    }

    public void recordDeath(ServerPlayerEntity player, long tick) {
        state(player.getUuid()).recordDeath(tick);
    }

    private static boolean outsideSpawnProtection(
            ServerWorld world,
            BlockPos position,
            ThreatDirectorConfig config) {
        if (world.getRegistryKey() != World.OVERWORLD) {
            return true;
        }
        BlockPos spawn = world.getServer().getOverworld().getSpawnPos();
        int radius = config.breachSimulation().spawnProtectionRadius();
        return Math.abs(position.getX() - spawn.getX()) > radius
                || Math.abs(position.getZ() - spawn.getZ()) > radius;
    }

    private static double dimensionThreat(RegistryKey<World> key) {
        if (key == World.NETHER) {
            return 0.85;
        }
        if (key == World.END) {
            return 0.9;
        }
        return 0.45;
    }

    private static long elapsedOrCap(long now, long then, long cap) {
        return then == Long.MIN_VALUE ? cap : Math.min(cap, Math.max(0L, now - then));
    }

    private static double square(double value) {
        return value * value;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static Map<PowerAxis, Integer> powerCaps(ThreatDirectorConfig config) {
        return Map.of(
                PowerAxis.DEFENSE, milliCap(config.intensity().defenseCap()),
                PowerAxis.OFFENSE, milliCap(config.intensity().offenseCap()),
                PowerAxis.SUSTAIN, milliCap(config.intensity().sustainCap()));
    }

    private static int milliCap(double value) {
        double scaled = Math.max(0.0, value) * 1_000.0;
        return scaled >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.round(scaled);
    }
}
