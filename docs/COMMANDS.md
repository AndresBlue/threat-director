# Threat Director 2.0 Command Reference

The root command is:

```text
/threatdirector
```

The routine surface is intentionally small: inspect or override threat, control the HUD, and enable
OP chat logging. Administrative test operations live under `debug`.

## Permissions and command sources

- Permission level 2 is required for every mutation, another player's data, HUD control, logging,
  and debug operation.
- A player may inspect their own threat without an explicit target when the command branch allows it.
- The server console must provide a player argument for operations that require a world position or
  player state.
- Player arguments must refer to connected players.
- Commands never bypass Peaceful, world borders, unloaded chunks, spawn protection, claims,
  `mobGriefing`, health relief, hard population caps, or the Performance Governor.

## Command tree

```text
/threatdirector threat [player]
/threatdirector threat set <0..100> [player]
/threatdirector threat clear [player]

/threatdirector hud [player]
/threatdirector hud on|off [player]

/threatdirector logging status [player]
/threatdirector logging on|off [player]

/threatdirector debug status [player]
/threatdirector debug intensity [player]
/threatdirector debug start [player]
/threatdirector debug cancel [player|all]
/threatdirector debug phase <phase> [player]
/threatdirector debug scene <scene> [player]
/threatdirector debug roles [player]
/threatdirector debug sectors [player]
/threatdirector debug budget [player]
/threatdirector debug breach simulate [player]
/threatdirector debug config reload
/threatdirector debug integrations
/threatdirector debug performance
/threatdirector debug performance reset
/threatdirector debug preset
/threatdirector debug telemetry
```

Legacy aliases may remain for compatibility, but new documentation and support should use the tree
above.

## Threat inspection

### `/threatdirector threat [player]`

Shows the effective threat value, its band, and whether the value is adaptive or forced. When the
requested player is a secondary participant in a shared encounter, the command reports the primary
owner whose threat currently drives that encounter.

Threat bands are:

- `LOW`: below 35;
- `GUARDED`: 35 to below 50;
- `TACTICAL`: 50 to below 65;
- `HIGH`: 65 to below 85;
- `EXTREME`: 85 to 100.

Adaptive values use smoothing and hysteresis, so a band may intentionally lag behind a sudden
equipment or environment change.

### `/threatdirector debug status [player]`

Shows a compact operational snapshot:

- effective threat and band;
- adaptive or forced mode;
- effective encounter pressure and zone label;
- power score;
- active encounter phase and living mob count;
- remaining cooldown ticks.

Encounter pressure is not the same as the HUD value. It combines the per-player threat with
Minecraft difficulty and environmental hazard to shape the tactical encounter.

### `/threatdirector debug intensity [player]`

Shows the detailed intensity breakdown:

- displayed and adaptive values;
- target intensity;
- power;
- environment;
- readiness;
- distress;
- server/planner load;
- playtime progress;
- XP progress;
- combined progression.

If no observation sample exists yet, the command says so instead of inventing zero-valued details.

## Threat overrides

### `/threatdirector threat set <0..100> [player]`

Requires permission level 2. The value must be finite and between 0 and 100. `NaN`, infinity, and
out-of-range values are rejected without modifying state.

The override immediately becomes the effective value used by the HUD, encounter selection, dynamic
targeting, and ability scaling. Adaptive calculation continues in the background and is displayed by
diagnostic commands.

An override does not rebuild the lifetime Threat Budget or population of an already active encounter.
When applied before starting a scene, it influences the new budget and contextual population.

A low override is never silently raised. If it cannot fund the contextual minimum, a forced start
returns a clear budget or minimum-population error.

### `/threatdirector threat clear [player]`

Removes the in-memory override and immediately returns to the most recent adaptive value. Overrides,
observation memory, and cooldowns are session state and do not persist across server restarts.

## HUD control

### `/threatdirector hud [player]`

Reports whether the target's threat HUD preference is enabled or hidden.

### `/threatdirector hud on|off [player]`

Changes HUD visibility for one connected player. This requires:

- `modded.enabled: true`;
- `modded.hud.enabled: true`;
- `modded.hud.allowCommandOptIn: true`.

The target client must have Threat Director installed to render the bar. Enabling HUD for an
unmodded client is harmless because the server checks whether the channel is available before
sending data.

Hiding the HUD does not stop threat calculation, encounters, targeting, or AI. The preference lasts
for the server session.

## OP chat logging

### `/threatdirector logging status [player]`

Shows whether the selected OP is subscribed and reports logger status.

### `/threatdirector logging on|off [player]`

Adds or removes an OP player from live diagnostic delivery. It requires
`general.telemetry.operatorChatLoggingEnabled: true`.

Messages use the `[ThreatDirector DEBUG]` prefix and may include:

- threat samples and changes;
- encounter admission and rejection reasons;
- phase transitions;
- population and reinforcement outcomes;
- Awareness and Trait assignment;
- Blood Moon omens, start, and end;
- performance level changes;
- command actions and safety denials.

Chat logging is not broadcast to normal players and is independent from local NDJSON telemetry. It
ends when the server session closes.

## Encounter controls

### `/threatdirector debug start [player]`

Builds a safe population and creates an encounter in Stalking. Feedback includes the encounter UUID,
member count, and lifetime Threat Budget.

A forced start bypasses ordinary cooldown, random chance, automatic anchor selection, and the normal
night/darkness requirement. It does not bypass safety rules, minimum affordable population,
Performance Governor rejection, loaded chunks, valid paths, world border, or spawn protection.

Without an override, the command uses the strongest of the current adaptive value, the configured
minimum, and the administrative start floor. With an override, it uses the requested value exactly.

### `/threatdirector debug cancel [player|all]`

Moves the selected encounter—or every active encounter—to Relief. It releases tactical ownership
through normal cleanup rather than abruptly deleting mobs.

### `/threatdirector debug phase <phase> [player]`

Forces an existing encounter state. Valid internal phase names are:

```text
STALKING
POSITIONING
ENGAGING
SEARCHING
CLIMAX
RELIEF
RETREATING
```

This low-level command requires an existing encounter and may reject a transition that violates the
state machine.

### `/threatdirector debug scene <scene> [player]`

This is the recommended feature-testing command. Valid lowercase scenes are:

```text
stalking
positioning
engaging
searching
climax
relief
retreating
```

If the player has no active encounter, the command attempts to create one safely before forcing the
requested scene. Feedback identifies whether a new encounter was created and reports threat, living
mobs, and budget.

`scene` is scenario-oriented; `phase` is a direct state-machine operation.

## Tactical diagnostics

### `/threatdirector debug roles [player]`

Lists abbreviated member UUIDs and their assigned tactical roles. Use it to confirm that Vanguard,
Flanker, Harasser, and Interceptor assignments are distributed without duplicate ownership.

### `/threatdirector debug sectors [player]`

Spawns short-lived `END_ROD` particles at current tactical positions and reports their count. This is
visual debugging only and does not change navigation or orders.

### `/threatdirector debug budget [player]`

Reports:

- total lifetime budget;
- base budget;
- reinforcement reserve;
- active mob spending;
- retired mob spending;
- pattern reservations;
- remaining budget.

Killed mobs move into retired spending; their cost is not returned. Reinforcements therefore cannot
continue forever even when the concurrent cap has room.

## Breach simulation

### `/threatdirector debug breach simulate [player]`

Runs a read-only breach plan from a representative encounter member toward the target. If there is no
encounter, it uses a nearby synthetic origin. The command reports decision, reason, block count,
budget cost, protection state, safety, and visited nodes.

It requires `vanilla.breachSimulation.enabled: true`. The simulator never breaks blocks and is
separate from the Modded creeper and zombie abilities.

## Configuration

### `/threatdirector debug config reload`

Reads `config/threatdirector.json5`, verifies the complete schema and cross-field relationships, and
publishes it only if every check passes.

Examples of rejected configurations:

- a missing property;
- an unknown or misspelled property;
- probability outside `0.0..1.0`;
- minimum greater than maximum;
- non-increasing performance thresholds;
- invalid Minecraft identifiers;
- missing threat cost for an allowed mob;
- a population value above its hard safety cap.

On failure, the previous immutable snapshot remains active. See
[CONFIGURATION.md](CONFIGURATION.md) for every section and reload caveat.

## Integrations and performance

### `/threatdirector debug integrations`

Shows loaded integration IDs, failure count, and claim-system detection. Integration providers are
loaded at runtime startup, not by this command or a config reload.

### `/threatdirector debug performance`

Reports governor level `0..6`, current MSPT, decision interval, candidate limit, active encounter
count, new-encounter admission, and configured planner budgets.

At high levels, the governor rejects new encounters. Emergency level 6 can release encounter AI
ownership temporarily so vanilla behavior resumes under severe load.

### `/threatdirector debug performance reset`

Resets the governor level and ordinary counters, then gives the system a short observation warmup.
It does not change actual server load or conceal emergencies: consecutive critical samples can still
trigger safety level 6 during the warmup.

### `/threatdirector debug preset`

Shows the active preset. Version 2.0 supports `VANILLA_PLUS`; detailed behavior is controlled by the
three configuration categories rather than multiple opaque presets.

### `/threatdirector debug telemetry`

Shows the local telemetry recorder snapshot. Disk telemetry is configured under `general.telemetry`
and requires a restart when changing writer construction settings.

## Reproducible test flows

### Maximum-pressure combat

```text
/threatdirector threat set 100
/threatdirector logging on
/threatdirector debug scene engaging
/threatdirector debug roles
/threatdirector debug budget
/threatdirector debug scene climax
/threatdirector threat clear
```

### Relief and cleanup

```text
/threatdirector threat set 20
/threatdirector debug scene relief
/threatdirector debug status
/threatdirector threat clear
```

### Configuration verification

```text
/threatdirector debug config reload
/threatdirector debug status
/threatdirector debug performance
/threatdirector debug integrations
```

### Multiplayer ownership

```text
/threatdirector threat set 80 PlayerA
/threatdirector threat set 35 PlayerB
/threatdirector debug scene engaging PlayerA
/threatdirector debug status PlayerA
/threatdirector debug status PlayerB
/threatdirector debug roles PlayerA
```

Confirm that PlayerB retains their own threat and targeting pressure while participating in the
shared encounter owned by PlayerA.
