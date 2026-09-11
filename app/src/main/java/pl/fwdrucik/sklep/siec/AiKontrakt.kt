package pl.fwdrucik.sklep.siec

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

fun pytanieOBrakujacyFakt(pole: String): String = when (pole.trim().lowercase()) {
    "material" -> "Z czego jest wyrób?"
    "wymiary" -> "Jakie ma wymiary? Dopisz jednostkę, np. cm."
    else -> pole
}

fun kosztPoPolsku(koszt: String): String = when (koszt.trim().lowercase()) {
    "free", "bezplatny", "bezplatne" -> "bezpłatnie"
    "local" -> "lokalnie na komputerze"
    "paid", "platny", "platne" -> "płatna usługa"
    "subscription" -> "w ramach abonamentu"
    "unknown", "" -> "nie podano kosztu"
    else -> koszt
}

@Serializable
data class ModelAi(
    val id: String = "",
    val nazwa: String = "",
    val rodzaje: List<String> = emptyList(),
    val droga: String = "",
    val koszt: String = "Nie podano kosztu",
    val dostepny: Boolean = false,
    val powod: String = "",
    val vision: Boolean = false,
)

/** Deklaracja zatwierdzonej automatycznej drogi ze zdjęciem, nie lista dowolnych vision. */
@Serializable
data class AutoTekstAi(
    val model: String = "",
    val dostepny: Boolean = false,
    val vision: Boolean = false,
)

@Serializable
data class KatalogAi(
    val ok: Boolean = false,
    val modele: List<ModelAi> = emptyList(),
    val domyslne: Map<String, String> = emptyMap(),
    @SerialName("auto_tekst") val autoTekst: AutoTekstAi = AutoTekstAi(),
) {
    fun dla(rodzaj: String): List<ModelAi> = modele
        .filter { it.id.isNotBlank() && it.id != "auto" && rodzaj in it.rodzaje }
        .distinctBy { it.id }

    /** Sam model vision na liście nie upoważnia do użycia go w auto. */
    fun powodBlokadyOpisu(dane: DaneOpisuAi, maZdjecie: Boolean): String? = when {
        !maZdjecie && !dane.maFakty() -> "Dodaj zdjęcie lub kilka słów o wyrobie."
        !moznaWybrac(dane.model, "tekst") -> "Wybrany model jest niedostępny. Odśwież listę lub wybierz Automatycznie."
        dane.maFakty() -> null
        !czyCzytaZdjecie(dane.model) -> "Odczyt samego zdjęcia nie jest teraz potwierdzony. Dopisz lub podyktuj kilka słów w kroku 1, albo wybierz dostępny model z odczytem zdjęć. Zdjęcie i film możesz zrobić osobno."
        else -> null
    }

    fun czyCzytaZdjecie(id: String): Boolean = ok && if (id == "auto") {
        autoTekst.model.isNotBlank() && autoTekst.dostepny && autoTekst.vision
    } else {
        modele.any { it.id == id && "tekst" in it.rodzaje && it.dostepny && it.vision }
    }

    fun moznaWybrac(id: String, rodzaj: String): Boolean =
        id == "auto" || (ok && dla(rodzaj).any { it.id == id && it.dostepny })
}

@Serializable
data class DaneOpisuAi(
    val notatka: String = "",
    val nazwa: String = "",
    val material: String = "",
    val wymiary: String = "",
    val model: String = "auto",
) {
    fun maFakty(): Boolean = listOf(notatka, nazwa, material, wymiary).any { it.isNotBlank() }
}

@Serializable
data class OpisAi(
    val ok: Boolean = false,
    val nazwa: String = "",
    @SerialName("opis_krotki") val opisKrotki: String = "",
    val opis: String = "",
    val kategoria: String? = null,
    @SerialName("do_uzupelnienia") val doUzupelnienia: List<String> = emptyList(),
    val model: String = "",
    val zrodlo: String = "",
    val ostrzezenie: String? = null,
    val blad: String? = null,
    val kod: String? = null,
)

/** Ten sam klient jest używany przez repozytorium i testy HTTP; żadnych kluczy dostawców. */
class AiWarsztatu(private val api: SerwerWarsztatu) {
    suspend fun modele(base: String): KatalogAi = bezpieczneAi {
        api.modeleAi("${base.trimEnd('/')}/ai/modele").also {
            if (!it.ok) throw bladOdpowiedziAi("model_unavailable")
        }
    }

    suspend fun opis(base: String, dane: DaneOpisuAi, plik: MultipartBody.Part? = null): OpisAi = bezpieczneAi {
        require(plik != null || dane.maFakty()) { "Dodaj zdjęcie lub napisz kilka słów o wyrobie." }
        api.opisAi(
            "${base.trimEnd('/')}/ai/opis", plik,
            pole(dane.notatka), pole(dane.nazwa), pole(dane.material), pole(dane.wymiary),
            pole(dane.model.ifBlank { "auto" }),
        ).also {
            if (!it.ok) throw bladOdpowiedziAi(it.kod, it.blad)
            check(it.nazwa.isNotBlank() || it.opis.isNotBlank() || it.opisKrotki.isNotBlank()) {
                "Serwer nie zwrócił opisu. Dopisz, co przedstawia zdjęcie, i spróbuj ponownie."
            }
        }
    }

    companion object {
        fun pole(tekst: String) = tekst.toRequestBody("text/plain; charset=utf-8".toMediaType())
    }
}

/** Etykieta wyboru, nie zgadywanie silnika faktycznie użytego przez auto. */
fun nazwaHistoriiAi(zadanie: String, model: String, katalog: KatalogAi): String {
    val czynnosc = if (zadanie.startsWith("animacja")) "Film" else "Poprawa zdjęcia"
    val wybrany = when {
        model == "auto" -> "Automatycznie — wybór serwera"
        model.isNotBlank() -> katalog.modele.firstOrNull { it.id == model }?.nazwa?.ifBlank { "wybrany model" } ?: "wybrany model"
        zadanie.endsWith("-flow") -> "Flow"
        zadanie.endsWith("-meta") -> "Meta AI"
        zadanie.endsWith("-copilot") -> "Copilot"
        else -> "dotychczasowa droga warsztatu"
    }
    return "$czynnosc ($wybrany)"
}
