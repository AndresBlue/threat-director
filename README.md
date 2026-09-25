# Threat Director

Threat Director is a server-authoritative Fabric mod for Minecraft 1.20.1 that turns ordinary
hostile mobs into adaptive, coordinated pressure. Difficulty comes from awareness, positioning,
roles, pursuit, reinforcements, environmental danger, and per-player progression—not arbitrary
health or damage inflation.

## Platform

- Minecraft 1.20.1
- Fabric Loader 0.18.4
- Fabric API 0.92.11+1.20.1
- Yarn mappings 1.20.1+build.10
- Java 17

Threat Director registers no blocks, items, attributes, or entity types. Its gameplay is controlled
by the server, so remote players can join without installing the mod. A client only needs the JAR to
render the optional threat HUD.

## Version 2.0

The release artifact is `threat-director-mc1.20.1-2.0.jar`.

Version 2.0 adds a fully documented, strict JSON5 configuration divided into exactly three sections:

- `general`: threat calculation, performance, telemetry, and integrations;
- `vanilla`: clientless AI, encounters, targeting, multiplayer scaling, and tactical coordination;
- `modded`: Awareness, Traits, Blood Moons, HUD, and extraordinary mob abilities.

Each layer can be enabled independently:

| Experience | `general.enabled` | `vanilla.enabled` | `modded.enabled` |
| --- | ---: | ---: | ---: |
| Completely vanilla | `false` | any | any |
| Server-side AI only | `true` | `true` | `false` |
| Abilities without Director encounters | `true` | `false` | `true` |
| Full Threat Director | `true` | `true` | `true` |

Every generated property has an English comment. Missing properties, unknown names, invalid ranges,
and unsafe relationships reject the entire reload while the last known-good configuration remains
active. Legacy schema 3 `threatdirector.json` files migrate without deleting the original.

See the [complete configuration guide](docs/CONFIGURATION.md).

## Core systems

### Per-player threat

Threat is calculated independently for every player from equipment, combat capability, supplies,
health, recent damage and death, environment, group context, server load, playtime, and XP. Playtime
prevents deliberately remaining under-equipped forever, while death partially reduces its effective
contribution so a player who loses their gear can recover.

The result is smoothed into a 0–100 value. Additional targeting and abilities begin scaling at the
configured activation threshold instead of immediately punishing a new player.

### Adaptive encounters

The Director recruits loaded natural mobs first and can optionally spawn vanilla mobs to fill a
budget. Encounters move through Stalking, Positioning, Engaging, Searching, Climax, Relief, and
Retreating. A shared blackboard assigns Vanguard, Flanker, Harasser, and Interceptor roles, reserves
sectors, limits simultaneous melee attackers, and staggers pathfinding work.

Night, darkness, caves, difficulty, threat, group size, and Blood Moons control encounter chance,
population, quorum, reinforcement waves, replanning cadence, and cooldown. Every part of that curve
is configurable in 2.0.

### Awareness

Hostile mobs may spawn with additional movement, sound, or light senses. These senses are independent
and persistent, so rare combinations emerge without creating new entity variants.

Sound-sensitive mobs react to real server GameEvents from mining, explosions, doors, containers,
combat, deaths, and projectile shots. Attributable sounds can reveal the responsible player; other
sounds only create a location to investigate. Movement and light require line of sight, and sneaking
reduces movement detection.

### Traits

Rare hostile mobs can receive persistent combinations of:

- `COWARD`: retreats at low health;
- `BERSERKER`: gains speed and damage while wounded;
- `LEADER`: recruits nearby idle hostiles;
- `REGENERATIVE`: heals periodically;
- `SWIFT`: receives a movement bonus;
- `RESILIENT`: gains armor and knockback resistance.

Assignment is deterministic from the world seed and entity UUID. Reloading a chunk cannot reroll an
entity for a better or worse combination.

### Blood Moons

A deterministic Blood Moon occurs every 4–6 nights by default. An indirect omen appears during the
scheduled day. The night cannot be skipped by sleeping and restores the full apocalypse profile:
larger encounters, stronger reinforcements, maximum targeting ranges, and complete mob abilities.
The calendar persists across restarts and can be fully configured.

## Mob behavior

- **Skeletons:** extended per-player range, predictive aim, optional homing arrows, configurable
  status effects, faster attack cadence, and retreat dashes. Defaults reach 128 blocks during a
  Blood Moon; effect duration is bounded to 1–30 seconds with an approximate 8-second mean.
- **Zombies:** persistent aggro, nearby alerts, lightning-backed horde calls, bounded reinforcements,
  tools, shields with real block feedback, axe shield disable, and guarded block breaking. Pickaxes
  and shields remain specialist rolls rather than common equipment.
- **Spiders:** configurable speed, jump, regeneration, and strength; rare cobweb placement on hit;
  and a reduced chance to produce cave-spider hatchlings on death.
- **Creepers:** silent full-profile movement, rear-approach speed, randomized fuse timing, dash, target
  memory, and coordinated breaching. Only one suitable nearby creeper is assigned to a viable wall,
  and every destructive action respects `mobGriefing`, spawn protection, chunks, and claims.
- **Endermen:** shared retaliation, a two-block combat posture, and bounded anti-pillar teleporting.
- **Drowned:** aggressive night swimming, extended targeting, underwater dash, trident and Riptide
  profiles, and optional boat destruction on eligible drowned-trident impact.
- **Other hostiles:** bounded family behaviors for witches and raiders, aerial fire mobs, guardians,
  drowned and husks, swarms, ravagers, and piglin brutes.

Easy remains close to vanilla, Normal applies partial pressure, ordinary Hard uses the reduced 2.0
curve, and Blood Moons restore the intentionally brutal peak. All ranges and major behaviors remain
per-player so an inexperienced guest does not inherit a geared host's full pressure.

## Multiplayer and server-side operation

Threat, targeting range, health relief, and progression remain individual. Nearby players can join a
shared encounter with diminishing returns and a configurable group multiplier. Each mob has exactly
one encounter owner. On an integrated LAN server, the host is the automatic anchor; guests can still
be explicit OP command targets.

Unmodded clients receive normal vanilla entities, effects, sounds, explosions, and movement. The
server checks channel support before sending HUD data, so the optional client overlay never becomes
a connection requirement.

## Performance and safety

The Performance Governor monitors MSPT and degrades work in stages. It limits planning operations,
candidates, path attempts, encounter admission, and decision cadence. Critical load can suspend new
encounters and release AI ownership safely.

The mod never forces chunks for combat. Block changes require loaded positions, world-border access,
`mobGriefing`, spawn protection, and a positive claim decision. Unknown claim authority fails closed.

## Commands

Common commands:

```text
/threatdirector threat [player]
/threatdirector threat set <0..100> [player]
/threatdirector threat clear [player]
/threatdirector hud [on|off] [player]
/threatdirector logging on|off|status [player]
/threatdirector debug status|intensity [player]
/threatdirector debug start|cancel|scene|phase ...
/threatdirector debug roles|sectors|budget
/threatdirector debug breach simulate
/threatdirector debug config reload
/threatdirector debug integrations|performance|preset|telemetry
/threatdirector debug performance reset
```

Mutations, another player's information, HUD control, and debug operations require permission level
2. `/threatdirector logging on` streams session diagnostics only to subscribed OP players.

See the [complete command reference](docs/COMMANDS.md).

## Installation

1. Install Minecraft 1.20.1, Fabric Loader 0.18.4, Fabric API 0.92.11+1.20.1, and Java 17.
2. Place `threat-director-mc1.20.1-2.0.jar` in the server or integrated instance `mods` folder.
3. Start once to generate `config/threatdirector.json5`.
4. Choose Vanilla-only or the full experience and run `/threatdirector debug config reload`.

## Development

```powershell
.\gradlew.bat build
```

The remapped artifact is generated in `build/libs/`.

Additional references:

- [Configuration](docs/CONFIGURATION.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Commands](docs/COMMANDS.md)
- [Manual acceptance tests](docs/MANUAL_TESTS.md)
- [CurseForge description](docs/CURSEFORGE_DESCRIPTION.md)
- [2.0 changelog](docs/CHANGELOG_2.0.md)
