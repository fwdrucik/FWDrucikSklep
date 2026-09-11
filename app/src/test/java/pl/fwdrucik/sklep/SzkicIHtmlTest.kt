package pl.fwdrucik.sklep

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import pl.fwdrucik.sklep.dane.*
import pl.fwdrucik.sklep.narzedzia.AllegroFormat

class SzkicIHtmlTest {
    @Test fun starszaKopiaOtwieraAsystentaZAuto() {
        val kopia = Json.decodeFromString<KopiaRobocza>("""{"id":7,"nazwa":"Miska","opis_krotki":"Niebieska"}""")
        assertTrue(kopia.trybProsty)
        assertEquals("auto", kopia.modelTekstuAi)
        assertEquals("auto", kopia.modelObrazuAi)
        assertEquals("auto", kopia.modelWideoAi)
        assertEquals("Niebieska", kopia.opisKrotki)
    }

    @Test fun zapisZachowujeNoweFaktyModelTrybIMedia() {
        val kopia = KopiaRobocza(nazwa = "Miska", material = "żywica", wymiary = "20 cm",
            notatka = "niebiska miska", trybProsty = false, krokAsystenta = 2,
            modelTekstuAi = "local:polski", modelObrazuAi = "browser:flow-image", modelWideoAi = "local:comfy-video",
            zdjecie = "/private/glowne.jpg", dodatkoweKadry = listOf("/private/kadr.png"), animacja = "/private/film.mp4",
            allegroId = "123", allegroCena = "55,20", cenaPromo = "45", stan = "0",
            doUzupelnienia = listOf("wymiary"), ostrzezenieOpisu = "Sprawdź fakty", zrodloOpisu = "lokalne")
        assertEquals(kopia, Json.decodeFromString<KopiaRobocza>(Json.encodeToString(kopia)))
        assertEquals(kopia, kopia.copy(trybProsty = true).copy(trybProsty = false))
    }

    @Test fun wyczyszczonePolaKopiiMajaPierwszenstwoPrzedSerwerem() {
        val baza = Produkt(id = 9, nazwa = "Stara nazwa", opis = "Stary opis", cenaGr = 1000,
            cenaPromoGr = 800, stan = 3, allegroUrl = "https://example.invalid/123", allegroKategoria = "789",
            allegroParametry = "istniejące parametry", obrazy = listOf(Obraz(id = 5)))
        val odtworzony = KopiaRobocza(id = 9, nazwa = "Nowa nazwa", opis = "", cena = "", stan = "0").naProdukt(baza)
        assertEquals("Nowa nazwa", odtworzony.nazwa)
        assertEquals("", odtworzony.opis)
        assertNull(odtworzony.cenaPromoGr)
        assertEquals(0, odtworzony.cenaGr)
        assertNull(odtworzony.allegroUrl)
        assertEquals(0, odtworzony.stan)
        assertEquals("789", odtworzony.allegroKategoria)
        assertEquals(baza.obrazy, odtworzony.obrazy)
        assertEquals(baza.allegroParametry, odtworzony.allegroParametry)
    }

    @Test fun sameFaktyNieSaPustaKopia() {
        assertFalse(KopiaRobocza(material = "dąb").pusta)
        assertFalse(KopiaRobocza(wymiary = "30 cm").pusta)
        assertFalse(KopiaRobocza(stan = "0").pusta)
        assertTrue(KopiaRobocza().pusta)
    }

    @Test fun formatterEscapujeHtmlWszystkichPolIZachowujePolskiTekst() {
        val html = AllegroFormat.zbudujOpisHtml("Łódź <script>x</script>", "A & B", "Wysokość < 20 cm\r\n\"Żółta\" 'miska'")
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("Łódź &lt;script&gt;x&lt;/script&gt;"))
        assertTrue(html.contains("A &amp; B"))
        assertTrue(html.contains("Wysokość &lt; 20 cm<br>&quot;Żółta&quot; &#39;miska&#39;"))
    }

    @Test fun formatterNieDopisujeObietnicHandlowych() {
        val html = AllegroFormat.zbudujOpisHtml("Miska", "Niebieska", "20 cm")
        assertFalse(html.contains("Smart"))
        assertFalse(html.contains("100%"))
        assertFalse(html.contains("Dostawa"))
        assertTrue(html.contains("20 cm"))
    }

    @Test fun tytulFaktycznieKorzystaZFormatowaniaProdukcji() {
        val tytul = AllegroFormat.oczyscTytul("HIT SUPER Miska   z żywicy OKAZJA")
        assertEquals("Miska z żywicy", tytul)
        assertTrue(AllegroFormat.oczyscTytul("Wyrób ".repeat(30)).length <= 75)
    }
}
