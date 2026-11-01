package app.petdance.ui.components

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.petdance.media.VideoCache
import org.koin.compose.koinInject

@Composable
fun LoopVideo(
    url: String,
    muted: Boolean,
    modifier: Modifier = Modifier,
    onError: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val cache = koinInject<VideoCache>()
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = androidx.compose.runtime.remember(url) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cache.dataSourceFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = if (muted) 0f else 1f
                playWhenReady = true
                prepare()
            }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onError("视频暂时无法播放")
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_START -> player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { view -> view.player = player },
    )
}
