package pl.fwdrucik.sklep

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import pl.fwdrucik.sklep.siec.OdpowiedzWyceny

class WycenaInternetowaTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun wynik(
        rows: String = listOf(
            row("https://sklep.pl/miska-1", 40.0, snippet = "Miska 20 cm, cena 40 zł"),
            row("https://sklep.pl/miska-2", 50.0),
            row("https://inny-sklep.pl/miska", 60.0, snippet = "Miska 20 cm, cena 60 zł"),
        ).joinToString(","),
        count: Int = 3,
        extra: String = "",
        ok: Boolean = true,
        price: String = "50.0",
    ) = json.decodeFromString<OdpowiedzWyceny>("""
        {"ok":$ok,"fraza":"miska żywiczna 20 cm","zrodlo":"tavily-chatgpt",
         "sugerowana_cena":$price,"sugerowana_allegro":50,"srednia_cena":50,
         "min_cena":40,"max_cena":60,"liczba_ofert":$count,
         "sprawdzono":"2026-09-12T10:15:30+02:00",
         "ostrzezenie":"Wyniki wyszukiwania to ceny ofertowe, nie ceny sprzedaży. Sprawdź porównywalność wyrobów.",
         "znalezione":[$rows]$extra}
    """.trimIndent())

    private fun row(url: String, price: Double = 50.0, currency: String = "PLN", portal: String = "Sklep", title: String = "Miska żywiczna", snippet: String = "Miska 20 cm, cena 50 zł") =
        """{"portal":${json.encodeToString(portal)},"cena":$price,"tytul":${json.encodeToString(title)},"url":${json.encodeToString(url)},"fragment":${json.encodeToString(snippet)},"waluta":"$currency"}"""

    @Test fun trzyUdokumentowaneOfertyPozwalajaWybracCene() {
        val odp = wynik()
        assertTrue(odp.zmierzoneNaRynku)
        assertEquals(50.0, odp.sugerowanaCena, 0.0)
        assertEquals(50.0, odp.sugerowanaAllegro, 0.0)
        assertEquals(40.0, odp.minCena, 0.0)
        assertEquals(60.0, odp.maxCena, 0.0)
        assertTrue(odp.ostrzezenie!!.contains("nie ceny sprzedaży"))
    }

    @Test fun kontraktNieGubiLinkowFragmentowWalutyAniCzasu() {
        val zapis = json.parseToJsonElement(json.encodeToString(wynik())).jsonObject
        assertEquals("2026-09-12T10:15:30+02:00", zapis["sprawdzono"]?.jsonPrimitive?.content)
        val oferta = zapis.getValue("znalezione").jsonArray.first().jsonObject
        assertEquals("https://sklep.pl/miska-1", oferta["url"]?.jsonPrimitive?.content)
        assertEquals("Miska 20 cm, cena 40 zł", oferta["fragment"]?.jsonPrimitive?.content)
        assertEquals("PLN", oferta["waluta"]?.jsonPrimitive?.content)
    }

    @Test fun licznikMusiOdpowiadacPrzynajmniejTrzemDostarczonymOfertom() {
        assertFalse(wynik(rows = "", count = 30).zmierzoneNaRynku)
        assertFalse(wynik(rows = row("https://sklep.pl/1"), count = 3).zmierzoneNaRynku)
        assertFalse(wynik(count = 2).zmierzoneNaRynku)
        assertFalse(wynik(count = 4).zmierzoneNaRynku)
        assertTrue(wynik(count = 3).zmierzoneNaRynku)
    }

    @Test fun duplikatLinkuNieJestTrzecimZrodlem() {
        listOf("https://sklep.pl/1", "https://SKLEP.pl:443/1#cena").forEach { duplicate ->
            assertFalse(wynik(rows = listOf(row("https://sklep.pl/1"), row("https://sklep.pl/2"), row(duplicate)).joinToString(",")).zmierzoneNaRynku)
        }
        assertTrue(wynik().zmierzoneNaRynku)
    }

    @Test fun kazdaOfertaWymagaDowodowOrazDodatniejCenyPLN() {
        val firstTwo = row("https://sklep.pl/1") + "," + row("https://sklep.pl/2") + ","
        listOf(
            row("https://sklep.pl/3", price = 0.0), row("https://sklep.pl/3", price = -5.0),
            row("https://sklep.pl/3", currency = "EUR"), row("https://sklep.pl/3", currency = ""),
            row("https://sklep.pl/3", portal = " "), row("https://sklep.pl/3", title = ""),
            row("https://sklep.pl/3", snippet = "\n"), row("https://sklep.pl/3").replace(",\"waluta\":\"PLN\"", ""),
            row("https://sklep.pl/3").replace("50.0", "1e999"),
        ).forEach { invalid ->
            val odp = runCatching { wynik(rows = firstTwo + invalid) }.getOrNull()
            assertFalse(invalid, odp?.zmierzoneNaRynku == true)
        }
        assertTrue(wynik(rows = firstTwo + row("https://sklep.pl/3")).zmierzoneNaRynku)
    }

    @Test fun niebezpiecznyLinkBlokujeUzycieCalejRekomendacji() {
        val firstTwo = row("https://sklep.pl/1") + "," + row("https://sklep.pl/2") + ","
        listOf("", "http://sklep.pl/3", "javascript:alert(1)", "file:///tmp/3", "//sklep.pl/3",
            "https://user:pass@sklep.pl/3", "https://localhost/3", "https://panel.local/3",
            "https://127.0.0.1/3", "https://192.168.1.1/3", "https://[::1]/3",
            "https://2130706433/3", "https://0x7f000001/3", "https://sklep.pl\\@localhost/3",
            "https://sklep.pl/\n3", "https://sklep.pl/%0a3", "https://sklep.pl/%5c3"
        ).forEach { url -> assertFalse(url, wynik(rows = firstTwo + row(url)).zmierzoneNaRynku) }
        assertTrue(wynik().zmierzoneNaRynku)
    }

    @Test fun odmowaLubBrakGroundinguNigdyNieStajeSieSzacunkiem() {
        listOf("Brak klucza Tavily", "Limit wyszukiwania", "Brak porównywalnych wyników").forEach { reason ->
            val odp = wynik(ok = false, extra = ",\"blad\":\"$reason\"")
            assertFalse(odp.zmierzoneNaRynku)
            assertEquals(reason, odp.blad)
        }
        listOf("0", "-1", "1e999").forEach { price ->
            assertFalse(runCatching { wynik(price = price) }.getOrNull()?.zmierzoneNaRynku == true)
        }
        assertFalse(wynik().copy(sugerowanaCena = Double.NaN).zmierzoneNaRynku)
        assertFalse(wynik().copy(sugerowanaCena = Double.POSITIVE_INFINITY).zmierzoneNaRynku)
    }

    @Test fun nowaSciezkaWymagaOstrzezeniaICzasuZeStrefa() {
        listOf("", "2026-09-12", "2026-09-12T10:15:30", "2026-02-30T10:15:30Z", "wczoraj").forEach {
            assertFalse(it, wynik().copy(sprawdzono = it).zmierzoneNaRynku)
        }
        assertFalse(wynik().copy(ostrzezenie = null).zmierzoneNaRynku)
        assertFalse(wynik().copy(ostrzezenie = " ").zmierzoneNaRynku)
        assertTrue(wynik().copy(sprawdzono = "2026-09-12T10:15:30.123Z").zmierzoneNaRynku)
    }

    @Test fun fragmentBezTejKwotyPLNNieUzasadniaCeny() {
        val firstTwo = row("https://sklep.pl/1") + "," + row("https://sklep.pl/2") + ","
        listOf(
            "Miska 20 cm", "Miska 20 cm, cena 40", "Cena: 40 EUR", "Cena: 140 zł",
            "Cena: 1 040 zł", "Cena: 1.040,00 zł", "Cena: 1,40 zł", "Cena: 40,99 zł",
            "Cena: 1\t040 zł", "Cena: 1\n040 zł", "Cena: 1'040 zł",
            "Cena: 140.00 PLN", "Cena: 40,001 zł", "Cena: 12 40 zł", "Cena: -40 zł",
            "Cena: - 40 zł", "Cena: 1e40 PLN", "Kod: X40PLN", "Cena: 40 PLNxxx",
            "Cena: .40 zł", "Cena: PLN 140", "Cena: PLN 40,99", "Cena: PLN 40cm",
        ).forEach { fragment ->
            assertFalse(fragment, wynik(rows = firstTwo + row("https://sklep.pl/3", 40.0, snippet = fragment)).zmierzoneNaRynku)
        }
    }

    @Test fun walutaPoprzedniejCenyNieZmieniaWymiaruWCene() {
        val rows = row("https://sklep.pl/1") + "," + row("https://sklep.pl/2") + "," +
            row("https://sklep.pl/3", 20.0, snippet = "Cena: 40 zł 20 cm")
        assertFalse(wynik(rows = rows).zmierzoneNaRynku)
    }

    @Test fun walutaPoprzedniejCenyNieZmieniaLiczbyOpiniiWCene() {
        val rows = row("https://sklep.pl/1") + "," + row("https://sklep.pl/2") + "," +
            row("https://sklep.pl/3", 40.0, snippet = "Cena: 140 zł 40 opinii")
        assertFalse(wynik(rows = rows).zmierzoneNaRynku)
    }

    @Test fun fragmentUzasadniaDokladnaKwoteWPolskichFormatach() {
        val firstTwo = row("https://sklep.pl/1") + "," + row("https://sklep.pl/2") + ","
        listOf(
            "Miska 20 cm. Cena: 40 zł" to 40.0,
            "Cena: 40,00 PLN" to 40.0,
            "Cena: 40.00 zł" to 40.0,
            "Cena: PLN 40,00" to 40.0,
            "Cena: 1 240,50 zł" to 1240.50,
            "Cena: 1\u00a0240,50zł" to 1240.50,
            "Cena: 1\u202f240,50 PLN" to 1240.50,
            "Cena: 1.240,50 zł" to 1240.50,
            "Cena: 1240.50 PLN" to 1240.50,
            "Cena: PLN 1 240,50" to 1240.50,
        ).forEach { (fragment, cena) ->
            assertTrue(fragment, wynik(rows = firstTwo + row("https://sklep.pl/3", cena, snippet = fragment)).zmierzoneNaRynku)
        }
    }
}
