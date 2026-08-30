package pl.fwdrucik.sklep.dane

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ETAP77: przenoszenie zamówień do wspólnej bazy w chmurze.
 *
 * PO CO: zamówienia zbiera backend PHP na serwerze, ale panel warsztatowy nie ma jak ich
 * zobaczyć - to osobna aplikacja, z osobnym logowaniem, często na innym telefonie. Dopisanie
 * kolejnej końcówki do PHP znaczyłoby budowanie własnej synchronizacji od zera. Wspólna
 * kolekcja w Firestore robi to samo taniej: sklep dopisuje, panel czyta.
 *
 * ŹRÓDŁEM PRAWDY ZOSTAJE PHP. Firestore jest kopią do podglądu, a nie drugą bazą zamówień -
 * gdyby obie mogły zmieniać stan niezależnie, po tygodniu nie dałoby się powiedzieć, która
 * ma rację. Panel zmienia tu tylko status, i to jest cała jego władza.
 *
 * BEZ KONFIGURACJI NIC SIĘ NIE DZIEJE: puste pola w ustawieniach = sklep działa dokładnie
 * tak, jak przed tą zmianą.
 */
object MostChmury {

    private const val KOLEKCJA = "zamowienia"

    @Volatile
    private var zalozona = false

    private fun aplikacja(context: Context, cfg: TrojkaFirebase): FirebaseApp? {
        if (!cfg.gotowa) return null
        FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?.let { return it }
        if (zalozona) return null

        val opcje = FirebaseOptions.Builder()
            .setProjectId(cfg.projectId)
            .setApplicationId(cfg.appId)
            .setApiKey(cfg.apiKey)
            .build()

        return runCatching {
            FirebaseApp.initializeApp(context, opcje).also { zalozona = true }
        }.getOrNull()
    }

    /**
     * Przepisuje zamówienia do chmury.
     *
     * Zapisujemy **nagłówki**, nie całe zamówienia: numer, status, nick, sumę, datę i liczbę
     * pozycji. Pozycje i adres klienta zostają w PHP - panel warsztatowy potrzebuje wiedzieć,
     * że coś przyszło i za ile, a nie znać adresu domowego klienta.
     *
     * @return liczba przepisanych zamówień; 0 znaczy „nie było czego" albo „nie udało się".
     */
    suspend fun przeniesZamowienia(
        context: Context,
        cfg: TrojkaFirebase,
        zamowienia: List<Zamowienie>,
    ): Int = withContext(Dispatchers.IO) {
        if (zamowienia.isEmpty()) return@withContext 0
        val app = aplikacja(context, cfg) ?: return@withContext 0
        val auth = FirebaseAuth.getInstance(app)

        runCatching {
            if (auth.currentUser == null) czekaj(auth.signInAnonymously())

            val baza = FirebaseFirestore.getInstance(app)
            var ile = 0
            zamowienia.forEach { z ->
                val dokument = baza.collection(KOLEKCJA).document(z.numer.ifBlank { z.id.toString() })
                val istnieje = czekaj(dokument.get()).exists()

                // Pola opisowe przepisujemy zawsze; status TYLKO przy zakladaniu dokumentu.
                //
                // PO CO TO ROZROZNIENIE: panel warsztatowy zmienia w chmurze status ("nowe" ->
                // "w realizacji"). Gdyby kazde przeniesienie nadpisywalo status tym, co widzi
                // sklep w PHP, praca panelu cofalaby sie przy najblizszym sprawdzeniu zamowien -
                // i wygladaloby to na gubienie zmian bez zadnej widocznej przyczyny.
                val dane = mutableMapOf<String, Any>(
                    "numer" to z.numer,
                    "nick" to z.nick,
                    "suma_gr" to z.sumaGr,
                    "utworzone" to z.utworzone,
                    "pozycji" to z.pozycje.size,
                    "zrodlo" to "sklep",
                )
                if (!istnieje) dane["status"] = z.status

                czekaj(dokument.set(dane, com.google.firebase.firestore.SetOptions.merge()))
                ile++
            }
            ile
        }.getOrDefault(0)
    }

    private suspend fun <T> czekaj(task: com.google.android.gms.tasks.Task<T>): T =
        suspendCancellableCoroutine { cont ->
            task.addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            task.addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }
}
