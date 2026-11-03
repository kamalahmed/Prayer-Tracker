package com.kamalahmed.prayerapp.core.location

data class City(
    val name: String,
    val country: String,
    val lat: Double,
    val lng: Double,
    val tz: String,
    val region: String = "",
    val countryCode: String = "",
    val population: Long = 0,
) {
    /** "City, Region, Country" (region omitted when empty or equal to the city name). */
    val display get() = listOfNotNull(name, region.takeIf { it.isNotBlank() && it != name }, country.takeIf { it.isNotBlank() }).joinToString(", ")
    /** Short label stored as the location name: "City, Country". */
    val label get() = if (country.isBlank()) name else "$name, $country"
    /** Regional-indicator flag emoji from the ISO country code ("" if unknown). */
    val flag get() = if (countryCode.length == 2 && countryCode.all { it in 'A'..'Z' })
        String(Character.toChars(0x1F1E6 + (countryCode[0] - 'A'))) + String(Character.toChars(0x1F1E6 + (countryCode[1] - 'A'))) else ""
}

/** Geofence: only accept a new auto location when it moved further than the radius. */
object GeoFence {
    fun shouldUpdate(oldLat: Double, oldLng: Double, newLat: Double, newLng: Double, radiusKm: Int): Boolean =
        com.kamalahmed.prayerapp.core.prayer.Geo.distanceKm(oldLat, oldLng, newLat, newLng) > radiusKm
}
