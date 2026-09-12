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
import pl.fwdrucik.sklep.siec.*
import retrofit2.Retrofit

class AiBledyTest {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var server: MockWebServer
    private lateinit var ai: AiWarsztatu
    private lateinit var base: String
    @Before fun start() {
        server = MockWebServer().also { it.start() }
        base = server.url("/").toString().trimEnd('/')
        ai = AiWarsztatu(Retrofit.Builder().baseUrl("$base/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(SerwerWarsztatu::class.java))
    }
    @After fun stop() { server.shutdown() }
    private fun blad(status: Int, body: String): Throwable = runBlocking {
        server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body))
        runCatching { ai.opis(base, DaneOpisuAi(notatka = "PRIVATE_PROMPT")) }.exceptionOrNull()
            ?: error("Expected failure")
    }
    @Test fun brakSrodkowNieJestBledemSieciINiePokazujeCiala() {
        val e = blad(402, """{"ok":false,"kod":"payment_required","blad":"PRIVATE_BODY sk-private-fixture"}""")
        assertTrue(e.message.orEmpty().contains("środk"))
        assertFalse(e.message.orEmpty().contains("PRIVATE"))
        assertFalse(e.message.orEmpty().contains("sk-private"))
        assertEquals(1, server.requestCount)
    }
    @Test fun limitPodpowiadaPonowienieBezZmianyDostawcy() {
        val e = blad(429, """{"ok":false,"blad":"PRIVATE_BODY"}""")
        assertTrue(e.message.orEmpty().contains("limit"))
        assertTrue(e.message.orEmpty().contains("później"))
        assertEquals(1, server.requestCount)
    }
    @Test fun strukturalnyBrakVisionWyjasniaJakKontynuowac() {
        val e = blad(422, """{"ok":false,"kod":"vision_unavailable","blad":"PRIVATE_BODY"}""")
        assertTrue(e.message.orEmpty().contains("zdję"))
        assertTrue(e.message.orEmpty().contains("słów"))
        assertFalse(e.message.orEmpty().contains("PRIVATE_BODY"))
    }
    @Test fun htmlProxyNieTrafiaDoKomunikatu() {
        val e = blad(502, "<html>PRIVATE_BODY password=fixture</html>")
        assertFalse(e.message.orEmpty().contains("PRIVATE"))
        assertTrue(e.message.orEmpty().contains("usług"))
    }
    @Test fun diagnostykaStanuOdróżniaFormatOdSieciBezTajnychDanych() {
        val format = diagnostykaAi("stan", kotlinx.serialization.SerializationException("PRIVATE_BODY"))
        assertEquals("operation=stan http=- class=SerializationException", format)
        assertEquals("operation=stan http=- class=ConnectException",
            diagnostykaAi("stan", java.net.ConnectException("PRIVATE_HOST")))
        assertEquals("operation=ai http=- class=Exception",
            diagnostykaAi("PRIVATE_OPERATION", Exception("PRIVATE_BODY")))
    }
    @Test fun diagnostykaMaTylkoStatusKlaseIOperacje() {
        val e = blad(429, """{"ok":false,"blad":"PRIVATE_BODY"}""")
        val log = diagnostykaAi("opis", e)
        assertTrue(log.contains("http=429"))
        assertTrue(log.contains("class=HttpException"))
        assertFalse(log.contains("PRIVATE"))
        val timeout = diagnostykaAi("opis", java.net.SocketTimeoutException("PRIVATE_PROMPT"))
        assertTrue(timeout.contains("SocketTimeoutException"))
        assertFalse(timeout.contains("PRIVATE"))
    }
    @Test fun rzeczywistyLimitChatGpt503NieUdajeNiedostepnegoModelu() {
        val e = blad(503, """{"ok":false,"blad":"Konto ChatGPT zgłosiło limit użycia. Nie ponowiono żądania."}""")
        assertTrue(e.message.orEmpty().contains("limit"))
        assertTrue(e.message.orEmpty().contains("później"))
        assertFalse(e.message.orEmpty().contains("model jest niedostępny"))
        assertEquals(1, server.requestCount)
    }
    @Test fun rzeczywistyBrakVision422ProsiOSlowa() {
        val e = blad(422, """{"ok":false,"blad":"Wybrany model nie widzi zdjęć. PRIVATE_FIXTURE"}""")
        assertTrue(e.message.orEmpty().contains("nie odczytał zdjęcia"))
        assertTrue(e.message.orEmpty().contains("słów"))
        assertFalse(e.message.orEmpty().contains("PRIVATE"))
    }
    @Test fun rzeczywisteLogowanie503NieUdajeAwariiSieci() {
        val e = blad(503, """{"ok":false,"blad":"Zaloguj się do ChatGPT na komputerze. PRIVATE_FIXTURE"}""")
        assertTrue(e.message.orEmpty().contains("logowanie"))
        assertFalse(e.message.orEmpty().contains("PRIVATE"))
    }
    @Test fun zajetyCli503ProsiOPoczekanieBezPonawiania() {
        listOf("ChatGPT CLI jest zajęty", "Asystent kończy poprzedni opis").forEach { powod ->
            val e = blad(503, """{"ok":false,"blad":"$powod. PRIVATE_FIXTURE"}""")
            assertTrue(e.message.orEmpty().contains("Poczekaj"))
            assertTrue(e.message.orEmpty().contains("poprzedni"))
            assertFalse(e.message.orEmpty().contains("PRIVATE"))
        }
        assertEquals(2, server.requestCount)
    }
    @Test fun okFalseTakzeNieWypisujeSekretow() {
        val e = blad(200, """{"ok":false,"kod":"vision_unavailable","blad":"PRIVATE_BODY token=fixture"}""")
        assertFalse(e.message.orEmpty().contains("PRIVATE"))
        assertTrue(e.message.orEmpty().contains("zdję"))
    }
}
