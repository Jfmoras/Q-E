package com.moraswood.qande.assets

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moraswood.qande.MainActivity
import com.moraswood.qande.R
import com.moraswood.qande.data.QeDatabase
import com.moraswood.qande.logic.AssetLogic
import com.moraswood.qande.logic.AssetLogs
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Feature C: once a day, on the device, notify every overdue asset (item + worker). No network. */
object OverdueAlerts {
    const val CHANNEL = "asset-overdue"
    private const val WORK = "asset-overdue-daily"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Overdue tools & machines", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Daily reminder for assets not returned by their due-back date."
                },
            )
        }
    }

    /** Idempotent (KEEP): safe to call on every app start. */
    fun schedule(context: Context) {
        ensureChannel(context)
        val req = PeriodicWorkRequestBuilder<OverdueWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Posts one notification per overdue asset; returns how many were overdue. */
    suspend fun check(context: Context, today: LocalDate = LocalDate.now()): Int {
        val dao = QeDatabase.get(context).assets()
        val logs = AssetLogs(dao.assets(), dao.issues(), dao.returns(), dao.corrections(), dao.decisions())
        val overdue = AssetLogic.overdue(logs, today)
        if (overdue.isEmpty() || !canNotify(context)) return overdue.size
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val nm = NotificationManagerCompat.from(context)
        for (s in overdue) {
            val issue = s.open ?: continue
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_overdue)
                .setContentTitle("Overdue: ${s.asset.name}")
                .setContentText("Issued to ${issue.worker} · due back ${issue.dueBack}")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try { nm.notify(10_000 + issue.raw.id.toInt(), n) } catch (_: SecurityException) { return overdue.size }
        }
        return overdue.size
    }
}

class OverdueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching { OverdueAlerts.check(applicationContext) }.fold({ Result.success() }, { Result.retry() })
}
