package dev.shortblocker.app.domain

internal data class DetectionTimingResult(
    val accumulatedMillis: Long,
    val requiredMillis: Long,
    val readyToTrigger: Boolean,
    val addedMillis: Long = 0L,
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

        val addedMillis = accumulateOverThresholdIntervalUntil(now)
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
                addedMillis = addedMillis,
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
            addedMillis = addedMillis,
        )
    }

    @Synchronized
    fun pause(now: Long? = null): DetectionTimingResult {
        val addedMillis = if (now != null) {
            accumulateOverThresholdIntervalUntil(now)
        } else {
            0L
        }
        lastUpdatedAtMillis = null
        wasOverThreshold = false
        return DetectionTimingResult(
            accumulatedMillis = accumulatedMillis,
            requiredMillis = 0L,
            readyToTrigger = false,
            addedMillis = addedMillis,
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

    private fun accumulateOverThresholdIntervalUntil(now: Long): Long {
        val lastUpdatedAt = lastUpdatedAtMillis
        if (lastUpdatedAt != null && wasOverThreshold) {
            val elapsedMillis = now - lastUpdatedAt
            if (elapsedMillis > 0L) {
                val addedMillis = elapsedMillis.coerceAtMost(maxCountableGapMillis)
                accumulatedMillis += addedMillis
                return addedMillis
            }
        }
        return 0L
    }

    private companion object {
        const val MAX_COUNTABLE_GAP_MILLIS = 10_000L
    }
}
