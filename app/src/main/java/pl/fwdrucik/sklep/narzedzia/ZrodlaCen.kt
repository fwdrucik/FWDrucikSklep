package pl.fwdrucik.sklep.narzedzia

import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import java.math.BigDecimal
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

// Najpierw cały token kwoty, dopiero potem format: błędne „12 40 zł” nie może dać końcówki „40 zł”.
private const val TOKEN_KWOTY = "[+\\-−]?[\\s\\p{Zs}]*[0-9][0-9.,'’\\s\\p{Zs}]*"
private val kwotaPrzedPln = Regex("(?<![\\p{L}\\p{N}_.,+\\-−])($TOKEN_KWOTY)(?:zł|PLN)(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
private val kwotaPoPln = Regex("(?<![\\p{L}\\p{N}_])(?:PLN|zł)[ \\u00a0\\u202f]*($TOKEN_KWOTY)(?![\\p{L}\\p{N}_.,])", RegexOption.IGNORE_CASE)
private val kwotaZOdstepami = Regex("(?:[0-9]+|[1-9][0-9]{0,2}(?:[ \\u00a0\\u202f][0-9]{3})+)(?:[,.][0-9]{1,2})?")
private val kwotaZKropkami = Regex("[1-9][0-9]{0,2}(?:\\.[0-9]{3})+(?:,[0-9]{1,2})?")

/** Sprawdza literalną kwotę PLN, bez wnioskowania o porównywalności ani cenach sprzedaży. */
fun fragmentPotwierdzaCenePln(fragment: String, cena: Double): Boolean {
    if (!cena.isFinite() || cena <= 0) return false
    val oczekiwana = BigDecimal.valueOf(cena)
    val zSufiksem = kwotaPrzedPln.findAll(fragment).toList()
    // „40 zł 20 cm”: waluta należy już do kwoty 40, nie może uzasadnić liczby 20.
    // Rezerwujemy również niepoprawne tokeny, aby nie odzyskiwać z nich innych cen.
    val zPrefiksem = kwotaPoPln.findAll(fragment).filter { prefiks ->
        zSufiksem.none { sufiks -> prefiks.range.first in sufiks.range }
    }
    return (zSufiksem.asSequence() + zPrefiksem).any { trafienie ->
        val token = trafienie.groupValues[1].trim()
        val liczba = when {
            kwotaZOdstepami.matches(token) -> token.replace(Regex("[ \\u00a0\\u202f]"), "").replace(',', '.')
            kwotaZKropkami.matches(token) -> token.replace(".", "").replace(',', '.')
            else -> return@any false
        }
        liczba.toBigDecimalOrNull()?.compareTo(oczekiwana) == 0
    }
}

/** Walidacja składni bez DNS i bez pobierania strony. Otwieranie tylko po dotknięciu. */
fun bezpiecznyUrlZrodla(adres: String): String? {
    if (adres.any { it.isWhitespace() }) return null
    var dekodowany = adres
    // Sprawdzamy także kodowanie procentowe, włącznie z podwójnym kodowaniem.
    // Plus w zwykłym URL nie oznacza odstępu. Nie wykonujemy zapytań DNS.
    repeat(5) {
        if (dekodowany.any { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() || it == '\\' } ||
            Regex("%(?:2f|5c)", RegexOption.IGNORE_CASE).containsMatchIn(dekodowany)) return null
        val kolejny = runCatching { URLDecoder.decode(dekodowany.replace("+", "%2B"), "UTF-8") }.getOrNull() ?: return null
        if (kolejny != dekodowany) {
            if (it == 4) return null
            dekodowany = kolejny
        }
    }
    val uri = runCatching { URI(adres) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null ||
        uri.rawAuthority.orEmpty().any { it == '@' || it == '%' } ||
        (uri.port != -1 && uri.port !in 1..65535)) return null
    val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return null
    val labels = host.split('.')
    if (host.length > 253 || labels.size < 2 || labels.any {
            !it.matches(Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"))
        } || !labels.last().matches(Regex("[a-z]{2,63}|xn--[a-z0-9-]+"))) return null
    // Pojedyncze nazwy, literały IP (także skrócone/hex/IPv6) i strefy lokalne nie są źródłami publicznymi.
    if (labels.last() in setOf("localhost", "local", "lan", "internal", "intranet", "home", "arpa", "onion", "invalid", "test", "example", "alt")) return null
    return adres.toHttpUrlOrNull()?.newBuilder()?.host(host)?.build()?.toString()
}
