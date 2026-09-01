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
import pl.fwdrucik.sklep.SklepAplikacja
import pl.fwdrucik.sklep.dane.Produkt
import pl.fwdrucik.sklep.dane.SzkicProduktu
import pl.fwdrucik.sklep.siec.StanSerwera
import pl.fwdrucik.sklep.siec.PozycjaKolejki
import pl.fwdrucik.sklep.dane.Wynik
import pl.fwdrucik.sklep.dane.TrojkaFirebase
import pl.fwdrucik.sklep.dane.KopiaRobocza
import pl.fwdrucik.sklep.dane.MostChmury
import pl.fwdrucik.sklep.dane.Polecenia
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
)

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
                _stan.update { it.copy(kopieRobocze = kopie) }
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
    //
    // Zapis idzie z opoznieniem, nie po kazdej literze: DataStore pisze na dysk,
    // a kreator ma kilkanascie pol. Sekunda ciszy w pisaniu to naturalny moment
    // na zapis i nie widac go na ekranie.

    private var zapisKopii: kotlinx.coroutines.Job? = null

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
        if (kopia.pusta) return
        zapisKopii?.cancel()
        zapisKopii = viewModelScope.launch {
            delay(500)
            ustawienia.zapiszKopie(
                kopia.copy(
                    zdjecie = zachowajZdjecie(kopia.id, kopia.zdjecie),
                    zapisano = System.currentTimeMillis(),
                )
            )
            ustawienia.zapiszAktywnyKreatorId(kopia.id)
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
            if (zrodlo.startsWith(katalog.absolutePath)) return@withContext zrodlo

            runCatching {
                katalog.mkdirs()
                val cel = java.io.File(katalog, "kopia_$id.jpg")
                getApplication<Application>().contentResolver
                    .openInputStream(android.net.Uri.parse(zrodlo))!!
                    .use { we -> cel.outputStream().use { wy -> we.copyTo(wy) } }
                cel.absolutePath
            }.getOrDefault("")
        }

    /** Po udanym zapisie na serwer kopia nie ma juz czego pilnowac. */
    fun skasujKopie(id: Int) {
        zapisKopii?.cancel()
        viewModelScope.launch {
            // Plik zdjecia idzie razem z wpisem — inaczej katalog `kopie`
            // rosnie w nieskonczonosc po kazdym porzuconym szkicu.
            _stan.value.kopieRobocze[id]?.zdjecie?.takeIf { it.isNotBlank() }?.let {
                runCatching { java.io.File(it).delete() }
            }
            ustawienia.skasujKopie(id)
            if (_stan.value.aktywnyKreatorId == id) {
                ustawienia.zapiszAktywnyKreatorId(-1)
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
     * Adres wpisany w ustawieniach — surowy, bez automatycznej podmiany.
     *
     * Trzymany osobno od `stan.adresWarsztatu`, bo ten drugi jest adresem
     * ROBOCZYM: tym, pod ktorym serwer faktycznie odpowiedzial. Gdyby oba byly
     * jednym polem, kazde przelaczenie na Tailscale nadpisywaloby czlowiekowi
     * ustawienie, a po powrocie do warsztatu apka trzymalaby sie adresu
     * zdalnego mimo szybszej sieci lokalnej.
     */
    private var adresUstawiony: String = ""

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
                val adres = _stan.value.adresWarsztatu
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
    ) {
        if (_stan.value.adresWarsztatu.isBlank()) {
            _stan.update { it.copy(blad = "Brak adresu serwera warsztatowego. Ustaw go w Pomocy.") }
            return
        }
        viewModelScope.launch {
            _stan.update { it.copy(agentPracuje = true, komunikat = "Wysyłam na komputer...") }
            try {
                // Adres sprawdzamy TUZ przed wyslaniem, nie bierzemy tego sprzed
                // ostatniego odpytania kontrolki — inaczej robota idzie jedna siecia,
                // a pytanie o wynik druga.
                val adres = adresRoboczy()
                val w = repozytorium.zlecWarsztatowi(
                    adres, zadanie, zdjecie, opis, proporcje,
                    // Gdy sieć padnie w trakcie liczenia, repozytorium pyta stąd
                    // o adres jeszcze raz — po zmianie Wi-Fi bywa już inny.
                    odswiezAdres = { adresRoboczy() },
                ) { etap -> _stan.update { it.copy(komunikat = "Komputer: $etap") } }
                val nazwaZadania = when (zadanie) {
                    "animacja" -> "Animacja (karta)"
                    "animacja-flow" -> "Animacja (Veo)"
                    "animacja-meta" -> "Animacja (Meta AI, $proporcje)"
                    "zdjecie-flow" -> "Poprawa zdjęcia (Flow)"
                    "zdjecie-meta" -> "Kadr z Meta AI ($proporcje)"
                    "zdjecie-copilot" -> "Kadr z Copilot ($proporcje)"
                    else -> "Poprawa zdjęcia (Forge)"
                }
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
                            silnik = "komputer w warsztacie",
                            wynik = w.dane.name,
                            plik = w.dane.absolutePath,
                        )
                        _stan.update { it.copy(komunikat = "Gotowe — zapisano w galerii i w szkicu") }
                    }
                    is Wynik.Blad -> {
                        dopiszCzynnosc(nazwaZadania, "komputer w warsztacie", w.komunikat.take(90), udana = false)
                        _stan.update { it.copy(blad = w.komunikat) }
                    }
                }
            } catch (e: Exception) {
                _stan.update { it.copy(blad = e.message ?: "Błąd połączenia z warsztatem") }
            } finally {
                _stan.update { it.copy(agentPracuje = false) }
            }
        }
    }

    /**
     * Cały ciąg obróbki jednym kliknięciem: tło → upiększanie → animacja.
     *
     * PO CO: te trzy kroki i tak robi się po kolei na tym samym zdjęciu, a każdy
     * z nich to osobne okienko, osobny wybór silnika i osobne czekanie. Przy
     * dziesięciu wyrobach to trzydzieści decyzji, z których żadna nie jest
     * ciekawa. Tutaj podejmuje je agregator — ten sam, który stoi za wyborem
     * „Automatycznie" — a człowiek dostaje gotowy komplet.
     *
     * WYNIK KROKU JEST WEJŚCIEM NASTĘPNEGO. Kolejność nie jest dowolna:
     * najpierw znika tło (maską, deterministycznie), potem poprawiamy światło
     * na czystym kadrze, a dopiero na końcu animujemy gotowy obraz. Odwrotnie
     * animacja pokazywałaby stary bałagan w tle, a poprawa światła walczyłaby
     * z cieniami, których już nie ma.
     *
     * Każdy krok wybiera silnik osobno, bo w trakcie liczenia coś może paść
     * albo wstać — Forge bywa zajęty, klucz Gemini potrafi trafić w limit.
     *
     * Animacja jest krokiem OPCJONALNYM w tym sensie, że jej awaria nie kasuje
     * poprawionego zdjęcia: kadr zostaje, a błąd trafia na ekran.
     */
    fun ciagAutomatyczny(
        zdjecie: Uri,
        coTo: String,
        proporcje: String,
        dodatkowe: String,
        gotoweZdjecie: (Uri) -> Unit,
        gotowaAnimacja: (Uri) -> Unit,
    ) {
        // Dopisek czlowieka doklejamy do KAZDEGO kroku. Brzmi to jak nadmiar,
        // ale „bez zoltego odcienia" albo „nie ruszaj napisu na spodzie" dotyczy
        // calej obrobki, a nie tylko tego kroku, w ktorym akurat zostalo wpisane.
        // Dopisek ucinamy na 300 znakach. Prompt idzie do modelu razem
        // z poleceniem zadania; wklejona strona tekstu nie doda niczego,
        // za to potrafi przykryc warunek „nie zmieniaj wyrobu".
        val dopisek = dodatkowe.trim().take(300)

        fun zDopiskiem(polecenie: String): String =
            if (dopisek.isBlank()) polecenie else polecenie + ", " + dopisek

        if (_stan.value.adresWarsztatu.isBlank()) {
            _stan.update {
                it.copy(blad = "Cały ciąg liczy komputer w warsztacie — wpisz jego adres w Pomocy.")
            }
            return
        }

        // Drugie klikniecie nie ma prawa puscic drugiego lancucha rownolegle:
        // oba pisalyby po tym samym stanie, a serwer i tak wykonuje zlecenia
        // po jednym, wiec drugi ciag tylko czekalby w kolejce i mieszal
        // komunikaty.
        if (_stan.value.agentPracuje) {
            _stan.update { it.copy(komunikat = "Poczekaj — poprzednia robota jeszcze trwa.") }
            return
        }

        viewModelScope.launch {
            _stan.update { it.copy(agentPracuje = true, komunikat = "Krok 1 z 3: wycinam tło...") }
            // Adres sprawdzony raz, na poczatku lancucha — trzy kroki ida tym
            // samym polaczeniem, wiec przelaczenie sieci w polowie ciagu
            // zerwaloby dopiero krok drugi albo trzeci.
            val adres = adresRoboczy()
            try {

            // --- krok 1: tło
            val silnikTla = silnikDo("zdjecie")
            // Gemini NIE MA tu swojej drogi: jego obrazy idą przez `agent`,
            // a cały ciąg jedzie przez serwer warsztatowy. Zamiast po cichu
            // podmieniać silnik, wpisujemy to wprost — Forge robi w tym kroku
            // dokładnie to, o co chodzi (maska, nie domysł modelu).
            val zadanieTla = when (silnikTla) {
                "meta" -> "zdjecie-meta"
                "flow" -> "zdjecie-flow"
                "copilot" -> "zdjecie-copilot"
                "forge", "gemini" -> "zdjecie-produktowe"
                else -> "zdjecie-produktowe"
            }
            val poTle = repozytorium.zlecWarsztatowi(
                adres, zadanieTla, zdjecie, zDopiskiem(Polecenia.tlo(coTo)), proporcje,
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
            val silnikSwiatla = silnikDo("zdjecie")
            val zadanieSwiatla = when (silnikSwiatla) {
                "meta" -> "zdjecie-meta"
                "flow" -> "zdjecie-flow"
                "copilot" -> "zdjecie-copilot"
                "forge", "gemini" -> "zdjecie-produktowe"
                else -> "zdjecie-produktowe"
            }
            val poSwietle = repozytorium.zlecWarsztatowi(
                adres, zadanieSwiatla, kadrBezTla, zDopiskiem(Polecenia.upieksz(coTo)), proporcje,
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
            val silnikRuchu = silnikDo("animacja")
            val zadanieRuchu = when (silnikRuchu) {
                "flow" -> "animacja-flow"
                "meta" -> "animacja-meta"
                "comfy" -> "animacja"
                else -> "animacja"
            }
            val poRuchu = repozytorium.zlecWarsztatowi(
                adres, zadanieRuchu, kadrGotowy, zDopiskiem(Polecenia.obrot(coTo)), proporcje,
            ) { etap -> _stan.update { it.copy(komunikat = "Krok 3 z 3 (animacja): $etap") } }

            when (poRuchu) {
                is Wynik.Jest -> {
                    pl.fwdrucik.sklep.narzedzia.GaleriaZapis.zapiszDoGalerii(
                        getApplication<Application>(),
                        poRuchu.dane,
                        czyWideo = true,
                    )
                    gotowaAnimacja(Uri.fromFile(poRuchu.dane))
                    dopiszCzynnosc("Ciąg auto — animacja", silnikRuchu, "gotowe", poRuchu.dane.absolutePath)
                    _stan.update {
                        it.copy(komunikat = "Gotowe: tło, światło i animacja. Wszystko zapisane w galerii.")
                    }
                }
                is Wynik.Blad -> {
                    dopiszCzynnosc("Ciąg auto — animacja", silnikRuchu, poRuchu.komunikat.take(90), udana = false)
                    _stan.update {
                        it.copy(blad = "Zdjęcie gotowe, animacja nie wyszła: " + poRuchu.komunikat)
                    }
                }
            }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Anulowanie to nie awaria — leci dalej, zeby korutyna
                // naprawde sie zakonczyla. `finally` i tak zgasi „pracuje".
                throw e
            } catch (e: Exception) {
                _stan.update { it.copy(blad = opisBledu(e)) }
            } finally {
                // JEDNO miejsce, w ktorym gasnie „pracuje".
                //
                // Wczesniej ustawialo sie to w trzech galeziach z osobna,
                // wiec kazdy wyjatek spoza tych galezi zostawial aplikacje
                // z zablokowanymi przyciskami az do ponownego uruchomienia.
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
     * Zdjęcie plus notatka w rodzaju „fioletowy brelok” — agent zwraca komplet
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

        if (klucz.isBlank() && adres.isBlank()) {
            _stan.update {
                it.copy(blad = "Wpisz klucz Gemini albo adres komputera w zakładce Pomoc.")
            }
            return
        }

        viewModelScope.launch {
            _stan.update { it.copy(agentPracuje = true) }
            try {
                // Gemini pierwszy, bo jako jedyny WIDZI zdjęcie. Muse czyta sam
                // tekst, więc bez notatki nie ma z czego pisać — i tak jest
                // uczciwiej niż zgadywanie materiału po nazwie.
                // Wybor z okienka ma pierwszenstwo nad ustawieniem w Pomocy:
                // przy stole decyduje to, co widac teraz, a nie co bylo wczoraj.
                val wybrany = silnikWybrany?.takeIf { it != "auto" }
                    ?: silnikDo("opis", maZdjecie = true, maNotatke = notatka.isNotBlank())
                val szkic = when (wybrany) {
                    "copilot" -> {
                        if (adres.isBlank()) throw java.io.IOException("Microsoft Copilot wymaga adresu komputera w Pomocy.")
                        agent.opiszPrzezCopilota(adres, notatka, _stan.value.poprawki, zdjecie)
                    }
                    "muse" -> {
                        if (adres.isBlank()) throw java.io.IOException("Muse wymaga adresu komputera w Pomocy.")
                        agent.opiszPrzezMuse(adres, notatka, _stan.value.poprawki, zdjecie)
                    }
                    else -> {
                        if (klucz.isNotBlank()) {
                            try {
                                agent.opiszZdjecie(
                                    klucz, zdjecie, notatka,
                                    wybor.model.ifBlank { _stan.value.modelOpisu },
                                    _stan.value.poprawki,
                                )
                            } catch (e: Exception) {
                                if (adres.isBlank() || notatka.isBlank()) throw e
                                dopiszCzynnosc(
                                    co = "Gemini odmówił — przechodzę na Muse",
                                    silnik = _stan.value.modelOpisu,
                                    wynik = opisBledu(e),
                                    udana = false,
                                )
                                _stan.update {
                                    it.copy(komunikat = "Gemini nie odpowiada — piszę przez Muse na komputerze...")
                                }
                                agent.opiszPrzezMuse(adres, notatka, _stan.value.poprawki, zdjecie)
                            }
                        } else {
                            agent.opiszPrzezMuse(adres, notatka, _stan.value.poprawki, zdjecie)
                        }
                    }
                }
                gotowe(szkic)
                _stan.update { it.copy(propozycjaAgenta = szkic) }
                dopiszCzynnosc(
                    co = "Opis ze zdjęcia",
                    silnik = when (wybrany) {
                        "copilot" -> "Copilot"
                        "muse" -> "Muse"
                        else -> if (klucz.isBlank()) "Muse" else _stan.value.modelOpisu
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

        // Gdy klucza nie ma, ale komputer stoi w warsztacie — poprawia Forge.
        // To ta sama robota (czyste tlo, wyrownane swiatlo) na wlasnej karcie,
        // za darmo i bez limitow. Muse tu nie pomoze: CLI nie widzi obrazu.
        if (klucz.isBlank()) {
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
                if (adres.isBlank()) {
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
        _stan.value = StanEkranu(zalogowany = false)
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
                skasujKopie(produkt.id)
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
        poZapisie: (Int) -> Unit = {},
    ) = wKtorymsMomencie {
        when (val w = repozytorium.zapisz(produkt, cenaZl, cenaPromoZl)) {
            is Wynik.Jest -> {
                val id = w.dane.id
                naucSie(produkt, cenaZl)
                skasujKopie(produkt.id)

                // 1. Animacja GIF / Wideo na pozycji 0 (pierwsze w sklepie)
                if (animacja != null) {
                    val sciezka = animacja.toString().lowercase()
                    val jestWideo = sciezka.endsWith(".mp4") || sciezka.endsWith(".webm") || sciezka.endsWith(".mov")
                    val opis = if (jestWideo) "Wideo obrotowe produktu" else "Animacja 360° produktu"
                    val wynikPliku = repozytorium.wgrajPlikProduktu(id, animacja, opis, pozycja = 0)
                    if (wynikPliku is Wynik.Blad) {
                        _stan.update { it.copy(blad = "Błąd wysyłania animacji: ${wynikPliku.komunikat}") }
                    }
                }

                // 2. Zdjęcie główne (okładka)
                if (glowneZdjecie != null) {
                    val z = repozytorium.wgrajZdjecie(id, glowneZdjecie, altZdjecia, 100)
                    if (z is Wynik.Blad) {
                        _stan.update { it.copy(blad = "Błąd wysyłania zdjęcia głównego: ${z.komunikat}") }
                    }
                }

                // 3. Dodatkowe zdjęcia galerii
                dodatkoweZdjecia.forEachIndexed { idx, kadr ->
                    val z = repozytorium.wgrajZdjecie(id, kadr, "$altZdjecia - kadr ${idx + 2}", 110 + (idx * 10))
                    if (z is Wynik.Blad) {
                        _stan.update { it.copy(blad = "Błąd wysyłania zdjęcia #${idx + 2}: ${z.komunikat}") }
                    }
                }

                _stan.update { it.copy(komunikat = "Zapisano „${produkt.nazwa}” i zaktualizowano w sklepie!") }
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
            blok()
            _stan.update { it.copy(ladowanie = false) }
        }
    }
}

/** Grosze na tekst do pola formularza: 12990 -> "129.90" (serwer przyjmie też przecinek). */
fun groszeNaPole(grosze: Int): String = String.format("%d.%02d", grosze / 100, grosze % 100)
