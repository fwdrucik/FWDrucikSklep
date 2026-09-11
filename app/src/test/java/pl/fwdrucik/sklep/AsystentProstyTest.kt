package pl.fwdrucik.sklep

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import pl.fwdrucik.sklep.dane.KopiaRobocza
import pl.fwdrucik.sklep.dane.Produkt
import pl.fwdrucik.sklep.dane.Polecenia
import pl.fwdrucik.sklep.dane.brakiSzkicuAllegro
import pl.fwdrucik.sklep.dane.bladPrywatnegoSzkicu
import pl.fwdrucik.sklep.dane.SzkicProduktu
import pl.fwdrucik.sklep.dane.cenaTylkoOdUzytkownika
import pl.fwdrucik.sklep.dane.zCenaPotwierdzona
import pl.fwdrucik.sklep.siec.OdpowiedzSzkicuAllegro
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject

class AsystentProstyTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun czytelnaWybranaKategoriaPrzezywaZapisKopii() {
        val kopia = json.decodeFromString<KopiaRobocza>("""{"nazwa":"Miska","allegroKategoria":"123456","allegroKategoriaNazwa":"Miski","allegroKategoriaSciezka":"Dom > Kuchnia > Miski"}""")
        val zapis = json.parseToJsonElement(json.encodeToString(kopia)).jsonObject
        assertEquals("\"Miski\"", zapis["allegroKategoriaNazwa"].toString())
        assertEquals("\"Dom > Kuchnia > Miski\"", zapis["allegroKategoriaSciezka"].toString())
        assertEquals("123456", kopia.naProdukt(Produkt()).allegroKategoria)
    }

    @Test fun wymyslonaCenaAgentaJestOdrzucana() {
        val szkic = json.decodeFromString<SzkicProduktu>("""{"nazwa":"Miska","opis":"Miska na klucze.","cena":"682"}""")
        val wynik = szkic.zCenaPotwierdzona("niebieska miska")
        assertEquals("", wynik.cena)
        assertTrue(wynik.doUzupelnienia.any { it.contains("cenę") })
    }

    @Test fun recznaCenaNieJestNadpisywanaAniPrzezAiAniPrzezNotatke() {
        assertEquals("49,90", SzkicProduktu(cena = "682").zCenaPotwierdzona("cena 90 zł", "49,90").cena)
        assertEquals("49,90", cenaTylkoOdUzytkownika("49,90", "miska 90 zł"))
    }

    @Test fun jawnaKwotaZNotatkiNieZalezyOdOdpowiedziModelu() {
        assertEquals("49.90", SzkicProduktu(cena = "682").zCenaPotwierdzona("miska niebiska 49,90zł").cena)
        assertEquals("1200.50", cenaTylkoOdUzytkownika("", "stolik cena 1 200,50 PLN"))
        assertEquals("30", cenaTylkoOdUzytkownika("", "miska za 30"))
    }

    @Test fun wymiaryCzasDostawaIWieleKwotNieSaCenaWyrobu() {
        listOf("miska 20 cm", "zrobię za 30 dni", "wysyłka 15 zł", "miska 30 zł, podstawka 20 zł").forEach {
            assertEquals(it, "", cenaTylkoOdUzytkownika("", it))
        }
    }

    @Test fun numerKategoriiAllegroPrzechodziZKopiiDoProduktu() {
        val kopia = json.decodeFromString<KopiaRobocza>("""{"nazwa":"Miska","allegroKategoria":"123456","krokAsystenta":4}""")
        assertEquals("123456", kopia.naProdukt(Produkt()).allegroKategoria)
        assertEquals(4, kopia.krokAsystenta)
    }

    @Test fun stareKopieNieKasująNumeruKategoriiZSerwera() {
        val kopia = json.decodeFromString<KopiaRobocza>("""{"nazwa":"Miska"}""")
        assertEquals("654321", kopia.naProdukt(Produkt(allegroKategoria = "654321")).allegroKategoria)
    }

    @Test fun wyczyszczenieNumeruKategoriiJestZachowanePoRestarcie() {
        val kopia = KopiaRobocza(nazwa = "Miska", allegroKategoria = "", krokAsystenta = 4)
        val odtworzona = json.decodeFromString<KopiaRobocza>(json.encodeToString(kopia))
        assertNull(odtworzona.naProdukt(Produkt(allegroKategoria = "654321")).allegroKategoria)
    }

    @Test fun daneAllegroIModeleMediowPrzezywajaZmianeTrybuIRestart() {
        val kopia = KopiaRobocza(nazwa = "Miska", allegroKategoria = "123456", allegroCena = "49,90", stan = "2",
            animacja = "film.mp4", modelObrazuAi = "local:studio", modelWideoAi = "browser:flow-video", krokAsystenta = 4)
        val odtworzona = json.decodeFromString<KopiaRobocza>(json.encodeToString(kopia.copy(trybProsty = false)))
        assertEquals("local:studio", odtworzona.modelObrazuAi)
        assertEquals("browser:flow-video", odtworzona.modelWideoAi)
        assertEquals("film.mp4", odtworzona.animacja)
        assertEquals("123456", odtworzona.naProdukt(Produkt()).allegroKategoria)
        assertEquals(4990, odtworzona.naProdukt(Produkt()).allegroCenaGr)
        assertEquals(2, odtworzona.naProdukt(Produkt()).stan)
    }

    private fun komplet() = KopiaRobocza(nazwa = "Miska", opis = "Niebieska miska na klucze.", cena = "49,90", stan = "1", allegroKategoria = "123456")

    @Test fun kompletneDanePozwalajaPrzejscDoPotwierdzeniaBezDoplaty() {
        assertTrue(komplet().brakiSzkicuAllegro().isEmpty())
        assertEquals("49,90", komplet().cena)
        assertEquals("", komplet().allegroCena)
    }

    @Test fun pustySzkicWskazujeBrakiZamiastZgadywacFakty() {
        val braki = KopiaRobocza().brakiSzkicuAllegro()
        assertEquals(5, braki.size)
        assertTrue(braki.any { it.contains("kategorii") })
        assertTrue(braki.any { it.contains("sztuk") })
    }

    @Test fun kategoriaSklepuNieZastepujeNumeruAllegro() {
        assertTrue(komplet().copy(allegroKategoria = "zywica", kategoria = "zywica").brakiSzkicuAllegro().isNotEmpty())
        assertTrue(komplet().copy(allegroKategoria = null).brakiSzkicuAllegro().isNotEmpty())
    }

    @Test fun bledneKwotyIZerowyStanBlokujaSzkic() {
        listOf("0", "-1", "NaN", "1.5.0").forEach { cena ->
            assertTrue(komplet().copy(allegroCena = cena).brakiSzkicuAllegro().isNotEmpty())
        }
        listOf("", "0", "-1", "1.5").forEach { stan ->
            assertTrue(komplet().copy(stan = stan).brakiSzkicuAllegro().isNotEmpty())
        }
    }

    @Test fun bladHttp200LubNieprywatnyStatusNieUdajeSukcesu() {
        val odmowa = json.decodeFromString<OdpowiedzSzkicuAllegro>("""{"ok":false,"blad":"Brakuje wymaganych parametrów"}""")
        assertEquals("Brakuje wymaganych parametrów", odmowa.bladPrywatnegoSzkicu())
        assertNotNull(OdpowiedzSzkicuAllegro(ok = true, id = "123", status = "ACTIVE").bladPrywatnegoSzkicu())
        assertNotNull(OdpowiedzSzkicuAllegro(ok = true, id = "123").bladPrywatnegoSzkicu())
        assertNotNull(OdpowiedzSzkicuAllegro(ok = true, status = "INACTIVE").bladPrywatnegoSzkicu())
        assertNull(OdpowiedzSzkicuAllegro(ok = true, id = "123", status = "INACTIVE").bladPrywatnegoSzkicu())
    }

    @Test fun wspolneZlecenieTlaISwiatlaChroniFaktyINieZachowujeStaregoTla() {
        val polecenie = Polecenia.tloISwiatlo("niebieska miska")
        assertTrue(polecenie.contains("niebieska miska"))
        assertTrue(polecenie.contains("usuń tło"))
        assertTrue(polecenie.contains("światło, ostrość i kontrast"))
        assertTrue(polecenie.contains("ten sam kształt"))
        assertTrue(polecenie.contains("nie dodawaj szczegółów"))
        assertFalse(polecenie.contains("zachowaj tło"))
    }
}
