package pl.fwdrucik.sklep

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import pl.fwdrucik.sklep.siec.*
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class AiKontraktTest {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var serwer: MockWebServer
    private lateinit var api: SerwerWarsztatu
    private lateinit var ai: AiWarsztatu
    private lateinit var base: String

    @Before fun uruchomAtrape() {
        serwer = MockWebServer()
        serwer.start()
        base = serwer.url("/").toString().trimEnd('/')
        api = Retrofit.Builder().baseUrl("$base/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(SerwerWarsztatu::class.java)
        ai = AiWarsztatu(api)
    }

    @After fun zatrzymajAtrape() { serwer.shutdown() }

    private fun odpowiedz(tresc: String) {
        serwer.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(tresc))
    }

    @Test fun formularzSzkicuAllegroWysylaJawnePolaBezAktywacji() = runBlocking {
        odpowiedz("""{"ok":true,"id":"123456","url":"https://allegro.pl/oferta/123456","status":"INACTIVE","kategoria_id":"654321"}""")
        val wynik = api.utworzSzkicAllegro("$base/allegro/szkic", "Miska niebieska", "654321", 49.9,
            "<p>Miska na klucze.</p>", "", 2)
        val request = serwer.takeRequest(2, TimeUnit.SECONDS)!!
        val body = java.net.URLDecoder.decode(request.body.readUtf8(), "UTF-8")
        assertEquals("POST", request.method)
        assertEquals("/allegro/szkic", request.path)
        assertTrue(body.contains("tytul=Miska niebieska"))
        assertTrue(body.contains("kategoria_id=654321"))
        assertTrue(body.contains("cena_pln=49.9"))
        assertTrue(body.contains("stan_sztuk=2"))
        assertTrue(body.contains("opis_tekst=<p>Miska na klucze.</p>"))
        assertTrue(body.contains("zdjecia="))
        assertFalse(body.contains("publication"))
        assertFalse(body.contains("ACTIVE"))
        assertEquals("INACTIVE", wynik.status)
    }

    @Test fun wycenaZachowujeFrazeIOstrzezenieGdySerwerZwracaSzacunek() = runBlocking {
        odpowiedz("""{"ok":true,"fraza":"miska żywiczna","sugerowana_cena":49.9,"sugerowana_allegro":55.9,"srednia_cena":0,"min_cena":30,"max_cena":70,"liczba_ofert":0,"zrodlo":"estymacja","ostrzezenie":"Brak aktualnych ofert — to tylko szacunek","znalezione":[],"blad":null}""")
        val wynik = api.wycena("$base/wycena", "miska żywiczna", "zywica")
        val request = serwer.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("GET", request.method)
        assertEquals("/wycena", request.requestUrl!!.encodedPath)
        assertEquals("miska żywiczna", request.requestUrl!!.queryParameter("fraza"))
        assertEquals("zywica", request.requestUrl!!.queryParameter("kategoria"))
        assertFalse(wynik.zmierzoneNaRynku)
        assertEquals("Brak aktualnych ofert — to tylko szacunek", wynik.ostrzezenie)
    }

    @Test fun katalogPobieraWlasciwaSciezkeIRzeczywisteMetadane() = runBlocking {
        odpowiedz("""{"ok":true,"modele":[
            {"id":"local:czytelnik","nazwa":"Czytelnik","rodzaje":["tekst"],"droga":"lokalna","koszt":"bezpłatnie","dostepny":true,"powod":"Uruchomiony","vision":true},
            {"id":"mcp:assistant","nazwa":"Asystent","rodzaje":["tekst"],"droga":"most","koszt":"abonament","dostepny":false,"powod":"Most nie działa autonomicznie"}],
            "domyslne":{"tekst":"auto","obraz":"auto","wideo":"auto"},"nowe_pole":1}""")
        val katalog = ai.modele("$base/")
        assertEquals("/ai/modele", serwer.takeRequest(2, TimeUnit.SECONDS)!!.path)
        assertTrue(katalog.ok)
        assertTrue(katalog.dla("tekst").first().vision)
        assertEquals("abonament", katalog.dla("tekst").last().koszt)
        assertEquals("Most nie działa autonomicznie", katalog.dla("tekst").last().powod)
        assertEquals("auto", katalog.domyslne["wideo"])
        assertFalse(katalog.moznaWybrac("mcp:assistant", "tekst"))
    }

    @Test fun wyborFiltrujeRodzajIDostepnoscNieWymyslaModeli() {
        val katalog = KatalogAi(ok = true, modele = listOf(
            ModelAi(id = "obraz", rodzaje = listOf("obraz"), dostepny = true),
            ModelAi(id = "wideo", rodzaje = listOf("wideo"), dostepny = false),
        ))
        assertEquals(listOf("wideo"), katalog.dla("wideo").map { it.id })
        assertFalse(katalog.moznaWybrac("obraz", "tekst"))
        assertFalse(katalog.moznaWybrac("wideo", "wideo"))
        assertFalse(katalog.moznaWybrac("zniknal", "obraz"))
        assertTrue(katalog.moznaWybrac("auto", "tekst"))
        assertTrue(katalog.dla("tekst").isEmpty())
    }

    @Test fun brakDostepnosciNieOznaczaZeModelDziala() {
        val katalog = json.decodeFromString<KatalogAi>("""{"ok":true,"modele":[{"id":"x","rodzaje":["tekst"]}]}""")
        assertFalse(katalog.moznaWybrac("x", "tekst"))
        assertEquals("Nie podano kosztu", katalog.modele.single().koszt)
        assertFalse(KatalogAi(modele = listOf(ModelAi(id = "x", rodzaje = listOf("tekst"), dostepny = true))).moznaWybrac("x", "tekst"))
    }

    @Test fun pytaniaIKosztySaCzytelneBezObietnicyDarmowegoApi() {
        assertEquals("Z czego jest wyrób?", pytanieOBrakujacyFakt("material"))
        assertTrue(pytanieOBrakujacyFakt("wymiary").contains("cm"))
        assertEquals("płatna usługa", kosztPoPolsku("paid"))
        assertEquals("nie podano kosztu", kosztPoPolsku("unknown"))
        assertEquals("0,02 zł / obraz", kosztPoPolsku("0,02 zł / obraz"))
    }

    @Test fun opisBezZdjeciaWysylaFaktyIPolskiTekstOrazAuto() = runBlocking {
        odpowiedz("""{"ok":true,"nazwa":"Miska","opis_krotki":"Niebieska miska.","opis":"Miska na klucze.","do_uzupelnienia":["material","wymiary"],"model":"local:redaktor","zrodlo":"lokalne","ostrzezenie":"Sprawdź fakty"}""")
        val wynik = ai.opis(base, DaneOpisuAi("miska niebiska", material = "żywica", wymiary = "20 cm"))
        val request = serwer.takeRequest(2, TimeUnit.SECONDS)!!
        val body = request.body.readUtf8()
        assertEquals("/ai/opis", request.path)
        assertEquals("POST", request.method)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        assertTrue(body.contains("miska niebiska"))
        assertTrue(body.contains("name=\"material\""))
        assertTrue(body.contains("żywica"))
        assertTrue(body.contains("20 cm"))
        assertTrue(body.contains("name=\"model\""))
        assertTrue(body.contains("\r\nauto\r\n"))
        assertFalse(body.contains("name=\"plik\""))
        assertEquals(listOf("material", "wymiary"), wynik.doUzupelnienia)
        assertEquals("Sprawdź fakty", wynik.ostrzezenie)
        assertEquals("local:redaktor", wynik.model)
        assertEquals("lokalne", wynik.zrodlo)
    }

    @Test fun opisZeZdjeciemZachowujeKonkretnyIdModeluBezPodmiany() = runBlocking {
        odpowiedz("""{"ok":true,"nazwa":"Wazon","opis":"Niebieski wazon.","model":"openrouter:provider/model:free","zrodlo":"api"}""")
        val plik = MultipartBody.Part.createFormData("plik", "wyrob.jpg", byteArrayOf(1, 2, 3).toRequestBody("image/jpeg".toMediaType()))
        ai.opis(base, DaneOpisuAi(model = "openrouter:provider/model:free"), plik)
        val body = serwer.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8()
        assertTrue(body.contains("name=\"plik\"; filename=\"wyrob.jpg\""))
        assertTrue(body.contains("openrouter:provider/model:free"))
        assertFalse(body.contains("\r\nauto\r\n"))
    }

    @Test fun odrzucenieKonkretnegoModeluNieUruchamiaDrugiegoZapytania() = runBlocking {
        odpowiedz("""{"ok":false,"blad":"Wybrany model jest niedostępny","do_uzupelnienia":[]}""")
        val blad = runCatching { ai.opis(base, DaneOpisuAi(notatka = "miska", model = "local:wylaczony")) }.exceptionOrNull()
        assertTrue(blad?.message.orEmpty().contains("Wybrany model jest niedostępny"))
        assertEquals(1, serwer.requestCount)
    }

    @Test fun pustyWynikNieZastepujeTekstuUzytkownika() = runBlocking {
        odpowiedz("""{"ok":true,"do_uzupelnienia":[]}""")
        assertTrue(runCatching { ai.opis(base, DaneOpisuAi(notatka = "miska")) }.isFailure)
    }

    @Test fun brakZdjeciaIFaktowNieWysylaZapytania() = runBlocking {
        assertTrue(runCatching { ai.opis(base, DaneOpisuAi()) }.isFailure)
        assertEquals(0, serwer.requestCount)
    }

    @Test fun starySerwerBezKataloguNieTworzyListyZastepczej() = runBlocking {
        serwer.enqueue(MockResponse().setResponseCode(404))
        assertTrue(runCatching { ai.modele(base) }.isFailure)
        assertEquals(1, serwer.requestCount)
    }

    @Test fun obrazIWideoPrzekazujaModelIOperacje() = runBlocking {
        listOf("zdjecie-produktowe" to "browser:flow-image", "animacja" to "local:comfy-video", "animacja" to "auto").forEach { (zadanie, model) ->
            odpowiedz("""{"ok":true,"id":"test"}""")
            api.zlec("$base/zlec", AiWarsztatu.pole(zadanie), AiWarsztatu.pole("nie zmieniaj wyrobu"), AiWarsztatu.pole("9:16"),
                MultipartBody.Part.createFormData("plik", "wyrob.jpg", "obraz".toRequestBody("image/jpeg".toMediaType())), AiWarsztatu.pole(model))
            val request = serwer.takeRequest(2, TimeUnit.SECONDS)!!
            val body = request.body.readUtf8()
            assertEquals("/zlec", request.path)
            assertTrue(body.contains("\r\n$zadanie\r\n"))
            assertTrue(body.contains("name=\"model\""))
            assertTrue(body.contains("\r\n$model\r\n"))
            assertTrue(body.contains("9:16"))
        }
    }

    @Test fun stareWywolanieZlecNadalNieWymagaModelu() = runBlocking {
        odpowiedz("""{"ok":true,"id":"test"}""")
        api.zlec("$base/zlec", AiWarsztatu.pole("zdjecie-meta"), AiWarsztatu.pole("tło"), AiWarsztatu.pole("1:1"),
            MultipartBody.Part.createFormData("plik", "foto.jpg", "x".toRequestBody()))
        assertFalse(serwer.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8().contains("name=\"model\""))
    }
}
