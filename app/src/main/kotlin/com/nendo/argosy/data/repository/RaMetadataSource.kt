package com.nendo.argosy.data.repository

import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.ra.RAWebClient
import com.nendo.argosy.data.remote.ra.RAWebCompletionEntry
import com.nendo.argosy.data.remote.ra.RAWebCredentials
import com.nendo.argosy.data.remote.ra.RAWebEarnedAchievement
import com.nendo.argosy.data.remote.ra.RAWebGameProgress
import com.nendo.argosy.data.remote.ra.RAWebResult
import com.nendo.argosy.data.remote.ra.RAWebUserSummary
import com.nendo.argosy.data.remote.romm.RomMAchievementService
import com.nendo.argosy.data.remote.romm.RomMEarnedAchievement
import com.nendo.argosy.data.remote.romm.RomMRAGameProgression
import com.nendo.argosy.data.remote.romm.RomMRaUserSummary
import com.nendo.argosy.domain.model.SnapshotUnlock
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where a recap's unlocks come from. [Dated] carries per-achievement unlock dates keyed by badge id;
 * [Snapshot] carries a session's launch-to-end snapshot difference, every unlock dated [Snapshot.at].
 */
sealed interface RaUnlockEvidence {
    data class Dated(val earned: List<RomMEarnedAchievement>) : RaUnlockEvidence
    data class Snapshot(val unlocks: List<SnapshotUnlock>, val at: Instant) : RaUnlockEvidence
}

/**
 * The one place that picks the source of RetroAchievements metadata for recaps. Unlocks try the
 * user's web API key first, then RomM's RA progression, then a session's stored unlock snapshots;
 * rich presence and awards try the web API key, then RomM. A failing source hands the call to the
 * next one. Reads the RA username and web API key once per call. Never calls `startsession` or
 * `ping` and never uses the RA password.
 */
@Singleton
class RaMetadataSource @Inject constructor(
    private val webClient: RAWebClient,
    private val achievementService: RomMAchievementService,
    private val raRepository: RetroAchievementsRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val keyHealth: RAWebKeyHealth
) {

    suspend fun userSummary(): RomMRaUserSummary? {
        webCredentials()?.let { credentials ->
            when (val result = webClient.userSummary(credentials).recordedFor(credentials)) {
                is RAWebResult.Success -> return result.value.toRomMShape()
                else -> Logger.debug(TAG, "Web user summary unavailable (${result.describe()}), asking RomM")
            }
        }
        return achievementService.fetchRaUserSummary()
    }

    /**
     * The evidence for [raGameId]'s unlocks up to [through]. [snapshotUnlocks] is the session's
     * stored snapshot difference, used only when neither dated source can answer; without one the
     * call ends on whatever RomM holds.
     */
    suspend fun unlockEvidence(
        raGameId: Long,
        through: Instant,
        snapshotUnlocks: List<SnapshotUnlock>? = null
    ): RaUnlockEvidence {
        webCredentials()?.let { credentials ->
            when (val result = webClient.gameProgress(credentials, raGameId, through).recordedFor(credentials)) {
                is RAWebResult.Success -> return RaUnlockEvidence.Dated(result.value.toEarnedAchievements())
                else -> Logger.debug(TAG, "Web progress for $raGameId unavailable (${result.describe()}), asking RomM")
            }
        }
        refreshRomMIfStale(through)
        val romMCurrent = !needsRefresh(achievementService.progressionRefreshedAt(), through)
        if (romMCurrent && achievementService.hasProgressionFor(raGameId)) {
            return RaUnlockEvidence.Dated(achievementService.getEarnedAchievements(raGameId))
        }
        if (snapshotUnlocks != null) return RaUnlockEvidence.Snapshot(snapshotUnlocks, through)
        return RaUnlockEvidence.Dated(achievementService.getEarnedAchievements(raGameId))
    }

    /**
     * Per-game award rows (highest award kind and date) through [through]: from the web API key
     * when set and answering, else RomM's progression.
     */
    suspend fun awardProgression(through: Instant): List<RomMRAGameProgression> {
        webCredentials()?.let { credentials ->
            when (val result = webClient.completionProgress(credentials).recordedFor(credentials)) {
                is RAWebResult.Success -> return result.value.map { it.toRomMShape() }
                else -> Logger.debug(TAG, "Web completion progress unavailable (${result.describe()}), asking RomM")
            }
        }
        return romMProgression(through)
    }

    /**
     * Every unlock earned in [from, to] from the web API key, or null when no key is set or the
     * call fails.
     */
    suspend fun webUnlocksBetween(from: Instant, to: Instant): List<RAWebEarnedAchievement>? {
        val credentials = webCredentials() ?: return null
        return when (val result = webClient.earnedBetween(credentials, from, to).recordedFor(credentials)) {
            is RAWebResult.Success -> result.value
            else -> {
                Logger.debug(TAG, "Web unlocks between $from and $to unavailable (${result.describe()}), asking RomM")
                null
            }
        }
    }

    /**
     * RomM's progression, refreshed first when the cache predates [through].
     */
    suspend fun romMProgression(through: Instant): List<RomMRAGameProgression> {
        refreshRomMIfStale(through)
        return achievementService.getProgression()
    }

    /**
     * Fetches [raGameId]'s web progress, fresh through [through], into the client's cache. Does
     * nothing without a web API key.
     */
    suspend fun warmGameProgress(raGameId: Long, through: Instant) {
        val credentials = webCredentials() ?: return
        val result = webClient.gameProgress(credentials, raGameId, through).recordedFor(credentials)
        if (result !is RAWebResult.Success) Logger.debug(TAG, "Web progress warm for $raGameId skipped (${result.describe()})")
    }

    /**
     * True when neither the web API key nor RomM's cached progression can answer for [raGameId],
     * the only case a session takes unlock snapshots.
     */
    suspend fun needsUnlockSnapshots(raGameId: Long): Boolean =
        webCredentials() == null && !achievementService.hasProgressionFor(raGameId)

    suspend fun unlockSnapshot(raGameId: Long): RetroAchievementsRepository.GameUnlocks? =
        raRepository.fetchUnlocksSnapshot(raGameId)

    private suspend fun refreshRomMIfStale(through: Instant) {
        if (needsRefresh(achievementService.progressionRefreshedAt(), through)) {
            achievementService.refreshRAProgressionIfNeeded(force = true)
        }
    }

    private suspend fun webCredentials(): RAWebCredentials? {
        val prefs = preferencesRepository.userPreferences.first()
        val username = prefs.raUsername?.takeIf { it.isNotBlank() } ?: return null
        val apiKey = prefs.raWebApiKey?.takeIf { it.isNotBlank() } ?: return null
        return RAWebCredentials(username, apiKey)
    }

    private fun <T> RAWebResult<T>.recordedFor(credentials: RAWebCredentials): RAWebResult<T> {
        keyHealth.record(credentials.apiKey, this)
        return this
    }

    companion object {
        private const val TAG = "RaMetadataSource"

        fun needsRefresh(refreshedAt: Instant?, through: Instant): Boolean =
            refreshedAt == null || refreshedAt.isBefore(through)

        fun RAWebUserSummary.toRomMShape(): RomMRaUserSummary = RomMRaUserSummary(
            richPresenceMsg = richPresenceMsg,
            richPresenceMsgDate = richPresenceMsgDate,
            lastGameId = lastGameId
        )

        /**
         * The earned achievements of a web progress answer, keyed by badge name as RomM keys them.
         * Achievements earned in neither mode are left out.
         */
        fun RAWebGameProgress.toEarnedAchievements(): List<RomMEarnedAchievement> =
            achievements.values.mapNotNull { achievement ->
                val badge = achievement.badgeName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (achievement.dateEarned == null && achievement.dateEarnedHardcore == null) return@mapNotNull null
                RomMEarnedAchievement(
                    id = badge,
                    date = achievement.dateEarned,
                    dateHardcore = achievement.dateEarnedHardcore
                )
            }

        fun RAWebCompletionEntry.toRomMShape(): RomMRAGameProgression = RomMRAGameProgression(
            romRaId = gameId,
            maxPossible = maxPossible,
            numAwarded = numAwarded,
            numAwardedHardcore = numAwardedHardcore,
            mostRecentAwardedDate = mostRecentAwardedDate,
            highestAwardKind = highestAwardKind,
            highestAwardDate = highestAwardDate
        )

        private fun RAWebResult<*>.describe(): String = when (this) {
            is RAWebResult.Success -> "ok"
            is RAWebResult.Rejected -> "key rejected"
            is RAWebResult.Failed -> reason
        }
    }
}
