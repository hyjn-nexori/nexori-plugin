# Nexori Operator Guide

This guide walks through a minimal two-server Nexori setup.

By the end of this guide, you will have:

- one lobby server
- one minigame server
- a full queue flow from the lobby
- travel to the match server
- return-to-lobby flow after the match ends

## Goal

For local testing, the minimum useful setup is:

- **Server 1** -> lobby / queues
- **Server 2** -> minigame matches

The full test flow is:

1. join the lobby
2. enter a queue portal
3. wait for the queue countdown
4. travel to the match on server 2
5. finish the match
6. return to the lobby

> If you test the built-in `Last Player Alive` mode alone, Nexori may immediately resolve you as the winner because you are the only remaining player.
>
> For a more realistic test, use at least two players.

---

## 1. Install Nexori on every server

Install Nexori on every server that will participate in the flow.

For this guide, install Nexori on:

- server 1
- server 2

<details>
<summary>Why this is required</summary>

Each participating server needs Nexori because Nexori will:

- generate a keypair on that server
- collect and distribute public key bundles during initial setup
- sign and verify referral messages
- understand travel, queue, and return flow

A server without Nexori cannot participate in the secure cross-server flow described in this guide.

</details>

---

## 2. Decide whether you will test locally or with other players

If you are testing by yourself only, you can keep both servers local.

If you want to test with friends, your local servers must be reachable through public connection addresses.

<details>
<summary>Recommended approach</summary>

The fastest free way to expose local servers for testing is to tunnel them through a public network.

For quick testing, this guide recommends **Playit** because it is simple and free.

- If you are testing alone, keep everything local.
- If other players will join, tunnel the servers first.

</details>

---

## 3. Start the servers

After installing Nexori, start all participating servers.

<details>
<summary>What Nexori does on startup</summary>

When Nexori starts on each server, it generates a server keypair.

Later, during the initial setup flow, Nexori uses your player as a messenger to:

1. visit each registered server
2. collect each server's public key
3. build a bundle containing all collected public keys
4. distribute that bundle back to every server

That lets all participating servers verify referral messages during secure travel.

This is automated in-game. You do not need to manually build or distribute the bundle.

</details>

---

## 4. Open the Nexori admin menu

Join **server 1** and open Nexori's admin menu:

```text
/nexorimenu
```

> **Version note (`2.0.0`)**  
> In Nexori `2.0.0`, opening the admin menu does not currently require operator-level access.
>
> Even so, it is still recommended to grant yourself operator access early in the setup, because later steps — especially portal interaction — do require it.
>
> This is simply how the first public `2.0.0` release behaves today.

<details>
<summary>What you should expect</summary>

Before the initial setup is completed, Nexori's admin flow is intentionally restricted.

You should expect that the main tabs are not fully usable until the server registration and trust setup are complete.

</details>

---

## 5. Register both servers

Go to the **Servers** view and register both participating servers.

For each server, provide:

- a display name
- its connection address

Register:

- the server you are currently on
- the second server

<details>
<summary>Important rule for connection addresses</summary>

Always register the same connection address players actually use to join that server.

Examples:

- if you joined with `localhost`, register `localhost`
- if you joined through a public tunneled address, register that tunneled address

Do not mix them.

If a server is reached through a tunneled public address but Nexori is configured with `localhost`, other players will not be able to follow the same travel path correctly.

</details>

---

## 6. Run the Initial Setup

After all participating servers are registered, run the **Initial Setup** action.

Do not press it until all required servers have been added.

<details>
<summary>What Initial Setup does</summary>

Nexori uses your player as a messenger.

The flow is:

1. visit each registered server
2. collect that server's public key
3. return to the origin server
4. build a trusted bundle from all collected public keys
5. travel back to the registered servers
6. distribute the bundle so every server can verify referral messages

This is what makes server-to-server travel secure in Nexori.

</details>

---

## 7. Confirm that setup succeeded

Wait until the flow returns you to the server where you started it.

Then check the **Action / Status** area and confirm that the registered servers appear as trusted travel targets.

<details>
<summary>What success means</summary>

If the setup worked, all participating servers now know how to verify signed referral messages from each other.

At that point, the network is ready for secure travel.

</details>

---

## 8. Set the lobby server and world

Still on **server 1**, go to **Minigames** -> **Lobby**.

Select the world on the current server that should act as the lobby, then save it.

<details>
<summary>Why there is only one lobby in this setup</summary>

Nexori is backendless.

That means queue synchronization is not handled by a central backend service. Because of that, this model is designed around a single server acting as:

- the lobby
- the queue authority

For this guide, **server 1** is the lobby / queue server.

When you save the lobby, Nexori uses your player as a messenger again to tell the other servers which server and world now act as the lobby.

</details>

---

## 9. Confirm the lobby setup

Check the **Action / Status** area again and make sure the lobby setup succeeded.

<details>
<summary>Why this matters</summary>

Nexori needs a known lobby destination so it can return players there after matches end.

Without a defined lobby, the minigame flow will not behave like a complete loop.

</details>

---

## 10. Create a GAME on server 2

On **server 1**, go to **Minigames** -> **Games**.

Create a new GAME using:

- **target server:** server 2
- **instance template:** `skywars_nexori`
- **return-to-lobby trigger:** `Last Player Alive`

<details>
<summary>What each field means</summary>

- **Target server**  
  The server where the actual match will happen.

- **Instance template**  
  The world template Nexori will use to create match instances.

- **Return-to-lobby trigger**  
  The match resolution logic.

For this guide, use Nexori's built-in trigger:

- `Last Player Alive`

That built-in logic marks dead players as `LOSS` and the final surviving player as `WIN`.

The `Manual` option is for third-party minigame mods that want to provide their own gameplay rule engine and report outcomes through Nexori's public API.

</details>

---

## 11. Create a QUEUE

Still on **server 1**, go to **Minigames** -> **Queues** and create a queue for that GAME.

Set:

- queue name
- minimum players
- maximum players
- countdown seconds

<details>
<summary>How to choose values</summary>

- If you are testing alone, set **minimum players = 1**
- If you are testing with two players, set **minimum players = 2**
- If you are testing with more players, match the value to your test group

The countdown controls how long Nexori waits after the minimum player requirement is satisfied before launching the match.

</details>

---

## 12. Travel to server 2

Go to the **Servers** view and travel to **server 2**.

<details>
<summary>Why this is required</summary>

The instance template and its spawn positions must be configured on the server where those matches will actually run.

For this guide, that is server 2.

</details>

---

## 13. Spawn the permanent template world

On **server 2**, open the creative inventory and go to:

- **Creative Tools**
- **Worlds**
- **Instances**

Find Nexori's built-in instance assets, open **NexoriPlugin**, select **skywars_nexori**, and spawn it.

<details>
<summary>What this world is for</summary>

This world is only used to create and save spawn positions for the instance template.

It is **not** the world where live matches will happen.

Each real match creates its **own separate instance** from this template, and Nexori destroys that match instance when the match ends.

So this setup world exists only to tell Nexori where players should be placed when a new match starts.

After you finish saving the spawn positions, you can leave this world as a permanent setup world or delete it later. Nexori does not need live matches to happen inside this manually spawned copy.

</details>

---

## 14. Save spawn positions

Move to the location where you want one spawn point to be.

Then open the admin menu and go to:

- **Minigames**
- **Spawns**

Select the instance you are currently in (`skywars_nexori`) and save the current position.

Repeat this for each spawn position you want.

<details>
<summary>How Nexori uses saved spawns</summary>

Nexori distributes players across saved spawn positions using a **round-robin** style assignment.

That means:

- if you saved 3 spawn positions
- and 3 players join
- Nexori can place one player on each position

If more players join than there are saved spawn positions, Nexori cycles through them again.

So if you want every player to start on a different island or slot, save at least as many spawn positions as the number of players you expect in the match.

For a Skywars-style layout, a common approach is:
- one saved spawn per island

</details>

---

## 15. Return to the lobby server

After saving the spawn positions, go back to **server 1**.

<details>
<summary>Why we return now</summary>

At this point, the trusted servers exist, the lobby exists, the GAME exists, the QUEUE exists, and the instance template now has spawn data.

The last step is wiring the player-facing portals in the lobby.

</details>

---

## 16. Place two Nexori portals in the lobby

On **server 1**, take Nexori portals from creative mode and place two portals in your lobby.

Use one portal to:

- join the queue

Use the other portal to:

- leave the queue

Interact with each portal and rename them to something easy to recognize.

> **Operator access required here**  
> In Nexori `2.0.0`, you do not currently need operator access just to open `/nexorimenu`, but you **do** need operator-level access to interact with Nexori portals during setup.
>
> Because of that, it is recommended to grant yourself operator access before reaching this step.

<details>
<summary>Why you want two portals</summary>

For a clean queue flow, you normally want:

- one portal that sends a player into the queue
- one portal that removes the player from the queue

Distinct names make the next binding steps easier.

</details>

---

## 17. Bind the two portals together

Open the admin menu and go to **Portals**.

Select:

- the join portal in table A
- the leave portal in table B

Then press the button that binds them in both directions.

<details>
<summary>What this binding does</summary>

This creates the paired relationship between the two portals.

At this point, that still does **not** mean the join portal is attached to your queue yet.

This step only wires the portal pair itself.

</details>

---

## 18. Attach the queue to the join portal

Still in the admin flow, go to **Queue Portals**.

Select:

- the portal that should join the queue
- the queue you created

Then attach that queue to the portal.

<details>
<summary>What this changes</summary>

Now that portal becomes the entry point into the queue.

Crossing it should place the player into the selected queue instead of only acting as a simple portal pair.

</details>

---

## 19. Mark the other portal as the leave-queue portal

In the same **Queue Portals** tab, select the second portal **without** selecting a queue. That will allow you to press the button that marks the portal as the one that removes players from the queue.

<details>
<summary>Why this matters</summary>

This gives your lobby a complete queue loop:

- one portal to join
- one portal to leave

At this point, the minigame flow is fully wired.

</details>

---

## 20. Test the full minigame flow

Walk into the queue-entry portal.

If everything is configured correctly, the queue should begin counting down once the minimum player requirement is satisfied.

<details>
<summary>What should happen next</summary>

Once the countdown finishes:

1. Nexori launches the match
2. players travel to server 2
3. players are placed in the saved spawn positions
4. the match begins

</details>

---

## 21. Test the match result

If you are testing alone, Nexori's built-in `Last Player Alive` trigger will usually resolve you as the winner quickly.

If you are testing with a friend, fight normally and let one player die.

<details>
<summary>Expected built-in behavior</summary>

With `Last Player Alive`:

- dead players are marked as `LOSS`
- the last surviving player is marked as `WIN`

After the result is recorded, Nexori handles the delayed return-to-lobby flow automatically.

</details>

---

## 22. Return to the lobby

Wait for the return flow to complete.

If everything is working, you should be sent back to the lobby on **server 1**.

<details>
<summary>Version note</summary>

In `2.0.0`, the return timing after a player dies is not configurable yet.

Right now, that delay is simply part of how the first public `2.0.0` release behaves.

There are a few directions this could improve in future versions:

- make the return delay configurable, so it can be reduced enough to feel almost instant
- wait for Hytale to provide a proper spectator mode and switch dead players into that flow
- build a custom spectator-style flow inside Nexori

For `2.0.0`, this was intentionally left simple so development could keep moving instead of getting blocked on a larger spectator-mode feature.

The important part for this release is that Nexori still resolves the result and returns the player to the lobby correctly.

</details>

---

## Result

If you completed the full setup, you now have:

- a trusted two-server Nexori network
- one lobby server
- one minigame server
- a queue entry portal
- a queue exit portal
- a built-in `Last Player Alive` match flow
- return-to-lobby behavior after the match ends

That gives you a complete backendless minigame flow built directly in-game.

---

## Connect two adventure servers

### Step 1. Place one portal on each server

Place one portal on each server and rename both portals to something easy to recognize.

<details>
<summary>Why this helps</summary>

You are about to sync portal information across servers and bind them for travel.

Using clear names makes it much easier to identify the correct portals in the admin tables.

</details>

---

### Step 2. Sync portal information across the servers

Go to the **Portals** tab.

It does not matter which server you are currently on.

If you inspect both servers from the portal view at this point, you will notice that you can only see the portal that belongs to the current server.

That happens because Nexori is backendless. The other servers do not automatically know that you placed a portal somewhere else.

Now press the button that syncs portal information across the servers.

<details>
<summary>What this is doing</summary>

Just like the initial secure setup, Nexori uses the player as a messenger.

This sync action carries the portal information to the other servers so they can become aware of the portals that exist elsewhere in the trusted network.

Without this sync step, the cross-server portal binding cannot be completed correctly.

</details>

---

### Step 3. Bind the portals for travel with inventory

Still in the **Portals** tab:

1. select the two portals
2. switch the travel mode from **Normal Travel** to **Travel With Inventory**
3. bind the portals in both directions

<details>
<summary>Why this guide uses Travel With Inventory</summary>

For the tests that follow, you need `Travel With Inventory`.

That mode makes Nexori carry the player's inventory across the server boundary, which allows you to test:

- normal inventory transfer
- failed travel recovery
- overwrite recovery
- backup pruning rules

</details>

---

### Step 4. Test a normal travel-with-inventory flow

Put some items in your inventory and use the portal.

If everything is working correctly, you should arrive on the destination server with the same inventory.

<details>
<summary>What should happen</summary>

For `Travel With Inventory`, Nexori moves the player's inventory across the travel boundary.

The important part for the next tests is understanding that Nexori treats that inventory transfer as something that must remain safe even if the travel fails halfway through.

</details>

---

### Step 5. Understand how inventory transfer avoids duplication

Nexori tries to prevent item duplication during cross-server travel.

<details>
<summary>How the transfer works</summary>

When a `Travel With Inventory` flow begins, the origin server clears the player's inventory and sends that inventory as context inside the referral payload.

The destination server rewrites that inventory into the player's inventory when the travel succeeds.

This solves one major problem: it avoids the situation where both servers keep a valid copy of the same inventory at the same time.

But it creates another question:

- what happens if the destination travel never completes successfully?

That is why Nexori also creates player-facing recovery backups.

</details>

---

### Step 6. Simulate a failed inventory travel

Put items in your inventory on **server 1**, use the travel portal, and then immediately cancel the travel.

You are simulating a failed travel such as:

- a disconnection
- a crash
- a power outage
- a machine shutdown
- or any interruption where the destination handoff does not complete

<details>
<summary>What this test is trying to prove</summary>

The goal is to reproduce a case where:

- the origin server already cleared the inventory
- but the destination server never completed the handoff

That is the exact situation where the recovery system matters.

</details>

---

### Step 7. Verify that the destination server does not have the inventory

After canceling the travel, manually connect to **server 2**.

You should notice that you do **not** have the inventory that originally existed on **server 1**.

<details>
<summary>Why this is expected</summary>

The travel never completed successfully.

So the destination server never finalized that inventory transfer into your live player state.

At this point, the important thing is that the inventory was also not duplicated.

Now you need to recover it safely.

</details>

---

### Step 8. Recover the backup as a normal player

Go back to **server 1** and run:

```text
/nexorirecovery
```

This opens the recovery UI.

Use this test as a **normal player**, not as an owner/admin workflow.

<details>
<summary>Why this feature matters</summary>

This flow is intentionally designed so a normal player does not need to:

- edit server files
- contact the server owner
- make manual recovery requests
- or use admin-only tools

The UI only shows backups that belong to the current player.

If this is your first failed test, you will probably see a single backup entry.

Use **Try Recover** on that backup.

Nexori will then ask the other server whether it actually received the inventory tied to that backup receipt.

- If the other server says **no**, Nexori allows the recovery and restores the inventory.
- If the other server says yes, Nexori does not restore it because that inventory already exists there or at least received it at some point.

When the recovery succeeds, the claimed backup should disappear.

If another recovery attempt fails again in the future, the backup should continue to exist until it is successfully claimed.

</details>

---

### Step 9. Test a backup that should not be recoverable

Now perform a normal `Travel With Inventory` flow that **does** complete successfully.

Then manually reconnect in a way that lets you attempt recovery from the original side again.

<details>
<summary>What this proves</summary>

Backups are created because each inventory travel removes the origin-side inventory first.

But if the destination server actually received that inventory, recovery should not restore a duplicate copy.

So when you try recovery in this case, Nexori should ask the destination server whether that backup receipt was already received.

This time the answer should be **yes**, and recovery should not give you the inventory back.

That is how Nexori avoids duplication while still allowing recovery for genuinely failed handoffs.

</details>

---

### Step 10. Test an overwrite backup

Now test a different case:

1. end up with your inventory on **server 2**
2. disconnect
3. manually rejoin **server 1**
4. arrive there with an empty or different inventory state
5. travel again so that one inventory state overwrites another

<details>
<summary>Why this test exists</summary>

In `2.0.0`, Nexori always moves the full inventory during travel, even when that inventory is empty.

The useful part is that whenever one inventory state overwrites another on arrival, Nexori creates a recovery path for the overwritten state.

So if you open the recovery UI after that overwrite, the newest backup may appear as a direct **Claim** instead of **Try Recover**.

That happens because Nexori recognizes that this inventory was overwritten by another inventory and does not need cross-server verification in the same way.

One practical note for `2.0.0`:

if recovery does not let you claim immediately, it may be because your current inventory is not empty and the current implementation overwrites the full inventory state.

For now, the simple test workaround is:

- drop your current items
- then claim the backup

This is an area that could improve later, for example by merging into empty slots instead of replacing the entire inventory.

</details>

---

### Step 11. Create a backup-pruning rule group

Now test the backup-pruning feature.

Open Nexori's admin menu and go to the **Rules** tab.

Create a new rule group by entering a group name and saving it.

Then configure:

- whether player recovery is allowed
- the maximum number of backups per player

For testing, set the backup limit to `2`.

<details>
<summary>Why this exists</summary>

If many players travel constantly between servers, the local server should not keep unlimited inventory backup data forever.

The rules system lets you cap how many backups can exist per player on the local server.

That keeps recovery available without letting backup storage grow forever.

</details>

---

### Step 12. Assign the servers to that rule group

In the **Rules** tab, add both servers to the rule group.

Then:

1. save the group
2. apply it to the assigned servers

<details>
<summary>What this does</summary>

Just like other Nexori network setup actions, this uses the same player-messenger style flow to distribute the rule configuration to the selected servers.

From that point on, the assigned servers should enforce the recovery behavior and backup limits defined in that rule group.

</details>

---

### Step 13. Verify that pruning really works

Repeat the travel and recovery scenarios that generate backups for a player.

Then confirm that the configured per-player backup limit is respected.

<details>
<summary>What you should verify</summary>

With a limit of `2`, keep generating new backups and make sure older backups are pruned according to the rule instead of growing without limit.

This final test confirms that Nexori's player recovery system is not only safe, but also bounded.

</details>


## Result

If you completed all of these steps correctly, you now have:

- two adventure servers connected through Nexori
- cross-server travel with inventory enabled
- player-facing recovery flows that can be used by normal players
- recovery checks designed to avoid item duplication
- overwrite recovery behavior for replaced inventories
- backup-pruning rules to keep recovery storage bounded

At this point, your players can travel between servers while carrying inventory, recover from failed handoffs without needing admin intervention, and keep using the system without creating duplicated inventories.

That concludes the `2.0.0` operator guide.