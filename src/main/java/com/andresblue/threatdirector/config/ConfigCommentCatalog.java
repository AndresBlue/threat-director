package com.andresblue.threatdirector.config;

import java.util.Map;

/** English help text embedded beside every JSON5 option for CurseForge/server users. */
final class ConfigCommentCatalog {
    private static final Map<String, String> EXACT = Map.ofEntries(
            Map.entry("schemaVersion", "Internal config format version. Do not edit this value."),
            Map.entry("general", "GENERAL: global switch, threat calculation, performance, telemetry and integrations."),
            Map.entry("vanilla", "VANILLA: server-authoritative AI and encounter changes; unmodded clients can join."),
            Map.entry("modded", "MODDED: extraordinary senses, traits, events and mob abilities."),
            Map.entry("general.enabled", "Master switch for Threat Director. false restores vanilla gameplay."),
            Map.entry("general.preset", "Base balance preset. VANILLA_PLUS is the supported value."),
            Map.entry("general.intensity", "Controls per-player threat calculation and progression."),
            Map.entry("general.performance", "Hard work budgets and automatic degradation thresholds."),
            Map.entry("general.telemetry", "Optional local NDJSON event log; independent from OP chat logging."),
            Map.entry("general.integrations", "Compatibility behavior for optional adapters and non-vanilla mobs."),
            Map.entry("vanilla.enabled", "Master switch for the vanilla-like Director and AI layer."),
            Map.entry("vanilla.adaptiveDirectorEnabled", "Allows automatic encounters based on each player's threat and environment."),
            Map.entry("vanilla.enhancedTargetingEnabled", "Extends detection/aggro without adding new attacks or status effects."),
            Map.entry("vanilla.director", "Automatic encounter scan, activation and cooldown rules."),
            Map.entry("vanilla.encounter", "Vanilla mob recruitment, spawning, population and phase limits."),
            Map.entry("vanilla.tactical", "Formation, positioning, pathfinding and melee coordination limits."),
            Map.entry("vanilla.multiplayer", "Nearby-player grouping and diminishing group scaling."),
            Map.entry("vanilla.targeting", "Maximum per-player target ranges at full threat."),
            Map.entry("vanilla.pressure", "Environment and Normal/Hard/Blood Moon encounter curves."),
            Map.entry("vanilla.pressure.easy", "Vanilla-like EASY caps; automatic encounters remain disabled."),
            Map.entry("vanilla.pressure.normal", "NORMAL encounter population, reinforcement and cadence curve."),
            Map.entry("vanilla.pressure.hard", "Ordinary HARD curve outside a Blood Moon."),
            Map.entry("vanilla.pressure.bloodMoon", "Full apocalypse encounter curve during a Blood Moon."),
            Map.entry("vanilla.breachSimulation", "Safe OP-only breach planner diagnostics; never edits the world."),
            Map.entry("modded.enabled", "Master switch for every extraordinary feature below."),
            Map.entry("modded.hud", "Optional threat bar; rendering requires Threat Director on that client."),
            Map.entry("modded.awareness", "Additional movement, sound and light senses assigned per mob."),
            Map.entry("modded.traits", "Rare persistent systemic trait combinations."),
            Map.entry("modded.bloodMoon", "Persistent Blood Moon calendar, omens and sleep rules."),
            Map.entry("modded.skeleton", "Predictive aim, homing, arrow effects and retreat dash."),
            Map.entry("modded.zombie", "Equipment, shields, sticky aggro, hordes, reinforcements and mining."),
            Map.entry("modded.spider", "Potion buffs, on-hit cobwebs and death hatchlings."),
            Map.entry("modded.creeper", "Stealth, rear pursuit, fuse variation, dash and coordinated breaching."),
            Map.entry("modded.enderman", "Shared aggro, two-block posture and anti-pillar teleport."),
            Map.entry("modded.drowned", "Night swimming pressure, tridents, Riptide dash and boat breaking."),
            Map.entry("modded.otherHostiles", "Bounded family abilities for remaining hostile mobs."),
            Map.entry("general.intensity.activationThreat", "Threat threshold (0-100) where per-player extensions begin scaling."),
            Map.entry("general.intensity.playTimeHoursForMaximum", "PLAY_TIME hours required for the maximum time contribution."),
            Map.entry("general.intensity.experienceLevelForMaximum", "XP level required for the maximum experience contribution."),
            Map.entry("general.intensity.deathPlayTimePenalty", "Fraction of effective playtime contribution lost per death."),
            Map.entry("general.intensity.maximumDeathPlayTimePenalty", "Maximum accumulated playtime-contribution loss after deaths."),
            Map.entry("vanilla.encounter.recruitNaturalMobs", "Uses already-loaded natural hostiles before spawning encounter members."),
            Map.entry("vanilla.encounter.directedSpawning", "Allows vanilla mobs to be spawned when recruitment cannot fill an encounter."),
            Map.entry("vanilla.encounter.allowDirectorSpawnedCreepers", "Allows creepers in the Director's spawned population."),
            Map.entry("vanilla.multiplayer.additionalPlayerScale", "Extra encounter scale added per nearby eligible player."),
            Map.entry("vanilla.multiplayer.maxGroupMultiplier", "Absolute cap on multiplayer encounter scaling."),
            Map.entry("vanilla.breachSimulation.enabled", "Enables the OP-only breach simulation command."),
            Map.entry("general.telemetry.includePlayerUuid", "Includes player UUIDs in local telemetry. Consider privacy before enabling."),
            Map.entry("general.telemetry.operatorChatLoggingEnabled", "Allows OP-only live debug events through /threatdirector logging."),
            Map.entry("general.integrations.enableExternalAdapters", "Loads compatible integration providers discovered at startup."),
            Map.entry("modded.hud.operatorsSeeByDefault", "Shows the bar automatically to OPs whose client has the mod."),
            Map.entry("modded.hud.allowCommandOptIn", "Allows OPs to change HUD visibility per player with commands."),
            Map.entry("modded.awareness.movementSenseEnabled", "Allows mobs to roll the movement-detection sense."),
            Map.entry("modded.awareness.soundSenseEnabled", "Allows mobs to hear configured real server GameEvents."),
            Map.entry("modded.awareness.lightSenseEnabled", "Allows mobs to detect sufficiently illuminated visible players."),
            Map.entry("modded.traits.cowardEnabled", "Allows COWARD: retreats while below its health threshold."),
            Map.entry("modded.traits.berserkerEnabled", "Allows BERSERKER: gains speed and damage while wounded."),
            Map.entry("modded.traits.leaderEnabled", "Allows LEADER: periodically recruits nearby idle hostiles."),
            Map.entry("modded.traits.regenerativeEnabled", "Allows REGENERATIVE: heals a bounded amount over time."),
            Map.entry("modded.traits.swiftEnabled", "Allows SWIFT: persistent movement-speed bonus."),
            Map.entry("modded.traits.resilientEnabled", "Allows RESILIENT: armor and knockback-resistance bonuses."),
            Map.entry("modded.bloodMoon.announceOmens", "Broadcasts one indirect omen during the scheduled day."),
            Map.entry("modded.bloodMoon.preventSleeping", "Prevents players from skipping an active Blood Moon by sleeping."),
            Map.entry("modded.bloodMoon.omenMessages", "Randomized omen message pool; keep at least one non-blank entry."),
            Map.entry("modded.bloodMoon.nightStartTick", "Day tick where Blood Moon rules start; vanilla night begins at 12000."),
            Map.entry("modded.skeleton.predictiveAim", "Leads moving targets and reduces projectile inaccuracy."),
            Map.entry("modded.skeleton.homingArrows", "Steers eligible skeleton arrows toward their assigned player target."),
            Map.entry("modded.skeleton.effectArrows", "Allows slowness, poison, nausea or blindness payloads."),
            Map.entry("modded.skeleton.retreatDash", "Allows Hard skeletons to dash backward when a target closes in."),
            Map.entry("modded.skeleton.meanEffectDurationTicks", "Approximate mean duration used by the bounded random arrow-effect distribution."),
            Map.entry("modded.zombie.equipmentEnabled", "Allows difficulty-scaled weapons, tools and shields at spawn."),
            Map.entry("modded.zombie.shieldBlockingEnabled", "Allows equipped shields to negate hits with vanilla block feedback."),
            Map.entry("modded.zombie.stickyAggroEnabled", "Keeps an acquired player target until the zombie or player dies."),
            Map.entry("modded.zombie.nearbyAggroEnabled", "Alerts nearby zombies when one enters combat."),
            Map.entry("modded.zombie.lightningHordesEnabled", "Allows first contact to summon a cosmetic-lightning zombie group."),
            Map.entry("modded.zombie.reinforcementsEnabled", "Allows bounded reinforcement calls during combat."),
            Map.entry("modded.zombie.blockBreakingEnabled", "Allows suitable zombie tools to break preferred blocks under mobGriefing/claim guards."),
            Map.entry("modded.zombie.axeShieldDisableTicks", "Time a melee axe hit disables a zombie shield, matching player-style counterplay."),
            Map.entry("modded.spider.statusBuffsEnabled", "Applies the configured difficulty-tier potion amplifiers."),
            Map.entry("modded.spider.cobwebOnHitEnabled", "Allows rare cobweb placement after a successful hit."),
            Map.entry("modded.spider.hatchlingsEnabled", "Allows cave-spider hatchlings when a spider dies."),
            Map.entry("modded.creeper.silentSteps", "Suppresses creeper step sounds in the full apocalypse profile."),
            Map.entry("modded.creeper.rearSpeedBoost", "Accelerates creepers while their target is facing away."),
            Map.entry("modded.creeper.randomFuse", "Randomizes each ignition fuse between the configured minimum ratio and vanilla time."),
            Map.entry("modded.creeper.dashEnabled", "Allows the Blood Moon approach dash."),
            Map.entry("modded.creeper.coordinatedBreaching", "Assigns one nearby creeper to open a viable shelter wall for the horde."),
            Map.entry("modded.creeper.maximumBreakableHardness", "Highest wall hardness considered by the breach coordinator."),
            Map.entry("modded.creeper.breachAssignmentSeconds", "How long one creeper owns a breach assignment before reevaluation."),
            Map.entry("modded.enderman.allyAggroEnabled", "Nearby endermen may retaliate when the player damages another mob."),
            Map.entry("modded.enderman.twoBlockPostureEnabled", "Allows the combat posture used under a two-block ceiling."),
            Map.entry("modded.enderman.antiPillarTeleportEnabled", "Allows teleport response when a player remains on a low pillar."),
            Map.entry("modded.drowned.swimSpeedEnabled", "Applies the configured night swimming-speed multiplier."),
            Map.entry("modded.drowned.tridentsEnabled", "Allows the configured trident spawn chance."),
            Map.entry("modded.drowned.riptideEnabled", "Allows spawned tridents to receive Riptide and underwater dash behavior."),
            Map.entry("modded.drowned.boatBreakingEnabled", "Allows eligible drowned trident hits to break boats."),
            Map.entry("modded.otherHostiles.witchesAndIllagers", "Enables bounded support pressure for witches and raiders."),
            Map.entry("modded.otherHostiles.aerialFireMobs", "Enables approach pressure for blazes, ghasts and phantoms."),
            Map.entry("modded.otherHostiles.guardians", "Enables guardian pressure rules."),
            Map.entry("modded.otherHostiles.drownedAndHusks", "Enables the generic drowned/husk family pressure in addition to dedicated drowned rules."),
            Map.entry("modded.otherHostiles.swarms", "Enables coordination for silverfish, endermites, slimes and magma cubes."),
            Map.entry("modded.otherHostiles.brutes", "Enables bounded close-pressure behavior for ravagers and piglin brutes."));

    private ConfigCommentCatalog() {
    }

    static String describe(String path) {
        String exact = EXACT.get(path);
        if (exact != null) {
            return exact;
        }
        String key = path.substring(path.lastIndexOf('.') + 1);
        String readable = humanize(key);
        if (key.equals("normal")) return "NORMAL-difficulty values for this feature.";
        if (key.equals("hard")) return "Ordinary HARD-difficulty values outside a Blood Moon.";
        if (key.equals("bloodMoon")) return "HARD-difficulty values while a Blood Moon is active.";
        if (key.equals("enabled")) return "Enables or disables this section without deleting its tuned values.";
        if (key.endsWith("Enabled")) return "Toggles " + humanize(key.substring(0, key.length() - 7)) + ".";
        if (key.contains("Chance")) return "Probability (0.0-1.0) for " + readable + ".";
        if (key.endsWith("Ticks")) return "Time in ticks (20 ticks = 1 second): " + readable + ".";
        if (key.endsWith("Seconds")) return "Time in seconds: " + readable + ".";
        if (key.endsWith("Multiplier")) return "Multiplier for " + humanize(key.substring(0, key.length() - 10)) + "; 1.0 means unchanged.";
        if (key.contains("Range") || key.contains("Radius") || key.contains("Distance")) {
            return "Distance in blocks: " + readable + ".";
        }
        if (key.contains("Speed")) return "Movement/impulse tuning value: " + readable + ".";
        if (key.endsWith("Ratio") || key.endsWith("Fraction")) return "Fraction (0.0-1.0): " + readable + ".";
        if (key.endsWith("Amplifier")) return "Vanilla status-effect amplifier for " + readable + "; -1 disables it.";
        if (key.contains("Minimum") || key.contains("Maximum") || key.startsWith("min") || key.startsWith("max")) {
            return "Configurable lower/upper bound: " + readable + ".";
        }
        if (key.endsWith("Cap")) return "Hard safety/balance cap: " + readable + ".";
        if (key.endsWith("Ids")) return "Minecraft identifiers allowed by this section.";
        if (key.equals("threatCosts")) return "Per-entity budget cost; every allowed mob ID needs a positive value.";
        return "Sets " + readable + ".";
    }

    private static String humanize(String key) {
        return key.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase();
    }
}
