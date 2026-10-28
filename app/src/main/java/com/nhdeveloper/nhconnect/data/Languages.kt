package com.nhdeveloper.nhconnect.data

data class Language(val code: String, val englishName: String, val nativeName: String, val flag: String)

data class Country(val code: String, val name: String) {
    val flag: String get() = flagEmoji(code)
}

fun flagEmoji(countryCode: String): String {
    if (countryCode.length != 2) return "\uD83C\uDFF3"
    val base = 0x1F1E6 - 'A'.code
    val a = String(Character.toChars(base + countryCode[0].uppercaseChar().code))
    val b = String(Character.toChars(base + countryCode[1].uppercaseChar().code))
    return a + b
}

/** Extensible list: add a Language(...) line here and the whole app (pickers, display, backend) accepts it. */
object Languages {
    val all: List<Language> = listOf(
        Language("en", "English", "English", flagEmoji("US")),
        Language("bn", "Bangla", "বাংলা", flagEmoji("BD")),
        Language("hi", "Hindi", "हिन्दी", flagEmoji("IN")),
        Language("ur", "Urdu", "اردو", flagEmoji("PK")),
        Language("ar", "Arabic", "العربية", flagEmoji("SA")),
        Language("es", "Spanish", "Español", flagEmoji("ES")),
        Language("fr", "French", "Français", flagEmoji("FR")),
        Language("de", "German", "Deutsch", flagEmoji("DE")),
        Language("pt", "Portuguese", "Português", flagEmoji("BR")),
        Language("ru", "Russian", "Русский", flagEmoji("RU")),
        Language("zh", "Chinese (Simplified)", "简体中文", flagEmoji("CN")),
        Language("ja", "Japanese", "日本語", flagEmoji("JP")),
        Language("ko", "Korean", "한국어", flagEmoji("KR")),
        Language("tr", "Turkish", "Türkçe", flagEmoji("TR")),
        Language("id", "Indonesian", "Bahasa Indonesia", flagEmoji("ID")),
        Language("ta", "Tamil", "தமிழ்", flagEmoji("IN")),
        Language("te", "Telugu", "తెలుగు", flagEmoji("IN")),
        Language("mr", "Marathi", "मराठी", flagEmoji("IN")),
        Language("ne", "Nepali", "नेपाली", flagEmoji("NP")),
        Language("it", "Italian", "Italiano", flagEmoji("IT"))
    )

    fun byCode(code: String?): Language? = all.firstOrNull { it.code.equals(code, ignoreCase = true) }
    fun nameOf(code: String?): String = byCode(code)?.englishName ?: (code ?: "")
}

object Countries {
    val all: List<Country> = listOf(
        Country("IN", "India"), Country("BD", "Bangladesh"), Country("PK", "Pakistan"),
        Country("NP", "Nepal"), Country("LK", "Sri Lanka"), Country("US", "United States"),
        Country("GB", "United Kingdom"), Country("CA", "Canada"), Country("AU", "Australia"),
        Country("NZ", "New Zealand"), Country("IE", "Ireland"), Country("AE", "United Arab Emirates"),
        Country("SA", "Saudi Arabia"), Country("QA", "Qatar"), Country("KW", "Kuwait"),
        Country("OM", "Oman"), Country("BH", "Bahrain"), Country("EG", "Egypt"),
        Country("TR", "Türkiye"), Country("MY", "Malaysia"), Country("SG", "Singapore"),
        Country("ID", "Indonesia"), Country("PH", "Philippines"), Country("TH", "Thailand"),
        Country("VN", "Vietnam"), Country("CN", "China"), Country("JP", "Japan"),
        Country("KR", "South Korea"), Country("DE", "Germany"), Country("FR", "France"),
        Country("ES", "Spain"), Country("IT", "Italy"), Country("PT", "Portugal"),
        Country("NL", "Netherlands"), Country("SE", "Sweden"), Country("NO", "Norway"),
        Country("CH", "Switzerland"), Country("RU", "Russia"), Country("BR", "Brazil"),
        Country("MX", "Mexico"), Country("NG", "Nigeria"), Country("ZA", "South Africa"),
        Country("KE", "Kenya")
    )

    fun byCode(code: String?): Country? = all.firstOrNull { it.code.equals(code, ignoreCase = true) }
}
