package com.aeroscanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*

class NearestAirportFinder(private val context: Context) {

    sealed class LocationResult {
        data class Success(val airport: Airport, val distanceKm: Double) : LocationResult()
        data class Error(val message: String) : LocationResult()
    }

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    suspend fun findNearestAirport(): LocationResult = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            return@withContext LocationResult.Error("Location permission not granted.")
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager != null && !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
            !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        ) {
            return@withContext LocationResult.Error("Location services disabled. Enable GPS for automatic airport detection.")
        }

        try {
            var location: android.location.Location? = null

            try {
                location = suspendCancellableCoroutine { cont ->
                    fusedClient.lastLocation.addOnCompleteListener { task ->
                        cont.resume(task.result) {}
                    }
                }
            } catch (_: SecurityException) {
                return@withContext LocationResult.Error("Location permission denied.")
            }

            if (location == null) {
                location = withTimeout(10000L) {
                    suspendCancellableCoroutine { cont ->
                        val request = LocationRequest.Builder(10000L)
                            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                            .build()
                        val callback = object : LocationCallback() {
                            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                                cont.resume(result.lastLocation) {}
                            }
                        }
                        try {
                            fusedClient.requestLocationUpdates(
                                request, callback, Looper.getMainLooper()
                            )
                        } catch (_: SecurityException) {
                            cont.resume(null) {}
                        }
                        cont.invokeOnCancellation {
                            fusedClient.removeLocationUpdates(callback)
                        }
                    }
                }
                if (location == null) {
                    return@withContext LocationResult.Error(
                        "Could not determine location. Enter airport code manually."
                    )
                }
            }

            val airport = AirportDatabase.findNearest(location.latitude, location.longitude)
                ?: return@withContext LocationResult.Error("No airports found in database.")

            val dist = GeoTools.greatCircleDistanceKm(
                location.latitude, location.longitude,
                airport.latitude, airport.longitude,
            )

            return@withContext LocationResult.Success(airport, dist)
        } catch (e: TimeoutCancellationException) {
            return@withContext LocationResult.Error("Could not get GPS fix. Enter airport code manually.")
        } catch (e: Exception) {
            return@withContext LocationResult.Error(
                e.message ?: "Location unavailable."
            )
        }
    }

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
