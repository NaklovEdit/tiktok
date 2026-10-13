package com.volkan.videofeed

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Stocke favoris + catégories (par ID MediaStore) dans SharedPreferences. Léger, sans base de données. */
class Store(context: Context) {

    private val prefs = context.getSharedPreferences("videofeed", Context.MODE_PRIVATE)
    private val favorites = linkedSetOf<Long>()
    private val categories = linkedMapOf<String, MutableSet<Long>>()

    init {
        runCatching {
            val fav = JSONArray(prefs.getString("favs", "[]"))
            for (i in 0 until fav.length()) favorites.add(fav.getLong(i))

            val cats = JSONObject(prefs.getString("cats", "{}"))
            cats.keys().forEach { name ->
                val arr = cats.getJSONArray(name)
                val set = linkedSetOf<Long>()
                for (i in 0 until arr.length()) set.add(arr.getLong(i))
                categories[name] = set
            }
        }
    }

    private fun save() {
        val fav = JSONArray()
        favorites.forEach { fav.put(it) }
        val cats = JSONObject()
        categories.forEach { (name, ids) ->
            val arr = JSONArray()
            ids.forEach { arr.put(it) }
            cats.put(name, arr)
        }
        prefs.edit().putString("favs", fav.toString()).putString("cats", cats.toString()).apply()
    }

    fun isFavorite(id: Long) = favorites.contains(id)

    fun toggleFavorite(id: Long): Boolean {
        val nowFav = if (favorites.contains(id)) {
            favorites.remove(id); false
        } else {
            favorites.add(id); true
        }
        save()
        return nowFav
    }

    fun categoryNames(): List<String> = categories.keys.toList()

    fun addCategory(name: String) {
        if (name.isNotBlank() && !categories.containsKey(name)) {
            categories[name] = linkedSetOf()
            save()
        }
    }

    fun deleteCategory(name: String) {
        categories.remove(name)
        save()
    }

    fun inCategory(name: String, id: Long) = categories[name]?.contains(id) == true

    fun setInCategory(name: String, id: Long, value: Boolean) {
        val set = categories[name] ?: return
        if (value) set.add(id) else set.remove(id)
        save()
    }

    fun idsOf(name: String): Set<Long> = categories[name] ?: emptySet()
}
