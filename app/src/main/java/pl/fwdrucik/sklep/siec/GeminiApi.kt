package pl.fwdrucik.sklep.siec

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Gemini — opis produktu ze zdjęcia i poprawianie zdjęcia.
 *
 * Osobny klient sieciowy niż reszta aplikacji, i to nie jest ozdobnik:
 * klient sklepowy nosi ciasteczko sesji administratora fwdrucik.pl. Wysłanie
 * go do Google byłoby wyciekiem danych logowania. Tu leci wyłącznie klucz API
 * w parametrze i treść zapytania.
 */
interface GeminiApi {

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generuj(
        @Path("model") model: String,
        @Query("key") klucz: String,
        @Body cialo: ZapytanieGemini,
    ): OdpowiedzGemini
}

// ------------------------------------------------------------------ zapytanie

@Serializable
data class ZapytanieGemini(
    val contents: List<Tresc>,
    val generationConfig: Konfiguracja? = null,
    val systemInstruction: Tresc? = null,
)

@Serializable
data class Tresc(val parts: List<Czesc>, val role: String? = null)

@Serializable
data class Czesc(
    val text: String? = null,
    @SerialName("inline_data") val inlineData: DaneWlasne? = null,
)

@Serializable
data class DaneWlasne(
    @SerialName("mime_type") val mimeType: String,
    /** Obraz zakodowany w base64, bez przedrostka `data:`. */
    val data: String,
)

@Serializable
data class Konfiguracja(
    val temperature: Double? = null,
    @SerialName("responseMimeType") val responseMimeType: String? = null,
    @SerialName("responseSchema") val responseSchema: Schemat? = null,
    @SerialName("responseModalities") val responseModalities: List<String>? = null,
)

/**
 * Schemat odpowiedzi.
 *
 * Model poproszony o „zwróć JSON” potrafi dokleić komentarz przed nawiasem
 * albo obudować całość blokiem kodu. Ze schematem odpowiedź jest strukturą,
 * a nie tekstem, który trzeba wróżyć — i parser nie wysypuje się co dziesiąte
 * wywołanie.
 */
@Serializable
data class Schemat(
    val type: String,
    val properties: Map<String, Schemat>? = null,
    val items: Schemat? = null,
    val required: List<String>? = null,
    val description: String? = null,
    val enum: List<String>? = null,
)

// ---------------------------------------------------------------- odpowiedź

@Serializable
data class OdpowiedzGemini(
    val candidates: List<Kandydat> = emptyList(),
    val promptFeedback: Informacja? = null,
)

@Serializable
data class Kandydat(
    val content: Tresc? = null,
    val finishReason: String? = null,
)

@Serializable
data class Informacja(val blockReason: String? = null)

/** Pierwszy fragment tekstu z odpowiedzi albo null. */
fun OdpowiedzGemini.tekst(): String? =
    candidates.firstOrNull()?.content?.parts?.firstNotNullOfOrNull { it.text }

/** Pierwszy obraz z odpowiedzi (base64) albo null. */
fun OdpowiedzGemini.obraz(): DaneWlasne? =
    candidates.firstOrNull()?.content?.parts?.firstNotNullOfOrNull { it.inlineData }
