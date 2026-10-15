package com.nendo.argosy.ui.screens.settings.components

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.R
import com.nendo.argosy.core.input.controllerIdOf
import com.nendo.argosy.core.input.isPhysicalGamepad
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.input.LocalGamepadInputHandler
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.SegmentedControl
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.primitives.segmentedInlineWidth
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun ControllerOrderModal(
    controller: ControllerSeatController,
    onDismiss: () -> Unit
) {
    val state by controller.state.collectAsState()
    val gamepadInputHandler = LocalGamepadInputHandler.current
    val currentOnDismiss = rememberUpdatedState(onDismiss)

    DisposableEffect(gamepadInputHandler, controller) {
        val listener: (KeyEvent) -> Boolean = { event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                val pressingPad = event.device
                    ?.takeIf { it.isPhysicalGamepad() }
                    ?.let(::controllerIdOf)
                val gamepadEvent = gamepadInputHandler?.mapKeyToEvent(event.keyCode)
                if (controller.onPadPress(gamepadEvent, pressingPad)) currentOnDismiss.value()
            }
            true
        }
        gamepadInputHandler?.setRawKeyEventListener(listener)
        onDispose { gamepadInputHandler?.setRawKeyEventListener(null) }
    }

    val seatLabels = SEAT_OPTIONS.map { port ->
        if (port == null) {
            stringResource(R.string.settings_controller_order_seat_none)
        } else {
            stringResource(R.string.settings_controller_order_slot_label, port + 1)
        }
    }
    val defaultName = stringResource(R.string.settings_controller_order_default_name)
    val listState = rememberLazyListState()

    LaunchedEffect(state.focusedIndex, state.rows.size) {
        if (state.rows.isNotEmpty()) listState.animateScrollToItemCentered(state.focusedIndex)
    }

    Modal(
        title = stringResource(R.string.settings_controller_order_title),
        subtitle = stringResource(R.string.settings_controller_order_subtitle),
        baseWidth = Dimens.modalWidthLg,
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.Y to stringResource(R.string.settings_controller_order_hint_clear_all)
        ),
        inlineFooterHints = true,
        onFooterHintClick = { button ->
            if (button == InputButton.Y) controller.clearAll()
        }
    ) {
        if (state.rows.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_controller_order_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                itemsIndexed(state.rows, key = { _, row -> row.controllerId }) { index, row ->
                    ControllerSeatRow(
                        name = row.name ?: defaultName,
                        seatLabels = seatLabels,
                        selectedIndex = SEAT_OPTIONS.indexOf(row.port),
                        isFocused = index == state.focusedIndex,
                        onFocus = { controller.focusRow(index) },
                        onSelectSeat = { option -> controller.setSeat(index, SEAT_OPTIONS[option]) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ControllerSeatRow(
    name: String,
    seatLabels: List<String>,
    selectedIndex: Int,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onSelectSeat: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.menuRowHeightLg)
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators.ListRow,
                shape = RoundedCornerShape(Dimens.radiusMd)
            )
            .clickableNoFocus(onClick = onFocus)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        SegmentedControl(
            options = seatLabels,
            selectedIndex = selectedIndex,
            onSelect = onSelectSeat,
            focused = isFocused,
            modifier = Modifier.width(segmentedInlineWidth(seatLabels))
        )
    }
}
