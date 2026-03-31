# Nexori Roadmap

## Product Direction

Nexori is a Hytale network kit for people who want to build a secure multi-server
experience without needing a backend first.

The primary audience for the first product is:

- server owners who do not want to code
- creators who want to build a survival/adventure network with in-game tools
- modders who want a safe transfer foundation they can build on later

The product direction is:

- bootstrap trust between servers with per-server keypairs
- use signed referrals for all protected server-to-server travel
- let owners configure their network from inside the game
- define the travel behaviors in Nexori itself instead of making owners invent
  their own protocol
- keep JSON files as persistence, but never make JSON editing the normal workflow

## What Exists Today

Current `0.6.0` foundation:

- per-server identity generation with Ed25519
- peer discovery and trust bootstrap
- trust bundle distribution to all enrolled servers
- signed secure referral envelope
- first secure travel payload type (`travel.direct`)
- basic peer management UI
- destination target definitions stored on the destination server
- automatic `<world>.natural_spawn` target registration
- secure destination target resolution on arrival
- first concrete arrival execution for:
  - `NATURAL_SPAWN`
  - `COORDINATE`
  - `PORTAL`
- trusted destination target discovery and caching on the origin server
- configurable Nexori portal instances with local trigger bindings
- first built-in travel profiles:
  - `KEEP_INVENTORY`
  - `CLEAR_INVENTORY`
  - `APPLY_INVENTORY`
- manual and UI-driven backup/recover flow for `APPLY_INVENTORY`
- local destination overwrite backups and direct local claims
- admin controls for recovery enable/disable and per-player backup limits
- top-level HyUI admin shell for servers, rules, and targets
- guided in-game destination target management and creation flows
- guided in-game portal setup and trigger binding flows
- HyUI portal admin entry from both the admin shell and direct portal interaction
- HyUI recovery page for normal players
- per-player and per-portal draft-aware setup resume after discovery travel
- owner flows that capture current world, position, and facing direction in game
- per-server Nexori rule groups with trusted remote refresh and apply flows
- bundle-backed trusted network view plus local bootstrap peer editing for rebootstrap
- resettable bootstrap runs and clearer bootstrap reporting for owners
- rerun safety checks so active trusted networks only accept bootstrap reruns from verified servers

## Target For 1.0.0

Nexori `1.0.0` should let a non-coder install the mod on multiple servers and
build a simple but real survival/adventure network completely in game.

That means `1.0.0` should include:

- secure bootstrap for all servers in the network
- signed server-to-server travel built on the trust bundle
- destination targets stored in config and editable from in-game UI
- trigger bindings stored in config and editable from in-game UI
- travel profiles defined by Nexori and selectable from in-game UI
- portal-based travel for survival/adventure style networks
- configurable spawn and coordinate targets between servers
- inventory rules for safe cross-server travel
- built-in arrival behaviors such as:
  - travel to natural spawn
  - travel to coordinate
  - travel to portal
- clear rejection messages when travel fails
- reset/rebootstrap tools for owners
- solid docs for both no-coders and modders

`1.0.0` does **not** need to include:

- hosted backend
- minigame queue orchestration
- distributed matchmaking
- instanced minigame lifecycle management
- cloud orchestration
- monetization features

## Planned Milestones

### `0.1.0` Foundation

- bootstrap works end-to-end
- signed referral envelope exists
- first secure travel test exists

### `0.2.0` Destination Targets

- add destination target definitions
- add destination target storage and runtime resolution
- resolve `destinationTargetId` in the destination server
- auto-register natural spawn targets from world config
- execute the first destination-target arrivals on the destination server
- define the first built-in arrival kinds:
  - `NATURAL_SPAWN`
  - `COORDINATE`
  - `PORTAL`

### `0.3.0` Portal Trigger Bindings And Travel Profiles

- add trigger binding definitions
- let owners link a local portal trigger to a remote destination target
- let trigger bindings select a built-in travel profile
- add trusted destination target discovery and caching from the origin server
- add configurable Nexori portal instances with admin-only management UI
- add first built-in travel profiles for inventory-aware travel
- add manual backup/recover flows for `APPLY_INVENTORY`

### `0.4.0` In-Game Owner UI

- create guided forms for destination target creation
- create guided forms for portal setup
- create guided forms for trigger binding setup
- allow owners to select current position/orientation in game
- reduce command usage for normal setup

### `0.5.0` Inventory Recovery UI

- add a simple in-game recovery page for normal players
- let players inspect their own recent inventory transfer backups without reading
  raw ids from chat
- let players try recovery from the UI instead of depending on raw recovery
  commands
- add direct local claims for destination-side overwrite backups
- add admin controls for recovery locks and per-player backup limits
- keep the low-level backup and recover commands as admin/debug tools

### `0.6.0` Global Admin UI And Server Rules

- turn the Nexori menu into a true top-level admin UI instead of leaving peer
  management as a separate island
- wrap the current owner flows for servers, targets, and portals inside one
  in-game admin shell
- add a rules view for per-server Nexori settings such as recovery mode and
  per-player backup limits
- support trusted round-trip refresh and apply flows for remote server rules
  using Nexori referrals instead of a backend
- make server-wide Nexori settings manageable in game before adding more
  diagnostics or maintenance surface area

### `0.7.0` Travel Outcomes And Diagnostics

- add clearer travel success/failure states for players and owners
- improve diagnostics when a portal, binding, target, or inventory transfer
  fails
- make failed travel and recovery states easier to understand without digging
  through logs
- keep the failure paths functional before spending time on visual polish

### `0.8.0` Reset And Maintenance Tools

- add owner tools to reset or rebootstrap trust safely
- add maintenance tools for clearing stale discovery cache, drafts, and stuck
  recovery state
- add safer operational commands for repairing a live survival/adventure
  network
- make maintenance flows part of the product instead of ad-hoc manual fixes

### `0.9.0` Hardening

- strengthen replay/stale payload protection
- improve validation around config, bindings, targets, and transfer state
- add migration handling and upgrade safety for pre-`1.0.0` releases
- improve compatibility checks so owners get safer upgrades between milestones

### `0.10.0` Docs And Release Prep

- write setup guides for no-code survival/adventure owners
- write troubleshooting guides for travel, recovery, and trust bootstrap issues
- document the owner happy path end-to-end
- prepare the project for a stable `1.0.0` release instead of adding more
  pre-release surface area

### `1.0.0` Stable Adventure Network Kit

- complete no-code happy path
- stable secure travel APIs
- stable portal and destination target workflows
- stable bootstrap and reset flows
- stable survival/adventure network setup for owners without code

## Beyond 1.0

After `1.0.0`, Nexori can expand into:

- minigame queue flows
- instance allocation
- match orchestration
- more developer-facing APIs for advanced custom game modes

## Versioning Rules

Nexori should use semantic versioning with **three numbers only**:

- `MAJOR.MINOR.PATCH`

Examples:

- `0.1.0`
- `0.3.0`
- `1.0.0`
- `1.0.1`

Meaning:

- `PATCH`
  - bug fixes
  - internal fixes
  - no breaking config or API changes
- `MINOR`
  - new features
  - new arrival behaviors
  - new UI features
  - still backward compatible
- `MAJOR`
  - breaking changes
  - config format changes that require migration
  - API contract changes

While the project is still in `0.x`, we should still behave in a disciplined
way:

- `0.x.y` is still pre-`1.0`, but each `MINOR` should represent a real
  milestone, not random churn
- avoid breaking setup flows casually between `0.x` releases
- if a breaking change is needed before `1.0.0`, document it clearly in the
  roadmap and release notes

Do **not** use four-number versions like `1.0.0.1`.

If we want prereleases, use suffixes such as:

- `0.2.0-alpha.1`
- `0.2.0-beta.1`
- `1.0.0-rc.1`

Recommended workflow:

- code in progress can use `-SNAPSHOT`
- when a milestone is ready, cut a real release version
- bug fixes after release increment `PATCH`

Examples:

- current release: `0.6.0`
- next in-progress line after release: `0.6.1-SNAPSHOT` or `0.7.0-SNAPSHOT`
- first bugfix after release: `0.6.1`
- next feature milestone: `0.7.0`

## Release Policy

For now, every meaningful project milestone should be captured by a commit that:

- updates the version
- updates the roadmap if scope changed
- keeps the code compiling

Simple release checklist:

1. confirm the roadmap still matches the intended milestone
2. update the version in the project files
3. confirm the plugin still builds
4. commit with a release-oriented message

Not every tiny commit must be a release, but every release commit should be
intentional and documented.
