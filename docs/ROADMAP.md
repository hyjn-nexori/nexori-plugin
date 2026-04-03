# Nexori Roadmap

## Product Direction

Nexori is a Hytale network kit for people who want to build coordinated
multi-server experiences without needing a backend first.

The product direction now has two clear stages:

- `1.0.0`: a stable adventure multi-server kit
- `2.0.0`: a stable minigame network kit

The first stage is about safe travel, portals, inventory handling, and in-game
owner workflows.

The second stage is **not** "more random features." It is a focused step toward
supporting a simple but real minigame tenant flow:

- lobby
- queue
- arena
- return to lobby

All of this should stay inside Nexori's current philosophy:

- no hosted backend
- no database requirement
- no cloud orchestration
- JSON/config persistence is allowed, but not as the main owner workflow
- owners should still be able to do the important setup in game

For phase 2, Nexori is **not** trying to compete with a backend-heavy stack.
The product bet is simpler and more honest:

- one region per tenant
- no backend
- no Redis
- no database
- one Lobby+Queue Server per tenant/region
- N Arena Servers for horizontal minigame capacity
- simpler setup and lower operational complexity

## What Exists Today

Current foundation already built in the plugin:

- per-server identity generation with Ed25519
- trust bootstrap between servers
- trust bundle distribution to enrolled servers
- signed secure referral transport for protected server-to-server flows
- secure cross-server travel with `travel.direct`
- destination targets stored locally and editable in game
- built-in arrival behaviors for:
  - `NATURAL_SPAWN`
  - `COORDINATE`
  - `PORTAL`
- trusted remote destination discovery and local caching
- configurable Nexori portals and trigger bindings
- built-in travel inventory behaviors:
  - `KEEP_INVENTORY`
  - `CLEAR_INVENTORY`
  - `APPLY_INVENTORY`
- inventory backup and recovery flows
- local overwrite backup claims and remote recovery query/reply flow
- per-server rules and trusted remote rules apply/refresh flow
- unified HyUI admin shell for:
  - servers
  - rules
  - targets
  - diagnostics
- structured diagnostics capture to JSONL
- resumable diagnostics collect between trusted servers
- consolidated diagnostics output on the collector server
- owner-facing diagnostics summary/alerts UI focused on suspicious traffic and
  important failures

## Current Release Closure: `0.7.0`

Nexori `0.7.0` is the release that closes the three-phase diagnostics milestone
inside the adventure multi-server line.

That means `0.7.0` now includes:

- structured diagnostics capture to local JSONL per server
- trusted resumable diagnostics collect between servers
- consolidated diagnostics output on the collector server
- owner-facing diagnostics summary/alerts focused on suspicious traffic and
  important failures

`0.7.0` is still a phase-1 release, not the final adventure release.

After `0.7.0`, the expected line before `1.0.0` is:

- `0.7.x` for bug fixes
- `0.7.x` for UI polish
- `0.7.x` for docs and release prep
- `0.7.x` for hardening the current adventure feature set

`0.7.x` should **not** absorb minigame scope or other phase-2 work.

## Current Hardening Release: `0.7.1`

Nexori `0.7.1` is the current patch release line after the diagnostics
milestone closure.

`0.7.1` should stay narrow:

- fix friction in already-shipped owner flows
- polish the current HyUI/admin experience
- harden the adventure multi-server line before `1.0.0`

`0.7.1` should still avoid pulling in phase-2 or minigame scope.

## Current Stable Release: `1.0.0`

Nexori `1.0.0` should be the release that lets a non-coder build a simple
survival/adventure multi-server network with safe travel and in-game setup.

That means `1.0.0` is the version for:

- secure bootstrap for the network
- secure signed server-to-server travel
- portal-based travel between servers
- destination targets and bindings editable in game
- travel inventory policies already built into Nexori
- inventory backup/recovery
- server rules editable in game
- basic owner diagnostics and collector support
- stable owner happy path for adventure-style networks

At this point, `1.0.0` marks the clean close of phase 1.

After this stable cut:

- `1.0.x` is for fixes, polish, and owner-flow hardening
- `1.1.0+` should only begin when phase 2 work truly starts

Phase 1 should remain closed after `1.0.0`; phase 2 should not leak backward
into `1.0.x`.

### `1.0.0` Does Not Need

`1.0.0` should explicitly **not** include:

- queue orchestration
- match lifecycle
- minigame arena management
- instance allocation
- matchmaker backend
- procedural world generation
- universal minigame scripting

## After `1.0.0`: Path To `2.0.0`

## Goal For `2.0.0`

Nexori `2.0.0` should be the version that lets an owner build a simple
coordinated minigame network on top of the secure travel foundation.

The intended deployment model for the backend-less MVP is:

- `1 Lobby+Queue Server`
- `N Arena Servers`

This is a regional tenant model, not a globally coordinated matchmaking
platform.

The target experience is:

1. players enter a lobby
2. players join a queue
3. once the minimum player count is reached, a countdown starts
4. players are sent to an arena
5. the match ends under one supported win condition
6. players return to the lobby

This should feel like a real product step forward, but it must stay tightly
scoped and finishable.

## `2.0.0` Design Constraints

The following constraints are intentional and should stay explicit in the
roadmap:

- no backend
- no Redis
- no database
- no procedural world generation from scratch
- no giant generic minigame framework
- no many-condition victory engine at the start
- no large admin control center before the core loop works
- no deep mod compatibility layer on day one
- no dedicated queue authority service outside Hytale
- no global multi-region matchmaking
- no multiple lobby servers sharing one queue in the MVP

## Core `2.0.0` Product Decisions

### 1. World Role Registration Comes First

The first real product feature for phase 2 should be registering worlds by
role, not copying template files.

An owner should be able to take an existing world and register it as:

- `LOBBY`
- `ARENA`

This supports both:

- Nexori-provided template worlds later
- owner-made custom worlds immediately

For the MVP, `QUEUE` should not be treated as a required physical world role.
Queue state should primarily be logical and live inside the Lobby+Queue Server.

### 2. World Creation Is Template Copy, Not In-Game Generation

Nexori should support copying prebuilt template worlds that already ship with
the mod or live in a Nexori-controlled template directory.

That means:

- a lobby template can be copied into a real world
- a queue template can be copied into a real world when desired
- an arena template can be copied into a real world

This is intentionally simpler than procedural generation.

Template copy is a convenience layer on top of role registration, not the core
product foundation.

### 3. The Queue Lives Inside The Lobby+Queue Server

For the backend-less MVP, Nexori should **not** model queue coordination as a
separate service or authority outside Hytale.

The practical product shape is:

- one main Lobby+Queue Server
- N Arena Servers
- the queue state lives inside that Lobby+Queue Server
- players can remain in the lobby while queued
- launching a match means assigning an arena server and sending players there

The MVP must not depend on a distinct queue world or queue-only server to
function.

### 4. Core Definitions For The MVP

The MVP should stay explicit about the main configuration concepts:

- `LobbyDefinition`
  - identifies the lobby world/server context
  - defines the player entry/return location
- `QueueDefinition`
  - is a logical queue, not a required world
  - has its own `queueId`, player-count rules, countdown, and arena-selection
    policy
  - lives inside the Lobby+Queue Server
- `ArenaDefinition`
  - identifies an arena world/server destination
  - represents the arena-side configuration needed for match launch and return

Portals should not own the queue.

Instead:

- a queue is created separately as a `QueueDefinition`
- a portal can optionally reference a `queueId`
- multiple portals may point to the same queue
- the portal binding is the MVP source of truth for "this lobby offers access to
  this queue"
- a portal should use explicit behavior such as:
  - `TRAVEL`
  - `JOIN_QUEUE`

This keeps travel logic and queue logic separate while still reusing the portal
system that already exists.

The queue should be the owner of the relationship toward arenas:

- a `QueueDefinition` lists the arena ids it may launch into
- an `ArenaDefinition` should not mirror that relationship back with its own
  allow-list

### 5. Reuse Existing Travel, Inventory, And Recovery Logic

Phase 2 should reuse the systems that already exist instead of inventing new
parallel infrastructure:

- secure travel
- travel profiles
- destination targets
- portals
- inventory backup/recovery
- rules
- diagnostics

Minigame flow should be expressed through Nexori's existing secure movement
model whenever possible.

### 6. Support Only One Victory Condition In The MVP

For the first minigame release, Nexori should support one tightly scoped win
condition:

- `LAST_PLAYER_ALIVE`

Why this is a good MVP:

- it maps naturally to arena flow
- it does not require a highly generic objective system
- it stays aligned with the "simple, controlled, finishable" direction

Other victory models can come later if the first one lands well.

## `2.0.0` MVP Scope

The `2.0.0` MVP should include:

- role registration for worlds:
  - lobby
  - arena
- queue definitions as logical entities, not required queue worlds
- queue access expressed through explicit portal bindings that reference
  `queueId`
- queue-to-arena relationships owned by the queue definition
- world template copy support
- support for using custom owner-made worlds instead of templates
- queue definition/configuration
- minimum player count
- queue countdown
- queue state owned by the Lobby+Queue Server
- launch from lobby/queue to an arena server
- one arena lifecycle with `LAST_PLAYER_ALIVE`
- return all players to lobby when the match ends
- reuse of existing travel inventory policy
- enough HyUI/player feedback for:
  - joined queue
  - queue waiting
  - countdown starting
  - match starting
  - match ended
  - returning to lobby
  - recovery available if something goes wrong

The MVP does **not** require a separate physical queue world. Players may stay
in the lobby until countdown and match launch begin.

## What `2.0.0` Should Explicitly Exclude

Even if they sound attractive, these should stay out of the first minigame
major release:

- dedicated queue authority services outside Hytale
- multiple lobby servers sharing one queue
- global multi-region queueing
- cross-region shared state
- dynamic elastic instance orchestration
- procedural arena generation
- multiple queue types
- ranked matchmaking
- parties and party matchmaking
- map voting
- tournament ladders
- advanced spectator systems
- many different win conditions
- generalized scripting of arbitrary game rules
- external backend dashboards
- database-backed persistence
- heavy compatibility layers for other mods

## Internal Systems To Reuse

The `2.0.0` work should reuse these current Nexori building blocks:

- trust bootstrap and signed referral transport
- secure cross-server travel
- destination targets
- portals and trigger bindings
- travel profiles and inventory semantics
- inventory recovery for failure handling
- rules for per-server behavior
- diagnostics for suspicious traffic and operational failures

This reuse is important because phase 2 should feel like a product evolution,
not a second unrelated system.

## Backend-Less Deployment Model

The roadmap should stay honest about what Nexori is and is not optimizing for.

For the backend-less MVP, the clearest deployment model is:

- one tenant per region
- one Lobby+Queue Server for that region
- N Arena Servers attached to that lobby/queue server

This means:

- Nexori is well suited for simple or medium regional tenants
- the lobby/queue side scales mostly vertically
- the arena side scales more horizontally by adding more game servers
- a second region means deploying a second tenant stack

Nexori is **not** trying to be the most scalable global queue architecture.
It is trying to be a simpler, usable, no-backend way to run a real multiserver
tenant.

## Multi-Region Positioning

Multi-region should be described conservatively in the roadmap.

If an owner wants NA, EU, SA, or other regions, the expected model is:

- each region gets its own Lobby+Queue Server
- each region gets its own Arena Servers
- each region is treated as separate infrastructure

There is no shared global queue or shared global state in the current
backend-less direction.

## Persistence Strategy For `2.0.0`

Still no backend.

Persistence should remain file-based and explicit:

- world template registry/config
- world role registry for lobby/arena
- queue definitions
- active queue state
- active match state
- minimal recovery state when needed

The goal is not to invent a general data platform.
The goal is to keep enough state to make lobby -> queue -> arena -> lobby
reliable.

## Product Risks And Scope Risks

### 1. The Lobby+Queue Model Must Stay Simple

If phase 2 tries to coordinate many lobby servers against one shared queue
without a backend, the scope will expand too fast.

For the MVP, one Lobby+Queue Server per tenant/region is the right compromise.

### 2. World Copy Must Only Target Safe, Unloaded Worlds

Template copy sounds simple, but world files can become risky if Nexori copies
into active/live loaded worlds.

The roadmap should assume:

- copy only into safe target world names
- do not overwrite active worlds casually
- keep the first version conservative

### 3. Minigame Rules Can Explode In Scope

The biggest feature trap is building a "generic minigame platform" too early.

The roadmap should stay disciplined around:

- one victory condition
- one queue flow
- one return-to-lobby flow
- enough polish to feel good
- no universal rule engine yet

### 4. Multi-Region Must Stay A Deployment Story, Not A Global System

Trying to merge regions into one shared queue or one shared match state would
pull the product toward backend problems it is explicitly avoiding.

For now, regional scaling should mean deploying another tenant, not sharing one
global queue.

### 5. Inventory Semantics Need Clear Defaults

Minigame owners should not have to invent inventory behavior from scratch.

The roadmap should assume that minigame flow reuses current travel inventory
policies, rather than introducing a second unrelated inventory system.

### 6. Failure Recovery Must Be Productized, Not Perfected

If a player disconnects, a server crashes, or a transfer fails mid-match, the
first goal is graceful recovery and return-to-lobby behavior.

The goal is **not** to solve every distributed edge case before shipping.

## Milestones After `1.0.0`

These milestones are intentionally narrow and should lead to `2.0.0` without
becoming a platform rewrite.

### `1.0.x` Release Hardening

- fix bugs found after `1.0.0`
- tighten docs and owner happy path
- avoid pulling in minigame scope here

### `1.1.0` World Role Registration

- allow existing worlds to be registered without template copy
- add world role registration:
  - lobby
  - arena

- add logical queue definitions with their own `queueId`
- do not require a physical queue world for the MVP
- define the relationship between:
  - lobby definitions
  - queue definitions
  - arena definitions
- allow portals to reference queues explicitly instead of owning queue state
- use portal bindings as the MVP source of truth for lobby -> queue access
- make the queue definition the source of truth for queue -> arena access

### `1.2.0` World Templates

- add support for Nexori-managed world templates
- allow owners to copy a template into a real world folder
- keep templates as a convenience on top of the world role registry

### `1.3.0` Queue Flow MVP

- define queue configurations
- support join queue / leave queue
- support minimum players
- support queue countdown
- keep queue state inside the Lobby+Queue Server
- keep players physically in the lobby while queued
- do not require multiple lobbies or a separate queue service

### `1.4.0` Arena Launch And Match Lifecycle

- consume a queue's `READY` batch as the launch input
- choose the arena from the queue-owned `arenaIds`
- reuse secure cross-server travel for lobby -> arena dispatch
- launch queued players into an arena
- start a match with one supported victory condition:
  - `LAST_PLAYER_ALIVE`
- detect match end
- persist enough runtime state to survive simple interruptions

### `1.5.0` Return To Lobby And Failure Recovery

- send players back to lobby after match end
- handle disconnect/failure cases conservatively
- reuse current recovery and diagnostics surface where it helps
- make the minigame loop survivable even when things go wrong

### `1.6.0` UX And Polish For Owners And Players

- better queue and countdown feedback
- better match start/end feedback
- simple HyUI/admin flow for configuring queue networks
- no giant admin dashboard, only enough UI to make the loop usable

### `2.0.0` Stable Minigame Network Kit

Nexori `2.0.0` should mean:

- owners can create or register worlds for lobby and arena roles
- owners can define queues as logical entities inside the Lobby+Queue Server
- owners can wire a basic minigame tenant without external backend
- owners can run a backend-less tenant with:
  - one Lobby+Queue Server
  - N Arena Servers
- players can flow through:
  - lobby
  - queue
  - arena
  - return to lobby
- one supported victory condition works reliably
- existing travel/inventory/recovery foundations are reused cleanly
- the minigame loop feels like a product, not a demo

## Versioning Rules

Nexori should use semantic versioning with **three numbers only**:

- `MAJOR.MINOR.PATCH`

Examples:

- `0.7.0`
- `0.7.1`
- `1.0.0`
- `1.0.1`
- `1.1.0`
- `2.0.0`

Meaning:

- `PATCH`
  - bug fixes
  - internal fixes
  - no breaking config or API changes
- `MINOR`
  - new features
  - new owner flows
  - new minigame milestones
  - still backward compatible
- `MAJOR`
  - breaking changes
  - config or product model changes that deserve a new contract

Near-term interpretation for the current line:

- `0.7.0`
  - closes the diagnostics milestone
- `0.7.x`
  - fixes, polish, and hardening before `1.0.0`
- `1.0.0`
  - closes the first adventure multi-server product line cleanly

## Release Policy

Every meaningful milestone should be captured by a commit that:

- updates the version
- updates the roadmap if scope changed
- keeps the code compiling

Simple release checklist:

1. confirm the roadmap still matches the intended milestone
2. update the version in the project files
3. confirm the plugin still builds
4. commit with a release-oriented message

The main discipline after this point should be:

- keep `1.0.x` narrow and stable
- do not leak phase 2 into `1.0.x`
- start `1.1.0+` only when the minigame work is truly beginning
- keep `2.0.0` tightly scoped around real minigame flow
- avoid turning Nexori into a generic infinite platform before the first
  minigame loop actually works
