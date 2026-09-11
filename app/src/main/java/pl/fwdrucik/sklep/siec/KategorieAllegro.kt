package pl.fwdrucik.sklep.siec

import kotlinx.serialization.Serializable
import java.io.IOException

@Serializable
data class KategoriaAllegro(
    val id: String = "",
    val nazwa: String = "",
    val sciezka: String = "",
)

@Serializable
data class OdpowiedzKategoriiAllegro(
    val ok: Boolean = false,
    val kategorie: List<KategoriaAllegro> = emptyList(),
    val blad: String? = null,
)

/** Tylko odczyt. Lista nie oznacza wyboru; wybiera ją użytkownik w kreatorze. */
class KategorieWarsztatu(private val api: SerwerWarsztatu) {
    suspend fun szukaj(base: String, fraza: String): List<KategoriaAllegro> {
        val szukana = fraza.trim()
        require(szukana.length in 2..100) { "Wpisz od 2 do 100 znaków nazwy wyrobu, aby znaleźć kategorię." }
        val odp = api.kategorieAllegro("${base.trim().trimEnd('/')}/allegro/kategorie", szukana)
        if (!odp.ok) throw IOException(odp.blad?.takeIf { it.isNotBlank() }
            ?: "Nie udało się pobrać kategorii. Spróbuj ponownie później.")
        return odp.kategorie.map { it.copy(id = it.id.trim(), nazwa = it.nazwa.trim(), sciezka = it.sciezka.trim()) }
            .filter { it.id.matches(Regex("[0-9]+")) && it.nazwa.isNotBlank() }
            .distinctBy { it.id }
            .take(10)
    }
}
