package pl.fwdrucik.sklep.siec

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

private val Context.magazynSesji by preferencesDataStore("sesja")
private val KLUCZ_CIASTKA = stringPreferencesKey("ciastka")

/**
 * Ciasteczka sesji administratora.
 *
 * Serwer uwierzytelnia po ciasteczku `fw_sesja` (httpOnly) i sprawdza CSRF
 * porównując ciasteczko `fw_csrf` z nagłówkiem `X-Fw-Csrf`. Retrofit sam z
 * siebie ciasteczek nie trzyma, więc robi to ten CookieJar.
 *
 * Zapis na dysk jest po to, żeby nie logować się przy każdym otwarciu
 * aplikacji. Kopie zapasowe i przenoszenie na inny telefon są dla tego pliku
 * wyłączone w reguly_kopii.xml — token sesji administratora nie ma prawa
 * wyjechać z urządzenia.
 */
class SlojNaCiastka(context: Context) : CookieJar {

    private val magazyn = context.applicationContext.magazynSesji
    private val ciastka = mutableMapOf<String, Cookie>()

    init {
        // Odczyt przy starcie jest blokujący, ale to jeden mały wpis czytany raz
        // przed pierwszym zapytaniem — asynchroniczność dawałaby tu wyścig.
        runBlocking {
            val zapisane = magazyn.data.first()[KLUCZ_CIASTKA].orEmpty()
            zapisane.split('\n').filter { it.isNotBlank() }.forEach { linia ->
                val (nazwa, wartosc) = linia.split('=', limit = 2).let {
                    if (it.size == 2) it[0] to it[1] else return@forEach
                }
                ciastka[nazwa] = Cookie.Builder()
                    .name(nazwa).value(wartosc)
                    .domain(DOMENA).path("/").build()
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { ciastka[it.name] = it }
        zapiszNaDysk()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = ciastka.values.toList()

    fun tokenCsrf(): String? = ciastka["fw_csrf"]?.value

    fun czyZalogowany(): Boolean = ciastka["fw_sesja"]?.value?.isNotBlank() == true

    fun wyczysc() {
        ciastka.clear()
        zapiszNaDysk()
    }

    private fun zapiszNaDysk() {
        val tekst = ciastka.values.joinToString("\n") { "${it.name}=${it.value}" }
        runBlocking { magazyn.edit { it[KLUCZ_CIASTKA] = tekst } }
    }

    private companion object {
        const val DOMENA = "fwdrucik.pl"
    }
}

/**
 * Dokłada nagłówek CSRF do każdego POST-a.
 *
 * Serwer odrzuca zapis bez tego nagłówka błędem 403. Token bierze się z
 * ciasteczka, które przychodzi razem z zalogowaniem.
 */
class NaglowekCsrf(private val sloj: SlojNaCiastka) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val zapytanie = chain.request()
        if (zapytanie.method != "POST") return chain.proceed(zapytanie)
        val token = sloj.tokenCsrf() ?: return chain.proceed(zapytanie)
        return chain.proceed(
            zapytanie.newBuilder().header("X-Fw-Csrf", token).build()
        )
    }
}
