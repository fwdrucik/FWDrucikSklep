package pl.fwdrucik.sklep.dane

import java.math.BigDecimal
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val utworzone: String = "",
    val dostawa: String = "",
    val platnosc: String = "",
    val pozycje: List<PozycjaZamowienia> = emptyList(),
    val adres: Map<String, String> = emptyMap(),
)

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

/** Statusy przepisane z FW_STATUSY_* w api/_sklep.php. */
object Statusy {
    val produktu = listOf("szkic", "opublikowany", "ukryty")
    val zamowienia = listOf("nowe", "oplacone", "w_realizacji", "wyslane", "zakonczone", "anulowane")

    fun opisProduktu(status: String) = when (status) {
        "opublikowany" -> "Widoczny w sklepie"
        "ukryty" -> "Schowany przed klientami"
        else -> "Szkic — tylko dla Ciebie"
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
