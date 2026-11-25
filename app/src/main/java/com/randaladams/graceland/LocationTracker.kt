package com.randaladams.graceland

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Streams device location updates using Google Play Services FusedLocationProviderClient
 * with an automatic fallback to Android's LocationManager.
 */
class LocationTracker(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun observeLocation(): Flow<Location> = callbackFlow {
        val hasPlayServices = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

        if (hasPlayServices) {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            fusedClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    trySend(location)
                }
            }

            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                .setMinUpdateIntervalMillis(1500L)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { trySend(it) }
                }
            }

            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            awaitClose {
                fusedClient.removeLocationUpdates(callback)
            }
        } else {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            ).filter { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }

            providers
                .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
                ?.let { trySend(it) }

            val listener = LocationListener { location ->
                trySend(location)
            }

            providers.forEach { provider ->
                runCatching {
                    locationManager.requestLocationUpdates(
                        provider,
                        2000L,
                        1f,
                        listener,
                        Looper.getMainLooper()
                    )
                }
            }

            awaitClose {
                locationManager.removeUpdates(listener)
            }
        }
    }

    suspend fun resolvePlaceLabel(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        return withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                            val first = addresses.firstOrNull()
                            val label = listOfNotNull(first?.locality, first?.adminArea ?: first?.countryName)
                                .distinct()
                                .joinToString(", ")
                                .ifBlank { null }
                            if (cont.isActive) cont.resume(label)
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val first = geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                    listOfNotNull(first?.locality, first?.adminArea ?: first?.countryName)
                        .distinct()
                        .joinToString(", ")
                        .ifBlank { null }
                }
            }.getOrNull()
        }
    }
}
