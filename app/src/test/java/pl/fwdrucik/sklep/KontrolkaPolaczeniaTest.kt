package pl.fwdrucik.sklep

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import pl.fwdrucik.sklep.siec.klientKontrolkiWarsztatu

class KontrolkaPolaczeniaTest {
    @Test fun zerwanePolaczenieZPuliNieOznaczaWylaczonegoKomputera() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("ready"))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            server.enqueue(MockResponse().setBody("ready"))
            val klient = klientKontrolkiWarsztatu()
            val get = Request.Builder().url(server.url("/stan")).build()
            klient.newCall(get).execute().use { assertEquals("ready", it.body!!.string()) }
            klient.newCall(get).execute().use { assertEquals("ready", it.body!!.string()) }
            assertEquals(3, server.requestCount)
        }
    }
    @Test fun kontrolkaNieMozeZuzycLimituAniWyslacZlecenia() {
        MockWebServer().use { server ->
            server.start()
            repeat(3) { server.enqueue(MockResponse().setBody("must not arrive")) }
            val klient = klientKontrolkiWarsztatu()
            val proby = listOf(
                Request.Builder().url(server.url("/wycena?fraza=miska")).build(),
                Request.Builder().url(server.url("/stan")).post("fixture".toRequestBody()).build(),
                Request.Builder().url(server.url("/stan?token=PRIVATE_FIXTURE")).build(),
            )
            proby.forEach { req ->
                assertTrue(runCatching { klient.newCall(req).execute().close() }.isFailure)
            }
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun kontrolkaMaKrotkiLimitCalegoWywolaniaBezPrzekierowan() {
        val klient = klientKontrolkiWarsztatu()
        assertTrue(klient.callTimeoutMillis in 1..6000)
        assertTrue(klient.readTimeoutMillis in 1..4000)
        assertFalse(klient.followRedirects)
        assertFalse(klient.followSslRedirects)
    }
}
