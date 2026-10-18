# Elemental Souls (Minecraft 1.21.11 / Fabric)

## Current prototype scope

- `/soul fire` — hide the normal player model with invisibility and surround the player with a fire-particle body; grants fire resistance while morphed.
- `/soul water` — hide the normal player model and create a flowing water-particle body.
- `/soul off` — turn the morph off.
- `/soul bridge fire` — build a 25-block fire-particle Japanese-style arched bridge.
- `/soul bridge water` — build a 25-block water-particle bridge.
- Bridge construction animates over 5 seconds, remains complete for 25 seconds, then dissolves.
- Bridge visuals use particles only: no blocks are placed or changed. A server-side virtual-surface movement assist is used to help players stand and walk on the bridge without fire damage.

## Build

Requires JDK 21. From this directory run:

```sh
gradle build
```

The JAR is produced in `build/libs/`.

## Prototype notes

This is an early gameplay prototype. The player morph is currently represented by a dense particle silhouette around an invisible player, rather than a custom mesh. The virtual bridge surface uses movement correction rather than Minecraft block collision, so edge cases such as very fast movement, lag, and jumping should be play-tested and tuned. No blocks are placed by the bridge ability.
