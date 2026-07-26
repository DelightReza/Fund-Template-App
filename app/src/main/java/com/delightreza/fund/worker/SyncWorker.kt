package com.delightreza.fund.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.delightreza.fund.MainActivity
import com.delightreza.fund.R
import com.delightreza.fund.data.AppDataStore
import com.delightreza.fund.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlin.math.abs

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): ListenableWorker.Result = withContext(Dispatchers.IO) {
        try {
            val dataStore = AppDataStore(applicationContext)
            val repository = Repository(dataStore)

            val oldData = repository.getCachedData()
            val oldCount = oldData?.transactions?.size ?: 0

            val newData = repository.fetchData() ?: return@withContext ListenableWorker.Result.retry()
            val newCount = newData.transactions.size

            if (oldData != null && newCount > oldCount) {
                val diff = newCount - oldCount
                val latestTx = newData.transactions.firstOrNull()
                
                val (title, contentText) = if (diff == 1 && latestTx != null) {
                    val symbol = if (latestTx.type == "credit") "+" else "-"
                    "New Transaction" to "${latestTx.whoOrBill}: $symbol${latestTx.amount.toInt()}"
                } else {
                    "Fund Update" to "$diff new transactions added."
                }

                sendNotification(1001, title, contentText)
            }

            val currentUser = dataStore.userFlow.firstOrNull()

            if (!currentUser.isNullOrEmpty()) {
                val balances = repository.calculateBalances(newData)
                val myBalance = balances[currentUser] ?: 0.0

                if (myBalance < 0) {
                    val debt = abs(myBalance).toInt()
                    
                    val messages = listOf(
                        "You owe $debt. Time to settle up! 💸",
                        "Friendly reminder: Your balance is -$debt. 📉",
                        "Hey! You still owe $debt. Pay up! 🚨",
                        "Your debt of $debt is waiting. Don't ignore it! ⏰",
                        "Balance check: -$debt. Let's fix that! 🛠️",
                        "Knock knock. It's your debt of $debt. 🚪",
                        "Stop scrolling and pay your $debt debt! 📱",
                        "Your wallet called. It wants $debt back. 👛",
                        "Debt alert: $debt outstanding. Handle it! ⚠️",
                        "Be a hero. Pay your $debt balance today. 🦸"
                    )

                    sendNotification(999, "⚠️ Balance Alert!", messages.random())
                }
            }

            ListenableWorker.Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            ListenableWorker.Result.retry()
        }
    }

    private fun sendNotification(notificationId: Int, title: String, message: String) {
        val channelId = "fund_sync_channel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Transaction Updates", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifies regarding account status and transactions" }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }
}
