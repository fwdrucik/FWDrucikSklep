package pl.fwdrucik.sklep.praca

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import pl.fwdrucik.sklep.GlownaAktywnosc
import pl.fwdrucik.sklep.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Kafelek sklepu na pulpicie: godzina, data i nowe zamówienia.
 *
 * PO CO: żeby dowiedzieć się o zamówieniu, trzeba było wejść do aplikacji.
 * Widget mówi to samo bez otwierania czegokolwiek — a przy robocie w warsztacie
 * telefon leży na stole ekranem do góry i to jest cała droga do informacji.
 *
 * Zegar rysuje `TextClock` z Androida, nie nasz kod: system odświeża go sam,
 * bez budzenia aplikacji. Widget budzi się tylko wtedy, gdy zmienia się liczba
 * zamówień — czyli po sprawdzeniu w tle albo po push-u.
 */
class WidgetSklepu : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        identyfikatory: IntArray,
    ) {
        identyfikatory.forEach { id -> narysuj(context, manager, id) }
    }

    private fun narysuj(context: Context, manager: AppWidgetManager, id: Int) {
        val widok = RemoteViews(context.packageName, R.layout.widget_sklep)

        val ustawienia = context.getSharedPreferences(PLIK, Context.MODE_PRIVATE)
        val nowe = ustawienia.getInt(KLUCZ_NOWE, 0)
        val sprawdzone = ustawienia.getLong(KLUCZ_SPRAWDZONE, 0L)

        widok.setTextViewText(
            R.id.widget_zamowienia,
            when {
                nowe > 0 -> "Nowe zamówienia: $nowe"
                sprawdzone > 0 -> "Bez nowych zamówień"
                else -> "Dotknij, żeby otworzyć sklep"
            },
        )
        widok.setTextViewText(
            R.id.widget_sprawdzone,
            if (sprawdzone > 0) {
                "sprawdzone " + SimpleDateFormat("HH:mm", Locale("pl")).format(Date(sprawdzone))
            } else {
                ""
            },
        )

        // Dotkniecie otwiera od razu zamowienia, a nie ekran startowy —
        // widget istnieje po to, zeby skrocic droge, a nie zaczac ja od nowa.
        val otworz = PendingIntent.getActivity(
            context,
            0,
            Intent(context, GlownaAktywnosc::class.java).putExtra("ekran", "zamowienia"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        widok.setOnClickPendingIntent(R.id.widget_korzen, otworz)

        manager.updateAppWidget(id, widok)
    }

    companion object {
        private const val PLIK = "widget_sklepu"
        private const val KLUCZ_NOWE = "nowe"
        private const val KLUCZ_SPRAWDZONE = "sprawdzone"

        /**
         * Zapisuje stan i odświeża wszystkie kafelki.
         *
         * Wołane po sprawdzeniu zamówień w tle i po push-u. Trzymamy to
         * w `SharedPreferences`, a nie w DataStore: widget rysuje się w procesie
         * launchera, synchronicznie, i nie ma gdzie czekać na korutynę.
         */
        fun odswiez(context: Context, nowe: Int) {
            context.getSharedPreferences(PLIK, Context.MODE_PRIVATE).edit()
                .putInt(KLUCZ_NOWE, nowe)
                .putLong(KLUCZ_SPRAWDZONE, System.currentTimeMillis())
                .apply()

            val manager = AppWidgetManager.getInstance(context)
            val kafelki = manager.getAppWidgetIds(ComponentName(context, WidgetSklepu::class.java))
            if (kafelki.isEmpty()) return
            manager.notifyAppWidgetViewDataChanged(kafelki, R.id.widget_zamowienia)
            context.sendBroadcast(
                Intent(context, WidgetSklepu::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, kafelki)
                }
            )
        }
    }
}
