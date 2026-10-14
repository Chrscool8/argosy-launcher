package com.nendo.argosy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DualScreenManagerHolder {
    private val current = MutableStateFlow<DualScreenManager?>(null)

    val instanceFlow: StateFlow<DualScreenManager?> = current.asStateFlow()

    var instance: DualScreenManager?
        get() = current.value
        set(value) {
            current.value = value
        }
}
