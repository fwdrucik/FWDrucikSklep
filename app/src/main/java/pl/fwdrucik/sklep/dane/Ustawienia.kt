package pl.fwdrucik.sklep.dane

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.magazynUstawien by preferencesDataStore("ustawienia")
private val KLUCZ_GEMINI = stringPreferencesKey("klucz_gemini")
private val ADRES_WARSZTATU = stringPreferencesKey("adres_warsztatu")
private val AKTYWNY_KREATOR_ID = intPreferencesKey("aktywny_kreator_id")

// ETAP77: dostep do wspolnego projektu Firebase. Te same cztery wartosci, co w panelu
// warsztatowym - obie aplikacje pisza i czytaja z jednej bazy.
private val FB_PROJECT = stringPreferencesKey("firebase_project_id")
private val FB_APP = stringPreferencesKey("firebase_app_id")
private val FB_KLUCZ = stringPreferencesKey("firebase_api_key")

// Pamiec robocza kreatora: mapa id produktu -> kopia. JSON w jednym polu,
// bo DataStore Preferences nie ma typow zlozonych, a kopii sa jednostki.
private val KOPIE = stringPreferencesKey("kopie_robocze")

// Modele Gemini wybrane przez uzytkownika. Nazwy modeli u Google zmieniaja sie
// co kilka tygodni (2.5 -> 3 -> 3.5 -> 3.6 -> 3.7), a stara nazwa potrafi
// zniknac razem z dzialajacym kluczem. Dlatego wybor jest w ustawieniach,
// a nie wpieczony w kod.
private val MODEL_OPISU = stringPreferencesKey("model_opisu")
private val MODEL_OBRAZU = stringPreferencesKey("model_obrazu")

// Czym wykonywac ktore zadanie. Kazde ma dwie drogi: chmure Google (szybka,
// limitowana, czasem 429) i wlasny komputer w warsztacie (wolniejszy, ale
// za darmo i bez limitow). Dotad wybor zapadal sam, w kodzie — teraz nalezy
// do czlowieka, bo zalezy od tego, co jest pod reka i ile zostalo limitu.
private val SILNIK_OPISU = stringPreferencesKey("silnik_opisu")
private val SILNIK_ZDJECIA = stringPreferencesKey("silnik_zdjecia")
private val SILNIK_ANIMACJI = stringPreferencesKey("silnik_animacji")

// Nauka agenta: poprawki, ktore czlowiek naniosl na jego propozycje.
private val POPRAWKI = stringPreferencesKey("poprawki_agenta")

/** Domyślny adres komputera w sieci domowej — ten sam, co Fooocus-API. */
const val DOMYSLNY_ADRES_WARSZTATU = "http://192.168.0.166:8770"

/**
 * Ten sam komputer, ale widziany przez sieć Tailscale.
 *
 * PO CO: adres domowy działa tylko wtedy, gdy telefon siedzi w tej samej sieci
 * Wi-Fi. Tailscale robi prywatną sieć między urządzeniami — komputer ma w niej
 * stały adres i jest dostępny z pracy, z terenu i przez komórkę, bez otwierania
 * czegokolwiek na świat. Warunek: włączony Tailscale na telefonie.
 */
const val ADRES_ZDALNY_WARSZTATU = "http://100.84.198.20:8770"

/** Najnowszy tekstowy, sprawdzony 31.08.2026. */
const val DOMYSLNY_MODEL_OPISU = "gemini-3.7-flash"

/** Nano Banana 2 — obrazowy odpowiednik tej samej generacji. */
const val DOMYSLNY_MODEL_OBRAZU = "gemini-3.1-flash-image"

/** Silniki opisow: Gemini widzi zdjecie, Muse jest za darmo, ale tylko z tekstu. */
const val SILNIK_GEMINI = "gemini"
const val SILNIK_MUSE = "muse"

/** Silniki zdjec: Gemini w chmurze albo Forge na karcie w warsztacie. */
const val SILNIK_FORGE = "forge"

/**
 * Meta AI — czat na koncie firmowym, sterowany przegladarka na komputerze.
 *
 * Robi i kadry, i krotkie filmy, za darmo i bez limitu kadrow, ktory pierwszy
 * konczy sie przy Gemini. Film powstaje WYLACZNIE ze zdjecia — z samego opisu
 * czat wideo nie zrobi.
 */
const val SILNIK_META = "meta"

/** Silniki animacji: ComfyUI (Wan, lokalnie) albo Flow/Veo przez serwer. */
const val SILNIK_COMFY = "comfy"
const val SILNIK_FLOW = "flow"

/** Wybor zostawiony aplikacji: bierze to, co akurat zywe i najszybsze. */
const val SILNIK_AUTO = "auto"

/**
 * Ustawienia aplikacji: klucz do Gemini i adres serwera warsztatowego.
 *
 * Klucz zostaje na telefonie i nigdzie się stąd nie rusza. Nie ma go w
 * repozytorium ani w kodzie, bo klucz w repozytorium to klucz spalony —
 * wystarczy raz wypchnąć projekt na GitHuba. Kopie zapasowe są dla tego pliku
 * wyłączone razem z ciasteczkiem sesji (res/xml/reguly_kopii.xml).
 */
class Ustawienia(context: Context) {

    private val magazyn = context.applicationContext.magazynUstawien

    val kluczGemini: Flow<String> = magazyn.data.map { it[KLUCZ_GEMINI].orEmpty() }

    /**
     * Adres serwera warsztatowego. Trzymany osobno, bo komputer potrafi
     * dostać inny adres z routera — wtedy poprawia się jedno pole w aplikacji
     * zamiast przebudowywać ją od nowa.
     */
    val adresWarsztatu: Flow<String> = magazyn.data.map {
        it[ADRES_WARSZTATU]?.takeIf(String::isNotBlank) ?: DOMYSLNY_ADRES_WARSZTATU
    }

    /**
     * Kopie robocze kreatora, po id produktu (0 = nowy).
     *
     * Trzymamy je tutaj, a nie w pamieci ekranu, bo caly sens jest w tym, zeby
     * przezyly zamkniecie aplikacji przez system — to zdarza sie przy robocie
     * z telefonem odkladanym na stol.
     */
    val kopieRobocze: Flow<Map<Int, KopiaRobocza>> = magazyn.data.map { dane ->
        val tekst = dane[KOPIE].orEmpty()
        if (tekst.isBlank()) emptyMap()
        else runCatching {
            Json.decodeFromString<Map<Int, KopiaRobocza>>(tekst)
        }.getOrDefault(emptyMap())  // uszkodzony zapis nie moze zablokowac kreatora
    }

    /** Id aktualnie otwartego kreatora (0 = nowy, >0 = edycja, -1 = brak). */
    val aktywnyKreatorId: Flow<Int> = magazyn.data.map {
        it[AKTYWNY_KREATOR_ID] ?: -1
    }

    val modelOpisu: Flow<String> = magazyn.data.map {
        it[MODEL_OPISU]?.takeIf(String::isNotBlank) ?: DOMYSLNY_MODEL_OPISU
    }

    val modelObrazu: Flow<String> = magazyn.data.map {
        it[MODEL_OBRAZU]?.takeIf(String::isNotBlank) ?: DOMYSLNY_MODEL_OBRAZU
    }

    /** „gemini" albo „muse" — czym pisac opisy. */
    val silnikOpisu: Flow<String> = magazyn.data.map {
        it[SILNIK_OPISU]?.takeIf(String::isNotBlank) ?: SILNIK_GEMINI
    }

    /** „gemini" albo „forge" — czym poprawiac zdjecia. */
    val silnikZdjecia: Flow<String> = magazyn.data.map {
        it[SILNIK_ZDJECIA]?.takeIf(String::isNotBlank) ?: SILNIK_GEMINI
    }

    /** „comfy" albo „flow" — czym robic animacje. */
    val silnikAnimacji: Flow<String> = magazyn.data.map {
        it[SILNIK_ANIMACJI]?.takeIf(String::isNotBlank) ?: SILNIK_COMFY
    }

    /**
     * Poprawki uzytkownika — pamiec agenta. Trzymamy ostatnie 60.
     *
     * Limit jest po to, zeby plik ustawien nie rosl bez konca; do promptu i tak
     * idzie tylko dziesiec najswiezszych.
     */
    val poprawki: Flow<List<Poprawka>> = magazyn.data.map { dane ->
        runCatching {
            Json.decodeFromString<List<Poprawka>>(dane[POPRAWKI].orEmpty())
        }.getOrDefault(emptyList())
    }

    suspend fun dopiszPoprawki(nowe: List<Poprawka>) {
        if (nowe.isEmpty()) return
        magazyn.edit { dane ->
            val teraz = runCatching {
                Json.decodeFromString<List<Poprawka>>(dane[POPRAWKI].orEmpty())
            }.getOrDefault(emptyList())
            dane[POPRAWKI] = Json.encodeToString((teraz + nowe).takeLast(60))
        }
    }

    suspend fun zapomnijPoprawki() {
        magazyn.edit { it[POPRAWKI] = "" }
    }

    suspend fun zapiszSilnik(zadanie: String, silnik: String) {
        magazyn.edit {
            when (zadanie) {
                "opis" -> it[SILNIK_OPISU] = silnik
                "zdjecie" -> it[SILNIK_ZDJECIA] = silnik
                "animacja" -> it[SILNIK_ANIMACJI] = silnik
            }
        }
    }

    suspend fun zapiszModele(opisu: String, obrazu: String) {
        magazyn.edit {
            it[MODEL_OPISU] = opisu.trim()
            it[MODEL_OBRAZU] = obrazu.trim()
        }
    }

    suspend fun zapiszKopie(kopia: KopiaRobocza) {
        magazyn.edit { dane ->
            val teraz = runCatching {
                Json.decodeFromString<Map<Int, KopiaRobocza>>(dane[KOPIE].orEmpty())
            }.getOrDefault(emptyMap())
            dane[KOPIE] = Json.encodeToString<Map<Int, KopiaRobocza>>(teraz + (kopia.id to kopia))
        }
    }

    suspend fun skasujKopie(id: Int) {
        magazyn.edit { dane ->
            val teraz = runCatching {
                Json.decodeFromString<Map<Int, KopiaRobocza>>(dane[KOPIE].orEmpty())
            }.getOrDefault(emptyMap())
            dane[KOPIE] = Json.encodeToString<Map<Int, KopiaRobocza>>(teraz - id)
        }
    }

    suspend fun zapiszAktywnyKreatorId(id: Int) {
        magazyn.edit { it[AKTYWNY_KREATOR_ID] = id }
    }

    suspend fun zapiszKluczGemini(klucz: String) {
        magazyn.edit { it[KLUCZ_GEMINI] = klucz.trim() }
    }

    suspend fun usunKluczGemini() {
        magazyn.edit { it.remove(KLUCZ_GEMINI) }
    }

    /**
     * Dostep do Firebase - trojka wartosci przepisana z konsoli.
     *
     * PO CO SKLEP MA PISAC DO CHMURY: zamowienia zbiera backend PHP na serwerze, ale panel
     * warsztatowy nie ma jak ich zobaczyc - to osobna aplikacja z osobnym logowaniem.
     * Wspolna kolekcja w Firestore jest tanszym mostem niz kolejna koncowka w PHP.
     *
     * PUSTE POLA = przenoszenie wylaczone. Sklep dziala wtedy dokladnie tak jak wczesniej.
     */
    val chmura: Flow<TrojkaFirebase> = magazyn.data.map {
        TrojkaFirebase(
            projectId = it[FB_PROJECT]?.takeIf(String::isNotBlank) ?: DOMYSLNY_PROJEKT,
            appId = it[FB_APP]?.takeIf(String::isNotBlank) ?: DOMYSLNA_APLIKACJA,
            apiKey = it[FB_KLUCZ]?.takeIf(String::isNotBlank) ?: DOMYSLNY_KLUCZ,
        )
    }

    suspend fun zapiszChmure(projectId: String, appId: String, apiKey: String) {
        magazyn.edit {
            it[FB_PROJECT] = projectId.trim()
            it[FB_APP] = appId.trim()
            it[FB_KLUCZ] = apiKey.trim()
        }
    }

    suspend fun zapiszAdresWarsztatu(adres: String) {
        // Bez końcowego ukośnika — ścieżki doklejamy sami i dwa ukośniki
        // pod rząd potrafią zmylić serwer.
        magazyn.edit { it[ADRES_WARSZTATU] = adres.trim().trimEnd('/') }
    }
}

/** Komplet danych do polaczenia z Firebase. Pusty projectId znaczy "chmura wylaczona". */
data class TrojkaFirebase(
    val projectId: String = DOMYSLNY_PROJEKT,
    val appId: String = DOMYSLNA_APLIKACJA,
    val apiKey: String = DOMYSLNY_KLUCZ,
) {
    val gotowa: Boolean get() = projectId.isNotBlank() && appId.isNotBlank() && apiKey.isNotBlank()
}

/**
 * Dostep do projektu `fwdrucik-panel` wpisany z gory - tego samego, ktorego uzywa panel
 * warsztatowy. Sklep ma tam WLASNA rejestracje aplikacji (inna nazwa pakietu, wiec inny
 * identyfikator); klucz API jest wspolny dla calego projektu.
 *
 * DLACZEGO KLUCZ MOZE LEZEC W KODZIE: klucz API Firebase jest jawny z zalozenia - normalnie
 * siedzi w google-services.json, ktory laduje sie w kazdym APK. Nie jest haslem i nie otwiera
 * dostepu do danych; pilnuja ich reguly Firestore i lista dozwolonych podpisow aplikacji.
 */
const val DOMYSLNY_PROJEKT = "fwdrucik-panel"
const val DOMYSLNA_APLIKACJA = "1:690524549652:android:d171a52ad00a1347f26d01"
const val DOMYSLNY_KLUCZ = "AIzaSyAjuDWr2oI6Tc3g-f3HwT1MN11gNroGiQQ"
