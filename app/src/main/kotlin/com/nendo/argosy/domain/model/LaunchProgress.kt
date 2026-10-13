package com.nendo.argosy.domain.model

data class LaunchProgress(
    val gameTitle: String?,
    val step: LaunchStep
)

sealed interface LaunchStep {
    data object FinishingPrevious : LaunchStep
    data object RecapLoading : LaunchStep
    data class Recap(val recap: LaunchRecap) : LaunchStep
    data class Save(val progress: SyncProgress) : LaunchStep
    sealed interface Question : LaunchStep {
        val options: List<LaunchPromptOption>
    }
    data class Prompt(val conflict: SyncProgress, override val options: List<LaunchPromptOption>) : Question
    data class ChooseLauncher(override val options: List<LaunchPromptOption>) : Question
    data class DownloadingCore(val fraction: Float?) : LaunchStep
    data object PreparingSystemFiles : LaunchStep
    data object PreparingUi : LaunchStep
    data object Launching : LaunchStep
}

enum class LaunchPromptOption {
    KEEP_HARDCORE,
    DOWNGRADE_TO_CASUAL,
    SKIP_HARDCORE_SAVE,
    APPLY_LOCAL,
    RESTORE_SERVER,
    LAUNCH_WITHOUT_SYNC,
    KEEP_MINE,
    TAKE_THEIRS,
    BRANCH_LOCAL,
    REVERT_LOCAL,
    START_GAME,
    LAUNCH_WITH_GAMENATIVE,
    LAUNCH_WITH_DROIDDECK
}
