package pl.fwdrucik.sklep.siec

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
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

    /**
     * Muse Code przez most na komputerze.
     *
     * CLI `muse` chodzi wylacznie lokalnie, w WSL. Telefon nie ma jak go
     * zawolac, a uderzenie wprost w platne API bylo swiadomie odciete. Serwer
     * warsztatowy stoi na tym samym komputerze, wiec wola CLI w imieniu
     * telefonu — i nie kosztuje to nic ponad abonament.
     */
    @FormUrlEncoded
    @POST
    suspend fun muse(
        @Url adres: String,
        @Field("prompt") prompt: String,
        @Field("katalog") katalog: String = "",
    ): OdpowiedzMuse

    /**
     * To samo pytanie, ale ze zdjęciem w załączniku.
     *
     * CLI `muse` przyjmuje obraz przez `--image`, więc opis wyrobu z kadru
     * da się zrobić bez klucza Gemini — na komputerze i bez limitu.
     */
    @Multipart
    @POST
    suspend fun museZeZdjeciem(
        @Url adres: String,
        @Part plik: MultipartBody.Part,
        @Part("prompt") prompt: RequestBody,
    ): OdpowiedzMuse

    @Multipart
    @POST
    suspend fun zlec(
        @Url adres: String,
        @Part("zadanie") zadanie: RequestBody,
        @Part("opis") opis: RequestBody,
        /**
         * Ksztalt kadru: „16:9", „9:16" albo „1:1".
         *
         * Serwer nie ma na to panelu — format ustawia sie slowami w prompcie,
         * wiec telefon musi powiedziec wprost, czego chce. Aplikacja zawsze
         * przysyla jedna z trzech wartosci; serwer i tak sprawdza ja u siebie,
         * bo starsze wersje aplikacji nie wysylaja tego pola wcale.
         */
        @Part("proporcje") proporcje: RequestBody,
        @Part plik: MultipartBody.Part,
    ): OdpowiedzZlecenia

    /**
     * Copilot RAZEM ZE ZDJĘCIEM.
     *
     * Copilot potrafi czytać kadr, ale dotąd apka wysyłała mu wyłącznie tekst
     * notatki — więc materiał i kształt wyrobu zgadywał z nazwy. To jedyny
     * silnik opisów, który tego nie dostawał.
     */
    @Multipart
    @POST
    suspend fun copilotZeZdjeciem(
        @Url adres: String,
        @Part plik: MultipartBody.Part,
        @Part("prompt") prompt: RequestBody,
    ): OdpowiedzMuse

    @FormUrlEncoded
    @POST
    suspend fun copilot(
        @Url adres: String,
        @Field("prompt") prompt: String,
    ): OdpowiedzMuse

    @Multipart
    @POST
    suspend fun metaZeZdjeciem(
        @Url adres: String,
        @Part plik: MultipartBody.Part,
        @Part("prompt") prompt: RequestBody,
    ): OdpowiedzMuse

    @FormUrlEncoded
    @POST
    suspend fun meta(
        @Url adres: String,
        @Field("prompt") prompt: String,
    ): OdpowiedzMuse

    /**
     * Bada sugerowaną średnią cenę rynkową na Allegro, OLX, Erli i w internecie.
     */
    @GET
    suspend fun wycena(
        @Url adres: String,
        @retrofit2.http.Query("fraza") fraza: String,
        @retrofit2.http.Query("kategoria") kategoria: String = "",
    ): OdpowiedzWyceny

    /**
     * Tworzy prywatny szkic oferty na Allegro (status INACTIVE).
     */
    @FormUrlEncoded
    @POST
    suspend fun utworzSzkicAllegro(
        @Url adres: String,
        @Field("tytul") tytul: String,
        @Field("kategoria_id") kategoriaId: String,
        @Field("cena_pln") cenaPln: Double,
        @Field("opis_tekst") opisTekst: String,
        @Field("zdjecia") zdjecia: String = "",
        @Field("stan_sztuk") stanSztuk: Int = 1,
    ): OdpowiedzSzkicuAllegro

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
    /** „dostepne" albo „brak mostu" — czy serwer dosiegnie CLI Muse w WSL. */
    val muse: String = "",
    /** „dziala" albo „wylaczony" — przegladarka Flow (Veo) na komputerze. */
    val flow: String = "",
    val meta: String = "",
    val copilot: String = "",
    @SerialName("zadania_w_toku") val zadaniaWToku: Int = 0,
    /**
     * Kolejka do karty graficznej, w kolejnosci ustalonej przez agregator.
     *
     * Serwer nie oddaje jej w kolejnosci naciskania przycisku, tylko w tej,
     * w ktorej naprawde policzy: najpierw najtansze zlecenia, z doliczona kara
     * za przeladowanie silnika i znizka za czas juz przeczekany. Telefon ma
     * pokazac to, co sie wydarzy, a nie to, co zostalo zamowione.
     */
    val kolejka: List<PozycjaKolejki> = emptyList(),
)

@Serializable
data class PozycjaKolejki(
    val id: String = "",
    val zadanie: String = "",
    /** Ocena agregatora — im nizsza, tym wczesniej pojdzie. Nie jest czasem. */
    val koszt: Int = 0,
    /** Ile to zadanie liczy sie naprawde, przy rozgrzanym modelu. */
    @SerialName("czas_s") val czasS: Int = 0,
)

@Serializable
data class OdpowiedzMuse(
    val ok: Boolean = false,
    val odpowiedz: String = "",
    val blad: String = "",
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

@Serializable
data class OfertaCenowa(
    val portal: String = "",
    val cena: Double = 0.0,
    val tytul: String = "",
)

@Serializable
data class OdpowiedzWyceny(
    val ok: Boolean = false,
    val fraza: String = "",
    @SerialName("sugerowana_cena") val sugerowanaCena: Double = 0.0,
    @SerialName("sugerowana_allegro") val sugerowanaAllegro: Double = 0.0,
    @SerialName("min_cena") val minCena: Double = 0.0,
    @SerialName("max_cena") val maxCena: Double = 0.0,
    @SerialName("liczba_ofert") val liczbaOfert: Int = 0,
    val znalezione: List<OfertaCenowa> = emptyList(),
    val blad: String? = null,
)

@Serializable
data class OdpowiedzSzkicuAllegro(
    val ok: Boolean = false,
    val id: String? = null,
    val url: String? = null,
    val status: String = "",
    @SerialName("kategoria_id") val kategoriaId: String? = null,
    val blad: String? = null,
)

