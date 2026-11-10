package com.kaoyan.studybell.audio

interface VolumeStore {
    fun read(): Int
    fun write(percent: Int)
}

/** Pure settings logic; the Android adapter uses private SharedPreferences. */
class VolumeSettings(private val store: VolumeStore) {
    var percent: Int = store.read().coerceIn(0, 100)
        private set
    val gain: Float get() = percent / 100f
    fun set(value: Int) {
        val normalized = value.coerceIn(0, 100)
        if (normalized != percent) {
            store.write(normalized)
            percent = normalized
        }
    }
}
