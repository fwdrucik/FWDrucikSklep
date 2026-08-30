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
import pl.fwdrucik.sklep.SklepAplikacja
import pl.fwdrucik.sklep.dane.Produkt
import pl.fwdrucik.sklep.dane.SzkicProduktu
import pl.fwdrucik.sklep.siec.StanSerwera
import pl.fwdrucik.sklep.dane.Wynik
import pl.fwdrucik.sklep.dane.TrojkaFirebase
import pl.fwdrucik.sklep.dane.Zamowienie

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
    /** ETAP77: dostep do wspolnej bazy z panelem warsztatowym. */
    val chmura: TrojkaFirebase = TrojkaFirebase(),
)

class ModelSklepu(aplikacja: Application) : AndroidViewModel(aplikacja) {

    private val repozytorium = SklepAplikacja.z(aplikacja).repozytorium
    private val agent = SklepAplikacja.z(aplikacja).agent
    private val ustawienia = SklepAplikacja.z(aplikacja).ustawienia
    private val warsztat = SklepAplikacja.z(aplikacja).warsztat

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
                _stan.update { it.copy(adresWarsztatu = adres) }
            }
        }
        viewModelScope.launch {
            ustawienia.chmura.collect { cfg ->
                _stan.update { it.copy(chmura = cfg) }
            }
        }
        pilnujKontrolki()
    }

    /**
     * Odpytywanie serwera warsztatowego o kontrolke.
     *
     * Co dwadziescia sekund, w kolko przez cale zycie ekranu. Rzadziej mijaloby
     * sie z celem — roznica miedzy czerwonym a zielonym to okolo dwoch minut
     * czekania na wynik, wiec warto ja widziec, zanim sie zleci robote.
     * Czesciej nie ma sensu: backendy nie wstaja szybciej.
     */
    private fun pilnujKontrolki() {
        viewModelScope.launch {
            while (true) {
                val adres = _stan.value.adresWarsztatu
                if (adres.isNotBlank()) {
                    try {
                        val s: StanSerwera = warsztat.stan("$adres/stan")
                        _stan.update {
                            it.copy(swiatloWarsztatu = s.swiatlo, opisWarsztatu = s.opis)
                        }
                    } catch (e: Exception) {
                        // Wylaczony komputer to normalny stan, nie awaria —
                        // pokazujemy to kolorem, nie komunikatem bledu.
                        _stan.update {
                            it.copy(
                                swiatloWarsztatu = "brak",
                                opisWarsztatu = "Komputer wylaczony albo poza siecia",
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
        gotowe: (Uri) -> Unit,
    ) {
        val adres = _stan.value.adresWarsztatu
        if (adres.isBlank()) {
            _stan.update { it.copy(blad = "Brak adresu serwera warsztatowego. Ustaw go w Pomocy.") }
            return
        }
        viewModelScope.launch {
            _stan.update { it.copy(agentPracuje = true, komunikat = "Wysyłam na komputer...") }
            val w = repozytorium.zlecWarsztatowi(adres, zadanie, zdjecie, opis) { etap ->
                _stan.update { it.copy(komunikat = "Komputer: $etap") }
            }
            when (w) {
                is Wynik.Jest -> {
                    gotowe(Uri.fromFile(w.dane))
                    _stan.update { it.copy(komunikat = "Gotowe — sprawdź wynik") }
                }
                is Wynik.Blad -> _stan.update { it.copy(blad = w.komunikat) }
            }
            _stan.update { it.copy(agentPracuje = false) }
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
    fun opiszZeZdjecia(zdjecie: Uri, notatka: String, gotowe: (SzkicProduktu) -> Unit) {
        val klucz = _stan.value.kluczGemini
        if (klucz.isBlank()) {
            _stan.update { it.copy(blad = "Najpierw wpisz klucz Gemini w zakładce Pomoc.") }
            return
        }
        viewModelScope.launch {
            _stan.update { it.copy(agentPracuje = true) }
            try {
                val szkic = agent.opiszZdjecie(klucz, zdjecie, notatka)
                gotowe(szkic)
                _stan.update {
                    it.copy(komunikat = "Opis gotowy — sprawdź go przed publikacją")
                }
            } catch (e: Exception) {
                _stan.update { it.copy(blad = opisBledu(e)) }
            } finally {
                _stan.update { it.copy(agentPracuje = false) }
            }
        }
    }

    /** Poprawione zdjęcie produktowe. Oryginał zostaje — decyzja należy do Ciebie. */
    fun poprawZdjecie(zdjecie: Uri, gotowe: (Uri) -> Unit) {
        val klucz = _stan.value.kluczGemini
        if (klucz.isBlank()) {
            _stan.update { it.copy(blad = "Najpierw wpisz klucz Gemini w zakładce Pomoc.") }
            return
        }
        viewModelScope.launch {
            _stan.update { it.copy(agentPracuje = true) }
            try {
                val plik = agent.poprawZdjecie(klucz, zdjecie)
                gotowe(Uri.fromFile(plik))
                _stan.update { it.copy(komunikat = "Zdjęcie poprawione — porównaj z oryginałem") }
            } catch (e: Exception) {
                _stan.update { it.copy(blad = opisBledu(e)) }
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
    fun zapisz(
        produkt: Produkt,
        cenaZl: String,
        cenaPromoZl: String,
        poZapisie: (Int) -> Unit = {},
    ) = wKtorymsMomencie {
        when (val w = repozytorium.zapisz(produkt, cenaZl, cenaPromoZl)) {
            is Wynik.Jest -> {
                _stan.update { it.copy(komunikat = "Zapisano „${produkt.nazwa}”") }
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
    fun zapiszZeZdjeciem(
        produkt: Produkt,
        cenaZl: String,
        cenaPromoZl: String,
        zdjecie: Uri?,
        altZdjecia: String,
        poZapisie: (Int) -> Unit = {},
    ) = wKtorymsMomencie {
        when (val w = repozytorium.zapisz(produkt, cenaZl, cenaPromoZl)) {
            is Wynik.Jest -> {
                val id = w.dane.id
                if (zdjecie != null) {
                    when (val z = repozytorium.wgrajZdjecie(id, zdjecie, altZdjecia, 100)) {
                        is Wynik.Jest -> _stan.update {
                            it.copy(komunikat = "Zapisano „${produkt.nazwa}” razem ze zdjęciem")
                        }
                        is Wynik.Blad -> _stan.update {
                            it.copy(blad = "Produkt zapisany, ale zdjęcie nie poszło: ${z.komunikat}")
                        }
                    }
                } else {
                    _stan.update { it.copy(komunikat = "Zapisano „${produkt.nazwa}”") }
                }
                odswiezProdukty()
                poZapisie(id)
            }
            is Wynik.Blad -> _stan.update { it.copy(blad = w.komunikat) }
        }
    }

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

    fun usunZdjecie(id: Int) = wKtorymsMomencie {
        gdyDobrze(repozytorium.usunObraz(id), "Zdjęcie usunięte") { odswiezProdukty() }
    }

    fun zmienStatusZamowienia(id: Int, status: String) = wKtorymsMomencie {
        gdyDobrze(repozytorium.statusZamowienia(id, status), "Status zamówienia zmieniony") {
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
