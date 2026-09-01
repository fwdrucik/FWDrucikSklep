package pl.fwdrucik.sklep.narzedzia

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object GaleriaZapis {

    /**
     * Zapisuje wygenerowany plik graficzny lub wideo bezposrednio do publicznej galerii telefonu
     * (Pictures/FWDrucik lub Movies/FWDrucik) i odswieza indeks MediaStore.
     */
    fun zapiszDoGalerii(context: Context, plik: File, czyWideo: Boolean = false): Uri? {
        return try {
            val rozszerzenie = plik.extension.ifBlank { if (czyWideo) "mp4" else "jpg" }
            val mime = if (czyWideo) "video/mp4" else if (rozszerzenie.equals("png", true)) "image/png" else "image/jpeg"
            val folderNazwa = if (czyWideo) Environment.DIRECTORY_MOVIES + "/FWDrucik" else Environment.DIRECTORY_PICTURES + "/FWDrucik"
            val nazwa = "FWDrucik_" + System.currentTimeMillis() + "." + rozszerzenie

            // 1. Zapis bezposredni do folderu publicznego (Pictures/FWDrucik lub Movies/FWDrucik)
            val katPubliczny = File(
                Environment.getExternalStoragePublicDirectory(if (czyWideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES),
                "FWDrucik"
            ).apply { mkdirs() }
            val plikPubliczny = File(katPubliczny, nazwa)
            runCatching {
                FileInputStream(plik).use { we ->
                    FileOutputStream(plikPubliczny).use { wy -> we.copyTo(wy) }
                }
            }

            // 2. Wpis do MediaStore z ContentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, nazwa)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, folderNazwa)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val kolekcja = if (czyWideo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
            }

            val uri = context.contentResolver.insert(kolekcja, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(plik).use { wejscie ->
                        wejscie.copyTo(out)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }
            }

            // 3. Wymuszenie przeskanowania przez MediaScanner
            MediaScannerConnection.scanFile(
                context,
                arrayOf(plikPubliczny.absolutePath),
                arrayOf(mime),
            ) { _, scannedUri ->
                // Skonczono skanowanie
            }

            uri ?: Uri.fromFile(plikPubliczny)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Kopiuje wybrany kadr (z aparatu, galerii lub PhotoPickera) do prywatnego katalogu szkicow.
     * Zabezpiecza przed utrata uprawnien do URI po wylaczeniu/ubiciu aplikacji.
     */
    fun zabezpieczLokalnie(context: Context, uri: Uri, idProduktu: Int, sufiks: String = "glowne"): File? {
        return try {
            if (uri.scheme == "file") {
                val f = File(uri.path ?: return null)
                if (f.exists() && f.parentFile?.name == "szkice") {
                    return f
                }
            }
            val katalog = File(context.filesDir, "szkice").apply { mkdirs() }
            val docelowy = File(katalog, "szkic_" + idProduktu + "_" + sufiks + "_" + System.currentTimeMillis() + ".jpg")
            context.contentResolver.openInputStream(uri)?.use { wejscie ->
                FileOutputStream(docelowy).use { out ->
                    wejscie.copyTo(out)
                }
            }
            if (docelowy.exists() && docelowy.length() > 0L) docelowy else null
        } catch (e: Exception) {
            null
        }
    }
}