# Threat Director 2.0 Architecture

## Platform and authority

Threat Director targets Minecraft 1.20.1, Fabric Loader 0.18.4, Fabric API 0.92.11+1.20.1,
Yarn 1.20.1+build.10, and Java 17.

Every gameplay decision runs on the server thread. Remote clients may connect without the mod; only
the optional HUD renderer requires the client JAR. Threat Director registers no blocks, items,
attributes, packets required for login, or entity types.

Awareness and Traits persist through vanilla command tags. Blood Moon state uses `PersistentState`.
Director observations and encounter ownership live in `ThreatDirectorRuntime`.

## Configuration authority

`ConfigManager` publishes an immutable schema 4 snapshot from `config/threatdirector.json5`.
`DocumentedConfigWriter` emits one English comment before every property. The reader compares the
candidate's complete shape with the schema, rejecting missing and unknown fields before Gson can
silently apply primitive defaults.

Validation accumulates cross-field violations. A reload replaces the active snapshot only when every
check passes. Legacy schema 3 JSON migrates once without deleting the original file.

The root contains three categories:

- `general`: player threat, performance budgets, telemetry, and integrations;
- `vanilla`: Director encounters, population, coordination, targeting, and pressure curves;
- `modded`: HUD, Awareness, Traits, Blood Moon, and extraordinary mob behavior.

`ConfigAccess` supplies the same immutable snapshot to pure policies and live Fabric adapters.
Vanilla and Modded master switches are independent.

## Runtime layers

```text
Fabric lifecycle / game events / entity loading
        ├── BloodMoonModule ── PersistentState + sleep gate
        ├── AwarenessModule ── sound buffers + target/investigation goals
        ├── MobTraitsModule ── persistent selection + trait goals
        ├── mob-specific policies and mixins
        └── ThreatDirectorRuntime
                  ├── PlayerObservationService ── ThreatHudService
                  ├── PerformanceGovernor
                  ├── EncounterManager ── EncounterController
                  ├── EncounterPopulationService + GroupBlackboard + planners
                  ├── MobOrderStore + temporary goals
                  └── TelemetryRecorder + OP chat logger
```

No module treats references to unloaded entities as durable authority. Encounter controllers retain
UUIDs. Awareness stores bounded per-world buffers and weak entity sets. Entity-load hooks reinstall
goals after NBT materialization.

## Threat and player observations

`PlayerObservationService` samples each player and reuses expensive power reports where safe.
`AdventureIntensityCalculator` combines:

- equipped armor, weapons, enchantments, and inventory capability;
- night, darkness, dimension, biome, depth, and distance from the safe area;
- preparation since recent combat or encounters;
- health, recent damage, and death memory;
- entity and planner load;
- persistent playtime and XP level;
- nearby eligible players with diminishing returns.

The 0–100 result uses exponential smoothing and hysteresis. Playtime and XP create inevitable
progression pressure. Death reduces a configurable portion of effective playtime pressure so losing
equipment does not permanently trap a player at endgame intensity.

Threat remains per player. A lightly equipped guest does not inherit a host's long-range aggro or
ability scaling merely because both are online.

## Encounter pressure

`EncounterPressure` combines threat with Minecraft difficulty and environmental hazard. The schema 4
pressure section controls:

- base hazard per difficulty;
- night, darkness, and cave contributions;
- adaptive context span;
- encounter chance multipliers;
- population and environmental growth fractions;
- contextual minimum and melee fractions;
- reinforcement size, waves, and interval;
- replanning and cooldown curves.

Easy has zero automatic encounter chance. Normal applies partial pressure. Ordinary Hard uses a
reduced curve. Blood Moon selects a separate maximum profile.

Explicit OP overrides control the threat input but do not bypass population caps, health relief,
spawn safety, chunks, claims, or the governor.

## Encounter ownership and state machine

`EncounterManager` enforces one owner per mob and bounded active encounters. Each
`EncounterController` owns:

- one primary player plus optional nearby participants;
- a lifetime Threat Budget;
- a member roster with origin and cost;
- a `GroupBlackboard` containing roles, sectors, and positions;
- phase state and timers;
- reinforcement reservations and cooldowns.

The phase sequence is:

```text
STALKING → POSITIONING → ENGAGING ↔ SEARCHING → CLIMAX → RELIEF → RETREATING
```

Transitions account for visibility, distance, contact loss, elapsed time, deaths, player distress,
and explicit administrative testing. Relief and retreat release temporary AI ownership cleanly.

## Population and tactical planning

`EncounterPopulationService` recruits allowed natural mobs first. Directed spawning fills only the
remaining deficit when enabled. Candidates must be loaded, within the world border, outside protected
areas, valid for spawning, affordable, and navigable.

Threat Budget is lifetime spending. Killed or retired members do not refund cost. Reinforcements can
refill concurrent room but cannot create infinite total pressure.

The group blackboard assigns bounded tactical roles and positions. Planner budgets limit candidates,
path attempts, and operations per tick. Only a configured number of melee members may attack
simultaneously, creating the visual mass of a horde without every entity recalculating the same path.

## Awareness

`ServerWorldGameEventMixin` observes the server's real GameEvent stream. Relevant families are
classified into mining, explosion, door, container, and combat sounds. Each has configurable hearing
range, attribution radius, and lifetime.

Sound buffers are bounded per world and deduplicate equivalent pulses. Target scans are staggered and
bounded. Attributed sound may assign the responsible player. Unattributed sound only creates an
investigation position.

Movement and light use line of sight. Movement considers sprint, sneak, and velocity; light considers
block light and configured range growth. Creative and spectator players are never valid targets.

Mobs managed by a Director encounter yield to its ownership and do not run competing Awareness goals.

## Traits

`MobTraitsModule` covers vanilla and conventional modded entities in `SpawnGroup.MONSTER`. Selection
is deterministic from world seed and entity UUID and is marked even when no trait is chosen, blocking
reload rerolls.

`TraitMaintenanceGoal` owns stable attribute modifiers with fixed UUIDs and removes them when the
feature becomes inactive. `CowardFleeGoal` yields to Director-managed mobs. Leaders recruit only idle,
unmanaged allies and have bounded scans.

## Blood Moon

`BloodMoonPersistentState` stores initialization, world seed, event ordinal, scheduled night,
announcement state, and message selection. Only the Overworld advances the global calendar; other
dimensions query it through the server.

The event interval and omen selection are deterministic. During the scheduled day, one configured
omen is broadcast. From the configured night tick until the next day, sleeping is rejected. Shrinking
the configured message pool repairs a stale persisted index deterministically.

The active flag selects Blood Moon pressure and ability profiles rather than merely changing visuals.

## Mob-specific modules

### Skeletons

Higher-priority ranged goals provide extended target range while retaining vanilla navigation and
strafing. Mixins replace projectile aim only when predictive behavior is enabled. Homing arrows keep
a bounded owner/target relationship. Effect selection and duration are pure policy functions.

### Zombies

Entity mixins maintain sticky target tags, propagate nearby aggro, trigger bounded horde calls,
equip difficulty profiles, handle shield blocking, and invoke guarded block breaking. Axe hits disable
shields for a configured duration. Reinforcement spawn attempts are bounded and never force chunks.

### Spiders and endermen

Difficulty profiles produce immutable rules. Spider effects are refreshed without stacking permanent
modifiers. Web placement and hatchlings pass destructive guards. Enderman posture and pillar response
use target-specific threat and bounded timers.

### Creepers

`ApocalypticCreeperPolicy` provides pure rear-speed, fuse, dash, and memory rules.
`CreeperBreachCoordinator` caches one assignment per player and world, examines viable shelter rays,
and nominates one nearby creeper. The actual explosion remains a vanilla creeper action governed by
world rules and protection.

### Drowned

Night profiles control a temporary swim-speed modifier, target acquisition, underwater dash,
trident/Riptide equipment, and boat impact. Day and Easy remove the modifier and return to vanilla.
Player tridents and unrelated projectiles are unaffected.

### Other hostile families

All vanilla `Monster` entities may receive common per-player targeting. Supported families also gain
bounded thematic pressure. Dedicated mob modules retain ownership of their special combat logic.

## Multiplayer

Each player keeps an observation state and dynamic target range. Shared encounters select one primary
owner, scale with diminishing returns, and index every mob under exactly one controller.

On an integrated server, the host is the only automatic encounter anchor. OP commands may explicitly
target guests. Dedicated servers rotate candidate work to avoid permanently favoring the first player.

## Performance

The Performance Governor maps measured load to levels 0–6. Each level adjusts decision interval,
candidate limits, path work, and encounter admission. Emergency degradation may release encounters
to vanilla AI. Recovery requires sustained healthy samples.

No world or entity operation runs asynchronously. Telemetry writes through a bounded queue; gameplay
state remains on the server thread.

## Compatibility and destructive safety

Optional providers use a transactional integration SPI. Provider failure is isolated. Claim
authority has three states; `UNKNOWN` fails closed.

Block mutations require:

- server-side execution;
- loaded chunks and world-border access;
- `mobGriefing`;
- spawn protection clearance;
- no sensitive block entity;
- permitted hardness and tool policy;
- `UNPROTECTED` claim authority.

The breach command is read-only and cannot modify the world.
