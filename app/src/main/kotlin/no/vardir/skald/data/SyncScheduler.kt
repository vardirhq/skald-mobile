package no.vardir.skald.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import no.vardir.skald.SkaldApp
import java.util.concurrent.TimeUnit

/**
 * Scheduling lives out here rather than inside the engine: on a phone, a
 * long-lived timer is a battery bug, and the platform already has a scheduler
 * that survives the process being killed.
 */
object SyncScheduler {

    private const val WORK_NAME = "skald-sync"

    fun enable(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as SkaldApp).repository
        val status = repository.syncStatus.value
        if (!status.configured || !status.enabled) return Result.success()

        return try {
            repository.syncNow()
            Result.success()
        } catch (_: Exception) {
            // The engine has already recorded why in the status the UI reads; a
            // retry here only matters for a transient failure.
            Result.retry()
        }
    }
}
