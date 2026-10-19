package com.example.nearbyalert

import android.content.Context
import org.json.JSONArray

object DestinationStore {
    private const val PREFS = "nearby_alert_prefs"
    private const val KEY = "destinations"
    fun get(context: Context): MutableList<Destination> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return MutableList(arr.length()) { i -> Destination.fromJson(arr.getJSONObject(i)) }
    }
    fun save(context: Context, destinations: List<Destination>) {
        val arr = JSONArray(); destinations.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
