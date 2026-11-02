package com.kamalahmed.prayerapp.core.location

data class City(val name: String, val country: String, val lat: Double, val lng: Double, val tz: String) {
    val display get() = "$name, $country"
}

/** Small offline city list for manual location selection (custom coordinates are also supported). */
object Cities {
    val all = listOf(
        City("Sharjah", "UAE", 25.3463, 55.4209, "Asia/Dubai"),
        City("Dubai", "UAE", 25.2048, 55.2708, "Asia/Dubai"),
        City("Abu Dhabi", "UAE", 24.4539, 54.3773, "Asia/Dubai"),
        City("Ajman", "UAE", 25.4052, 55.5136, "Asia/Dubai"),
        City("Ras Al Khaimah", "UAE", 25.8007, 55.9762, "Asia/Dubai"),
        City("Fujairah", "UAE", 25.1288, 56.3265, "Asia/Dubai"),
        City("Al Ain", "UAE", 24.2075, 55.7447, "Asia/Dubai"),
        City("Makkah", "Saudi Arabia", 21.4225, 39.8262, "Asia/Riyadh"),
        City("Madinah", "Saudi Arabia", 24.4672, 39.6024, "Asia/Riyadh"),
        City("Riyadh", "Saudi Arabia", 24.7136, 46.6753, "Asia/Riyadh"),
        City("Jeddah", "Saudi Arabia", 21.4858, 39.1925, "Asia/Riyadh"),
        City("Doha", "Qatar", 25.2854, 51.5310, "Asia/Qatar"),
        City("Kuwait City", "Kuwait", 29.3759, 47.9774, "Asia/Kuwait"),
        City("Manama", "Bahrain", 26.2285, 50.5860, "Asia/Bahrain"),
        City("Muscat", "Oman", 23.5880, 58.3829, "Asia/Muscat"),
        City("Cairo", "Egypt", 30.0444, 31.2357, "Africa/Cairo"),
        City("Istanbul", "Turkey", 41.0082, 28.9784, "Europe/Istanbul"),
        City("Karachi", "Pakistan", 24.8607, 67.0011, "Asia/Karachi"),
        City("Lahore", "Pakistan", 31.5204, 74.3587, "Asia/Karachi"),
        City("Dhaka", "Bangladesh", 23.8103, 90.4125, "Asia/Dhaka"),
        City("Chittagong", "Bangladesh", 22.3569, 91.7832, "Asia/Dhaka"),
        City("Delhi", "India", 28.6139, 77.2090, "Asia/Kolkata"),
        City("Mumbai", "India", 19.0760, 72.8777, "Asia/Kolkata"),
        City("Hyderabad", "India", 17.3850, 78.4867, "Asia/Kolkata"),
        City("Kozhikode", "India", 11.2588, 75.7804, "Asia/Kolkata"),
        City("Jakarta", "Indonesia", -6.2088, 106.8456, "Asia/Jakarta"),
        City("Kuala Lumpur", "Malaysia", 3.1390, 101.6869, "Asia/Kuala_Lumpur"),
        City("Singapore", "Singapore", 1.3521, 103.8198, "Asia/Singapore"),
        City("London", "UK", 51.5072, -0.1276, "Europe/London"),
        City("Paris", "France", 48.8566, 2.3522, "Europe/Paris"),
        City("Berlin", "Germany", 52.5200, 13.4050, "Europe/Berlin"),
        City("New York", "USA", 40.7128, -74.0060, "America/New_York"),
        City("Toronto", "Canada", 43.6532, -79.3832, "America/Toronto"),
        City("Chicago", "USA", 41.8781, -87.6298, "America/Chicago"),
        City("Los Angeles", "USA", 34.0522, -118.2437, "America/Los_Angeles"),
        City("Sydney", "Australia", -33.8688, 151.2093, "Australia/Sydney"),
        City("Johannesburg", "South Africa", -26.2041, 28.0473, "Africa/Johannesburg"),
        City("Lagos", "Nigeria", 6.5244, 3.3792, "Africa/Lagos"),
        City("Amman", "Jordan", 31.9454, 35.9284, "Asia/Amman"),
        City("Baghdad", "Iraq", 33.3152, 44.3661, "Asia/Baghdad"),
        City("Tehran", "Iran", 35.6892, 51.3890, "Asia/Tehran"),
    )

    fun search(q: String): List<City> =
        if (q.isBlank()) all else all.filter { it.display.contains(q.trim(), ignoreCase = true) }
}

/** Geofence: only accept a new auto location when it moved further than the radius. */
object GeoFence {
    fun shouldUpdate(oldLat: Double, oldLng: Double, newLat: Double, newLng: Double, radiusKm: Int): Boolean =
        com.kamalahmed.prayerapp.core.prayer.Geo.distanceKm(oldLat, oldLng, newLat, newLng) > radiusKm
}
