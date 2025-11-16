package net.kibotu.ro.client.storage

import com.russhwolf.settings.Settings
import com.russhwolf.settings.PreferencesSettings
import java.util.prefs.Preferences

actual fun createSettings(): Settings {
    // Use Java Preferences API for persistent storage on JVM
    val prefs = Preferences.userRoot().node("net.kibotu.ro.client")
    return PreferencesSettings(prefs)
}

