package com.brpportal.codemotion

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.min

private const val EXPORT_FPS = 30
private const val MAX_EXPORT_MS = 60_000L

@Composable
fun ExportScreen(project: Project, onBack: () -> Unit) {
    val context = LocalContext.current
    var hd by remember { mutableStateOf(true) }
    var exporting by remember { mutableStateOf(false) }
    var doneFrames by remember { mutableStateOf(0) }
    var totalFrames by remember { mutableStateOf(0) }
    var resultPath by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val totalMs = min(Renderer.totalDurationMs(project), MAX_EXPORT_MS)
    val cappedNote = Renderer.totalDurationMs(project) > MAX_EXPORT_MS

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            Button(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(12.dp))
            Text(
                "Export MP4",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Project: ${project.width}x${project.height} • " +
                "${project.scenes.size} scenes • duration ${totalMs / 1000.0}s • $EXPORT_FPS fps"
        )
        if (cappedNote) {
            Text(
                "Note: export 60 second par cap hai (project is se lamba hai).",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("Output size", fontWeight = FontWeight.Bold)
        Row {
            FilterChip(
                selected = hd,
                onClick = { hd = true },
                label = { Text("1080 x 1920") }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = !hd,
                onClick = { hd = false },
                label = { Text("720 x 1280") }
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                exporting = true
                error = null
                resultPath = null
                doneFrames = 0
                totalFrames = 0
                val outW = if (hd) 1080 else 720
                val outH = if (hd) 1920 else 1280
                thread(name = "codemotion-export") {
                    try {
                        val file = Mp4Exporter.export(
                            context = context.applicationContext,
                            project = project,
                            outW = outW,
                            outH = outH,
                            fps = EXPORT_FPS,
                            onProgress = { done, total ->
                                postToMain {
                                    doneFrames = done
                                    totalFrames = total
                                }
                            }
                        )
                        postToMain {
                            exporting = false
                            resultPath = file.absolutePath
                        }
                    } catch (e: Exception) {
                        postToMain {
                            exporting = false
                            error = "Export error: ${e.message}"
                        }
                    }
                }
            },
            enabled = !exporting && project.scenes.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (exporting) "Export ho raha hai…" else "Export MP4")
        }

        if (exporting) {
            Spacer(Modifier.height(14.dp))
            val frac = if (totalFrames > 0) doneFrames.toFloat() / totalFrames else 0f
            LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text("Frames: $doneFrames / $totalFrames")
        }
        if (resultPath != null) {
            Spacer(Modifier.height(14.dp))
            Text("Ho gaya! File:", fontWeight = FontWeight.Bold)
            Text(resultPath!!, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            Button(onClick = {
                val file = File(resultPath!!)
                shareMp4(context, file)
            }) { Text("Share MP4") }
        }
        if (error != null) {
            Spacer(Modifier.height(14.dp))
            Text(error!!, color = androidx.compose.ui.graphics.Color(0xFFB00020))
        }
    }
}

private fun postToMain(block: () -> Unit) {
    Handler(Looper.getMainLooper()).post(block)
}

private fun shareMp4(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share MP4"))
    } catch (e: Exception) {
        // Sharing is best-effort; the file path is already shown on screen.
    }
}

/**
 * Frame-by-frame software export: Renderer -> Bitmap -> NV12 -> MediaCodec
 * (AVC) -> MediaMuxer (.mp4). Framework APIs only, so it is minify-safe.
 */
object Mp4Exporter {

    fun export(
        context: Context,
        project: Project,
        outW: Int,
        outH: Int,
        fps: Int,
        onProgress: (done: Int, total: Int) -> Unit
    ): File {
        val totalMs = min(Renderer.totalDurationMs(project), MAX_EXPORT_MS)
        val totalFrames = ((totalMs * fps) / 1000L).toInt().coerceAtLeast(1)
        onProgress(0, totalFrames)

        val outDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "movies")
        outDir.mkdirs()
        val outFile = File(outDir, "codemotion_${System.currentTimeMillis()}.mp4")

        val bitRate = if (outW >= 1080) 10_000_000 else 6_000_000
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, outW, outH)
            .apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                )
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        var muxer: MediaMuxer? = null
        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()

        fun drain(endOfStream: Boolean) {
            var done = false
            while (!done) {
                val outIdx = codec.dequeueOutputBuffer(
                    bufferInfo,
                    if (endOfStream) 100_000 else 10_000
                )
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        if (muxer == null) {
                            muxer = MediaMuxer(
                                outFile.absolutePath,
                                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                            )
                        }
                        if (!muxerStarted) {
                            trackIndex = muxer!!.addTrack(newFormat)
                            muxer!!.start()
                            muxerStarted = true
                        }
                    }
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> done = true
                    outIdx >= 0 -> {
                        val encoded = codec.getOutputBuffer(outIdx)
                        if (encoded != null) {
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size > 0 && muxerStarted) {
                                encoded.position(bufferInfo.offset)
                                encoded.limit(bufferInfo.offset + bufferInfo.size)
                                muxer!!.writeSampleData(trackIndex, encoded, bufferInfo)
                            }
                        }
                        val eos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outIdx, false)
                        if (eos) done = true
                    }
                    else -> done = true
                }
            }
        }

        val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val bmpCanvas = Canvas(bitmap)
        val pixels = IntArray(outW * outH)
        val yuv = ByteArray(outW * outH * 3 / 2)

        try {
            for (frame in 0 until totalFrames) {
                val tMs = frame * 1000L / fps
                val (sceneIdx, localMs) = Renderer.locate(project, tMs)
                Renderer.drawScene(bmpCanvas, project, sceneIdx, localMs)
                bitmap.getPixels(pixels, 0, outW, 0, 0, outW, outH)
                argbToNv12(pixels, outW, outH, yuv)

                val inIdx = codec.dequeueInputBuffer(100_000)
                if (inIdx >= 0) {
                    val input = codec.getInputBuffer(inIdx)
                    if (input != null) {
                        input.clear()
                        input.put(yuv)
                        val ptsUs = frame * 1_000_000L / fps
                        codec.queueInputBuffer(inIdx, 0, yuv.size, ptsUs, 0)
                    }
                }
                drain(false)
                if (frame % 5 == 0 || frame == totalFrames - 1) {
                    onProgress(frame + 1, totalFrames)
                }
            }
            // End of stream.
            val inIdx = codec.dequeueInputBuffer(100_000)
            if (inIdx >= 0) {
                codec.queueInputBuffer(
                    inIdx, 0, 0, totalFrames * 1_000_000L / fps,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }
            drain(true)
        } finally {
            try { codec.stop() } catch (e: Exception) { /* ignore */ }
            codec.release()
            if (muxerStarted) {
                try { muxer?.stop() } catch (e: Exception) { /* ignore */ }
            }
            muxer?.release()
            bitmap.recycle()
        }
        return outFile
    }

    /** ARGB int pixels -> NV12 (Y plane + interleaved UV), BT.601 limited range. */
    private fun argbToNv12(argb: IntArray, w: Int, h: Int, out: ByteArray) {
        var yIndex = 0
        val uvIndexStart = w * h
        for (y in 0 until h) {
            val rowOffset = y * w
            val uvRow = uvIndexStart + (y / 2) * w
            for (x in 0 until w) {
                val c = argb[rowOffset + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val yy = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                out[yIndex++] = yy.coerceIn(0, 255).toByte()
                if (y % 2 == 0 && x % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    val uvPos = uvRow + x
                    out[uvPos] = u.coerceIn(0, 255).toByte()
                    out[uvPos + 1] = v.coerceIn(0, 255).toByte()
                }
            }
        }
    }
}
