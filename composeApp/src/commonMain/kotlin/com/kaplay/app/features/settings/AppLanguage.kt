package com.kaplay.app.features.settings

import com.kaplay.app.features.player.DeviceLanguagePreferences
import kaplay.composeapp.generated.resources.Res
import kaplay.composeapp.generated.resources.lang_arabic
import kaplay.composeapp.generated.resources.lang_bengali
import kaplay.composeapp.generated.resources.lang_bulgarian
import kaplay.composeapp.generated.resources.lang_croatian
import kaplay.composeapp.generated.resources.lang_czech
import kaplay.composeapp.generated.resources.lang_english
import kaplay.composeapp.generated.resources.lang_french
import kaplay.composeapp.generated.resources.lang_german
import kaplay.composeapp.generated.resources.lang_greek
import kaplay.composeapp.generated.resources.lang_hebrew
import kaplay.composeapp.generated.resources.lang_hungarian
import kaplay.composeapp.generated.resources.lang_indonesian
import kaplay.composeapp.generated.resources.lang_italian
import kaplay.composeapp.generated.resources.lang_polish
import kaplay.composeapp.generated.resources.lang_portuguese_brazil
import kaplay.composeapp.generated.resources.lang_portuguese_portugal
import kaplay.composeapp.generated.resources.lang_romanian
import kaplay.composeapp.generated.resources.lang_russian
import kaplay.composeapp.generated.resources.lang_slovak
import kaplay.composeapp.generated.resources.lang_spanish
import kaplay.composeapp.generated.resources.lang_turkish
import kaplay.composeapp.generated.resources.lang_urdu
import kaplay.composeapp.generated.resources.lang_norwegian
import kaplay.composeapp.generated.resources.lang_dutch
import kaplay.composeapp.generated.resources.lang_japanese
import kaplay.composeapp.generated.resources.lang_vietnamese
import kaplay.composeapp.generated.resources.settings_appearance_app_language_device
import org.jetbrains.compose.resources.StringResource

enum class AppLanguage(
    val code: String,
    val labelRes: StringResource,
) {
    DEVICE("device", Res.string.settings_appearance_app_language_device),
    ARABIC("ar", Res.string.lang_arabic),
    BENGALI("bn", Res.string.lang_bengali),
    BULGARIAN("bg", Res.string.lang_bulgarian),
    CROATIAN("hr", Res.string.lang_croatian),
    CZECH("cs", Res.string.lang_czech),
    ENGLISH("en", Res.string.lang_english),
    FRENCH("fr", Res.string.lang_french),
    GERMAN("de", Res.string.lang_german),
    GREEK("el", Res.string.lang_greek),
    HEBREW("he", Res.string.lang_hebrew),
    HUNGARIAN("hu", Res.string.lang_hungarian),
    INDONESIAN("id", Res.string.lang_indonesian),
    ITALIAN("it", Res.string.lang_italian),
    POLISH("pl", Res.string.lang_polish),
    PORTUGUESE_BRAZIL("pt-BR", Res.string.lang_portuguese_brazil),
    PORTUGUESE("pt", Res.string.lang_portuguese_portugal),
    ROMANIAN("ro", Res.string.lang_romanian),
    RUSSIAN("ru", Res.string.lang_russian),
    SLOVAK("sk", Res.string.lang_slovak),
    SPANISH("es", Res.string.lang_spanish),
    TURKISH("tr", Res.string.lang_turkish),
    URDU("ur", Res.string.lang_urdu),
    NORWEGIAN("nb", Res.string.lang_norwegian),
    DUTCH("nl", Res.string.lang_dutch),
    JAPANESE("ja", Res.string.lang_japanese),
    VIETNAMESE("vi", Res.string.lang_vietnamese),
    ;

    fun isRightToLeft(): Boolean {
        val languageCode = if (this == DEVICE) DeviceLanguagePreferences.preferredLanguageCodes().firstOrNull() else code
        return languageCode?.substringBefore('-') in setOf(ARABIC.code, HEBREW.code, URDU.code)
    }

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: DEVICE
    }
}
