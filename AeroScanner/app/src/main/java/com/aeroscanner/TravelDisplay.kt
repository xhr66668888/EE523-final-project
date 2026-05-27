package com.aeroscanner

import java.util.Locale
import kotlin.math.roundToInt

enum class UnitSystem(val label: String, val distanceUnit: String, val weightUnit: String) {
    METRIC("Metric", "km", "kg"),
    IMPERIAL("Imperial", "mi", "lb"),
}

data class CurrencySpec(val code: String, val symbol: String, val decimals: Int)

object TravelDisplay {
    val supportedCurrencies = listOf(
        CurrencySpec("CNY", "¥", 2),
        CurrencySpec("USD", "$", 2),
        CurrencySpec("EUR", "€", 2),
        CurrencySpec("GBP", "£", 2),
        CurrencySpec("JPY", "¥", 0),
    )

    val fallbackCnyRates = mapOf(
        "CNY" to 1.0,
        "USD" to 0.14,
        "EUR" to 0.13,
        "GBP" to 0.11,
        "JPY" to 22.0,
    )

    fun rateFor(currencyCode: String, rates: Map<String, Double>): Double =
        rates[currencyCode] ?: fallbackCnyRates[currencyCode] ?: 1.0

    fun currencySpec(currencyCode: String): CurrencySpec =
        supportedCurrencies.firstOrNull { it.code == currencyCode } ?: supportedCurrencies.first()

    fun formatMoneyFromCny(
        cny: Double,
        currencyCode: String,
        rates: Map<String, Double>,
        includeCode: Boolean = true,
    ): String {
        val spec = currencySpec(currencyCode)
        val amount = cny * rateFor(currencyCode, rates)
        val formatted = "%.${spec.decimals}f".format(Locale.US, amount)
        return if (includeCode) "${spec.symbol}$formatted ${spec.code}" else "${spec.symbol}$formatted"
    }

    fun formatCostRate(
        cnyPerKm: Double,
        currencyCode: String,
        rates: Map<String, Double>,
        unitSystem: UnitSystem,
    ): String {
        val spec = currencySpec(currencyCode)
        val multiplier = if (unitSystem == UnitSystem.IMPERIAL) 1.609344 else 1.0
        val amount = cnyPerKm * multiplier * rateFor(currencyCode, rates)
        return "${spec.symbol}${"%.2f".format(Locale.US, amount)}/${unitSystem.distanceUnit}"
    }

    fun formatDistance(km: Double, unitSystem: UnitSystem): String {
        val value = if (unitSystem == UnitSystem.IMPERIAL) km * 0.621371 else km
        return "${"%.0f".format(Locale.US, value)} ${unitSystem.distanceUnit}"
    }

    fun formatWeight(kg: Double, unitSystem: UnitSystem): String {
        val value = if (unitSystem == UnitSystem.IMPERIAL) kg * 2.2046226218 else kg
        return "${"%.1f".format(Locale.US, value)} ${unitSystem.weightUnit}"
    }

    fun parseWeightToKg(value: String, unitSystem: UnitSystem): Double? {
        val cleaned = value.trim().replace(",", "")
        if (cleaned.isEmpty()) return null
        val parsed = cleaned.toDoubleOrNull()
            ?: throw IllegalArgumentException("Baggage weight must be a number.")
        if (parsed < 0) {
            throw IllegalArgumentException("Baggage weight cannot be negative.")
        }
        return if (unitSystem == UnitSystem.IMPERIAL) parsed / 2.2046226218 else parsed
    }

    fun formatTemperature(tempC: Double, unitSystem: UnitSystem): String {
        val value = if (unitSystem == UnitSystem.IMPERIAL) tempC * 9.0 / 5.0 + 32.0 else tempC
        val unit = if (unitSystem == UnitSystem.IMPERIAL) "°F" else "°C"
        return "${value.roundToInt()}$unit"
    }

    fun formatSpeed(speedKmh: Double, unitSystem: UnitSystem): String {
        val value = if (unitSystem == UnitSystem.IMPERIAL) speedKmh * 0.621371 else speedKmh
        val unit = if (unitSystem == UnitSystem.IMPERIAL) "mph" else "km/h"
        return "${value.roundToInt()} $unit"
    }

    fun weatherLabel(code: Int?): String = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67 -> "Rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Weather"
    }
}
