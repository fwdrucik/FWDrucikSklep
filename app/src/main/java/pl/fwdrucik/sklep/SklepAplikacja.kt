package pl.fwdrucik.sklep

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import pl.fwdrucik.sklep.dane.AgentProduktu
import pl.fwdrucik.sklep.dane.Repozytorium
import pl.fwdrucik.sklep.dane.Ustawienia
import pl.fwdrucik.sklep.praca.ObserwatorZamowien
import pl.fwdrucik.sklep.siec.GeminiApi
import pl.fwdrucik.sklep.siec.NaglowekCsrf
import pl.fwdrucik.sklep.siec.SerwerWarsztatu
import pl.fwdrucik.sklep.siec.SklepApi
import pl.fwdrucik.sklep.siec.SlojNaCiastka
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Zależności aplikacji.
 *
 * Świadomie bez Hilta: to jeden moduł z jednym repozytorium i trzema obiektami
 * sieciowymi. Wstrzykiwanie przez adnotacje dołożyłoby KSP, dłuższą kompilację
 * i drugą tabelę zgodności wersji, a nie usunęłoby ani jednej linii tutaj.
 */
class SklepAplikacja : Application() {

    lateinit var repozytorium: Repozytorium
        private set

    lateinit var agent: AgentProduktu
        private set

    lateinit var ustawienia: Ustawienia
        private set

    lateinit var warsztat: SerwerWarsztatu
        private set

    override fun onCreate() {
        super.onCreate()

        val sloj = SlojNaCiastka(this)
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        val klient = OkHttpClient.Builder()
            .cookieJar(sloj)
            .addInterceptor(NaglowekCsrf(sloj))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // Zdjęcia z telefonu bywają duże, a wysyłka idzie przez komórkę.
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        val api = Retrofit.Builder()
            .baseUrl(BuildConfig.ADRES_API.trimEnd('/') + "/")
            .client(klient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SklepApi::class.java)

        ustawienia = Ustawienia(this)

        // Osobny klient dla Google — BEZ ciasteczek i bez nagłówka CSRF.
        // Klient sklepowy nosi token sesji administratora fwdrucik.pl; wysłanie
        // go pod obcy adres byłoby wyciekiem danych logowania.
        val klientGemini = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        val gemini = Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(klientGemini)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeminiApi::class.java)

        agent = AgentProduktu(gemini, this)

        // Serwer warsztatowy na komputerze. Osobny klient, bo to sasiad
        // w sieci domowej, a nie fwdrucik.pl — nie ma po co dostawac
        // ciasteczka sesji sklepu.
        //
        // Krotkie limity czasu: gdy komputer jest wylaczony, telefon ma sie
        // dowiedziec o tym w sekunde, a nie wisiec pol minuty na kropce stanu.
        val klientWarsztatu = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .build()

        warsztat = Retrofit.Builder()
            // Adres bazowy jest atrapa — kazde wywolanie podaje pelny adres
            // przez @Url, bo uzytkownik moze go zmienic w ustawieniach.
            .baseUrl("http://127.0.0.1/")
            .client(klientWarsztatu)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SerwerWarsztatu::class.java)

        repozytorium = Repozytorium(api, sloj, this, warsztat)

        zalozKanalPowiadomien()
        zaplanujSprawdzanieZamowien()
    }

    private fun zalozKanalPowiadomien() {
        val kanal = NotificationChannel(
            KANAL_ZAMOWIENIA,
            "Nowe zamówienia",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Powiadomienie, gdy w sklepie pojawi się nowe zamówienie."
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(kanal)
    }

    /**
     * Sprawdzanie zamówień w tle.
     *
     * Co 15 minut, bo to najkrótszy odstęp, na jaki Android pozwala pracy
     * cyklicznej — proszenie o mniej i tak zostałoby wydłużone przez system.
     * Wymóg sieci jest po to, żeby nie budzić telefonu bez internetu.
     */
    private fun zaplanujSprawdzanieZamowien() {
        val praca = PeriodicWorkRequestBuilder<ObserwatorZamowien>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "obserwator-zamowien",
            ExistingPeriodicWorkPolicy.KEEP,
            praca,
        )
    }

    companion object {
        const val KANAL_ZAMOWIENIA = "zamowienia"

        fun z(context: Context): SklepAplikacja =
            context.applicationContext as SklepAplikacja
    }
}
