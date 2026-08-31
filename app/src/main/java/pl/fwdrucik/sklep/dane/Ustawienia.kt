package pl.fwdrucik.sklep.dane

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.magazynUstawien by preferencesDataStore("ustawienia")
private val KLUCZ_GEMINI = stringPreferencesKey("klucz_gemini")
private val ADRES_WARSZTATU = stringPreferencesKey("adres_warsztatu")

// ETAP77: dostep do wspolnego projektu Firebase. Te same cztery wartosci, co w panelu
// warsztatowym - obie aplikacje pisza i czytaja z jednej bazy.
private val FB_PROJECT = stringPreferencesKey("firebase_project_id")
private val FB_APP = stringPreferencesKey("firebase_app_id")
private val FB_KLUCZ = stringPreferencesKey("firebase_api_key")

/** Domyślny adres komputera w sieci domowej — ten sam, co Fooocus-API. */
const val DOMYSLNY_ADRES_WARSZTATU = "http://192.168.0.166:8770"

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
