package com.aeroscanner

object RouteTools {
    const val MAX_SEGMENTS = 6

    data class RouteSegment(
        val originCode: String,
        val destinationCode: String,
    )

    data class ParsedRoute(
        val routeLabel: String,
        val routeCodes: List<String>,
        val segments: List<RouteSegment>,
        val airports: Map<String, Airport>,
    )

    data class SurfaceTransfer(
        val originCode: String,
        val destinationCode: String,
        val originCity: String,
        val destinationCity: String,
        val distanceKm: Double,
    )

    fun normalizeIata(code: String): String {
        val cleaned = code.trim().uppercase()
        if (!Regex("^[A-Z]{3}$").matches(cleaned)) {
            throw IllegalArgumentException("'$code' is not a valid three-letter IATA airport code.")
        }
        return cleaned
    }

    fun extractIataCodes(value: String): List<String> {
        val regex = Regex("(?<![A-Za-z])([A-Za-z]{3})(?![A-Za-z])")
        return regex.findAll(value).map { normalizeIata(it.groupValues[1]) }.toList()
    }

    fun parseRoute(value: String): ParsedRoute {
        val cleaned = value.trim()
        if (cleaned.isEmpty()) {
            throw IllegalArgumentException("Enter at least two airport codes, for example PEK-ICN.")
        }

        // Try open-jaw chunks first
        val segments = openJawChunks(cleaned)
            ?: naturalLanguagePairs(cleaned)
            ?: continuousRouteSegments(extractIataCodes(cleaned))

        validateSegments(segments)

        // Fetch airports for all unique codes
        val uniqueCodes = linkedSetOf<String>()
        for (seg in segments) {
            uniqueCodes.add(seg.originCode)
            uniqueCodes.add(seg.destinationCode)
        }
        val airports = mutableMapOf<String, Airport>()
        for (code in uniqueCodes) {
            airports[code] = AirportDatabase.get(code)
                ?: throw IllegalArgumentException("Airport $code not found in database.")
        }

        val routeCodes = routeCodesFromSegments(segments)
        val label = routeSegmentLabel(segments)

        return ParsedRoute(
            routeLabel = label,
            routeCodes = routeCodes,
            segments = segments,
            airports = airports,
        )
    }

    fun routeCodesFromSegments(segments: List<RouteSegment>): List<String> {
        val codes = mutableListOf<String>()
        for ((origin, destination) in segments) {
            if (codes.isEmpty()) {
                codes.add(origin)
            } else if (codes.last() != origin) {
                codes.add(origin)
            }
            codes.add(destination)
        }
        return codes
    }

    fun routeSegmentLabel(segments: List<RouteSegment>): String {
        if (!hasSurfaceTransfer(segments)) {
            return routeCodesFromSegments(segments).joinToString("-")
        }
        return segments.joinToString(" / ") { "${it.originCode}-${it.destinationCode}" }
    }

    fun hasSurfaceTransfer(segments: List<RouteSegment>): Boolean {
        return segments.zipWithNext().any { (prev, next) ->
            prev.destinationCode != next.originCode
        }
    }

    fun calculateSurfaceTransfers(
        segments: List<RouteSegment>,
        airports: Map<String, Airport>,
    ): List<SurfaceTransfer> {
        if (segments.size < 2) return emptyList()
        val transfers = mutableListOf<SurfaceTransfer>()
        for (i in 0 until segments.size - 1) {
            val prevDest = segments[i].destinationCode
            val nextOrig = segments[i + 1].originCode
            if (prevDest != nextOrig) {
                val prevAirport = airports[prevDest] ?: continue
                val nextAirport = airports[nextOrig] ?: continue
                val dist = GeoTools.greatCircleDistanceKm(
                    prevAirport.latitude, prevAirport.longitude,
                    nextAirport.latitude, nextAirport.longitude,
                )
                transfers.add(
                    SurfaceTransfer(
                        originCode = prevDest,
                        destinationCode = nextOrig,
                        originCity = prevAirport.city,
                        destinationCity = nextAirport.city,
                        distanceKm = dist,
                    )
                )
            }
        }
        return transfers
    }

    // --- Private helpers ---

    private fun validateSegments(segments: List<RouteSegment>) {
        if (segments.isEmpty()) {
            throw IllegalArgumentException("Enter at least two airport codes, for example PEK-ICN.")
        }
        if (segments.size > MAX_SEGMENTS) {
            throw IllegalArgumentException("A ticket can contain at most $MAX_SEGMENTS flight segments.")
        }
        for ((origin, destination) in segments) {
            if (origin == destination) {
                throw IllegalArgumentException("Adjacent flight segment airports must be different.")
            }
        }
    }

    private fun openJawChunks(value: String): List<RouteSegment>? {
        val chunks = value.split(Regex("[,;/|；、\\n]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (chunks.size < 2) return null

        val chunkCodes = chunks.map { extractIataCodes(it) }
        if (chunkCodes.all { it.size == 2 }) {
            return chunkCodes.map { RouteSegment(it[0], it[1]) }
        }
        return null
    }

    private fun naturalLanguagePairs(value: String): List<RouteSegment>? {
        val pattern = Regex(
            "(?<![A-Za-z])([A-Za-z]{3})(?![A-Za-z])\\s*(?:to|到)\\s*" +
            "(?<![A-Za-z])([A-Za-z]{3})(?![A-Za-z])",
            RegexOption.IGNORE_CASE,
        )
        val pairs = pattern.findAll(value).map {
            RouteSegment(normalizeIata(it.groupValues[1]), normalizeIata(it.groupValues[2]))
        }.toList()
        if (pairs.size < 2) return null

        val codes = extractIataCodes(value)
        if (codes.size != pairs.size * 2) return null
        return pairs
    }

    private fun continuousRouteSegments(codes: List<String>): List<RouteSegment> {
        if (codes.size < 2) {
            throw IllegalArgumentException("Enter at least two airport codes, for example PEK-ICN.")
        }
        return codes.zip(codes.drop(1)).map { RouteSegment(it.first, it.second) }
    }
}
