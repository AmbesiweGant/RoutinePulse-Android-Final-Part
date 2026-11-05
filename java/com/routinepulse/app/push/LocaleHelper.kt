package com.routinepulse.app.push

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Per-app language (REQ-03 / REQ-10): English, isiZulu ("zu"), Afrikaans ("af"), and isiXhosa ("xh").
 * AppCompat stores the choice and recreates the screen; Android 13+ also shows it in system settings.
 */
object LocaleHelper {
    val supported = listOf("en", "zu", "af", "xh")

    /** The language the app is currently showing, limited to the ones we translate. */
    fun currentLanguage(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        val chosen = if (!locales.isEmpty()) locales.get(0)?.language else null
        val language = chosen ?: Locale.getDefault().language
        return if (language in supported) language else "en"
    }

    fun setLanguage(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    /**
     * A context that uses the app language. Background code (e.g. the push service) has no Activity, so on
     * Android 12 and older it would otherwise show notifications in the phone's language instead of the app's.
     */
    fun localized(context: Context): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty()) return context
        val locale = locales.get(0) ?: return context
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
