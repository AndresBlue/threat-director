# Threat Director 2.0

## Complete configuration

- Added `config/threatdirector.json5` schema 4 with an English comment before every property.
- Added exactly three root categories: `general`, `vanilla`, and `modded`.
- Added independent master switches for Vanilla-only, Modded-only, and complete operation.
- Added per-feature toggles and values for HUD, Awareness, Traits, Blood Moon, skeletons, zombies,
  spiders, creepers, endermen, drowned, and other hostile families.
- Exposed family and difficulty targeting ranges, horde chance and size, equipment and shields,
  effects, hatchlings, cobwebs, breaching, and movement/sound/light detection.
- Moved the full encounter curve into configuration: difficulty and environmental hazard, contextual
  threat, encounter chance, population, quorum, melee pressure, reinforcements, replanning, and
  cooldown for Easy, Normal, Hard, and Blood Moon.

## Safety and operation

- Missing properties and unknown names are rejected instead of silently receiving Gson defaults.
- Added cross-field validation for ranges, minimum/maximum pairs, probabilities, durations, and hard
  population caps.
- Config reload is transactional and preserves the last known-good snapshot on failure.
- Legacy schema 3 `threatdirector.json` migrates automatically without deleting the original.
- Breach simulation and OP chat logging now honor their configuration toggles.
- Disk telemetry and integration settings document when a restart is required.
- Blood Moon message selection repairs persisted indexes when the configured omen pool shrinks.

## Language and localization

- All bundled documentation, generated config help, and metadata are written in English.
- Added complete client-side English and Spanish localization for Blood Moon announcements, sleep
  denial, administrative commands, status values, errors, and OP debug-chat labels.
- Each player receives in-game text in the language selected by their own Minecraft client.
- Every Spanish locale bundled by Minecraft 1.20.1 uses the Spanish translation pack.
- Unmodded clients receive no Threat Director announcements, HUD packets, debug messages, or command
  suggestions; all server-side AI and encounter behavior remains compatible and invisible.

## Final balance fixes

- Reduced cave-spider hatchling, zombie pickaxe, and zombie shield probabilities.
- Added player-style zombie shield blocking, block sound feedback, damage negation, and the vanilla
  ten-second axe disable interaction.
- Added persistent creeper target memory and safe coordinated wall-breacher assignment.

## Compatibility

- Vanilla-only keeps Director encounters, targeting, and coordination fully server-side for unmodded
  clients.
- Modded-only can run abilities without automatic Director encounters.
- The HUD remains the only presentation feature that requires the JAR on the viewing client.
- Minecraft 1.20.1, Fabric Loader 0.18.4, Fabric API 0.92.11, and Java 17.
