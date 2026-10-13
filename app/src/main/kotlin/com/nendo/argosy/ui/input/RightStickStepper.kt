package com.nendo.argosy.ui.input

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun rightStickStepFor(axisValue: Float): GamepadEvent? = when {
    axisValue < -RightStickStepper.DEADZONE -> GamepadEvent.RightStickUp
    axisValue > RightStickStepper.DEADZONE -> GamepadEvent.RightStickDown
    else -> null
}

/**
 * Steps from the right stick's vertical axis. Crossing [DEADZONE] emits one step at once, holding
 * past it emits a repeat every [STEP_INTERVAL_MS] at a constant rate, and returning inside it stops
 * the repeats.
 */
class RightStickStepper(
    private val scope: CoroutineScope,
    private val emit: (event: GamepadEvent, isRepeat: Boolean) -> Unit
) {
    private var held: GamepadEvent? = null
    private var repeatJob: Job? = null

    fun onAxis(value: Float) {
        val step = rightStickStepFor(value)
        if (step == held) return
        held = step
        repeatJob?.cancel()
        repeatJob = null
        if (step == null) return
        emit(step, false)
        repeatJob = scope.launch {
            while (true) {
                delay(STEP_INTERVAL_MS)
                emit(step, true)
            }
        }
    }

    fun release() {
        onAxis(0f)
    }

    companion object {
        const val DEADZONE = 0.5f
        const val STEP_INTERVAL_MS = 350L
    }
}
