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
        chain.proceed(pojedynczy)
    }
    .build()
