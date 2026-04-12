# Nexori Operator Guide

This guide is for owners who want to run Nexori as a connected Hytale tenant.

## 1. Install Nexori On Every Server

Each server in the tenant should have:

- the same Nexori build
- valid Hytale auth
- a reachable connection address

If the tenant is public or remote, use the real reachable address that other
servers and players will actually use. Do not bootstrap a shared tenant with
`127.0.0.1` unless the whole network is truly local to one machine.

## 2. Add Local Peers

Open:

```text
/nexorimenu
```

In the **Servers** view:

- the left table is your editable local peer list
- the right table reflects the installed trust bundle

Add the servers that should belong to the tenant, then run **Initial Setup**.

## 3. Run Initial Setup

`Initial Setup` does three important jobs:

1. verifies the peer network
2. builds a new trust bundle
3. distributes that bundle across the enrolled servers

If you later change a server's connection address, rerun `Initial Setup`.
Nexori will also migrate persisted connection-address references inside its own
storage so the tenant does not keep stale addresses.

## 4. Choose The Lobby Server

In **Minigames -> Lobby**:

- select the local world that acts as the lobby
- save the lobby definition

Only one server should act as the active lobby for a tenant/region at a time.

## 5. Define Games

In **Minigames -> Games**:

- choose the remote game server
- choose the remote entry target
- choose the instance template
- choose the return trigger
- save the game definition

A game definition tells Nexori where and how to launch a match.

If the return trigger is manual, that means Nexori is waiting for a third-party
mod to decide who won or lost by using the public API that Nexori exposes. In
that setup:

- the external mod owns the gameplay logic
- the external mod decides when a player should be resolved as `WIN` or `LOSS`
- the external mod calls Nexori's public API to resolve that player outcome
- Nexori then handles the delayed return-to-lobby flow and the secure travel
  side of the handoff

This keeps the minigame-specific rules inside the custom mod while Nexori keeps
owning the tenant-level travel and return behavior.

## 6. Define Queues

In **Minigames -> Queues**:

- create a queue
- set min players
- set max players
- set countdown
- choose which game it launches into

Queues live on the lobby server.

## 7. Bind Queues To Portals

In **Portals -> Queue Portals**:

- choose a portal
- choose a queue
- attach the queue

You can also designate a portal as a leave-queue portal.

## 8. Create Travel Portals

In **Portals -> Travel**:

- choose endpoint A
- choose endpoint B
- choose travel policy
- bind one-way or two-way travel

Nexori supports:

- local target travel
- secure cross-server travel
- queue portals
- leave-queue portals

Players currently inside a queue are blocked from using normal cross-server
travel portals until they leave the queue.

## 9. Configure Spawn Slots For Minigame Instances

In **Minigames**, use the spawn-slot tab/workflow to:

- select an instance template
- save the player position as a spawn slot
- edit or remove saved slots

When a match launches, Nexori writes the assigned spawn provider into the
runtime instance config and distributes players across saved spawn slots.

If no spawn slots are defined, players fall back to the natural spawn of the
instance.

## 10. Recovery And Diagnostics

Nexori includes:

- travel inventory policies
- overwrite backups for `APPLY_INVENTORY`
- recovery flows
- diagnostics capture and collect

Useful commands include:

```text
/nexori help
/nexorirecovery
/nexoribackups
```

## 11. Recommended Release/Deployment Habits

- back up important worlds before major edits
- back up `mods/Nexori_NexoriPlugin` before large tenant changes
- rerun `Initial Setup` after peer address changes
- restart servers after trust-bundle updates or persistence migrations

## 12. What The Official Demo Proves

The official demo refers to both the companion repository and the example
tenant package built around Nexori's public API:

- the companion mod repo:
  [nexori-public-api-demo](https://github.com/hyjn-nexori/nexori-public-api-demo)
- the example tenant package you can ship alongside the release:
  a zip with four servers that includes Nexori plus a separate demo mod

That separate demo mod is not Nexori itself. It exists to show how another mod
can create its own minigame logic and interact with Nexori's public API while
Nexori handles the launch placement, secure travel, and return flow.

The demo flow built on top of Nexori validates:

- third-party win/loss detection
- public API integration
- queue launch into instances
- spawn slot placement
- return-to-lobby after resolution

See:

- [README.md](D:/JanielNunez/hyjn-nexori/nexori-public-api-demo/README.md)
- [docs/PUBLIC_API.md](D:/JanielNunez/hyjn-nexori/nexori-plugin/docs/PUBLIC_API.md)
