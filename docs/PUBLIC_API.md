# Nexori Public API

Nexori exposes a narrow public API for third-party minigame mods.

The intended story is:

1. your mod owns gameplay logic
2. your mod decides when a player won or lost
3. your mod asks Nexori to resolve that outcome
4. Nexori handles the delayed return-to-lobby flow

Nexori is not trying to be your rule engine.

## Main Interface

The public entrypoint is:

[NexoriMinigameApi.java](D:/JanielNunez/hyjn-nexori/nexori-plugin/src/main/java/io/github/hyjn/nexori/plugin/api/minigame/NexoriMinigameApi.java)

## Supported Calls

### `findActiveMatchId(UUID playerUuid)`

Use this when your mod needs to know whether a player is currently part of an
active Nexori match.

### `findActivePlayerUuid(String matchId, String playerToken)`

Use this to resolve one player inside a match. The token can be:

- the UUID string
- the current username

### `resolvePlayerOutcome(String matchId, UUID playerUuid, NexoriPlayerResolutionOutcome outcome, int returnDelaySeconds, String reason)`

This is the main integration hook.

Call it when your mod has already decided:

- this player won
- or this player lost

Nexori will update the match state and schedule the return flow.

### `findMatchPlacementState(String matchId)`

Use this when your mod needs to know whether Nexori already finished placing
players into their initial match positions.

This is especially useful when a minigame wants to wait until players are in
their assigned spawn slots before starting custom gameplay logic.

### `findMatchResolutionTriggerId(String matchId)`

Use this to detect whether the current Nexori match is configured for:

- a built-in trigger such as `last_player_alive`
- or manual third-party resolution such as `none`

If the match is not manual, a third-party mod should typically stay passive and
let Nexori's built-in flow own that match.

## Resolution Philosophy

Nexori's public API intentionally does **not** expose:

- custom trigger registration
- external rule evaluation callbacks
- a general-purpose match scripting engine

That path was removed on purpose to keep the public integration story clear.

The official pattern is:

- detect win/loss in your mod
- call Nexori to resolve the player

## Example Integration

See the official companion demo:

[nexori-public-api-demo](https://github.com/hyjn-nexori/nexori-public-api-demo)

That demo shows:

- how to discover whether a player is in an active Nexori match
- how to wait for placement readiness
- how to run custom game logic in a separate mod
- how to trigger return-to-lobby through Nexori once a player outcome is known
