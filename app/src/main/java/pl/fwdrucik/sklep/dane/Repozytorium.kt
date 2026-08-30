package pl.fwdrucik.sklep.dane

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import pl.fwdrucik.sklep.siec.SklepApi
import pl.fwdrucik.sklep.siec.SerwerWarsztatu
import pl.fwdrucik.sklep.siec.SlojNaCiastka
import pl.fwdrucik.sklep.siec.StanZadania
import retrofit2.HttpException
import java.io.File
import java.io.IOException

/** Wynik operacji sieciowej — albo dane, albo zdanie, które da się pokazać człowiekowi. */
sealed interface Wynik<out T> {
    data class Jest<T>(val dane: T) : Wynik<T>
    data class Blad(val komunikat: String) : Wynik<Nothing>
}

class Repozytorium(
    private val api: SklepApi,
    private val sloj: SlojNaCiastka,
    private val kontekst: Context,
    private val warsztat: SerwerWarsztatu,
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun czyZalogowany(): Boolean = sloj.czyZalogowany()

    suspend fun zaloguj(login: String, haslo: String): Wynik<Uzytkownik> = wywolaj {
        // Serwer chroni logowanie podwójnym ciasteczkiem CSRF: wymaga nagłówka
        // X-Fw-Csrf równego ciasteczku fw_csrf. Przeglądarka dostaje to
        // ciasteczko przy wejściu na stronę — aplikacja żadnej strony nie
        // otwiera, więc przy pierwszym uruchomieniu nie miała go w ogóle
        // i każde logowanie wracało jako 403. To zapytanie je wydaje.
        if (sloj.tokenCsrf() == null) {
            runCatching { api.ja() }
        }

        val odp = api.zaloguj(login, haslo)
        val u = odp.uzytkownik ?: throw IOException("Serwer nie odesłał danych konta.")
        if (!u.jestAdminem) {
            // Aplikacja jest narzędziem warsztatu, nie sklepem dla klienta.
            // Zalogowanie zwykłego konta i tak odbiłoby się o fw_wymagaj_admina()
            // przy pierwszym zapytaniu — lepiej powiedzieć to od razu.
            sloj.wyczysc()
            throw IOException("To konto nie ma uprawnień administratora sklepu.")
        }
        u
    }

    suspend fun wyloguj() {
        runCatching { api.wyloguj() }
        sloj.wyczysc()
    }

    suspend fun produkty(): Wynik<List<Produkt>> = wywolaj { api.produkty().produkty }

    suspend fun zapisz(p: Produkt, cenaZl: String, cenaPromoZl: String): Wynik<OdpowiedzZapisu> =
        wywolaj {
            api.zapiszProdukt(
                id = p.id,
                nazwa = p.nazwa,
                kategoria = p.kategoria,
                opisKrotki = p.opisKrotki,
                opis = p.opis,
                cena = cenaZl,
                cenaPromo = cenaPromoZl,
                // Puste pole to "wykonywane na zamówienie" — tak czyta je PHP.
                stan = p.stan?.toString().orEmpty(),
                wagaG = p.wagaG.toString(),
                jednostka = p.jednostka,
                czasRealizacji = p.czasRealizacji,
                status = p.status,
                pozycja = p.pozycja.toString(),
            )
        }

    suspend fun ustawStatus(id: Int, status: String): Wynik<Unit> =
        wywolaj { api.ustawStatusProduktu(id, status); Unit }

    suspend fun usun(id: Int): Wynik<Unit> = wywolaj { api.usunProdukt(id); Unit }

    suspend fun usunObraz(id: Int): Wynik<Unit> = wywolaj { api.usunObraz(id); Unit }

    suspend fun statusZamowienia(id: Int, status: String): Wynik<Unit> =
        wywolaj { api.ustawStatusZamowienia(id, status); Unit }

    suspend fun zamowienia(): Wynik<List<Zamowienie>> = wywolaj { api.zamowienia().zamowienia }

    /**
     * Wysyła zdjęcie wybrane w telefonie.
     *
     * Kopiujemy je najpierw do pliku tymczasowego, bo z `content://` nie da się
     * odczytać rozmiaru bez wczytania całości do pamięci, a serwer i tak odrzuca
     * pliki powyżej 6 MB (FW_MAX_OBRAZ_B).
     */
    suspend fun wgrajZdjecie(produktId: Int, uri: Uri, alt: String, pozycja: Int): Wynik<Obraz> =
        wywolaj {
            val plik = withContext(Dispatchers.IO) {
                val tymczasowy = File.createTempFile("zdjecie", ".jpg", kontekst.cacheDir)
                kontekst.contentResolver.openInputStream(uri).use { we ->
                    requireNotNull(we) { "Nie udało się otworzyć zdjęcia." }
                    tymczasowy.outputStream().use { wy -> we.copyTo(wy) }
                }
                tymczasowy
            }
            if (plik.length() > MAKS_ZDJECIE_B) {
                plik.delete()
                throw IOException(
                    "Zdjęcie waży ${plik.length() / 1_048_576} MB, a serwer przyjmuje do 6 MB. " +
                        "Zmniejsz je w galerii i spróbuj ponownie."
                )
            }
            val typ = kontekst.contentResolver.getType(uri) ?: "image/jpeg"
            val czesc = MultipartBody.Part.createFormData(
                "plik", plik.name, plik.asRequestBody(typ.toMediaType())
            )
            val odp = api.wgrajObraz(
                produktId = pole(produktId.toString()),
                alt = pole(alt),
                pozycja = pole(pozycja.toString()),
                plik = czesc,
            )
            plik.delete()
            odp.obraz ?: throw IOException("Serwer nie odesłał danych zdjęcia.")
        }

    /**
     * Zleca robotę komputerowi w warsztacie i czeka na wynik.
     *
     * Serwer liczy asynchronicznie: oddaje identyfikator od razu, a wynik
     * dopiero po chwili. Animacja na 12 GB VRAM potrafi liczyć się kilka
     * minut, więc odpytujemy co cztery sekundy i nie spieszymy się —
     * częstsze pytanie tylko zabiera komputerowi czas.
     *
     * Zwraca plik zapisany lokalnie, gotowy do wysłania do sklepu.
     */
    suspend fun zlecWarsztatowi(
        adres: String,
        zadanie: String,
        zdjecie: Uri,
        opis: String,
        postep: (String) -> Unit = {},
    ): Wynik<File> = wywolaj {
        val plikWejsciowy = withContext(Dispatchers.IO) {
            val tymczasowy = File.createTempFile("dowarsztatu", ".jpg", kontekst.cacheDir)
            kontekst.contentResolver.openInputStream(zdjecie).use { we ->
                requireNotNull(we) { "Nie udało się otworzyć zdjęcia." }
                tymczasowy.outputStream().use { wy -> we.copyTo(wy) }
            }
            tymczasowy
        }

        val czesc = MultipartBody.Part.createFormData(
            "plik", plikWejsciowy.name, plikWejsciowy.asRequestBody("image/jpeg".toMediaType())
        )
        val zlecenie = warsztat.zlec(
            adres = "$adres/zlec",
            zadanie = pole(zadanie),
            opis = pole(opis),
            plik = czesc,
        )
        plikWejsciowy.delete()

        if (!zlecenie.ok || zlecenie.id.isBlank()) {
            throw IOException(zlecenie.blad.ifBlank { "Serwer warsztatowy odrzucił zlecenie." })
        }

        // Limit trzydziestu minut jest hojny celowo: tyle bierze najdłuższa
        // animacja. Przekroczenie znaczy, że coś stanęło, a nie że trwa.
        val koniec = System.currentTimeMillis() + 30 * 60 * 1000
        var stan: StanZadania
        while (true) {
            delay(4000)
            stan = warsztat.zadanie("$adres/zadanie/${zlecenie.id}")
            postep(stan.stan)
            if (stan.gotowe) break
            stan.blad?.takeIf { it.isNotBlank() }?.let { throw IOException(it) }
            if (System.currentTimeMillis() > koniec) {
                throw IOException("Komputer nie skończył w pół godziny — sprawdź serwer.")
            }
        }

        val wynikUrl = stan.wynikUrl ?: throw IOException("Serwer nie oddał pliku wynikowego.")
        val cialo = warsztat.pobierz("$adres$wynikUrl")
        withContext(Dispatchers.IO) {
            val rozszerzenie = if (zadanie == "animacja") ".mp4" else ".png"
            val cel = File.createTempFile("zwarsztatu", rozszerzenie, kontekst.cacheDir)
            cel.outputStream().use { wy -> cialo.byteStream().use { we -> we.copyTo(wy) } }
            cel
        }
    }

    private fun pole(wartosc: String): RequestBody =
        wartosc.toRequestBody("text/plain".toMediaType())

    /**
     * Jedno miejsce na tłumaczenie awarii na zdanie po polsku.
     *
     * PHP przy błędzie oddaje kod HTTP i `{"blad": "..."}`. Retrofit rzuca wtedy
     * HttpException, w którym ta treść siedzi w ciele odpowiedzi — bez tego
     * użytkownik zobaczyłby samo "HTTP 422", co nie mówi nic.
     */
    private suspend fun <T> wywolaj(blok: suspend () -> T): Wynik<T> = try {
        Wynik.Jest(blok())
    } catch (e: HttpException) {
        Wynik.Blad(trescBledu(e))
    } catch (e: IOException) {
        Wynik.Blad(e.message ?: "Brak połączenia z serwerem.")
    } catch (e: Exception) {
        Wynik.Blad(e.message ?: "Coś poszło nie tak.")
    }

    private fun trescBledu(e: HttpException): String {
        val cialo = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        val zServera = cialo
            ?.let { runCatching { json.decodeFromString<OdpowiedzOgolna>(it).blad }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
        return zServera ?: when (e.code()) {
            401 -> "Sesja wygasła. Zaloguj się ponownie."
            403 -> "Brak uprawnień albo wygasła sesja. Zaloguj się ponownie."
            404 -> "Serwer nie zna tego adresu. Sprawdź, czy sklep jest wgrany."
            else -> "Serwer odpowiedział błędem ${e.code()}."
        }
    }

    private companion object {
        const val MAKS_ZDJECIE_B = 6L * 1024 * 1024
    }
}
