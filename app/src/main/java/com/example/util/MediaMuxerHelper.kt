package com.example.util

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

object MediaMuxerHelper {
    private const val TAG = "MediaMuxerHelper"
    private const val DEFAULT_BUFFER_SIZE = 1024 * 1024 // 1MB buffer

    /**
     * Muxes a video-only MP4 file and an audio-only M4A/AAC file into a single complete MP4.
     * Uses Android MediaMuxer for native direct stream copying (fast and lossless, no transcoding).
     */
    fun muxVideoAndAudio(videoFile: File, audioFile: File, outputFile: File): Boolean {
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            if (!videoFile.exists() || videoFile.length() == 0L) {
                Log.e(TAG, "Video file does not exist or is empty: ${videoFile.name}")
                return false
            }

            if (!audioFile.exists() || audioFile.length() == 0L) {
                Log.e(TAG, "Audio file does not exist or is empty: ${audioFile.name}")
                return false
            }

            videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }

            if (outputFile.exists()) outputFile.delete()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // 1. Select and configure video track
            var videoTrackIndex = -1
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) {
                    videoTrackIndex = muxer.addTrack(format)
                    videoExtractor.selectTrack(i)
                    Log.d(TAG, "Found video track: $mime at index $i")
                    break
                }
            }

            // 2. Select and configure audio track
            var audioTrackIndex = -1
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = muxer.addTrack(format)
                    audioExtractor.selectTrack(i)
                    Log.d(TAG, "Found audio track: $mime at index $i")
                    break
                }
            }

            if (videoTrackIndex < 0) {
                Log.e(TAG, "No video track found in ${videoFile.name}")
                return false
            }

            muxer.start()

            val buffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE)
            val bufferInfo = MediaCodec.BufferInfo()

            // 3. Copy Video Samples
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = videoExtractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(videoTrackIndex, buffer, bufferInfo)
                videoExtractor.advance()
            }

            // 4. Copy Audio Samples
            if (audioTrackIndex >= 0) {
                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = audioExtractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) break

                    bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                    bufferInfo.flags = audioExtractor.sampleFlags
                    muxer.writeSampleData(audioTrackIndex, buffer, bufferInfo)
                    audioExtractor.advance()
                }
            }

            Log.d(TAG, "Successfully muxed ${videoFile.name} + ${audioFile.name} -> ${outputFile.name} (${outputFile.length() / (1024 * 1024)}MB)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mux video and audio: ${e.message}", e)
            return false
        } finally {
            try {
                videoExtractor?.release()
                audioExtractor?.release()
                muxer?.stop()
                muxer?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Cleanup error: ${e.message}")
            }
        }
    }
}
