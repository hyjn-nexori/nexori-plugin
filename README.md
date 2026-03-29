# Nexori Plugin

Nexori is a Hytale network plugin focused on one job first: giving creators a
safe, guided way to establish trust between their servers without forcing them
onto a hosted backend.

Project planning and release/versioning rules live in
[`docs/ROADMAP.md`](D:\JanielNunez\hyjn-nexori\nexori-plugin\docs\ROADMAP.md).
The travel domain model lives in
[`docs/TRAVEL_MODEL.md`](D:\JanielNunez\hyjn-nexori\nexori-plugin\docs\TRAVEL_MODEL.md).

## Current Scope

This repository currently contains the first usable slice of the plugin:

- per-server identity generation with an Ed25519 keypair
- local persistence for server identity and bootstrap state
- short-lived bootstrap sessions for enrollment windows
- saved peer IPs persisted as JSON in the server data folder
- a lightweight peer manager UI based on custom pages
- bootstrap travel that collects public keys, gets a signed proof from each peer,
  and installs the verified trust bundle across enrolled servers
- destination targets stored on the destination server
- trusted destination target discovery and caching on the origin server
- configurable Nexori portals with secure trigger bindings
- built-in travel profiles for keeping, clearing, or applying inventory
- manual backup/recover commands for `APPLY_INVENTORY`
- guided in-game owner setup for destination targets and portal binding setup
- per-player draft-aware setup flows that resume after destination discovery travel
- target manager UI for listing, editing, and removing owner-managed targets

## Current Commands

Use these in game:

```text
/nexori help
/nexori status
/nexori peers
/nexori add <host:port>
/nexori remove <host:port>
/nexori clear
/nexoristart
/nexoritravel <host:port> [--targetId=<id>] [--arrivalPoint=<id>] [--travelProfile=<id>]
/nexoritarget
/nexoritargetwizard
/nexoritargetlist
/nexoritargetadd <targetId> <kind> <world> <arrivalPoint>
/nexoritargetshow <targetId>
/nexoritargetremove <targetId>
/nexoridiscover <host:port>
/nexoridiscovered [host:port]
/nexoriportalgive
/nexoriportallist
/nexoriportalshow <portalId>
/nexoriportalbind <portalId> <host:port> <targetId> [--travelProfile=<id>]
/nexoriportalunbind <portalId>
/nexoribackups
/nexorirecover <transferId>
/nexorimenu
```

Normal owner setup now expects:

- `/nexoritargetwizard` for the in-game target manager and guided target creation flow
- portal interaction with `F` for the guided portal setup flow

The raw command `/nexoritargetadd` is now mainly an advanced/manual path for
coordinate targets. Natural spawn targets are generated automatically per world,
and portal targets are generated automatically when a Nexori portal is placed.

Saved data currently lives under the plugin data directory:

- `config/configured-peers.json`
- `config/discovered-destination-targets.json`
- `config/destination-targets.json`
- `config/portal-instances.json`
- `config/trigger-bindings.json`
- `state/bootstrap-state.properties`
- `state/bootstrap-run.json`
- `state/inventory-transfer-backups.json`
- `state/inventory-transfer-receipts.json`
- `state/trust-bundle.json`
- `identity/*`

## Current Bootstrap Flow

Today the plugin does this:

1. collect each target server's public key
2. deliver a short-lived challenge
3. receive a signed proof back through referral payloads
4. verify the proof on the origin server
5. save a trusted bundle locally on the origin server
6. distribute that same bundle back out so every enrolled server installs it

## Destination Targets

The next layer above secure travel is the destination target system.

- the destination server offers explicit `DestinationTarget`s
- a travel payload now identifies a destination target instead of a free-form route
- each target can define a world and an arrival point id
- the first target kinds are `NATURAL_SPAWN`, `COORDINATE`, and `PORTAL`
- the plugin now auto-registers `<world>.natural_spawn` targets by reading each
  world's `config.json` spawn point
- the secure travel handler rejects unknown destination targets instead of accepting raw labels

## Release Line

The current committed milestone is `0.4.0`.

- `0.4.x` is for fixes and stability on the new owner setup UI
- `0.5.0` is planned for spawn/portal owner flow polish and more complete no-code loops
- `1.0.0` is the target for the first non-coder-friendly adventure network kit

The next active development line after this release is `0.5.0-SNAPSHOT`.

## Secure Referrals

Nexori now also includes a signed referral envelope for normal server-to-server
travel after bootstrap.

- the full payload is signed, not just one field
- the destination verifies the signature against the current trust bundle
- the payload type is explicit so future protocols can reuse the same envelope
- the first payload type implemented is `travel.direct`

This is now the foundation for destination targets, portals, discovery,
inventory-aware travel profiles, and the first complete guided owner setup
flows without redesigning the security model.

The next milestone is polishing those owner flows and simplifying the remaining
rough edges in the no-code setup experience.

## Development Notes

- Java 25 is required.
- The Gradle build expects a local Hytale install.
- If Hytale is installed outside the default location, pass `-Phytale_home=<path>`.

Example:

```powershell
.\gradlew.bat compileJava -Phytale_home=D:\JanielNunez\AppData
```

## Authentication Reminder

When running a local Hytale server for development, authenticate it with the
official flow:

```text
auth login device
auth persistence Encrypted
```

Never share the resulting encrypted auth material.
