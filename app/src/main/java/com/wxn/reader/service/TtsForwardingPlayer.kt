package com.wxn.reader.service

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

@UnstableApi
class TtsForwardingPlayer(player: Player) : ForwardingPlayer(player) {

    override fun isCommandAvailable(command: Int): Boolean {
        if (command == Player.COMMAND_SEEK_TO_NEXT) return true
        return super.isCommandAvailable(command)
    }

    override fun seekToNext() {
    }
}