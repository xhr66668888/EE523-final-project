package com.aeroscanner

import kotlin.math.*

object GeoTools {
    const val EARTH_RADIUS_KM = 6371.0088

    fun greatCircleDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val deltaLat = Math.toRadians(lat2 - lat1)
        val deltaLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(deltaLat / 2).pow(2) +
                cos(rLat1) * cos(rLat2) * sin(deltaLon / 2).pow(2)
        val centralAngle = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * centralAngle
    }
}
