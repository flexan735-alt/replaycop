# Mocap Studio (Fabric, Minecraft 1.21.11, Mojang mappings)

## Build
1. Easiest: generate a fresh template at https://fabricmc.net/develop (1.21.11, **Mojang mappings**, Fabric API), then copy `src/` over the template's `src/` (replace fabric.mod.json) and keep the template's Gradle files.
2. Or use the included Gradle files and run `gradle wrapper && ./gradlew build`.
3. The jar appears in `build/libs/` (use the one without `-sources`). Put it in `.minecraft/mods` with Fabric API.

## Use
In a world where you have permission to use commands, run:

- `/fly on` to enable flight in Survival or Adventure. Double-tap jump to start flying.
- `/fly off` to disable flight and stop flying.
- `/reco` to snapshot/restore an arena.
- The existing Mocap Studio controls include **M** in-game and `/studio record <name> [scene]`, `/studio stop`, `/studio play <name> [relative]`, scene commands, skin commands, and clear.

The `/fly` command requires permission level 2 (operator/cheats) and changes only the command-running player. In multiplayer, the mod must be installed on the server.

Files live in `.minecraft/mocapstudio/`.

## No Java setup? Build online
Push this folder to a GitHub repo. The Actions tab builds the jar and offers it as a download (artifact `mocap-studio-jar`).
