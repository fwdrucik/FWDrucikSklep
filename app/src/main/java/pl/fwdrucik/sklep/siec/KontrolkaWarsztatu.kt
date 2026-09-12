package pl.fwdrucik.sklep.siec

import okhttp3.OkHttpClient
import retrofit2.http.GET
import retrofit2.http.Url
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Oddzielny kontrakt: żadnej wyceny, generacji ani zapisów. */
interface KontrolkaWarsztatu {
    @GET suspend fun stan(@Url adres: String): StanSerwera
}

/** GET może odzyskać zerwane połączenie z puli; zlecenia pozostają one-shot. */
internal fun klientKontrolkiWarsztatu(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(4, TimeUnit.SECONDS)
    .readTimeout(4, TimeUnit.SECONDS)
    .callTimeout(6, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .followRedirects(false)
    .followSslRedirects(false)
    .addInterceptor { chain ->
        val r = chain.request()
        if (r.method != "GET" || r.url.encodedPath != "/stan" || r.url.encodedQuery != null) {
            throw IOException("Kontrolka obsługuje wyłącznie odczyt stanu.")
        }
        chain.proceed(r)
    }
    .build()
