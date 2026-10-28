package es.kinetrace.cajanegrapocket

import kotlin.math.*

data class UtmCoordinate(
    val zoneNumber: Int,
    val hemisphere: String,
    val eastingMeters: Double,
    val northingMeters: Double
) {
    val zoneLabel: String get() = "$zoneNumber$hemisphere"
}

/** Conversión WGS84 geodésica -> UTM, con factor de escala UTM k0 = 0,9996. */
object UtmConverter {
    private const val A = 6378137.0
    private const val ECC_SQ = 0.00669437999014
    private const val K0 = 0.9996

    fun fromLatLon(latitudeDeg: Double, longitudeDeg: Double): UtmCoordinate? {
        if (!latitudeDeg.isFinite() || !longitudeDeg.isFinite()) return null
        if (latitudeDeg !in -80.0..84.0 || longitudeDeg !in -180.0..180.0) return null

        var zone = floor((longitudeDeg + 180.0) / 6.0).toInt() + 1
        // Zonas especiales oficiales de Noruega y Svalbard.
        if (latitudeDeg in 56.0..64.0 && longitudeDeg in 3.0..12.0) zone = 32
        if (latitudeDeg in 72.0..84.0) {
            zone = when {
                longitudeDeg in 0.0..9.0 -> 31
                longitudeDeg in 9.0..21.0 -> 33
                longitudeDeg in 21.0..33.0 -> 35
                longitudeDeg in 33.0..42.0 -> 37
                else -> zone
            }
        }
        val lonOriginDeg = (zone - 1) * 6 - 180 + 3
        val latRad = Math.toRadians(latitudeDeg)
        val lonRad = Math.toRadians(longitudeDeg)
        val lonOriginRad = Math.toRadians(lonOriginDeg.toDouble())
        val eccPrimeSq = ECC_SQ / (1.0 - ECC_SQ)
        val n = A / sqrt(1.0 - ECC_SQ * sin(latRad).pow(2))
        val t = tan(latRad).pow(2)
        val c = eccPrimeSq * cos(latRad).pow(2)
        val aa = cos(latRad) * (lonRad - lonOriginRad)
        val m = A * ((1 - ECC_SQ / 4 - 3 * ECC_SQ.pow(2) / 64 - 5 * ECC_SQ.pow(3) / 256) * latRad
                - (3 * ECC_SQ / 8 + 3 * ECC_SQ.pow(2) / 32 + 45 * ECC_SQ.pow(3) / 1024) * sin(2 * latRad)
                + (15 * ECC_SQ.pow(2) / 256 + 45 * ECC_SQ.pow(3) / 1024) * sin(4 * latRad)
                - (35 * ECC_SQ.pow(3) / 3072) * sin(6 * latRad))
        val easting = K0 * n * (aa + (1 - t + c) * aa.pow(3) / 6.0
                + (5 - 18 * t + t.pow(2) + 72 * c - 58 * eccPrimeSq) * aa.pow(5) / 120.0) + 500000.0
        var northing = K0 * (m + n * tan(latRad) * (aa.pow(2) / 2.0
                + (5 - t + 9 * c + 4 * c.pow(2)) * aa.pow(4) / 24.0
                + (61 - 58 * t + t.pow(2) + 600 * c - 330 * eccPrimeSq) * aa.pow(6) / 720.0))
        val hemisphere = if (latitudeDeg >= 0.0) "N" else "S"
        if (latitudeDeg < 0.0) northing += 10000000.0
        return UtmCoordinate(zone, hemisphere, easting, northing)
    }
}
