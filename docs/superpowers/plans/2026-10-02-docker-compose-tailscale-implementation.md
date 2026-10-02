# Docker Compose Tailscale Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a clone-and-run Docker Compose deployment for a private Ms-v206 server reachable through Tailscale while preserving MySQL player data across normal shutdowns and rebuilds.

**Architecture:** A Java 17 multi-stage image runs the server, and a private MySQL 8 service owns a named data volume. A small Java settings layer reads a mounted properties file and environment variables, with defaults retaining IDE behavior. Compose initializes an empty database deterministically from the existing SQL data and publishes only game traffic ports on the host.

**Tech Stack:** Java 17, Maven, JUnit 5, Docker Engine/Compose v2, MySQL 8, PowerShell 7.

**Spec:** `docs/superpowers/specs/2026-10-02-docker-compose-tailscale-design.md`

## Global Constraints

- Target private Tailscale deployments only; do not automate public exposure or Tailscale installation.
- Use Java 17, matching the existing Maven compiler release.
- Use MySQL 8 with a Git-ignored named volume called `ms-v206-db-data`.
- Do not commit real secrets, initialized DB files, backups, or local `.env`/`config/server.properties` files.
- `docker compose down` and `docker compose up --build` must retain player data.
- Keep MySQL unpublished on the host; publish only API 8483, login 8484, and the configured channel range from 8584.
- Protocol version and encryption constants remain code-owned.
- Never run `git commit` or `git push` without the user's explicit approval after reviewing the diff and commit message.

## Review Focus

- Existing `.env` and `config/server.properties` are never overwritten by setup; cover this in the PowerShell Pester test task.
- Invalid numeric server settings must fail startup with the property name and expected range; cover this in `ServerSettingsTest`.
- A database volume that already contains data must not rerun SQL initialization; cover this in the clean-start/persistence smoke test.
- Database credentials containing shell-special characters must be passed through Compose without appearing in logs; cover with `docker compose config` using a temporary `.env`.
- A channel count of one and a channel count of ten must map to the exact published inclusive port range; cover in the Compose configuration test.

---

## File Structure

| Path | Responsibility |
| --- | --- |
| `src/main/java/net/swordie/ms/config/ServerSettings.java` | Immutable operator-facing settings, property parsing, validation, and defaults. |
| `src/main/java/net/swordie/ms/config/DatabaseSettings.java` | Database environment parsing and Hibernate property overrides. |
| `src/main/java/net/swordie/ms/Server.java` | Loads settings before database and world startup. |
| `src/main/java/net/swordie/ms/ServerConfig.java` | Delegates mutable operator-facing values to `ServerSettings`; retains compatibility for existing callers. |
| `src/main/java/net/swordie/ms/constants/GameConstants.java` | Delegates the four configurable rates/channel values to `ServerSettings`. |
| `src/main/java/net/swordie/ms/connection/db/DatabaseManager.java` | Applies `DatabaseSettings` before building Hibernate's `SessionFactory`. |
| `src/main/java/hibernate.cfg.xml` | Contains password-free local defaults only. |
| `src/test/java/net/swordie/ms/config/*Test.java` | Unit tests for property/env parsing and validation. |
| `Dockerfile` | Reproducible Java 17 multi-stage build and runtime image. |
| `docker-compose.yml` | Server, private MySQL service, health check, named volume, and published ports. |
| `database/init/*.sql` | Deterministically named copies/wrappers for first-run database initialization. |
| `.env.example`, `config/server.properties.example` | Safe, documented operator configuration templates. |
| `scripts/*.ps1`, `tests/powershell/*.Tests.ps1` | Windows operator commands and Pester coverage. |
| `.dockerignore`, `.gitignore`, `README.md` | Build context controls, local-data exclusion, and supported workflow. |

### Task 1: Add a tested runtime settings boundary

**Files:**
- Create: `src/main/java/net/swordie/ms/config/ServerSettings.java`
- Create: `src/main/java/net/swordie/ms/config/DatabaseSettings.java`
- Create: `src/test/java/net/swordie/ms/config/ServerSettingsTest.java`
- Create: `src/test/java/net/swordie/ms/config/DatabaseSettingsTest.java`
- Modify: `pom.xml`
- Modify: `src/main/java/net/swordie/ms/Server.java`
- Modify: `src/main/java/net/swordie/ms/ServerConfig.java`
- Modify: `src/main/java/net/swordie/ms/constants/GameConstants.java`
- Modify: `src/main/java/net/swordie/ms/connection/db/DatabaseManager.java`
- Modify: `src/main/java/hibernate.cfg.xml`

**Interfaces:**
- Produces: `ServerSettings.load(Path configPath, Map<String, String> environment): ServerSettings`
- Produces: `DatabaseSettings.fromEnvironment(Map<String, String> environment): DatabaseSettings`
- Produces: `DatabaseSettings.applyTo(Configuration configuration): void`
- Consumes: `config/server.properties` mounted at `/opt/ms-v206/config/server.properties` in Docker and located at `config/server.properties` for local runs.

- [ ] **Step 1: Add JUnit 5 and Surefire, then write the failing settings tests**

Add test dependencies/plugins in `pom.xml`:

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.2</version>
    <scope>test</scope>
</dependency>
```

Write tests that assert defaults, a complete override, an invalid `channels=0`, and a DB URL constructed from host/port/name:

```java
@Test
void loadsConfiguredChannelCount() throws IOException {
    Path config = tempDir.resolve("server.properties");
    Files.writeString(config, "channels=3\n");

    ServerSettings settings = ServerSettings.load(config, Map.of());

    assertEquals(3, settings.channelCount());
}

@Test
void rejectsChannelCountBelowOne() throws IOException {
    Path config = tempDir.resolve("server.properties");
    Files.writeString(config, "channels=0\n");

    IllegalArgumentException error = assertThrows(
        IllegalArgumentException.class,
        () -> ServerSettings.load(config, Map.of())
    );

    assertTrue(error.getMessage().contains("channels"));
}
```

- [ ] **Step 2: Run the tests to verify they fail for missing settings classes**

Run: `mvn test -Dtest=ServerSettingsTest,DatabaseSettingsTest`

Expected: compilation failure because `ServerSettings` and `DatabaseSettings` do not exist.

- [ ] **Step 3: Implement immutable settings parsing and database overrides**

Implement records or final classes with these defaults:

```java
public record ServerSettings(
    String serverName,
    String serverMessage,
    WorldId worldId,
    int channelCount,
    int mobExpRate,
    int mobMesoRate,
    int mobDropRate
) { }
```

`ServerSettings.load` must load an optional UTF-8 properties file, use current source values as defaults, and reject non-positive channel/rate values. `DatabaseSettings` must use `DB_HOST=127.0.0.1`, `DB_PORT=3306`, `DB_NAME=v206`, `DB_USER=root`, and an empty default password only for local compatibility; `applyTo` must set `hibernate.connection.url`, `hibernate.connection.username`, and `hibernate.connection.password` on the Hibernate `Configuration`.

Load settings once at the start of `Server.init`, expose them to `ServerConfig` and `GameConstants` through getters, and call `DatabaseSettings.applyTo(configuration)` in `DatabaseManager.init` immediately after `new Configuration().configure()`.

Remove the committed real password from `hibernate.cfg.xml`.

- [ ] **Step 4: Run focused tests and Maven package**

Run: `mvn test -Dtest=ServerSettingsTest,DatabaseSettingsTest`

Expected: PASS.

Run: `mvn clean package`

Expected: BUILD SUCCESS and an assembled JAR in `bin/`.

- [ ] **Step 5: Commit the settings boundary**

Proposed message: `feat: add environment-based server configuration`

Do not commit until the user explicitly approves the reviewed diff and message.

### Task 2: Create deterministic database initialization and Compose topology

**Files:**
- Create: `database/init/001-characters.sql`
- Create: `database/init/002-drops.sql`
- Create: `database/init/003-cashshop.sql`
- Create: `database/init/004-extra-drops.sql`
- Create: `database/init/005-equip-drops.sql`
- Create: `database/init/006-beautyalbum.sql`
- Create: `database/init/007-charactercard.sql`
- Create: `database/init/008-npc.sql`
- Create: `database/init/009-monster-collection.sql`
- Create: `database/init/010-shops.sql`
- Create: `database/init/011-hair-equips.sql`
- Create: `database/init/012-unseen-equips.sql`
- Create: `docker-compose.yml`
- Create: `.env.example`
- Create: `config/server.properties.example`
- Create: `tests/docker/compose-config.ps1`

**Interfaces:**
- Consumes: `DB_*` values from `.env` and `ServerSettings` values from `config/server.properties`.
- Produces: Compose services named `server` and `db`, volume `ms-v206-db-data`, and a private service name `db` resolvable by the server.

- [ ] **Step 1: Write failing Compose contract checks**

Create a temporary `.env` from `.env.example`, then write a PowerShell check that asserts the resolved Compose model has no `db.ports`, has volume `ms-v206-db-data`, and publishes exactly `8483`, `8484`, and `8584-8593` when `CHANNEL_COUNT=10`:

```powershell
$config = docker compose --env-file $envFile config --format json | ConvertFrom-Json
$config.services.db.ports | Should -BeNullOrEmpty
$config.volumes.'ms-v206-db-data' | Should -Not -BeNullOrEmpty
$config.services.server.ports.published | Should -Contain '8484'
```

- [ ] **Step 2: Run the contract check to verify it fails before Compose exists**

Run: `pwsh -File tests/docker/compose-config.ps1`

Expected: FAIL because `docker-compose.yml` is absent.

- [ ] **Step 3: Add ordered SQL initialization and Compose files**

Copy the existing SQL content into the numbered `database/init` files in current documented execution order: `1` through `10`, then `hairequips.sql` and `unseenequips.sql`. Do not alter statements while renaming/copying.

Create Compose with:

```yaml
services:
  db:
    image: mysql:8.4
    environment:
      MYSQL_DATABASE: ${MYSQL_DATABASE}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    volumes:
      - ms-v206-db-data:/var/lib/mysql
      - ./database/init:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-p${MYSQL_ROOT_PASSWORD}"]
      interval: 5s
      timeout: 5s
      retries: 30

  server:
    build: .
    depends_on:
      db:
        condition: service_healthy
    environment:
      DB_HOST: db
      DB_PORT: 3306
      DB_NAME: ${MYSQL_DATABASE}
      DB_USER: ${MYSQL_USER}
      DB_PASSWORD: ${MYSQL_PASSWORD}
    ports:
      - "${API_PORT:-8483}:8483"
      - "${LOGIN_PORT:-8484}:8484"
      - "${CHANNEL_PORT_RANGE:-8584-8593}:8584-8593"
    volumes:
      - ./config/server.properties:/opt/ms-v206/config/server.properties:ro
      - ./wz:/opt/ms-v206/wz:ro
      - ./dat:/opt/ms-v206/dat:ro
      - ./resources:/opt/ms-v206/resources:ro
      - ./properties:/opt/ms-v206/properties:ro
      - ./scripts:/opt/ms-v206/scripts:ro

volumes:
  ms-v206-db-data:
```

Because Compose cannot derive a port range from a properties file, `.env` contains the corresponding `CHANNEL_PORT_RANGE`; `setup.ps1` generates it from `channels` and rejects a mismatch.

- [ ] **Step 4: Run Compose validation and the contract check**

Run: `docker compose --env-file .env.example config --quiet`

Expected: exit code 0.

Run: `pwsh -File tests/docker/compose-config.ps1`

Expected: PASS.

- [ ] **Step 5: Commit Compose and database initialization**

Proposed message: `feat: add docker compose database setup`

Do not commit until the user explicitly approves the reviewed diff and message.

### Task 3: Build and run the server image with mounted runtime content

**Files:**
- Create: `Dockerfile`
- Create: `.dockerignore`
- Modify: `docker-compose.yml`
- Create: `tests/docker/build-and-start.ps1`

**Interfaces:**
- Consumes: Maven project output and runtime folders mounted by Compose.
- Produces: `server` image that runs the main class JAR from `/opt/ms-v206` and emits startup logs to stdout.

- [ ] **Step 1: Write a failing clean-start smoke test**

Create a PowerShell test script that creates a unique Compose project name, starts `db` and `server`, waits for database health and the `Finished loading server` log line, writes a marker row using `docker compose exec db`, runs `docker compose down`, starts again, and verifies the marker remains. It must end with `docker compose -p $projectName down --volumes` in a `finally` block.

```powershell
$projectName = "msv206-smoke-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
docker compose -p $projectName up --build -d
docker compose -p $projectName logs server | Should -Match 'Finished loading server'
docker compose -p $projectName down
docker compose -p $projectName up -d
```

- [ ] **Step 2: Run the smoke test to verify it fails before the image exists**

Run: `pwsh -File tests/docker/build-and-start.ps1`

Expected: FAIL because `Dockerfile` is absent.

- [ ] **Step 3: Implement the multi-stage Dockerfile and build exclusions**

Create this image structure:

```dockerfile
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /opt/ms-v206
COPY --from=build /build/bin/*-jar-with-dependencies.jar ./Server.jar
COPY src/main/java/log4j.properties ./
ENTRYPOINT ["java", "--enable-preview", "-jar", "Server.jar"]
```

Adjust the JAR copy source only if the verified Maven output name differs. `.dockerignore` excludes `.git`, `bin`, IDE directories, local `.env`, local `config/server.properties`, database backups, and `database/docker-db-data`.

- [ ] **Step 4: Run build and persistence smoke tests**

Run: `docker compose build --no-cache server`

Expected: build succeeds with Java 17.

Run: `pwsh -File tests/docker/build-and-start.ps1`

Expected: PASS; the marker survives the stop/start cycle and `db` has no published port.

- [ ] **Step 5: Commit the server container image**

Proposed message: `feat: containerize the v206 server`

Do not commit until the user explicitly approves the reviewed diff and message.

### Task 4: Provide safe Windows operator helpers

**Files:**
- Create: `scripts/setup.ps1`
- Create: `scripts/start.ps1`
- Create: `scripts/stop.ps1`
- Create: `scripts/logs.ps1`
- Create: `scripts/backup-db.ps1`
- Create: `scripts/reset-db.ps1`
- Create: `tests/powershell/OperatorScripts.Tests.ps1`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: `.env.example`, `config/server.properties.example`, Docker Compose v2.
- Produces: local `.env`, local `config/server.properties`, Git-ignored `backups/`, and safe Compose lifecycle commands.

- [ ] **Step 1: Write failing Pester coverage for the safety contract**

Use a temporary repository copy and test the actual scripts:

```powershell
It 'does not overwrite an existing env file' {
    Set-Content "$testRepo/.env" 'MYSQL_PASSWORD=keep-this'
    & "$testRepo/scripts/setup.ps1"
    (Get-Content "$testRepo/.env" -Raw) | Should -Be "MYSQL_PASSWORD=keep-this`r`n"
}

It 'requires an explicit confirmation to reset database data' {
    { & "$testRepo/scripts/reset-db.ps1" -Confirm:$false } | Should -Throw
}
```

- [ ] **Step 2: Run Pester to verify it fails before helper scripts exist**

Run: `Invoke-Pester tests/powershell/OperatorScripts.Tests.ps1 -CI`

Expected: FAIL because helper scripts are absent.

- [ ] **Step 3: Implement small single-purpose scripts**

Implement scripts with `Set-StrictMode -Version Latest` and `$ErrorActionPreference = 'Stop'`.

- `setup.ps1`: require `docker compose version`; copy templates only when destination does not exist; read `channels` from `config/server.properties`; calculate and write `CHANNEL_PORT_RANGE=8584-$((8584 + channels) - 1)` to `.env`; reject values below one.
- `start.ps1`: run `docker compose up --build -d`.
- `stop.ps1`: run `docker compose down` without `--volumes`.
- `logs.ps1`: run `docker compose logs --follow server`.
- `backup-db.ps1`: create `backups` then run `docker compose exec -T db mysqldump --single-transaction -u$env:MYSQL_USER -p$env:MYSQL_PASSWORD $env:MYSQL_DATABASE` to a timestamped file; never echo credentials.
- `reset-db.ps1`: accept `[switch] $Force`; without `-Force`, require the literal input `DELETE`; then run `docker compose down --volumes`.

Add `.env`, `config/server.properties`, and `backups/` to `.gitignore`.

- [ ] **Step 4: Run Pester and an operator smoke check**

Run: `Invoke-Pester tests/powershell/OperatorScripts.Tests.ps1 -CI`

Expected: PASS.

Run: `pwsh -File scripts/setup.ps1`

Expected: existing local config remains unchanged; missing template-derived files are created; printed instructions contain no secret values.

- [ ] **Step 5: Commit operator scripts**

Proposed message: `feat: add docker setup helper scripts`

Do not commit until the user explicitly approves the reviewed diff and message.

### Task 5: Document the supported local and Tailscale workflow

**Files:**
- Modify: `README.md`
- Modify: `.env.example`
- Modify: `config/server.properties.example`

**Interfaces:**
- Consumes: scripts and Compose contract implemented in Tasks 1-4.
- Produces: a copy-paste setup path and a setting-to-file reference table.

- [ ] **Step 1: Write a failing documentation command check**

Create `tests/docs/readme-commands.ps1` that extracts every fenced PowerShell command from the Docker Setup section and confirms each referenced `scripts/*.ps1` file exists. It must also assert the README states that `docker compose down` preserves data and that reset is destructive.

```powershell
$readme = Get-Content README.md -Raw
$readme | Should -Match 'docker compose down.*preserv'
$readme | Should -Match 'reset.*destructive'
Test-Path scripts/setup.ps1 | Should -BeTrue
```

- [ ] **Step 2: Run the documentation check to verify it fails before README changes**

Run: `pwsh -File tests/docs/readme-commands.ps1`

Expected: FAIL because the Docker Setup section does not exist.

- [ ] **Step 3: Rewrite README setup guidance around supported commands**

Add sections for prerequisites (Docker Desktop and an already-connected Tailscale host), Quick Start, configuration reference, ports, Tailscale client IP/MagicDNS setup, start/stop/logs/rebuild, database persistence, backup/restore, destructive reset, and non-Docker development.

Include this exact quick start:

```powershell
git clone https://github.com/momoyuki/Ms-v206.git
cd Ms-v206
.\scripts\setup.ps1
# Edit .env and config/server.properties if needed.
.\scripts\start.ps1
.\scripts\logs.ps1
```

Document that `docker compose down` retains the `ms-v206-db-data` volume, while `scripts/reset-db.ps1` is destructive. Add a configuration table mapping each adjustable setting to `.env` or `config/server.properties`, its default, valid range, and restart/rebuild requirement.

- [ ] **Step 4: Run documentation and full validation suite**

Run: `pwsh -File tests/docs/readme-commands.ps1`

Expected: PASS.

Run: `mvn clean test`

Expected: PASS.

Run: `docker compose --env-file .env.example config --quiet`

Expected: PASS.

Run: `Invoke-Pester tests/powershell/OperatorScripts.Tests.ps1 -CI`

Expected: PASS.

Run: `pwsh -File tests/docker/build-and-start.ps1`

Expected: PASS.

Run: `git diff --check`

Expected: no output.

- [ ] **Step 5: Commit documentation and final validation changes**

Proposed message: `docs: document docker server setup`

Do not commit until the user explicitly approves the reviewed diff and message.

## Final Review and PR Preparation

- [ ] Inspect `git status --short` and `git diff --check`.
- [ ] Inspect the complete diff and confirm no `.env`, backup, database volume, password, or generated artifact is included.
- [ ] Run all Task 5 validation commands and report any unavailable tooling as unverified.
- [ ] Present the diff summary, verification output, and proposed Conventional Commit messages to the user.
- [ ] Only after explicit user approval, create the approved commits, push the branch, and open a PR whose description summarizes setup, persistence guarantees, configuration, tests, and manual client/Tailscale validation still required.
