# Threat Director 2.0 Integrations

Threat Director keeps compatibility optional and isolated. The core has no required content-mod,
claims, accessories, magic, or modpack dependency.

## Loading

`general.integrations.enableExternalAdapters` controls whether compatible providers are discovered at
runtime startup. Changing it requires a server restart; a config reload does not rebuild the provider
registry.

Providers load transactionally. A provider that throws, returns invalid data, or conflicts with an
existing registration is rejected without partially mutating the active compatibility registry.

## Claims

Claim checks return one of three authority states:

- `UNPROTECTED`: destructive behavior may proceed if every other safety check passes;
- `PROTECTED`: the action is denied;
- `UNKNOWN`: the action is denied.

Failing closed prevents a missing or broken adapter from turning zombie mining, cobweb placement, or
creeper breaching into an accidental griefing bypass.

## Modded mobs

Awareness and Traits target conventional hostile spawn groups and therefore work with many modded
mobs without a dedicated adapter. The Director's directed-spawn roster remains an explicit list of
known vanilla IDs; natural mobs are adapted only when their type and cost policy are supported.

Threat Director does not overwrite another mod's registered entity type or require custom clients to
understand server-side AI decisions.

## Diagnostics

Use:

```text
/threatdirector debug integrations
```

The command reports loaded integration IDs, failure count, and whether a claim system was detected.
Startup logs contain provider-specific failure details.
