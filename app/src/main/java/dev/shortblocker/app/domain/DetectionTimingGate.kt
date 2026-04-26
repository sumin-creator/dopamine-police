package dev.shortblocker.app.domain

internal data class DetectionTimingResult(
    val accumulatedMillis: Long,
    val requiredMillis: Long,
    val readyToTrigger: Boolean,
)

internal class DetectionTimingGate(
    private val maxCountableGapMillis: Long = MAX_COUNTABLE_GAP_MILLIS,
) {
    private var currentPackageName: String = ""
    private var accumulatedMillis: Long = 0L
    private var lastOverThresholdAtMillis: Long? = null
    private var hasTriggered: Boolean = false

    @Synchronized
    fun update(
        packageName: String,
        overThreshold: Boolean,
        now: Long,
        requiredMillis: Long,
    ): DetectionTimingResult {
        val normalizedRequiredMillis = requiredMillis.coerceAtLeast(0L)
        if (packageName.isBlank()) {
            reset()
            return DetectionTimingResult(
                accumulatedMillis = 0L,
                requiredMillis = normalizedRequiredMillis,
                readyToTrigger = false,
            )
        }

        if (currentPackageName != packageName) {
            resetForPackage(packageName)
        }

        if (!overThreshold) {
            lastOverThresholdAtMillis = null
            if (hasTriggered) {
                resetForPackage(packageName)
            }
            return DetectionTimingResult(
                accumulatedMillis = accumulatedMillis,
                requiredMillis = normalizedRequiredMillis,
                readyToTrigger = false,
            )
        }

        val lastOverThresholdAt = lastOverThresholdAtMillis
        if (lastOverThresholdAt == null) {
            lastOverThresholdAtMillis = now
        } else {
            val elapsedMillis = now - lastOverThresholdAt
            if (elapsedMillis > 0L) {
                accumulatedMillis += elapsedMillis.coerceAtMost(maxCountableGapMillis)
            }
            lastOverThresholdAtMillis = now
        }

        val readyToTrigger = !hasTriggered && accumulatedMillis >= normalizedRequiredMillis
        if (readyToTrigger) {
            hasTriggered = true
        }
        return DetectionTimingResult(
            accumulatedMillis = accumulatedMillis,
            requiredMillis = normalizedRequiredMillis,
            readyToTrigger = readyToTrigger,
        )
    }

    @Synchronized
    fun reset() {
        currentPackageName = ""
        accumulatedMillis = 0L
        lastOverThresholdAtMillis = null
        hasTriggered = false
    }

    private fun resetForPackage(packageName: String) {
        currentPackageName = packageName
        accumulatedMillis = 0L
        lastOverThresholdAtMillis = null
        hasTriggered = false
    }

    private companion object {
        const val MAX_COUNTABLE_GAP_MILLIS = 10_000L
    }
}
