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
import pl.fwdrucik.sklep.siec.NaglowekUserAgent
import pl.fwdrucik.sklep.siec.SerwerWarsztatu
import pl.fwdrucik.sklep.siec.SklepApi
import pl.fwdrucik.sklep.siec.SlojNaCiastka
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

import android.os.Build.VERSION.SDK_INT
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder

/**
 * Zależności aplikacji.
 *
 * Świadomie bez Hilta: to jeden moduł z jednym repozytorium i trzema obiektami
 * sieciowymi. Wstrzykiwanie przez adnotacje dołożyłoby KSP, dłuższą kompilację
 * i drugą tabelę zgodności wersji, a nie usunęłoby ani jednej linii tutaj.
 */
class SklepAplikacja : Application(), ImageLoaderFactory {

    lateinit var repozytorium: Repozytorium
        private set

    lateinit var agent: AgentProduktu
        private set

    lateinit var ustawienia: Ustawienia
        private set

    lateinit var warsztat: SerwerWarsztatu
        private set

    /** Wystawiony osobno, bo kontrolka w Pomocy sprawdza klucz bez udziału agenta. */
    lateinit var gemini: GeminiApi
        private set

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        // Push o zamowieniach zamiast czekania na kolejne odpytanie.
        zapiszSieNaPowiadomienia()

        val sloj = SlojNaCiastka(this)
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        val klient = OkHttpClient.Builder()
            .cookieJar(sloj)
            .addInterceptor(NaglowekUserAgent())
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

        this.gemini = gemini

        // Serwer warsztatowy na komputerze. Osobny klient, bo to sasiad
        // w sieci domowej, a nie fwdrucik.pl — nie ma po co dostawac
        // ciasteczka sesji sklepu.
        //
        // Krotkie limity czasu: gdy komputer jest wylaczony, telefon ma sie
        // dowiedziec o tym w sekunde, a nie wisiec pol minuty na kropce stanu.
        val klientWarsztatu = pl.fwdrucik.sklep.siec.klientWarsztatu()

        warsztat = Retrofit.Builder()
            // Adres bazowy jest atrapa — kazde wywolanie podaje pelny adres
            // przez @Url, bo uzytkownik moze go zmienic w ustawieniach.
            .baseUrl("http://127.0.0.1/")
            .client(klientWarsztatu)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SerwerWarsztatu::class.java)

        // Agent dostaje OBA silniki: Gemini czyta zdjecie, Muse pisze tekst
        // przez most na komputerze, gdy klucza Gemini brak albo zostal odrzucony.
        agent = AgentProduktu(gemini, warsztat, this)

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
     * Zapasowe sprawdzanie zamówień w tle.
     *
     * Od czasu wprowadzenia push-a (`UslugaPowiadomien`) to jest **druga
     * droga**, nie pierwsza: powiadomienie przychodzi w ułamku sekundy, a to
     * odpytywanie ma tylko złapać przypadki, w których push nie dotarł —
     * telefon bez usług Google, wyłączona sieć, stojący nadawca.
     *
     * Odstęp podniesiony z 15 minut do godziny. Piętnaście minut miało sens,
     * gdy było jedynym źródłem wiedzy o zamówieniu; przy działającym push-u
     * budzenie telefonu cztery razy na godzinę jest tylko podatkiem od baterii.
     */
    private fun zaplanujSprawdzanieZamowien() {
        val praca = PeriodicWorkRequestBuilder<ObserwatorZamowien>(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "obserwator-zamowien",
            // UPDATE, a nie KEEP: telefony, ktore maja juz zaplanowana prace
            // co 15 minut, musza dostac nowy odstep. Przy KEEP stara praca
            // zostalaby na zawsze.
            ExistingPeriodicWorkPolicy.UPDATE,
            praca,
        )
    }

    companion object {
        const val KANAL_ZAMOWIENIA = "zamowienia"

        /** Temat FCM, na ktory ida powiadomienia o zamowieniach. */
        const val TEMAT_ZAMOWIENIA = "zamowienia-fwdrucik"

        /**
         * Zapisuje telefon na push o zamowieniach.
         *
         * Temat zamiast tokenu urzadzenia: nadawca nie musi prowadzic rejestru
         * telefonow, a wymiana albo przeinstalowanie aplikacji niczego nie psuje.
         * Wolamy to przy starcie i po kazdej zmianie tokenu — zapis jest
         * bezpieczny do powtorzenia.
         */
        fun zapiszSieNaPowiadomienia() {
            runCatching {
                com.google.firebase.messaging.FirebaseMessaging.getInstance()
                    .subscribeToTopic(TEMAT_ZAMOWIENIA)
            }
        }

        fun z(context: Context): SklepAplikacja =
            context.applicationContext as SklepAplikacja
    }
}
