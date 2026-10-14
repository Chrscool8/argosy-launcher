package com.nendo.argosy.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nendo.argosy.R
import com.nendo.argosy.data.emulator.LaunchProgressTracker
import com.nendo.argosy.data.launcher.SteamLaunchers
import com.nendo.argosy.domain.model.LaunchProgress
import com.nendo.argosy.domain.model.LaunchPromptOption
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.domain.model.SnapshotConflictChoice
import com.nendo.argosy.domain.model.SyncProgress
import com.nendo.argosy.ui.common.statusMessage
import com.nendo.argosy.ui.components.recap.LaunchRecapCard
import com.nendo.argosy.ui.components.recap.LaunchRecapCompactCard
import com.nendo.argosy.ui.components.recap.LaunchRecapCursor
import com.nendo.argosy.ui.components.recap.RecapCursor
import com.nendo.argosy.ui.components.recap.RecapSurface
import com.nendo.argosy.ui.components.recap.rememberRecapFullCardSurface
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LaunchProgressEntryPoint {
    fun launchProgressTracker(): LaunchProgressTracker
    fun launchRecapCursor(): LaunchRecapCursor
}

private fun launchProgressEntryPoint(context: Context): LaunchProgressEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, LaunchProgressEntryPoint::class.java)

/**
 * The launch in progress over the surface that starts launches. It holds the input stack while a
 * launch runs and ends the launch once that surface stops.
 */
@Composable
fun LaunchOverlay(
    modifier: Modifier = Modifier,
    viewModel: LaunchOverlayViewModel = hiltViewModel()
) {
    val progress by viewModel.progress.collectAsState()
    val promptFocus by viewModel.promptFocus.collectAsState()
    val recapCursor by viewModel.recapCursorState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.hostStopped()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    ModalInputEffect(active = progress != null, handler = viewModel.inputHandler)
    if (progress?.step is LaunchStep.Recap) {
        FooterHints(hints = emptyList())
    }

    LaunchOverlayContent(
        progress = progress,
        promptFocus = promptFocus,
        recapCursor = recapCursor,
        surface = RecapSurface.CONTROL,
        onAnswer = viewModel::answer,
        onCancel = { viewModel.cancel() },
        onRecapCurrent = viewModel::recapCurrent,
        modifier = modifier
    )
}

@Composable
fun LaunchOverlayMirror(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entryPoint = remember { launchProgressEntryPoint(context) }
    val tracker = remember(entryPoint) { entryPoint.launchProgressTracker() }
    val cursorStore = remember(entryPoint) { entryPoint.launchRecapCursor() }
    val progress by tracker.progress.collectAsState()
    val recapCursor by cursorStore.state.collectAsState()
    LaunchOverlayContent(
        progress = progress,
        promptFocus = NO_PROMPT_FOCUS,
        recapCursor = recapCursor,
        surface = RecapSurface.PRESENTATION,
        onAnswer = tracker::answer,
        onCancel = { tracker.cancel() },
        onRecapCurrent = { index ->
            val count = (tracker.progress.value?.step as? LaunchStep.Recap)?.recap?.unlocks?.size
            if (count != null) cursorStore.centered(index, count)
        },
        modifier = modifier
    )
}

/**
 * The blur the screen behind the launch overlay takes while a recap shows, for the surface's
 * content to apply alongside its other modal blurs.
 */
@Composable
fun launchRecapBackdropBlur(): Dp {
    val context = LocalContext.current
    val tracker = remember { launchProgressEntryPoint(context).launchProgressTracker() }
    val progress by tracker.progress.collectAsState()
    val blur by animateDpAsState(
        targetValue = if (progress?.step is LaunchStep.Recap) Motion.blurRadiusDrawer else 0.dp,
        animationSpec = tween(launchOverlayFadeMs()),
        label = "launchRecapBlur"
    )
    return blur
}

/**
 * The launch overlay's fade length, zero under reduced motion.
 */
@Composable
fun launchOverlayFadeMs(): Int =
    if (LocalMotionTier.current == MotionTier.Reduced) 0 else ComponentDefaults.LaunchOverlay.fadeMs

private const val NO_PROMPT_FOCUS = -1

@Composable
private fun LaunchOverlayContent(
    progress: LaunchProgress?,
    promptFocus: Int,
    recapCursor: RecapCursor,
    surface: RecapSurface,
    onAnswer: (LaunchPromptOption) -> Unit,
    onCancel: () -> Unit,
    onRecapCurrent: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var lastShown by remember { mutableStateOf<LaunchProgress?>(null) }
    SideEffect { progress?.let { lastShown = it } }

    val scrimAlpha = if (LocalLauncherTheme.current.isDarkTheme) {
        ComponentDefaults.LaunchOverlay.scrimAlphaDark
    } else {
        ComponentDefaults.LaunchOverlay.scrimAlphaLight
    }
    val scrimColor = MaterialTheme.colorScheme.background.copy(alpha = scrimAlpha)
    val fadeMs = launchOverlayFadeMs()

    AnimatedVisibility(
        visible = progress != null,
        enter = fadeIn(tween(fadeMs)),
        exit = fadeOut(tween(fadeMs)),
        modifier = modifier.fillMaxSize().focusProperties { canFocus = false }
    ) {
        val shown = progress ?: lastShown ?: return@AnimatedVisibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColor)
                .clickableNoFocus { },
            contentAlignment = Alignment.Center
        ) {
            when (val step = shown.step) {
                is LaunchStep.Prompt -> PromptContent(
                    gameTitle = shown.gameTitle,
                    prompt = step,
                    focusIndex = promptFocus,
                    onAnswer = onAnswer,
                    onCancel = onCancel
                )
                is LaunchStep.ChooseLauncher -> LauncherChoiceContent(
                    gameTitle = shown.gameTitle ?: stringResource(R.string.ui_sync_overlay_unknown_game),
                    options = step.options,
                    focusIndex = promptFocus,
                    onAnswer = onAnswer,
                    onCancel = onCancel
                )
                is LaunchStep.Recap -> if (rememberRecapFullCardSurface() == surface) {
                    LaunchRecapCard(
                        recap = step.recap,
                        cursor = recapCursor,
                        onStart = { onAnswer(LaunchPromptOption.START_GAME) },
                        onCancel = onCancel,
                        onCurrent = onRecapCurrent
                    )
                } else {
                    LaunchRecapCompactCard(
                        recap = step.recap,
                        onStart = { onAnswer(LaunchPromptOption.START_GAME) },
                        onCancel = onCancel
                    )
                }
                else -> StepContent(
                    gameTitle = shown.gameTitle,
                    step = step,
                    onCancel = onCancel
                )
            }
        }
    }
}

@Composable
private fun StepContent(
    gameTitle: String?,
    step: LaunchStep,
    onCancel: () -> Unit
) {
    val rotation by rememberInfiniteTransition(label = "launch_spin").animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            tween(ComponentDefaults.LaunchOverlay.spinMs, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "launch_rotation"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Sync,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(ComponentDefaults.LaunchOverlay.iconSizeDp.dp)
                .rotate(rotation)
        )
        Spacer(modifier = Modifier.height(Dimens.spacingLg))
        AnimatedContent(
            targetState = step.message(),
            transitionSpec = {
                fadeIn(tween(ComponentDefaults.LaunchOverlay.stepFadeInMs)) togetherWith
                    fadeOut(tween(ComponentDefaults.LaunchOverlay.stepFadeOutMs))
            },
            label = "launchStep"
        ) { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (gameTitle != null) {
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = gameTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (step is LaunchStep.DownloadingCore) {
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            ArgosyProgressBar(
                progress = step.fraction,
                modifier = Modifier.width(ComponentDefaults.LaunchOverlay.progressWidthDp.dp)
            )
        }
        if (step != LaunchStep.Launching) {
            Spacer(modifier = Modifier.height(Dimens.spacingLg))
            LaunchCancelButton(onClick = onCancel)
        }
    }
}

@Composable
internal fun LaunchCancelButton(onClick: () -> Unit) {
    ActionButton(
        label = stringResource(R.string.ui_launch_overlay_cancel),
        onClick = onClick,
        glyph = InputButton.B
    )
}

@Composable
private fun PromptContent(
    gameTitle: String?,
    prompt: LaunchStep.Prompt,
    focusIndex: Int,
    onAnswer: (LaunchPromptOption) -> Unit,
    onCancel: () -> Unit
) {
    when (val conflict = prompt.conflict) {
        is SyncProgress.HardcoreConflict -> HardcoreConflictContent(
            gameName = conflict.gameName,
            focusIndex = focusIndex,
            onKeepHardcore = { onAnswer(LaunchPromptOption.KEEP_HARDCORE) },
            onDowngradeToCasual = { onAnswer(LaunchPromptOption.DOWNGRADE_TO_CASUAL) },
            onKeepLocal = { onAnswer(LaunchPromptOption.SKIP_HARDCORE_SAVE) },
            onCancelLaunch = onCancel
        )
        is SyncProgress.LocalModified -> if (conflict.snapshotConflict) {
            SnapshotConflictContent(
                gameTitle = gameTitle ?: stringResource(R.string.ui_sync_overlay_unknown_game),
                focusIndex = focusIndex,
                onChoice = { choice ->
                    onAnswer(
                        when (choice) {
                            SnapshotConflictChoice.MINE -> LaunchPromptOption.KEEP_MINE
                            SnapshotConflictChoice.THEIRS -> LaunchPromptOption.TAKE_THEIRS
                            SnapshotConflictChoice.BRANCH -> LaunchPromptOption.BRANCH_LOCAL
                            SnapshotConflictChoice.REVERT -> LaunchPromptOption.REVERT_LOCAL
                        }
                    )
                },
                onCancelLaunch = onCancel
            )
        } else if (conflict.restoreFailed) {
            RestoreFailedContent(
                gameTitle = gameTitle ?: stringResource(R.string.ui_sync_overlay_unknown_game),
                focusIndex = focusIndex,
                onRestoreSynced = { onAnswer(LaunchPromptOption.RESTORE_SERVER) },
                onLaunchWithoutSync = { onAnswer(LaunchPromptOption.LAUNCH_WITHOUT_SYNC) },
                onCancelLaunch = onCancel
            )
        } else LocalModifiedContent(
            gameTitle = gameTitle ?: stringResource(R.string.ui_sync_overlay_unknown_game),
            focusIndex = focusIndex,
            onKeepLocal = { onAnswer(LaunchPromptOption.APPLY_LOCAL) },
            onRestoreSelected = { onAnswer(LaunchPromptOption.RESTORE_SERVER) },
            onCancelLaunch = onCancel
        )
        else -> Unit
    }
}

@Composable
private fun LaunchStep.message(): String = when (this) {
    LaunchStep.FinishingPrevious -> stringResource(R.string.ui_launch_overlay_step_finishing_previous)
    LaunchStep.RecapLoading -> stringResource(R.string.ui_launch_overlay_step_loading_recap)
    is LaunchStep.Recap -> ""
    is LaunchStep.Save -> progress.statusMessage()
    is LaunchStep.DownloadingCore -> stringResource(R.string.ui_launch_overlay_step_downloading_core)
    LaunchStep.PreparingSystemFiles -> stringResource(R.string.ui_launch_overlay_step_preparing_system_files)
    LaunchStep.PreparingUi -> stringResource(R.string.ui_launch_overlay_step_preparing_ui)
    LaunchStep.Launching -> stringResource(R.string.ui_launch_overlay_step_launching)
    is LaunchStep.Prompt -> ""
    is LaunchStep.ChooseLauncher -> ""
}

@Composable
private fun LauncherChoiceContent(
    gameTitle: String,
    options: List<LaunchPromptOption>,
    focusIndex: Int,
    onAnswer: (LaunchPromptOption) -> Unit,
    onCancel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = Dimens.spacingXl)
    ) {
        Text(
            text = stringResource(R.string.ui_launch_overlay_launcher_choice_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        Text(
            text = stringResource(R.string.ui_launch_overlay_launcher_choice_message, gameTitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimens.spacingLg))
        Column(modifier = Modifier.fillMaxWidth(0.85f)) {
            options.forEachIndexed { index, option ->
                val launcherName = SteamLaunchers.forPromptOption(option)?.displayName.orEmpty()
                ConflictOption(
                    label = launcherName,
                    subtitle = stringResource(R.string.ui_launch_overlay_launcher_choice_option_subtitle, launcherName),
                    isFocused = focusIndex == index,
                    onClick = { onAnswer(option) }
                )
            }
            CancelLaunchOption(isFocused = focusIndex == options.size, onClick = onCancel)
        }
    }
}
