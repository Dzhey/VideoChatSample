package com.github.dzhey.videochatsample.decoder

interface SessionStatusObserver {
    fun onNext(status: SessionStatus)
}