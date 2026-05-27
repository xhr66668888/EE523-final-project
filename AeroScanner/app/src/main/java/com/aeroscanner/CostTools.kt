package com.aeroscanner

import androidx.compose.ui.graphics.Color
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object CostTools {
    const val LOW_COST_THRESHOLD = 0.40
    const val HIGH_COST_THRESHOLD = 0.80
    const val DEFAULT_BAGGAGE_ALLOWANCE_KG = 23.0
    const val BAGGAGE_OVERAGE_FEE_RMB_PER_KG = 80.0
    const val FLIGHT_CO2E_KG_PER_PASSENGER_KM = 0.115

    enum class PriceLevel { LOW, MID, HIGH }

    data class CostResult(
        val route: String,
        val routeCodes: List<String>,
        val segments: List<CostSegment>,
        val surfaceTransfers: List<RouteTools.SurfaceTransfer>,
        val totalDistanceKm: Double,
        val ticketPriceRmb: Double,
        val baggageWeightKg: Double?,
        val baggageAllowanceKg: Double,
        val baggageOverageFeeRmb: Double,
        val totalTripCostRmb: Double,
        val costPerKmRmb: Double,
        val carbonKg: Double,
        val priceLevel: PriceLevel,
        val createdAt: String,
    )

    data class CostSegment(
        val originCode: String,
        val destinationCode: String,
        val originCity: String,
        val destinationCity: String,
        val distanceKm: Double,
        val flightNumber: String? = null,
        val departureTime: String? = null,
        val arrivalTime: String? = null,
        val cabin: String? = null,
        val bookingClass: String? = null,
        val fareBasis: String? = null,
    )

    fun classifyCostPerKm(costPerKm: Double): PriceLevel = when {
        costPerKm <= LOW_COST_THRESHOLD -> PriceLevel.LOW
        costPerKm <= HIGH_COST_THRESHOLD -> PriceLevel.MID
        else -> PriceLevel.HIGH
    }

    fun priceLevelColor(level: PriceLevel): Color = when (level) {
        PriceLevel.LOW -> AeroscannerColors.CostLow
        PriceLevel.MID -> AeroscannerColors.CostMid
        PriceLevel.HIGH -> AeroscannerColors.CostHigh
    }

    fun priceLevelLabel(level: PriceLevel): String = level.name

    fun calculateManualCost(
        parsedRoute: RouteTools.ParsedRoute,
        ticketPriceRmb: Double,
        baggageWeightKg: Double? = null,
        baggageAllowanceKg: Double = DEFAULT_BAGGAGE_ALLOWANCE_KG,
    ): CostResult {
        val costSegments = mutableListOf<CostSegment>()
        var totalDistance = 0.0

        for (seg in parsedRoute.segments) {
            val origin = parsedRoute.airports[seg.originCode]!!
            val destination = parsedRoute.airports[seg.destinationCode]!!
            val dist = GeoTools.greatCircleDistanceKm(
                origin.latitude, origin.longitude,
                destination.latitude, destination.longitude,
            )
            totalDistance += dist
            costSegments.add(
                CostSegment(
                    originCode = seg.originCode,
                    destinationCode = seg.destinationCode,
                    originCity = origin.city,
                    destinationCity = destination.city,
                    distanceKm = dist,
                )
            )
        }

        if (totalDistance <= 0) {
            throw IllegalArgumentException("Route distance must be greater than 0.")
        }

        val surfaceTransfers = RouteTools.calculateSurfaceTransfers(
            parsedRoute.segments, parsedRoute.airports
        )
        val overageFee = calculateBaggageOverageFeeRmb(baggageWeightKg, baggageAllowanceKg)
        val totalTripCost = ticketPriceRmb + overageFee
        val totalCostPerKm = totalTripCost / totalDistance
        val carbonKg = totalDistance * FLIGHT_CO2E_KG_PER_PASSENGER_KM

        return CostResult(
            route = parsedRoute.routeLabel,
            routeCodes = parsedRoute.routeCodes,
            segments = costSegments,
            surfaceTransfers = surfaceTransfers,
            totalDistanceKm = totalDistance,
            ticketPriceRmb = ticketPriceRmb,
            baggageWeightKg = baggageWeightKg,
            baggageAllowanceKg = baggageAllowanceKg,
            baggageOverageFeeRmb = overageFee,
            totalTripCostRmb = totalTripCost,
            costPerKmRmb = totalCostPerKm,
            carbonKg = carbonKg,
            priceLevel = classifyCostPerKm(totalCostPerKm),
            createdAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        )
    }

    fun calculateBaggageOverageFeeRmb(weightKg: Double?, allowanceKg: Double): Double {
        if (weightKg == null || allowanceKg <= 0) return 0.0
        val overageKg = (weightKg - allowanceKg).coerceAtLeast(0.0)
        return overageKg * BAGGAGE_OVERAGE_FEE_RMB_PER_KG
    }

    fun parseTicketPriceToCny(
        value: String,
        currencyCode: String,
        cnyToCurrencyRate: Double,
    ): Double {
        val cleaned = value.trim()
            .replace(",", "")
            .replace(Regex("(?i)(RMB|CNY|USD|EUR|GBP|JPY|¥|￥|\\$|€|£)"), "")
            .trim()

        val price = cleaned.toDoubleOrNull()
            ?: throw IllegalArgumentException("Ticket price must be a number.")

        if (price <= 0) {
            throw IllegalArgumentException("Ticket price must be greater than 0.")
        }
        if (cnyToCurrencyRate <= 0) {
            throw IllegalArgumentException("Exchange rate for $currencyCode is unavailable.")
        }
        return price / cnyToCurrencyRate
    }
}
