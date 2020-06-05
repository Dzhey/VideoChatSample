package com.github.dzhey.videochatsample.decoder

interface SessionStatusSupplier {
    /**
     * Represents current decode session status
     * @see [SessionStatus]
     */
    val currentStatus: SessionStatus
}