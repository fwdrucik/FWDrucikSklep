package pl.fwdrucik.sklep.siec

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Serwer warsztatowy na komputerze — generowanie na własnej karcie.
 *
 * Zdjęcia poprawia Forge, animacje robi ComfyUI. Serwer sam przełącza jeden
 * na drugi, bo przy 12 GB VRAM oba naraz się nie mieszczą.
 *
 * Adresy są pełne (`@Url`), a nie względne: ten serwer stoi pod adresem
 * w sieci domowej, który użytkownik wpisuje w ustawieniach, a nie pod
 * fwdrucik.pl. Dzięki temu jeden klient Retrofit obsługuje dowolny adres
 * bez budowania nowej instancji przy każdej zmianie.
 */
interface SerwerWarsztatu {

    @GET
    suspend fun stan(@Url adres: String): StanSerwera

    @GET
    suspend fun zadania(@Url adres: String): OdpowiedzZadan

    @Multipart
    @POST
    suspend fun zlec(
        @Url adres: String,
        @Part("zadanie") zadanie: RequestBody,
        @Part("opis") opis: RequestBody,
        @Part plik: MultipartBody.Part,
    ): OdpowiedzZlecenia

    @GET
    suspend fun zadanie(@Url adres: String): StanZadania

    /** Gotowy plik — obraz albo wideo. Strumieniem, bo film waży swoje. */
    @Streaming
    @GET
    suspend fun pobierz(@Url adres: String): ResponseBody
}

/**
 * Kontrolka jak na maszynie.
 *
 * czerwony — nic nie chodzi, karta zimna, pierwsze zlecenie potrwa ~2 minuty
 * zolty    — backend wstaje albo coś właśnie liczy
 * zielony  — gotowe, można zlecać
 */
@Serializable
data class StanSerwera(
    val ok: Boolean = false,
    val swiatlo: String = "czerwony",
    val opis: String = "",
    @SerialName("mozna_zlecac") val moznaZlecac: Boolean = false,
    val comfyui: String = "",
    val forge: String = "",
    @SerialName("zadania_w_toku") val zadaniaWToku: Int = 0,
)

@Serializable
data class ZadanieSerwera(
    val klucz: String = "",
    val opis: String = "",
    val wynik: String = "",
    val backend: String = "",
)

@Serializable
data class OdpowiedzZadan(val ok: Boolean = false, val zadania: List<ZadanieSerwera> = emptyList())

@Serializable
data class OdpowiedzZlecenia(val ok: Boolean = false, val id: String = "", val blad: String = "")

@Serializable
data class StanZadania(
    val ok: Boolean = false,
    val id: String = "",
    val stan: String = "",
    val gotowe: Boolean = false,
    val blad: String? = null,
    @SerialName("wynik_url") val wynikUrl: String? = null,
)
