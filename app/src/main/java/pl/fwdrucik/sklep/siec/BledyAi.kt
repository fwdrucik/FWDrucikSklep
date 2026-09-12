package pl.fwdrucik.sklep.siec

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/** Wyłącznie własny, stały komunikat. Nigdy treść odpowiedzi/prompt/URL/klucz. */
class BladAi(
    komunikat: String,
    val http: Int? = null,
    val klasa: String = "BladAi",
) : IOException(komunikat)

fun bladOdpowiedziAi(kod: String? = null, tresc: String? = null, http: Int? = null, klasa: String = "BladAi"): BladAi {
    // Starsze serwery mają tylko "blad". Tekst służy wyłącznie klasyfikacji, nie wychodzi do UI/logów.
    val wskazowka = (kod.orEmpty() + " " + tresc.orEmpty().take(2048)).lowercase()
    val komunikat = when {
        http == 402 || "payment" in wskazowka || "credit" in wskazowka || "402" in wskazowka ->
            "Usługa nie ma dostępnych środków lub uprawnień do tego modelu. Wybierz inny dostępny model. Niczego nie kupiono ani nie przełączono na płatną usługę."
        http == 429 || "rate_limit" in wskazowka || "quota" in wskazowka || "429" in wskazowka ||
            Regex("\\blimit(?:u|y|ów|em)?\\b").containsMatchIn(wskazowka) ->
            "Usługa osiągnęła limit. Spróbuj później lub wybierz inny dostępny model."
        "cli jest zajęty" in wskazowka || "kończy poprzedni opis" in wskazowka ->
            "Asystent jest zajęty. Poczekaj, aż skończy poprzedni opis. Nie wysłano ponownie żądania."
        http == 401 || http == 403 || "zaloguj" in wskazowka ->
            "Usługa nie pozwoliła na dostęp. Sprawdź logowanie do wybranej usługi na komputerze lub wybierz inny model."
        "vision" in wskazowka || "photo_only" in wskazowka || "odczytuje zdję" in wskazowka || "nie widzi zdję" in wskazowka ->
            "Ten model nie odczytał zdjęcia. Dopisz lub podyktuj kilka słów o wyrobie albo wybierz model z odczytem zdjęć."
        http == 413 -> "Zdjęcie jest za duże. Wybierz mniejszy plik i spróbuj ponownie."
        http == 422 || http == 400 -> "Usługa nie przyjęła danych. Sprawdź zdjęcie, wybrany format i dopisz kilka słów o wyrobie."
        http == 404 || http == 503 || "unavailable" in wskazowka || "niedostępny" in wskazowka ->
            "Wybrany model jest niedostępny. Odśwież listę i wybierz dostępny model. Nie zmieniono go automatycznie."
        else -> "Wybrana usługa nie przygotowała wyniku. Spróbuj później lub wybierz inny dostępny model."
    }
    return BladAi(komunikat + " Twoje dane i wcześniejsze wyniki pozostają w szkicu.", http, klasa)
}

fun bladAi(blad: Throwable): BladAi = when (blad) {
    is BladAi -> blad
    is HttpException -> {
        // Ograniczony odczyt i tylko klasyfikacja. HTML, stosy, adresy i klucze nigdy nie trafiają do komunikatu.
        val pola = runCatching {
            blad.response()?.errorBody()?.use { body ->
                val znaki = CharArray(8192)
                val ile = body.charStream().read(znaki)
                if (ile > 0) Json.parseToJsonElement(String(znaki, 0, ile)).jsonObject else null
            }
        }.getOrNull()
        fun pole(nazwa: String) = runCatching { pola?.get(nazwa)?.jsonPrimitive?.content }.getOrNull()
        bladOdpowiedziAi(pole("kod") ?: pole("code"), pole("blad"), blad.code(), "HttpException")
    }
    is SocketTimeoutException -> BladAi("Oczekiwanie na usługę trwało za długo. Sprawdź stan zadania przed ponowieniem. Twoje dane pozostają w szkicu.", klasa = "SocketTimeoutException")
    is IOException -> BladAi("Nie udało się połączyć z warsztatem lub odebrać wyniku. Sprawdź połączenie i stan zadania przed ponowieniem. Twoje dane pozostają w szkicu.", klasa = "IOException")
    is IllegalArgumentException -> BladAi("Sprawdź zdjęcie i wpisane dane lub wybierz dostępny model. Twoje dane pozostają w szkicu.", klasa = "IllegalArgumentException")
    else -> BladAi("Nie udało się odczytać wyniku usługi. Spróbuj później. Twoje dane i wcześniejsze wyniki pozostają w szkicu.", klasa = "Exception")
}

/** Log whitelist: żadnych message, cause, URL, stacktrace ani ciała HTTP. */
fun diagnostykaAi(operacja: String, blad: Throwable): String {
    val nazwa = operacja.takeIf { it in setOf("opis", "modele", "media", "stan") } ?: "ai"
    val status = when (blad) { is BladAi -> blad.http; is HttpException -> blad.code(); else -> null }
    val klasa = when (blad) {
        is BladAi -> blad.klasa.takeIf { it in setOf("BladAi", "HttpException", "SocketTimeoutException", "IOException", "IllegalArgumentException", "Exception") } ?: "BladAi"
        is HttpException -> "HttpException"
        is kotlinx.serialization.SerializationException -> "SerializationException"
        is java.net.ConnectException -> "ConnectException"
        is SocketTimeoutException -> "SocketTimeoutException"
        is IOException -> "IOException"
        is IllegalArgumentException -> "IllegalArgumentException"
        else -> "Exception"
    }
    return "operation=$nazwa http=${status ?: "-"} class=$klasa"
}

suspend fun <T> bezpieczneAi(blok: suspend () -> T): T = try {
    blok()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    throw bladAi(e)
}
