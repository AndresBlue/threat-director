# Threat Director 2.0 Manual Acceptance Matrix

## Test setup

1. Run `.\gradlew.bat clean build` and verify
   `build/libs/threat-director-mc1.20.1-2.0.jar`.
2. Test an integrated world and a dedicated Minecraft 1.20.1 server with Fabric Loader 0.18.4,
   Fabric API, and Java 17.
3. Keep one remote client without Threat Director and one with the JAR for server-side/HUD checks.
4. Enable `/threatdirector logging on` as an OP and retain the server log.
5. Use a disposable world for explosions, cobwebs, zombie mining, and high-population tests.

## BOOT — startup, configuration, and compatibility

### BOOT-01 — minimal installation

Start integrated and dedicated servers with only Fabric API and Threat Director.

Expected: `Threat Director 2.0 initialized` appears, no mixin errors occur, schema 4 is generated,
and no external content dependency is required.

### BOOT-02 — unmodded client

Connect an unmodded Fabric/vanilla client to dedicated and LAN servers.

Expected: the client connects and receives server-side AI behavior. It does not render the threat bar
and does not fail because the HUD channel is absent.

### BOOT-03 — reload and shutdown

Reload config, close an integrated world, open another, and stop a dedicated server.

Expected: listeners and goals do not duplicate. Runtime, HUD, sound buffers, and telemetry close
without retaining players or worlds.

### BOOT-04 — self-documenting config

Remove `threatdirector.json5` from a stopped disposable instance and start it again.

Expected: the file contains `schemaVersion`, `general`, `vanilla`, and `modded`; every property has a
preceding English comment, and units and probability conventions are clear.

### BOOT-05 — independent layers

Test `vanilla=true/modded=false`, `vanilla=false/modded=true`, and both enabled. Reload between cases
and materialize new mobs.

Expected: Vanilla-only keeps encounters and targeting without special effects. Modded-only keeps
Awareness, Traits, Blood Moon, and abilities without automatic Director encounters. Both enabled
provides the complete experience. Unmodded clients connect in every case.

### BOOT-06 — transactional rejection

With a valid snapshot active, remove `modded.enabled`, add `modded.enabeld`, invert a minimum/maximum
pair, and run `/threatdirector debug config reload` after each edit.

Expected: every invalid candidate is rejected with an identifiable path, gameplay keeps the previous
snapshot, and restoring the file permits a successful reload.

### BOOT-07 — schema 3 migration

Place a schema 3 `threatdirector.json` in a clean instance and start it.

Expected: the original JSON remains untouched, a documented schema 4 JSON5 appears, compatible
values are preserved, and new sections receive defaults.

## AWR — Awareness

### AWR-01 — assignment and persistence

Spawn a large hostile sample in Easy, Normal, and Hard. Inspect `threatdirector_awareness_*` tags,
save, unload, and reload representative entities.

Expected: Easy assigns no extra senses. Normal and Hard produce uncommon independent combinations,
with Hard more frequent. Every entity retains exactly its original result, including an empty roll.

### AWR-02 — movement and light

Test a movement-sensitive mob while walking, sprinting, and sneaking at several distances. Repeat
with a light-sensitive mob in darkness and near different light sources.

Expected: movement requires line of sight and minimum velocity; sprint reaches farther and sneaking
reduces range. Light below the threshold does not trigger. Creative and spectator players are never
selected.

### AWR-03 — a cave wakes up

Place sound-sensitive mobs behind navigable geometry. Mine, explode, open/close doors and containers,
and fight, waiting for memory expiry between cases.

Expected: real GameEvents wake only mobs within the configured radius. Attributed events acquire the
responsible player; unattributed events create a location investigation without wall vision.

### AWR-04 — limits

Generate repeated noise around many mobs and observe MSPT/profiler output.

Expected: buffers remain bounded, equivalent pulses deduplicate, events expire, scans are staggered,
player checks are capped, and no chunks are forced.

## TRT — Traits

### TRT-01 — prevalence and persistence

Spawn large samples by difficulty and inspect `threatdirector_trait_*` tags across chunk reloads.

Expected: Easy has no traits, Normal has rare traits, and Hard has more combinations. UUID/seed-based
selection does not reroll.

### TRT-02 — individual behavior

Test each trait separately using tagged disposable mobs.

Expected:

- Coward retreats below its threshold and stops after recovery;
- Berserker gains configured speed/damage while wounded;
- Leader recruits only bounded idle allies;
- Regenerative heals at its interval without exceeding max health;
- Swift and Resilient use stable non-stacking modifiers.

### TRT-03 — ownership and cleanup

Recruit trait mobs into an encounter, disable Traits, reload, then stop the server.

Expected: Director ownership wins over conflicting flee/leader actions, modifiers become inert or are
removed, and no entity/world references survive shutdown.

## BM — Blood Moon

### BM-01 — deterministic scheduling

Advance several worlds with fixed seeds through multiple events and restart between them.

Expected: each next event falls within the configured interval, remains stable after restart, and
advances only after the scheduled night passes.

### BM-02 — omen and sleep

Reach a scheduled day, observe the omen, reconnect players, then attempt to sleep after night start.

Expected: exactly one configured English omen is broadcast. Sleep is denied with the English Blood
Moon message, and the night cannot be skipped.

### BM-03 — profile switch

Compare ordinary Hard and Blood Moon with the same threat, location, and group.

Expected: Blood Moon selects the configured maximum population, reinforcement, targeting, and mob
ability profiles; ordinary Hard remains the reduced curve.

### BM-04 — edited omen pool

Schedule and save an event with a large omen list, shrink it to one entry, and reload.

Expected: the persisted message index is repaired deterministically and the world tick does not crash.

## MOB — mob-specific behavior

### MOB-01 — skeletons

Measure target acquisition, projectile cadence, leading, homing, effect frequency/duration, and dash
in Easy, Normal, ordinary Hard, and Blood Moon.

Expected: Easy remains vanilla-like; higher tiers scale per player. Effects stay inside configured
bounds, homing remains target-owned, and dash obeys distance and cooldown.

### MOB-02 — zombie aggro and hordes

Let a zombie acquire a high-threat player behind geometry, hit one member of a cluster, and repeat
first-contact trials.

Expected: sticky aggro persists until death, nearby zombies acquire the correct player, lightning
hordes stay within configured chance and size, and spawned members begin with the intended target.

### MOB-03 — zombie equipment and shields

Collect spawn statistics for tools, pickaxes, and shields. Attack shield zombies from front/back and
with ordinary weapons and axes.

Expected: specialist equipment remains rare. Successful frontal blocks negate damage and play the
shield sound. Back attacks and bypass damage work. Melee axes disable the shield for the configured
duration and play break feedback.

### MOB-04 — zombie mining

Give zombies shovels, axes, swords, and pickaxes behind suitable and unsuitable blocks.

Expected: only preferred, tool-suitable blocks are broken. No drops appear. Bedrock, block entities,
protected areas, unloaded chunks, and excessive hardness are denied.

### MOB-05 — spiders

Measure buffs, cobweb placement, and hatchlings across difficulty profiles and threat values.

Expected: ordinary cobwebs remain rare and cooldown-limited. Ordinary Hard usually creates at most
one cave spider and less often than Blood Moon. Easy remains vanilla-like.

### MOB-06 — endermen

Damage mobs near endermen, fight under a two-block ceiling, and remain on a short pillar.

Expected: alerts respect chance/radius/cap; posture changes only in eligible combat; pillar response
waits for confirmation and obeys cooldown.

### MOB-07 — creeper pursuit and breaching

Turn away from a creeper, lose line of sight inside several shelters, and place multiple creepers
around a viable wall.

Expected: rear speed and fuse follow the selected profile. Target memory survives temporary LOS loss.
Only one nearby creeper receives a breach assignment, approaches a useful wall, and ignites within
the configured distance.

### MOB-08 — drowned and boats

Compare day/night and Easy/Normal/Hard/Blood Moon in open water. Test drowned and player tridents
against occupied and empty boats.

Expected: only eligible night drowned receive speed, targeting, dash, trident/Riptide, and boat
behavior. Day, Easy, and player projectiles remain unaffected.

### MOB-09 — other hostile families

Test each family toggle with representative mobs.

Expected: disabling a family removes its thematic pressure while common targeting follows the
active Vanilla/Modded ownership rules. Dedicated modules do not receive duplicate special behavior.

## ENC — encounters and multiplayer

### ENC-01 — phase lifecycle

Force each documented scene and observe transitions, orders, and cleanup.

Expected: the controller reaches the requested state when legal, telemetry records transitions, and
Relief/Retreating releases ownership without deleting natural mobs.

### ENC-02 — population sources and budget

Test natural recruitment only, directed spawning only, both enabled, and both disabled.

Expected: natural mobs are preferred, deficit spawning respects IDs and caps, an unaffordable minimum
rolls back atomically, and killed members never refund lifetime budget.

### ENC-03 — reinforcement bounds

Hold an encounter in Engaging/Climax with visibility and budget, then remove each prerequisite.

Expected: waves respect size, interval, maximum waves, concurrent cap, living-member quorum,
visibility, budget, loaded chunks, and governor limits.

### ENC-04 — four-player group

Use four players with very different equipment and threat inside and outside `groupRadius`.

Expected: group pressure uses diminishing returns and never exceeds `maxGroupMultiplier`. Each player
retains their own target range and ability scaling. Every mob has one owner.

### ENC-05 — integrated host authority

Open a LAN world and let a guest enter danger alone, then join with the host and use explicit OP
commands on the guest.

Expected: only the host anchors automatic encounters. Explicit authorized commands may target the
guest. The guest's personal threat is not replaced by the host's.

## PERF — performance and telemetry

### PERF-01 — staged degradation

Drive synthetic or real MSPT through each configured threshold.

Expected: level changes require configured persistence, limits shrink monotonically, high levels deny
new encounters, and recovery requires sustained healthy load.

### PERF-02 — emergency behavior

Trigger consecutive critical samples during ordinary play and after `performance reset`.

Expected: safety level 6 can activate in both cases, releases encounter ownership safely, and does
not falsify measured MSPT.

### PERF-03 — telemetry and OP logging

Test chat logging with normal players and OPs, disk telemetry on/off, UUID inclusion, queue pressure,
and shutdown.

Expected: only subscribed OPs receive chat events; disk output follows privacy settings; bounded
queues do not block gameplay; closing flushes and releases resources.

## SAFE — destructive safety

### SAFE-01 — protection matrix

Repeat zombie mining, cobweb placement, and creeper breaching with `mobGriefing=false`, spawn
protection, `PROTECTED` claims, `UNKNOWN` claims, unloaded/border-invalid positions, and finally a
permitted location.

Expected: every denied case remains unchanged. `UNKNOWN` fails closed. Only a loaded, permitted,
non-sensitive location may be modified.

### SAFE-02 — Peaceful and distress

Switch to Peaceful and separately reduce a player below the configured health threshold after recent
damage or death.

Expected: no hostile encounter population starts in Peaceful. Distress and death memory provide
relief according to configuration rather than increasing pressure.

## Release criteria

- `build` and 139+ JUnit tests pass.
- Integrated and dedicated servers start without mixin errors.
- All bundled documentation and user-facing text are English.
- JSON5 documents every property and rejects missing/unknown fields transactionally.
- Vanilla-only and Modded-only operate independently.
- Easy remains close to vanilla; ordinary Hard uses the reduced curve; Blood Moon restores the peak.
- Awareness reacts to real events and Traits persist without rerolls.
- Multiplayer keeps threat and targeting per player with unique ownership.
- No chunk forcing, infinite reinforcement loops, stacking modifiers, or retained world references.
