package com.nankai.flutter_nearby_connections

import android.app.NotificationManager
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
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*


/**
 * Plain bound service, not a foreground service: Nearby only runs while the host app
 * has a share/receive screen open, so nothing needs to survive in the background.
 * Without startForeground the app needs no FOREGROUND_SERVICE permissions and no
 * Play Console foreground-service declaration.
 */
class NearbyService : Service() {
    private val binder: IBinder = LocalBinder(this)
    private lateinit var callbackUtils: CallbackUtils
    private lateinit var connectionsClient: ConnectionsClient

    override fun onCreate() {
        super.onCreate()
        // Older versions of this fork posted a foreground notification on these
        // channels; remove them so they don't linger in the app's settings.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.deleteNotificationChannel("channel")
            manager.deleteNotificationChannel("nearby_sharing")
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
}

internal class LocalBinder(private val nearbyService: NearbyService) : Binder() {
    val service: NearbyService
        get() = nearbyService
}