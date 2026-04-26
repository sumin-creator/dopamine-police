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
    private var lastUpdatedAtMillis: Long? = null
    private var wasOverThreshold: Boolean = false
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

        accumulateOverThresholdIntervalUntil(now)
        lastUpdatedAtMillis = now
        wasOverThreshold = overThreshold

        if (!overThreshold) {
            if (hasTriggered) {
                resetForPackage(packageName)
            }
            return DetectionTimingResult(
                accumulatedMillis = accumulatedMillis,
                requiredMillis = normalizedRequiredMillis,
                readyToTrigger = false,
            )
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
    fun pause(now: Long? = null): DetectionTimingResult {
        if (now != null) {
            accumulateOverThresholdIntervalUntil(now)
        }
        lastUpdatedAtMillis = null
        wasOverThreshold = false
        return DetectionTimingResult(
            accumulatedMillis = accumulatedMillis,
            requiredMillis = 0L,
            readyToTrigger = false,
        )
    }

    @Synchronized
    fun reset() {
        currentPackageName = ""
        accumulatedMillis = 0L
        lastUpdatedAtMillis = null
        wasOverThreshold = false
        hasTriggered = false
    }

    private fun resetForPackage(packageName: String) {
        currentPackageName = packageName
        accumulatedMillis = 0L
        lastUpdatedAtMillis = null
        wasOverThreshold = false
        hasTriggered = false
    }

    private fun accumulateOverThresholdIntervalUntil(now: Long) {
        val lastUpdatedAt = lastUpdatedAtMillis
        if (lastUpdatedAt != null && wasOverThreshold) {
            val elapsedMillis = now - lastUpdatedAt
            if (elapsedMillis > 0L) {
                accumulatedMillis += elapsedMillis.coerceAtMost(maxCountableGapMillis)
            }
        }
    }

    private companion object {
        const val MAX_COUNTABLE_GAP_MILLIS = 10_000L
    }
}
