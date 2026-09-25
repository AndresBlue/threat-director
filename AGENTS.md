# Threat Director

Fabric 1.20.1, Java 17, server-authoritative. Mod id `threatdirector`.
Run Gradle from this directory.

Config snapshot is schema 4 JSON5 with three independent layers:
`general` (threat, governor, telemetry), `vanilla` (encounters), `modded`
(awareness, traits, blood moon, extra abilities).

## Where to look

| Area | Package |
| --- | --- |
| Entrypoint / switches | `ThreatDirectorMod`, `ThreatDirectorClient` |
| Config | `config/` |
| Threat 0–100 | `power/`, `server/PlayerObservationService` |
| Encounters | `encounter/`, `director/`, `budget/` |
| Roles / sectors | `role/`, `tactical/`, `pattern/`, `ai/` |
| Performance | `performance/` |
| Awareness | `awareness/` |
| Type capabilities (MELEE, RANGED, …) | `trait/` |
| Persistent variants (COWARD, BERSERKER, …) | `traits/` |
| Blood Moon | `bloodmoon/` |
| Per-mob extras | `difficulty/`, `creeper/`, `drowned/`, `apocalypse/` |
| Mixins | `mixin/` + `threatdirector.mixins.json` |
| Commands / HUD | `command/`, `client/` |
| Claims / adapters | `compatibility/`, `api/integration/` |

Do not read TriStorage source for Threat Director work.
Docs: `docs/ARCHITECTURE.md`, `docs/CONFIGURATION.md`, `docs/COMMANDS.md`.
