package pl.fwdrucik.sklep.pomoc

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File

/**
 * Miejsce na zdjęcie z aparatu.
 *
 * Aparat to obcy proces i nie wolno mu podać zwykłej ścieżki do pliku —
 * Android rzuca wtedy `FileUriExposedException` i aparat się nie otwiera.
 * Dostaje adres `content://` wystawiony przez `FileProvider`, opisany
 * w `res/xml/pliki.xml`.
 *
 * Pliki lądują w pamięci podręcznej: system sprząta ją sam, gdy zabraknie
 * miejsca, a zdjęcie i tak jedzie na serwer zaraz po zrobieniu.
 */
object Aparat {

    fun nowePlikDoZdjecia(context: Context): File {
        val katalog = File(context.cacheDir, "zdjecia").apply { mkdirs() }
        return File(katalog, "aparat_${System.currentTimeMillis()}.jpg")
    }

    fun adresDlaAparatu(context: Context, plik: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.pliki", plik)
}
