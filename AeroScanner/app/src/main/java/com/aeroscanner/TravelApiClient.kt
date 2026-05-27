package com.aeroscanner

import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class ExchangeRateSnapshot(
    val baseCode: String,
    val rates: Map<String, Double>,
    val updatedAt: String,
    val isFallback: Boolean = false,
)

data class DestinationWeather(
    val airportCode: String,
    val airportCity: String,
    val condition: String,
    val temperatureC: Double?,
    val windSpeedKmh: Double?,
    val maxTempC: Double?,
    val minTempC: Double?,
    val precipitationProbability: Int?,
)

private interface ExchangeRateService {
    @GET("v6/latest/{base}")
    suspend fun latest(@Path("base") base: String): ExchangeRateResponse
}

private data class ExchangeRateResponse(
    val result: String? = null,
    @SerializedName("base_code") val baseCode: String? = null,
    @SerializedName("time_last_update_utc") val updatedAt: String? = null,
    val rates: Map<String, Double>? = null,
)

private interface OpenMeteoService {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,weather_code,wind_speed_10m",
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min,precipitation_probability_max",
        @Query("forecast_days") forecastDays: Int = 1,
        @Query("timezone") timezone: String = "auto",
    ): OpenMeteoResponse
}

private data class OpenMeteoResponse(
    val current: OpenMeteoCurrent? = null,
    val daily: OpenMeteoDaily? = null,
)

private data class OpenMeteoCurrent(
    @SerializedName("temperature_2m") val temperatureC: Double? = null,
    @SerializedName("weather_code") val weatherCode: Int? = null,
    @SerializedName("wind_speed_10m") val windSpeedKmh: Double? = null,
)

private data class OpenMeteoDaily(
    @SerializedName("temperature_2m_max") val maxTempC: List<Double>? = null,
    @SerializedName("temperature_2m_min") val minTempC: List<Double>? = null,
    @SerializedName("precipitation_probability_max") val precipitationProbability: List<Int>? = null,
)

class TravelApiClient {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val exchangeService = retrofit("https://open.er-api.com/")
        .create(ExchangeRateService::class.java)

    private val weatherService = retrofit("https://api.open-meteo.com/")
        .create(OpenMeteoService::class.java)

    suspend fun fetchCnyExchangeRates(): ExchangeRateSnapshot = withContext(Dispatchers.IO) {
        val response = exchangeService.latest("CNY")
        val rates = response.rates.orEmpty() + ("CNY" to 1.0)
        if (response.result != "success" || rates.isEmpty()) {
            throw IllegalStateException("Exchange rate service returned no rates.")
        }
        ExchangeRateSnapshot(
            baseCode = response.baseCode ?: "CNY",
            rates = rates,
            updatedAt = response.updatedAt ?: "latest daily update",
        )
    }

    suspend fun fetchDestinationWeather(airport: Airport): DestinationWeather =
        withContext(Dispatchers.IO) {
            val response = weatherService.forecast(
                latitude = airport.latitude,
                longitude = airport.longitude,
            )
            val current = response.current
            val daily = response.daily
            DestinationWeather(
                airportCode = airport.code,
                airportCity = airport.city,
                condition = TravelDisplay.weatherLabel(current?.weatherCode),
                temperatureC = current?.temperatureC,
                windSpeedKmh = current?.windSpeedKmh,
                maxTempC = daily?.maxTempC?.firstOrNull(),
                minTempC = daily?.minTempC?.firstOrNull(),
                precipitationProbability = daily?.precipitationProbability?.firstOrNull(),
            )
        }

    private fun retrofit(baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
}
