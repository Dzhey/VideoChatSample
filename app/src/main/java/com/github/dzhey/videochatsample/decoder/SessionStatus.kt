package com.github.dzhey.videochatsample.decoder

enum class SessionStatus {
    STARTED, FINISHED, RESTARTING, STOPPING, STOPPED, ERROR;

    val isTransitiveState: Boolean
        get() = this == STOPPING || this == RESTARTING

    val isStoppingOrStopped: Boolean
        get() = this == STOPPING || this == STOPPED
}