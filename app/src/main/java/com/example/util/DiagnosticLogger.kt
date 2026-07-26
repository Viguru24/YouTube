package com.example.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DiagnosticStatus {
    HEALTHY,    // Green - Direct ExoPlayer MP4 playback
    FALLBACK,   // Yellow - Piped Stream API fallback mirror active
    ERROR       // Red - Extraction or playback failure
}

data class LogEntry(
    val timestamp: String,
    val level: String,
    val tag: String,
    val message: String,
    val details: String = ""
)

object DiagnosticLogger {
    private const val TAG = "DiagnosticLogger"
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _status = MutableStateFlow(DiagnosticStatus.HEALTHY)
    val status: StateFlow<DiagnosticStatus> = _status.asStateFlow()

    private val _statusMessage = MutableStateFlow("Direct Native MP4 Engine")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _logEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val logEntries: StateFlow<List<LogEntry>> = _logEntries.asStateFlow()

    private var logFile: File? = null

    fun init(context: Context) {
        try {
            logFile = File(context.cacheDir, "diagnostic_logs.txt")
            if (logFile?.exists() == false) {
                logFile?.createNewFile()
            }
            logInfo("SYSTEM", "DiagnosticLogger initialized successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init log file: ${e.message}")
        }
    }

    fun logInfo(tag: String, message: String, details: String = "") {
        addLog("INFO", tag, message, details)
        Log.i(tag, "$message $details")
    }

    fun logWarning(tag: String, message: String, details: String = "") {
        _status.value = DiagnosticStatus.FALLBACK
        _statusMessage.value = message
        addLog("WARN", tag, message, details)
        Log.w(tag, "$message $details")
    }

    fun logError(tag: String, message: String, details: String = "") {
        _status.value = DiagnosticStatus.ERROR
        _statusMessage.value = message
        addLog("ERROR", tag, message, details)
        Log.e(tag, "$message $details")
    }

    fun logHealthy(tag: String, message: String) {
        _status.value = DiagnosticStatus.HEALTHY
        _statusMessage.value = message
        addLog("INFO", tag, message)
        Log.i(tag, message)
    }

    private fun addLog(level: String, tag: String, message: String, details: String = "") {
        val timeStr = dateFormat.format(Date())
        val entry = LogEntry(timeStr, level, tag, message, details)

        val updated = ArrayList(_logEntries.value)
        updated.add(0, entry)
        if (updated.size > 100) updated.removeAt(updated.size - 1)
        _logEntries.value = updated

        // Append to local log file
        try {
            logFile?.appendText("[$timeStr] [$level] [$tag] $message ${if (details.isNotBlank()) "-> $details" else ""}\n")
        } catch (e: Exception) {
            // Ignore file write errors
        }
    }

    fun clearLogs() {
        _logEntries.value = emptyList()
        _status.value = DiagnosticStatus.HEALTHY
        _statusMessage.value = "Direct Native MP4 Engine"
        try {
            logFile?.writeText("")
        } catch (e: Exception) {
            // Ignore
        }
    }
}
