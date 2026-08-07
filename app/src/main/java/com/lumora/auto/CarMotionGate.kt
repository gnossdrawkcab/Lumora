package com.lumora.auto

import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.CarInfo
import androidx.car.app.hardware.info.Speed
import androidx.core.content.ContextCompat
import kotlin.math.abs

/**
 * Fail-closed vehicle-motion state for projected Android Auto.
 *
 * UNKNOWN never enables video. The host can withhold data, a permission can be denied, and older
 * hosts can lack Car API 3; none of those cases may be interpreted as "parked".
 */
class CarMotionGate(private val carContext: CarContext) {
    enum class State { UNKNOWN, PARKED, MOVING }

    private val listeners = mutableSetOf<(State) -> Unit>()
    private var carInfo: CarInfo? = null
    private var started = false
    var state: State = State.UNKNOWN
        private set

    private val speedListener = OnCarDataAvailableListener<Speed> { speed ->
        val raw = speed.rawSpeedMetersPerSecond
        val display = speed.displaySpeedMetersPerSecond
        val metersPerSecond = when {
            raw.status == CarValue.STATUS_SUCCESS -> raw.value
            display.status == CarValue.STATUS_SUCCESS -> display.value
            else -> null
        }
        update(stateForSpeed(metersPerSecond))
    }

    fun start() {
        if (started || carContext.carAppApiLevel < 3) return
        val info = runCatching {
            carContext.getCarService(CarHardwareManager::class.java).carInfo
        }.getOrNull() ?: return
        val registered = runCatching {
            info.addSpeedListener(ContextCompat.getMainExecutor(carContext), speedListener)
        }.isSuccess
        if (registered) {
            carInfo = info
            started = true
        }
    }

    fun stop() {
        carInfo?.let { info -> runCatching { info.removeSpeedListener(speedListener) } }
        carInfo = null
        started = false
        update(State.UNKNOWN)
    }

    fun addListener(listener: (State) -> Unit) {
        listeners += listener
        listener(state)
    }

    fun removeListener(listener: (State) -> Unit) {
        listeners -= listener
    }

    private fun update(newState: State) {
        if (state == newState) return
        state = newState
        listeners.toList().forEach { listener -> runCatching { listener(newState) } }
    }

    companion object {
        const val SPEED_PERMISSION = "com.google.android.gms.permission.CAR_SPEED"
        private const val PARKED_THRESHOLD_MPS = 0.5f

        internal fun stateForSpeed(metersPerSecond: Float?): State = when {
            metersPerSecond == null || !metersPerSecond.isFinite() -> State.UNKNOWN
            abs(metersPerSecond) <= PARKED_THRESHOLD_MPS -> State.PARKED
            else -> State.MOVING
        }
    }
}
