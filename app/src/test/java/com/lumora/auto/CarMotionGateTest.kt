package com.lumora.auto

import org.junit.Assert.assertEquals
import org.junit.Test

class CarMotionGateTest {
    @Test
    fun `missing or invalid speed fails closed`() {
        assertEquals(CarMotionGate.State.UNKNOWN, CarMotionGate.stateForSpeed(null))
        assertEquals(CarMotionGate.State.UNKNOWN, CarMotionGate.stateForSpeed(Float.NaN))
    }

    @Test
    fun `near-zero speed is parked`() {
        assertEquals(CarMotionGate.State.PARKED, CarMotionGate.stateForSpeed(0f))
        assertEquals(CarMotionGate.State.PARKED, CarMotionGate.stateForSpeed(-0.5f))
    }

    @Test
    fun `speed above threshold is moving in either direction`() {
        assertEquals(CarMotionGate.State.MOVING, CarMotionGate.stateForSpeed(0.51f))
        assertEquals(CarMotionGate.State.MOVING, CarMotionGate.stateForSpeed(-0.51f))
    }
}
