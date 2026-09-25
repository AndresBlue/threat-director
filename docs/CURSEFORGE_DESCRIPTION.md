# Threat Director 2.0

## Adaptive hostile AI for Minecraft 1.20.1

Threat Director makes vanilla hostile mobs more aware, coordinated, and responsive without replacing Minecraft's combat system. Difficulty is created through player-specific Threat, positioning, investigation, reinforcements, tactical roles, and environmental pressure rather than simply multiplying mob health or damage.

**Full documentation:** [Threat Director Wiki](https://threat-director.fandom.com/wiki/Threat_Director_Wiki)

The wiki contains the complete configuration reference, commands, mob behavior details, troubleshooting guides, and multiplayer documentation.

## What the mod does

### Per-player Threat

Threat Director evaluates every player independently using equipment, health, combat stress, environment, XP, playtime, recent encounters, deaths, and nearby teammates. New or under-equipped players remain close to vanilla until they cross the configured activation threshold.

Playtime and XP prevent permanent difficulty avoidance through deliberate under-equipping. Death removes part of the effective playtime contribution so a player who loses their gear has room to recover.

In multiplayer, detection range, aggro, abilities, and encounter pressure are scaled against the actual target player. Nearby teammates increase group pressure with diminishing returns rather than a simple linear multiplier.

### Awareness

Hostile mobs can receive additional movement, sound, or light senses alongside their normal AI. Sound-aware mobs can investigate real server events such as:

- mining and block destruction;
- explosions;
- doors and containers;
- combat, deaths, and projectile attacks.

An investigation identifies a location, not perfect knowledge of the player. Movement and light detection still depend on line of sight.

### Traits

Some mobs receive rare persistent combinations of systemic Traits:

- **Coward** — retreats while badly injured;
- **Berserker** — becomes faster and stronger when wounded;
- **Leader** — recruits nearby idle hostiles;
- **Regenerative** — heals over time;
- **Swift** — moves faster;
- **Resilient** — gains armor and knockback resistance.

Traits create behavioral variety without requiring large numbers of visual mob variants.

### Blood Moons

Every several nights, the world can schedule a Blood Moon. A subtle daytime omen warns that the next night will be different. During the event, sleep cannot skip the night and Threat Director uses its full high-pressure profile: stronger encounters, larger reinforcement waves, extended targeting, and the complete set of configured mob abilities.

Ordinary difficulty is intentionally more readable than a Blood Moon. The event interval, omens, sleep rule, and pressure profile are configurable.

### Coordinated encounters

The Director recruits suitable loaded mobs first and can fill a controlled deficit with safe spawning. Encounters move through stalking, positioning, engaging, searching, climax, relief, and retreating phases.

Mobs can receive tactical roles such as pursuer, flanker, ranged unit, breacher, support, blocker, or ambusher. Population, pathfinding, reinforcement work, and encounter budgets are bounded to keep hordes coordinated without recalculating every route every tick.

## Enhanced hostile mobs

### Skeletons

Long-range, player-specific detection; predictive aim; optional homing arrows; slowness, poison, nausea, or blindness arrows; and retreat dashes. Effect durations, range, accuracy, and attack cadence scale with difficulty, Threat, and Blood Moon pressure.

### Zombies

Persistent aggro, nearby aggro propagation, lightning-backed horde calls, combat reinforcements, difficulty-scaled weapons and tools, rare shields, and guarded tool-based block breaking. Shields can block attacks with vanilla feedback, while axe hits temporarily disable them.

### Creepers

Silent movement in the full profile, faster rear pursuit, randomized fuse timing, target memory through temporary line-of-sight loss, and coordinated breaching. A suitable nearby creeper may open a viable shelter wall for the rest of the horde, subject to `mobGriefing`, claims, spawn protection, loaded chunks, world borders, and block safety checks.

### Spiders

Configurable speed, jump, regeneration, and strength variants; rare cooldown-limited cobweb attacks; and limited hatchlings on death. Cave-spider offspring are uncommon during ordinary gameplay and become more likely under extreme pressure.

### Endermen

Shared retaliation when nearby hostile mobs are attacked, two-block combat posture, and bounded anti-pillar teleport behavior.

### Drowned

More aggressive nighttime swimming, extended targeting, underwater approach dashes, configurable trident and Riptide behavior, and trident impacts that can break occupied boats. Daytime and Easy behavior remain close to vanilla.

Other hostile families receive bounded, configurable thematic behavior where supported.

## Server-side and multiplayer compatibility

Threat Director is designed for singleplayer, integrated servers, dedicated servers, and mixed-client multiplayer.

The **Vanilla** layer can run entirely server-side and controls encounter direction, targeting, group scaling, and tactical AI. The **Modded** layer controls Awareness, Traits, Blood Moons, the HUD, and extraordinary mob abilities. Both layers can be enabled independently.

Players without the client mod can join a modded server and see gameplay changes through normal Minecraft rendering. They do not receive the Threat HUD, Threat Director announcements, debug telemetry, raw translation keys, or command suggestions.

## Configuration and administration

Threat Director generates a commented JSON5 configuration with three sections:

- **General:** Threat calculation, progression, playtime, XP, performance, telemetry, and integrations;
- **Vanilla:** Server-sided encounter and tactical behavior;
- **Modded:** Awareness, Traits, Blood Moons, HUD, and mob-specific abilities.

Operators can inspect Threat, force or clear values for testing, control the HUD, enable OP-only chat logging, inspect encounters and performance, force test scenes, and reload the configuration.

See the [wiki](https://threat-director.fandom.com/wiki/Threat_Director_Wiki) for the complete command list and a property-by-property explanation of the configuration file.

## Performance and safety

A staged Performance Governor reduces decision frequency, candidate scans, pathfinding, reinforcement work, and new encounter admission when server load rises.

Destructive behavior is restricted by `mobGriefing`, spawn protection, loaded chunks, world borders, safe block rules, and compatible claim systems. The mod does not force chunks for combat, and invalid configuration reloads preserve the last known-good snapshot.

## Requirements

- Minecraft 1.20.1
- Fabric Loader 0.18.4 or newer compatible release
- Fabric API 0.92.11+1.20.1
- Java 17

## Installation

1. Install Fabric Loader and Fabric API for Minecraft 1.20.1.
2. Place `threat-director-mc1.20.1-2.0.jar` in the server or integrated instance `mods` folder.
3. Start once to generate `config/threatdirector.json5`.
4. Review the generated comments and choose Vanilla-only or the complete experience.

For detailed setup, behavior explanations, commands, and troubleshooting, visit the [Threat Director Wiki](https://threat-director.fandom.com/wiki/Threat_Director_Wiki).
