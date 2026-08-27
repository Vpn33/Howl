package com.example.howl

class RecorderOutput : BaseOutput() {
    override val type = OutputType.RECORDER
    override val pulseDivider = 1
    override val pulseBatchSize = 1
    override val sendSilenceWhenMuted = false
    override var ready = true
    private val recordBuffer: CircularBuffer<Pulse> = CircularBuffer(4800)

    override fun sendPulses(pulses: List<OutputPulse>) {
        val recordState = Player.recordState.value
        if (recordState.recordMode && !recordState.recording)
            return
        recordBuffer.addAll(pulses.map { it.toPulse() }, overwrite = true)
        updateDuration()
    }

    fun updateDuration() {
        val duration = recordBuffer.size / HWL_PULSES_PER_SEC.toFloat()
        Player.setRecordState(Player.recordState.value.copy(duration = duration))
    }

    fun clear() {
        recordBuffer.clear()
        updateDuration()
    }

    fun resize(duration: Int, clear: Boolean = false) {
        recordBuffer.resize(duration * HWL_PULSES_PER_SEC, clear)
        updateDuration()
    }

    fun pulseCount(): Int {
        return recordBuffer.size
    }

    fun getPulses(): List<Pulse> {
        return recordBuffer.toList()
    }
}
