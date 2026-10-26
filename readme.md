# MoWin

**A minimal Windows compatibility runtime for Android, powered by Linux and open-source compatibility technologies.**

MoWin aims to bring Windows application and game execution to Android devices by combining a self-contained Arch Linux ARM64 userspace with CPU translation and Windows compatibility technologies.

The goal is to create a lightweight runtime that lets users manage and launch compatible Windows applications and games directly from Android, without requiring a separate desktop Linux installation.

> **Project status:** Experimental / Proof of Concept. Windows application compatibility, game execution, and hardware-accelerated graphics are development goals, not guaranteed capabilities.

## Table of Contents

- [Vision](#vision)
- [Objectives](#objectives)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Game Store Integration](#game-store-integration)
- [Execution Pipeline](#execution-pipeline)
- [Development Roadmap](#development-roadmap)
- [Target Platform](#target-platform)
- [Constraints and Challenges](#constraints-and-challenges)
- [Security and Licensing](#security-and-licensing)
- [Project Status](#project-status)
- [Contributing](#contributing)
- [Acknowledgements](#acknowledgements)

## Vision

MoWin is an attempt to build a modular Windows runtime for Android rather than a complete operating system or a new CPU emulator.

Instead of reimplementing existing compatibility technologies, MoWin aims to integrate established open-source projects and provide an Android-native layer for runtime management, application installation, configuration, and launching.

The long-term vision is to support:

- Windows x86-64 applications and games on ARM64 Android devices.
- A self-contained Arch Linux ARM64 userspace managed by the application.
- Per-application environments and configurable runtime settings.
- Offline Windows game installers, including compatible DRM-free games from GOG.
- Future storefront integrations, potentially including Steam.
- A unified interface for installing, configuring, and launching applications.

Compatibility will depend on each application's architecture, dependencies, graphics requirements, and runtime support.

## Objectives

### Primary objectives

1. Provide an Android application that manages its own Linux userspace.
2. Create a persistent Arch Linux ARM64 environment in application-managed storage.
3. Support native ARM64 Linux executable execution.
4. Investigate x86-64 execution through a suitable CPU translation layer.
5. Integrate Wine or selected Proton components for Windows API compatibility.
6. Provide a unified interface for managing and launching applications.
7. Establish a modular architecture that can accommodate multiple game distribution sources.

### Non-goals for the initial prototype

- Reimplementing Wine, Proton, or a CPU emulator from scratch.
- Replacing Android's kernel or booting a separate Linux kernel.
- Guaranteeing compatibility with every Windows application.
- Supporting every DirectX version or graphics feature.
- Bypassing DRM, licensing restrictions, or anti-cheat systems.

## Architecture

MoWin is designed around a layered architecture.

```text
+---------------------------------------+
|           MoWin Android App           |
|   UI, Library, Settings, Input        |
+---------------------------------------+
                    |
+---------------------------------------+
|          Runtime Manager              |
| Install, Configure, Launch, Logs       |
+---------------------------------------+
                    |
+---------------------------------------+
|      Arch Linux ARM64 Userspace       |
| Libraries, Tools, Filesystem, Prefixes |
+---------------------------------------+
                    |
+---------------------------------------+
|      Windows Compatibility Layer      |
|           Wine / Proton               |
+---------------------------------------+
                    |
+---------------------------------------+
|      CPU Translation Layer            |
|        x86-64 to ARM64                |
+---------------------------------------+
                    |
+---------------------------------------+
|       Android / Linux Kernel          |
|    Device Drivers and Hardware        |
+---------------------------------------+
```

This diagram represents the intended architecture; individual components may require a different arrangement depending on the chosen CPU translation and graphics technologies.

### 1. Android application

The Android application is responsible for:

- Providing the user interface.
- Managing application metadata and game libraries.
- Importing installers and selecting executable files.
- Managing runtime configurations.
- Launching processes and collecting logs.
- Managing application data and storage permissions.

### 2. Arch Linux ARM64 userspace

MoWin will maintain its own minimal Arch Linux ARM64 root filesystem inside app-managed storage.

The initial prototype can use PRoot to provide a Linux-like filesystem and process environment without requiring root access.

This is a userspace environment, not a virtual machine. It shares the Android kernel and does not independently boot Arch Linux.

### 3. CPU translation

Windows x86-64 applications cannot execute directly as ordinary ARM64 Linux binaries.

A compatible CPU translation technology is therefore required to execute x86-64 code on an ARM64 processor.

Potential technologies to investigate include:

- [Box64](https://github.com/ptitSeb/box64)
- [FEX](https://github.com/FEX-Emu/FEX)

Their suitability must be evaluated against the target architecture, Android environment, binary formats, and integration requirements.

### 4. Windows compatibility

[Wine](https://www.winehq.org/) provides Windows API compatibility by implementing Windows interfaces on Unix-like systems.

[Proton](https://github.com/ValveSoftware/Proton), maintained by Valve, builds on Wine and incorporates additional components intended to improve compatibility with Windows games.

MoWin aims to reuse these projects where practical rather than implement Windows APIs itself.

Proton is not a complete standalone Android runtime. Its dependencies, launcher assumptions, architecture support, and graphics requirements must be addressed during integration.

### 5. Graphics

Graphics compatibility is a separate engineering challenge.

Potential components include:

- [DXVK](https://github.com/doitsujin/dxvk) for translating supported Direct3D 9, 10, and 11 workloads to Vulkan.
- [vkd3d-proton](https://github.com/HansKristian-Work/vkd3d-proton) for Direct3D 12 compatibility through Vulkan.

The Android device's Vulkan support, GPU drivers, memory constraints, and compatibility with the selected translation stack will determine which applications can use accelerated graphics.

Desktop Linux graphics drivers cannot be assumed to work on Android hardware.

## Technology Stack

| Component | Candidate technology |
|---|---|
| Platform | Android |
| Application development | Kotlin, Android SDK |
| Native integration | C/C++, Android NDK where necessary |
| Linux userspace | Arch Linux ARM64 |
| Initial environment isolation | PRoot |
| CPU translation | Box64 or FEX, subject to compatibility testing |
| Windows API compatibility | Wine |
| Gaming compatibility | Selected Proton components |
| DirectX translation | DXVK / vkd3d-proton, where supported |
| Build environment | Android Studio, Gradle, Linux development tools |

These are candidate technologies. The final stack will be determined by prototype results rather than assuming every component can be combined without modification.

## Game Store Integration

MoWin is intended to separate game discovery and installation from the runtime used to execute games.

Each store integration should act as a library adapter that supplies game metadata, installation files, launch information, and optional store-specific functionality.

### GOG

GOG is a promising initial distribution source because many games provide offline installers and are designed to run without requiring the GOG GALAXY client.

The initial workflow can support:

1. Importing a user-provided offline installer.
2. Installing or extracting compatible game files.
3. Creating a per-game runtime configuration.
4. Identifying the executable and required launch arguments.
5. Launching the game through the configured compatibility stack.

Store authentication, automatic library synchronization, updates, cloud saves, and achievements are separate features that may require additional integration.

DRM-free distribution does not guarantee that a game will run under Wine or on ARM64 hardware.

### Steam

Steam support is a potential later milestone.

Proton is primarily designed for use with Steam Play. Integrating it into MoWin may require adapting its launch workflow, runtime dependencies, graphics configuration, and architecture assumptions.

A standalone game launcher is a more appropriate initial target than attempting to run the entire Steam client inside MoWin.

### Store-independent runtime

The intended separation is:

```text
GOG Adapter --------+
Steam Adapter ------+----> MoWin Runtime Manager
Manual Import ------+              |
                                    v
                         Configured Game Environment
                                    |
                                    v
                         Wine / Proton Components
                                    |
                                    v
                            Application Launch
```

This separation should allow the runtime to evolve independently of any specific storefront.

## Execution Pipeline

The intended execution pipeline for a Windows game is:

1. **Import:** Obtain the installer or game files from a legitimate source.
2. **Install:** Place the game in its designated storage directory.
3. **Configure:** Create or select the appropriate compatibility environment.
4. **Resolve dependencies:** Identify required libraries, architecture, and runtime settings.
5. **Translate CPU instructions:** Use a compatible translation layer when the application contains x86-64 code.
6. **Implement Windows APIs:** Execute through Wine or an appropriate Proton-based environment.
7. **Translate graphics:** Use a compatible graphics translation path when needed.
8. **Launch:** Start the application and capture logs, exit status, and errors.

Not every game will complete this pipeline successfully. Failures at the CPU, Windows API, graphics, or application-specific level must be diagnosed independently.

## Development Roadmap

### Phase 1 — Android foundation

- [x] Create the Android Studio project.
- [x] Install and launch the application on the target device.
- [x] Establish app-private storage for runtime files.
- [x] Implement a basic command execution and logging interface.

### Phase 2 — Linux userspace

- [x] Obtain a compatible Arch Linux ARM64 root filesystem.
- [x] Initialize the root filesystem in app-managed storage.
- [x] Configure PRoot or an alternative execution environment.
- [x] Verify shell execution and persistent filesystem state.
- [x] Run and validate a native ARM64 Linux executable.

### Phase 3 — Cross-architecture execution

- [ ] Evaluate Box64 and FEX for the target environment.
- [ ] Build or obtain a compatible CPU translation runtime.
- [ ] Execute a simple x86-64 Linux test binary.
- [ ] Document architecture, loader, and library compatibility issues.

### Phase 4 — Windows compatibility

- [ ] Evaluate Wine and Proton dependencies.
- [ ] Determine a viable ARM64-compatible runtime configuration.
- [ ] Attempt to launch a simple Windows executable.
- [ ] Implement per-application environment configuration.
- [ ] Investigate graphics acceleration separately.

### Phase 5 — Game management

- [ ] Implement manual game and installer import.
- [ ] Add installation directories and launch configurations.
- [ ] Prototype support for a GOG offline installer.
- [ ] Add per-game logs and error reporting.
- [ ] Evaluate additional storefront adapters.

### Phase 6 — Hardening and distribution

- [ ] Improve process lifecycle and error handling.
- [ ] Verify storage isolation and permissions.
- [ ] Measure memory use, startup time, and execution performance.
- [ ] Package a reproducible APK build.
- [ ] Document tested applications and known limitations.

## Target Platform

The initial target is the Samsung Galaxy S23 with an ARM64 processor running Android.

The implementation must
