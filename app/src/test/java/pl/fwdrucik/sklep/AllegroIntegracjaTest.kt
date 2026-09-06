package pl.fwdrucik.sklep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.fwdrucik.sklep.dane.Produkt

class AllegroIntegracjaTest {

    private val buzzwords = listOf(
        "HIT", "OKAZJA", "NOWOŚĆ", "NOWOSC", "WYPRZEDAŻ", "WYPRZEDAZ",
        "POLECAM", "SUPER", "CENA", "GRATIS", "BESTSELLER"
    )

    @Test
    fun testPrzeliczenieCenyAllegroZBuforem12Procent() {
        val cenaSklepGrosze = 16000 // 160.00 zł
        val cenaAllegroGrosze = (cenaSklepGrosze * 1.12).toInt() // 17920 groszy
        assertEquals(17920, cenaAllegroGrosze)

        val cenaStolikSklep = 85000 // 850.00 zł
        val cenaStolikAllegro = (cenaStolikSklep * 1.12).toInt() // 95200 groszy
        assertEquals(95200, cenaStolikAllegro)
    }

    @Test
    fun testLimitTytuluAllegroOrazBuzzwordy() {
        val tytulPrawidlowy = "Stolik Kawowy Plaster Dębu Żywica Epoksydowa Loft Stalowy Stelaż"
        assertTrue("Tytuł powinien mieścić się w 75 znakach", tytulPrawidlowy.length <= 75)
        assertEquals(64, tytulPrawidlowy.length)

        val tytulZBuzzwordami = "HIT OKAZJA Stolik Kawowy Dębowy Żywica Epoksydowa SUPER CENA GRATIS!"
        val slowa = tytulZBuzzwordami.split(Regex("[\\s,.;:!?/\\\\-]+"))
        val znalezioneBuzzwordy = slowa.map { it.uppercase().trim() }.filter { it in buzzwords }

        assertTrue(znalezioneBuzzwordy.contains("HIT"))
        assertTrue(znalezioneBuzzwordy.contains("OKAZJA"))
        assertTrue(znalezioneBuzzwordy.contains("SUPER"))
        assertTrue(znalezioneBuzzwordy.contains("CENA"))
        assertTrue(znalezioneBuzzwordy.contains("GRATIS"))

        // Czyszczenie ze słów zakazanych
        var tytulOczyszczony = tytulZBuzzwordami
        for (b in buzzwords) {
            tytulOczyszczony = tytulOczyszczony.replace(Regex("\\b$b\\b", RegexOption.IGNORE_CASE), "")
        }
        tytulOczyszczony = tytulOczyszczony.replace(Regex("!+"), "").replace(Regex("\\s{2,}"), " ").trim()

        assertEquals("Stolik Kawowy Dębowy Żywica Epoksydowa", tytulOczyszczony)
        assertTrue(tytulOczyszczony.length <= 75)
    }

    @Test
    fun testModelProduktuPolaAllegro() {
        val produkt = Produkt(
            id = 1,
            slug = "patera-3-poziomowa",
            nazwa = "Patera 3-poziomowa z żywicy epoksydowej żółto-złota",
            kategoria = "Żywica epoksydowa",
            cenaGr = 16000,
            allegroId = "18905741114",
            allegroUrl = "https://allegro.pl/oferta/18905741114",
            allegroKategoria = "110888",
            allegroCenaGr = 17900,
            allegroStatus = "szkic"
        )

        assertEquals("18905741114", produkt.allegroId)
        assertEquals("https://allegro.pl/oferta/18905741114", produkt.allegroUrl)
        assertEquals("szkic", produkt.allegroStatus)
        assertEquals(17900, produkt.allegroCenaGr)
        assertEquals("110888", produkt.allegroKategoria)
        assertFalse(produkt.brakNaStanie)
    }

    @Test
    fun testStrukturaSekcjiOpisuAllegroRestApi() {
        val dozwoloneTagi = setOf("h2", "p", "ul", "li", "b")
        val przykladowyOpisAllegro = "<h2>O wyrobie</h2><p>Rękodzieło z żywicy epoksydowej.</p><ul><li>Wymiary: 25 cm</li></ul>"

        // Weryfikacja czy zawiera dozwolone tagi
        val tagiWOpisie = Regex("<([a-zA-Z0-9]+)[^>]*>").findAll(przykladowyOpisAllegro)
            .map { it.groupValues[1].lowercase() }
            .toSet()

        for (tag in tagiWOpisie) {
            assertTrue("Tag <$tag> musi być dozwolony w Allegro REST API", tag in dozwoloneTagi)
        }

        // Sprawdzenie czy niedozwolony tag 'div' lub 'table' jest wykrywany
        val nieprawidlowyHtml = "<div><p>Tekst</p><table><tr><td>Komorka</td></tr></table></div>"
        val niedozwoloneZnalezione = Regex("<([a-zA-Z0-9]+)[^>]*>").findAll(nieprawidlowyHtml)
            .map { it.groupValues[1].lowercase() }
            .filter { it !in dozwoloneTagi }
            .toSet()

        assertTrue(niedozwoloneZnalezione.contains("div"))
        assertTrue(niedozwoloneZnalezione.contains("table"))
        assertTrue(niedozwoloneZnalezione.contains("tr"))
        assertTrue(niedozwoloneZnalezione.contains("td"))
    }
}
