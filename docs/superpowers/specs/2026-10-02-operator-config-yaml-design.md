# Operator YAML Configuration Design

## Goal

Provide a single, documented `config/config.yaml` for settings that a private Ms-v206 operator should safely adjust without recompiling Java. Keep infrastructure secrets in `.env` and leave client/protocol-sensitive constants in source code.

## Scope

The first configuration slice exposes these categories:

```yaml
server:
  name: v206
  message: v206
  worldId: Bera
  channels: 10
  userLimit: 20
  maxCharacters: 30

network:
  loginPort: 8484
  apiPort: 8483

rates:
  mobExp: 50
  mobMeso: 2
  mobDrop: 1

gameplay:
  hideGmOnLogin: false
  startMap: 4000011
  hubMap: 100000000

drops:
  remainSeconds: 120
  ownershipSeconds: 30

events:
  randomPortalChance: 0
  runeRespawnMinutes: 10
  runeCooldownMinutes: 0
```

## Configuration Boundaries

- `.env` remains the only source for database credentials and Docker host port mappings.
- `config/config.yaml` contains non-secret gameplay and server-operation settings.
- Every YAML value has the same default as the currently committed Java constant.
- Missing configuration uses defaults; malformed YAML or invalid values terminates startup with an actionable field-specific error.
- All YAML changes require `docker compose restart server`; port changes also require `docker compose up -d` so Compose applies host mappings.

## Implementation Shape

Replace the properties-based `ServerSettings` parser with a typed YAML-backed settings model. The model is loaded exactly once before database and world initialization. Its validated values are applied only to existing runtime settings that are safe to make mutable.

The initial implementation changes settings consumers in `ServerConfig`, `ServerConstants`, and `GameConstants`; it does not introduce a generic reflection-based mechanism that could silently mutate arbitrary constants.

`setup.ps1` creates `config/config.yaml` from `config/config.yaml.example` when absent and derives `CHANNEL_PORT_RANGE` from `server.channels`. It never overwrites an existing config file.

## Non-Goals

- Making every Java constant configurable.
- Changing client protocol version, encryption, WZ paths, packet IDs, map IDs, item IDs, or data-driven formulas.
- Hot reload without restarting the server.
- Moving database passwords from `.env` to YAML.

## Validation

- `server.channels`, ports, counts, rates, and duration values must be positive where zero is not a documented valid value.
- `events.randomPortalChance` must be between 0 and 1000.
- `worldId` must resolve to an existing `WorldId` enum constant.
- Map IDs must be non-negative.
- Unknown YAML properties fail fast to prevent typographical configuration that has no effect.

## Verification

1. Unit tests cover defaults, valid overrides, invalid ranges, invalid enum values, unknown keys, and malformed YAML.
2. Unit tests prove each first-slice setting is applied to its current Java consumer.
3. `setup.ps1` creates YAML only when absent and synchronizes the channel port range without overwriting local values.
4. Docker Compose starts using `config/config.yaml`; server logs show the configured server identity and channel count.
5. The README documents every first-slice key, default, valid range, and required restart action.
