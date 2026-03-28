# Nexori Roadmap

## Product Direction

Nexori is a Hytale network kit for people who want to build a secure multi-server
experience without needing a backend first.

The primary audience for the first product is:

- server owners who do not want to code
- creators who want to build an adventure network with in-game tools
- modders who want a safe transfer foundation they can build on later

The product direction is:

- bootstrap trust between servers with per-server keypairs
- use signed referrals for all protected server-to-server travel
- let owners configure their network from inside the game
- keep JSON files as persistence, but never make JSON editing the normal workflow

## What Exists Today

Current `0.1.0` foundation:

- per-server identity generation with Ed25519
- peer discovery and trust bootstrap
- trust bundle distribution to all enrolled servers
- signed secure referral envelope
- first secure travel payload type (`travel.direct`)
- basic peer management UI

## Target For 1.0.0

Nexori `1.0.0` should let a non-coder install the mod on multiple servers and
build a simple but real network completely in game.

That means `1.0.0` should include:

- secure bootstrap for all servers in the network
- signed server-to-server travel built on the trust bundle
- route definitions stored in config and editable from in-game UI
- portal-based travel for adventure/survival style networks
- route behaviors such as:
  - send to spawn
  - send to portal target
  - send into a queue
  - return to lobby
- clear rejection messages when travel fails
- reset/rebootstrap tools for owners
- solid docs for both no-coders and modders

`1.0.0` does **not** need to include:

- hosted backend
- complex distributed matchmaking
- cloud orchestration
- monetization features

## Planned Milestones

### `0.1.0` Foundation

- bootstrap works end-to-end
- signed referral envelope exists
- first secure travel test exists

### `0.2.0` Route System

- add `RouteDefinition`
- add `RouteStore` and `RouteService`
- resolve `routeKey` in the destination server
- add initial route config persistence

### `0.3.0` Portal Basics

- add portal route behavior
- let owners define destination server + route + entry point
- support arrival at predefined portal/spawn targets

### `0.4.0` In-Game Route UI

- create guided forms for route creation
- create guided forms for portal setup
- allow owners to select current position/orientation in game
- reduce command usage for normal setup

### `0.5.0` Queue Basics

- add simple queue route behavior
- support one lobby -> one queue -> one destination flow
- support route-based dispatch to minigame entry points

### `0.6.0` Travel Outcomes And Errors

- explicit success/failure states
- better owner-facing diagnostics
- protection against stale or replayed travel payloads
- cleaner recovery/reset flows

### `0.7.0` Inventory And Return Flows

- secure inventory transfer payloads
- return-to-origin or return-to-lobby routes
- route behaviors that combine travel + inventory rules

### `0.8.0` Owner Experience

- polish UI wording and forms
- presets for common network types
- easier route linking between servers
- better operational commands

### `0.9.0` Hardening

- migration handling
- upgrade safety
- more validation and compatibility checks
- documentation and setup guides

### `1.0.0` Stable Adventure Network Kit

- complete no-code happy path
- stable secure travel APIs
- stable portal and route workflows
- stable bootstrap and reset flows

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
  - new route behaviors
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

- current milestone: `0.1.0`
- next in-progress line after release: `0.1.1-SNAPSHOT` or `0.2.0-SNAPSHOT`
- first bugfix after release: `0.1.1`
- next feature milestone: `0.2.0`

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
