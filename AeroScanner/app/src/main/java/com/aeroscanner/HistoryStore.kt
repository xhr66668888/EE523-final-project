package com.aeroscanner

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class CostRecord(
    val route: String,
    val routeCodes: List<String>,
    val segments: List<CostSegmentData>,
    val surfaceTransfers: List<SurfaceTransferData>,
    val totalDistanceKm: Double,
    val type: String = "manual_cost",
    val source: String = "manual",
    val createdAt: String,
    val ticketPriceRmb: Double,
    val totalTripCostRmb: Double = ticketPriceRmb,
    val costPerKmRmb: Double,
    val carbonKg: Double = 0.0,
    val baggageWeightKg: Double? = null,
    val baggageAllowanceKg: Double? = null,
    val baggageOverageFeeRmb: Double? = null,
    val priceLevel: String,
    val seatsBookable: String? = null,
    val bookingClasses: String? = null,
    val offerId: String? = null,
    val currency: String? = "CNY",
)

data class CostSegmentData(
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

data class SurfaceTransferData(
    val originCode: String,
    val destinationCode: String,
    val originCity: String,
    val destinationCity: String,
    val distanceKm: Double,
)

data class HistoryPayload(val version: Int = 3, val queries: List<CostRecord>)

object HistoryStore {
    private var historyFile: File? = null

    fun init(context: Context) {
        if (historyFile == null) {
            historyFile = File(context.filesDir, "flight_cost_history.json")
        }
    }

    suspend fun load(): List<CostRecord> {
        val file = historyFile ?: return emptyList()
        return withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext emptyList<CostRecord>()

            try {
                val json = file.readText()
                val payload = Gson().fromJson(json, HistoryPayload::class.java)
                payload.queries
            } catch (e: Exception) {
                // Corrupt file — clear and start fresh
                file.delete()
                emptyList()
            }
        }
    }

    suspend fun save(records: List<CostRecord>) {
        val file = historyFile ?: return
        withContext(Dispatchers.IO) {
            val payload = HistoryPayload(queries = records)
            val json = Gson().toJson(payload)
            val tempFile = File(file.parent, "flight_cost_history.json.tmp")
            tempFile.writeText(json)
            tempFile.renameTo(file)
        }
    }

    suspend fun append(record: CostRecord) {
        val records = load().toMutableList()
        records.add(record)
        save(records)
    }

    suspend fun delete(indexes: Set<Int>) {
        val records = load().toMutableList()
        val sorted = indexes.sortedDescending()
        for (i in sorted) {
            if (i in records.indices) {
                records.removeAt(i)
            }
        }
        save(records)
    }

    suspend fun rankedEntries(): List<IndexedValue<CostRecord>> {
        val records = load()
        return records
            .mapIndexed { index, record -> IndexedValue(index, record) }
            .filter { it.value.costPerKmRmb > 0 || it.value.totalDistanceKm > 0 }
            .sortedBy { it.value.costPerKmRmb }
    }
}
