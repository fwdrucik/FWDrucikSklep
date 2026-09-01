package pl.fwdrucik.sklep.praca

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import pl.fwdrucik.sklep.GlownaAktywnosc
import pl.fwdrucik.sklep.R
import pl.fwdrucik.sklep.SklepAplikacja
import pl.fwdrucik.sklep.dane.MostChmury
import pl.fwdrucik.sklep.dane.Ustawienia
import pl.fwdrucik.sklep.dane.Wynik
import pl.fwdrucik.sklep.dane.groszeNaZlote

private val Context.magazynObserwatora by preferencesDataStore("obserwator")
private val OSTATNIE_ID = intPreferencesKey("ostatnie_zamowienie_id")

/**
 * Sprawdza, czy przyszło nowe zamówienie, i pokazuje powiadomienie.
 *
 * Zapamiętujemy największy widziany identyfikator zamówienia zamiast liczby
 * zamówień. Liczba potrafi zmaleć, gdy zamówienie zostanie usunięte, i wtedy
 * kolejne nowe zamówienie nie podniosłoby jej ponad zapamiętany stan —
 * powiadomienie by nie przyszło. Identyfikatory tylko rosną.
 */
class ObserwatorZamowien(
    context: Context,
    parametry: WorkerParameters,
) : CoroutineWorker(context, parametry) {

    override suspend fun doWork(): Result {
        val aplikacja = SklepAplikacja.z(applicationContext)
        if (!aplikacja.repozytorium.czyZalogowany()) {
            // Bez zalogowania nie ma czego pilnować. Nie traktujemy tego jako
            // awarii — praca ma zostać w kolejce na później.
            return Result.success()
        }

        val wynik = aplikacja.repozytorium.zamowienia()
        if (wynik !is Wynik.Jest) return Result.retry()

        val zamowienia = wynik.dane

        // ETAP77: przy okazji przepisujemy zamówienia do wspólnej bazy w chmurze, żeby panel
        // warsztatowy je widział. Bez skonfigurowanej chmury to pusty przebieg - i tak ma być.
        val chmura = Ustawienia(applicationContext).chmura.first()
        if (chmura.gotowa) {
            MostChmury.przeniesZamowienia(applicationContext, chmura, zamowienia)
        }

        val najwyzsze = zamowienia.maxOfOrNull { it.id } ?: 0
        val magazyn = applicationContext.magazynObserwatora
        val zapamietane = magazyn.data.first()[OSTATNIE_ID] ?: 0

        if (zapamietane == 0) {
            // Pierwsze uruchomienie: zapisujemy stan bez powiadamiania, żeby nie
            // wysypać na użytkownika informacji o zamówieniach sprzed instalacji.
            magazyn.edit { it[OSTATNIE_ID] = najwyzsze }
            return Result.success()
        }

        val nowe = zamowienia.filter { it.id > zapamietane }
        if (nowe.isNotEmpty()) {
            powiadom(nowe.size, nowe.sumOf { it.sumaGr }, nowe.first().numer)
            magazyn.edit { it[OSTATNIE_ID] = najwyzsze }
        }

        // Kafelek na pulpicie dostaje to samo, co powiadomienie — takze wtedy,
        // gdy nowych nie ma. „Sprawdzone 12:40, bez nowych" to inna informacja
        // niz cisza, bo mowi, ze aplikacja w ogole patrzyla.
        WidgetSklepu.odswiez(applicationContext, nowe.size)
        return Result.success()
    }

    private fun powiadom(ile: Int, sumaGr: Int, numer: String) {
        val wolno = ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!wolno) return

        val otworz = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, GlownaAktywnosc::class.java)
                .putExtra("ekran", "zamowienia"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val tytul = if (ile == 1) "Nowe zamówienie $numer" else "$ile nowych zamówień"
        val powiadomienie = NotificationCompat.Builder(
            applicationContext, SklepAplikacja.KANAL_ZAMOWIENIA
        )
            .setSmallIcon(R.drawable.ic_powiadomienie)
            .setContentTitle(tytul)
            .setContentText("Na kwotę ${groszeNaZlote(sumaGr)}. Dotknij, żeby zobaczyć.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(otworz)
            .build()

        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(ID_POWIADOMIENIA, powiadomienie)
    }

    private companion object {
        const val ID_POWIADOMIENIA = 1001
    }
}
