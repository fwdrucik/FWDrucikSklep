package pl.fwdrucik.sklep

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import pl.fwdrucik.sklep.siec.klientWarsztatu

class WycenaHttpTest {
    @Test fun tylkoWycenaMaZapasNaDwieMinutySerwera() {
        MockWebServer().use { server ->
            val limity = mutableListOf<Int>()
            val client = klientWarsztatu().newBuilder().addInterceptor { chain ->
                limity += chain.readTimeoutMillis()
                chain.proceed(chain.request())
            }.build()
            listOf("/wycena?fraza=miska", "/stan", "/ai/opis", "/wycena-inna").forEach { path ->
                server.enqueue(MockResponse().setBody("{}"))
                client.newCall(Request.Builder().url(server.url(path)).build()).execute().close()
            }
            assertEquals(listOf(150_000, 120_000, 120_000, 120_000), limity)
        }
    }

    @Test fun nawet503RetryAfterZeroNieZuzywaLimituWyszukiwaniaDrugiRaz() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            server.enqueue(MockResponse().setBody("SECOND_REQUEST_FORBIDDEN"))
            klientWarsztatu().newCall(Request.Builder().url(server.url("/wycena?fraza=miska")).build())
                .execute().use { assertEquals(503, it.code) }
            assertEquals(1, server.requestCount)
        }
    }
}
