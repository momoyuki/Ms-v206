# Docker Compose Tailscale Deployment Design

## Goal

Make Ms-v206 easy to run as a private MapleStory server for friends connected through Tailscale. A new operator must be able to clone the repository, create local configuration from templates, and start the server with Docker Compose. Player and game data must survive `docker compose down` and normal container rebuilds.

## Scope

- Build and run the Java 17 server and MySQL in Docker Compose.
- Initialize a new database automatically from the repository SQL files.
- Read database and operator-facing server settings from environment variables and mounted configuration files.
- Provide Windows PowerShell helpers and a concise setup guide.
- Support Tailscale access by exposing game ports on the host only.
- Preserve database data in a named Docker volume.

## Non-Goals

- Public-internet hosting, reverse proxy setup, or firewall automation.
- Changes to gameplay, client protocol, or MapleStory version.
- Updating unrelated Java dependencies.
- Committing secrets, initialized database files, backups, or player data.

## Architecture

Docker Compose defines two services on a private Compose network:

1. `server` builds the project with Maven and Java 17, then runs the assembled server JAR.
2. `db` runs MySQL 8 and stores all database files in the `ms-v206-db-data` named volume.

The database service is not published to the host. The server connects to it at the Compose DNS name `db`. The server waits for a successful MySQL health check before startup. Compose publishes only the API, login, and configured channel ports that Tailscale peers need.

`wz`, `dat`, `resources`, `properties`, and `scripts` remain outside the image as mounted runtime content where appropriate. This keeps routine content/configuration updates independent of a Java image rebuild.

## Configuration Model

The repository provides templates only:

- `.env.example` contains Docker and operator values.
- `config/server.properties.example` contains documented server settings.

The local `.env` and `config/server.properties` files are ignored by Git. The setup helper creates them when absent and never overwrites existing configuration.

Environment variables configure database connectivity and deployment-specific values:

- `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, and `MYSQL_ROOT_PASSWORD`
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, and `DB_PASSWORD`
- host port mappings and Tailscale-facing bind settings

Operator-facing server values such as server name, message, world ID, channel count, and rates move from Java constants into the mounted properties file. Protocol-sensitive constants, including MapleStory version and encryption behavior, remain code-owned.

The Java database bootstrap reads the environment variables with safe local-development defaults. It must not embed a real database password.

## Database Lifecycle

Repository SQL files are copied or mounted under MySQL's first-run initialization directory in a deterministic numeric order. The initialization runs only when the named database volume is empty.

Normal operations preserve data:

- `docker compose down` stops and removes containers but retains the named volume.
- `docker compose up --build` rebuilds the server image but retains the named volume.
- Database reset is an explicit helper command that asks for confirmation and runs `docker compose down --volumes` only after confirmation.

The documentation includes a backup command that writes a timestamped SQL dump outside containers and explains restore steps.

## Operator Workflow

The supported first-run flow is:

```powershell
git clone https://github.com/momoyuki/Ms-v206.git
cd Ms-v206
.\scripts\setup.ps1
.\scripts\start.ps1
```

`setup.ps1` verifies Docker Compose availability, creates local config files from templates, and reports the next configuration values to review. It does not start containers, delete data, or install external software.

The helper scripts provide start, stop, logs, backup, and reset operations. The README also lists their underlying Docker Compose equivalents for users who prefer direct commands.

## Ports and Tailscale

The initial implementation publishes:

- API: `8483`
- Login: `8484`
- Channel range beginning at `8584`, sized from the configured channel count

The README explains that a client must target the server host's Tailscale IP or MagicDNS name. Docker does not install or configure Tailscale; it only exposes the game services on the host for an existing Tailscale installation.

## Files to Add or Change

- `Dockerfile`
- `docker-compose.yml`
- `.dockerignore`
- `.env.example`
- `config/server.properties.example`
- `database/init/*` or an equivalent deterministic SQL initialization layout
- `scripts/setup.ps1`, `scripts/start.ps1`, `scripts/stop.ps1`, `scripts/logs.ps1`, `scripts/backup-db.ps1`, and `scripts/reset-db.ps1`
- `.gitignore`
- `src/main/java/hibernate.cfg.xml` and narrowly scoped Java configuration code
- `README.md`

## Verification

Before the PR is proposed, verify:

1. A fresh clone-style configuration can run the setup helper without overwriting local files.
2. `docker compose up --build` builds the Java server and initializes a fresh database.
3. The server starts only after the database health check is healthy.
4. `docker compose down` followed by `docker compose up` retains created database data.
5. Configuration changes documented as restart-only take effect after `docker compose restart server`.
6. Docker Compose configuration validates, the Maven build succeeds, and applicable tests/lint/type checks are reported.
7. The README commands and Tailscale connection instructions are accurate.

## Security and Safety

- No password is committed outside templates containing placeholder values.
- The database port remains unpublished by default.
- Reset is opt-in, interactive, and documented as destructive.
- Backups are written to a Git-ignored local directory.
