# Development Guide

This document is for developers and CI maintainers working on The Apiarist Terminal.

- Game/Tooling target: Minecraft Forge 1.12.2, ForgeGradle 2.x, Java 8.
- Primary goal: expose Gendustry machines to OpenComputers as components (currently `advmutatron`).

## Project layout
- `src/main/java/net/ocgendustry/OCGendustryMod.java` — Mod entrypoint; hard requires OC + Gendustry and calls `DriverRegistry`.
- `src/main/java/net/ocgendustry/driver/DriverRegistry.java` — Central place to register all drivers (add more here as you extend support).
- `src/main/java/net/ocgendustry/driver/DriverAdvMutatron.java` — Driver for Advanced Mutatron (component: `advmutatron`).
- `src/main/resources/mcmod.info` — Mod metadata (hard depends on OC + Gendustry).

## Prerequisites
- JDK 8 on PATH (JAVA_HOME set to JDK 1.8).
- Gradle Wrapper is included (no manual Gradle installation needed).

## Dependency management: CurseMaven auto-download + deobf
This project can auto-download Gendustry and bdlib from CurseMaven and have ForgeGradle remap them (deobf) at compile time.
Configure the CurseForge project/file IDs via `gradle.properties` or `-P` overrides.

### Option A: gradle.properties (recommended for local dev)
Edit `gradle.properties` in the repository root and set numeric IDs:

```
# examples — replace with real IDs from CurseForge
# Gendustry
gendustryProjectId=XXXXX
gendustryFileId=YYYYYYY
# bdlib
bdlibProjectId=AAAAA
bdlibFileId=BBBBBBB
```

### Option B: Command-line overrides (useful in CI)

```bash
# Setup workspace with explicit IDs
./gradlew setupDecompWorkspace -PgendustryProjectId=XXXXX -PgendustryFileId=YYYYYYY -PbdlibProjectId=AAAAA -PbdlibFileId=BBBBBBB

# Build with the same IDs
./gradlew build -PgendustryProjectId=XXXXX -PgendustryFileId=YYYYYYY -PbdlibProjectId=AAAAA -PbdlibFileId=BBBBBBB
```

### How to find IDs
- On CurseForge, the `projectId` and `fileId` are visible in the URL of a file page (or via API). Choose the project (Gendustry / bdlib) and the specific file for 1.12.2.
- The Maven coordinates used by CurseMaven are: `curse.maven:<slug>-<projectId>:<fileId>`.

### Fallback: local deobf jars
If IDs are not provided, the build falls back to `compileOnly` jars in `libs/`:
- `libs/gendustry-deobf.jar`
- `libs/bdlib-deobf.jar`

See `libs/README.md` for options to obtain these.

## Build
ForgeGradle 2.x, Java 8. Uses Gradle Wrapper (no manual Gradle installation needed).

```bash
# First-time workspace setup (can be slow)
./gradlew setupDecompWorkspace

# Build the mod
./gradlew build
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

- Artifact output: `build/libs/apiarist-terminal-<version>.jar`.

## Extending to more Gendustry machines
- Create a new `DriverXxx` class implementing `SidedBlock`, with an `Env` component implementing `SimpleComponent`.
- Register it in `DriverRegistry.registerAll()`.
- Use strong typing to the relevant Gendustry Tile class (no reflection).

## Troubleshooting
- First build slow: The initial `setupDecompWorkspace` can take 10–30 minutes (decompiling MC/Forge). Subsequent builds are faster.
- Use Java 8: ForgeGradle 2.3 requires JDK 8. Running with Java 9+ can cause NPEs in tasks like `recompileMc`.
- Stale generated sources: If you see errors in `build/sources/...` (e.g., duplicate `package` lines):
  1. Remove stale outputs: `./gradlew clean`
  2. Re-run: `./gradlew setupCiWorkspace` (faster) or `./gradlew setupDecompWorkspace`
  3. Build: `./gradlew build`
- Network hiccups: If Forge/MCP downloads time out/corrupt, clear `~/.gradle/caches/minecraft` (Linux/Mac) or `%USERPROFILE%\.gradle\caches\minecraft` (Windows) and retry.

## Notes
- Component name: `advmutatron`. Attach an Adapter/Cable to the Advanced Mutatron during in-game testing.
- Logging tag: `[ApiaristTerminal]` for driver-related diagnostics.
