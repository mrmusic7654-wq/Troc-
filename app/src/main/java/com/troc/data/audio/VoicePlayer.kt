package com.troc.data.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoicePlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null

    fun getPlayer(): ExoPlayer {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                // Use speakerphone for voice mode
            }
        }
        return exoPlayer!!
    }

    fun playWavFile(file: File, onComplete: (() -> Unit)? = null) {
        val player = getPlayer()
        player.stop()
        player.clearMediaItems()
        val mediaItem = MediaItem.fromUri(file.toURI().toString())
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        if (onComplete != null) {
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        player.removeListener(this)
                        onComplete()
                    }
                }
            })
        }
    }

    fun playByteArray(wavBytes: ByteArray, cacheDir: File, onComplete: (() -> Unit)? = null): File {
        val tempFile = File.createTempFile("tts_${System.currentTimeMillis()}", ".wav", cacheDir)
        tempFile.writeBytes(wavBytes)
        playWavFile(tempFile, onComplete)
        return tempFile
    }

    fun queueFiles(files: List<File>) {
        val player = getPlayer()
        player.stop()
        player.clearMediaItems()
        files.forEach { file ->
            player.addMediaItem(MediaItem.fromUri(file.toURI().toString()))
        }
        player.prepare()
        player.play()
    }

    fun stop() {
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }

    fun isPlaying(): Boolean = exoPlayer?.isPlaying == true

    fun observePlaybackState(): Flow<PlayerState> = callbackFlow {
        val player = getPlayer()
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                trySend(if (isPlaying) PlayerState.Playing else PlayerState.Paused)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> trySend(PlayerState.Ended)
                    Player.STATE_READY -> trySend(PlayerState.Ready)
                    Player.STATE_BUFFERING -> trySend(PlayerState.Buffering)
                }
            }
        }
        player.addListener(listener)
        awaitClose { player.removeListener(listener) }
    }

    sealed class PlayerState {
        object Playing : PlayerState()
        object Paused : PlayerState()
        object Ended : PlayerState()
        object Ready : PlayerState()
        object Buffering : PlayerState()
    }
}
