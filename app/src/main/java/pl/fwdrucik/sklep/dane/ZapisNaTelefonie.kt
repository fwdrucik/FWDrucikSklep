package pl.fwdrucik.sklep.dane

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Zapis wyników do pamięci telefonu — tam, gdzie widzi je galeria.
 *
 * PO CO: animacja z Veo i poprawione zdjęcie z Forge lądowały w katalogu
 * roboczym aplikacji. Plik istniał, ale nie było go w galerii, nie dało się go
 * wysłać klientowi ani wrzucić na Instagram bez szukania po pamięci telefonu.
 * Z punktu widzenia człowieka przy stole to jest to samo, co „nie ma pliku".
 *
 * Zapisujemy przez `MediaStore`, a nie wprost do `/sdcard`: od Androida 10
 * aplikacja nie ma prawa pisać po cudzych katalogach, a `MediaStore` dokłada
 * wpis do galerii od razu — bez tego plik bywa niewidoczny do restartu telefonu.
 */
object ZapisNaTelefonie {

    /** Podkatalog w Obrazach i Filmach — żeby robota z warsztatu nie mieszała się z resztą. */
    private const val FOLDER = "FW DRUCIK"

    /**
     * Kopiuje plik do galerii.
     *
     * @return czytelna ścieżka do pokazania człowiekowi albo `null`, gdy się nie udało.
     */
    fun zapisz(context: Context, zrodlo: File): String? {
        if (!zrodlo.exists()) return null

        val wideo = zrodlo.extension.lowercase() in listOf("mp4", "mov", "webm", "avi", "mkv")
        val typMime = when {
            wideo -> "video/" + zrodlo.extension.lowercase().let { if (it == "mkv") "x-matroska" else it }
            zrodlo.extension.lowercase() == "png" -> "image/png"
            else -> "image/jpeg"
        }

        val zbior = if (wideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val katalog = if (wideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        val nazwa = nazwaZDaty(zrodlo)

        val pola = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, nazwa)
            put(MediaStore.MediaColumns.MIME_TYPE, typMime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$katalog/$FOLDER")
                // IS_PENDING chroni przed tym, ze galeria pokaze plik w polowie
                // kopiowania — przy filmie z Veo to kilka megabajtow.
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val adres: Uri = context.contentResolver.insert(zbior, pola) ?: return null

        return runCatching {
            context.contentResolver.openOutputStream(adres)!!.use { wy ->
                zrodlo.inputStream().use { we -> we.copyTo(wy) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.update(
                    adres,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
            "$katalog/$FOLDER/$nazwa"
        }.getOrElse {
            // Niedokonczony wpis zostawilby w galerii pusta pozycje.
            runCatching { context.contentResolver.delete(adres, null, null) }
            null
        }
    }

    /**
     * Nazwa z datą i godziną.
     *
     * Pliki z serwera nazywają się `e1ae0e231160.mp4` — identyfikatorem zlecenia.
     * W galerii, obok zdjęć z aparatu, taka nazwa nic nie mówi; data i godzina
     * pozwalają dopasować plik do tego, co się wtedy robiło.
     */
    private fun nazwaZDaty(zrodlo: File): String {
        val stempel = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale("pl"))
            .format(java.util.Date())
        return "drucik_${stempel}.${zrodlo.extension.ifBlank { "jpg" }}"
    }
}
