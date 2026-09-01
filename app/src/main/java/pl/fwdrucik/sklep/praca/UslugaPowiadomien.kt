package pl.fwdrucik.sklep.praca

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import pl.fwdrucik.sklep.GlownaAktywnosc
import pl.fwdrucik.sklep.R
import pl.fwdrucik.sklep.SklepAplikacja

/**
 * Powiadomienia o zamówieniach przychodzą teraz push-em, nie z odpytywania.
 *
 * PO CO: `WorkManager` sprawdzał sklep **co 15 minut**, bo tyle wynosi
 * najkrótszy odstęp, na jaki pozwala Android. Klient stojący nad telefonem
 * dostawał więc wiadomość o swoim zamówieniu nawet kwadrans po fakcie —
 * a przy odbiorze osobistym to jest różnica między „już pakuję" a ciszą.
 * Push przychodzi w ułamku sekundy i w Firebase jest darmowy bez limitu.
 *
 * ODPYTYWANIE ZOSTAJE JAKO ZAPASOWA DROGA. Push wymaga usług Google i tego,
 * żeby ktoś go wysłał; gdy telefon jest bez sieci albo nadawca stoi, robotę
 * przejmuje stary `ObserwatorZamowien`. Lepiej dowiedzieć się z opóźnieniem
 * niż nie dowiedzieć się wcale.
 *
 * CO PRZYCHODZI: wiadomość z polami `numer`, `suma_gr`, `ile`. Treści nie
 * budujemy po stronie nadawcy, żeby telefon mógł ją pokazać po swojemu —
 * i żeby w chmurze nie lądował ani grosz danych klienta ponad kwotę.
 */
class UslugaPowiadomien : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Token zmienia się przy przeinstalowaniu i czyszczeniu danych.
        // Nie wysyłamy go nigdzie: telefon słucha TEMATU, a nie własnego
        // adresu — dzięki temu nadawca nie musi prowadzić żadnego rejestru
        // urządzeń, a wymiana telefonu nie wymaga niczyjej pamięci.
        Log.i(ETYKIETA, "Nowy token FCM (dlugosc ${token.length})")
        SklepAplikacja.zapiszSieNaPowiadomienia()
    }

    override fun onMessageReceived(wiadomosc: RemoteMessage) {
        val dane = wiadomosc.data
        val numer = dane["numer"].orEmpty()
        val ile = dane["ile"]?.toIntOrNull() ?: 1
        val sumaGr = dane["suma_gr"]?.toIntOrNull() ?: 0

        val wolno = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!wolno) {
            Log.w(ETYKIETA, "Push przyszedl, ale brak zgody na powiadomienia.")
            return
        }

        val otworz = PendingIntent.getActivity(
            this,
            0,
            Intent(this, GlownaAktywnosc::class.java).putExtra("ekran", "zamowienia"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // Serwer warsztatowy przysyla gotowy tytul i tresc (np. „Animacja gotowa"),
        // sklep — surowe pola zamowienia. Obsługujemy oba, bo to ten sam kanal.
        val tytul = dane["tytul"] ?: when {
            ile > 1 -> "$ile nowych zamówień"
            numer.isNotBlank() -> "Nowe zamówienie $numer"
            else -> "Nowe zamówienie"
        }
        val tresc = dane["tresc"] ?: if (sumaGr > 0) {
            "Na kwotę ${pl.fwdrucik.sklep.dane.groszeNaZlote(sumaGr)}. Dotknij, żeby zobaczyć."
        } else {
            "Dotknij, żeby zobaczyć."
        }

        val powiadomienie = NotificationCompat.Builder(this, SklepAplikacja.KANAL_ZAMOWIENIA)
            .setSmallIcon(R.drawable.ic_powiadomienie)
            .setContentTitle(tytul)
            .setContentText(tresc)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(otworz)
            .build()

        getSystemService(NotificationManager::class.java).notify(ID_POWIADOMIENIA, powiadomienie)
        WidgetSklepu.odswiez(this, ile)
    }

    private companion object {
        const val ETYKIETA = "UslugaPowiadomien"

        /** Ten sam identyfikator co przy odpytywaniu — dwie drogi, jedno powiadomienie. */
        const val ID_POWIADOMIENIA = 4201
    }
}
