# Nexori Plugin

Nexori is a Hytale network plugin focused on one job first: giving creators a
safe, guided way to establish trust between their servers without forcing them
onto a hosted backend.

Project planning and release/versioning rules live in
[`docs/ROADMAP.md`](D:\JanielNunez\hyjn-nexori\nexori-plugin\docs\ROADMAP.md).

## Current Scope

This repository currently contains the first usable slice of the plugin:

- per-server identity generation with an Ed25519 keypair
- local persistence for server identity and bootstrap state
- short-lived bootstrap sessions for enrollment windows
- saved peer IPs persisted as JSON in the server data folder
- a lightweight peer manager UI based on custom pages
- bootstrap travel that collects public keys, gets a signed proof from each peer,
  and installs the verified trust bundle across enrolled servers

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
/nexoritravel <host:port> [--routeKey=<key>] [--entryPoint=<id>]
/nexorimenu
```

Saved data currently lives under the plugin data directory:

- `config/configured-peers.json`
- `state/bootstrap-state.properties`
- `state/bootstrap-run.json`
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

## Release Line

The current committed milestone is `0.1.0`.

- `0.1.x` is for fixes and stability
- `0.2.0` is planned for the first route system
- `1.0.0` is the target for the first non-coder-friendly adventure network kit

## Secure Referrals

Nexori now also includes a signed referral envelope for normal server-to-server
travel after bootstrap.

- the full payload is signed, not just one field
- the destination verifies the signature against the current trust bundle
- the payload type is explicit so future protocols can reuse the same envelope
- the first payload type implemented is `travel.direct`

This is the foundation for future portal, queue, return, and inventory
protocols without redesigning the security model.

The next milestone is building higher-level tools such as portals and queue
flows on top of these secure referrals.

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
