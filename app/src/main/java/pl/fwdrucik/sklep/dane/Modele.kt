package pl.fwdrucik.sklep.dane

import java.math.BigDecimal
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Odpowiedniki tego, co oddaje api/sklep.php. Nazwy pól są celowo takie same
 * jak w PHP — przy zmianie po stronie serwera od razu widać, co poprawić tutaj.
 *
 * Wszystko, co serwer może pominąć, jest tu z wartością domyślną. Aplikacja
 * administratora nie ma prawa wywalić się dlatego, że backend dodał pole.
 */

@Serializable
data class Obraz(
    val id: Int = 0,
    val src: String = "",
    val alt: String = "",
    val szerokosc: Int = 0,
    val wysokosc: Int = 0,
)

@Serializable
data class Produkt(
    val id: Int = 0,
    val slug: String = "",
    val nazwa: String = "",
    val kategoria: String = "inne",
    @SerialName("opis_krotki") val opisKrotki: String = "",
    val opis: String = "",
    @SerialName("cena_gr") val cenaGr: Int = 0,
    // null znaczy "brak promocji" — nie to samo co 0.
    @SerialName("cena_promo_gr") val cenaPromoGr: Int? = null,
    // null znaczy "wykonywane na zamówienie", 0 znaczy "chwilowo nie ma".
    // Ta różnica przechodzi przez cały sklep aż do danych dla Google.
    val stan: Int? = null,
    @SerialName("waga_g") val wagaG: Int = 0,
    val jednostka: String = "szt.",
    @SerialName("czas_realizacji") val czasRealizacji: String = "",
    val status: String = "szkic",
    val pozycja: Int = 100,
    val url: String = "",
    val obrazy: List<Obraz> = emptyList(),
    val pliki: List<PlikProduktu> = emptyList(),
    @SerialName("allegro_id") val allegroId: String? = null,
    @SerialName("allegro_url") val allegroUrl: String? = null,
    @SerialName("allegro_kategoria") val allegroKategoria: String? = null,
    @SerialName("allegro_cena_gr") val allegroCenaGr: Int? = null,
    @SerialName("allegro_status") val allegroStatus: String = "brak",
    @SerialName("allegro_parametry") val allegroParametry: String? = null,
) {
    val cenaObowiazujaca: Int get() = cenaPromoGr ?: cenaGr
    val naZamowienie: Boolean get() = stan == null
    val brakNaStanie: Boolean get() = stan != null && stan < 1
    val malyStan: Boolean get() = stan != null && stan in 1..3
}

@Serializable
data class PozycjaZamowienia(
    val nazwa: String = "",
    @SerialName("cena_gr") val cenaGr: Int = 0,
    val ilosc: Int = 1,
)

@Serializable
data class Zamowienie(
    val id: Int = 0,
    val numer: String = "",
    val status: String = "nowe",
    val nick: String = "",
    @SerialName("suma_gr") val sumaGr: Int = 0,
    @SerialName("towar_gr") val towarGr: Int = 0,
    @SerialName("dostawa_gr") val dostawaGr: Int = 0,
    val utworzone: String = "",
    @SerialName("sposob_dostawy") val sposobDostawy: String = "",
    @SerialName("sposob_platnosci") val sposobPlatnosci: String = "",
    val uwagi: String = "",
    val email: String = "",
    val gosc: Int = 0,
    /**
     * Numer listu przewozowego. Pusty, dopoki paczka nie wyszla.
     *
     * Trafia do maila, ktory sklep wysyla klientowi przy statusie „wyslane" —
     * to jedyna droga, ktora ma kupujacy bez konta.
     */
    val przesylka: String = "",
    val pozycje: List<PozycjaZamowienia> = emptyList(),
    val adres: Map<String, JsonElement> = emptyMap(),
) {
    // Zachowanie zgodnosci z dotychczasowym UI
    val dostawa: String get() = sposobDostawy
    val platnosc: String get() = sposobPlatnosci
}

// ------------------------------------------------------------- odpowiedzi

@Serializable
data class OdpowiedzProduktow(val ok: Boolean = false, val produkty: List<Produkt> = emptyList())

@Serializable
data class OdpowiedzZamowien(val ok: Boolean = false, val zamowienia: List<Zamowienie> = emptyList())

@Serializable
data class OdpowiedzZapisu(
    val ok: Boolean = false,
    val id: Int = 0,
    val slug: String = "",
    val url: String = "",
)

@Serializable
data class OdpowiedzObrazu(val ok: Boolean = false, val obraz: Obraz? = null)

/**
 * Plik produktu, ktory nie jest zdjeciem: film, animacja, model 3D albo szkic.
 *
 * Osobno od `Obraz`, bo nie ma szerokosci ani wysokosci, za to ma rodzaj
 * i typ MIME — po nich sklep decyduje, czy pokazac odtwarzacz, ruchomy
 * obrazek czy odnosnik do pobrania.
 */
@Serializable
data class PlikProduktu(
    val id: Int = 0,
    val src: String = "",
    /** „wideo", „animacja", „model" albo „szkic" — prosto z api/sklep.php. */
    val rodzaj: String = "",
    val mime: String = "",
    val opis: String = "",
    val rozmiar: Long = 0,
)

@Serializable
data class OdpowiedzPliku(val ok: Boolean = false, val plik: PlikProduktu? = null)

@Serializable
data class Uzytkownik(
    val id: Int = 0,
    val nick: String = "",
    val email: String = "",
    val rola: String = "",
) {
    val jestAdminem: Boolean get() = rola == "admin"
}

@Serializable
data class OdpowiedzLogowania(
    val ok: Boolean = false,
    val uzytkownik: Uzytkownik? = null,
    val blad: String = "",
)

@Serializable
data class OdpowiedzOgolna(val ok: Boolean = false, val blad: String = "", val status: String = "")

/**
 * Pamiec robocza kreatora — to, co wpisales, zanim cokolwiek poszlo na serwer.
 *
 * PO CO: szkic powstaje przy stole, jedna reka, czesto z telefonem odkladanym
 * w polowie. Dotad przerwana proba znikala bez sladu — telefon uspiony przez
 * system albo cofniecie z kreatora kasowalo dziesiec minut pisania razem
 * z opisem od agenta, ktory kosztowal zapytanie do Gemini.
 *
 * Kopia siedzi wylacznie na telefonie i nie ma nic wspolnego ze statusem
 * produktu w sklepie: `szkic` to stan NA SERWERZE, a to jest zapis roboczy,
 * o ktorym serwer nic nie wie.
 */
@Serializable
data class KopiaRobocza(
    /** 0 dla nowego produktu; id istniejacego przy poprawianiu. */
    val id: Int = 0,
    val nazwa: String = "",
    val kategoria: String = "",
    @SerialName("opis_krotki") val opisKrotki: String = "",
    val opis: String = "",
    val cena: String = "",
    @SerialName("cena_promo") val cenaPromo: String = "",
    val stan: String = "",
    val jednostka: String = "",
    val waga: String = "",
    val czas: String = "",
    val status: String = "szkic",
    val pozycja: String = "",
    val notatka: String = "",
    @SerialName("opis_zdjecia") val opisZdjecia: String = "",
    /**
     * Sciezka do KOPII zdjecia w pamieci aplikacji, nie adres z galerii.
     *
     * Adres `content://` z galerii jest wazny tylko dopoty, dopoki zyje
     * uprawnienie nadane przy wyborze — po restarcie aplikacji wskazuje
     * w prozne. Dlatego przy pierwszym zapisie kopiujemy plik do siebie.
     */
    val zdjecie: String = "",
    val dodatkoweKadry: List<String> = emptyList(),
    val animacja: String = "",
    val sekcjaOtwarta: Int = 1,
    val allegroUrl: String = "",
    val allegroCena: String = "",
    val allegroId: String = "",
    val allegroStatus: String = "brak",
    /** null: stara kopia bez tego pola; pusty tekst: świadomie wyczyszczone. */
    val allegroKategoria: String? = null,
    val allegroKategoriaNazwa: String = "",
    val allegroKategoriaSciezka: String = "",
    val sugerowanaCenaRynkowa: String = "",
    val sugerowanaCenaAllegro: String = "",
    val zakresCen: String = "",
    val trybProsty: Boolean = true,
    val krokAsystenta: Int = 1,
    val material: String = "",
    val wymiary: String = "",
    val modelTekstuAi: String = "auto",
    val modelObrazuAi: String = "auto",
    val modelWideoAi: String = "auto",
    val doUzupelnienia: List<String> = emptyList(),
    val zrodloOpisu: String = "",
    val ostrzezenieOpisu: String = "",
    /** Kiedy zapisana — millisekundy, do pokazania godziny na banerze i limitu 24h. */
    val zapisano: Long = 0,
) {
    /** Pusta kopia nie ma czego przywracac — nie zawracamy nia glowy. */
    val pusta: Boolean
        get() = listOf(nazwa, opisKrotki, opis, cena, notatka, zdjecie, animacja,
            material, wymiary, cenaPromo, stan, waga, czas, allegroUrl, allegroId,
            allegroKategoria.orEmpty()).all { it.isBlank() } && dodatkoweKadry.isEmpty()

    /** Także puste pola są świadomą zmianą. Nie zastępuj ich danymi z serwera. */
    fun naProdukt(baza: Produkt): Produkt = baza.copy(
        nazwa = nazwa, kategoria = kategoria.ifBlank { "inne" }, opisKrotki = opisKrotki, opis = opis,
        cenaGr = zloteNaGrosze(cena) ?: 0, cenaPromoGr = zloteNaGrosze(cenaPromo),
        stan = stan.toIntOrNull(), jednostka = jednostka.ifBlank { "szt." }, wagaG = waga.toIntOrNull() ?: 0,
        czasRealizacji = czas, status = status, pozycja = pozycja.toIntOrNull() ?: 100,
        allegroUrl = allegroUrl.ifBlank { null }, allegroId = allegroId.ifBlank { null },
        allegroCenaGr = zloteNaGrosze(allegroCena), allegroStatus = allegroStatus,
        allegroKategoria = if (allegroKategoria == null) baza.allegroKategoria else allegroKategoria.ifBlank { null },
    )
}

/** Statusy przepisane z FW_STATUSY_* w api/_sklep.php. */
object Statusy {
    val produktu = listOf("szkic", "opublikowany", "ukryty")
    val zamowienia = listOf("nowe", "oplacone", "w_realizacji", "wyslane", "zakonczone", "anulowane")

    /** Krotka nazwa na przycisk — tak, jak sie o tym mowi na co dzien. */
    fun nazwaProduktu(status: String) = when (status) {
        "opublikowany" -> "Publiczny"
        "ukryty" -> "Prywatny"
        else -> "W przygotowaniu"
    }

    /** Zdanie wyjasniajace, co ten stan naprawde znaczy dla klienta. */
    fun opisProduktu(status: String) = when (status) {
        "opublikowany" -> "Widoczny w sklepie"
        "ukryty" -> "Schowany przed klientami"
        else -> "Szkic — tylko dla Ciebie"
    }

    /**
     * Nastepny stan w kolku: w przygotowaniu -> publiczny -> prywatny -> ...
     *
     * Kolejnosc nie jest przypadkowa: z przygotowania idzie sie do publikacji,
     * a wycofanie ze sklepu to krok dalej, nie powrot do szkicu. Szkic znaczy
     * „jeszcze nad tym pracuje", prywatny znaczy „gotowe, ale nie teraz".
     */
    fun nastepnyProduktu(status: String) = when (status) {
        "szkic" -> "opublikowany"
        "opublikowany" -> "ukryty"
        else -> "szkic"
    }

    fun opisZamowienia(status: String) = when (status) {
        "nowe" -> "Nowe"
        "oplacone" -> "Opłacone"
        "w_realizacji" -> "W realizacji"
        "wyslane" -> "Wysłane"
        "zakonczone" -> "Zakończone"
        "anulowane" -> "Anulowane"
        else -> status
    }
}

/** Grosze na tekst, jaki widzi człowiek: 12990 -> "129,90 zł". */
fun groszeNaZlote(grosze: Int): String =
    String.format(Locale("pl", "PL"), "%d,%02d zł", grosze / 100, grosze % 100)

/** Odwrotnie — pole tekstowe na grosze, tolerancyjnie jak fw_grosze() w PHP. */
fun zloteNaGrosze(tekst: String): Int? {
    val czyste = tekst.trim().replace(" ", "").replace(" ", "").replace(',', '.')
    if (czyste.isEmpty()) return null
    if (!Regex("""^\d+(\.\d{1,2})?$""").matches(czyste)) return null
    return runCatching {
        BigDecimal(czyste).movePointRight(2).intValueExact()
    }.getOrNull()
}
