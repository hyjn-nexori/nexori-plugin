# Nexori Plugin

Nexori is a Hytale network plugin for creators who want to turn separate
servers into one connected experience without building a backend first.

With Nexori, one owner can set up:

- trusted server-to-server travel
- labeled portals
- lobbies
- queues
- instanced minigames
- return-to-lobby flows
- in-game owner setup for the full tenant

The current release line is `2.0.0`: the first stable **minigame network kit**
built on top of Nexori's secure travel foundation.

## What Nexori Does

Nexori is designed around one simple product story:

1. connect your servers into one trusted network
2. define where players can arrive
3. wire portals and queue access in game
4. launch players into instanced matches
5. return them cleanly when the match resolves

This lets one tenant mix:

- adventure servers
- lobby servers
- queue portals
- instanced minigames
- local target travel inside the same server/world
- secure travel between different servers

## Key Features

- trust bootstrap between servers with a distributed trust bundle
- signed secure referrals for protected server-to-server flows
- destination targets for:
  - natural spawn
  - coordinate targets
  - portal targets
- labeled Nexori portals with trigger bindings
- built-in travel inventory policies:
  - `KEEP_INVENTORY`
  - `CLEAR_INVENTORY`
  - `APPLY_INVENTORY`
- inventory backup and recovery support
- in-game owner menu for:
  - servers
  - portals
  - rules
  - minigames
- lobby setup and queue orchestration
- reusable game definitions that point to remote arena servers and instance
  templates
- queue-to-portal binding
- instanced arena launch and return flow
- spawn slot setup for instance templates
- runtime placement of players into per-match spawn slots
- queue HUD and return HUD feedback
- public minigame API for third-party mods that want Nexori to handle return
  flow after a manual win/loss decision
- structured diagnostics and trusted diagnostics collect flows

## Who Nexori Is For

Nexori is for creators who want to build connected Hytale tenants such as:

- one lobby plus several adventure servers
- one lobby plus queue-driven minigames
- one tenant per region without Redis, SQL, or hosted backend services
- a hybrid network with portals, queues, and instanced matches living together

Nexori is **not** trying to be:

- a globally coordinated backend platform
- a database-heavy orchestration layer
- a giant generic minigame scripting engine

## Current Release: `2.0.0`

`2.0.0` is the first release where Nexori's minigame network loop is a real
product slice instead of future roadmap work.

That includes:

- lobby designation
- queue creation and countdown
- reusable game definitions
- instance template selection
- return trigger selection
- queue launch into instanced matches
- spawn slot authoring for instance templates
- runtime per-match spawn assignment
- built-in `LAST_PLAYER_ALIVE` support
- manual match resolution support for third-party mods through the public API
- return-to-lobby flow after match resolution

## Public API

Nexori's public minigame integration story is intentionally narrow:

- the third-party mod detects gameplay state itself
- the third-party mod decides when a player won or lost
- the third-party mod calls Nexori to resolve that outcome
- Nexori handles the delayed return flow

Main entrypoints live in
[NexoriMinigameApi.java](D:/JanielNunez/hyjn-nexori/nexori-plugin/src/main/java/io/github/hyjn/nexori/plugin/api/minigame/NexoriMinigameApi.java).

Detailed docs:

- [docs/PUBLIC_API.md](D:/JanielNunez/hyjn-nexori/nexori-plugin/docs/PUBLIC_API.md)
- example integration repo:
  [nexori-public-api-demo](https://github.com/hyjn-nexori/nexori-public-api-demo)

## Operator Docs

Start here if you want to run Nexori as an owner/operator:

- [docs/OPERATOR_GUIDE.md](D:/JanielNunez/hyjn-nexori/nexori-plugin/docs/OPERATOR_GUIDE.md)
- [docs/ROADMAP.md](D:/JanielNunez/hyjn-nexori/nexori-plugin/docs/ROADMAP.md)
- [docs/TRAVEL_MODEL.md](D:/JanielNunez/hyjn-nexori/nexori-plugin/docs/TRAVEL_MODEL.md)

## Main Owner Flow

Today the intended happy path is:

1. install Nexori on each server
2. In the game run /nexorimenu command
3. Add your local peers in the Servers view
4. run `Initial Setup` to build and distribute the trust bundle
5. set one lobby server
6. define portals, games, queues, and rules from the in-game menu
7. attach queues to portals
8. test the player loop:
   - lobby
   - queue
   - match launch
   - match end
   - return to lobby

## Commands

For most owners, the main entrypoint is:

```text
/nexorimenu
```

That opens the full in-game admin menu.

Additional command surfaces still exist for diagnostics, recovery, advanced
manual flows, and debugging. Use:

```text
/nexori help
```

for the current command list.

## Persistence

Nexori stores its config and state under the plugin data directory.

Important files include:

- `config/configured-peers.json`
- `config/discovered-destination-targets.json`
- `config/destination-targets.json`
- `config/portal-instances.json`
- `config/trigger-bindings.json`
- `config/queues.json`
- `config/lobbies.json`
- `config/arenas.json`
- `config/server-rule-groups.json`
- `config/network-lobby.json`
- `config/instance-spawn-slots.json`
- `state/trust-bundle.json`
- `state/bootstrap-run.json`
- `state/match-sessions.json`
- `state/arena-active-matches.json`
- `state/inventory-transfer-backups.json`
- `identity/*`

## Development Notes

- Java 25 is required
- the Gradle build expects a local Hytale install
- if Hytale is installed elsewhere, pass `-Phytale_home=<path>`

Example:

```powershell
.\gradlew.bat compileJava -Phytale_home=D:\USER\SOME_LOCATION
```

## Authentication Reminder

When running local Hytale servers for development:

```text
auth login device
auth persistence Encrypted
```

Never share your encrypted auth material.
