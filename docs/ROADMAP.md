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

Current `0.2.0` foundation:

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

### `0.3.0` Trigger Bindings

- add trigger binding definitions
- let owners link a command or local portal trigger to a remote destination target
- let trigger bindings select a built-in travel profile
- add trusted destination target discovery and caching from the origin server

### `0.4.0` In-Game Owner UI

- create guided forms for destination target creation
- create guided forms for portal setup
- create guided forms for trigger binding setup
- allow owners to select current position/orientation in game
- reduce command usage for normal setup

### `0.5.0` Spawn And Portal Owner Flows

- polish owner-facing spawn and portal setup flows
- support destination target discovery from the origin server
- support survival/adventure travel loops without custom code
- reduce remaining command-heavy setup steps

### `0.6.0` Travel Outcomes And Errors

- explicit success/failure states
- better owner-facing diagnostics
- protection against stale or replayed travel payloads
- cleaner recovery/reset flows

### `0.7.0` Inventory Travel Profiles

- secure inventory transfer payloads
- return-to-origin or return-to-hub presets built from targets + trigger bindings
- travel profiles that combine arrival + inventory rules

### `0.8.0` Owner Experience

- polish UI wording and forms
- presets for common survival/adventure network types
- easier target linking between servers
- better operational commands

### `0.9.0` Hardening

- migration handling
- upgrade safety
- more validation and compatibility checks
- documentation and setup guides

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
- `0.2.0`
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

- current milestone: `0.2.0`
- next in-progress line after release: `0.2.1-SNAPSHOT` or `0.3.0-SNAPSHOT`
- first bugfix after release: `0.2.1`
- next feature milestone: `0.3.0`

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
