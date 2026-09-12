package pl.fwdrucik.sklep.siec

import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okio.BufferedSink
import java.util.concurrent.TimeUnit

/** Bez ciasteczek sklepu, kluczy usług i logowania ciał HTTP. */
fun klientWarsztatu(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(4, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .writeTimeout(180, TimeUnit.SECONDS)
    .retryOnConnectionFailure(false)
    .followRedirects(false)
    .followSslRedirects(false)
    .addInterceptor { chain ->
        val request = chain.request()
        val body = request.body
        val pojedynczy = if (request.method == "POST" && body != null) {
            // Także 503 Retry-After: 0 nie może zużyć limitu po raz drugi.
            val raz = object : RequestBody() {
                override fun contentType() = body.contentType()
                override fun contentLength() = body.contentLength()
                override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
                override fun isOneShot() = true
            }
            request.newBuilder().method(request.method, raz).build()
        } else request
        // Wycena: do 15 s kontroli limitu + 15 s wyszukiwania + 90 s CLI, z zapasem.
        val oczekiwanie = if (request.url.encodedPath == "/wycena") chain.withReadTimeout(150, TimeUnit.SECONDS) else chain
        oczekiwanie.proceed(pojedynczy)
    }
    .addNetworkInterceptor { chain ->
        val response = chain.proceed(chain.request())
        // OkHttp ponawia 503 Retry-After: 0 także przy retryOnConnectionFailure(false).
        // Tylko dla wyceny usuwamy wskazówkę ponowienia; kod i treść błędu zostają.
        if (chain.request().url.encodedPath == "/wycena" && response.code == 503) {
            response.newBuilder().removeHeader("Retry-After").build()
        } else response
    }
    .build()
