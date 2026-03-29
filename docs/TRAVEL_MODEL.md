# Nexori Travel Model

## Purpose

This document defines the travel model Nexori should follow for the `1.0.0`
survival/adventure product.

The goal is to make the owner experience simple:

- Nexori defines the protocol and the allowed behaviors
- the owner configures destinations and triggers in game
- the owner does not design security or invent payload formats

## Core Concepts

### `DestinationTarget`

A `DestinationTarget` belongs to the **destination server**.

It answers one question:

> "If a player arrives here, where exactly can they appear?"

Examples:

- the natural spawn of a world
- a specific coordinate and rotation
- a placed portal that can be arrived at

For `1.0.0`, the main built-in destination target kinds are:

- `NATURAL_SPAWN`
- `COORDINATE`
- `PORTAL`

Important:

- a destination target is **not** the trigger
- a destination target is **not** the travel policy
- a destination target is the arrival point offered by the server

### `TriggerBinding`

A `TriggerBinding` belongs to the **origin server**.

It answers one question:

> "What local event should trigger a trip?"

Examples:

- crossing a portal
- running a command
- interacting with a UI button

Later versions may add triggers such as:

- death
- match found
- NPC interaction

But those are not required for the first survival/adventure release.

### `TravelProfile`

A `TravelProfile` defines **how** the player travels.

It answers one question:

> "What policies or runtime rules should apply to this trip?"

Examples:

- keep inventory
- clear inventory
- mark the arrival for later observation
- attach future gameplay context

Important:

- a travel profile should be attached to a trip or trigger binding
- it should **not** be a global profile that must be pre-distributed to every
  server for every player
- the payload can carry the selected profile id and any supporting context

This makes the model reusable later for minigames without making `1.0.0`
depend on minigame logic.

## Recommended Survival/Adventure Flow

### 1. Destination setup

On the destination server, the owner registers one or more destination targets.

Examples:

- `default.natural_spawn`
- `mountain.overlook`
- `hub.main_portal`

These are discoverable arrival points.

### 2. Origin setup

On the origin server, the owner creates a trigger binding.

Examples:

- local portal `hub_to_survival`
- local command `/survival`

That trigger binding selects:

- a remote server
- one remote destination target
- one travel profile

### 3. Secure travel

When the trigger fires, Nexori sends a signed secure referral containing:

- the destination server
- the destination target id
- the selected travel profile id
- any signed context payload

The destination server verifies the payload, resolves the destination target,
and performs the arrival behavior.

## Why This Model Fits `1.0.0`

This model supports the no-code product goal:

- owners configure destination targets
- owners link triggers to those targets
- Nexori owns the protocol, security, and travel semantics

It also leaves a clean path for later expansion:

- minigame queue triggers
- match lifecycle triggers
- death/return flows
- richer travel profiles

## Implementation Direction

### What the current code should become

The current `route` concept should be rewritten into:

- `DestinationTarget`

And later expanded with:

- `TriggerBinding`
- `TravelProfile`

### What should remain true

The existing secure foundation stays valid:

- per-server keypairs
- signed payload envelopes
- trust bundle verification
- destination-side validation before arrival

Only the domain model needs to be corrected and clarified.
