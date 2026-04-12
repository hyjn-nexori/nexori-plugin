# Nexori Roadmap

## Product Direction

Nexori is a Hytale network kit for creators who want to build connected worlds,
queues, and instanced minigames without starting from a backend stack.

Nexori is optimized for:

- one tenant per region
- one lobby server
- N destination or game servers
- in-game owner workflows
- file-based persistence
- secure server-to-server trust and travel

Nexori is not trying to be:

- a global matchmaking backend
- a Redis/database platform
- an infinite generic rule engine

## Current Release Line: `2.0.0`

`2.0.0` is the first stable Nexori release where the minigame network loop is
already part of the shipped product.

That means the current product already supports:

- secure trust bootstrap between servers
- trust-bundle distribution
- signed secure travel referrals
- in-game peer setup
- local and remote destination targets
- portal placement and travel binding
- inventory-aware travel profiles
- inventory backup and recovery
- server rules
- lobby designation
- queue creation and countdown
- reusable game definitions
- queue portals and leave-queue portals
- instance template selection
- spawn slot setup for templates
- runtime per-match spawn placement
- built-in `LAST_PLAYER_ALIVE`
- manual third-party match resolution through the public API
- queue HUD and return HUD flows

## What `2.0.0` Means

At `2.0.0`, one owner should be able to build a tenant that looks like this:

1. players arrive in a lobby
2. players use portals to move around the network
3. some portals lead to adventure/travel destinations
4. some portals join queues
5. queues countdown and launch players into instanced matches
6. matches resolve through:
   - built-in `LAST_PLAYER_ALIVE`
   - or a third-party mod calling Nexori's public API
7. players return cleanly to the lobby

That is the core product story of the current release.

## What Ships In `2.0.0`

- secure trust bootstrap
- rerunnable trust-bundle updates
- peer address migration inside Nexori persistence on bundle rerun
- destination target discovery and caching
- labeled portals
- travel bindings
- queue bindings
- leave-queue bindings
- local and cross-server travel
- rules workspace
- minigame workspace
- lobby workspace
- game workspace
- queue workspace
- spawn slot setup workflow
- instance placement tracking
- return-to-lobby orchestration
- diagnostics capture and collect
- public API bridge for third-party mods

## What `2.0.0` Still Intentionally Does Not Try To Be

- multiple lobby servers sharing one queue
- cross-region shared matchmaking
- party matchmaking
- ranked matchmaking
- map voting
- backend dashboards
- database-backed orchestration
- advanced spectator systems
- generalized arbitrary rule scripting

Those may become future work, but they are not part of the contract of the
current stable line.

## Near-Term Release Discipline

After `2.0.0`, the line should stay disciplined:

- `2.0.x`
  - fixes
  - polish
  - owner-flow hardening
  - documentation
  - release-quality cleanup
- `2.1.0+`
  - only for new product scope that clearly expands beyond the current stable
    minigame network kit

## Near-Term Priorities After `2.0.0`

The next meaningful work should focus on:

- operator documentation
- example tenant packaging
- release polish
- better onboarding copy and UI guidance
- safer cleanup/admin utilities
- continued hardening of queue/placement/travel edge cases

## Possible Future Expansion Areas

These are valid future directions, but not promises for the immediate next
release:

- stronger world-role registration stories
- better packaged official sample tenants
- more built-in victory conditions
- richer player-facing HUD presentation
- safer admin import/export flows
- better tooling for migrating tenant addresses and topology changes

## Current Feedback Goal

Before Nexori keeps expanding too far beyond the current `2.0.0` line, the
project should spend time collecting real feedback from creators and server
owners.

Right now, one of the most important goals is to learn:

- whether the current product story feels useful in real Hytale workflows
- whether the in-game setup flow is intuitive enough for owners
- which parts of the tenant-building loop feel powerful
- which parts still feel confusing, incomplete, or too technical

That feedback matters because the next steps should be guided by what people
actually want to build with Nexori, not by continuing to add features in the
dark.

## Versioning Rules

Nexori uses semantic versioning:

- `MAJOR.MINOR.PATCH`

Examples:

- `2.0.0`
- `2.0.1`
- `2.1.0`

Meaning:

- `PATCH`
  - bug fixes
  - hardening
  - no major contract shift
- `MINOR`
  - meaningful new product capability
  - still backward compatible
- `MAJOR`
  - a new product contract or major compatibility shift

## Release Policy

Every release-oriented commit should:

1. keep the code compiling
2. keep docs aligned with the actual product
3. keep the manifest/version aligned with the intended milestone

For the current line, the main rule is simple:

- do not undersell `2.0.0` as if minigames were still future work
- do not overload `2.0.x` with a brand-new unrelated platform rewrite
