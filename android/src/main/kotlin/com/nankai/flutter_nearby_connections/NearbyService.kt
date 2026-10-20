package com.nankai.flutter_nearby_connections

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.media.session.PlaybackState.ACTION_STOP
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*


const val NOTIFICATION_ID = 101
// Importance of an existing channel can't be changed, so a new id is needed for the
// low-importance channel to reach users upgrading from the old default-importance one.
const val CHANNEL_ID = "nearby_sharing"

class NearbyService : Service() {
    private val binder: IBinder = LocalBinder(this)
    private lateinit var callbackUtils: CallbackUtils
    private lateinit var connectionsClient: ConnectionsClient

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, getNotification())
        }
    }

    // The service only lives while the host app is sharing or receiving; if the system
    // kills it, it must not come back on its own with nobody using it.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    fun initService(callbackUtils: CallbackUtils) {
        connectionsClient = Nearby.getConnectionsClient(this)
        this@NearbyService.callbackUtils = callbackUtils
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    fun sendStringPayload(endpointId: String, str: String) {
        Log.d(TAG, "sendStringPayload $endpointId -> $str")
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(str.toByteArray()))
    }

    fun startAdvertising(strategy: Strategy, deviceName: String) {
        Log.d(TAG, "startAdvertising()")
        connectionsClient.startAdvertising(
            deviceName, SERVICE_ID, callbackUtils.connectionLifecycleCallback,
            AdvertisingOptions.Builder().setStrategy(strategy).build()
        )
    }

    fun startDiscovery(strategy: Strategy) {
        Log.d(TAG, "startDiscovery()")
        connectionsClient.startDiscovery(
            SERVICE_ID, callbackUtils.endpointDiscoveryCallback,
            DiscoveryOptions.Builder().setStrategy(strategy).build()
        )
    }

    fun stopDiscovery() {
        Log.d(TAG, "stopDiscovery()")
        connectionsClient.stopDiscovery()
    }

    fun stopAdvertising() {
        Log.d(TAG, "stopAdvertising()")
        connectionsClient.stopAdvertising()
    }

    fun disconnect(endpointId: String) {
        Log.d(TAG, "disconnect $endpointId")
        connectionsClient.disconnectFromEndpoint(endpointId)
    }

    fun connect(endpointId: String, displayName: String) {
        Log.d(TAG, "connect $endpointId | $displayName")
        connectionsClient.requestConnection(
            displayName,
            endpointId,
            callbackUtils.connectionLifecycleCallback
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAdvertising()
        stopDiscovery()
        connectionsClient.stopAllEndpoints()
    }

    private fun getNotification(): Notification? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, appString("nearby_notification_channel", "Nearby sharing"),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.deleteNotificationChannel("channel") // pre-rename default-importance channel
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(appString("nearby_notification_title", "Nearby Service"))
            .setContentText(appString("nearby_notification_text", "Wi-Fi Direct"))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
        // Tapping the notification brings the user back to the app that is sharing.
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setContentIntent(
                PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE)
            )
        }
        return builder.build()
    }

    /** Host apps can localize the notification by defining these string resources. */
    private fun appString(name: String, fallback: String): String {
        val id = resources.getIdentifier(name, "string", packageName)
        return if (id != 0) getString(id) else fallback
    }
}

internal class LocalBinder(private val nearbyService: NearbyService) : Binder() {
    val service: NearbyService
        get() = nearbyService
}