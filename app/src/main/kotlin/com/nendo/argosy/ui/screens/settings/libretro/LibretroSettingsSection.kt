package com.nendo.argosy.ui.screens.settings.libretro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nendo.argosy.core.emulator.LibretroSettingDef
import com.nendo.argosy.data.platform.PlatformWeightRegistry
import com.nendo.argosy.ui.components.ListSection
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

/**
 * Which libretro settings a screen renders. [VIDEO] is every group except saving; [SAVES] is
 * the saving group alone.
 */
enum class LibretroSettingsScope { VIDEO, SAVES }

private const val SAVING_SECTION = "saving"

data class LibretroVisibilityState(
    val platformSlug: String?,
    val canEnableBFI: Boolean,
    val scope: LibretroSettingsScope = LibretroSettingsScope.VIDEO
)

private val libretroSettingsLayout = SettingsLayout<LibretroSettingDef, LibretroVisibilityState>(
    allItems = LibretroSettingDef.ALL,
    isFocusable = { true },
    visibleWhen = { item, state ->
        val inScope = (item.section == SAVING_SECTION) == (state.scope == LibretroSettingsScope.SAVES)
        inScope && PlatformWeightRegistry.isSettingVisible(item, state.platformSlug, state.canEnableBFI)
    },
    sectionOf = { it.section }
)

@Composable
fun LibretroSettingsSection(
    accessor: LibretroSettingsAccessor,
    focusedIndex: Int,
    platformSlug: String? = null,
    canEnableBFI: Boolean = false,
    scope: LibretroSettingsScope = LibretroSettingsScope.VIDEO,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
    enablePicker: Boolean = true,
    pickerTokenFor: (LibretroSettingDef) -> Int = { 0 },
    trailingContent: @Composable (() -> Unit)? = null,
    trailingFocusCount: Int = 0,
    trailingItems: (androidx.compose.foundation.lazy.LazyListScope.() -> Unit)? = null
) {
    val isPerPlatform = platformSlug != null

    val visibilityState = remember(platformSlug, canEnableBFI, scope) {
        LibretroVisibilityState(platformSlug, canEnableBFI, scope)
    }

    val visibleSettings = remember(visibilityState) {
        libretroSettingsLayout.focusableItems(visibilityState)
    }

    val groupedBySection = remember(visibleSettings) {
        LibretroSettingDef.SECTION_ORDER.mapNotNull { section ->
            val items = visibleSettings.filter { it.section == section }
            if (items.isNotEmpty()) section to items else null
        }
    }

    val flatItems = remember(groupedBySection) {
        buildList {
            groupedBySection.forEachIndexed { index, (section, items) ->
                add(LibretroSettingsListItem.Header(section, isFirst = index == 0))
                items.forEach { add(LibretroSettingsListItem.Setting(it)) }
            }
        }
    }

    val trailingContentOffset = if (trailingContent != null) 1 else 0

    val sections = remember(flatItems, visibleSettings, trailingContentOffset, trailingFocusCount) {
        val settingSections = buildFlatItemSections(flatItems, visibleSettings)
        if (trailingFocusCount <= 0) {
            settingSections
        } else {
            settingSections + ListSection(
                listStartIndex = flatItems.size,
                listEndIndex = flatItems.size + trailingContentOffset + trailingFocusCount - 1,
                focusStartIndex = visibleSettings.size,
                focusEndIndex = visibleSettings.size + trailingFocusCount - 1
            )
        }
    }

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = focusedIndex,
        focusToListIndex = { focusIdx ->
            val setting = visibleSettings.getOrNull(focusIdx)
            if (setting != null) {
                flatItems.indexOfFirst { it is LibretroSettingsListItem.Setting && it.def == setting }
            } else {
                val trailingIdx = focusIdx - visibleSettings.size
                flatItems.size + trailingContentOffset + trailingIdx
            }
        },
        sections = sections
    )

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        items(flatItems, key = { it.key }) { item ->
            when (item) {
                is LibretroSettingsListItem.Header -> {
                    if (!item.isFirst) {
                        Spacer(modifier = Modifier.height(Dimens.spacingMd))
                    }
                    Text(
                        text = LibretroSettingDef.SECTIONS[item.section]
                            ?.let { stringResource(it) }
                            ?: item.section,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            start = Dimens.spacingSm,
                            top = Dimens.spacingXs,
                            bottom = Dimens.spacingXs
                        )
                    )
                }

                is LibretroSettingsListItem.Setting -> {
                    val settingFocusIndex = visibleSettings.indexOf(item.def)
                    LibretroSettingItem(
                        setting = item.def,
                        accessor = accessor,
                        isFocused = focusedIndex == settingFocusIndex,
                        isPerPlatform = isPerPlatform,
                        enablePicker = enablePicker,
                        pickerRequestToken = pickerTokenFor(item.def)
                    )
                }
            }
        }

        if (trailingContent != null) {
            item(key = "trailing_content") {
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                trailingContent()
            }
        }

        trailingItems?.invoke(this)
    }
}

private sealed class LibretroSettingsListItem(val key: String) {
    data class Header(val section: String, val isFirst: Boolean) : LibretroSettingsListItem("header_$section")
    data class Setting(val def: LibretroSettingDef) : LibretroSettingsListItem(def.key)
}

private fun buildFlatItemSections(
    flatItems: List<LibretroSettingsListItem>,
    visibleSettings: List<LibretroSettingDef>
): List<ListSection> {
    val sectionNames = LibretroSettingDef.SECTION_ORDER.filter { section ->
        visibleSettings.any { it.section == section }
    }

    return sectionNames.mapNotNull { sectionName ->
        val sectionSettings = visibleSettings.filter { it.section == sectionName }
        if (sectionSettings.isEmpty()) return@mapNotNull null

        val firstFlatIdx = flatItems.indexOfFirst {
            it is LibretroSettingsListItem.Header && it.section == sectionName
        }
        val lastFlatIdx = flatItems.indexOfLast {
            it is LibretroSettingsListItem.Setting && it.def.section == sectionName
        }
        if (firstFlatIdx < 0 || lastFlatIdx < 0) return@mapNotNull null

        ListSection(
            listStartIndex = firstFlatIdx,
            listEndIndex = lastFlatIdx,
            focusStartIndex = visibleSettings.indexOf(sectionSettings.first()),
            focusEndIndex = visibleSettings.indexOf(sectionSettings.last())
        )
    }
}

fun libretroSettingsMaxFocusIndex(
    platformSlug: String?,
    canEnableBFI: Boolean,
    scope: LibretroSettingsScope = LibretroSettingsScope.VIDEO
): Int =
    libretroSettingsLayout.maxFocusIndex(LibretroVisibilityState(platformSlug, canEnableBFI, scope))

/**
 * Resolves a focus index to a libretro setting. [scope] must match the value the matching
 * [LibretroSettingsSection] renders with, or indices resolve against the other screen's rows.
 */
fun libretroSettingsItemAtFocusIndex(
    index: Int,
    platformSlug: String?,
    canEnableBFI: Boolean,
    scope: LibretroSettingsScope = LibretroSettingsScope.VIDEO
): LibretroSettingDef? =
    libretroSettingsLayout.itemAtFocusIndex(
        index,
        LibretroVisibilityState(platformSlug, canEnableBFI, scope)
    )
