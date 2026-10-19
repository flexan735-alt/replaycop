# Fly in Survival (Fabric, Minecraft 1.21.11, Mojang mappings)

## Build
1. Easiest: generate a fresh template at https://fabricmc.net/develop (1.21.11, **Mojang mappings**, Fabric API), then copy `src/` over the template's `src/` and keep the template's Gradle files.
2. Or use the included Gradle files and run `gradle wrapper && ./gradlew build`.
3. The jar appears in `build/libs/` as `fly-in-survival-1.0.0.jar`. Put it in `.minecraft/mods` with Fabric API.

## Use
In a world where you have permission to use commands, run:

- `/fly on` to enable flight in Survival or Adventure. Double-tap jump to start flying.
- `/fly off` to disable flight and stop flying.

The `/fly` command requires permission level 2 (operator/cheats) and changes only the command-running player. In multiplayer, the mod must be installed on the server.

## No Java setup? Build online
The GitHub Actions workflow builds the mod and offers it as the `fly-in-survival-jar` artifact.
