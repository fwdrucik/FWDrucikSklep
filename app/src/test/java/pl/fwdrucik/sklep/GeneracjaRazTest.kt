package pl.fwdrucik.sklep

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import pl.fwdrucik.sklep.siec.klientWarsztatu
import java.util.concurrent.TimeUnit

class GeneracjaRazTest {
    @Test fun klientNiePonawiaPolaczeniaAniNiePrzekierowujeZdjecia() {
        val klient = klientWarsztatu()
        assertFalse(klient.retryOnConnectionFailure)
        assertFalse(klient.followRedirects)
        assertFalse(klient.followSslRedirects)
    }
    @Test fun odrzucone503NieZlecaPonownie() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            server.enqueue(MockResponse().setBody("SECOND_REQUEST_FORBIDDEN"))
            klientWarsztatu().newCall(Request.Builder().url(server.url("/zlec"))
                .post("fixture".toRequestBody()).build()).execute().use { assertEquals(503, it.code) }
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun utrataOdpowiedziNiePonawiaOpisu() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            server.enqueue(MockResponse().setBody("SECOND_REQUEST_FORBIDDEN"))
            val wynik = runCatching {
                klientWarsztatu().newBuilder().readTimeout(2, TimeUnit.SECONDS).build()
                    .newCall(Request.Builder().url(server.url("/ai/opis")).post("fixture".toRequestBody()).build()).execute().use {}
            }
            assertTrue(wynik.isFailure)
            assertEquals(1, server.requestCount)
        }
    }
}
