package pl.fwdrucik.sklep

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.fwdrucik.sklep.narzedzia.AllegroFormat
import pl.fwdrucik.sklep.siec.OdpowiedzWyceny

/**
 * ETAP105 — wycena musi się przyznawać, skąd ma liczbę.
 *
 * Do etapu 104 serwer po cichu podstawiał oszacowanie z tabeli słów
 * kluczowych, gdy nie dosięgnął Allegro, a ekran pokazywał je identycznie
 * jak cenę zmierzoną. Dla frazy „stolik z drutu" dawało to 682 zł przy
 * rzeczywistej medianie 228 zł — trzykrotnie za drogo, czyli wyrób, którego
 * nikt nie kupi. Te testy pilnują, żeby to rozróżnienie nie zniknęło.
 */
class WycenaRynkowaTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test fun bladLubPustyListingNigdyNieDajePomiaruDoZastosowania() {
        assertFalse(OdpowiedzWyceny(ok = false, zrodlo = "allegro-api", sugerowanaCena = 99.0, liczbaOfert = 3).zmierzoneNaRynku)
        assertFalse(OdpowiedzWyceny(ok = true, zrodlo = "allegro-listing", sugerowanaCena = 99.0, liczbaOfert = 0).zmierzoneNaRynku)
        assertFalse(OdpowiedzWyceny(ok = true, zrodlo = "allegro-estymacja", sugerowanaCena = 99.0, liczbaOfert = 3).zmierzoneNaRynku)
    }

    @Test fun brakDostepu403ZachowujeWyjasnienieZamiastCeny() {
        val odp = json.decodeFromString<OdpowiedzWyceny>("""{"ok":false,"zrodlo":"brak","liczba_ofert":0,"sugerowana_cena":0,"sugerowana_allegro":0,"min_cena":0,"max_cena":0,"blad":"Allegro nie przyznało dostępu do listingu (403 AccessDenied). Wpisz cenę ręcznie."}""")
        assertFalse(odp.zmierzoneNaRynku)
        assertEquals(0.0, odp.sugerowanaCena, 0.0)
        assertTrue(odp.blad!!.contains("Wpisz cenę ręcznie"))
    }

    @Test
    fun listingZAllegroJestOznaczonyJakoZmierzony() {
        val odp = json.decodeFromString<OdpowiedzWyceny>(
            """
            {"ok":true,"fraza":"stolik z drutu","zrodlo":"allegro-listing",
             "sugerowana_cena":228.0,"sugerowana_allegro":255.0,"srednia_cena":210.0,
             "min_cena":65.0,"max_cena":380.0,"liczba_ofert":73,
             "znalezione":[{"portal":"Allegro","cena":227.63,"tytul":"Stolik Kawowy Z Drutu"}]}
            """.trimIndent()
        )

        assertTrue(odp.zmierzoneNaRynku)
        assertNull("Zmierzona cena nie ma powodu nieść ostrzeżenia", odp.ostrzezenie)
        assertEquals(228.0, odp.sugerowanaCena, 0.001)
        assertEquals(73, odp.liczbaOfert)
    }

    @Test
    fun restApiTezLiczySieJakoZmierzony() {
        // Gdy Allegro przyzna dostęp do /offers/listing, źródło zmieni nazwę
        // na „allegro-api" i ekran nie może wtedy pokazać ostrzeżenia.
        val odp = json.decodeFromString<OdpowiedzWyceny>(
            """{"ok":true,"zrodlo":"allegro-api","sugerowana_cena":300.0,"liczba_ofert":1,"znalezione":[{"portal":"Allegro","cena":300,"tytul":"Stolik"}]}"""
        )
        assertTrue(odp.zmierzoneNaRynku)
    }

    @Test
    fun estymacjaNieUdajeCenyRynkowej() {
        val odp = json.decodeFromString<OdpowiedzWyceny>(
            """
            {"ok":true,"fraza":"stolik z drutu","zrodlo":"estymacja",
             "sugerowana_cena":682.0,"sugerowana_allegro":764.0,
             "min_cena":585.0,"max_cena":748.0,"liczba_ofert":3,
             "ostrzezenie":"To NIE jest cena rynkowa, tylko oszacowanie z tabeli pracowni."}
            """.trimIndent()
        )

        assertFalse(odp.zmierzoneNaRynku)
        assertNotNull("Estymacja musi przyjść z powodem", odp.ostrzezenie)
        assertTrue(odp.ostrzezenie!!.contains("NIE jest cena rynkowa"))
    }

    @Test
    fun brakPolaZrodloNieUchodziZaPomiarRynku() {
        // Starszy serwer nie zna pola `zrodlo`. Domyślna pusta wartość musi
        // wypaść na stronę ostrożną, nie na „zmierzone".
        val odp = json.decodeFromString<OdpowiedzWyceny>(
            """{"ok":true,"sugerowana_cena":100.0}"""
        )
        assertFalse(odp.zmierzoneNaRynku)
    }

    @Test
    fun bufor12ProcentLiczyOdCenySklepowej() {
        // Ta sama reguła, co po stronie serwera (PROWIZJA_ALLEGRO = 0.12).
        assertEquals(255.0, AllegroFormat.obliczCeneAllegro(228.0), 0.5)
        assertEquals(1165.0, AllegroFormat.obliczCeneAllegro(1040.0), 0.5)
    }

    @Test
    fun tytulZListinguDaSieSkrocicDoLimituAllegro() {
        // Tytuły z listingu bywają dłuższe niż 75 znaków — kreator podstawia
        // je jako propozycję nazwy, więc muszą przejść przez czyszczenie.
        val zListingu = "Stolik Kawowy Z Drutu Okrągły Industrialny 55x55x37 Czarny + Blat Dąb Lancelot Loft"
        assertTrue("Materiał testowy ma sens tylko, gdy przekracza limit", zListingu.length > 75)

        val oczyszczony = AllegroFormat.oczyscTytul(zListingu)
        assertTrue("Po skróceniu tytuł musi zmieścić się w 75 znakach", oczyszczony.length <= 75)
        assertFalse("Skracanie nie może zostawić urwanego słowa na końcu", oczyszczony.endsWith(" "))
        assertTrue(oczyszczony.startsWith("Stolik Kawowy Z Drutu"))
    }
}
