package com.tej.directo.ui.call

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.shepeliev.webrtckmp.VideoTrack
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.EglBase

@Composable
fun WebRtcVideoView(
    videoTrack: VideoTrack?,
    modifier: Modifier = Modifier,
    mirror: Boolean = false
) {
    val eglBaseContext = remember { EglBase.create().eglBaseContext }
    
    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                init(eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setMirror(mirror)
            }
        },
        modifier = modifier,
        update = { view ->
            videoTrack?.addSink(view)
        },
        onRelease = { view ->
            videoTrack?.removeSink(view)
            view.release()
        }
    )
}
