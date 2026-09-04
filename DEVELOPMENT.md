# Development Guide

This document is for developers and CI maintainers working on The Apiarist Terminal.

## Target stack
- Game target: Minecraft Forge 1.12.2.
- Build system: Gradle 9 (Java 25).
- Build target: the mod still compiles against Java 8-compatible code.
- Primary goal: expose Gendustry machines to OpenComputers as components (currently `advmutatron`).

## Project layout
- `src/main/java/net/ocgendustry/OCGendustry.java` — Mod entrypoint; hard requires OC + Gendustry and calls `DriverRegistry`.
- `src/main/java/net/ocgendustry/driver/DriverRegistry.java` — Central place to register all drivers (add more here as you extend support).
- `src/main/java/net/ocgendustry/driver/DriverAdvMutatron.java` — Driver for Advanced Mutatron (component: `advmutatron`).
- `src/main/resources/mcmod.info` — Mod metadata (hard depends on OC + Gendustry).

## Prerequisites
- JDK 25 on PATH.
- Internet access on the first build, so Gradle can download the wrapper, Minecraft/Forge artifacts, and required toolchains.

Gradle Wrapper is included, so no manual Gradle installation is needed. `settings.gradle` enables automatic toolchain provisioning, and the build resolves the legacy Java runtimes it still needs.

## Build
For most work, a plain build is enough:

```bash
# Windows
gradlew.bat build

# Linux / macOS / WSL
./gradlew build
```

- Artifact output: `build/libs/apiarist-terminal-<version>.jar`.

## Extending to more Gendustry machines
- Create a new `DriverXxx` class implementing `SidedBlock`, with an `Env` component implementing `SimpleComponent`.
- Register it in `DriverRegistry.registerAll()`.
- Use strong typing to the relevant Gendustry Tile class (no reflection).

## Troubleshooting
- First build slow: The initial build can take 10–30 minutes while Gradle downloads Minecraft/Forge artifacts, generates patched sources, and resolves required toolchains. Subsequent builds are faster, especially if using Gradle's daemon and caching (mainly via IntelliJ IDEA).
- Wrong host JDK: Gradle 9.2.1 should be launched with JDK 25. If Gradle starts under an older JVM, update `JAVA_HOME` or PATH and retry.
- Toolchain download issues: If automatic provisioning is disabled or blocked, enable Gradle toolchain downloads or install the requested toolchains locally and retry.
- Stale generated sources: If you see errors in `build/sources/...` (e.g., duplicate `package` lines):
  1. Remove stale outputs: `gradlew.bat clean` on Windows, or `./gradlew clean` on Linux / macOS / WSL.
  2. Re-run the build: `gradlew.bat build` on Windows, or `./gradlew build` on Linux / macOS / WSL.
- Network hiccups: If Forge/MCP downloads time out/corrupt, clear `~/.gradle/caches/minecraft` (Linux/Mac) or `%USERPROFILE%\.gradle\caches\minecraft` (Windows) and retry.

## Notes
- Component name: `advmutatron`. Attach an Adapter/Cable to the Advanced Mutatron during in-game testing.
- Logging tag: `[ApiaristTerminal]` for driver-related diagnostics.
