package com.example.waybill

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

class LocationHelper(private val context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentAddress(): String? = withContext(Dispatchers.IO) {
        val location = suspendCancellableCoroutine<Location?> { continuation ->
            val cts = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resume(null) }
            continuation.invokeOnCancellation { cts.cancel() }
        } ?: return@withContext null

        return@withContext try {
            val geocoder = Geocoder(context, Locale("ru", "RU"))
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val a = addresses[0]
                listOfNotNull(a.locality ?: a.subAdminArea, a.thoroughfare, a.subThoroughfare).joinToString(", ")
            } else "%.4f, %.4f".format(location.latitude, location.longitude)
        } catch (e: Exception) {
            "%.4f, %.4f".format(location.latitude, location.longitude)
        }
    }
}
