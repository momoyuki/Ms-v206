SALE OF THIS FONT IS PROHIBITED. IF A USER USES IT FOR SALE, THEY WILL BE UNSUBSCRIBED AND LOSE ALL RECENT UPDATES.

# Maplestory-v206
Source code Maplestory v206.

## Docker Setup (Tailscale)

This setup is for a private server shared through an existing Tailscale network. It does not install or configure Tailscale. Point the game client at this host's Tailscale IP or MagicDNS name.

### Prerequisites

- Docker Desktop with Docker Compose v2
- Tailscale connected on the server host and each player's machine

### Quick Start

```powershell
git clone https://github.com/momoyuki/Ms-v206.git
cd Ms-v206
.\scripts\setup.ps1
# Edit .env and config/config.yaml before the first start.
.\scripts\start.ps1
.\scripts\logs.ps1
```

`docker compose down` stops the services but preserves the `ms-v206-db-data` Docker volume and all player data. `scripts/reset-db.ps1` is destructive: it removes that volume and permanently deletes database data.

### Configuration

| Setting | File | Effect |
| --- | --- | --- |
| Database credentials and host game ports | `.env` | Run `setup.ps1` after changing YAML ports, then `docker compose up -d`. |
| Server and gameplay settings | `config/config.yaml` | Restart the server after changes. |

`setup.ps1` synchronizes `SERVER_API_PORT`, `SERVER_LOGIN_PORT`, and `CHANNEL_PORT_RANGE` from YAML while preserving database credentials and the host-facing `API_PORT` and `LOGIN_PORT` choices in `.env`. With the defaults, channels use `8585-8594` (`network.loginPort + 101` through `network.loginPort + 100 + server.channels`). MySQL is intentionally not published to the host.

### `config/config.yaml`

| Key | Default | Valid values | Runtime consumer |
| --- | --- | --- | --- |
| `server.name` | `v206` | Non-empty text | Server identity and event message |
| `server.message` | `v206` | Non-empty text | Login server message |
| `server.worldId` | `Bera` | Existing `WorldId` name | World selection |
| `server.channels` | `10` | 1-100 | Channel creation and Compose port range |
| `server.userLimit` | `20` | Positive integer | Server user limit |
| `server.maxCharacters` | `30` | Positive integer | Account slot creation and character allocation |
| `network.loginPort` | `8484` | 1-65435 | Login listener and derived channel listeners |
| `network.apiPort` | `8483` | 1-65535 | API listener |
| `rates.mobExp` | `50` | Positive integer | Mob EXP rate |
| `rates.mobMeso` | `2` | Positive integer | Mob meso rate |
| `rates.mobDrop` | `1` | Positive integer | Mob item drop rate |
| `gameplay.hideGmOnLogin` | `false` | Boolean | Compatibility-only; no active runtime consumer |
| `gameplay.startMap` | `4000011` | Non-negative map ID | New character start map |
| `gameplay.hubMap` | `100000000` | Non-negative map ID | Hub warp destination |
| `drops.remainSeconds` | `120` | Positive integer | Drop expiration timer |
| `drops.ownershipSeconds` | `30` | Positive integer | Drop ownership timer |
| `events.randomPortalChance` | `0` | 0-1000 | Random portal spawn chance |
| `events.runeRespawnMinutes` | `10` | Positive integer | Rune respawn timer |
| `events.runeCooldownMinutes` | `0` | Non-negative integer | Rune cooldown timer |

YAML changes need `docker compose restart server`. If `network.loginPort`, `network.apiPort`, or `server.channels` changes, run `./scripts/setup.ps1` first, then `docker compose up -d`.

### Operations

```powershell
.\scripts\stop.ps1
.\scripts\start.ps1
.\scripts\backup-db.ps1
.\scripts\reset-db.ps1
```

# [Features]

All Classes can be created. Some might not work as intended
Some Bosses work (Not 100% which ones work and which don't, some were fixed, others not)
Cash Shop Works (Has most, if not entire WZ Directory added into the SQL database)
Guilds Work
Custom Quick Move
Others, check in game / source code to see

# [Quick Tutorial]
*Check Pom.xml to see what requirements are needed (JDK 14, MySQL 5.1.39)
1) Open the folder in IntelliJ and wait for everything to index.
2) Go to src/main/java/ and open hibernate.cfg.xml. Add your MySQL Password under Password, change username root if you have a different username for MySQL.
3) Check File Structure (CTRL+ALT+SHIFT+S) to ensure you have OpenJDK 14 selected.
4) Open MySQL and create a new database v206, run each individual .sql file inside the main folder / sql from 1-10 in order, plus the extras at the bottom.
5) go to src/main/java/net.swordie.ms/ and right-click Server and click Run or Debug. If everything is setup properly, the last item loaded into Console will be channels.

# [Auth Hook]

Download Visual C++ Redistributable:
x86: https://aka.ms/vs/17/release/vc_redist.x86.exe
x64: https://aka.ms/vs/17/release/vc_redist.x64.exe

1) Open AuthHook.sln in Visual Studio
2) Open global.h and change server ip to whatever you want
3) Open discord.c and scroll down to line 69 for tutorial
4) Right-click the source and click properties to change the .dll name or change upon build
5) Compile and move .dll to your v206 client folder.

# [Launcher]

Download .NET Framework 4.8 runtime: https://dotnet.microsoft.com/en-us/download/dotnet-framework/net48

1) Open SwordieLauncher.sln in Visual Studio
2) Open Client.cs and change IP to what you want
3) Right-Click Form1.cs and click View Code, change v206.dll to whatever your AuthHook dll name is.
4) Compile and move the launcher to your v206 client folder.

If completed successfully, you will be able to launch MapleStory v206.
DISCORD https://discord.com/invite/F3gxsSTuHV
