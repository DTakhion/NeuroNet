/*
 * Copyright 2025 pyamsoft
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pyamsoft.tetherfi.core

import com.pyamsoft.tetherfi.core.BuildConfig
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import timber.log.Timber

/**
 * Infrastructure for tracing data flow D→C→B→A with unique session IDs.
 *
 * Features:
 * - UUID-based session tracing (consistent across entire data flow)
 * - Incremental counter for event ordering
 * - Verbose flag to control log noise (privacy + performance aware)
 * - DEBUG level for debug builds, INFO/WARN in release
 */
@Singleton
class TraceLoggingManager
@Inject
constructor() {
  // Session-wide UUID for correlating all logs in a single proxy session
  private val sessionId = UUID.randomUUID().toString()

  // Incremental counter for ordering events chronologically
  private val eventCounter = AtomicLong(0L)

  // Global flag to control verbose logging (set via BuildConfig or preferences)
  var isVerboseLoggingEnabled: Boolean = false

  // In-app observable stream for real-time UI display of traces (MVP)
  private val _traceStream: MutableSharedFlow<String> = MutableSharedFlow(extraBufferCapacity = 200)
  val traceStream: SharedFlow<String> = _traceStream.asSharedFlow()

  /**
   * Generates a unique trace ID combining session UUID + counter.
   * Format: "SESSION-0001" for easy reading in logs.
   */
  fun nextTraceId(): String {
    val counter = eventCounter.incrementAndGet()
    return "TRACE-$sessionId-${String.format("%05d", counter)}"
  }

  /**
   * Get the current session ID (same for entire app lifetime).
   */
  fun getSessionId(): String = sessionId

  /**
   * Get the next counter value without incrementing.
   */
  fun peekCounter(): Long = eventCounter.get() + 1

  /**
   * Logs a message with trace context.
   * Respects isVerboseLoggingEnabled to avoid log spam.
   *
   * Usage:
   *   traceLogger.logDebug("socket.accept()", "Client connected from ${socket.remoteAddress}")
   */
  fun logDebug(stage: String, message: String) {
    if (BuildConfig.DEBUG || isVerboseLoggingEnabled) {
      val traceId = nextTraceId()
      val formatted = "[$traceId] [$stage] $message"
      Timber.d(formatted)
      // Emit to in-app trace stream (best-effort)
      try {
        _traceStream.tryEmit(formatted)
      } catch (_: Throwable) {
        // ignore
      }
    }
  }

  /**
   * Logs a warning with trace context.
   * Always logged regardless of verbosity (for issues).
   */
  fun logWarn(stage: String, message: String, throwable: Throwable? = null) {
    val traceId = nextTraceId()
    val formatted = "[$traceId] [$stage] $message"
    if (throwable != null) {
      Timber.w(throwable, formatted)
    } else {
      Timber.w(formatted)
    }
    try {
      _traceStream.tryEmit(formatted)
    } catch (_: Throwable) {}
  }

  /**
   * Logs an error with trace context.
   * Always logged (critical issues).
   */
  fun logError(stage: String, message: String, throwable: Throwable? = null) {
    val traceId = nextTraceId()
    val formatted = "[$traceId] [$stage] $message"
    if (throwable != null) {
      Timber.e(throwable, formatted)
    } else {
      Timber.e(formatted)
    }
    try {
      _traceStream.tryEmit(formatted)
    } catch (_: Throwable) {}
  }

  /**
   * Logs the data flow in sequence: D→C→B→A.
   * Stage code:
   *  D = Device (client connection accepted)
   *  C = Relay/routing (data forwarding)
   *  B = Binding (network selection)
   *  A = Upstream (external connection)
   */
  fun logDataFlow(stage: Char, message: String, bytes: Long? = null) {
    val suffix = if (bytes != null) " (${bytes} bytes)" else ""
    logDebug("D-C-B-A:$stage", message + suffix)
  }

  /**
   * Helper to emit arbitrary lines to the trace stream (UI clients can call this).
   */
  fun emitUiLine(line: String) {
    try {
      _traceStream.tryEmit(line)
    } catch (_: Throwable) {}
  }
}

// Convenient extension function for easy use in coroutine scopes
val traceLoggingManager by lazy { TraceLoggingManager() }
