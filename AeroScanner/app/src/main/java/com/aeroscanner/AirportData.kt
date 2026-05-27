package com.aeroscanner

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

data class Airport(
    val code: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val name: String? = null,
    val country: String? = null,
)

object AirportDatabase {
    private var airports: Map<String, Airport> = emptyMap()

    val isLoaded: Boolean get() = airports.isNotEmpty()

    suspend fun load(context: Context) {
        if (airports.isNotEmpty()) return
        withContext(Dispatchers.IO) {
            try {
                val json = context.assets.open("airports_cache.json")
                    .bufferedReader()
                    .use { it.readText() }
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val root: Map<String, Any> = Gson().fromJson(json, type)
                val airportsMap = root["airports"] as? Map<String, Any> ?: emptyMap()

                val parsed = mutableMapOf<String, Airport>()
                for ((code, value) in airportsMap) {
                    @Suppress("UNCHECKED_CAST")
                    val obj = value as? Map<String, Any> ?: continue
                    val lat = (obj["latitude"] as? Number)?.toDouble() ?: continue
                    val lng = (obj["longitude"] as? Number)?.toDouble() ?: continue
                    val city = (obj["city"] as? String) ?: code
                    parsed[code] = Airport(
                        code = code,
                        city = city,
                        latitude = lat,
                        longitude = lng,
                        name = obj["name"] as? String,
                        country = obj["country"] as? String,
                    )
                }
                airports = parsed
            } catch (e: IOException) {
                throw RuntimeException("Airport database not found. Reinstall app.", e)
            } catch (e: com.google.gson.JsonSyntaxException) {
                throw RuntimeException("Airport database is corrupt. Reinstall app.", e)
            }
        }
    }

    fun get(code: String): Airport? = airports[code.uppercase()]

    fun getAll(): Collection<Airport> = airports.values

    fun findNearest(lat: Double, lon: Double): Airport? {
        var best: Airport? = null
        var bestDist = Double.MAX_VALUE
        for (airport in airports.values) {
            val dist = GeoTools.greatCircleDistanceKm(lat, lon, airport.latitude, airport.longitude)
            if (dist < bestDist) {
                bestDist = dist
                best = airport
            }
        }
        return best
    }
}
