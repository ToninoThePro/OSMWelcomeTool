package com.antoninofaro.welcometool.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class BBoxUtilsTest {

    @Test
    fun `fromNominatim converts south, north, west, east to west, south, east, north`() {
        val nominatimList = listOf("37.5", "37.6", "15.0", "15.1") // [south, north, west, east]
        val geoBox = GeoBox.fromNominatim(nominatimList)

        assertEquals(15.0, geoBox.west, 0.00001)
        assertEquals(37.5, geoBox.south, 0.00001)
        assertEquals(15.1, geoBox.east, 0.00001)
        assertEquals(37.6, geoBox.north, 0.00001)
        assertEquals("15.0,37.5,15.1,37.6", geoBox.toOsmParam())
    }

    @Test
    fun `toOsmParam uses dot as decimal separator even under Locale ITALY`() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ITALY)
            val box = GeoBox(west = 15.08, south = 37.50, east = 15.10, north = 37.52)
            val param = box.toOsmParam()
            val parts = param.split(",")

            assertEquals(4, parts.size)
            assertTrue("West must contain dot", parts[0].contains("."))
            assertFalse("West must not contain comma decimal", parts[0].contains(","))
            assertEquals("15.08,37.5,15.1,37.52", param)
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun `intersectsInclusive returns true for overlapping boxes`() {
        val area = GeoBox(west = 10.0, south = 10.0, east = 20.0, north = 20.0)
        val overlapping = GeoBox(west = 15.0, south = 15.0, east = 25.0, north = 25.0)

        assertTrue(area.intersectsInclusive(overlapping))
        assertTrue(overlapping.intersectsInclusive(area))
    }

    @Test
    fun `intersectsInclusive returns true when touching on edge`() {
        val area = GeoBox(west = 10.0, south = 10.0, east = 20.0, north = 20.0)
        val touchingEdge = GeoBox(west = 20.0, south = 10.0, east = 30.0, north = 20.0)

        assertTrue(area.intersectsInclusive(touchingEdge))
        assertTrue(touchingEdge.intersectsInclusive(area))
    }

    @Test
    fun `intersectsInclusive returns true when touching on corner`() {
        val area = GeoBox(west = 10.0, south = 10.0, east = 20.0, north = 20.0)
        val touchingCorner = GeoBox(west = 20.0, south = 20.0, east = 30.0, north = 30.0)

        assertTrue(area.intersectsInclusive(touchingCorner))
        assertTrue(touchingCorner.intersectsInclusive(area))
    }

    @Test
    fun `intersectsInclusive returns false for disjoint boxes`() {
        val area = GeoBox(west = 10.0, south = 10.0, east = 20.0, north = 20.0)
        val disjoint = GeoBox(west = 20.1, south = 20.1, east = 30.0, north = 30.0)

        assertFalse(area.intersectsInclusive(disjoint))
        assertFalse(disjoint.intersectsInclusive(area))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `validation fails when west is greater than east`() {
        GeoBox(west = 25.0, south = 10.0, east = 20.0, north = 20.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `validation fails when south is greater than north`() {
        GeoBox(west = 10.0, south = 25.0, east = 20.0, north = 20.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `validation fails when latitude is out of bounds`() {
        GeoBox(west = 10.0, south = -95.0, east = 20.0, north = 20.0)
    }

    @Test
    fun `parseOsmParam parses valid string and returns null for invalid string`() {
        val valid = GeoBox.parseOsmParam("15.0,37.5,15.1,37.6")
        assertNotNull(valid)
        assertEquals(15.0, valid!!.west, 0.00001)

        val invalid = GeoBox.parseOsmParam("invalid,bbox,format")
        assertNull(invalid)
    }
}
