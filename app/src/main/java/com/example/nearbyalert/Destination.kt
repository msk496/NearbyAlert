package com.example.nearbyalert

import org.json.JSONObject

data class Destination(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 1000f,
    val enabled: Boolean = true,
    val favorite: Boolean = false
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("lat", latitude); put("lon", longitude)
        put("radius", radiusMeters.toDouble()); put("enabled", enabled); put("favorite", favorite)
    }
    companion object {
        fun fromJson(o: JSONObject) = Destination(
            o.getString("id"), o.optString("name", "목적지"), o.getDouble("lat"), o.getDouble("lon"),
            o.optDouble("radius", 1000.0).toFloat(), o.optBoolean("enabled", true), o.optBoolean("favorite", false)
        )
    }
}
