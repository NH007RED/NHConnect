package com.nhdeveloper.nhconnect.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nhdeveloper.nhconnect.NHConnectApp
import java.util.concurrent.TimeUnit

/** Retries unconfirmed messages with exponential backoff once the network is back. */
class PendingSendWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? NHConnectApp ?: return Result.success()
        if (!app.firebaseReady) return Result.success()
        return if (app.container.chatRepository.reconcilePending()) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "nh_pending_send"

        fun enqueue(context: Context) {
            val req = OneTimeWorkRequestBuilder<PendingSendWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
