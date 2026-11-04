package yt.dsh.barq_wear.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Supported languages in BARQ! Wear.
 *
 * How to add a new language to the project:
 * 1. Create `app/src/main/res/values-<code>/strings.xml` (copied from `res/values/strings.xml`)
 * 2. Add `<locale android:name="<code>" />` to `app/src/main/res/xml/locales_config.xml`
 * 3. Add a new entry below (e.g. `DE("de", "Deutsch")`)
 */
enum class AppLanguage(
    val code: String,
    val nativeLabel: String
) {
    SYSTEM("system", ""),
    EN("en", "English"),
    PL("pl", "Polski");

    companion object {
        fun fromCode(code: String?): AppLanguage {
            if (code.isNullOrBlank()) return SYSTEM
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: SYSTEM
        }
    }
}

object LocaleHelper {
    fun resolveLocale(language: AppLanguage): Locale {
        return when (language) {
            AppLanguage.SYSTEM -> {
                val sysLocale = Locale.getDefault()
                // Primary fallback is English unless the system locale is explicitly supported
                val supportedCodes = AppLanguage.entries
                    .filter { it != AppLanguage.SYSTEM }
                    .map { it.code.lowercase(Locale.ROOT) }
                if (sysLocale.language.lowercase(Locale.ROOT) in supportedCodes) {
                    sysLocale
                } else {
                    Locale.ENGLISH
                }
            }
            else -> Locale(language.code)
        }
    }

    fun localizedContext(baseContext: Context, language: AppLanguage): Context {
        val targetLocale = resolveLocale(language)
        val config = Configuration(baseContext.resources.configuration)
        config.setLocale(targetLocale)
        return baseContext.createConfigurationContext(config)
    }
}

private tailrec fun Context.findActivityResultRegistryOwner(): ActivityResultRegistryOwner? {
    return when (this) {
        is ActivityResultRegistryOwner -> this
        is ContextWrapper -> baseContext.findActivityResultRegistryOwner()
        else -> null
    }
}

@Composable
fun ProvideAppLocale(
    language: AppLanguage,
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val registryOwner = LocalActivityResultRegistryOwner.current
        ?: baseContext.findActivityResultRegistryOwner()

    val (localizedContext, localizedConfig) = remember(baseContext, baseConfig, language) {
        val targetLocale = LocaleHelper.resolveLocale(language)
        Locale.setDefault(targetLocale)
        val newConfig = Configuration(baseConfig).apply {
            setLocale(targetLocale)
        }
        val newContext = baseContext.createConfigurationContext(newConfig)
        newContext to newConfig
    }

    val currentRegistryOwner = registryOwner
    if (currentRegistryOwner != null) {
        CompositionLocalProvider(
            LocalContext provides localizedContext,
            LocalConfiguration provides localizedConfig,
            LocalActivityResultRegistryOwner provides currentRegistryOwner,
            content = content
        )
    } else {
        CompositionLocalProvider(
            LocalContext provides localizedContext,
            LocalConfiguration provides localizedConfig,
            content = content
        )
    }
}

