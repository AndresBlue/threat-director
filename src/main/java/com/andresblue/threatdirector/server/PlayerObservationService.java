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
import net.minecraft.stat.Stats;
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
    /** Six hours of persisted play time reaches the maximum time-based pressure contribution. */
    private static final double PLAY_TIME_FOR_FULL_PRESSURE_TICKS = 6.0 * 60.0 * 60.0 * 20.0;
    /** Level 30 reaches the maximum experience-based pressure contribution. */
    private static final double EXPERIENCE_LEVEL_FOR_FULL_PRESSURE = 30.0;
    /** Each death removes a bounded slice of the time-based progression pressure. */
    private static final double PLAY_TIME_DEATH_PENALTY_PER_DEATH = 0.15;
    /** Repeated deaths never erase more than 65% of the time-based progression. */
    private static final double PLAY_TIME_MAX_DEATH_PENALTY = 0.65;

    private final PowerEvaluationPipeline powerPipeline;
    private final Map<UUID, PlayerDirectorState> states = new HashMap<>();
    private final Map<UUID, Long> lastSeenTicks = new HashMap<>();

    public PlayerObservationService(PowerEvaluationPipeline powerPipeline) {
        this.powerPipeline = powerPipeline;
    }

    public void sampleAll(MinecraftServer server, ThreatDirectorConfig config, double planningLoad) {
        long tick = server.getTicks();
        Map<UUID, PowerReport> powerReports = null;
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
            if (powerReports == null) {
                powerReports = evaluateOnlinePlayers(server, config);
            }
            sample(server, player, state, config, planningLoad, tick, powerReports);
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
            long tick,
            Map<UUID, PowerReport> powerReports) {
        ServerWorld world = player.getServerWorld();
        double effectiveHealth = player.getHealth() + player.getAbsorptionAmount();
        state.observeHealth(tick, effectiveHealth);

        Map<PowerAxis, Integer> powerCaps = powerCaps(config);
        PowerReport targetPower = powerReports.get(player.getUuid());
        if (targetPower == null) {
            targetPower = powerPipeline.evaluate(
                    VanillaPlayerSnapshotFactory.capture(player), powerCaps(config));
        }
        Collection<ServerPlayerEntity> nearbyPlayers = server.getPlayerManager().getPlayerList().stream()
                .filter(other -> other.getServerWorld() == world)
                .filter(other -> other.isAlive()
                        && other.interactionManager.getGameMode() == GameMode.SURVIVAL)
                .filter(other -> other.squaredDistanceTo(player)
                        <= square(config.multiplayer().groupRadius()))
                .toList();
        ArrayList<Double> groupPowers = new ArrayList<>(nearbyPlayers.size());
        for (ServerPlayerEntity member : nearbyPlayers) {
            PowerReport memberPower = powerReports.get(member.getUuid());
            if (memberPower == null && member == player) {
                memberPower = targetPower;
            }
            if (memberPower == null) {
                memberPower = powerPipeline.evaluate(
                        VanillaPlayerSnapshotFactory.capture(member), powerCaps);
            }
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
        double playTimeProgress = playTimeProgress(
                playerPlayTimeTicks(player),
                playerDeathCount(player),
                config.intensity().playTimeHoursForMaximum() * 60.0 * 60.0 * 20.0,
                config.intensity().deathPlayTimePenalty(),
                config.intensity().maximumDeathPlayTimePenalty());
        double experienceProgress = clamp01(
                (Math.max(0, player.experienceLevel) + Math.max(0.0F, player.experienceProgress))
                        / config.intensity().experienceLevelForMaximum());

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
                state.adaptiveIntensity(),
                player.getMaxHealth(),
                player.getHealth(),
                player.getAbsorptionAmount(),
                state.damageWithin(tick, Math.min(200L, recentWindow)),
                recentDeath,
                sinceDeath,
                entityLoad,
                planningLoad,
                playTimeProgress,
                experienceProgress);
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
                        state.adaptiveIntensity(),
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

    /**
     * Evaluates each online player once per observation cycle. Previously every player re-evaluated
     * all nearby players, making a four-player group perform up to sixteen inventory/attribute
     * scans. The cache is local to this call and never retains entity or world references.
     */
    private Map<UUID, PowerReport> evaluateOnlinePlayers(
            MinecraftServer server,
            ThreatDirectorConfig config) {
        Map<PowerAxis, Integer> powerCaps = powerCaps(config);
        Map<UUID, PowerReport> result = new HashMap<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.isSpectator()) {
                result.put(player.getUuid(), powerPipeline.evaluate(
                        VanillaPlayerSnapshotFactory.capture(player), powerCaps));
            }
        }
        return result;
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

    private static long playerPlayTimeTicks(ServerPlayerEntity player) {
        return Math.max(0L, player.getStatHandler().getStat(
                Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME)));
    }

    private static int playerDeathCount(ServerPlayerEntity player) {
        return Math.max(0, player.getStatHandler().getStat(
                Stats.CUSTOM.getOrCreateStat(Stats.DEATHS)));
    }

    /**
     * Applies a persistent, bounded recovery penalty to vanilla play time. The vanilla statistic
     * continues to advance normally, but deaths reduce how much of it contributes to progression:
     * 85% remains after one death, 70% after two, and the retained share never drops below 35%.
     */
    static double playTimeProgress(long playTimeTicks, int deathCount) {
        return playTimeProgress(
                playTimeTicks,
                deathCount,
                PLAY_TIME_FOR_FULL_PRESSURE_TICKS,
                PLAY_TIME_DEATH_PENALTY_PER_DEATH,
                PLAY_TIME_MAX_DEATH_PENALTY);
    }

    static double playTimeProgress(
            long playTimeTicks,
            int deathCount,
            double ticksForMaximum,
            double penaltyPerDeath,
            double maximumPenalty) {
        double rawProgress = clamp01(Math.max(0L, playTimeTicks)
                / Math.max(1.0, ticksForMaximum));
        double boundedDeaths = Math.max(0, deathCount);
        double penalty = Math.min(
                Math.max(0.0, maximumPenalty),
                boundedDeaths * Math.max(0.0, penaltyPerDeath));
        return clamp01(rawProgress * (1.0 - penalty));
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
