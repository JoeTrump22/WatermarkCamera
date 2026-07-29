package com.example.watermarkcamera

import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class LocationHelper(private val context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    suspend fun getCurrentLocation(): Location? {
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val isGpsEnabled = try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) { false }

        val isNetworkEnabled = try {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) { false }

        if (!isGpsEnabled && !isNetworkEnabled) {
            return getLastKnownLocation()
        }

        val provider = when {
            isNetworkEnabled -> LocationManager.NETWORK_PROVIDER
            isGpsEnabled -> LocationManager.GPS_PROVIDER
            else -> return getLastKnownLocation()
        }

        return requestLocation(provider)
    }

    private fun getLastKnownLocation(): Location? {
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        for (provider in providers) {
            try {
                @Suppress("MissingPermission")
                val lastKnown = locationManager.getLastKnownLocation(provider)
                if (lastKnown != null && lastKnown.time > System.currentTimeMillis() - 10 * 60 * 1000) {
                    return lastKnown
                }
            } catch (_: SecurityException) { }
        }
        return null
    }

    private suspend fun requestLocation(provider: String): Location? =
        suspendCoroutine { cont ->
            var resumed = false
            lateinit var listener: LocationListener

            listener = LocationListener { location ->
                if (!resumed) {
                    resumed = true
                    try { locationManager.removeUpdates(listener) } catch (_: Exception) {}
                    cont.resume(location)
                }
            }

            try {
                @Suppress("MissingPermission")
                locationManager.requestLocationUpdates(
                    provider,
                    0L,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )

                android.os.Handler(Looper.getMainLooper()).postDelayed({
                    if (!resumed) {
                        resumed = true
                        try { locationManager.removeUpdates(listener) } catch (_: Exception) {}
                        cont.resume(getLastKnownLocation())
                    }
                }, 10000)
            } catch (e: SecurityException) {
                cont.resume(null)
            } catch (e: IllegalArgumentException) {
                cont.resume(null)
            }
        }

    suspend fun getLocationName(location: Location): String? =
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context)
                val addresses: List<Address>? = geocoder.getFromLocation(
                    location.latitude,
                    location.longitude,
                    1
                )

                if (addresses != null && addresses.isNotEmpty()) {
                    val address = addresses[0]
                    buildString {
                        val featureName = address.featureName
                        val subLocality = address.subLocality
                        val locality = address.locality
                        val adminArea = address.adminArea

                        when {
                            !featureName.isNullOrEmpty() -> append(featureName)
                            !subLocality.isNullOrEmpty() -> append(subLocality)
                            !locality.isNullOrEmpty() -> append(locality)
                            !adminArea.isNullOrEmpty() -> append(adminArea)
                            else -> append(String.format("%.4f, %.4f", location.latitude, location.longitude))
                        }
                    }
                } else {
                    String.format("%.4f, %.4f", location.latitude, location.longitude)
                }
            } catch (e: Exception) {
                String.format("%.4f, %.4f", location.latitude, location.longitude)
            }
        }
}