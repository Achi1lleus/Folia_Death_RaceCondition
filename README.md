# Folia isRemoved() Edge Case Dupe

Meteor Client module exploiting Folia's `isRemoved()` death handling bug.

**Worked once.**

## The Bug

In Folia, players are NEVER removed from the world on death - only on disconnect.
This means `isRemoved()` always returns false after death, breaking death checks in `AbstractContainerMenu`.

```java
// Folia's broken death check
if (player.isRemoved() && player.getRemovalReason() != CHANGED_DIMENSION) {
    // drop items
}
// But isRemoved() returns FALSE for dead players in Folia
// So crafting grid items behave unexpectedly
```

## The Exploit

1. Place item in crafting grid (e.g. Melon)
2. Take crafting output (e.g. Melon Seeds) - this consumes the input
3. Send `/kill` command
4. Disconnect while dead (or immediately after)
5. Reconnect → Both INPUT and OUTPUT in inventory = **DUPE**

## What Happened

- Jan 1, 2026: Found the bug by analyzing Folia source with Claude Opus
- Built this module to test it
- Successfully duped once on 2b2t
- ~10 minutes later: Server restart, `/kill` no longer works while inventory open
- Haven't been able to reproduce since - rare edge case

## Important: /kill Method Changed After Restart

Before the server restart, we used:
```java
mc.player.networkHandler.sendChatMessage("/kill");
```

**This stopped working after the restart.** The server now blocks `/kill` sent this way while inventory is open.

However, this method still works:
```java
mc.getNetworkHandler().sendChatCommand("kill");
```

They patched `sendChatMessage` but `sendChatCommand` still goes through. Interesting difference in how the server handles these two methods. The module now uses `sendChatCommand`.

## Settings

- `delay-after-death`: Ticks to wait after death before disconnect
- `delay-before-kill`: Ticks to wait between craft and kill
- `disconnect-before-kill`: Send disconnect and kill in same tick
- `fill-all-slots`: Fill all 4 crafting slots

## Building

```bash
./gradlew build
```

JAR will be in `build/libs/`

## Note

This is research code. The vulnerability exists but reproduction is inconsistent.
More people experimenting = someone finds the reproducible method.

The Folia source is public: https://github.com/PaperMC/Folia

## Credits

- Exploit research: Achi1lleus + Claude Opus
- First AI-assisted Minecraft dupe discovery (probably)

