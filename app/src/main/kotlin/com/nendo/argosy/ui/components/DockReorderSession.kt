package com.nendo.argosy.ui.components

import androidx.compose.runtime.Immutable

@Immutable
data class DockReorderSession(
    val token: Long,
    val controller: Boolean,
    val visiblePackages: List<String>,
    val reorder: ListReorder<String>,
    val order: List<String>,
    val saving: Boolean = false
) {
    val heldPackage: String get() = reorder.backup[reorder.originIndex]
    val targetIndex: Int get() = visiblePackages.indexOf(reorder.backup[reorder.heldIndex])

    fun target(index: Int): DockReorderSession {
        val packageName = visiblePackages[index.coerceIn(0, visiblePackages.lastIndex)]
        val step = reorder.swapTo(reorder.backup.indexOf(packageName))
        return if (step.moved) copy(reorder = step.reorder, order = step.items) else this
    }

    fun move(delta: Int): DockReorderSession = target(targetIndex + delta)

    companion object {
        fun lift(
            token: Long,
            controller: Boolean,
            pins: List<String>,
            visible: List<String>,
            packageName: String
        ): DockReorderSession? {
            if (visible.size < 2 || packageName !in visible) return null
            val reorder = ListReorder.lift(pins, pins.indexOf(packageName)) ?: return null
            return DockReorderSession(token, controller, visible, reorder, pins)
        }
    }
}

internal fun dockDragTarget(
    current: Int,
    previousCenter: Float,
    center: Float,
    centers: List<Pair<Int, Float>>
): Int = if (center >= previousCenter) {
    centers.filter { (index, x) -> index > current && x <= center }
        .maxOfOrNull { it.first } ?: current
} else {
    centers.filter { (index, x) -> index < current && x >= center }
        .minOfOrNull { it.first } ?: current
}
