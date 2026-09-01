package pl.fwdrucik.sklep.praca

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.fwdrucik.sklep.SklepAplikacja

/**
 * Przyjmuje klucz Gemini z komputera przez kabel (adb), zamiast przepisywania go palcem.
 *
 * PO CO: klucz ma 56 znaków bez sensu i wpisywanie go na klawiaturze telefonu kończy się
 * literówką, po której aplikacja mówi tylko „403". Komputer, na którym ten klucz i tak
 * leży, potrafi go wysłać jednym poleceniem.
 *
 * CZEGO TU NIE MA: odczytu. Odbiornik wyłącznie zapisuje. Nie ma żadnej drogi, którą
 * dałoby się klucz z aplikacji wyjąć — ani przez adb, ani przez inną aplikację.
 *
 * Odbiornik jest `exported`, bo inaczej powłoka adb nie miałaby jak go zawołać. Znaczy to,
 * że inna aplikacja na tym telefonie mogłaby PODMIENIĆ klucz — nie podejrzeć. Najgorsze,
 * co z tego wynika, to przestawienie agenta na cudzy klucz, widoczne od razu w kontrolce
 * w Pomocy. Dlatego sprawdzamy jeszcze kształt: cokolwiek, co nie wygląda na klucz Google,
 * jest odrzucane bez zapisu.
 */
class OdbiorKlucza : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AKCJA) return

        val klucz = intent.getStringExtra("klucz")?.trim().orEmpty()
        if (!wyglada_na_klucz(klucz)) {
            Log.w(ETYKIETA, "Odrzucony klucz: zly ksztalt (dlugosc ${klucz.length}).")
            return
        }

        // goAsync: zapis do DataStore jest asynchroniczny, a odbiornik bez tego
        // konczy sie, zanim korutyna zdazy cokolwiek zapisac.
        val czekaj = goAsync()
        val ustawienia = SklepAplikacja.z(context.applicationContext).ustawienia
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ustawienia.zapiszKluczGemini(klucz)
                // W logu NIGDY cala wartosc — tylko tyle, zeby dalo sie potwierdzic,
                // ze doszedl ten klucz, o ktory chodzilo.
                Log.i(ETYKIETA, "Zapisany klucz ${klucz.take(3)}...${klucz.takeLast(4)}")
            } finally {
                czekaj.finish()
            }
        }
    }

    private fun wyglada_na_klucz(klucz: String): Boolean =
        (klucz.startsWith("AQ.") || klucz.startsWith("AIza")) && klucz.length in 30..200

    companion object {
        const val AKCJA = "pl.fwdrucik.sklep.USTAW_KLUCZ"
        private const val ETYKIETA = "OdbiorKlucza"
    }
}
