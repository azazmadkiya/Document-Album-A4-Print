package com.example.utils

import kotlin.math.abs

class DocumentCaptureManager(
    private val requiredStableFrames: Int = 4,
    private val stabilityThreshold: Float = 0.03f,
    private val onAutoCapture: () -> Unit
) {
    private var lastEdges: FloatArray? = null
    private var stableFrameCount = 0
    private var isCaptured = false

    /**
     * Evaluates new detected edges [left, top, right, bottom].
     * Returns true if document is stable and aligned, triggering auto-capture once.
     */
    fun evaluateEdges(edges: FloatArray): Boolean {
        if (isCaptured) return false
        if (edges.size < 4) return false

        val left = edges[0]
        val top = edges[1]
        val right = edges[2]
        val bottom = edges[3]

        // Check alignment: document width and height must be within valid bounds
        val width = right - left
        val height = bottom - top
        val isAligned = width in 0.45f..0.98f && 
                        height in 0.35f..0.98f && 
                        left >= 0.01f && top >= 0.01f && 
                        right <= 0.99f && bottom <= 0.99f

        if (!isAligned) {
            stableFrameCount = 0
            lastEdges = null
            return false
        }

        val prev = lastEdges
        if (prev != null) {
            val delta = abs(left - prev[0]) + 
                        abs(top - prev[1]) + 
                        abs(right - prev[2]) + 
                        abs(bottom - prev[3])

            if (delta <= stabilityThreshold * 4) {
                stableFrameCount++
            } else {
                stableFrameCount = 1
            }
        } else {
            stableFrameCount = 1
        }

        lastEdges = edges

        if (stableFrameCount >= requiredStableFrames) {
            isCaptured = true
            onAutoCapture()
            return true
        }

        return false
    }

    /**
     * Resets capture state to allow another capture (e.g. for back side or retake).
     */
    fun reset() {
        lastEdges = null
        stableFrameCount = 0
        isCaptured = false
    }
}
