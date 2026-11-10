package com.kaoyan.studybell.audio

import org.junit.Assert.*
import org.junit.Test

class VolumeSettingsTest {
    private class Store(var value: Int = 70) : VolumeStore {
        var writes = 0
        override fun read() = value
        override fun write(percent: Int) { value = percent; writes++ }
    }
    @Test fun restoresSavedVolume() {
        val store = Store(); val first = VolumeSettings(store)
        first.set(35)
        assertEquals(35, VolumeSettings(store).percent)
        assertEquals(0.35f, VolumeSettings(store).gain, 0.0001f)
    }
    @Test fun supportsMuteAndMaximum() {
        val s = VolumeSettings(Store())
        s.set(0); assertEquals(0f, s.gain, 0f)
        s.set(100); assertEquals(1f, s.gain, 0f)
    }
    @Test fun boundsOutOfRangeValues() {
        val s = VolumeSettings(Store(-99))
        assertEquals(0, s.percent)
        s.set(123); assertEquals(100, s.percent)
        s.set(-1); assertEquals(0, s.percent)
        assertEquals(100, VolumeSettings(Store(999)).percent)
    }
    @Test fun unchangedValueAvoidsDuplicateWrites() {
        val store = Store(50); val s = VolumeSettings(store)
        s.set(50); s.set(50); assertEquals(0, store.writes)
        s.set(25); s.set(25); assertEquals(1, store.writes)
    }
}
