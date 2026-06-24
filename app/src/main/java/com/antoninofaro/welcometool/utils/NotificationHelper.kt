package com.antoninofaro.welcometool.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.antoninofaro.welcometool.R
import com.antoninofaro.welcometool.MainActivity
import com.antoninofaro.welcometool.data.model.OsmChangeset
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_ID = "osm_updates_channel"
        const val NOTIFICATION_ID = 1001
        const val NEW_MAPPER_CHANNEL = "NEW_MAPPER_CHANNEL"
        const val NEW_USER_NOTIFICATION_ID = 2001
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Main updates channel
            val name = context.getString(R.string.channel_updates_name)
            val descriptionText = context.getString(R.string.channel_updates_desc)
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)

            // New mappers channel
            val mapperName = context.getString(R.string.channel_new_mapper_name)
            val mapperDesc = context.getString(R.string.channel_new_mapper_desc)
            val mapperChannel = NotificationChannel(NEW_MAPPER_CHANNEL, mapperName, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = mapperDesc
            }
            notificationManager.createNotificationChannel(mapperChannel)
        }
    }

    fun sendTestNotification() {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.notif_test_title))
            .setContentText(context.getString(R.string.notif_test_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        try {
            if (Build.VERSION.SDK_INT >= 33) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Timber.w("Missing POST_NOTIFICATIONS permission for test notification")
                    return
                }
            }

            with(NotificationManagerCompat.from(context)) {
                notify(999, builder.build())
            }
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException while showing test notification")
        }
    }

    fun sendNewChangesetsNotification(newChangesets: List<OsmChangeset>) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val count = newChangesets.size
        val distinctUsers = newChangesets.map { it.user }.distinct()
        val bodyText = if (distinctUsers.size <= 3) {
            context.getString(R.string.notif_changeset_list, count, distinctUsers.joinToString(", "))
        } else {
            context.getString(R.string.notif_changeset_count, count, distinctUsers.size)
        }

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(context.getString(R.string.notif_changeset_big_title, count))
            .setSummaryText(context.getString(R.string.notif_changeset_summary, count))

        newChangesets.forEach { cs ->
            val comment = cs.tags?.get("comment") ?: context.getString(R.string.no_comment_placeholder)
            inboxStyle.addLine("${cs.user}: $comment")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(context.getString(R.string.notif_changeset_content_title))
            .setContentText(bodyText)
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            if (Build.VERSION.SDK_INT >= 33) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Timber.w("Missing POST_NOTIFICATIONS permission. Notification not sent.")
                    return
                }
            }

            with(NotificationManagerCompat.from(context)) {
                notify(NOTIFICATION_ID, builder.build())
            }
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException while showing notification")
        }
    }

    fun sendNewMapperNotification(newcomers: List<Pair<String, String>>) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.w("Missing POST_NOTIFICATIONS permission. Notification not sent.")
            return
        }

        val usernames = newcomers.map { it.first }
        val areaName = newcomers.first().second

        if (newcomers.size == 1) {
            val username = usernames.first()
            val builder = NotificationCompat.Builder(context, NEW_MAPPER_CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.notif_new_mapper_title))
                .setContentText(context.getString(R.string.notif_new_mapper_text, username, areaName))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
            NotificationManagerCompat.from(context).notify(NEW_USER_NOTIFICATION_ID, builder.build())
        } else {
            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle(context.getString(R.string.notif_new_mappers_plural_title, newcomers.size))
                .setSummaryText(areaName)
            newcomers.forEach { (username, _) ->
                inboxStyle.addLine(username)
            }

            val bodyText = context.getString(R.string.notif_new_mappers_plural_text, newcomers.size, usernames.joinToString(", "))
            val builder = NotificationCompat.Builder(context, NEW_MAPPER_CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.notif_new_mappers_content_title))
                .setContentText(bodyText)
                .setStyle(inboxStyle)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
            NotificationManagerCompat.from(context).notify(NEW_USER_NOTIFICATION_ID, builder.build())
        }
    }
}
