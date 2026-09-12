package pl.fwdrucik.sklep

import org.junit.Assert.*
import org.junit.Test
import pl.fwdrucik.sklep.dane.KopiaRobocza
import pl.fwdrucik.sklep.dane.Produkt
import pl.fwdrucik.sklep.siec.OdpowiedzWyceny
import pl.fwdrucik.sklep.siec.OfertaCenowa
import pl.fwdrucik.sklep.ui.StanEkranu

class StanWycenyTest {
    private fun odpowiedz() = OdpowiedzWyceny(ok = true, zrodlo = "tavily-chatgpt", liczbaOfert = 3,
        sugerowanaCena = 50.0, sugerowanaAllegro = 50.0, minCena = 40.0, maxCena = 60.0,
        sprawdzono = "2026-09-12T10:15:30Z", ostrzezenie = "Wyniki wyszukiwania: ceny ofertowe, nie ceny sprzedaży. Sprawdź porównywalność.",
        znalezione = listOf(40.0, 50.0, 60.0).mapIndexed { i, cena ->
            OfertaCenowa(portal = "Sklep", cena = cena, tytul = "Miska", url = "https://sklep.pl/$i",
                fragment = listOf("Miska 20 cm. Cena 40 zł", "Miska 20 cm. Cena 50 zł", "Miska 20 cm. Cena 60 zł")[i], waluta = "PLN")
        })

    private fun stanZeSzkicem() = StanEkranu(
        produkty = listOf(Produkt(id = 7, cenaGr = 4990)),
        kopieRobocze = mapOf(7 to KopiaRobocza(id = 7, cena = "49,90", allegroCena = "55,90", nazwa = "Moja miska",
            zdjecie = "wlasne.jpg", animacja = "film.mp4", dodatkoweKadry = listOf("detal.jpg"))),
    )

    @Test fun poprawnaCenaNiesieOstrzezenieZrodlaICzasBezZmianySzkicuIZdjec() {
        val przed = stanZeSzkicem()
        val po = przed.rozpocznijBadanieCeny().zWynikiemWyceny(odpowiedz())
        assertTrue(po.wycenaZmierzona)
        assertEquals(50.0, po.sugerowanaCenaRynkowa!!, 0.0)
        assertEquals("Telefon nie dolicza narzutu do kwoty serwera", 50.0, po.sugerowanaCenaAllegro!!, 0.0)
        assertEquals(odpowiedz().ostrzezenie, po.ostrzezenieWyceny)
        assertEquals("2026-09-12T10:15:30Z", po.sprawdzonoWyceny)
        assertEquals("https://sklep.pl/0", po.ofertyRynkowe.first().url)
        assertEquals(przed.kopieRobocze, po.kopieRobocze)
        assertEquals(przed.produkty, po.produkty)
    }

    @Test fun nowaProbaUsuwaWszystkieStareDowodyAleNieWpisanaCene() {
        val przed = stanZeSzkicem().copy(wycenaZmierzona = true, sugerowanaCenaRynkowa = 50.0, sugerowanaCenaAllegro = 56.0,
            minCenaRynkowa = 40.0, maxCenaRynkowa = 60.0, ofertyRynkowe = odpowiedz().znalezione,
            sprawdzonoWyceny = "2026-09-12T10:15:30Z", ostrzezenieWyceny = "Stare ostrzeżenie", bladWyceny = "Stary błąd")
        val po = przed.rozpocznijBadanieCeny()
        assertTrue(po.badanieCenyWToku)
        assertFalse(po.wycenaZmierzona)
        assertNull(po.sugerowanaCenaRynkowa)
        assertNull(po.sugerowanaCenaAllegro)
        assertNull(po.minCenaRynkowa)
        assertNull(po.maxCenaRynkowa)
        assertTrue(po.ofertyRynkowe.isEmpty())
        assertEquals("", po.sprawdzonoWyceny)
        assertNull(po.ostrzezenieWyceny)
        assertNull(po.bladWyceny)
        assertEquals(przed.kopieRobocze, po.kopieRobocze)
    }

    @Test fun odmowaNieZostawiaCenyDoUzyciaIZachowujeWyjasnienie() {
        val po = stanZeSzkicem().copy(wycenaZmierzona = true, sugerowanaCenaRynkowa = 99.0)
            .zWynikiemWyceny(odpowiedz().copy(ok = false, blad = "Limit wyszukiwania"))
        assertFalse(po.wycenaZmierzona)
        assertNull(po.sugerowanaCenaRynkowa)
        assertFalse(po.badanieCenyWToku)
        assertTrue(po.bladWyceny!!.contains("Limit wyszukiwania"))
        assertEquals(odpowiedz().ostrzezenie, po.ostrzezenieWyceny)
        assertEquals("49,90", po.kopieRobocze[7]!!.cena)
    }
}
