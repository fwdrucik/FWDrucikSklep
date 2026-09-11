package pl.fwdrucik.sklep

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import pl.fwdrucik.sklep.dane.KopiaRobocza
import pl.fwdrucik.sklep.dane.moznaPrzejscDoKroku
import pl.fwdrucik.sklep.siec.*

class AsystentChmuraTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun model(id: String, vision: Boolean) =
        ModelAi(id = id, nazwa = id, rodzaje = listOf("tekst"), dostepny = true, vision = vision)
    @Test fun samoZdjecieBezPotwierdzonejDrogiAutoNieRusza() {
        assertNotNull(KatalogAi(ok = true, modele = listOf(model("text-only", false)))
            .powodBlokadyOpisu(DaneOpisuAi(), true))
    }
    @Test fun samaObecnoscVisionNiePozwalaZmieniacOdbiorcyAutomatycznie() {
        assertNotNull(KatalogAi(ok = true, modele = listOf(model("openrouter:vision", true)))
            .powodBlokadyOpisu(DaneOpisuAi(), true))
    }
    @Test fun potwierdzonaDrogaAutoVisionPrzyjmujeZdjecie() {
        val katalog = json.decodeFromString<KatalogAi>("""{"ok":true,"modele":[],"auto_tekst":{"model":"chatgpt:gpt-5.5","dostepny":true,"vision":true}}""")
        assertNull(katalog.powodBlokadyOpisu(DaneOpisuAi(), true))
        assertTrue(katalog.czyCzytaZdjecie("auto"))
    }
    @Test fun pojedynczyWierszAutoNieZastepujeDeklaracjiSerwera() {
        assertNotNull(KatalogAi(ok = true, modele = listOf(model("auto", true)))
            .powodBlokadyOpisu(DaneOpisuAi(), true))
    }
    @Test fun deklaracjaAutoNiepotwierdzonaNieWysylaZdjecia() {
        listOf(
            """{"model":"chatgpt:test","vision":true}""",
            """{"model":"chatgpt:test","dostepny":true,"vision":false}""",
            """{"dostepny":true,"vision":true}"""
        ).forEach { deklaracja ->
            val katalog = json.decodeFromString<KatalogAi>("""{"ok":true,"auto_tekst":$deklaracja}""")
            assertNotNull(katalog.powodBlokadyOpisu(DaneOpisuAi(), true))
            assertFalse(katalog.czyCzytaZdjecie("auto"))
        }
    }
    @Test fun nieaktualnyKatalogNiePotwierdzaVision() {
        assertNotNull(KatalogAi(ok = false, modele = listOf(model("auto", true)))
            .powodBlokadyOpisu(DaneOpisuAi(), true))
    }
    @Test fun konkretnyModelBezVisionWymagaSlow() {
        val katalog = KatalogAi(ok = true, modele = listOf(model("chatgpt-text", false)))
        assertNotNull(katalog.powodBlokadyOpisu(DaneOpisuAi(model = "chatgpt-text"), true))
        assertNull(katalog.powodBlokadyOpisu(DaneOpisuAi(notatka = "miska", model = "chatgpt-text"), true))
    }
    @Test fun konkretnyVisionPrzyjmujeZdjecieBezPodmiany() {
        assertNull(KatalogAi(ok = true, modele = listOf(model("wybrany", true)))
            .powodBlokadyOpisu(DaneOpisuAi(model = "wybrany"), true))
    }
    @Test fun faktyPozwalajaUzycAutoBezZdjecia() {
        assertNull(KatalogAi().powodBlokadyOpisu(DaneOpisuAi(notatka = "miska"), false))
        assertNotNull(KatalogAi().powodBlokadyOpisu(DaneOpisuAi(), false))
    }
    @Test fun kopiaZachowujeFormatIWynikiDoAkceptacji() {
        val dane = """{"proporcjeAi":"9:16","kadrDoAkceptacji":"C:/test/kadr.jpg","filmDoAkceptacji":"C:/test/film.mp4","propozycjaOpisu":{"ok":true,"nazwa":"Miska","opis":"Opis do sprawdzenia","model":"cloud-test"}}"""
        val zapis = json.parseToJsonElement(json.encodeToString(json.decodeFromString<KopiaRobocza>(dane))).jsonObject
        assertEquals("\"9:16\"", zapis["proporcjeAi"].toString())
        assertEquals("\"C:/test/kadr.jpg\"", zapis["kadrDoAkceptacji"].toString())
        assertEquals("\"C:/test/film.mp4\"", zapis["filmDoAkceptacji"].toString())
        assertTrue(zapis["propozycjaOpisu"].toString().contains("Opis do sprawdzenia"))
    }
    @Test fun historiaChmuryNieUdajeForge() {
        val katalog = KatalogAi(ok = true, modele = listOf(ModelAi(id = "browser:flow-image", nazwa = "Flow")))
        val nazwa = nazwaHistoriiAi("zdjecie-produktowe", "browser:flow-image", katalog)
        assertTrue(nazwa.contains("Flow"))
        assertFalse(nazwa.contains("Forge"))
    }
    @Test fun bladOpisuIPustaNazwaNieBlokujaMediowAniCeny() {
        val kopia = KopiaRobocza(nazwa = "", opis = "", krokAsystenta = 2, zdjecie = "foto.jpg")
        assertTrue(kopia.moznaPrzejscDoKroku(3))
        assertTrue(kopia.moznaPrzejscDoKroku(4))
        assertFalse(kopia.moznaPrzejscDoKroku(0))
    }
    @Test fun historiaAutoNieUdajeKarty() {
        val nazwa = nazwaHistoriiAi("animacja", "auto", KatalogAi())
        assertFalse(nazwa.contains("karta"))
        assertTrue(nazwa.contains("Automatycznie"))
    }
}
