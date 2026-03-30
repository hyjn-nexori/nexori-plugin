# Nexori Vanilla UI Notes

## Scope

This document tracks what we know about building Nexori's in-game UI using
Hytale's vanilla custom page system.

For Nexori, "vanilla UI" means:

- `InteractiveCustomUIPage`
- `.ui` page files under `src/main/resources/Common/UI/Custom/Pages/...`
- `UICommandBuilder` and `UIEventBuilder`
- page data codecs
- event bindings through `CustomUIEventBindingType`

This document is **not** about HyUI.

## Working Rules

- Prefer reusing page patterns that are already proven in-game before creating
  brand-new UI structures.
- Keep gameplay-facing UI and admin/owner UI separated.
- Use the same selectors in Java and `.ui` files exactly; selector mismatches
  are a common source of disconnects.
- Treat every new UI bug as something to confirm in game before documenting the
  final fix here.

## Error Documentation Policy

When a new UI bug appears:

1. reproduce the bug
2. propose and implement a likely fix in code
3. let the user test the fix in game
4. only after the user confirms it worked, add a dedicated error entry here

That means this file should only contain:

- confirmed UI behaviors
- confirmed selector rules
- confirmed solutions

It should not contain guesses that have not been validated in game yet.

## Confirmed UI Patterns

### Custom Page Structure

- Nexori pages currently work by combining:
  - a Java page class extending `InteractiveCustomUIPage`
  - a `.ui` file loaded with `commands.append(...)`
  - index or action event bindings
  - a `PageData` codec carrying UI event data back into Java

### Selector Safety

- If Java sets or binds a selector that does not exist in the `.ui` file,
  Hytale can disconnect the player from the server instead of failing softly.
- Because of that, selector changes must be kept in sync between Java and `.ui`
  files.

### Resume-After-Referral Pattern

- Nexori already uses a working pattern where an admin flow can:
  - save draft state
  - send the player through a secure referral
  - return to the origin server
  - reopen the UI in the same step with the same draft

This pattern is already working for:

- destination target discovery
- portal setup resume
- inventory recovery result reopen

## Confirmed Error Fixes

### Long Label Text Gets Clipped Instead Of Wrapping

Symptom:

- a `Label` with long text stays on one line and gets cut off instead of
  flowing to a second line

Confirmed fix:

- set `Wrap: true` inside the label `Style`
- also give the label a real width so Hytale knows the wrap boundary

Confirmed vanilla reference:

- `Common/UI/Custom/Pages/PortalDeviceError.ui`
- vanilla uses:
  - `Style: (...$C.@DefaultLabelStyle, Wrap: true, HorizontalAlignment: Center);`

What worked in Nexori:

- for `#RulesHintText` in
  [NexoriRulesBody.ui](D:\JanielNunez\hyjn-nexori\nexori-plugin\src\main\resources\Common\UI\Custom\Pages\Nexori\NexoriRulesBody.ui)
  the working combination was:
  - `Anchor: (Width: 520);`
  - `Style: (TextColor: ..., Wrap: true);`

## Pending UI Issues

Add future items here only after the user confirms the fix works in game.
