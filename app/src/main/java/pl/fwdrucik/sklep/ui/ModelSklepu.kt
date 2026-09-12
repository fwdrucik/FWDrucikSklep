package pl.fwdrucik.sklep.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import pl.fwdrucik.sklep.siec.KatalogAi
import pl.fwdrucik.sklep.siec.DaneOpisuAi
import pl.fwdrucik.sklep.siec.OpisAi
import pl.fwdrucik.sklep.siec.BladAi
import pl.fwdrucik.sklep.siec.bladAi
import pl.fwdrucik.sklep.siec.diagnostykaAi
import pl.fwdrucik.sklep.siec.nazwaHistoriiAi
import pl.fwdrucik.sklep.SklepAplikacja
import pl.fwdrucik.sklep.dane.Produkt
import pl.fwdrucik.sklep.dane.SzkicProduktu
import pl.fwdrucik.sklep.siec.StanSerwera
import pl.fwdrucik.sklep.siec.PozycjaKolejki
import pl.fwdrucik.sklep.siec.OdpowiedzWyceny
import pl.fwdrucik.sklep.dane.Wynik
import pl.fwdrucik.sklep.dane.TrojkaFirebase
import pl.fwdrucik.sklep.dane.KopiaRobocza
import pl.fwdrucik.sklep.dane.MostChmury
import pl.fwdrucik.sklep.dane.Polecenia
import pl.fwdrucik.sklep.dane.bladPrywatnegoSzkicu
import pl.fwdrucik.sklep.dane.Poprawka
import pl.fwdrucik.sklep.dane.Zamowienie
import pl.fwdrucik.sklep.dane.ADRES_ZDALNY_WARSZTATU
import pl.fwdrucik.sklep.dane.DOMYSLNY_ADRES_WARSZTATU

/**
 * Stan całej aplikacji.
 *
 * Jeden model, a nie jeden na ekran: ekrany dzielą tę samą listę produktów
 * (katalog, magazyn i kreator to trzy widoki tych samych danych), więc osobne
 * modele musiałyby się między sobą synchronizować po każdym zapisie.
 */
data class StanEkranu(
    val ladowanie: Boolean = false,
    val zalogowany: Boolean = false,
    val produkty: List<Produkt> = emptyList(),
    val zamowienia: List<Zamowienie> = emptyList(),
    val blad: String? = null,
    val komunikat: String? = null,
    val kluczGemini: String = "",
    /** Agent pracuje — kreator blokuje przyciski, żeby nie wysłać zapytania dwa razy. */
    val agentPracuje: Boolean = false,
    val adresWarsztatu: String = "",
    /** Kontrolka serwera warsztatowego: czerwony / zolty / zielony / brak. */
    val swiatloWarsztatu: String = "brak",
    val opisWarsztatu: String = "Szukam komputera...",
    /** Czy serwer warsztatowy dosiega CLI Muse — drugi silnik opisow. */
    val museWarsztatu: String = "",
    /** ETAP77: dostep do wspolnej bazy z panelem warsztatowym. */
    val chmura: TrojkaFirebase = TrojkaFirebase(),
    /** Kontrolki trzech pozostalych uslug — sprawdzane na zadanie, w Pomocy. */
    val stanApi: StanUslugi = StanUslugi(),
    val stanGemini: StanUslugi = StanUslugi(),
    val stanChmury: StanUslugi = StanUslugi(),
    /** Pamiec robocza kreatora: id produktu (0 = nowy) -> to, co bylo wpisane. */
    val kopieRobocze: Map<Int, KopiaRobocza> = emptyMap(),
    val kopieWczytane: Boolean = false,
    val katalogAi: KatalogAi = KatalogAi(),
    val katalogWToku: Boolean = false,
    val bladKatalogu: String? = null,
    /** Wybrane modele Gemini i lista do wyboru, pobierana z konta przy sprawdzeniu klucza. */
    val modelOpisu: String = "",
    val modelObrazu: String = "",
    val modeleTekstowe: List<String> = emptyList(),
    val modeleObrazowe: List<String> = emptyList(),
    /** Czym robic ktore zadanie: opis (gemini/muse), zdjecie (gemini/forge), animacja (comfy/flow). */
    val silnikOpisu: String = "gemini",
    val silnikZdjecia: String = "gemini",
    val silnikAnimacji: String = "comfy",
    /** Co agent robil, od najnowszego. Sluzy do zajrzenia, a nie do audytu — trzymamy ostatnie 30. */
    val czynnosci: List<Czynnosc> = emptyList(),
    /** Czego agent nauczyl sie z poprawek wlasciciela. */
    val poprawki: List<Poprawka> = emptyList(),
    /** Ostatnia propozycja agenta — do porownania z tym, co czlowiek zapisal. */
    val propozycjaAgenta: SzkicProduktu? = null,
    /** Co zywe po stronie komputera: „dziala"/„wylaczony" prosto z /stan. */
    val stanForge: String = "",
    val stanComfy: String = "",
    val stanFlow: String = "",
    val stanMeta: String = "",
    val stanCopilot: String = "",
    /** Kolejka do karty, w kolejnosci ustalonej przez agregator na serwerze. */
    val kolejkaWarsztatu: List<PozycjaKolejki> = emptyList(),
    /** Ile zlecen serwer wlasnie liczy — 0 albo 1, bo silniki sa wylaczne. */
    val zadaniaWToku: Int = 0,
    /** Id aktualnie otwartego kreatora (0 = nowy, >0 = istniejacy, -1 = zamkniety). */
    val aktywnyKreatorId: Int = -1,
    val sugerowanaCenaRynkowa: Double? = null,
    val sugerowanaCenaAllegro: Double? = null,
    val minCenaRynkowa: Double? = null,
    val maxCenaRynkowa: Double? = null,
    val ofertyRynkowe: List<pl.fwdrucik.sklep.siec.OfertaCenowa> = emptyList(),
    val badanieCenyWToku: Boolean = false,
    val bladWyceny: String? = null,
    val wyszukiwanieKategorii: StanWyszukiwaniaKategorii = StanWyszukiwaniaKategorii(),
    /** Wynik przeszedł walidację dowodów; użycie ceny nadal wymaga dotknięcia. */
    val wycenaZmierzona: Boolean = false,
    /** Ostrzeżenie z serwera, także dla poprawnej rekomendacji. */
    val ostrzezenieWyceny: String? = null,
    val sprawdzonoWyceny: String = "",
) {
    internal fun rozpocznijBadanieCeny(): StanEkranu = copy(
        badanieCenyWToku = true, komunikat = "Sprawdzam ceny w internecie...",
        bladWyceny = null, sugerowanaCenaRynkowa = null, sugerowanaCenaAllegro = null,
        minCenaRynkowa = null, maxCenaRynkowa = null, ofertyRynkowe = emptyList(),
        wycenaZmierzona = false, ostrzezenieWyceny = null, sprawdzonoWyceny = "",
    )

    internal fun zWynikiemWyceny(odp: OdpowiedzWyceny): StanEkranu {
        val baza = rozpocznijBadanieCeny().copy(
            badanieCenyWToku = false, komunikat = null,
            ofertyRynkowe = odp.znalezione, sprawdzonoWyceny = odp.sprawdzono,
            ostrzezenieWyceny = odp.ostrzezenie?.takeIf { it.isNotBlank() }
                ?: if (odp.zrodlo == "tavily-chatgpt")
                    "Wyniki wyszukiwania to ceny ofertowe, nie ceny sprzedaży. Sprawdź porównywalność wyrobów."
                else null,
        )
        if (!odp.zmierzoneNaRynku) {
            val opis = odp.blad?.takeIf { it.isNotBlank() }
                .orEmpty().ifBlank { "Brak wystarczających, poprawnych źródeł do rekomendacji ceny." }
            val powod = if (opis.contains("Wpisz cenę ręcznie", ignoreCase = true)) opis else
                opis + " Wpisz cenę ręcznie. Twoja dotychczasowa cena nie została zmieniona."
            // The price section owns this error. Do not duplicate it in the global
            // banner or overwrite an independent product-save failure.
            return baza.copy(bladWyceny = powod)
        }
        return baza.copy(
            sugerowanaCenaRynkowa = odp.sugerowanaCena,
            sugerowanaCenaAllegro = odp.sugerowanaAllegro.takeIf { it.isFinite() && it > 0 },
            minCenaRynkowa = odp.minCena.takeIf { it.isFinite() && it > 0 },
            maxCenaRynkowa = odp.maxCena.takeIf { it.isFinite() && it > 0 },
            wycenaZmierzona = true,
            komunikat = "Propozycja z ${odp.liczbaOfert} ofert w internecie: ${odp.sugerowanaCena} zł " +
                "— sprawdź porównywalność wyrobów przed zastosowaniem ceny.",
        )
    }

    /** Czyścimy sesję i jej zawartość; adres komputera jest zwykłą konfiguracją. */
    internal fun poWylogowaniu(): StanEkranu = StanEkranu(
        zalogowany = false,
        adresWarsztatu = adresWarsztatu,
    )

    /** Pierwsza kontrola nie musi czekać na emisję ustawień z DataStore. */
    internal fun adresDoKontrolki(adresUstawiony: String): String =
        adresWarsztatu.ifBlank { adresUstawiony.ifBlank { DOMYSLNY_ADRES_WARSZTATU } }
}

/**
 * Jedna czynnosc agenta albo komputera — do podejrzenia po fakcie.
 *
 * PO CO: robota idzie w tle i trwa minutami. Bez sladu po niej zostaje tylko
 * wrazenie „nic sie nie stalo" — nawet wtedy, gdy plik lezy gotowy na dysku.
 * Dziennik mowi wprost: co, czym, kiedy i gdzie wyladowal wynik.
 */
data class Czynnosc(
    val co: String,
    val silnik: String,
    val wynik: String,
    /** Sciezka albo adres wyniku — po to, zeby dalo sie go otworzyc z listy. */
    val plik: String = "",
    val udana: Boolean = true,
    val czas: Long = System.currentTimeMillis(),
)

/** Wynik odczytu kategorii; wybór użytkownika jest przechowywany osobno w kopii. */
data class StanWyszukiwaniaKategorii(
    val fraza: String = "",
    val wToku: Boolean = false,
    val sprawdzono: Boolean = false,
    val kategorie: List<pl.fwdrucik.sklep.siec.KategoriaAllegro> = emptyList(),
    val blad: String? = null,
)

/**
 * Stan jednej uslugi pod kontrolke.
 *
 * `opis` niesie tu wiecej niz kolor: „zly klucz", „wygasla sesja" i „brak sieci"
 * to trzy rozne ruchy do wykonania, a wszystkie swiecilyby na czerwono.
 */
data class StanUslugi(
    val kolor: String = "brak",
    val opis: String = "Nie sprawdzane",
)

class ModelSklepu(aplikacja: Application) : AndroidViewModel(aplikacja) {

    private val repozytorium = SklepAplikacja.z(aplikacja).repozytorium
    private val agent = SklepAplikacja.z(aplikacja).agent
    private val ustawienia = SklepAplikacja.z(aplikacja).ustawienia
    private val warsztat = SklepAplikacja.z(aplikacja).warsztat
    private val gemini = SklepAplikacja.z(aplikacja).gemini

    private val _stan = MutableStateFlow(StanEkranu(zalogowany = repozytorium.czyZalogowany()))
    val stan: StateFlow<StanEkranu> = _stan.asStateFlow()

    /**
     * Adres wpisany w ustawieniach, osobny od ostatniego działającego adresu.
     * Przełączenie na Tailscale nie nadpisuje ustawienia użytkownika.
     * Musi być zainicjalizowany przed init: kontrolka może ruszyć od razu.
     */
    private var adresUstawiony: String = ""

    init {
        if (_stan.value.zalogowany) odswiez()
        viewModelScope.launch {
            ustawienia.kluczGemini.collect { klucz ->
                _stan.update { it.copy(kluczGemini = klucz) }
            }
        }
        viewModelScope.launch {
            ustawienia.adresWarsztatu.collect { adres ->
                adresUstawiony = adres
                _stan.update { it.copy(adresWarsztatu = adres) }
            }
        }
        viewModelScope.launch {
            ustawienia.chmura.collect { cfg ->
                _stan.update { it.copy(chmura = cfg) }
            }
        }
        viewModelScope.launch {
            ustawienia.kopieRobocze.collect { kopie ->
                _stan.update { it.copy(kopieRobocze = kopie, kopieWczytane = true) }
            }
        }
        viewModelScope.launch {
            ustawienia.modelOpisu.collect { m -> _stan.update { it.copy(modelOpisu = m) } }
        }
        viewModelScope.launch {
            ustawienia.modelObrazu.collect { m -> _stan.update { it.copy(modelObrazu = m) } }
        }
        viewModelScope.launch {
            ustawienia.silnikOpisu.collect { v -> _stan.update { it.copy(silnikOpisu = v) } }
        }
        viewModelScope.launch {
            ustawienia.silnikZdjecia.collect { v -> _stan.update { it.copy(silnikZdjecia = v) } }
        }
        viewModelScope.launch {
            ustawienia.silnikAnimacji.collect { v -> _stan.update { it.copy(silnikAnimacji = v) } }
        }
        viewModelScope.launch {
            ustawienia.poprawki.collect { p -> _stan.update { it.copy(poprawki = p) } }
        }
        viewModelScope.launch {
            ustawienia.aktywnyKreatorId.collect { id -> _stan.update { it.copy(aktywnyKreatorId = id) } }
        }
        pilnujKontrolki()
    }

    /** Dopisuje czynnosc do dziennika. Najnowsza na gorze, starsze wypadaja. */
    private fun dopiszCzynnosc(co: String, silnik: String, wynik: String, plik: String = "", udana: Boolean = true) {
        _stan.update {
            it.copy(czynnosci = (listOf(Czynnosc(co, silnik, wynik, plik, udana)) + it.czynnosci).take(30))
        }
    }

    // ------------------------------------------------- pamiec robocza
    fun odswiezKatalogAi() {
        if (_stan.value.katalogWToku) return
        _stan.update { it.copy(katalogWToku = true, bladKatalogu = null) }
        viewModelScope.launch {
            try {
                val katalog = repozytorium.modeleAi(adresRoboczy())
                _stan.update { it.copy(katalogAi = katalog) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("FW_AI", diagnostykaAi("modele", e))
                _stan.update { it.copy(katalogAi = KatalogAi(), bladKatalogu = bladAi(e).message) }
            } finally {
                _stan.update { it.copy(katalogWToku = false) }
            }
        }
    }

    /** Konkretny wybór sprawdzamy ponownie przed wysłaniem; nie podmieniamy go po cichu. */
    private suspend fun sprawdzModelAi(adres: String, id: String, rodzaj: String) {
        if (id == "auto" || id.isBlank()) return
        val katalog = repozytorium.modeleAi(adres)
        _stan.update { it.copy(katalogAi = katalog) }
        require(katalog.moznaWybrac(id, rodzaj)) {
            "Wybrany model jest teraz niedostępny do tego zadania. Wybierz inny model lub Automatycznie."
        }
    }

    fun napiszOpisAi(zdjecie: Uri?, dane: DaneOpisuAi, gotowe: (OpisAi) -> Unit) {
        if (_stan.value.agentPracuje) return
        _stan.update { it.copy(agentPracuje = true, blad = null, komunikat = "Układam opis po polsku…") }
        viewModelScope.launch {
            try {
                val adres = adresRoboczy()
                sprawdzModelAi(adres, dane.model, "tekst")
                if (zdjecie != null && dane.model == "auto") {
                    val aktualny = repozytorium.modeleAi(adres)
                    _stan.update { it.copy(katalogAi = aktualny) }
                }
                val katalog = _stan.value.katalogAi
                katalog.powodBlokadyOpisu(dane, zdjecie != null)?.let { throw BladAi(it) }
                val tylkoSlowa = zdjecie != null && !katalog.czyCzytaZdjecie(dane.model)
                val opis = repozytorium.opisAi(adres, if (tylkoSlowa) null else zdjecie, dane)
                gotowe(if (tylkoSlowa) opis.copy(ostrzezenie = listOf("Ten model ułożył opis tylko z podanych słów. Nie oglądał zdjęcia.",
                    opis.ostrzezenie.orEmpty()).filter { it.isNotBlank() }.joinToString(" ")) else opis)
                _stan.update { it.copy(propozycjaAgenta = SzkicProduktu(
                    nazwa = opis.nazwa, opisKrotki = opis.opisKrotki, opis = opis.opis,
                    kategoria = opis.kategoria ?: "inne", doUzupelnienia = opis.doUzupelnienia,
                )) }
                dopiszCzynnosc("Propozycja opisu", opis.model, "Do sprawdzenia")
                _stan.update { it.copy(komunikat = "Opis gotowy. Sprawdź, czy zgadza się z wyrobem.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("FW_AI", diagnostykaAi("opis", e))
                _stan.update { it.copy(blad = bladAi(e).message) }
            } finally {
                _stan.update { it.copy(agentPracuje = false) }
            }
        }
    }

    //
    // Zapis idzie z opoznieniem, nie po kazdej literze: DataStore pisze na dysk,
    // a kreator ma kilkanascie pol. Sekunda ciszy w pisaniu to naturalny moment
    // na zapis i nie widac go na ekranie.

    private var zapisKopii: kotlinx.coroutines.Job? = null
    // Zapis szkicu kończy się również po zamknięciu Activity.
    private val zakresZapisu = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    fun ustawAktywnyKreator(id: Int) {
        viewModelScope.launch {
            android.util.Log.d("FW_KREATOR", "ustawAktywnyKreator id=$id")
            ustawienia.zapiszAktywnyKreatorId(id)
        }
    }

    fun zamknijKreator() {
        viewModelScope.launch {
            android.util.Log.d("FW_KREATOR", "zamknijKreator -> -1")
            ustawienia.zapiszAktywnyKreatorId(-1)
        }
    }

    fun zapiszKopie(kopia: KopiaRobocza) {
        if (kopia.pusta && kopia.id == 0 && !_stan.value.kopieRobocze.containsKey(0)) return
        val poprzedni = zapisKopii
        zapisKopii?.cancel()
        zapisKopii = zakresZapisu.launch {
            poprzedni?.join()
            delay(500)
            try {
            ustawienia.zapiszKopie(
                kopia.copy(
                    zdjecie = zachowajZdjecie(kopia.id, kopia.zdjecie),
                    dodatkoweKadry = kopia.dodatkoweKadry.map { zachowajZdjecie(kopia.id, it) },
                    animacja = zachowajZdjecie(kopia.id, kopia.animacja),
                    kadrDoAkceptacji = zachowajZdjecie(kopia.id, kopia.kadrDoAkceptacji),
                    filmDoAkceptacji = zachowajZdjecie(kopia.id, kopia.filmDoAkceptacji),
                    zapisano = System.currentTimeMillis(),
                )
            )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _stan.update { it.copy(blad = "Nie udało się zachować szkicu na telefonie. Sprawdź wolne miejsce i spróbuj ponownie.") }
            }
        }
    }

    fun zachowajIZamknij(gotowe: () -> Unit) {
        viewModelScope.launch {
            zapisKopii?.join()
            ustawienia.zapiszAktywnyKreatorId(-1)
            gotowe()
        }
    }

    /**
     * Wciaga zdjecie z galerii do pamieci aplikacji, zeby przezylo restart.
     *
     * Adres `content://` dziala tylko z uprawnieniem nadanym przy wyborze pliku
     * i po ponownym uruchomieniu aplikacji prowadzi donikad — kopia robocza ze
     * sciezka do galerii wygladalaby jak zachowana, a pokazywalaby pusty kadr.
     * Kopiujemy raz: sciezka wskazujaca juz na nasz katalog wraca bez zmian.
     */
    private suspend fun zachowajZdjecie(id: Int, zrodlo: String): String =
        withContext(Dispatchers.IO) {
            if (zrodlo.isBlank()) return@withContext ""
            val katalog = java.io.File(getApplication<Application>().filesDir, "kopie")
            val uri = if (zrodlo.startsWith("/")) Uri.fromFile(java.io.File(zrodlo)) else Uri.parse(zrodlo)
            if (uri.scheme == "file") {
                val lokalny = java.io.File(uri.path.orEmpty())
                val prywatny = getApplication<Application>().filesDir.canonicalPath + java.io.File.separator
                if (lokalny.exists() && lokalny.canonicalPath.startsWith(prywatny)) return@withContext lokalny.absolutePath
            }

            runCatching {
                katalog.mkdirs()
                val rozszerzenie = uri.lastPathSegment?.substringAfterLast('.', "jpg")
                    ?.takeIf { it in listOf("jpg", "jpeg", "png", "webp", "gif", "mp4", "webm", "mov") } ?: "jpg"
                val cel = java.io.File.createTempFile("kopia_${id}_", ".$rozszerzenie", katalog)
                getApplication<Application>().contentResolver
                    .openInputStream(uri)!!
                    .use { we -> cel.outputStream().use { wy -> we.copyTo(wy) } }
                cel.absolutePath
            }.getOrDefault(zrodlo)
        }

    /** Po udanym zapisie na serwer kopia nie ma juz czego pilnowac. */
    fun skasujKopie(id: Int) {
        viewModelScope.launch { usunKopiePoZapisie(id) }
    }

    private suspend fun usunKopiePoZapisie(id: Int) {
        zapisKopii?.cancel()
        zapisKopii?.join()
        _stan.value.kopieRobocze[id]?.zdjecie?.takeIf { it.isNotBlank() }?.let { sciezka ->
            runCatching {
                val plik = java.io.File(sciezka)
                val katalog = java.io.File(getApplication<Application>().filesDir, "kopie").canonicalPath + java.io.File.separator
                val wspoldzielony = _stan.value.kopieRobocze.any { (innyId, kopia) -> innyId != id &&
                    (kopia.zdjecie == sciezka || sciezka in kopia.dodatkoweKadry || kopia.animacja == sciezka) }
                if (plik.canonicalPath.startsWith(katalog) && !wspoldzielony) plik.delete()
            }
        }
        ustawienia.skasujKopie(id)
        _stan.update { it.copy(kopieRobocze = it.kopieRobocze - id) }
        if (_stan.value.aktywnyKreatorId == id) ustawienia.zapiszAktywnyKreatorId(-1)
    }

    fun zbadajCeneRynkowa(
        fraza: String,
        kategoria: String = "",
        naWynik: (Double, Double, Double, Double) -> Unit = { _, _, _, _ -> }
    ) {
        if (fraza.isBlank() || _stan.value.badanieCenyWToku) return
        viewModelScope.launch {
            _stan.update { it.rozpocznijBadanieCeny() }
            try {
                val adres = adresRoboczy()
                val wynik = repozytorium.zbadajCeneRynkowa(adres, fraza, kategoria)
                when (wynik) {
                    is Wynik.Jest -> {
                        val odp = wynik.dane
                        _stan.update { it.zWynikiemWyceny(odp) }
                        if (!odp.zmierzoneNaRynku) return@launch
                        naWynik(odp.sugerowanaCena, odp.sugerowanaAllegro, odp.minCena, odp.maxCena)
                    }
                    is Wynik.Blad -> {
                        _stan.update {
                            it.copy(
                                badanieCenyWToku = false,
                                bladWyceny = "Nie udało się sprawdzić cen: ${wynik.komunikat}. Spróbuj ponownie później.",
                                blad = "Błąd badania cen: ${wynik.komunikat}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _stan.update { it.copy(badanieCenyWToku = false, bladWyceny = "Nie udało się połączyć podczas sprawdzania cen. Spróbuj ponownie później.", blad = "Błąd połączenia: ${e.message}") }
            }
        }
    }

    fun znajdzKategorieAllegro(fraza: String) {
        if (_stan.value.wyszukiwanieKategorii.wToku) return
        val szukana = fraza.trim()
        if (szukana.length !in 2..100) {
            _stan.update { it.copy(wyszukiwanieKategorii = StanWyszukiwaniaKategorii(fraza = szukana, sprawdzono = true,
                blad = "Wpisz od 2 do 100 znaków nazwy wyrobu.")) }
            return
        }
        _stan.update { it.copy(wyszukiwanieKategorii = StanWyszukiwaniaKategorii(fraza = szukana, wToku = true)) }
        viewModelScope.launch {
            val wynik = try {
                repozytorium.kategorieAllegro(adresRoboczy(), szukana)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Wynik.Blad("Nie udało się połączyć z warsztatem. Spróbuj ponownie.")
            }
            val nowy = when (wynik) {
                is Wynik.Jest -> StanWyszukiwaniaKategorii(fraza = szukana, sprawdzono = true, kategorie = wynik.dane)
                is Wynik.Blad -> StanWyszukiwaniaKategorii(fraza = szukana, sprawdzono = true,
                    blad = "Nie udało się znaleźć kategorii: ${wynik.komunikat} Możesz zmienić nazwę i spróbować ponownie.")
            }
            _stan.update { it.copy(wyszukiwanieKategorii = nowy) }
        }
    }

    fun utworzSzkicAllegro(
        tytul: String,
        kategoriaId: String,
        cenaPln: Double,
        opisTekst: String,
        zdjecia: String = "",
        stanSztuk: Int = 1,
        naKoniec: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        if (_stan.value.ladowanie) return
        viewModelScope.launch {
            _stan.update { it.copy(ladowanie = true, komunikat = "Tworzę prywatny szkic na Allegro...") }
            try {
                val adres = adresRoboczy()
                val wynik = repozytorium.utworzSzkicAllegro(
                    adres = adres,
                    tytul = tytul,
                    kategoriaId = kategoriaId,
                    cenaPln = cenaPln,
                    opisTekst = opisTekst,
                    zdjecia = zdjecia,
                    stanSztuk = stanSztuk
                )
                when (wynik) {
                    is Wynik.Jest -> {
                        val odp = wynik.dane
                        val bladSzkicu = odp.bladPrywatnegoSzkicu()
                        if (bladSzkicu != null) {
                            _stan.update { it.copy(ladowanie = false, blad = bladSzkicu, komunikat = null) }
                            naKoniec(false, null)
                            return@launch
                        }
                        _stan.update {
                            it.copy(
                                ladowanie = false,
                                komunikat = "Utworzono prywatny szkic na Allegro!"
                            )
                        }
                        naKoniec(true, odp.url?.takeIf { it.isNotBlank() }
                            ?: odp.id?.takeIf { it.all(Char::isDigit) }?.let { "https://allegro.pl/oferta/$it" })
                    }
                    is Wynik.Blad -> {
                        _stan.update {
                            it.copy(
                                ladowanie = false,
                                blad = "Błąd tworzenia szkicu Allegro: ${wynik.komunikat}"
                            )
                        }
                        naKoniec(false, null)
                    }
                }
            } catch (e: Exception) {
                _stan.update { it.copy(ladowanie = false, blad = "Błąd połączenia: ${e.message}") }
                naKoniec(false, null)
            }
        }
    }

    // ------------------------------------------------- kontrolki uslug
    //
    // Sprawdzamy na zadanie, a nie w kolko. Kazda z tych sond kosztuje: zapytanie
    // do fwdrucik.pl, zapytanie do Google, a przy chmurze nawet zalozenie sesji
    // anonimowej. Kontrolka warsztatu chodzi sama, bo tam odpowiedz zmienia sie
    // co chwile — tutaj stan potrafi staly przez tygodnie.

    fun sprawdzApi() {
        _stan.update { it.copy(stanApi = StanUslugi("zolty", "Sprawdzam...")) }
        viewModelScope.launch {
            val wynik = repozytorium.ktoJestem()
            _stan.update {
                it.copy(
                    stanApi = when (wynik) {
                        is Wynik.Jest -> wynik.dane?.let { u ->
                            StanUslugi("zielony", "Zalogowany jako ${u.nick} (${u.rola})")
                        } ?: StanUslugi(
                            "czerwony",
                            "Serwer odpowiada, ale sesja wygasla — zaloguj sie ponownie.",
                        )
                        is Wynik.Blad -> StanUslugi("czerwony", wynik.komunikat)
                    }
                )
            }
        }
    }

    fun sprawdzGemini() {
        val klucz = _stan.value.kluczGemini
        if (klucz.isBlank()) {
            _stan.update { it.copy(stanGemini = StanUslugi("brak", "Klucz nie wpisany.")) }
            return
        }
        _stan.update { it.copy(stanGemini = StanUslugi("zolty", "Sprawdzam...")) }
        viewModelScope.launch {
            val stanUslugi = try {
                val nazwy = gemini.modele(klucz).models.map { it.name.removePrefix("models/") }

                // Przy okazji sprawdzenia klucza bierzemy liste modeli — to ten sam,
                // darmowy strzal. Nazwy u Google zmieniaja sie co kilka tygodni, wiec
                // lista jest pobierana, a nie wpisana w kod.
                val tekstowe = nazwy.filter { n ->
                    ("flash" in n || "pro" in n) &&
                        listOf("image", "tts", "embedding", "vision", "customtools", "banana")
                            .none { it in n }
                }
                val obrazowe = nazwy.filter { "image" in it || "banana" in it }
                _stan.update {
                    it.copy(modeleTekstowe = tekstowe.sorted(), modeleObrazowe = obrazowe.sorted())
                }
                StanUslugi("zielony", "Klucz dziala, modeli: ${nazwy.size}")
            } catch (e: retrofit2.HttpException) {
                // 400 i 403 to najczestsze odpowiedzi Google na zly albo
                // nieuprawniony klucz — warto powiedziec to wprost, bo brzmia
                // tak samo jak awaria sieci, a wymagaja czego innego.
                val powod = when (e.code()) {
                    400 -> "Klucz odrzucony (400) — sprawdz, czy nie ma spacji na koncu."
                    403 -> "Klucz bez uprawnien (403) — wlacz Generative Language API."
                    429 -> "Limit wyczerpany (429) — klucz dziala, ale nie teraz."
                    else -> "Google odpowiedzial bledem ${e.code()}."
                }
                StanUslugi("czerwony", powod)
            } catch (e: Exception) {
                StanUslugi("czerwony", e.message?.take(120) ?: "Brak lacznosci z Google.")
            }
            _stan.update { it.copy(stanGemini = stanUslugi) }
        }
    }

    // ------------------------------------------------- tryb auto
    //
    // Reguly sa proste i wynikaja z tego, co ktora droga naprawde potrafi:
    //
    //   opis     — Gemini, bo jako jedyny WIDZI zdjecie. Bez klucza albo po
    //              jego odrzuceniu zostaje Muse, ale wtedy potrzebna jest notatka.
    //   zdjecie  — Gemini bywa szybsze, ale jego limit obrazow konczy sie
    //              najszybciej ze wszystkiego (429). Gdy Forge stoi rozgrzany,
    //              jest pewniejszy i nic nie kosztuje.
    //   animacja — Flow daje 8 s z dzwiekiem za punkty z abonamentu, karta daje
    //              5 s za darmo. Przy zywej karcie wybieramy karte.
    //
    // Kazdy wybor trafia do dziennika, zeby dalo sie sprawdzic, czemu poszlo
    // tak, a nie inaczej.

    private fun wybierzSilnikOpisu(maZdjecie: Boolean, maNotatke: Boolean): String {
        val stan = _stan.value
        val geminiZdrowe = stan.kluczGemini.isNotBlank() && stan.stanGemini.kolor != "czerwony"
        return when {
            geminiZdrowe && maZdjecie -> "gemini"
            stan.museWarsztatu == "dostepne" && maNotatke -> "muse"
            geminiZdrowe -> "gemini"
            else -> "muse"
        }
    }

    private fun wybierzSilnikZdjecia(): String {
        val stan = _stan.value
        val geminiZdrowe = stan.kluczGemini.isNotBlank() && stan.stanGemini.kolor != "czerwony"
        return when {
            // Komputer pierwszy: wycina tlo maska, wiec robi to, o co chodzi,
            // a nie to, co model uzna za poprawe. I nie ma limitu.
            stan.stanForge == "dziala" -> "forge"
            // Flow przed Meta AI, bo jest szybszy: kadr schodzi w kilkanascie
            // sekund, a przez czat Mety trwa to okolo minuty. Oba sa za darmo.
            //
            // ALE: Flow oddaje wylacznie kwadrat 1024x1024 — zmierzone, wybor
            // 16:9 w panelu nie zmienia wyniku przy poprawianiu zdjecia.
            // Kto potrzebuje kadru poziomego albo pionowego, wybiera Meta AI
            // recznie; automat nie zna przeznaczenia zdjecia, wiec bierze
            // szybsza droge.
            stan.stanFlow == "dziala" -> "flow"
            stan.stanMeta == "dziala" -> "meta"
            geminiZdrowe -> "gemini"
            stan.adresWarsztatu.isNotBlank() -> "forge"
            else -> "gemini"
        }
    }

    private fun wybierzSilnikAnimacji(): String {
        val stan = _stan.value
        return when {
            stan.stanComfy == "dziala" -> "comfy"
            stan.stanFlow == "dziala" -> "flow"
            stan.stanMeta == "dziala" -> "meta"
            stan.adresWarsztatu.isNotBlank() -> "comfy"
            else -> "flow"
        }
    }

    /** Silnik do zadania z uwzglednieniem trybu auto. Publiczne, bo pyta o to takze kreator. */
    fun silnikDo(zadanie: String, maZdjecie: Boolean = true, maNotatke: Boolean = true): String {
        val wybrany = when (zadanie) {
            "opis" -> _stan.value.silnikOpisu
            "zdjecie" -> _stan.value.silnikZdjecia
            else -> _stan.value.silnikAnimacji
        }
        if (wybrany != "auto") return wybrany
        return when (zadanie) {
            "opis" -> wybierzSilnikOpisu(maZdjecie, maNotatke)
            "zdjecie" -> wybierzSilnikZdjecia()
            else -> wybierzSilnikAnimacji()
        }
    }

    fun zapiszSilnik(zadanie: String, silnik: String) {
        viewModelScope.launch { ustawienia.zapiszSilnik(zadanie, silnik) }
    }

    /**
     * Poprawia opis, ktory juz jest w polach kreatora.
     *
     * Idzie tym silnikiem, ktory wybrales w Pomocy — bez zdjecia, wiec Muse
     * radzi sobie tu tak samo dobrze jak Gemini i nie kosztuje limitu.
     */
    fun poprawOpis(
        nazwa: String,
        opisKrotki: String,
        opis: String,
        cena: String = "",
        wybor: pl.fwdrucik.sklep.ui.ekrany.WyborRoboty,
        gotowe: (SzkicProduktu) -> Unit,
    ) {
        val silnikWybrany = wybor.silnik
        if (listOf(nazwa, opisKrotki, opis).all { it.isBlank() }) {
            _stan.update { it.copy(blad = "Nie ma czego poprawiac — najpierw wpisz cokolwiek.") }
            return
        }
        viewModelScope.launch {
            _stan.update { it.copy(agentPracuje = true) }
            try {
                val wybrany = silnikWybrany?.takeIf { it != "auto" }
                    ?: silnikDo("opis", maZdjecie = false, maNotatke = true)
                val szkic = agent.poprawOpis(
                    silnik = wybrany,
                    klucz = _stan.value.kluczGemini,
                    adresWarsztatu = _stan.value.adresWarsztatu,
                    model = wybor.model.ifBlank { _stan.value.modelOpisu },
                    nazwa = nazwa, opisKrotki = opisKrotki, opis = opis,
                    cena = cena,
                    dodatkowe = wybor.dodatkowe,
                )
                gotowe(szkic)
                // Tez jest propozycja agenta — jesli czlowiek jeszcze ja poprawi
                // przed zapisem, roznica bedzie kolejna lekcja.
                _stan.update { it.copy(propozycjaAgenta = szkic) }
                dopiszCzynnosc(
                    co = "Poprawa opisu",
                    silnik = if (wybrany == "muse") "Muse" else if (wybrany == "copilot") "Copilot" else _stan.value.modelOpisu,
                    wynik = szkic.nazwa.ifBlank { "poprawiony" },
                )
                _stan.update { it.copy(komunikat = "Opis poprawiony — porownaj przed zapisem") }
            } catch (e: Exception) {
                dopiszCzynnosc("Poprawa opisu", silnikWybrany ?: _stan.value.silnikOpisu, e.message?.take(90).orEmpty(), udana = false)
                _stan.update { it.copy(blad = opisBledu(e)) }
            } finally {
                _stan.update { it.copy(agentPracuje = false) }
            }
        }
    }

    fun zapiszModele(opisu: String, obrazu: String) {
        viewModelScope.launch {
            ustawienia.zapiszModele(opisu, obrazu)
            _stan.update { it.copy(komunikat = "Model zapisany: $opisu") }
        }
    }

    fun sprawdzChmure() {
        val cfg = _stan.value.chmura
        _stan.update { it.copy(stanChmury = StanUslugi("zolty", "Sprawdzam...")) }
        viewModelScope.launch {
            val blad = MostChmury.sprawdz(getApplication(), cfg)
            _stan.update {
                it.copy(
                    stanChmury = if (blad == null) {
                        StanUslugi("zielony", "Polaczenie z baza dziala.")
                    } else {
                        StanUslugi(if (cfg.gotowa) "czerwony" else "brak", blad)
                    }
                )
            }
        }
    }

    /**
     * Odpytywanie serwera warsztatowego o kontrolke.
     *
     * Co dwadziescia sekund, w kolko przez cale zycie ekranu. Rzadziej mijaloby
     * sie z celem — roznica miedzy czerwonym a zielonym to okolo dwoch minut
     * czekania na wynik, wiec warto ja widziec, zanim sie zleci robote.
     * Czesciej nie ma sensu: backendy nie wstaja szybciej.
     */
    /**
     * Pyta o `/stan` po kolei pod kandydujacymi adresami i oddaje ten, ktory
     * odpowiedzial.
     *
     * PO CO: komputer w warsztacie ma dwa adresy — lokalny `192.168.0.166`
     * (dziala tylko w sieci warsztatu) i Tailscale `100.84.198.20` (dziala
     * z pracy, z terenu, z komorki). Dotad wybor nalezal do czlowieka i byl
     * jednym przyciskiem w Pomocy — telefon poza warsztatem pukal w adres
     * lokalny i apka pokazywala „komputer wylaczony", choc komputer chodzil.
     *
     * Kolejnosc: najpierw ostatni dzialajacy, potem ustawiony recznie, potem
     * lokalny, na koncu Tailscale. Lokalny idzie przed zdalnym, bo w warsztacie
     * jest szybszy i nie wymaga wlaczonego VPN-u w telefonie.
     */
    private suspend fun zapytajOStan(ostatni: String): Pair<String, StanSerwera> {
        val kandydaci = listOf(
            ostatni,
            adresUstawiony,
            DOMYSLNY_ADRES_WARSZTATU,
            ADRES_ZDALNY_WARSZTATU,
        ).filter { it.isNotBlank() }.distinct()

        var ostatniBlad: Exception? = null
        for (adres in kandydaci) {
            try {
                return adres to warsztat.stan("$adres/stan")
            } catch (e: Exception) {
                ostatniBlad = e
            }
        }
        throw ostatniBlad ?: java.io.IOException("Brak adresu serwera warsztatowego.")
    }

    /**
     * Adres, pod ktorym serwer ODPOWIADA TERAZ — sprawdzony przed zleceniem.
     *
     * PO CO: kontrolka odpytuje serwer co dwadziescia sekund i to ona
     * przestawiala adres roboczy. Zlecenie wysylane w miedzyczasie szlo pod
     * adres sprzed tego przestawienia. W praktyce: telefon poza warsztatem
     * wysylal robote przez Tailscale, a o wynik pytal pod `192.168.0.166`
     * i dostawal „failed to connect" — mimo ze serwer robote wlasnie liczyl.
     *
     * Sprawdzenie kosztuje jedno zapytanie o `/stan` z limitem 4 sekund na
     * probe. Przy zleceniu, ktore liczy sie minutami, to nie jest koszt.
     */
    private suspend fun adresRoboczy(): String {
        val teraz = _stan.value.adresWarsztatu
        return try {
            val (dzialajacy, _) = zapytajOStan(teraz)
            if (dzialajacy != teraz) {
                _stan.update { it.copy(adresWarsztatu = dzialajacy) }
            }
            dzialajacy
        } catch (e: Exception) {
            // Zaden adres nie odpowiada. Oddajemy ten z ustawien — niech
            // zlecenie polegnie na swoim wlasnym, czytelnym bledzie zamiast
            // na wyjatku stad.
            teraz
        }
    }

    private fun pilnujKontrolki() {
        viewModelScope.launch {
            while (true) {
                // Tu bierzemy adres wprost ze stanu: `zapytajOStan` ponizej i tak
                // przechodzi po kandydatach, a `adresRoboczy()` robilby to samo
                // drugi raz, czyli dwa komplety prob co dwadziescia sekund.
                val adres = _stan.value.adresDoKontrolki(adresUstawiony)
                if (adres.isNotBlank()) {
                    try {
                        val (dzialajacy, s) = zapytajOStan(adres)
                        if (dzialajacy != adres) {
                            _stan.update { it.copy(adresWarsztatu = dzialajacy) }
                        }
                        _stan.update {
                            it.copy(
                                swiatloWarsztatu = s.swiatlo,
                                opisWarsztatu = s.opis,
                                museWarsztatu = s.muse,
                                stanForge = s.forge,
                                stanComfy = s.comfyui,
                                stanFlow = s.flow,
                                stanMeta = s.meta,
                                stanCopilot = s.copilot,
                                kolejkaWarsztatu = s.kolejka,
                                zadaniaWToku = s.zadaniaWToku,
                            )
                        }
                    } catch (e: Exception) {
                        // Wylaczony komputer to normalny stan, nie awaria —
                        // pokazujemy to kolorem, nie komunikatem bledu.
                        _stan.update {
                            it.copy(
                                swiatloWarsztatu = "brak",
                                opisWarsztatu = "Komputer niedostepny — poza siecia warsztatu wlacz Tailscale",
                                museWarsztatu = "",
                                kolejkaWarsztatu = emptyList(),
                                zadaniaWToku = 0,
                            )
                        }
                    }
                }
                delay(20_000)
            }
        }
    }

    /**
     * Zleca robotę komputerowi i oddaje gotowy plik.
     *
     * Postęp trafia do komunikatu na ekranie, bo animacja potrafi liczyć się
     * kilka minut — bez tego użytkownik widziałby zamrożony ekran i uznałby,
     * że aplikacja padła.
     */
    fun zlecWarsztatowi(
        zadanie: String,
        zdjecie: Uri,
        opis: String,
        proporcje: String,
        gotowe: (Uri) -> Unit,
    ) = zlecModelem(zadanie, zdjecie, opis, proporcje, "", gotowe)

    fun zlecModelem(
        zadanie: String,
        zdjecie: Uri,
        opis: String,
        proporcje: String,
        model: String,
        gotowe: (Uri) -> Unit,
    ) {
        if (_stan.value.agentPracuje) return
        if (_stan.value.adresWarsztatu.isBlank()) {
            _stan.update { it.copy(blad = "Brak adresu serwera warsztatowego. Ustaw go w Pomocy.") }
            return
        }
        _stan.update { it.copy(agentPracuje = true, blad = null, komunikat = "Wysyłam do wybranej usługi…") }
        viewModelScope.launch {
            try {
                // Adres sprawdzamy TUZ przed wyslaniem, nie bierzemy tego sprzed
                // ostatniego odpytania kontrolki — inaczej robota idzie jedna siecia,
                // a pytanie o wynik druga.
                val adres = adresRoboczy()
                sprawdzModelAi(adres, model, if (zadanie.startsWith("animacja")) "wideo" else "obraz")
                val w = repozytorium.zlecWarsztatowi(
                    adres, zadanie, zdjecie, opis, proporcje,
                    // Gdy sieć padnie w trakcie liczenia, repozytorium pyta stąd
                    // o adres jeszcze raz — po zmianie Wi-Fi bywa już inny.
                    odswiezAdres = { adresRoboczy() },
                    model = model,
                ) { etap -> _stan.update { it.copy(komunikat = "Zadanie: $etap") } }
                val nazwaZadania = nazwaHistoriiAi(zadanie, model, _stan.value.katalogAi)
                when (w) {
                    is Wynik.Jest -> {
                        pl.fwdrucik.sklep.narzedzia.GaleriaZapis.zapiszDoGalerii(
                            getApplication<Application>(),
                            w.dane,
                            czyWideo = zadanie.startsWith("animacja"),
                        )
                        gotowe(Uri.fromFile(w.dane))
                        dopiszCzynnosc(
                            co = nazwaZadania,
                            silnik = model,
                            wynik = w.dane.name,
                            plik = w.dane.absolutePath,
                        )
                        _stan.update { it.copy(komunikat = "Gotowe — wynik zapisano w galerii. Sprawdź go i zdecyduj, czy użyć w produkcie.") }
                    }
                    is Wynik.Blad -> {
                        dopiszCzynnosc(nazwaZadania, "wybrana usługa", w.komunikat.take(90), udana = false)
                        _stan.update { it.copy(blad = w.komunikat) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("FW_AI", diagnostykaAi("media", e))
                _stan.update { it.copy(blad = bladAi(e).message) }
            } finally {
                _stan.update { it.copy(agentPracuje = false) }
            }
        }
    }

    /**
     * Cały ciąg obróbki wybranym agentem: tło → upiększanie → animacja.
     */
    fun ciagAutomatyczny(
        zdjecie: Uri,
        coTo: String,
        proporcje: String,
        dodatkowe: String,
        wybranySilnik: String = "meta",
        gotoweZdjecie: (Uri) -> Unit,
        gotowaAnimacja: (Uri) -> Unit,
        modelObrazu: String = "auto",
        modelWideo: String = "auto",
    ) {
        val dopisek = dodatkowe.trim().take(300)

        fun zDopiskiem(polecenie: String): String =
            if (dopisek.isBlank()) polecenie else polecenie + ", " + dopisek

        if (_stan.value.adresWarsztatu.isBlank()) {
            _stan.update {
                it.copy(blad = "Cały ciąg liczy komputer w warsztacie — wpisz jego adres w Pomocy.")
            }
            return
        }

        if (_stan.value.agentPracuje) {
            _stan.update { it.copy(komunikat = "Poczekaj — poprzednia robota jeszcze trwa.") }
            return
        }

        viewModelScope.launch {
            _stan.update { it.copy(agentPracuje = true, komunikat = "Krok 1 z 3: wycinam tło...") }
            try {
            val adres = adresRoboczy()
            sprawdzModelAi(adres, modelObrazu, "obraz")
            sprawdzModelAi(adres, modelWideo, "wideo")

            // --- krok 1: tło
            val silnikTla = nazwaHistoriiAi("zdjecie-produktowe", modelObrazu, _stan.value.katalogAi)
            val zadanieTla = when (wybranySilnik) {
                "meta" -> "zdjecie-meta"
                "flow" -> "zdjecie-flow"
                "copilot" -> "zdjecie-copilot"
                "gemini" -> "zdjecie-gemini"
                "forge" -> "zdjecie-produktowe"
                else -> "zdjecie-produktowe"
            }
            val poTle = repozytorium.zlecWarsztatowi(
                adres, zadanieTla, zdjecie, zDopiskiem(Polecenia.tlo(coTo)), proporcje,
                model = modelObrazu,
            ) { etap -> _stan.update { it.copy(komunikat = "Krok 1 z 3 (tło): $etap") } }

            val kadrBezTla = when (poTle) {
                is Wynik.Jest -> Uri.fromFile(poTle.dane)
                is Wynik.Blad -> {
                    dopiszCzynnosc("Ciąg auto — tło", silnikTla, poTle.komunikat.take(90), udana = false)
                    _stan.update { it.copy(blad = poTle.komunikat) }
                    return@launch
                }
            }
            dopiszCzynnosc("Ciąg auto — tło", silnikTla, "gotowe", kadrBezTla.path.orEmpty())
            gotoweZdjecie(kadrBezTla)

            // --- krok 2: upiększanie
            _stan.update { it.copy(komunikat = "Krok 2 z 3: poprawiam światło...") }
            val silnikSwiatla = nazwaHistoriiAi("zdjecie-produktowe", modelObrazu, _stan.value.katalogAi)
            val zadanieSwiatla = when (wybranySilnik) {
                "meta" -> "zdjecie-meta"
                "flow" -> "zdjecie-flow"
                "copilot" -> "zdjecie-copilot"
                "gemini" -> "zdjecie-gemini"
                "forge" -> "zdjecie-produktowe"
                else -> "zdjecie-produktowe"
            }
            val poSwietle = repozytorium.zlecWarsztatowi(
                adres, zadanieSwiatla, kadrBezTla, zDopiskiem(Polecenia.upieksz(coTo)), proporcje,
                model = modelObrazu,
            ) { etap -> _stan.update { it.copy(komunikat = "Krok 2 z 3 (światło): $etap") } }

            val kadrGotowy = when (poSwietle) {
                is Wynik.Jest -> {
                    pl.fwdrucik.sklep.narzedzia.GaleriaZapis.zapiszDoGalerii(
                        getApplication<Application>(),
                        poSwietle.dane,
                        czyWideo = false,
                    )
                    Uri.fromFile(poSwietle.dane)
                }
                is Wynik.Blad -> {
                    dopiszCzynnosc(
                        "Ciąg auto — światło", silnikSwiatla,
                        poSwietle.komunikat.take(90), udana = false,
                    )
                    _stan.update {
                        it.copy(komunikat = "Światła nie poprawiłem (" +
                            poSwietle.komunikat.take(60) + ") — animuję kadr bez tła.")
                    }
                    kadrBezTla
                }
            }
            if (kadrGotowy != kadrBezTla) {
                dopiszCzynnosc("Ciąg auto — światło", silnikSwiatla, "gotowe", kadrGotowy.path.orEmpty())
                gotoweZdjecie(kadrGotowy)
            }

            // --- krok 3: animacja
            _stan.update { it.copy(komunikat = "Krok 3 z 3: animuję (to potrwa)...") }
            val silnikRuchu = nazwaHistoriiAi("animacja-szybka", modelWideo, _stan.value.katalogAi)
            val zadanieRuchu = when (wybranySilnik) {
                "meta" -> "animacja-meta"
                "flow" -> "animacja-flow"
                "gemini" -> "animacja-gemini"
                "forge", "comfy" -> "animacja"
                "auto" -> "animacja-szybka"
                else -> "animacja"
            }
            val poRuchu = repozytorium.zlecWarsztatowi(
                adres, zadanieRuchu, kadrGotowy, zDopiskiem(Polecenia.obrot(coTo)), proporcje,
                model = modelWideo,
            ) { etap -> _stan.update { it.copy(komunikat = "Krok 3 z 3 (animacja): $etap") } }

            when (poRuchu) {
                is Wynik.Jest -> {
                    pl.fwdrucik.sklep.narzedzia.GaleriaZapis.zapiszDoGalerii(
                        getApplication<Application>(),
                        poRuchu.dane,
                        czyWideo = true,
                    )
                    val uri = Uri.fromFile(poRuchu.dane)
                    dopiszCzynnosc("Ciąg auto — animacja", silnikRuchu, "gotowe", uri.path.orEmpty())
                    gotowaAnimacja(uri)
                    _stan.update { it.copy(komunikat = "Ciąg zakończony pomyślnie!") }
                }
                is Wynik.Blad -> {
                    dopiszCzynnosc(
                        "Ciąg auto — animacja", silnikRuchu,
                        poRuchu.komunikat.take(90), udana = false,
                    )
                    _stan.update { it.copy(blad = poRuchu.komunikat) }
                }
            }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("FW_AI", diagnostykaAi("media", e))
                _stan.update { it.copy(blad = bladAi(e).message) }
            } finally {
                _stan.update { it.copy(agentPracuje = false) }
            }
        }
    }

    fun zapiszAdresWarsztatu(adres: String) {
        viewModelScope.launch {
            ustawienia.zapiszAdresWarsztatu(adres)
            _stan.update { it.copy(komunikat = "Adres serwera zapisany") }
        }
    }

    fun zapiszKluczGemini(klucz: String) {
        viewModelScope.launch {
            ustawienia.zapiszKluczGemini(klucz)
            _stan.update { it.copy(komunikat = if (klucz.isBlank()) "Klucz usunięty" else "Klucz zapisany") }
        }
    }

    /**
     * Propozycja opisu ze zdjęcia i krótkiej notatki.
     *
     * Zwraca `SzkicProduktu`, który ekran kreatora może wstawić do odpowiednich
     * pól kreatora. Wynik trafia do wywołania zwrotnego, a nie do stanu, bo
     * pola kreatora są jego lokalnym stanem i użytkownik może je jeszcze poprawić.
     */
    fun opiszZeZdjecia(
        zdjecie: Uri,
        notatka: String,
        wybor: pl.fwdrucik.sklep.ui.ekrany.WyborRoboty,
        gotowe: (SzkicProduktu) -> Unit,
    ) {
        val silnikWybrany = wybor.silnik
        val klucz = _stan.value.kluczGemini
        val adres = _stan.value.adresWarsztatu

        viewModelScope.launch {
            _stan.update { it.copy(agentPracuje = true) }
            try {
                val szkic = when (silnikWybrany) {
                    "meta" -> {
                        if (adres.isBlank()) throw java.io.IOException("Agent Meta AI wymaga włączonego serwera warsztatu (wpisz adres w zakładce Pomoc).")
                        agent.opiszPrzezMete(adres, notatka, _stan.value.poprawki, zdjecie)
                    }
                    "copilot" -> {
                        if (adres.isBlank()) throw java.io.IOException("Agent Microsoft Copilot wymaga włączonego serwera warsztatu (wpisz adres w zakładce Pomoc).")
                        agent.opiszPrzezCopilota(adres, notatka, _stan.value.poprawki, zdjecie)
                    }
                    "muse" -> {
                        if (adres.isBlank()) throw java.io.IOException("Agent Muse Code wymaga włączonego serwera warsztatu (wpisz adres w zakładce Pomoc).")
                        agent.opiszPrzezMuse(adres, notatka, _stan.value.poprawki, zdjecie)
                    }
                    "gemini" -> {
                        if (klucz.isBlank()) throw java.io.IOException("Agent Google Gemini wymaga klucza API w zakładce Pomoc.")
                        agent.opiszZdjecie(
                            klucz, zdjecie, notatka,
                            wybor.model.ifBlank { _stan.value.modelOpisu },
                            _stan.value.poprawki,
                        )
                    }
                    else -> {
                        if (klucz.isNotBlank()) {
                            agent.opiszZdjecie(klucz, zdjecie, notatka, wybor.model.ifBlank { _stan.value.modelOpisu }, _stan.value.poprawki)
                        } else if (adres.isNotBlank()) {
                            agent.opiszPrzezMete(adres, notatka, _stan.value.poprawki, zdjecie)
                        } else {
                            throw java.io.IOException("Wpisz klucz Gemini lub adres warsztatu w Pomocy.")
                        }
                    }
                }
                gotowe(szkic)
                _stan.update { it.copy(propozycjaAgenta = szkic) }
                dopiszCzynnosc(
                    co = "Opis ze zdjęcia",
                    silnik = when (silnikWybrany) {
                        "meta" -> "Meta AI"
                        "copilot" -> "Copilot"
                        "muse" -> "Muse"
                        "gemini" -> "Gemini"
                        else -> "Agent"
                    },
                    wynik = szkic.nazwa.ifBlank { "gotowy" },
                )
                _stan.update {
                    it.copy(komunikat = "Opis gotowy — sprawdź go przed publikacją")
                }
            } catch (e: Exception) {
                dopiszCzynnosc("Opis ze zdjęcia", silnikWybrany ?: "auto", e.message?.take(90).orEmpty(), udana = false)
                _stan.update { it.copy(blad = opisBledu(e)) }
            } finally {
                _stan.update { it.copy(agentPracuje = false) }
            }
        }
    }

    /** Poprawione zdjęcie produktowe. Oryginał zostaje — decyzja należy do Ciebie. */
    fun poprawZdjecie(
        zdjecie: Uri,
        wybor: pl.fwdrucik.sklep.ui.ekrany.WyborRoboty,
        gotowe: (Uri) -> Unit,
    ) {
        val klucz = _stan.value.kluczGemini
        val adres = _stan.value.adresWarsztatu
        val silnik = wybor.silnik.takeIf { it != "auto" } ?: silnikDo("zdjecie")

        if (silnik == "forge" && adres.isNotBlank()) {
            zlecWarsztatowi("zdjecie-produktowe", zdjecie, wybor.dodatkowe, wybor.proporcje, gotowe)
            return
        }
        if (silnik == "flow" && adres.isNotBlank()) {
            zlecWarsztatowi("zdjecie-flow", zdjecie, wybor.dodatkowe, wybor.proporcje, gotowe)
            return
        }
        if (silnik == "meta") {
            if (adres.isBlank()) {
                _stan.update {
                    it.copy(blad = "Meta AI liczy przez komputer w warsztacie — wpisz jego adres w zakładce Pomoc.")
                }
                return
            }
            zlecWarsztatowi("zdjecie-meta", zdjecie, wybor.dodatkowe, wybor.proporcje, gotowe)
            return
        }
        if (silnik == "copilot") {
            if (adres.isBlank()) {
                _stan.update {
                    it.copy(blad = "Microsoft Copilot liczy przez komputer w warsztacie — wpisz jego adres w zakładce Pomoc.")
                }
                return
            }
            zlecWarsztatowi("zdjecie-copilot", zdjecie, wybor.dodatkowe, wybor.proporcje, gotowe)
            return
        }

        if (silnik == "gemini") {
            if (adres.isNotBlank()) {
                zlecWarsztatowi("zdjecie-gemini", zdjecie, wybor.dodatkowe, wybor.proporcje, gotowe)
                return
            }
            if (klucz.isBlank()) {
                _stan.update { it.copy(blad = "Wpisz klucz Gemini w zakładce Pomoc lub uruchom serwer warsztatu.") }
                return
            }
        }

        // Gdy klucza nie ma, ale komputer stoi w warsztacie i silnik to auto/forge — poprawia Forge.
        if (klucz.isBlank() && silnik != "gemini") {
            if (adres.isBlank()) {
                _stan.update {
                    it.copy(blad = "Wpisz klucz Gemini albo adres komputera w zakładce Pomoc.")
                }
            } else {
                _stan.update { it.copy(komunikat = "Bez klucza Gemini — poprawia komputer w warsztacie") }
                zlecWarsztatowi("zdjecie-produktowe", zdjecie, wybor.dodatkowe, wybor.proporcje, gotowe)
            }
            return
        }

        viewModelScope.launch {
            _stan.update { it.copy(agentPracuje = true) }
            try {
                val plik = agent.poprawZdjecie(
                    klucz, zdjecie,
                    wybor.model.ifBlank { _stan.value.modelObrazu },
                    wybor.dodatkowe,
                )
                pl.fwdrucik.sklep.narzedzia.GaleriaZapis.zapiszDoGalerii(
                    getApplication<Application>(),
                    plik,
                    czyWideo = false,
                )
                gotowe(Uri.fromFile(plik))
                dopiszCzynnosc("Poprawa zdjęcia", _stan.value.modelObrazu, "gotowe", plik.absolutePath)
                _stan.update { it.copy(komunikat = "Zdjęcie poprawione — zapisano w galerii") }
            } catch (e: Exception) {
                if (wybor.silnik == "gemini" || adres.isBlank()) {
                    _stan.update { it.copy(blad = opisBledu(e)) }
                } else {
                    _stan.update {
                        it.copy(
                            agentPracuje = false,
                            komunikat = "Gemini nie odpowiada — poprawia komputer w warsztacie",
                        )
                    }
                    zlecWarsztatowi("zdjecie-produktowe", zdjecie, wybor.dodatkowe, wybor.proporcje, gotowe)
                }
            } finally {
                _stan.update { it.copy(agentPracuje = false) }
            }
        }
    }

    private fun opisBledu(e: Exception): String = when {
        e.message?.contains("401") == true || e.message?.contains("403") == true ->
            "Gemini odrzuciło klucz. Sprawdź go w zakładce Pomoc."
        e.message?.contains("429") == true ->
            "Przekroczony limit zapytań do Gemini. Spróbuj za chwilę."
        else -> e.message ?: "Nie udało się połączyć z Gemini."
    }

    fun zaloguj(login: String, haslo: String) = wKtorymsMomencie {
        when (val w = repozytorium.zaloguj(login, haslo)) {
            is Wynik.Jest -> {
                _stan.update { it.copy(zalogowany = true, komunikat = "Zalogowano jako ${w.dane.nick}") }
                odswiez()
            }
            is Wynik.Blad -> _stan.update { it.copy(blad = w.komunikat) }
        }
    }

    fun wyloguj() = wKtorymsMomencie {
        repozytorium.wyloguj()
        _stan.update { it.poWylogowaniu() }
    }

    fun odswiez() = wKtorymsMomencie {
        when (val w = repozytorium.produkty()) {
            is Wynik.Jest -> _stan.update { it.copy(produkty = w.dane) }
            is Wynik.Blad -> _stan.update { it.copy(blad = w.komunikat) }
        }
        when (val w = repozytorium.zamowienia()) {
            is Wynik.Jest -> _stan.update { it.copy(zamowienia = w.dane) }
            is Wynik.Blad -> _stan.update { it.copy(blad = w.komunikat) }
        }
    }

    /** Zapis produktu. Zwraca identyfikator, żeby ekran mógł od razu dodać zdjęcia. */
    /**
     * Porownuje to, co zaproponowal agent, z tym, co czlowiek naprawde zapisal.
     *
     * Rozne wartosci to gotowa lekcja. Rownych nie zapisujemy: powtarzanie
     * agentowi, ze mial racje, nie uczy go niczego, a rozpycha prompt.
     */
    private fun zbierzPoprawki(produkt: Produkt, cenaZl: String): List<Poprawka> {
        val p = _stan.value.propozycjaAgenta ?: return emptyList()
        val teraz = System.currentTimeMillis()
        return listOfNotNull(
            porownaj("nazwa", p.nazwa, produkt.nazwa, teraz),
            porownaj("opis krotki", p.opisKrotki, produkt.opisKrotki, teraz),
            porownaj("opis", p.opis, produkt.opis, teraz),
            porownaj("kategoria", p.kategoria, produkt.kategoria, teraz),
            porownaj("jednostka", p.jednostka, produkt.jednostka, teraz),
            porownaj("cena", p.cena, cenaZl, teraz),
            porownaj("waga", p.waga, produkt.wagaG.takeIf { it > 0 }?.toString().orEmpty(), teraz),
            porownaj("czas realizacji", p.czasRealizacji, produkt.czasRealizacji, teraz),
        )
    }

    private fun porownaj(pole: String, odAgenta: String, zapisane: String, kiedy: Long): Poprawka? {
        val a = odAgenta.trim()
        val b = zapisane.trim()
        // Puste po obu stronach i drobne roznice w bialych znakach to nie poprawka.
        if (a.isBlank() && b.isBlank()) return null
        if (a.equals(b, ignoreCase = true)) return null
        return Poprawka(pole = pole, odAgenta = a, poPoprawce = b, kiedy = kiedy)
    }

    private fun naucSie(produkt: Produkt, cenaZl: String) {
        val nowe = zbierzPoprawki(produkt, cenaZl)
        if (nowe.isEmpty()) {
            _stan.update { it.copy(propozycjaAgenta = null) }
            return
        }
        viewModelScope.launch {
            ustawienia.dopiszPoprawki(nowe)
            dopiszCzynnosc(
                co = "Nauka z poprawek",
                silnik = "pamiec agenta",
                wynik = nowe.joinToString(", ") { it.pole },
            )
            _stan.update { it.copy(propozycjaAgenta = null) }
        }
    }

    fun zapisz(
        produkt: Produkt,
        cenaZl: String,
        cenaPromoZl: String,
        poZapisie: (Int) -> Unit = {},
    ) = wKtorymsMomencie {
        when (val w = repozytorium.zapisz(produkt, cenaZl, cenaPromoZl)) {
            is Wynik.Jest -> {
                _stan.update { it.copy(komunikat = "Zapisano „${produkt.nazwa}”") }
                naucSie(produkt, cenaZl)
                usunKopiePoZapisie(produkt.id)
                odswiezProdukty()
                poZapisie(w.dane.id)
            }
            is Wynik.Blad -> _stan.update { it.copy(blad = w.komunikat) }
        }
    }

    /**
     * Zapis produktu razem ze zdjęciem wybranym przed jego powstaniem.
     *
     * Serwer przypina zdjęcia do istniejącego produktu, więc przy nowym wyrobie
     * trzeba najpierw zapisać, a dopiero potem wysłać plik. Kreator zaczyna się
     * od zdjęcia, bo tak wygląda praca w warsztacie — więc ta kolejność jest
     * ukryta tutaj, zamiast obciążać nią użytkownika.
     */
    /**
     * Zapis produktu razem ze wszystkimi mediami (animacja GIF/wideo, zdjecie glowne, galeria).
     *
     * Sekwencja publikacji:
     * 1. Animacja ruchoma (GIF lub MP4) staje na pozycji 0 (przed zdjeciami, widoczna jako pierwsza).
     * 2. Zdjecie glowne (okladka, pozycja 100).
     * 3. Dodatkowe kadry galerii (pozycje 110, 120, ...).
     */
    fun zapiszZeZdjeciami(
        produkt: Produkt,
        cenaZl: String,
        cenaPromoZl: String,
        glowneZdjecie: Uri?,
        dodatkoweZdjecia: List<Uri> = emptyList(),
        animacja: Uri? = null,
        altZdjecia: String,
        poCzesciowymZapisie: (Int) -> Unit = {},
        poZapisie: (Int) -> Unit = {},
    ) = wKtorymsMomencie {
        zapisKopii?.join()
        when (val w = repozytorium.zapisz(produkt, cenaZl, cenaPromoZl)) {
            is Wynik.Jest -> {
                val id = w.dane.id
                naucSie(produkt, cenaZl)
                val brakujace = mutableListOf<String>()
                val brakujaceZdjecia = mutableListOf<Uri>()
                var brakGlownego = false
                var brakAnimacji = false

                // 1. Animacja GIF / Wideo na pozycji 0 (pierwsze w sklepie)
                if (animacja != null) {
                    val sciezka = animacja.toString().lowercase()
                    val jestWideo = sciezka.endsWith(".mp4") || sciezka.endsWith(".webm") || sciezka.endsWith(".mov")
                    val opis = if (jestWideo) "Wideo obrotowe produktu" else "Animacja 360° produktu"
                    val wynikPliku = repozytorium.wgrajPlikProduktu(id, animacja, opis, pozycja = 0)
                    if (wynikPliku is Wynik.Blad) {
                        brakAnimacji = true
                        brakujace.add("film lub animacja")
                        _stan.update { it.copy(blad = "Błąd wysyłania animacji: ${wynikPliku.komunikat}") }
                    }
                }

                // 2. Zdjęcie główne (okładka)
                if (glowneZdjecie != null) {
                    val z = repozytorium.wgrajZdjecie(id, glowneZdjecie, altZdjecia, 100)
                    if (z is Wynik.Blad) {
                        brakGlownego = true
                        brakujace.add("zdjęcie główne")
                        _stan.update { it.copy(blad = "Błąd wysyłania zdjęcia głównego: ${z.komunikat}") }
                    }
                }

                // 3. Dodatkowe zdjęcia galerii
                dodatkoweZdjecia.forEachIndexed { idx, kadr ->
                    val z = repozytorium.wgrajZdjecie(id, kadr, "$altZdjecia - kadr ${idx + 2}", 110 + (idx * 10))
                    if (z is Wynik.Blad) {
                        brakujaceZdjecia.add(kadr)
                        brakujace.add("zdjęcie nr ${idx + 2}")
                        _stan.update { it.copy(blad = "Błąd wysyłania zdjęcia #${idx + 2}: ${z.komunikat}") }
                    }
                }

                if (brakujace.isNotEmpty()) {
                    zapisKopii?.cancel()
                    zapisKopii?.join()
                    val kopia = (_stan.value.kopieRobocze[produkt.id] ?: KopiaRobocza(
                        nazwa = produkt.nazwa, opis = produkt.opis, opisKrotki = produkt.opisKrotki, cena = cenaZl,
                    )).copy(
                        id = id, status = produkt.status,
                        zdjecie = if (brakGlownego) zachowajZdjecie(id, glowneZdjecie.toString()) else "",
                        dodatkoweKadry = brakujaceZdjecia.map { zachowajZdjecie(id, it.toString()) },
                        animacja = if (brakAnimacji) zachowajZdjecie(id, animacja.toString()) else "",
                    )
                    ustawienia.zapiszKopie(kopia)
                    if (produkt.id != id) ustawienia.skasujKopie(produkt.id)
                    _stan.update { it.copy(
                        kopieRobocze = (it.kopieRobocze - produkt.id) + (id to kopia),
                        blad = "Dane produktu zapisano. Nie wysłano: ${brakujace.joinToString()}. Materiały są w kopii roboczej. Spróbuj zapisać ponownie.",
                        komunikat = null,
                    ) }
                    odswiezProdukty()
                    poCzesciowymZapisie(id)
                    return@wKtorymsMomencie
                }
                // Dopiero teraz zdjęcia są na serwerze — można usunąć kopię.
                usunKopiePoZapisie(produkt.id)
                _stan.update { it.copy(komunikat = "Zapisano „${produkt.nazwa}” i zaktualizowano w sklepie!") }
                val chmuraCfg = _stan.value.chmura
                if (chmuraCfg.gotowa) {
                    runCatching {
                        MostChmury.przeniesProdukt(getApplication(), chmuraCfg, produkt.copy(id = id))
                    }
                }
                odswiezProdukty()
                poZapisie(id)
            }
            is Wynik.Blad -> _stan.update { it.copy(blad = w.komunikat) }
        }
    }

    fun zapiszZeZdjeciem(
        produkt: Produkt,
        cenaZl: String,
        cenaPromoZl: String,
        zdjecie: Uri?,
        altZdjecia: String,
        poZapisie: (Int) -> Unit = {},
    ) = zapiszZeZdjeciami(
        produkt = produkt,
        cenaZl = cenaZl,
        cenaPromoZl = cenaPromoZl,
        glowneZdjecie = zdjecie,
        dodatkoweZdjecia = emptyList(),
        animacja = null,
        altZdjecia = altZdjecia,
        poZapisie = poZapisie,
    )

    fun zmienStatus(id: Int, status: String) = wKtorymsMomencie {
        gdyDobrze(repozytorium.ustawStatus(id, status), "Zmieniono status produktu") {
            odswiezProdukty()
        }
    }

    fun usun(id: Int) = wKtorymsMomencie {
        gdyDobrze(repozytorium.usun(id), "Produkt usunięty") { odswiezProdukty() }
    }

    fun wgrajZdjecie(produktId: Int, uri: Uri, alt: String) = wKtorymsMomencie {
        when (val w = repozytorium.wgrajZdjecie(produktId, uri, alt, pozycja = 100)) {
            is Wynik.Jest -> {
                _stan.update { it.copy(komunikat = "Zdjęcie dodane") }
                odswiezProdukty()
            }
            is Wynik.Blad -> _stan.update { it.copy(blad = w.komunikat) }
        }
    }

    fun importujZAukcjiAllegro(url: String, poImportcie: (Int) -> Unit = {}) = wKtorymsMomencie {
        _stan.update { it.copy(ladowanie = true, komunikat = "Pobieranie danych z aukcji Allegro...") }
        when (val w = repozytorium.importujAukcjeAllegro(url.trim())) {
            is Wynik.Jest -> {
                _stan.update { it.copy(ladowanie = false, komunikat = "Aukcja Allegro została zaimportowana i opublikowana na fwdrucik.pl!") }
                odswiezProdukty()
                poImportcie(w.dane.id)
            }
            is Wynik.Blad -> {
                _stan.update { it.copy(ladowanie = false, blad = w.komunikat) }
            }
        }
    }

    fun synchronizujAukcjeAllegro(
        allegroUrl: String,
        allegroId: String,
        nazwa: String,
        cenaZl: String,
        kategoria: String = "inne",
        opis: String = "",
        opisKrotki: String = "",
        poSynchronizacji: (Int) -> Unit = {},
    ) = wKtorymsMomencie {
        _stan.update { it.copy(ladowanie = true, komunikat = "Wysyłanie ustandaryzowanego schematu Allegro na fwdrucik.pl...") }
        when (val w = repozytorium.synchronizujAukcjeAllegro(
            allegroUrl = allegroUrl.trim(),
            allegroId = allegroId.trim(),
            nazwa = nazwa.trim(),
            cena = cenaZl.trim(),
            kategoria = kategoria,
            opis = opis,
            opisKrotki = opisKrotki,
        )) {
            is Wynik.Jest -> {
                _stan.update { it.copy(ladowanie = false, komunikat = "Aukcja Allegro zsynchronizowana ze sklepem fwdrucik.pl!") }
                odswiezProdukty()
                poSynchronizacji(w.dane.id)
            }
            is Wynik.Blad -> {
                _stan.update { it.copy(ladowanie = false, blad = w.komunikat) }
            }
        }
    }

    /**
     * Film albo animacja do ogloszenia — zawsze na POCZATEK galerii.
     *
     * Pozycja 0 i kolejnosc na stronie produktu ida w parze: sklep rysuje
     * material ruchomy przed zdjeciami, bo to on pokazuje wyrob ze wszystkich
     * stron. Zdjecia zostaja tam, gdzie byly, tylko za nim.
     */
    /**
     * Sciaga zdjecie produktu ze sklepu na telefon i oddaje sciezke do pliku.
     *
     * PO CO: kreator otwarty na istniejacym produkcie mial zdjecie tylko jako
     * adres http. Wszystko, co robi cokolwiek ze zdjeciem — opis z AI, poprawa
     * kadru, animacja, zapis — czyta plik przez `contentResolver`, a ten nie
     * otworzy adresu ze sklepu. W praktyce znaczylo to, ze przy edycji gotowego
     * ogloszenia trzeba bylo wskazac zdjecie z galerii jeszcze raz, mimo ze
     * lezalo w sklepie od tygodnia.
     *
     * Plik ladzie w tym samym katalogu `kadry`, co kadry z aparatu, wiec dziala
     * z nim dokladnie to samo co ze swiezym zdjeciem.
     */
    fun pobierzZdjecieProduktu(adres: String, gotowe: (Uri) -> Unit) {
        viewModelScope.launch {
            val plik = withContext(Dispatchers.IO) {
                runCatching {
                    val katalog = java.io.File(
                        getApplication<Application>().filesDir, "kadry"
                    ).apply { mkdirs() }
                    val cel = java.io.File(katalog, "sklep_" + adres.hashCode().toString().replace("-", "m") + ".jpg")
                    // Raz sciagniete zostaje: ten sam produkt otwierany kilka razy
                    // pod rzad nie ma po co obciazac ani sieci, ani serwera.
                    if (!cel.exists() || cel.length() == 0L) {
                        java.net.URL(adres).openStream().use { we ->
                            cel.outputStream().use { wy -> we.copyTo(wy) }
                        }
                    }
                    cel
                }.getOrNull()
            }
            if (plik != null) {
                gotowe(Uri.fromFile(plik))
            } else {
                _stan.update {
                    it.copy(blad = "Nie udało się pobrać zdjęcia produktu ze sklepu.")
                }
            }
        }
    }

    fun wgrajPlikDoOgloszenia(produktId: Int, uri: Uri, opis: String) = wKtorymsMomencie {
        when (val w = repozytorium.wgrajPlikProduktu(produktId, uri, opis, pozycja = 0)) {
            is Wynik.Jest -> {
                dopiszCzynnosc(
                    co = if (w.dane.rodzaj == "animacja") "Animacja w ogłoszeniu"
                    else "Film w ogłoszeniu",
                    silnik = "sklep",
                    wynik = w.dane.src,
                )
                _stan.update {
                    it.copy(komunikat = "Dodane do ogłoszenia — będzie pierwsze w galerii")
                }
                odswiezProdukty()
            }
            is Wynik.Blad -> _stan.update { it.copy(blad = w.komunikat) }
        }
    }

    fun usunZdjecie(id: Int) = wKtorymsMomencie {
        gdyDobrze(repozytorium.usunObraz(id), "Zdjęcie usunięte") { odswiezProdukty() }
    }

    fun zmienStatusZamowienia(id: Int, status: String, przesylka: String = "") = wKtorymsMomencie {
        gdyDobrze(
            repozytorium.statusZamowienia(id, status, przesylka),
            if (status == "wyslane" && przesylka.isNotBlank())
                "Wysłane — klient dostał mail z numerem przesyłki"
            else "Status zmieniony — klient dostał wiadomość",
        ) {
            when (val w = repozytorium.zamowienia()) {
                is Wynik.Jest -> _stan.update { it.copy(zamowienia = w.dane) }
                is Wynik.Blad -> _stan.update { it.copy(blad = w.komunikat) }
            }
        }
    }

    /** Szybka zmiana stanu magazynowego bez wchodzenia w kreator. */
    fun ustawStan(produkt: Produkt, nowyStan: Int?) {
        zapisz(
            produkt = produkt.copy(stan = nowyStan),
            cenaZl = groszeNaPole(produkt.cenaGr),
            cenaPromoZl = produkt.cenaPromoGr?.let(::groszeNaPole).orEmpty(),
        )
    }

    fun wyczyscKomunikaty() = _stan.update { it.copy(blad = null, komunikat = null) }

    fun znajdz(id: Int): Produkt? = _stan.value.produkty.firstOrNull { it.id == id }

    private suspend fun odswiezProdukty() {
        when (val w = repozytorium.produkty()) {
            is Wynik.Jest -> _stan.update { it.copy(produkty = w.dane) }
            is Wynik.Blad -> _stan.update { it.copy(blad = w.komunikat) }
        }
    }

    private suspend fun gdyDobrze(wynik: Wynik<Unit>, tekst: String, dalej: suspend () -> Unit) {
        when (wynik) {
            is Wynik.Jest -> {
                _stan.update { it.copy(komunikat = tekst) }
                dalej()
            }
            is Wynik.Blad -> _stan.update { it.copy(blad = wynik.komunikat) }
        }
    }

    /**
     * Zapisuje dostep do wspolnej bazy.
     *
     * PO CO SKLEP MA CHMURE: przepisuje zamowienia do Firestore, zeby panel warsztatowy
     * je widzial. Puste pola = przenoszenie wylaczone i sklep dziala jak wczesniej.
     */
    fun zapiszChmure(projectId: String, appId: String, apiKey: String) {
        viewModelScope.launch { ustawienia.zapiszChmure(projectId, appId, apiKey) }
    }

    private fun wKtorymsMomencie(blok: suspend () -> Unit) {
        viewModelScope.launch {
            _stan.update { it.copy(ladowanie = true) }
            try {
                blok()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _stan.update { it.copy(blad = "Nie udało się zakończyć operacji. Sprawdź połączenie i spróbuj ponownie.") }
            } finally {
                _stan.update { it.copy(ladowanie = false) }
            }
        }
    }
}

/** Grosze na tekst do pola formularza: 12990 -> "129.90" (serwer przyjmie też przecinek). */
fun groszeNaPole(grosze: Int): String = String.format("%d.%02d", grosze / 100, grosze % 100)
