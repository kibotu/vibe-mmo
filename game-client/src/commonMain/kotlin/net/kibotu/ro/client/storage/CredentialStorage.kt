package net.kibotu.ro.client.storage

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set

class CredentialStorage(private val settings: Settings = createSettings()) {
    companion object {
        private const val KEY_EMAIL = "saved_email"
        private const val KEY_PASSWORD = "saved_password"
        private const val KEY_REMEMBER_ME = "remember_me"
    }
    
    fun saveCredentials(email: String, password: String) {
        settings[KEY_EMAIL] = email
        settings[KEY_PASSWORD] = password
        settings[KEY_REMEMBER_ME] = true
    }
    
    fun clearCredentials() {
        settings.remove(KEY_EMAIL)
        settings.remove(KEY_PASSWORD)
        settings[KEY_REMEMBER_ME] = false
    }
    
    fun getSavedEmail(): String? {
        val email = if (settings.get(KEY_REMEMBER_ME, false)) {
            settings.get(KEY_EMAIL, "")
        } else {
            null
        }
        return email?.takeIf { it.isNotBlank() }
    }
    
    fun getSavedPassword(): String? {
        val password = if (settings.get(KEY_REMEMBER_ME, false)) {
            settings.get(KEY_PASSWORD, "")
        } else {
            null
        }
        return password?.takeIf { it.isNotBlank() }
    }
    
    fun hasSavedCredentials(): Boolean {
        return settings.get(KEY_REMEMBER_ME, false) && 
               settings.get(KEY_EMAIL, "").isNotBlank() && 
               settings.get(KEY_PASSWORD, "").isNotBlank()
    }
    
    fun isRememberMeEnabled(): Boolean {
        return settings.get(KEY_REMEMBER_ME, false)
    }
}

