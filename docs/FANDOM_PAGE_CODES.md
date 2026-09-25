# Threat Director Fandom Wiki Page Codes

This document contains the grouped page structure for the Threat Director Fandom Wiki. Each section is a complete MediaWiki source block for one page. Create the page using the title shown above the block, then paste the block into Fandom's Source Editor.

The structure intentionally groups related content into substantial pages. Section anchors make it possible to link directly to a mob, configuration layer, or gameplay system without creating dozens of short articles.

## Main Page

```mediawiki
<mainpage-leftcolumn-start />

<div style="text-align:center; padding:12px;">
'''Threat Director Wiki'''<br />
''The community encyclopedia for adaptive hostile AI, coordinated encounters, mob abilities, configuration, and multiplayer systems.''<br />
'''Minecraft 1.20.1''' · '''Fabric Loader 0.18.4''' · '''Threat Director 2.0'''
</div>

== Welcome ==

Threat Director transforms vanilla hostile mobs into adaptive, coordinated threats. This wiki documents Threat, Awareness, Traits, Blood Moons, encounters, hostile mob abilities, configuration, commands, and server compatibility.

== Start here ==

* [[Getting Started]]
* [[Threat System]]
* [[Director Systems]]
* [[Hostile Mobs]]
* [[Configuration]]
* [[Commands and HUD]]
* [[Multiplayer and Compatibility]]
* [[Performance and Troubleshooting]]

== Featured systems ==

{| class="wikitable"
! System !! Description
|-
| [[Director Systems#Awareness|Awareness]] || Mobs investigate movement, sound, and light.
|-
| [[Director Systems#Traits|Traits]] || Rare systemic combinations change mob behavior.
|-
| [[Blood Moon]] || Recurring nights use the full pressure profile.
|-
| [[Director Systems#Encounter_Director|Encounter Director]] || Hostile mobs coordinate through phases and roles.
|-
| [[Threat System]] || Every player receives an independent Threat value.
|}

== Hostile mobs ==

* [[Hostile Mobs#Skeleton|Skeleton]] · [[Hostile Mobs#Zombie|Zombie]] · [[Hostile Mobs#Creeper|Creeper]]
* [[Hostile Mobs#Spider|Spider]] · [[Hostile Mobs#Enderman|Enderman]] · [[Hostile Mobs#Drowned|Drowned]]
* [[Hostile Mobs#Other Hostiles|Other Hostiles]]

<mainpage-endcolumn />
<mainpage-rightcolumn-start />

== About this wiki ==

The Threat Director Wiki is a community-maintained reference for players, server administrators, testers, and contributors.

== Latest version ==

'''Threat Director 2.0''' · [[Version History]] · [[Version History#2.0|Changelog 2.0]]

== Contributing ==

* [[Project:Wiki rules|Wiki rules]]
* [[Project:Policies|Policies]]
* [[Performance and Troubleshooting#Debugging_and_Telemetry|Debugging and Telemetry]]
* [[Version History]]

<mainpage-endcolumn />
[[Category:Threat Director]]
__NOTOC__
__NOEDITSECTION__
```

## Getting Started

```mediawiki
= Getting Started =

Getting Started explains how to install Threat Director and prepare a world or server for its first session.

== Requirements ==

* Minecraft 1.20.1
* Fabric Loader 0.18.4 or newer compatible release
* Fabric API 0.92.11+1.20.1
* Java 17

== Installation ==

# Install Fabric Loader for Minecraft 1.20.1.
# Install Fabric API.
# Place the Threat Director JAR in the server or client <code>mods</code> folder.
# Start the game or server once to generate <code>config/threatdirector.json5</code>.
# Review the generated comments before changing values.

The current release is '''Threat Director 2.0'''.

== Server and client installation ==

The Vanilla layer can run entirely on the server. Players without the mod can join without seeing Threat Director UI or messages.

Install the mod on both server and client to use the optional Threat HUD, localized messages, and operator presentation tools.

== First steps ==

* Read [[Threat System]] to understand per-player scaling.
* Read [[Configuration]] before changing difficulty values.
* Use [[Commands and HUD#Commands|Commands]] to inspect or test encounters as an operator.
* Enable [[Performance and Troubleshooting#Debugging_and_Telemetry|Debugging and Telemetry]] when reporting a problem.

== Recommended first test ==

Start a Normal or Hard world, observe a night in a dangerous area, and compare behavior before and after changing one configuration property. Avoid changing many population and performance values at once.

[[Category:Guides]]
[[Category:Threat Director]]
```

## Threat System

```mediawiki
= Threat System =

'''Threat''' is Threat Director's per-player measure of pressure and danger. It ranges from 0 to 100 and is calculated independently for each player.

== Main inputs ==

* Equipment, armor, weapons, enchantments, and inventory capability
* Health, recent damage, combat stress, and distress
* Night, darkness, caves, depth, biome, and dimension
* Playtime and XP progression
* Recent deaths and recovery state
* Preparation and recent encounter history
* Nearby players with diminishing group returns
* Server load and planner availability

== Progression protection ==

Playtime and XP prevent a player from avoiding long-term difficulty by staying deliberately unequipped. Death removes part of the effective playtime contribution, allowing recovery after losing equipment.

== Threat bands ==

{| class="wikitable"
! Band !! Typical behavior
|-
| Low || Vanilla-like behavior and limited Director pressure.
|-
| Tactical || Awareness, positioning, and limited coordination begin to matter.
|-
| High || Stronger pursuit, abilities, reinforcements, and encounter planning.
|-
| Extreme || Full ordinary Hard pressure. Blood Moons use an even stronger profile.
|}

== Adaptive calculation ==

Threat is smoothed over time to avoid sudden oscillation. A forced operator override can be used for testing, but adaptive calculation resumes when the override is cleared.

== Multiplayer ==

Threat is evaluated per target player. A well-equipped player does not automatically give an inexperienced teammate the same detection range or mob ability pressure.

See [[Configuration#General|General Configuration]] for calculation values.

[[Category:Gameplay]]
[[Category:Threat Director]]
```

## Director Systems

```mediawiki
= Director Systems =

Director Systems are the core gameplay systems that make hostile mobs react to the player and to one another.

== Awareness ==
<span id="Awareness"></span>

'''Awareness''' gives hostile mobs additional senses alongside their vanilla AI.

=== Awareness profiles ===

* '''Movement''' — detects visible player movement.
* '''Sound''' — reacts to configured server-side GameEvents.
* '''Light''' — detects sufficiently illuminated players when line of sight permits it.

=== Sound events ===

Sound-aware mobs can investigate mining, explosions, doors, containers, combat, damage, and projectile attacks.

An investigation points the mob toward the event location. It does not reveal the player's exact position through walls.

Awareness is assigned persistently and is bounded by Threat, difficulty, line of sight, and performance limits.

== Traits ==
<span id="Traits"></span>

'''Traits''' are rare persistent systemic modifiers assigned to hostile mobs.

{| class="wikitable"
! Trait !! Behavior
|-
| Coward || Retreats while badly wounded.
|-
| Berserker || Gains speed and damage while wounded.
|-
| Leader || Recruits nearby idle hostiles.
|-
| Regenerative || Restores a bounded amount of health over time.
|-
| Swift || Receives a movement-speed bonus.
|-
| Resilient || Receives armor and knockback-resistance bonuses.
|}

Traits can combine, but their prevalence is limited so ordinary encounters remain readable.

== Encounter Director ==
<span id="Encounter_Director"></span>

The '''Encounter Director''' organizes loaded hostile mobs into coordinated encounters.

=== Encounter phases ===

* Stalking
* Positioning
* Engaging
* Searching
* Climax
* Relief
* Retreating

=== Director responsibilities ===

* Select a target player or group.
* Calculate a threat budget.
* Assign tactical roles and sectors.
* Reuse suitable loaded mobs.
* Schedule bounded reinforcements.
* Replan around terrain and performance.
* Release ownership during Relief or Retreating.

Encounter pressure is scaled per player and adjusted for nearby teammates with diminishing returns.

== Tactical Roles ==
<span id="Tactical_Roles"></span>

Tactical roles allow hostile mobs to behave as a group instead of as identical attackers.

{| class="wikitable"
! Role !! Function
|-
| Pursuer || Closes distance and maintains pressure.
|-
| Flanker || Approaches from a side or alternate route.
|-
| Ranged || Applies projectile pressure from distance.
|-
| Breacher || Opens a safe, viable route through soft blocks.
|-
| Support || Recruits, reinforces, or enables nearby hostiles.
|-
| Blocker || Controls an exit or denies a tactical route.
|-
| Ambusher || Waits for a favorable approach or line of sight.
|}

Roles are selected using Threat, mob type, terrain, equipment, and current encounter phase.

[[Category:Gameplay]]
[[Category:Encounters]]
```

## Blood Moon

```mediawiki
= Blood Moon =

'''Blood Moon''' is a recurring high-pressure night event.

== Scheduling ==

The world schedules a Blood Moon every several nights using a configurable interval and persistent world state. An indirect omen is announced during the day before the event.

== Rules ==

* The active night cannot be skipped by sleeping.
* The full apocalypse pressure profile is enabled.
* Population, reinforcements, Awareness, and abilities become more intense.
* Drowned become especially dangerous in oceans and rivers.

== Omens ==

Built-in omens are localized for English and supported Spanish client locales. Custom configuration messages remain exactly as written by the administrator.

== Configuration ==

Blood Moon settings are located under <code>modded.bloodMoon</code>. Administrators can control scheduling, announcements, sleep prevention, night start, and the omen pool.

[[Category:Gameplay]]
[[Category:Events]]
[[Category:Threat Director]]
```

## Hostile Mobs

```mediawiki
= Hostile Mobs =

Threat Director enhances hostile mobs with additional abilities, behaviors, equipment, and traits. All sections below are part of this page so readers can compare mob families without navigating through many short articles.

== Skeleton ==
<span id="Skeleton"></span>

Skeletons are long-range hostile mobs enhanced by Threat Director.

=== Abilities ===

* Extended target detection
* Predictive aim against moving players
* Homing arrows
* Slowness, poison, nausea, and blindness arrows
* Retreat movement
* Backward dash behavior

Effects, range, accuracy, and attack cadence scale with difficulty, Threat, and Blood Moon pressure.

=== Counterplay ===

Use cover, break line of sight, approach from multiple directions, and force skeletons to reposition. Shield timing and terrain remain valuable even when arrows are enhanced.

== Zombie ==
<span id="Zombie"></span>

Zombies are persistent melee and support threats.

=== Abilities ===

* Sticky aggro after acquiring a player
* Nearby zombie aggro propagation
* Lightning-backed horde calls
* Combat reinforcements
* Difficulty-scaled weapons, tools, and shields
* Tool-based block breaking
* Player-style shield blocking

An axe attack can disable a zombie shield for the configured duration. Successful blocks negate damage and play shield feedback.

Pickaxe and shield spawn probabilities are deliberately limited to avoid excessive early-game pressure.

== Creeper ==
<span id="Creeper"></span>

Creepers can become coordinated breach units during high-pressure encounters.

=== Abilities ===

* Silent movement
* Increased rear-approach speed
* Variable fuse duration
* Persistent target memory after losing line of sight
* Shelter and wall analysis
* Coordinated wall-breacher assignment

A selected creeper may ignite against a viable wall so the rest of the horde can reach the player. Breaching respects <code>mobGriefing</code>, claims, spawn protection, world borders, loaded chunks, and block safety rules.

== Spider ==
<span id="Spider"></span>

Spiders can receive rare ability combinations and produce limited offspring.

=== Abilities ===

* Speed
* Regeneration
* Jump enhancement
* Strength
* On-hit cobweb placement
* Spider hatchlings on death
* Rare cave-spider hatchlings

Hatchling and cobweb probabilities are reduced during ordinary gameplay. Stronger variants are concentrated in high Threat and Blood Moon encounters.

== Enderman ==
<span id="Enderman"></span>

Endermen gain conditional group-awareness and anti-pillar behavior.

=== Abilities ===

* Anger when nearby hostile mobs are damaged
* Shared pursuit of a player target
* Movement through two-block spaces
* Conditional teleportation against simple pillar strategies

Enderman behavior remains dependent on difficulty, Threat, line of sight, and safe teleport destinations.

== Drowned ==
<span id="Drowned"></span>

Drowned provide dedicated nighttime pressure in oceans, rivers, and other water areas.

=== Abilities ===

* Increased nighttime swimming speed
* Higher trident pressure
* Riptide behavior
* Underwater dash approaches
* Trident impacts that can destroy occupied boats

Drowned become especially dangerous during Blood Moons. Their behavior is configurable under <code>modded.drowned</code>.

== Other Hostiles ==
<span id="Other_Hostiles"></span>

Threat Director can apply bounded thematic behavior to additional hostile mob families.

Depending on mob type, difficulty, Threat, and performance, these behaviors may include ranged repositioning, ambush movement, support behavior, area denial, conditional pursuit, and group pressure.

The goal is to create readable differences without replacing the base identity of every vanilla mob.

== Cross-mob scaling ==

Mob abilities are scaled using difficulty, per-player Threat, Blood Moon state, encounter phase, and Performance Governor limits. Easy remains close to vanilla, while ordinary Hard and Blood Moon events unlock the strongest behavior.

[[Category:Mobs]]
[[Category:Hostile mobs]]
[[Category:Gameplay]]
```

## Configuration

```mediawiki
= Configuration =

Threat Director generates its documented configuration at <code>config/threatdirector.json5</code>.

JSON5 behaves like JSON but permits comments. Every generated property has an English explanation immediately above it, including its purpose, unit, and safety limits.

== Quick start: choose an experience ==

The three master switches are <code>general.enabled</code>, <code>vanilla.enabled</code>, and <code>modded.enabled</code>. Do not delete disabled sections; keeping them preserves your tuning for later use.

{| class="wikitable"
! Result !! <code>general.enabled</code> !! <code>vanilla.enabled</code> !! <code>modded.enabled</code>
|-
| No Threat Director gameplay || <code>false</code> || Any || Any
|-
| Vanilla-like server AI only || <code>true</code> || <code>true</code> || <code>false</code>
|-
| Extraordinary abilities without automatic Director encounters || <code>true</code> || <code>false</code> || <code>true</code>
|-
| Complete experience || <code>true</code> || <code>true</code> || <code>true</code>
|}

Vanilla-only is recommended when an administrator wants smarter pressure without changing extraordinary mob abilities or requiring client installation. The complete experience still accepts unmodded clients; only optional presentation features require the client JAR.

After editing the file, run:

<syntaxhighlight lang="text">
/threatdirector debug config reload
</syntaxhighlight>

Reloading is transactional. If a property is missing, unknown, malformed, or violates a safety relationship, the entire candidate is rejected and the last known-good snapshot remains active.

== File conventions ==

* Probabilities use decimals: <code>0.25</code> means 25%.
* Minecraft runs at 20 ticks per second; <code>200</code> ticks equals 10 seconds.
* Distances and radii are measured in blocks.
* Vanilla effect amplifiers are zero-based: <code>0</code> is level I and <code>1</code> is level II.
* In fields that allow it, <code>-1</code> disables an effect.
* Identifiers use <code>namespace:path</code>, such as <code>minecraft:zombie</code>.
* Keep <code>schemaVersion: 4</code>; Threat Director owns schema evolution.
* Do not remove properties or invent names. The strict reader reports both cases.

The file exposes gameplay modules, public behavior, probabilities, ranges, durations, and balance curves. Internal invariants such as attribute UUIDs, numerical tolerances, anti-loop batches, and ownership bookkeeping remain fixed so valid configuration cannot corrupt entity persistence or stall the server thread.

== General ==
<span id="General"></span>

The <code>general</code> category controls systems shared by the Vanilla and Modded layers.

=== <code>general.enabled</code> ===

This is the absolute gameplay switch. Setting it to <code>false</code> releases active encounters, clears the HUD, and makes entity extensions inert while preserving all configured values.

=== <code>general.intensity</code> ===

This section controls the per-player 0–100 Threat calculation.

{| class="wikitable"
! Properties !! Purpose
|-
| <code>sampleIntervalTicks</code>, <code>smoothingAlpha</code>, <code>hysteresisPoints</code> || Sampling frequency and stability. Higher alpha reacts faster; higher hysteresis prevents band flapping.
|-
| <code>maxIntensity</code>, <code>activationThreat</code> || Global ceiling and the point where targeting and abilities begin scaling.
|-
| <code>minimumHealthRatio</code>, <code>recentDamageWindowTicks</code>, <code>recentDeathCooldownTicks</code> || Relief for injured or recently defeated players.
|-
| <code>defenseCap</code>, <code>offenseCap</code>, <code>sustainCap</code>, <code>groupCap</code>, <code>contextCap</code>, <code>stressPenaltyCap</code> || Maximum contribution from each signal family.
|-
| <code>playTimeHoursForMaximum</code>, <code>experienceLevelForMaximum</code> || Progression pace for time and XP pressure.
|-
| <code>deathPlayTimePenalty</code>, <code>maximumDeathPlayTimePenalty</code> || Partial playtime-pressure recovery after death and its accumulated cap.
|}

Threat is never blindly copied between players. Equipment, health, XP, playtime, environment, and distress remain individual. Group scaling applies only when eligible players are near the same encounter.

=== <code>general.performance</code> ===

Performance settings bound planner work:

* <code>workBudgetMicrosPerTick</code> and <code>maxPlannerOperationsPerTick</code> limit work per tick.
* <code>degradeAfterTicks</code> requires sustained load before reducing quality.
* <code>recoverAfterTicks</code> requires sustained stability before recovery.
* <code>level1TickTimeMs</code> through <code>level6TickTimeMs</code> must be strictly increasing.

As the Performance Governor rises, it reduces decision frequency, candidate counts, pathfinding, and new encounter admission. Raising these limits may increase visible intensity but also increases server MSPT risk.

=== <code>general.telemetry</code> ===

{| class="wikitable"
! Option !! Effect
|-
| <code>enabled</code> || Writes local NDJSON telemetry inside the world save. Changing the writer requires a server restart.
|-
| <code>operatorChatLoggingEnabled</code> || Allows or blocks <code>/threatdirector logging</code> for OP players.
|-
| <code>queueCapacity</code>, <code>flushBatchSize</code> || Maximum queue and write batch. The batch cannot exceed the queue.
|-
| <code>includePlayerUuid</code> || Includes UUIDs in local telemetry. Consider privacy before enabling it.
|}

Chat logging is session-only, OP-only, and independent from disk telemetry.

=== <code>general.integrations</code> ===

<code>enableExternalAdapters</code> loads compatible optional providers at server startup. Keep it disabled for the most conservative compatibility profile. Changing it requires a restart because providers load transactionally once per runtime.

== Vanilla ==
<span id="Vanilla"></span>

The <code>vanilla</code> category contains behavior understandable to vanilla clients and can run entirely server-side.

=== Master switches ===

* <code>enabled</code> enables the category.
* <code>adaptiveDirectorEnabled</code> allows automatic encounters based on Threat and environment.
* <code>enhancedTargetingEnabled</code> extends detection and aggro without enabling special arrows, Traits, hatchlings, fuse changes, or other Modded abilities.

Operators can still inspect Threat while automatic Director behavior is disabled.

=== <code>vanilla.director</code> ===

* <code>scanIntervalTicks</code> controls the interval between candidate scans.
* <code>minimumIntensity</code> sets the minimum Threat for automatic activation.
* <code>encounterChancePerScan</code> sets the chance per eligible scan.
* <code>cooldownMinTicks</code> and <code>cooldownMaxTicks</code> define the random relief window after an encounter.
* <code>requireNightOrDarkness</code> requires a dangerous environmental context.
* <code>allowedDimensions</code> defines an explicit dimension allowlist.

=== <code>vanilla.encounter</code> ===

{| class="wikitable"
! Group !! Properties
|-
| Population || <code>minMobs</code>, <code>maxMobs</code>, <code>hardMaxMobs</code>, <code>maxNearbyHostiles</code>, <code>maxActiveEncounters</code>
|-
| Source || <code>recruitNaturalMobs</code>, <code>directedSpawning</code>, <code>allowDirectorSpawnedCreepers</code>
|-
| Space || <code>recruitRadius</code>, <code>spawnMinDistance</code>, <code>spawnMaxDistance</code>, <code>maxPursuitDistance</code>
|-
| Phases || <code>stalkingMinTicks</code>, <code>stalkingMaxTicks</code>, <code>positioningMaxTicks</code>, <code>engagingMaxTicks</code>, <code>reliefTicks</code>, <code>contactLossTicks</code>
|-
| Composition || <code>allowedMobIds</code>, <code>threatCosts</code>
|}

Natural loaded mobs are recruited first when enabled. Directed vanilla spawning only fills a deficit. <code>hardMaxMobs</code> is validated to an absolute ceiling of 128, and <code>maxNearbyHostiles</code> must cover at least the ordinary maximum.

Every <code>allowedMobIds</code> entry needs a positive matching key in <code>threatCosts</code>. Spent budget is not recycled when a mob dies, preventing infinite reinforcement loops.

=== <code>vanilla.tactical</code> ===

This section controls formation radii, candidate limits, path attempts, replanning interval, target movement required for replanning, and simultaneous melee attackers. Roles and orders continue using vanilla navigation and attacks. The limits prevent large hordes from recalculating every route in the same tick.

=== <code>vanilla.multiplayer</code> ===

* <code>groupRadius</code> defines the distance at which players share an encounter context.
* <code>additionalPlayerScale</code> defines extra encounter pressure per nearby eligible player.
* <code>maxGroupMultiplier</code> is the hard cap on multiplayer scaling.

The model uses diminishing returns so three or four players do not trivialize an encounter without creating an absurd linear mob multiplier.

=== <code>vanilla.targeting</code> ===

These are the maximum ranges at Threat 100 for skeletons, zombies, and generic <code>Monster</code> mobs in Normal and Hard. Actual range interpolates upward only after <code>activationThreat</code>, protecting new or underdeveloped players. When a family's Modded section is active, its family-specific range takes priority.

=== <code>vanilla.pressure</code> ===

This section exposes the encounter curve:

* Easy, Normal, and Hard base difficulty hazard
* Night, darkness, and cave hazard
* Contextual Threat span
* Population fractions and environmental growth
* Minimum horde and melee fractions
* Reinforcement minimum, fraction, waves, and interval
* Tactical cadence and cooldown multipliers
* Separate ordinary Hard and Blood Moon profiles

Easy automatic encounter chance remains zero. Normal applies partial pressure. Ordinary Hard uses the reduced 2.0 curve. Blood Moon restores the full apocalypse profile.

=== <code>vanilla.breachSimulation</code> ===

This is an OP diagnostic tool only. <code>enabled</code> controls the command; ray distance, block count, hardness, and spawn radius constrain the plan. Simulation never changes blocks. The same spawn-protection radius also guards real destructive Modded abilities.

== Modded ==
<span id="Modded"></span>

<code>modded.enabled</code> disables every extraordinary feature below as a group. Each subsection has its own <code>enabled</code>, and each major ability has a separate toggle.

=== <code>modded.hud</code> ===

* <code>enabled</code> permits synchronization and rendering.
* <code>operatorsSeeByDefault</code> automatically shows the bar to OPs with the client mod.
* <code>allowCommandOptIn</code> allows per-player visibility changes through commands.

The server checks channel support before sending HUD data. An unmodded client receives nothing and is not disconnected.

=== <code>modded.awareness</code> ===

Movement, sound, and light senses roll independently when a hostile mob is first assigned a profile. Normal and Hard have separate probabilities.

Movement settings cover walking range, sprinting range, sneak reduction, and minimum speed. Light settings cover minimum block light, base range, and range gained per light level.

Sound uses real server GameEvents:

* Mining: <code>block_destroy</code>
* Explosions: <code>explode</code>
* Doors: <code>block_open</code> and <code>block_close</code>
* Containers: <code>container_open</code> and <code>container_close</code>
* Combat: <code>entity_damage</code>, <code>entity_die</code>, and <code>projectile_shoot</code>

Each family has an audible range, player-attribution radius, and memory lifetime. An unattributed sound creates only an investigation position; it does not grant omniscient player knowledge.

Detection values affect existing mobs that already own the sense. Assignment chance changes affect new or previously unassigned mobs. Persisted entities are never rerolled by reloading the file.

=== <code>modded.traits</code> ===

Independent toggles control <code>COWARD</code>, <code>BERSERKER</code>, <code>LEADER</code>, <code>REGENERATIVE</code>, <code>SWIFT</code>, and <code>RESILIENT</code>. Normal and Hard profiles configure first, second, and third trait probability, maximum traits, health thresholds, flee speed, damage, regeneration, recruitment, movement, armor, knockback resistance, and particle cadence.

Trait combinations are deterministic and persistent. Disabling a trait makes its behavior inert; new probabilities apply to newly assigned mobs.

=== <code>modded.bloodMoon</code> ===

<code>minimumIntervalNights</code> and <code>maximumIntervalNights</code> define deterministic spacing between events. <code>announceOmens</code>, <code>preventSleeping</code>, <code>nightStartTick</code>, and <code>omenMessages</code> control presentation and sleep behavior. The current scheduled event persists; changed intervals govern future scheduling.

=== <code>modded.skeleton</code> ===

Separate toggles control predictive aim, homing, status-effect arrows, and retreat dashes. Values cover Normal, Hard, and Blood Moon range; attack interval; effect chance; minimum, mean, and maximum duration; homing strength; dash velocity; and cooldown bounds.

=== <code>modded.zombie</code> ===

Separate toggles control equipment, shield blocking, sticky aggro, nearby alerts, lightning hordes, reinforcements, and block breaking. Each difficulty tier controls equipment chance, tool choices, pickaxe and shield rarity, shield block chance, reinforcement caps, and mining cooldown.

Additional values cover target range, axe shield-disable duration, horde chance at activation and maximum Threat, nearby alert radius, and horde size per tier.

Block breaking requires a suitable tool, <code>mobGriefing</code>, loaded safe blocks, world-border access, spawn protection, and claim permission. It produces no drops to prevent automated resource farms.

=== <code>modded.spider</code> ===

Independent toggles control status buffs, on-hit cobwebs, and death hatchlings. Tier settings expose effect amplifiers, web cooldown, hatchling count, and hatchling chance. Ordinary and Blood Moon web probabilities have separate activation and maximum-Threat values.

=== <code>modded.creeper</code> ===

Independent toggles control silent steps, rear pursuit, randomized fuse, dash, and coordinated breaching. Values expose ordinary and Blood Moon speed/fuse profiles, target memory, dash impulse and cap, breach distance, wall hardness, assignment lifetime, and fuse delay.

The coordinator reserves one nearby creeper for a viable route-opening wall. Real explosions still respect <code>mobGriefing</code>, spawn protection, chunks, the world border, and claims.

=== <code>modded.enderman</code> ===

Independent toggles control allied retaliation, the two-block combat posture, and anti-pillar teleporting. Each tier exposes alert radius, ally count, chance, pillar confirmation time, and teleport cooldown.

=== <code>modded.drowned</code> ===

Independent toggles control night swim speed, tridents, Riptide, and boat breaking. Normal, Hard, and Blood Moon profiles expose speed multiplier, trident/Riptide chance, enchantment level, dash speed, cooldown, target range, and boat-breaking permission.

Equipment and enchantments roll at spawn. Movement and targeting respond dynamically.

=== <code>modded.otherHostiles</code> ===

Family toggles cover witches and raiders, aerial fire mobs, guardians, drowned and husks, swarms, and brutes. Normal, Hard, and Blood Moon ranges control generic family targeting. These bounded rules do not replace AI installed by other mods.

== Reload behavior ==

{| class="wikitable"
! Change type !! When it takes effect
|-
| Master toggles, active chances, cooldowns, dynamic ranges, caps, messages, HUD, and OP logging || Immediately after a successful reload.
|-
| Awareness, Traits, and spawn equipment assignment || New or newly materialized mobs; persistent entities are not rerolled.
|-
| Increasing the initial target-predicate maximum for an already loaded mob || Guaranteed for mobs loaded after the change; dynamic checks still enforce reductions immediately.
|-
| Disk telemetry and integrations || Server restart.
|-
| Blood Moon interval || When the next event is scheduled; the current persisted event is not rewritten.
|}

== Migration and recovery ==

When upgrading from schema 3:

# The old <code>config/threatdirector.json</code> remains intact.
# Compatible values migrate into <code>general</code> and <code>vanilla</code>.
# New 2.x features receive safe defaults in <code>modded</code>.
# Threat Director writes a fully documented <code>config/threatdirector.json5</code>.

JSON5 becomes authoritative after creation. To regenerate defaults, stop the server, move the JSON5 file to a backup location, and start again.

== Recommended diagnostics ==

<syntaxhighlight lang="text">
/threatdirector debug config reload
/threatdirector threat
/threatdirector logging on
/threatdirector debug status
</syntaxhighlight>

If a session feels too intense, adjust <code>activationThreat</code>, ranges, and chances before raising performance limits. If it feels inactive, confirm the three master switches, the relevant feature toggle, Minecraft difficulty, and whether the player has passed the activation threshold.

[[Category:Configuration]]
[[Category:Server Administration]]
[[Category:Guides]]
```

## Commands and HUD

```mediawiki
= Commands and HUD =

This page documents operator commands, the optional Threat HUD, localization, and client visibility rules.

== Commands ==
<span id="Commands"></span>

Commands are primarily intended for operators and testing.

{| class="wikitable"
! Command !! Purpose
|-
| <code>/threatdirector threat</code> || View the current Threat Level.
|-
| <code>/threatdirector threat set &lt;value&gt;</code> || Force a Threat value from 0 to 100.
|-
| <code>/threatdirector threat clear</code> || Restore adaptive calculation.
|-
| <code>/threatdirector hud on|off</code> || Control the Threat HUD.
|-
| <code>/threatdirector logging on|off</code> || Enable OP-only chat telemetry.
|-
| <code>/threatdirector debug status</code> || Inspect Threat and encounter state.
|-
| <code>/threatdirector debug scene &lt;scene&gt;</code> || Force a test encounter scene.
|-
| <code>/threatdirector debug performance</code> || Inspect Performance Governor state.
|-
| <code>/threatdirector debug config reload</code> || Reload the configuration.
|}

Most commands accept an optional player argument. Unmodded clients do not receive the command tree or suggestions.

== HUD ==
<span id="HUD"></span>

The '''Threat HUD''' is an optional Minecraft-style bar displayed between the XP bar and the hotbar.

The bar uses the Threat Director green-to-purple palette and has dedicated vertical space so it does not overlap the XP bar.

Operators can enable or disable it per player with:

<code>/threatdirector hud on</code>

<code>/threatdirector hud off</code>

The HUD is never sent to an unmodded client.

== Localization ==
<span id="Localization"></span>

Threat Director localizes player-facing messages per client.

=== Supported locales ===

* English: <code>en_us</code>
* Spanish: <code>es_ar</code>, <code>es_cl</code>, <code>es_ec</code>, <code>es_es</code>, <code>es_mx</code>, <code>es_uy</code>, and <code>es_ve</code>

Localized content includes Blood Moon omens, sleep denial, commands, errors, Threat bands, encounter phases, HUD values, and OP debug logging.

An English fallback is supplied when a client does not have the Threat Director language resource. Unmodded clients do not receive Threat Director-specific messages.

[[Category:Commands]]
[[Category:Gameplay]]
[[Category:Server Administration]]
```

## Multiplayer and Compatibility

```mediawiki
= Multiplayer and Compatibility =

Threat Director supports singleplayer, integrated servers, dedicated servers, and mixed-client multiplayer.

== Per-player behavior ==

Threat, detection range, aggro, and ability scaling are calculated against the actual target player. A lightly equipped player is not automatically penalized because another teammate has endgame equipment.

== Group scaling ==

Nearby players contribute to group pressure with diminishing returns. Groups of three or four players receive stronger encounters without simply multiplying every mob or projectile.

== Server-side layer ==

The Vanilla layer can run with only the server mod installed. Unmodded clients continue to see ordinary Minecraft interfaces while the server applies encounter direction, targeting, scaling, and tactical AI.

== Unmodded clients ==

Unmodded clients receive no:

* Threat HUD
* Blood Moon announcements
* Custom sleep messages
* Debug telemetry
* Threat Director commands
* Command suggestions
* Raw translation keys

The server checks channel compatibility before sending Threat Director packets or messages. Modded and unmodded players can coexist on the same server.

== Gameplay compatibility ==

The mod respects vanilla difficulty, <code>mobGriefing</code>, world borders, spawn protection, loaded chunks, protected block entities, and compatible claim systems. External integrations are optional.

[[Category:Multiplayer]]
[[Category:Compatibility]]
[[Category:Server Administration]]
```

## Performance and Troubleshooting

```mediawiki
= Performance and Troubleshooting =

This page combines performance controls, telemetry, debugging, troubleshooting, and frequently asked questions.

== Performance Governor ==
<span id="Performance_Governor"></span>

The Performance Governor protects server stability by adapting Director work to measured load.

As server load rises, it can reduce:

* Decision frequency
* Candidate scans
* Path-planning work
* Reinforcement work
* New encounter creation
* Optional high-cost behavior

Active encounter cleanup continues during degradation. Severe load can temporarily prevent new encounters while preserving safe recovery behavior.

Operators can inspect the current state with <code>/threatdirector debug performance</code>.

== Debugging and Telemetry ==
<span id="Debugging_and_Telemetry"></span>

Threat Director includes optional telemetry for testing and bug reports.

=== Operator chat logging ===

Enable logging with:

<code>/threatdirector logging on</code>

Only OP players with the mod installed can receive the stream. Events may include Threat updates, encounter transitions, mob assignments, reinforcements, Awareness events, and feature actions.

=== Local telemetry ===

General telemetry settings control whether events are written to disk. Chat logging and disk telemetry can be enabled independently.

=== Recommended bug report data ===

Include Minecraft and Fabric versions, Threat Director version, difficulty, dimension, player equipment, approximate Threat, relevant configuration, log lines, and reproduction steps.

== Troubleshooting ==
<span id="Troubleshooting"></span>

=== The mod does not appear to do anything ===

Check that the server configuration has the correct master switches enabled. Easy difficulty and low Threat intentionally remain close to vanilla.

=== The HUD is missing ===

Install the mod on the client, verify that the Modded HUD setting is enabled, and use <code>/threatdirector hud on</code> as an operator.

=== The configuration was rejected ===

Read the server log for the rejected property. Restore the previous valid file or correct the value, then run <code>/threatdirector debug config reload</code>.

=== Mobs are not spawning in large groups ===

Check difficulty, Threat, encounter cooldowns, performance limits, and whether the Performance Governor is suppressing new encounters.

=== An unmodded client sees a raw key ===

Report the server log and client/server mod versions. Threat Director uses English fallbacks and compatibility checks specifically to prevent this case.

== FAQ ==
<span id="FAQ"></span>

=== Does every player need the mod? ===

No. The Vanilla layer is server-sided. The client mod is required for the Threat HUD, client localization resources, and operator presentation features.

=== Does Threat Director simply increase mob health? ===

No. Most pressure comes from Awareness, positioning, coordination, targeting, reinforcements, and abilities.

=== Can I use only the AI improvements? ===

Yes. Enable the Vanilla layer and disable the Modded layer.

=== Can I disable Blood Moons? ===

Yes. Blood Moon scheduling and behavior are configurable under <code>modded.bloodMoon</code>.

=== Can I test scenes manually? ===

Operators can use <code>/threatdirector debug scene &lt;scene&gt;</code>.

=== Are Threat values shared between players? ===

No. Threat is calculated independently, although nearby players contribute limited group pressure.

=== What happens after death? ===

Part of the effective playtime contribution is removed, allowing the player to recover without making progression completely persistent.

[[Category:Performance]]
[[Category:Debugging]]
[[Category:Guides]]
[[Category:Compatibility]]
```

## Version History

```mediawiki
= Version History =

This page lists public Threat Director releases.

{| class="wikitable"
! Version !! Summary
|-
| [[#2.0|2.0]] || Awareness, Traits, Blood Moons, localization, server compatibility, and the complete configuration overhaul.
|-
| 1.1 || Per-player progression, XP and playtime scaling, HUD improvements, and expanded commands.
|-
| 0.3 || Initial adaptive encounters, hostile mob abilities, and operator testing tools.
|}

== 2.0 ==
<span id="2.0"></span>

Threat Director 2.0 is a complete overhaul focused on systemic hostile AI, configurable difficulty, multiplayer fairness, server compatibility, and performance.

=== Highlights ===

* Added Awareness profiles for movement, sound, and light.
* Added rare persistent mob Traits.
* Added Blood Moon events with omens and sleep prevention.
* Rebalanced ordinary difficulty while preserving full Blood Moon pressure.
* Added improved Skeleton, Zombie, Creeper, Spider, Enderman, and Drowned behavior.
* Added complete General, Vanilla, and Modded JSON5 configuration.
* Added English and Spanish localization.
* Added invisible support for unmodded clients.
* Improved multiplayer scaling and performance degradation.

=== Migration ===

Back up the existing configuration before updating. The new configuration writer migrates supported legacy values while preserving the original file.

=== 2.0.1 hotfix notes ===

The 2.0 line also includes balance fixes for spider hatchlings, creeper breaching, zombie equipment probabilities, and player-style zombie shields. These changes are included in the current 2.0 release line.

[[Category:Versions]]
[[Category:Changelogs]]
```

## Redirect pages

Create these optional redirect pages if you already used the older one-page-per-topic structure. They preserve old links while moving readers to the grouped pages.

### Threat Levels

```mediawiki
#REDIRECT [[Threat System]]
```

### Progression and Playtime

```mediawiki
#REDIRECT [[Threat System#Progression_protection]]
```

### Awareness

```mediawiki
#REDIRECT [[Director Systems#Awareness]]
```

### Traits

```mediawiki
#REDIRECT [[Director Systems#Traits]]
```

### Encounter Director

```mediawiki
#REDIRECT [[Director Systems#Encounter_Director]]
```

### Tactical Roles

```mediawiki
#REDIRECT [[Director Systems#Tactical_Roles]]
```

### Commands

```mediawiki
#REDIRECT [[Commands and HUD#Commands]]
```

### HUD

```mediawiki
#REDIRECT [[Commands and HUD#HUD]]
```

### Localization

```mediawiki
#REDIRECT [[Commands and HUD#Localization]]
```

### Multiplayer

```mediawiki
#REDIRECT [[Multiplayer and Compatibility]]
```

### Server-Side Compatibility

```mediawiki
#REDIRECT [[Multiplayer and Compatibility#Server-side_layer]]
```

### Performance

```mediawiki
#REDIRECT [[Performance and Troubleshooting#Performance_Governor]]
```

### Debugging and Telemetry

```mediawiki
#REDIRECT [[Performance and Troubleshooting#Debugging_and_Telemetry]]
```

### Troubleshooting

```mediawiki
#REDIRECT [[Performance and Troubleshooting#Troubleshooting]]
```

### FAQ

```mediawiki
#REDIRECT [[Performance and Troubleshooting#FAQ]]
```

### Changelog/2.0

```mediawiki
#REDIRECT [[Version History#2.0]]
```

## Template:Mob infobox (optional)

```mediawiki
<noinclude>This template creates a compact mob information table.</noinclude><includeonly>
{| class="wikitable" style="float:right; margin-left:1em;"
! colspan="2" | {{{name|Mob}}}
|-
| Base mob || {{{base_mob|Vanilla hostile mob}}}
|-
| Threat scaling || {{{threat_scaling|Yes}}}
|-
| Blood Moon scaling || {{{blood_moon_scaling|Yes}}}
|}
</includeonly>
```

## Category pages

Use the following source blocks for the main categories. Additional categories can be created later as the wiki grows.

### Category:Threat Director

```mediawiki
This category contains pages about Threat Director, its systems, configuration, commands, and releases.

[[Category:Main topic classifications]]
```

### Category:Gameplay

```mediawiki
This category contains pages describing Threat Director gameplay systems.

[[Category:Threat Director]]
```

### Category:Configuration

```mediawiki
This category contains configuration documentation for Threat Director.

[[Category:Threat Director]]
```

### Category:Mobs

```mediawiki
This category contains pages about hostile mob behavior in Threat Director.

[[Category:Threat Director]]
```

### Category:Hostile mobs

```mediawiki
This category contains individual hostile mob behavior sections and related pages.

[[Category:Mobs]]
```

### Category:Encounters

```mediawiki
This category contains pages about coordinated encounters, phases, budgets, and tactical roles.

[[Category:Gameplay]]
```

### Category:Multiplayer

```mediawiki
This category contains pages about multiplayer, server-side behavior, group scaling, and client compatibility.

[[Category:Threat Director]]
```

### Category:Server Administration

```mediawiki
This category contains configuration, performance, telemetry, and compatibility pages for server administrators.

[[Category:Threat Director]]
```

### Category:Commands

```mediawiki
This category contains Threat Director operator commands and command-based testing tools.

[[Category:Threat Director]]
```

### Category:Awareness

```mediawiki
This category contains pages about movement, sound, light, and investigation Awareness.

[[Category:Gameplay]]
```

### Category:Traits

```mediawiki
This category contains pages about systemic hostile mob Traits.

[[Category:Gameplay]]
```

### Category:Blood Moon

```mediawiki
This category contains pages about Blood Moon events, scheduling, omens, and special rules.

[[Category:Gameplay]]
```

### Category:Versions

```mediawiki
This category contains Threat Director version pages and changelogs.

[[Category:Threat Director]]
```

### Category:Performance

```mediawiki
This category contains pages about server load, planner limits, and Performance Governor behavior.

[[Category:Server Administration]]
```

### Category:Debugging

```mediawiki
This category contains telemetry, testing, troubleshooting, and bug-reporting documentation.

[[Category:Server Administration]]
```

### Category:Changelogs

```mediawiki
This category contains detailed Threat Director release notes.

[[Category:Versions]]
```

### Category:Guides

```mediawiki
This category contains player and administrator guides for Threat Director.

[[Category:Threat Director]]
```

### Category:Compatibility

```mediawiki
This category contains pages about Fabric, client compatibility, server-side behavior, and integrations.

[[Category:Threat Director]]
```

### Category:Maintenance

```mediawiki
This category contains known issues and maintenance information.

[[Category:Threat Director]]
```

### Category:FAQ

```mediawiki
This category contains frequently asked questions about Threat Director.

[[Category:Guides]]
```
