package com.petr.toll.session

/**
 * Counts push-ups from the proximity sensor, with the phone on the floor under Petr's face: one push-up is "far → near
 * → far". Pure: the push-up screen feeds it sensor readings with their timestamps.
 *
 * The Pixel's proximity sensor is effectively binary (near = a reading below its maximum range). To avoid counting a
 * hand wave or sensor flicker, a dip must stay near for [minNearMillis], and push-ups closer together than
 * [minRepMillis] are ignored.
 */
class PushupCounter(
    private val minNearMillis: Long = 150,
    private val minRepMillis: Long = 600,
) {
    var count = 0
        private set

    private var nearSince: Long? = null
    private var lastRepAt: Long? = null

    /**
     * [distanceCm] is the sensor value, [maxRangeCm] the sensor's maximum range, [atMillis] the event time.
     * Returns true when this reading completed a push-up.
     */
    fun onReading(distanceCm: Float, maxRangeCm: Float, atMillis: Long): Boolean {
        val near = distanceCm < maxRangeCm
        val since = nearSince
        if (near) {
            if (since == null) nearSince = atMillis
            return false
        }
        nearSince = null
        if (since == null || atMillis - since < minNearMillis) return false
        val last = lastRepAt
        if (last != null && atMillis - last < minRepMillis) return false
        lastRepAt = atMillis
        count++
        return true
    }

    fun reset() {
        count = 0
        nearSince = null
        lastRepAt = null
    }
}
