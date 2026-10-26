# MoWin — Development Plan

A practical checklist for building MoWin, an experimental Windows compatibility runtime for Android.

**Initial target:** Samsung Galaxy S23 (ARM64, Android)  
**Sprint target:** A working Android prototype with its own persistent Arch Linux ARM64 userspace, plus a tested attempt at cross-architecture and Windows execution.

> Check items off only after verifying them on the target device. Planned support is not the same as working support.

## 0. Scope and Setup

- [ ] Confirm the initial success criteria for the 24-hour sprint.
- [ ] Install and verify Android Studio and the Android SDK.
- [ ] Confirm Android SDK Platform Tools (`adb`) work.
- [ ] Enable developer options and USB debugging on the test phone.
- [ ] Verify the phone is detected with `adb devices`.
- [ ] Create the Git repository and initial project structure.
- [ ] Add a `README.md` describing the project and current status.
- [ ] Record device model, Android version, ABI, available storage, and memory.

## 1. Android Application Foundation

- [ ] Create the Android project.
- [ ] Build and install a debug APK on the phone.
- [ ] Create a minimal main screen.
- [ ] Add a runtime status area.
- [ ] Add a console or command-output view.
- [ ] Add persistent application logging.
- [ ] Handle process start, exit, and failure states.
- [ ] Establish app-private storage for the runtime.
- [ ] Confirm runtime files persist after closing and reopening MoWin.
- [ ] Add basic error messages for missing files and failed commands.

## 2. Arch Linux ARM64 Userspace

- [ ] Choose a compatible Arch Linux ARM64 root filesystem source.
- [ ] Verify the root filesystem architecture and integrity.
- [ ] Create the root filesystem directory in app-private storage.
- [ ] Extract or initialize the root filesystem.
- [ ] Select and integrate an initial userspace execution method, such as PRoot.
- [ ] Configure the required environment variables and filesystem paths.
- [ ] Launch a shell inside the root filesystem.
- [ ] Verify `uname -m` and package architecture report the expected ARM64 environment.
- [ ] Run basic commands such as `ls`, `cat`, and `sh`.
- [ ] Verify files persist across app restarts.
- [ ] Document Android-specific filesystem or permission constraints.
- [ ] Test cleanup and recovery if initialization is interrupted.

## 3. Native ARM64 Execution

- [ ] Compile or obtain a minimal ARM64 Linux test executable.
- [ ] Place the executable inside the userspace.
- [ ] Launch it through MoWin.
- [ ] Capture standard output, standard error, and exit status.
- [ ] Test a second executable that uses shared libraries.
- [ ] Document loader and library-path requirements.
- [ ] Verify process termination does not leave MoWin in a broken state.

## 4. x86-64 CPU Translation Investigation

- [ ] Review Box64 support and requirements for the target environment.
- [ ] Review FEX support and requirements for the target environment.
- [ ] Select one candidate based12
