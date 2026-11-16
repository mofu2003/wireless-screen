package app.opendisplay.android.sender

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Bundle
import android.view.Surface
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * MediaProjection -> encoder surface, mirrors the scrcpy capture shape.
 * The encoder writes Annex B units into the same framed stream the Mac
 * sender uses, so the Mac receiver needs no new decode path.
 */
class ScreenEncoder(
    private val context: Context,
    private val projection: MediaProjection,
    private val width: Int,
    private val height: Int,
    private val densityDpi: Int,
    private val sink: SenderSink,
) {
    private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private var inputSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private var parameterSets: ByteArray? = null

    fun start() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        format.setInteger(MediaFormat.KEY_BIT_RATE, 8_000_000)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        try {
            format.setInteger(MediaFormat.KEY_BITRATE_MODE,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
        } catch (_: Exception) {
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        inputSurface = surface
        codec.start()
        try {
            codec.setParameters(Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            })
        } catch (_: Exception) {
        }
        virtualDisplay = projection.createVirtualDisplay(
            app.opendisplay.android.ProductInfo.DISPLAY_NAME, width, height, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, null)
        running = true
        thread = Thread({ drain() }, "od-encoder").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        try {
            thread?.join(500)
        } catch (_: InterruptedException) {
        }
        thread = null
        virtualDisplay?.release()
        virtualDisplay = null
        try {
            codec.stop()
        } catch (_: Exception) {
        }
        try {
            codec.release()
        } catch (_: Exception) {
        }
        inputSurface?.release()
        inputSurface = null
    }

    private fun drain() {
        val info = MediaCodec.BufferInfo()
        while (running) {
            when (val index = codec.dequeueOutputBuffer(info, 10_000)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    parameterSets = formatParameterSets(codec.outputFormat)
                }
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                else -> if (index >= 0) {
                    val buffer = codec.getOutputBuffer(index)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val data = ByteArray(info.size)
                        buffer.get(data)
                        val annex = toAnnexB(data)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            parameterSets = annex
                        } else if (annex.isNotEmpty()) {
                            val key = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                            val sets = parameterSets
                            sink.video(if (key && sets != null) sets + annex else annex)
                        }
                    }
                    codec.releaseOutputBuffer(index, false)
                }
            }
        }
    }

    private fun formatParameterSets(format: MediaFormat): ByteArray? {
        val out = ByteArrayOutputStream()
        for (name in listOf("csd-0", "csd-1")) {
            val buffer = format.getByteBuffer(name) ?: continue
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            out.write(toAnnexB(data))
        }
        return out.toByteArray().takeIf { it.isNotEmpty() }
    }

    private fun toAnnexB(data: ByteArray): ByteArray =
        if (hasStartCode(data)) normalizeStartCodes(data) else fromAvcc(data)

    private fun hasStartCode(data: ByteArray): Boolean {
        var i = 0
        while (i + 2 < data.size) {
            if (data[i].toInt() == 0 && data[i + 1].toInt() == 0 &&
                data[i + 2].toInt() == 1) return true
            i++
        }
        return false
    }

    private fun normalizeStartCodes(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i + 2 < data.size) {
            if (data[i].toInt() == 0 && data[i + 1].toInt() == 0 &&
                data[i + 2].toInt() == 1) {
                val start = i + 3
                var end = start
                while (end + 2 < data.size &&
                    !(data[end].toInt() == 0 && data[end + 1].toInt() == 0 &&
                        data[end + 2].toInt() == 1)) end++
                val stop = if (end + 2 < data.size) end else data.size
                if (stop > start) {
                    out.write(byteArrayOf(0, 0, 0, 1))
                    out.write(data, start, stop - start)
                }
                i = if (stop < data.size) stop else data.size
            } else {
                i++
            }
        }
        return if (out.size() > 0) out.toByteArray() else data
    }

    private fun fromAvcc(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i + 4 <= data.size) {
            val length = ((data[i].toInt() and 0xFF) shl 24) or
                ((data[i + 1].toInt() and 0xFF) shl 16) or
                ((data[i + 2].toInt() and 0xFF) shl 8) or
                (data[i + 3].toInt() and 0xFF)
            if (length <= 0 || i + 4 + length > data.size) break
            out.write(byteArrayOf(0, 0, 0, 1))
            out.write(data, i + 4, length)
            i += 4 + length
        }
        return if (out.size() > 0) out.toByteArray() else data
    }
}
