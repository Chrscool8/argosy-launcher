package com.nendo.argosy.ui.screens.settings.components

import android.view.InputDevice
import com.nendo.argosy.core.input.isPhysicalGamepad
import com.nendo.argosy.data.repository.InputConfigRepository
import com.nendo.argosy.libretro.ControllerPortResolver
import com.nendo.argosy.libretro.ControllerSeating
import com.nendo.argosy.ui.input.GamepadEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SeatRow(
    val controllerId: String,
    val name: String?,
    val port: Int?
)

data class ControllerSeatState(
    val rows: List<SeatRow> = emptyList(),
    val focusedIndex: Int = 0
)

val SEAT_OPTIONS: List<Int?> = listOf<Int?>(null) + (0 until ControllerSeating.PORT_COUNT)

/**
 * [rows] with the row at [index] moved to [port]. A row already on [port] takes the moved row's
 * previous seat, or none if it had none.
 */
fun reseatRows(rows: List<SeatRow>, index: Int, port: Int?): List<SeatRow> {
    val moved = rows.getOrNull(index) ?: return rows
    if (moved.port == port) return rows
    return rows.mapIndexed { i, row ->
        when {
            i == index -> row.copy(port = port)
            port != null && row.port == port -> row.copy(port = moved.port)
            else -> row
        }
    }
}

/**
 * Seat assignments for every connected physical pad, and the focused row of the seat modal.
 * Each change is written to the saved controller order as it is made.
 */
class ControllerSeatController(
    private val repository: InputConfigRepository,
    private val scope: CoroutineScope,
    private val onSeatsChanged: suspend () -> Unit = {}
) {
    private val _state = MutableStateFlow(ControllerSeatState())
    val state: StateFlow<ControllerSeatState> = _state.asStateFlow()
    private val writes = Mutex()

    fun open() {
        scope.launch {
            val rows = loadRows()
            _state.update { ControllerSeatState(rows = rows, focusedIndex = 0) }
        }
    }

    /**
     * Applies one press from the seat modal. Directions and clear-all act on the focused row from
     * any pad; any other button from a pad that has a row focuses that row. True when the press
     * closes the modal.
     */
    fun onPadPress(event: GamepadEvent?, pressingControllerId: String?): Boolean {
        when (event) {
            GamepadEvent.Up -> moveFocus(-1)
            GamepadEvent.Down -> moveFocus(1)
            GamepadEvent.Left -> cycleFocusedSeat(-1)
            GamepadEvent.Right -> cycleFocusedSeat(1)
            GamepadEvent.Back -> return true
            GamepadEvent.SecondaryAction -> clearAll()
            else -> pressingControllerId?.let(::focusController)
        }
        return false
    }

    fun moveFocus(delta: Int) {
        _state.update { state ->
            if (state.rows.isEmpty()) state
            else state.copy(focusedIndex = (state.focusedIndex + delta).mod(state.rows.size))
        }
    }

    fun focusRow(index: Int) {
        _state.update { state ->
            if (index !in state.rows.indices) state else state.copy(focusedIndex = index)
        }
    }

    fun focusController(controllerId: String) {
        val index = _state.value.rows.indexOfFirst { it.controllerId == controllerId }
        if (index >= 0) {
            focusRow(index)
            return
        }
        scope.launch {
            val rows = loadRows()
            _state.update { state ->
                val found = rows.indexOfFirst { it.controllerId == controllerId }
                state.copy(
                    rows = rows,
                    focusedIndex = if (found >= 0) found else state.focusedIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
                )
            }
        }
    }

    fun cycleFocusedSeat(direction: Int) {
        val state = _state.value
        val row = state.rows.getOrNull(state.focusedIndex) ?: return
        val next = (SEAT_OPTIONS.indexOf(row.port) + direction).mod(SEAT_OPTIONS.size)
        setSeat(state.focusedIndex, SEAT_OPTIONS[next])
    }

    fun setSeat(index: Int, port: Int?) {
        val row = _state.value.rows.getOrNull(index) ?: return
        if (row.port == port) {
            focusRow(index)
            return
        }
        _state.update { state ->
            state.copy(rows = reseatRows(state.rows, index, port), focusedIndex = index)
        }
        scope.launch {
            writes.withLock {
                repository.seatController(row.controllerId, row.name ?: row.controllerId, port)
                onSeatsChanged()
            }
        }
    }

    fun clearAll() {
        _state.update { state -> state.copy(rows = state.rows.map { it.copy(port = null) }) }
        scope.launch {
            writes.withLock {
                repository.clearControllerOrder()
                onSeatsChanged()
            }
        }
    }

    private suspend fun loadRows(): List<SeatRow> {
        val saved = repository.getControllerOrder().associate { it.controllerId to it.port }
        return connectedPhysicalPads().map { device ->
            val controllerId = ControllerPortResolver.getControllerId(device)
            SeatRow(controllerId = controllerId, name = device.name, port = saved[controllerId])
        }
    }

    private fun connectedPhysicalPads(): List<InputDevice> =
        InputDevice.getDeviceIds().toList()
            .mapNotNull { InputDevice.getDevice(it) }
            .filter { it.isPhysicalGamepad() }
            .distinctBy { ControllerPortResolver.getControllerId(it) }
}
