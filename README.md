# Mocap Studio (Fabric, Minecraft 1.21.11, Mojang mappings)

## Build
1. Easiest: generate a fresh template at https://fabricmc.net/develop (1.21.11, **Mojang mappings**, Fabric API),
   then copy `src/` over the template's `src/` (replace fabric.mod.json) and keep the template's gradle files.
   Or use the included build.gradle/gradle.properties (check the versions at the same site) and run `gradle wrapper && ./gradlew build`.
2. Jar appears in `build/libs/` (use the one WITHOUT `-sources`). Put it in `.minecraft/mods` with Fabric API.

## Use
Press **M** in-game. Or commands: /studio record <name> [scene], /studio stop, /studio play <name> [relative],
/studio scene create|add|remove|delete, /studio playscene <scene> [relative], /studio skin ..., /studio clear.
Files live in `.minecraft/mocapstudio/`.

## No Java setup? Build online
Push this folder to a free GitHub repo. The Actions tab builds the jar and offers it as a download (artifact "mocap-studio-jar").
