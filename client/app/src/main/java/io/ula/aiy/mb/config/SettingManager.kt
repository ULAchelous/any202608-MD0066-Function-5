package io.ula.aiy.mb.config

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

class SettingManager(
    context: Context
) {
    private var settings = HashMap<String, SettingContent<out Number>>()
    private var context: Context
    private var settingFile : File
    private var dataPath : File
    private var content  = JsonObject()

    val gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    private fun checkOut(){
        if(!settingFile.exists()) {
            settingFile.createNewFile()
        }
        settings.entries.forEach { entry ->
            if(!content.has(entry.key)) {
                if(entry.value.type == CONTENT_TYPE.BOOL)
                    content.addProperty(entry.key, entry.value.getBoolean())
                else
                    content.addProperty(entry.key, entry.value.getNumber())
            } else {
                // Update setting value from content
                val element = content.get(entry.key)
                if (entry.value.type == CONTENT_TYPE.BOOL) {
                    @Suppress("UNCHECKED_CAST")
                    (entry.value as SettingContent<Number>).set(if (element.asBoolean) 1 else 0)
                } else {
                    @Suppress("UNCHECKED_CAST")
                    (entry.value as SettingContent<Number>).set(element.asNumber)
                }
            }
        }
    }

    public fun register(entries : List<SettingContent<out Number>>){
        entries.forEach { entry ->
            settings.put(entry.id, entry)
        }
        checkOut()
        saveAll()
    }

    public fun getEntry(id :String): SettingContent<out Number>?{
        return settings.get(id)
    }

    public fun updateSetting(id: String, value: Number) {
        val entry = settings.get(id) ?: return
        @Suppress("UNCHECKED_CAST")
        (entry as SettingContent<Number>).set(value)
        if (entry.type == CONTENT_TYPE.BOOL) {
            content.addProperty(id, entry.getBoolean())
        } else {
            content.addProperty(id, entry.getNumber())
        }
        saveAll()
    }

    public fun saveAll(){
        settingFile.writeText(gson.toJson(content))
    }

    init {
        this.context = context
        this.dataPath = context.filesDir
        this.settingFile = File(this.dataPath,"settings.json")
        if (settingFile.exists()) {
            try {
                content = JsonParser.parseString(settingFile.readText()).asJsonObject
            } catch (e: Exception) {
                content = JsonObject()
            }
        }
    }
}
