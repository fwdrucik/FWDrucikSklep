package pl.fwdrucik.sklep

import org.junit.Assert.*
import org.junit.Test
import pl.fwdrucik.sklep.dane.KopiaRobocza
import pl.fwdrucik.sklep.dane.Produkt
import pl.fwdrucik.sklep.dane.SzkicProduktu
import pl.fwdrucik.sklep.dane.Zamowienie
import pl.fwdrucik.sklep.dane.DOMYSLNY_ADRES_WARSZTATU
import pl.fwdrucik.sklep.ui.Czynnosc
import pl.fwdrucik.sklep.ui.StanEkranu

class StanWarsztatuTest {
    @Test fun wylogowanieZostawiaAdresDoPonownegoLogowaniaBezEmisjiUstawien() {
        val przed = StanEkranu(zalogowany = true, adresWarsztatu = "http://192.0.2.23:8770")

        val po = przed.poWylogowaniu()

        assertFalse(po.zalogowany)
        assertEquals("http://192.0.2.23:8770", po.adresWarsztatu)
        assertEquals("http://192.0.2.23:8770", po.adresDoKontrolki(""))
    }

    @Test fun wylogowanieNieZachowujeDanychSesjiAniPrywatnejZawartosciEkranu() {
        val przed = StanEkranu(
            zalogowany = true,
            adresWarsztatu = "http://192.0.2.23:8770",
            produkty = listOf(Produkt(id = 7, nazwa = "Roboczy produkt")),
            zamowienia = listOf(Zamowienie(id = 9, email = "test@example.invalid")),
            kopieRobocze = mapOf(7 to KopiaRobocza(nazwa = "Prywatna notatka")),
            kopieWczytane = true,
            kluczGemini = "wartosc-testowa",
            blad = "Prywatny komunikat",
            komunikat = "Dane sesji testowej",
            propozycjaAgenta = SzkicProduktu(opis = "Prywatna propozycja"),
            czynnosci = listOf(Czynnosc("Opis", "test", "Prywatny wynik", czas = 1)),
            aktywnyKreatorId = 7,
            swiatloWarsztatu = "zielony",
            opisWarsztatu = "Poprzedni stan",
        )

        val po = przed.poWylogowaniu()

        // Cały stan poza zwykłym adresem ma wrócić do wartości początkowych.
        assertEquals(StanEkranu(), po.copy(adresWarsztatu = ""))
    }

    @Test fun pustyStartKontrolkiKorzystaZZapisanegoAdresu() {
        assertEquals("http://192.0.2.23:8770", StanEkranu().adresDoKontrolki("http://192.0.2.23:8770"))
    }

    @Test fun kontrolkaPrzedWczytaniemUstawienMaAdresDoPierwszejProby() {
        assertEquals(DOMYSLNY_ADRES_WARSZTATU, StanEkranu().adresDoKontrolki(""))
    }

    @Test fun bialeZnakiNieZatrzymujaKontrolki() {
        assertEquals(DOMYSLNY_ADRES_WARSZTATU, StanEkranu(adresWarsztatu = "  ").adresDoKontrolki("\t"))
    }

    @Test fun ostatniDzialajacyAdresNadalMaPierwszenstwoPrzedZapisanym() {
        val stan = StanEkranu(adresWarsztatu = "http://192.0.2.24:8770")
        assertEquals("http://192.0.2.24:8770", stan.adresDoKontrolki("http://192.0.2.23:8770"))
    }
}
