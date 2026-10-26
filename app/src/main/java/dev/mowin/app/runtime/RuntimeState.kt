package dev.mowin.app.runtime

enum class RuntimeState(val label: String) {
    NotInitialized("Not initialized"),
    Initializing("Initializing"),
    Ready("Ready"),
    Failed("Failed"),
}
