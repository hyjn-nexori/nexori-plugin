# Nexori Public API

This guide documents the public minigame integration API exposed by Nexori `2.0.0`.

It is written for developers who want to build their own gameplay mod on top of Nexori's match flow. The model is simple: your mod owns the game rules, and Nexori owns the match-aware return flow once you report a player outcome.

## 1. What this API is for

Use this API when your mod wants to plug custom gameplay into a Nexori-managed match.

In practice, that means:

- Nexori launches or tracks the match
- your mod runs the game rule engine
- your mod decides when a player has won or lost
- your mod reports that result through the public API
- Nexori handles the delayed return-to-lobby flow

## 2. How to read this guide

This guide was written using the official companion demo as the practical integration reference:

- [nexori-capture-the-zone-minigame](https://github.com/hyjn-nexori/nexori-capture-the-zone-minigame)

That demo implements a simple "Mid Capture" minigame and shows how a separate mod can:

- detect active Nexori matches
- wait for Nexori placement to finish
- check whether the match is using manual resolution
- run its own gameplay rules
- report `WIN` and `LOSS` back to Nexori
- let Nexori handle the delayed return flow

<details>
<summary><code>Guide context</code></summary>

| Section | Details |
|---|---|
| Nexori version | `2.0.0` |
| Companion demo | `nexori-capture-the-zone-minigame` |
| Demo minigame | Mid Capture |
| What the demo proves | A separate mod can own gameplay logic while Nexori owns match-aware coordination and return flow. |
| Why this matters | The examples below are grounded in a real integration, not a hypothetical API shape. |

</details>

## 3. Add Nexori as a dependency

The companion demo uses two layers of dependency setup:

1. a runtime plugin dependency in `manifest.json`
2. a compile-time dependency in Gradle

<details>
<summary><code>Runtime plugin dependency</code></summary>

| Section | Details |
|---|---|
| Where it goes | `src/main/resources/manifest.json` |
| What it does | Declares that your mod depends on Nexori at runtime. |
| Use this when | Your mod requires Nexori to be present in the server/plugin environment. |

**Example**

```json
"Dependencies": {
  "Nexori:NexoriPlugin": "*"
}
```

</details>

<details>
<summary><code>Compile-time dependency on Nexori</code></summary>

| Section | Details |
|---|---|
| What it is | A compile-time dependency on the built Nexori plugin jar. |
| Why it exists | Your mod needs Nexori's public Java types on the compile classpath. |
| Recommended configuration | `compileOnly` |
| Runtime model | Nexori is still provided at runtime through the plugin dependency in `manifest.json`. |
| Notes | The companion demo currently compiles against a locally built Nexori jar. The exact file path is a workspace choice, not a required folder layout. |

This reflects the current integration model used by the companion demo. If Nexori later defines a more formal artifact distribution channel, this compile-time setup may become simpler or change accordingly.

**Example**

```groovy
dependencies {
    implementation(files("$hytaleHome/install/$patchline/package/game/latest/Server/HytaleServer.jar"))
    compileOnly(files("/path/to/nexori-plugin.jar"))
}
```

</details>

<details>
<summary><code>Compile-time dependency on HyUI</code></summary>

| Section | Details |
|---|---|
| What it is | A compile-time dependency on `HyUI`. |
| Use it when | Your mod imports `HyUI` classes directly. |
| Recommended source | CurseMaven |
| Recommended configuration | `compileOnly` |
| Why this is preferred | It avoids local machine paths and gives other developers a reproducible dependency source. |
| Version guidance | Pin a specific version instead of floating to "latest". |
| Notes | Nexori itself uses `HyUI` for HUD and UI behavior. If your mod only uses `NexoriMinigameApi` and does not import `HyUI` classes, you do not need to treat `HyUI` as part of Nexori's public API surface. |

**Example**

```groovy
repositories {
    maven {
        url = uri("https://www.cursemaven.com")
    }
}

dependencies {
    implementation(files("$hytaleHome/install/$patchline/package/game/latest/Server/HytaleServer.jar"))
    compileOnly(files("/path/to/nexori-plugin.jar"))
    compileOnly("curse.maven:hyui-1431415:7667069")
}
```

</details>

## 4. Get access to the API

Nexori exposes the public minigame API through:

- [`NexoriPlugin#getMinigameApi()`](../src/main/java/io/github/hyjn/nexori/plugin/NexoriPlugin.java)

The companion demo resolves the plugin through `PluginManager`, casts it to `NexoriPlugin`, and then calls `getMinigameApi()`.

<details>
<summary><code>API access pattern used by the demo</code></summary>

| Section | Details |
|---|---|
| Public access point | `NexoriPlugin#getMinigameApi()` |
| Demo lookup pattern | `PluginManager.get().getPlugin(new PluginIdentifier("Nexori", "NexoriPlugin"))` |
| Next step | Cast the plugin to `NexoriPlugin` and call `getMinigameApi()`. |
| Use this when | Your mod runs in the same plugin environment and needs the live Nexori API instance. |

**Example**

```java
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMinigameApi;

final class NexoriMinigameApiLocator {

    private static final PluginIdentifier NEXORI_PLUGIN_ID =
        new PluginIdentifier("Nexori", "NexoriPlugin");

    static NexoriMinigameApi resolve() {
        PluginBase plugin = PluginManager.get().getPlugin(NEXORI_PLUGIN_ID);
        if (!(plugin instanceof NexoriPlugin nexoriPlugin)) {
            throw new IllegalStateException(
                "Could not resolve Nexori plugin dependency " + NEXORI_PLUGIN_ID + "."
            );
        }
        return nexoriPlugin.getMinigameApi();
    }
}
```

</details>

## 5. Manual resolution model

Nexori can run matches with built-in resolution logic, but this API is especially useful when a match is configured for manual resolution.

In manual mode:

- Nexori does not decide who won or lost
- your mod becomes the authority for gameplay resolution
- your mod stores player outcomes with `setPlayerOutcome(...)`
- your mod closes the result with `submitFinalMatchResult(...)`
- your mod schedules returns explicitly with `returnPlayerToLobby(...)` when needed
- Nexori takes over again once the outcome has been reported

If the match is not in manual mode:

- your mod should stay passive for match resolution
- your mod should not try to decide the winner
- your mod should not call result commands as the owner of the rule engine
- Nexori's built-in trigger is the system that owns match resolution

If your plugin contains multiple minigames, Nexori still only tells you whether the match is in manual mode. Your own plugin must decide which internal minigame controller, rule set, or game manager should handle that match.

<details>
<summary><code>Authority model</code></summary>

| Section | Details |
|---|---|
| Built-in Nexori resolution | Nexori owns the match outcome. |
| Manual resolution | Your mod owns the match outcome. |
| How to detect manual mode | Call `findMatchResolutionTriggerId(matchId)`. |
| Manual trigger meaning | The demo treats blank or `"none"` as manual resolution. |
| Your responsibility in manual mode | Decide which gameplay logic is active and when players should be resolved as `WIN` or `LOSS`. |
| Your behavior in built-in mode | Do not run your own match-resolution authority. Stay passive and let Nexori's built-in trigger own the outcome. |
| Nexori's responsibility after that | Store the result, queue backend reporting when configured, and run explicit return-to-lobby requests. |

</details>

<details>
<summary><code>Recommended integration flow</code></summary>

| Step | What to do |
|---|---|
| 1 | Resolve `NexoriMinigameApi` from the Nexori plugin. |
| 2 | Call `findActiveMatchId(playerUuid)` to detect whether the player is inside an active Nexori match. |
| 3 | Call `findMatchResolutionTriggerId(matchId)` to determine whether the match is using manual resolution or a Nexori built-in trigger. |
| 4 | Before starting gameplay logic that depends on players being in the correct world or position, call `findMatchPlacementState(matchId)` and wait until `placementComplete` is `true`. |
| 5 | Only if the trigger is manual, hand control to your own game rule engine or minigame controller. |
| 6 | When your mod decides the winner/losers, call `setPlayerOutcome(...)` for each required player. |
| 7 | Call `submitFinalMatchResult(...)` to close and optionally report the final match result. |
| 8 | Call `returnPlayerToLobby(...)` for players that should leave the arena runtime. |

</details>

<details>
<summary><code>Why placement completion matters</code></summary>

Nexori's minigame launch flow does more than simply mark a player as "connected" to the destination server.

At a high level, the flow is:

1. a player leaves the lobby or source server
2. Nexori routes that player to the destination server using a safe default-world natural spawn entry
3. on the destination server, Nexori prepares or materializes the instance world for that match
4. if the instance has configured spawn slots, Nexori prepares those assignments for the arriving players
5. once the player is inside the destination world flow, Nexori still validates that the player has actually completed the initial placement phase for the match
6. only after that does Nexori count the player as fully placed

This matters because "the player reached the destination server" and "the player is fully placed for the match" are not the same thing.

Your mod can easily hit race-condition-style problems if it starts acting on players too early, especially when your gameplay assumes:

- the player is already in the correct instance world
- the player is already at the final spawn location for the match
- all expected players have already completed the same placement flow
- Nexori has finished its own initial handoff work for the match

In Nexori's code, the destination server first resolves a safe default-world natural spawn target. Nexori then prepares the match instance world. If explicit spawn slots exist for that instance, Nexori configures those assignments before the player enters the instance. After arrival, Nexori still tracks whether each player has actually completed the initial placement phase and only then counts that player as placed.

That is why `findMatchPlacementState(matchId)` is important: it gives your mod a clean handoff point. When `placementComplete` becomes `true`, Nexori has finished its initial arrival-and-placement work for that match. That includes the case where there are no explicit spawn slots and the flow settles using the instance's natural/default spawn behavior.

Use that moment as the green light for logic such as:

- pre-match setup
- countdowns
- minigame initialization
- position-sensitive checks
- world-sensitive logic
- any system that assumes the players are now fully handed over to your mod's game flow

### Summary

| Section | Details |
|---|---|
| What Nexori is confirming | All expected players for the match have arrived, and all of them have completed Nexori's initial placement phase. |
| How the code decides this | `placementComplete` becomes `true` only when `expectedPlayers > 0`, `arrivedPlayers >= expectedPlayers`, and `placedPlayers >= expectedPlayers`. |
| Why this matters for your mod | If your mod starts too early, it can race against Nexori's arrival and placement flow and act on players before they are fully settled in the match. |
| Practical risk | World-sensitive or position-sensitive gameplay logic can run while players are still arriving, still being placed, or still being validated by Nexori's placement flow. |
| Recommended rule | Treat `placementComplete == true` as the safe point to begin pre-match logic, countdowns, minigame setup, or live gameplay systems. |

</details>

## 6. Public types and methods

The public integration types live in:

- [`io.github.hyjn.nexori.plugin.api.minigame`](../src/main/java/io/github/hyjn/nexori/plugin/api/minigame)

### `NexoriMinigameApi`

Main interface:

- [`NexoriMinigameApi.java`](../src/main/java/io/github/hyjn/nexori/plugin/api/minigame/NexoriMinigameApi.java)

<details>
<summary><code>Optional&lt;String&gt; findActiveMatchId(@Nonnull UUID playerUuid)</code></summary>

| Section | Details |
|---|---|
| Signature | `Optional<String> findActiveMatchId(@Nonnull UUID playerUuid)` |
| What it does | Returns the active Nexori match id currently associated with a player. |
| Arguments | `playerUuid` - the player you want to check. |
| Returns | `Optional<String>` containing the active match id, or `Optional.empty()` when the player is not currently part of an active Nexori match. |
| Use this when | You need to know whether your mod should treat the player as being inside a Nexori-managed match, or when you need the match id for follow-up API calls. |
| Notes | This is usually the first API call your gameplay logic should make. |

**Example**

```java
Optional<String> activeMatchId = minigameApi.findActiveMatchId(playerRef.getUuid());
if (activeMatchId.isEmpty()) {
    return;
}

String matchId = activeMatchId.get();
```

</details>

<details>
<summary><code>Optional&lt;UUID&gt; findActivePlayerUuid(@Nonnull String matchId, @Nonnull String playerToken)</code></summary>

| Section | Details |
|---|---|
| Signature | `Optional<UUID> findActivePlayerUuid(@Nonnull String matchId, @Nonnull String playerToken)` |
| What it does | Resolves one player inside an active match by UUID string or current username. |
| Arguments | `matchId` - the active match id.<br>`playerToken` - a UUID string or the player's current username. |
| Returns | `Optional<UUID>` containing the resolved active player UUID, or `Optional.empty()` when the match is not active, the token does not resolve, or the player is not in that match. |
| Use this when | You receive player identity as a string from your own mod logic and need Nexori's active UUID for a player inside a known match. |
| Notes | Usernames are matched case-insensitively. UUID strings are accepted directly. |

**Example**

```java
Optional<UUID> resolvedPlayerUuid =
    minigameApi.findActivePlayerUuid(matchId, playerRef.getUsername());

if (resolvedPlayerUuid.isEmpty()) {
    return;
}

UUID playerUuid = resolvedPlayerUuid.get();
```

</details>

<details>
<summary><code>Optional&lt;NexoriMatchPlacementState&gt; findMatchPlacementState(@Nonnull String matchId)</code></summary>

| Section | Details |
|---|---|
| Signature | `Optional<NexoriMatchPlacementState> findMatchPlacementState(@Nonnull String matchId)` |
| What it does | Returns Nexori's public snapshot of the initial player placement phase for one active match. |
| Arguments | `matchId` - the active match id. |
| Returns | `Optional<NexoriMatchPlacementState>` for an active match, or `Optional.empty()` when the match is not active. |
| Use this when | Your gameplay should start only after Nexori has finished its initial arrival-and-placement flow, or when you need expected, arrived, and placed player counts. |
| Notes | This is the safe gate for world-sensitive or position-sensitive gameplay logic. Wait for `placementComplete` before acting on player positions, spawn setup, match countdowns, or other systems that assume players are fully placed. |

**Example**

```java
NexoriMatchPlacementState placementState =
    minigameApi.findMatchPlacementState(matchId).orElse(null);

if (placementState == null || !placementState.placementComplete()) {
    return;
}

// Safe point to start gameplay logic that depends on players
// being fully placed for the match.
```

</details>

<details>
<summary><code>Optional&lt;String&gt; findMatchResolutionTriggerId(@Nonnull String matchId)</code></summary>

| Section | Details |
|---|---|
| Signature | `Optional<String> findMatchResolutionTriggerId(@Nonnull String matchId)` |
| What it does | Returns the current match resolution trigger id for an active match. |
| Arguments | `matchId` - the active match id. |
| Returns | `Optional<String>` containing the trigger id, or `Optional.empty()` when the match is not active. |
| Use this when | You need to decide whether your mod has authority to run the match rule engine or whether it should stay passive and let Nexori resolve the match. |
| Notes | The companion demo treats blank or `"none"` as manual resolution. The codebase also uses the built-in trigger id `last_player_alive`. If the trigger is not manual, your mod should not act as the match-resolution authority. |

**Example**

```java
String triggerId = minigameApi.findMatchResolutionTriggerId(matchId).orElse("");
boolean manualResolution = triggerId.isBlank() || "none".equalsIgnoreCase(triggerId);

if (!manualResolution) {
    // Nexori owns match resolution for this match.
    // This mod should stay passive and not run its own rule engine.
    return;
}

// Manual mode: this mod now has authority to run its own
// gameplay rule engine and eventually report WIN/LOSS.
```

</details>

## 7. Public enums and records

<details>
<summary><code>NexoriMatchPlacementState</code></summary>

| Section | Details |
|---|---|
| Source | [`NexoriMatchPlacementState.java`](../src/main/java/io/github/hyjn/nexori/plugin/api/minigame/NexoriMatchPlacementState.java) |
| What it represents | Nexori's public snapshot of the initial placement phase for an active match. |
| Fields | `expectedPlayers`, `arrivedPlayers`, `placedPlayers`, `placementComplete` |
| Use this when | You want to wait until Nexori has finished initial placement before your game logic fully starts. |
| Notes | This is the handoff point between Nexori's initial arrival/placement work and your mod's gameplay logic. |

**Example**

```java
if (placementState.placementComplete()) {
    logger.atInfo().log(
        "placement ready expected=" + placementState.expectedPlayers()
            + " arrived=" + placementState.arrivedPlayers()
            + " placed=" + placementState.placedPlayers()
    );
}
```

</details>

## 8. Example integration patterns

The companion demo uses two patterns that are worth copying directly:

1. resolve the API once during plugin setup
2. query Nexori during your gameplay loop, then resolve outcomes when your own rules decide the winner

<details>
<summary><code>Resolve the API during plugin setup</code></summary>

| Section | Details |
|---|---|
| Pattern | Resolve the API once and pass it into your gameplay service. |
| Why it helps | Your game systems can work against `NexoriMinigameApi` directly instead of rediscovering the plugin each time. |
| Source in demo | `NexoriPublicApiDemoPlugin` |

**Example**

```java
public final class NexoriPublicApiDemoPlugin extends JavaPlugin {

    private MidCaptureService midCaptureService;

    @Override
    protected void setup() {
        NexoriMinigameApi minigameApi = NexoriMinigameApiLocator.resolve();
        this.midCaptureService = new MidCaptureService(minigameApi, this.getLogger());

        this.getEntityStoreRegistry().registerSystem(
            new MidCaptureTickSystem(this.midCaptureService, this.midCaptureHudService)
        );
    }
}
```

</details>

<details>
<summary><code>Gate your gameplay on active match, manual trigger, and placement completion</code></summary>

| Section | Details |
|---|---|
| Pattern | Exit early until the player is in an active Nexori match, the trigger is manual, and placement is complete. |
| Why it helps | Your gameplay logic only takes control when Nexori has fully handed authority to the external mod. If the trigger is not manual, your mod does nothing and Nexori keeps ownership of the match rule engine. |
| Source in demo | `MidCaptureService#handlePlayerTick(...)` |

**Example**

```java
Optional<String> activeMatchId = minigameApi.findActiveMatchId(playerRef.getUuid());
if (activeMatchId.isEmpty()) {
    return;
}

String matchId = activeMatchId.get();

String triggerId = minigameApi.findMatchResolutionTriggerId(matchId).orElse("");
boolean manualResolution = triggerId.isBlank() || "none".equalsIgnoreCase(triggerId);
if (!manualResolution) {
    return;
}

NexoriMatchPlacementState placementState = minigameApi.findMatchPlacementState(matchId).orElse(null);
if (placementState == null || !placementState.placementComplete()) {
    return;
}

// At this point, your mod can safely run its own gameplay logic.
```

</details>

<details>
<summary><code>Store player outcomes, submit the final result, then return players</code></summary>

| Section | Details |
|---|---|
| Pattern | Your mod decides the winner, stores one outcome per required player, submits the final result, then schedules returns explicitly. |
| Why it helps | Nexori keeps orchestration, result reporting, and return travel separate from gameplay rules. |
| Source in demo | `MidCaptureService#resolveWinner(...)` |

**Example**

```java
for (UUID playerUuid : playerUuidsInThisMatch) {
    boolean winner = playerUuid.equals(winnerUuid);
    NexoriSetPlayerOutcomeResult outcomeResult = minigameApi.setPlayerOutcome(
        matchId,
        playerUuid,
        winner ? NexoriMatchResultPlayerOutcome.WIN : NexoriMatchResultPlayerOutcome.LOSS,
        winner ? "mid_capture_win" : "mid_capture_loss"
    );

    if (outcomeResult.status() != NexoriSetPlayerOutcomeStatus.UPDATED) {
        logger.atWarning().log(
            "Failed to store player outcome matchId=" + matchId
                + " playerUuid=" + playerUuid
                + " status=" + outcomeResult.status()
        );
    }
}

NexoriSubmitFinalMatchResultResult finalResult = minigameApi.submitFinalMatchResult(
    new NexoriSubmitFinalMatchResultRequest(matchId, "mid_capture_finished", customData)
);

if (finalResult.matchStatus() == NexoriMatchCompletionStatus.ACCEPTED) {
    for (UUID playerUuid : playerUuidsInThisMatch) {
        minigameApi.returnPlayerToLobby(matchId, playerUuid, 5, "mid_capture_finished");
    }
}
```

</details>

## 9. What counts as the public API

<details>
<summary><code>Public API surface</code></summary>

| Section | Details |
|---|---|
| Public package | [`io.github.hyjn.nexori.plugin.api.minigame`](../src/main/java/io/github/hyjn/nexori/plugin/api/minigame) |
| Public plugin access point | [`NexoriPlugin#getMinigameApi()`](../src/main/java/io/github/hyjn/nexori/plugin/NexoriPlugin.java) |
| Treat as internal | Queue services, travel services, arena runtime services, and other implementation classes outside the public API package. |

</details>
