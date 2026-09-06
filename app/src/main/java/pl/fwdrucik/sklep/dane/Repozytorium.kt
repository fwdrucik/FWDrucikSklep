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
import pl.fwdrucik.sklep.siec.OdpowiedzZlecenia
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

    /**
     * Kto jestem według serwera — sonda dla kontrolki w Pomocy.
     *
     * `auth.php?akcja=ja` to najtańsze zapytanie w całym API: nie dotyka bazy
     * produktów, a odpowiada na oba pytania naraz — czy serwer żyje i czy
     * sesja jeszcze jest ważna.
     */
    suspend fun ktoJestem(): Wynik<Uzytkownik?> = wywolaj { api.ja().uzytkownik }

    suspend fun produkty(): Wynik<List<Produkt>> = wywolaj { api.produkty().produkty }

    suspend fun zapisz(
        p: Produkt,
        cenaZl: String,
        cenaPromoZl: String,
        allegroCenaZl: String = "",
    ): Wynik<OdpowiedzZapisu> =
        wywolaj {
            val allegroCenaWysylka = if (allegroCenaZl.isNotBlank()) {
                allegroCenaZl
            } else {
                p.allegroCenaGr?.let { groszeNaZlote(it).replace(" zł", "").trim() }.orEmpty()
            }
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
                allegroUrl = p.allegroUrl.orEmpty(),
                allegroCena = allegroCenaWysylka,
                allegroId = p.allegroId.orEmpty(),
                allegroStatus = p.allegroStatus,
                allegroKategoria = p.allegroKategoria.orEmpty(),
                allegroParametry = p.allegroParametry.orEmpty(),
            )
        }

    suspend fun ustawStatus(id: Int, status: String): Wynik<Unit> =
        wywolaj { api.ustawStatusProduktu(id, status); Unit }

    suspend fun usun(id: Int): Wynik<Unit> = wywolaj { api.usunProdukt(id); Unit }

    suspend fun usunObraz(id: Int): Wynik<Unit> = wywolaj { api.usunObraz(id); Unit }

    suspend fun usunPlik(id: Int): Wynik<Unit> = wywolaj { api.usunPlik(id); Unit }

    suspend fun synchronizujAukcjeAllegro(
        allegroUrl: String,
        allegroId: String,
        nazwa: String,
        cena: String,
        kategoria: String = "inne",
        opis: String = "",
        opisKrotki: String = "",
        stan: String = "1",
        jednostka: String = "szt.",
        czasRealizacji: String = "1-2 dni robocze",
    ): Wynik<OdpowiedzZapisu> = wywolaj {
        api.synchronizujAukcjeAllegro(
            allegroUrl = allegroUrl,
            allegroId = allegroId,
            nazwa = nazwa,
            cena = cena,
            kategoria = kategoria,
            opis = opis,
            opisKrotki = opisKrotki,
            stan = stan,
            jednostka = jednostka,
            czasRealizacji = czasRealizacji,
        )
    }

    suspend fun importujAukcjeAllegro(url: String): Wynik<OdpowiedzZapisu> = wywolaj {
        api.importujAukcjeAllegro(url = url)
    }

    suspend fun statusZamowienia(
        id: Int,
        status: String,
        przesylka: String = "",
    ): Wynik<Unit> = wywolaj { api.ustawStatusZamowienia(id, status, przesylka.trim()); Unit }

    suspend fun zamowienia(): Wynik<List<Zamowienie>> = wywolaj { api.zamowienia().zamowienia }

    /**
     * Wysyła zdjęcie wybrane w telefonie.
     *
     * Kopiujemy je najpierw do pliku tymczasowego, bo z `content://` nie da się
     * odczytać rozmiaru bez wczytania całości do pamięci, a serwer i tak odrzuca
     * pliki powyżej 6 MB (FW_MAX_OBRAZ_B).
     */
    /**
     * Film albo animacja do ogloszenia.
     *
     * POZYCJA 0 NIE JEST PRZYPADKIEM: material ruchomy ma stac przed zdjeciami.
     * Klient na telefonie widzi pierwszy ekran i nic wiecej — film schowany pod
     * galeria to film, ktorego nikt nie obejrzy.
     *
     * Rozszerzenie bierzemy z nazwy pliku, bo serwer po nim WSKAZUJE, czego
     * szukac w zawartosci (o przyjeciu decyduje sygnatura, nie nazwa).
     */
    suspend fun wgrajPlikProduktu(
        produktId: Int,
        uri: Uri,
        opis: String,
        pozycja: Int = 0,
    ): Wynik<PlikProduktu> = wywolaj {
        val nazwaZrodla = uri.lastPathSegment.orEmpty()
        val rozszerzenie = nazwaZrodla.substringAfterLast('.', "").lowercase()
            .takeIf { it.length in 2..5 } ?: "mp4"
        val typ = when (rozszerzenie) {
            "gif" -> "image/gif"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            else -> "video/mp4"
        }

        val plik = withContext(Dispatchers.IO) {
            val tymczasowy = File.createTempFile("material", ".$rozszerzenie", kontekst.cacheDir)
            kontekst.contentResolver.openInputStream(uri).use { we ->
                requireNotNull(we) { "Nie udało się otworzyć pliku." }
                tymczasowy.outputStream().use { wy -> we.copyTo(wy) }
            }
            tymczasowy
        }

        // Limity są takie same, jak po stronie serwera. Sprawdzamy je tutaj,
        // żeby nie wysyłać przez sieć komórkową pliku, który i tak wróci
        // błędem 422 po kilkudziesięciu sekundach.
        val limit = if (rozszerzenie == "gif") MAKS_ANIMACJA_B else MAKS_WIDEO_B
        if (plik.length() > limit) {
            val maB = plik.length() / 1_048_576
            plik.delete()
            throw IOException(
                "Materiał waży $maB MB, a serwer przyjmuje do ${limit / 1_048_576} MB."
            )
        }

        val czesc = MultipartBody.Part.createFormData(
            "plik", plik.name, plik.asRequestBody(typ.toMediaType())
        )
        try {
            val odp = api.wgrajPlik(
                produktId = pole(produktId.toString()),
                opis = pole(opis),
                pozycja = pole(pozycja.toString()),
                plik = czesc,
            )
            odp.plik ?: throw IOException("Serwer nie odesłał danych pliku.")
        } finally {
            plik.delete()
        }
    }

    suspend fun wgrajZdjecie(produktId: Int, uri: Uri, alt: String, pozycja: Int): Wynik<Obraz> =
        wywolaj {
            val sciezkaZrodla = uri.lastPathSegment.orEmpty()
            val rozszerzenie = sciezkaZrodla.substringAfterLast('.', "").lowercase()
                .takeIf { it in listOf("jpg", "jpeg", "png", "webp", "gif") } ?: "jpg"
            val plik = withContext(Dispatchers.IO) {
                if (rozszerzenie == "gif") {
                    val tymczasowy = File.createTempFile("animacja", ".gif", kontekst.cacheDir)
                    kontekst.contentResolver.openInputStream(uri).use { we ->
                        requireNotNull(we) { "Nie udało się otworzyć animacji GIF." }
                        tymczasowy.outputStream().use { wy -> we.copyTo(wy) }
                    }
                    tymczasowy
                } else {
                    val tymczasowy = File.createTempFile("zdjecie", ".jpg", kontekst.cacheDir)
                    val bitmap = kontekst.contentResolver.openInputStream(uri).use { we ->
                        android.graphics.BitmapFactory.decodeStream(we)
                    } ?: throw IOException("Nie udało się zdekodować zdjęcia.")

                    val maxWymiar = 1920
                    val szerokosc = bitmap.width
                    val wysokosc = bitmap.height
                    val przeskalowany = if (szerokosc > maxWymiar || wysokosc > maxWymiar) {
                        val skala = maxWymiar.toFloat() / maxOf(szerokosc, wysokosc)
                        android.graphics.Bitmap.createScaledBitmap(
                            bitmap,
                            (szerokosc * skala).toInt(),
                            (wysokosc * skala).toInt(),
                            true
                        )
                    } else {
                        bitmap
                    }

                    tymczasowy.outputStream().use { wy ->
                        przeskalowany.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, wy)
                    }
                    if (przeskalowany != bitmap) {
                        przeskalowany.recycle()
                    }
                    bitmap.recycle()
                    tymczasowy
                }
            }
            val typ = if (rozszerzenie == "gif") "image/gif" else "image/jpeg"
            val czesc = MultipartBody.Part.createFormData(
                "plik", plik.name, plik.asRequestBody(typ.toMediaType())
            )
            try {
                val odp = api.wgrajObraz(
                    produktId = pole(produktId.toString()),
                    alt = pole(alt),
                    pozycja = pole(pozycja.toString()),
                    plik = czesc,
                )
                odp.obraz ?: throw IOException("Serwer nie odesłał danych zdjęcia.")
            } finally {
                plik.delete()
            }
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
        proporcje: String = "16:9",
        /**
         * Skad wziac adres, gdy polaczenie padnie w trakcie liczenia.
         *
         * Telefon potrafi przejsc z komorkowej na Wi-Fi w polowie zlecenia —
         * wtedy dotychczasowe polaczenie umiera, a serwer liczy dalej.
         *
         * Stoi PRZED `postep`, zeby ten ostatni dalej dalo sie podac jako
         * lambda na koncu wywolania — inaczej kazde istniejace wywolanie
         * trzeba by przepisywac.
         */
        odswiezAdres: (suspend () -> String)? = null,
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

        val stanZadania = try {
            val czesc = MultipartBody.Part.createFormData(
                "plik", plikWejsciowy.name, plikWejsciowy.asRequestBody("image/jpeg".toMediaType())
            )
            // Automatyczny failover miedzy siecia lokalna a Tailscale
            var zlecenie: OdpowiedzZlecenia? = null
            var adresTeraz = adres
            val kandydaci = listOf(
                adres,
                if (adres.contains("100.84.198.20")) "http://192.168.0.166:8770" else "http://100.84.198.20:8770"
            ).distinct()

            var ostatniBladZlecenia: Exception? = null
            for (kandydat in kandydaci) {
                try {
                    val z = warsztat.zlec(
                        adres = "$kandydat/zlec",
                        zadanie = pole(zadanie),
                        opis = pole(opis),
                        proporcje = pole(
                            if (proporcje in listOf("16:9", "9:16", "1:1")) proporcje else "16:9"
                        ),
                        plik = czesc,
                    )
                    if (z.ok && z.id.isNotBlank()) {
                        zlecenie = z
                        adresTeraz = kandydat
                        break
                    }
                } catch (e: Exception) {
                    ostatniBladZlecenia = e
                }
            }

            val zlecenieGotowe = zlecenie ?: throw (ostatniBladZlecenia ?: IOException("Serwer warsztatowy odrzucił zlecenie."))

            // Limit trzydziestu minut jest hojny celowo: tyle bierze najdłuższa
            // animacja. Przekroczenie znaczy, że coś stanęło, a nie że trwa.
            val koniec = System.currentTimeMillis() + 30 * 60 * 1000
            var stan: StanZadania
            var bledySieci = 0
            while (true) {
                delay(4000)

                stan = try {
                    val s = warsztat.zadanie("$adresTeraz/zadanie/${zlecenieGotowe.id}")
                    bledySieci = 0
                    s
                } catch (e: IOException) {
                    bledySieci++
                    if (bledySieci >= 8) {
                        throw IOException(
                            "Zerwane połączenie z komputerem. Robota może być gotowa — " +
                                "sprawdź Studio, gdy sieć wróci."
                        )
                    }
                    postep("czekam na sieć ($bledySieci/8)")
                    odswiezAdres?.let { adresTeraz = it() }
                    continue
                }

                postep(stan.stan)
                if (stan.gotowe) break
                stan.blad?.takeIf { it.isNotBlank() }?.let { throw IOException(it) }
                if (System.currentTimeMillis() > koniec) {
                    throw IOException("Komputer nie skończył w pół godziny — sprawdź serwer.")
                }
            }
            stan
        } finally {
            plikWejsciowy.delete()
        }

        val wynikUrl = stanZadania.wynikUrl ?: throw IOException("Serwer nie oddał pliku wynikowego.")
        val adresWyniku = odswiezAdres?.invoke() ?: adres
        val cialo = warsztat.pobierz("$adresWyniku$wynikUrl")
        withContext(Dispatchers.IO) {
            // Po PREFIKSIE, nie po dokladnej nazwie. Wczesniej tylko „animacja"
            // dostawala .mp4, wiec film z Flow ladowal na dysku jako .png —
            // odtwarzacz go otwieral, ale udostepnienie i zapis w galerii
            // podawaly zly typ pliku.
            val rozszerzenie = if (zadanie.startsWith("animacja")) ".mp4" else ".png"
            val cel = File.createTempFile("zwarsztatu", rozszerzenie, kontekst.cacheDir)
            cel.outputStream().use { wy ->
                cialo.use { resBody ->
                    resBody.byteStream().use { we -> we.copyTo(wy) }
                }
            }
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

        // Te same granice, co w api/_pliki_produktu.php. Rozjazd w tych
        // liczbach znaczylby albo odrzucenie pliku po wyslaniu, albo
        // wyslanie 60 MB przez sim-kartke po nic.
        const val MAKS_WIDEO_B = 64L * 1024 * 1024
        const val MAKS_ANIMACJA_B = 16L * 1024 * 1024
    }
}
