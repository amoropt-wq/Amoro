package com.example.util

import org.drinkless.tdlib.TdApi
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Modifier

object TdJsonSerializer {

    fun toJsonObject(obj: TdApi.Object?, maxDepth: Int = 6): JSONObject {
        if (obj == null) return JSONObject()
        return serializeObject(obj, 0, maxDepth)
    }

    fun toPrettyJson(obj: TdApi.Object?, maxDepth: Int = 6): String {
        return try {
            toJsonObject(obj, maxDepth).toString(2)
        } catch (_: Exception) {
            "{}"
        }
    }

    private fun serializeObject(obj: TdApi.Object, depth: Int, maxDepth: Int): JSONObject {
        val json = JSONObject()
        json.put("@type", obj.javaClass.simpleName)
        if (depth >= maxDepth) {
            json.put("_truncated", true)
            return json
        }

        val fields = obj.javaClass.fields
        for (field in fields) {
            if (Modifier.isStatic(field.modifiers)) continue
            val name = field.name
            if (name == "CONSTRUCTOR") continue
            try {
                val value = field.get(obj)
                json.put(name, serializeValue(value, depth + 1, maxDepth))
            } catch (_: Exception) {
                // Skip inaccessible field safely
            }
        }
        return json
    }

    private fun serializeValue(value: Any?, depth: Int, maxDepth: Int): Any {
        return when (value) {
            null -> JSONObject.NULL
            is Boolean, is Int, is Long, is Double, is Float, is String -> value
            is ByteArray -> "<${value.size} bytes>"
            is IntArray -> JSONArray().apply { value.forEach { put(it) } }
            is LongArray -> JSONArray().apply { value.forEach { put(it) } }
            is TdApi.Object -> serializeObject(value, depth, maxDepth)
            is Array<*> -> JSONArray().apply {
                val limit = minOf(value.size, 25)
                for (i in 0 until limit) {
                    put(serializeValue(value[i], depth + 1, maxDepth))
                }
            }
            else -> value.toString()
        }
    }
}
