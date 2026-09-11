package pl.fwdrucik.sklep

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import pl.fwdrucik.sklep.dane.KopiaRobocza
import pl.fwdrucik.sklep.dane.wybierzKategorieAllegro
import pl.fwdrucik.sklep.siec.*
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.TimeUnit

class KategorieAllegroTest {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var serwer: MockWebServer
    private lateinit var katalog: KategorieWarsztatu
    private lateinit var base: String

    @Before fun uruchom() {
        serwer = MockWebServer().also { it.start() }
        base = serwer.url("/").toString()
        val api = Retrofit.Builder().baseUrl(base)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(SerwerWarsztatu::class.java)
        katalog = KategorieWarsztatu(api)
    }

    @After fun zamknij() { serwer.shutdown() }

    private fun odpowiedz(json: String) {
        serwer.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(json))
    }

    @Test fun szukaGetemPoNazwieIZwracaCzytelnaSciezke() = runBlocking {
        odpowiedz("""{"ok":true,"kategorie":[{"id":"123456","nazwa":"Miski","sciezka":"Dom > Kuchnia > Miski"}],"dodatkowe_pole":1}""")
        val wynik = katalog.szukaj(base, "  miska z żywicy & drewna  ")
        val request = serwer.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("GET", request.method)
        assertEquals("/allegro/kategorie", request.requestUrl!!.encodedPath)
        assertEquals("miska z żywicy & drewna", request.requestUrl!!.queryParameter("fraza"))
        assertEquals(0L, request.bodySize)
        assertEquals("123456", wynik.single().id)
        assertEquals("Miski", wynik.single().nazwa)
        assertEquals("Dom > Kuchnia > Miski", wynik.single().sciezka)
    }

    @Test fun bladHttp200NieStajeSiePropozycjaKategorii() = runBlocking {
        odpowiedz("""{"ok":false,"kategorie":[{"id":"123","nazwa":"Nie wybieraj","sciezka":""}],"blad":"Brak dostępu do kategorii"}""")
        try {
            katalog.szukaj(base, "miska")
            fail("Odpowiedź ok=false musi zatrzymać wybór")
        } catch (e: IOException) { assertEquals("Brak dostępu do kategorii", e.message) }
    }

    @Test fun brakWynikowNieTworzyKategoriiDomyslnej() = runBlocking {
        odpowiedz("""{"ok":true,"kategorie":[]}""")
        assertTrue(katalog.szukaj(base, "nietypowy wyrób").isEmpty())
    }

    @Test fun niekompletneKategorieIDuplikatyNieTrafiajaDoWyboru() = runBlocking {
        odpowiedz("""{"ok":true,"kategorie":[{"id":"","nazwa":"Pusta"},{"id":"zywica","nazwa":"Nie jest ID"},{"id":"456","nazwa":""},{"id":"123","nazwa":"Miski","sciezka":"Dom"},{"id":"123","nazwa":"Duplikat"}]}""")
        assertEquals(listOf(KategoriaAllegro("123", "Miski", "Dom")), katalog.szukaj(base, "miska"))
    }

    @Test fun pustaFrazaNieWysylaZapytania() = runBlocking {
        try {
            katalog.szukaj(base, " ")
            fail("Brak nazwy powinien zablokować zapytanie")
        } catch (_: IllegalArgumentException) { assertEquals(0, serwer.requestCount) }
    }

    @Test fun frazaMusiMiecOdDwochDoStuZnakowPoObcieciuSpacji() = runBlocking {
        for (fraza in listOf(" a ", "x".repeat(101))) {
            odpowiedz("""{"ok":true,"kategorie":[]}""")
            try {
                katalog.szukaj(base, fraza)
                fail("Nie wysyłaj frazy spoza zakresu 2–100")
            } catch (_: IllegalArgumentException) { assertEquals(0, serwer.requestCount) }
        }
    }

    @Test fun listaNiePrzekraczaDziesieciuWynikow() = runBlocking {
        val elementy = (1..12).joinToString(",") { """{"id":"$it","nazwa":"Kategoria $it","sciezka":"Dom > Kategoria $it"}""" }
        odpowiedz("""{"ok":true,"kategorie":[$elementy]}""")
        assertEquals(10, katalog.szukaj(base, "miska").size)
    }

    @Test fun jawnyWyborZmieniaIdINazweAleNieCenyAniReszteSzkicu() {
        val kopia = KopiaRobocza(nazwa = "Miska", cena = "49,90", allegroCena = "59", stan = "2",
            allegroKategoria = "654321", allegroKategoriaNazwa = "Dawna kategoria", opis = "Opis użytkownika")
        val wybrana = kopia.wybierzKategorieAllegro(KategoriaAllegro("123456", "Miski", "Dom > Miski"))
        assertEquals("123456", wybrana.allegroKategoria)
        assertEquals("Miski", wybrana.allegroKategoriaNazwa)
        assertEquals("Dom > Miski", wybrana.allegroKategoriaSciezka)
        assertEquals("49,90", wybrana.cena)
        assertEquals("59", wybrana.allegroCena)
        assertEquals("2", wybrana.stan)
        assertEquals("Opis użytkownika", wybrana.opis)
    }
}
