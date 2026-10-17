package com.antoninofaro.welcometool.utils

import com.antoninofaro.welcometool.data.entity.ChangesetEntity
import com.antoninofaro.welcometool.data.model.OsmChangeset
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

data class GeoBox(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double
) {
    init {
        require(west in -180.0..180.0) { "west ($west) must be in [-180.0, 180.0]" }
        require(east in -180.0..180.0) { "east ($east) must be in [-180.0, 180.0]" }
        require(south in -90.0..90.0) { "south ($south) must be in [-90.0, 90.0]" }
        require(north in -90.0..90.0) { "north ($north) must be in [-90.0, 90.0]" }
        require(west <= east) { "west ($west) must be <= east ($east)" }
        require(south <= north) { "south ($south) must be <= north ($north)" }
    }

    fun toOsmParam(): String {
        return "$west,$south,$east,$north"
    }

    fun intersectsInclusive(o: GeoBox): Boolean {
        return o.west <= east && o.east >= west && o.south <= north && o.north >= south
    }

    companion object {
        fun fromNominatim(boundingBox: List<String>): GeoBox {
            require(boundingBox.size >= 4) { "Nominatim boundingBox must have at least 4 elements" }
            val south = boundingBox[0].toDouble()
            val north = boundingBox[1].toDouble()
            val west = boundingBox[2].toDouble()
            val east = boundingBox[3].toDouble()
            return GeoBox(west = west, south = south, east = east, north = north)
        }

        fun parseOsmParam(bboxStr: String): GeoBox? {
            if (bboxStr.isBlank()) return null
            val parts = bboxStr.split(",").mapNotNull { it.trim().toDoubleOrNull() }
            if (parts.size != 4) return null
            return try {
                GeoBox(west = parts[0], south = parts[1], east = parts[2], north = parts[3])
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

object BBoxUtils {
    val anomalyCounter = AtomicInteger(0)

    fun resetAnomalyCounter() {
        anomalyCounter.set(0)
    }

    fun getAnomalyCount(): Int = anomalyCounter.get()
}

fun OsmChangeset.toGeoBox(): GeoBox? {
    val minW = minLon ?: return null
    val minS = minLat ?: return null
    val maxE = maxLon ?: return null
    val maxN = maxLat ?: return null
    return try {
        GeoBox(west = minW, south = minS, east = maxE, north = maxN)
    } catch (_: IllegalArgumentException) {
        null
    }
}

fun ChangesetEntity.toGeoBox(): GeoBox? {
    val minW = minLon ?: return null
    val minS = minLat ?: return null
    val maxE = maxLon ?: return null
    val maxN = maxLat ?: return null
    return try {
        GeoBox(west = minW, south = minS, east = maxE, north = maxN)
    } catch (_: IllegalArgumentException) {
        null
    }
}

fun OsmChangeset.intersectsBBox(bboxStr: String): Boolean {
    if (bboxStr.isBlank() || bboxStr == Constants.WORLD_BBOX) return true
    val areaBox = GeoBox.parseOsmParam(bboxStr) ?: return true
    val changesetBox = this.toGeoBox() ?: return false
    return areaBox.intersectsInclusive(changesetBox)
}

fun ChangesetEntity.intersectsBBox(bboxStr: String): Boolean {
    if (bboxStr.isBlank() || bboxStr == Constants.WORLD_BBOX) return true
    val areaBox = GeoBox.parseOsmParam(bboxStr) ?: return true
    val changesetBox = this.toGeoBox() ?: return false
    return areaBox.intersectsInclusive(changesetBox)
}

fun List<OsmChangeset>.filterForAreaBBox(areaBBox: String): List<OsmChangeset> {
    if (areaBBox.isBlank() || areaBBox == Constants.WORLD_BBOX) return this
    val areaBox = GeoBox.parseOsmParam(areaBBox) ?: return this

    return this.filter { changeset ->
        val cBox = changeset.toGeoBox()
        if (cBox == null) {
            // bbox del changeset assente (changeset vuoto): scartalo senza warning
            false
        } else if (areaBox.intersectsInclusive(cBox)) {
            true
        } else {
            // NON interseca: scartalo, conta un'anomalia e fai log warning
            BBoxUtils.anomalyCounter.incrementAndGet()
            Timber.w(
                "Anomaly: Changeset id=%d with bbox=%s does not intersect area bbox=%s",
                changeset.id,
                cBox.toOsmParam(),
                areaBBox
            )
            false
        }
    }
}
