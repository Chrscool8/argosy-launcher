package com.nendo.argosy.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import com.nendo.argosy.ui.theme.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Stable
internal class DockDragReorderState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val edge: Float,
    private val isRtl: Boolean,
    private val onStart: (String) -> Long?,
    private val onTarget: (Long, Int) -> Unit,
    private val onSave: (Long) -> Unit,
    private val onCancel: (Long) -> Unit
) {
    var draggingPackage by mutableStateOf<String?>(null)
        private set
    var accessiblePackage by mutableStateOf<String?>(null)
        private set
    private var center by mutableFloatStateOf(0f)
    private var token: Long? = null
    private var target = 0
    private var lastCenter = 0f
    private var scrollJob: Job? = null
    private var speed = 0f

    fun start(position: Offset) {
        if (token != null) return
        val layout = listState.layoutInfo
        val x = if (isRtl) layout.viewportStartOffset + layout.viewportEndOffset - position.x else position.x
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            x >= it.offset && x < it.offset + it.size
        } ?: return
        val packageName = item.key as? String ?: return
        val started = onStart(packageName) ?: return
        token = started
        draggingPackage = packageName
        target = item.index
        center = item.offset + item.size / 2f
        lastCenter = center
    }

    fun startAccessible(packageName: String): Boolean {
        if (token != null) return false
        token = onStart(packageName) ?: return false
        accessiblePackage = packageName
        return true
    }

    fun drag(delta: Float) {
        if (token == null) return
        center += if (isRtl) -delta else delta
        resolveTarget()
        updateScroll()
    }

    fun reconcile(session: DockReorderSession?) {
        if (token != null && session?.token != token) finish(false)
    }

    fun finish(save: Boolean) {
        scrollJob?.cancel()
        scrollJob = null
        val finished = token ?: return
        token = null
        draggingPackage = null
        accessiblePackage = null
        if (save) onSave(finished) else onCancel(finished)
    }

    fun translation(packageName: String): Float {
        if (packageName != draggingPackage) return 0f
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == packageName } ?: return 0f
        val offset = center - item.offset - item.size / 2f
        return if (isRtl) -offset else offset
    }

    private fun resolveTarget(scrolled: Float = 0f) {
        val active = token ?: return
        val centers = listState.layoutInfo.visibleItemsInfo.map { it.index to (it.offset + it.size / 2f) }
        val next = dockDragTarget(target, lastCenter - scrolled, center, centers)
        if (next != target) {
            target = next
            listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            onTarget(active, next)
        }
        lastCenter = center
    }

    private fun updateScroll() {
        val layout = listState.layoutInfo
        speed = when {
            center < layout.viewportStartOffset + edge ->
                -(layout.viewportStartOffset + edge - center) / edge * MAX_SCROLL_STEP
            center > layout.viewportEndOffset - edge ->
                (center - layout.viewportEndOffset + edge) / edge * MAX_SCROLL_STEP
            else -> 0f
        }.coerceIn(-MAX_SCROLL_STEP, MAX_SCROLL_STEP)
        if (speed == 0f) {
            scrollJob?.cancel()
            scrollJob = null
        } else if (scrollJob?.isActive != true) {
            scrollJob = scope.launch {
                while (isActive && token != null && speed != 0f) {
                    val consumed = listState.scrollBy(speed)
                    if (consumed == 0f) break
                    resolveTarget(consumed)
                    delay(FRAME_MILLIS)
                }
            }
        }
    }

    private companion object {
        const val MAX_SCROLL_STEP = 12f
        const val FRAME_MILLIS = 16L
    }
}

@Composable
internal fun rememberDockDragReorderState(
    listState: LazyListState,
    edge: Float,
    onStart: (String) -> Long?,
    onTarget: (Long, Int) -> Unit,
    onSave: (Long) -> Unit,
    onCancel: (Long) -> Unit
): DockDragReorderState {
    val scope = rememberCoroutineScope()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val start by rememberUpdatedState(onStart)
    val target by rememberUpdatedState(onTarget)
    val save by rememberUpdatedState(onSave)
    val cancel by rememberUpdatedState(onCancel)
    val state = remember(listState, scope, edge, isRtl) {
        DockDragReorderState(listState, scope, edge, isRtl, { start(it) }, { token, index -> target(token, index) },
            { save(it) }, { cancel(it) })
    }
    DisposableEffect(state) { onDispose { state.finish(false) } }
    return state
}

internal fun Modifier.dockDragContainer(state: DockDragReorderState): Modifier = pointerInput(state) {
    detectDragGesturesAfterLongPress(
        onDragStart = state::start,
        onDrag = { change, amount ->
            if (state.draggingPackage != null) {
                change.consume()
                state.drag(amount.x)
            }
        },
        onDragEnd = { if (state.draggingPackage != null) state.finish(true) },
        onDragCancel = { if (state.draggingPackage != null) state.finish(false) }
    )
}

@Composable
internal fun Modifier.dockDraggedItem(
    state: DockDragReorderState,
    packageName: String,
    lifted: Boolean
): Modifier {
    val dragging = state.draggingPackage == packageName
    val translation by animateFloatAsState(
        targetValue = state.translation(packageName),
        animationSpec = if (dragging) snap() else tween(Motion.durationSlide, easing = Motion.argosyEase),
        label = "dock-drop"
    )
    return zIndex(if (lifted || dragging || translation != 0f) 1f else 0f)
        .graphicsLayer { translationX = if (dragging) state.translation(packageName) else translation }
}
