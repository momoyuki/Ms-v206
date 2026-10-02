# Operator YAML Configuration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `server.properties` with validated `config/config.yaml` for approved operator settings.

**Architecture:** `ServerSettings` becomes the typed YAML parser and validates a closed schema once before initialization. Existing constants become mutable only when explicitly represented by YAML. `setup.ps1` reads YAML and synchronizes Compose target ports while preserving database credentials and host mapping choices in `.env`.

**Tech Stack:** Java 17, SnakeYAML 2.2, JUnit Jupiter 5, PowerShell, Docker Compose v2.

**Spec:** `docs/superpowers/specs/2026-10-02-operator-config-yaml-design.md`

## Global Constraints

- Database credentials remain only in `.env`.
- Unknown, malformed, or invalid YAML fails startup with a field-specific error.
- No hot reload; YAML changes require `docker compose restart server`.
- Port changes require `setup.ps1` and `docker compose up -d`.
- Default channel ports are `8585-8594`, derived from login port 8484.
- Protocol, encryption, WZ paths, packet IDs, and arbitrary constants stay in source code.

## Review Focus

- Unknown YAML keys must fail rather than silently use defaults.
- Invalid YAML must fail before database or network initialization.
- A changed login port must change all derived channel ports, including channel 10.
- Setup must preserve `MYSQL_PASSWORD`, `API_PORT`, and `LOGIN_PORT`.
- `server.maxCharacters` must consistently affect account slot creation and character allocation.

---

### Task 1: Parse and validate YAML

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/java/net/swordie/ms/config/ServerSettings.java`
- Create: `config/config.yaml.example`
- Delete: `config/server.properties.example`
- Modify: `src/test/java/net/swordie/ms/config/ServerSettingsTest.java`

**Interfaces:** `ServerSettings.load(Path)` returns typed values for server, network, rates, gameplay, drops, and events.

- [ ] Write failing JUnit tests for defaults, valid overrides, unknown fields, malformed YAML, invalid `WorldId`, invalid ranges, and negative maps.
- [ ] Run focused `ServerSettingsTest`; expect failure because YAML fields and parser do not exist.
- [ ] Add `org.yaml:snakeyaml:2.2`; implement nested typed records, defaults matching current constants, duplicate-key rejection, closed key sets, and dotted validation errors.
- [ ] Add `config/config.yaml.example` containing every first-slice key from the spec.
- [ ] Run `ServerSettingsTest` and `DatabaseSettingsTest`; expect zero failures.
- [ ] Commit with `feat: load operator settings from YAML`.

### Task 2: Apply YAML values at startup

**Files:**
- Modify: `src/main/java/net/swordie/ms/Server.java`
- Modify: `src/main/java/net/swordie/ms/ServerConfig.java`
- Modify: `src/main/java/net/swordie/ms/ServerConstants.java`
- Modify: `src/main/java/net/swordie/ms/constants/GameConstants.java`
- Modify: `src/test/java/net/swordie/ms/config/ServerSettingsTest.java`

**Interfaces:** `ServerConfig.apply(ServerSettings)` and `ServerConstants.apply(ServerSettings)` run before `DatabaseManager.init()`.

- [ ] Write failing tests that assert configured identity, channels, limits, ports, rates, maps, drops, random portal chance, and rune timers reach their current consumers.
- [ ] Run the focused test; expect failure because consumers are immutable or unset.
- [ ] Make only first-slice settings mutable and apply them once in `Server.loadSettings()` before database/world initialization.
- [ ] Reconcile the duplicate max-character constants so `ServerConstants.MAX_CHARACTERS` is authoritative for both account slots and character allocation.
- [ ] Reset static values after each test; run `mvn -B test`; expect zero failures.
- [ ] Commit with `feat: apply YAML settings at startup`.

### Task 3: Synchronize Compose ports from YAML

**Files:**
- Modify: `docker-compose.yml`
- Modify: `scripts/setup.ps1`
- Create: `scripts/test-setup-config.ps1`

**Interfaces:** YAML `network.apiPort`, `network.loginPort`, and `server.channels` produce `.env` keys `SERVER_API_PORT`, `SERVER_LOGIN_PORT`, and `CHANNEL_PORT_RANGE`.

- [ ] Write a failing isolated PowerShell fixture test for login port 8484 and 10 channels; expect `CHANNEL_PORT_RANGE=8585-8594` while existing host ports and `MYSQL_PASSWORD` remain unchanged.
- [ ] Run the test; expect failure because setup reads properties and produces `8584-8593`.
- [ ] Implement a limited scalar YAML reader for the three setup paths. Atomically rewrite only generated target keys; calculate first channel as `loginPort + 101` and last as `loginPort + 100 + channels`.
- [ ] Mount `config/config.yaml` in Compose. Map host API/login ports to synchronized target ports and map the generated channel range to itself. Remove the properties mount.
- [ ] Run the PowerShell test and `docker compose config --quiet`; expect success with the full default channel range.
- [ ] Commit with `feat: synchronize Compose ports from YAML`.

### Task 4: Document and verify the workflow

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-10-02-operator-config-yaml-design.md`

- [ ] Document every YAML key, default, range, Java consumer, and restart action. State `.env` retains database credentials and host mapping choices.
- [ ] Run Dockerfile regression check, setup regression test, Compose validation, full Maven tests, and `git diff --check`; every command must exit zero.
- [ ] Manually configure one channel, run setup and Docker Compose, and confirm the one channel listens on `loginPort + 101`; restore the local config afterward.
- [ ] Commit with `docs: document YAML operator configuration`.
