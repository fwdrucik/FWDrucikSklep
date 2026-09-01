import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Wlasny klucz podpisujacy jest do wskazania w local.properties (plik jest w .gitignore).
val localProps = Properties()
rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { localProps.load(it) }

// Numer wersji brany z historii gita, a nie wpisywany recznie.
//
// PO CO: recznie wpisywany numer rozjezdza sie z nazwami paczek w archiwum - po miesiacu
// nie da sie powiedziec, ktora paczka na telefonie odpowiada ktoremu stanowi kodu. Liczba
// commitow rosnie zawsze i nigdy nie maleje, czyli spelnia to, czego Android wymaga
// od versionCode. Panel warsztatowy liczy to tak samo.
//
// PO CO BAZA 10: wszystkie wczesniejsze paczki sklepu mialy versionCode 1. Baza odsuwa
// nowa numeracje od tamtych, zeby dwie rozne paczki nie chodzily pod tym samym numerem.
fun zGita(vararg argumenty: String, gdyBrak: String): String = try {
    val proces = ProcessBuilder(listOf("git") + argumenty)
        .directory(rootProject.projectDir)
        .redirectErrorStream(false)
        .start()
    val wynik = proces.inputStream.bufferedReader().readText().trim()
    proces.waitFor()
    if (proces.exitValue() == 0 && wynik.isNotBlank()) wynik else gdyBrak
} catch (e: Exception) {
    // Rozpakowana kopia repozytorium bez gita ma sie dalej budowac.
    gdyBrak
}

val liczbaCommitow = zGita("rev-list", "--count", "HEAD", gdyBrak = "0").toInt()
val skrotCommita = zGita("rev-parse", "--short", "HEAD", gdyBrak = "bezgita")

android {
    namespace = "pl.fwdrucik.sklep"
    compileSdk = 34

    defaultConfig {
        applicationId = "pl.fwdrucik.sklep"
        // Osobny identyfikator od pl.fwdrucik.panel — to ma być druga ikona
        // na pulpicie, a nie aktualizacja aplikacji warsztatowej.
        minSdk = 26
        targetSdk = 34
        versionCode = 10 + liczbaCommitow
        versionName = "1.$liczbaCommitow+$skrotCommita"

        // Adres serwera trzymamy w BuildConfig, żeby test na innym adresie nie
        // wymagał grzebania w kodzie. Zmiana w jednym miejscu.
        buildConfigField("String", "ADRES_API", "\"https://fwdrucik.pl\"")
    }

    // ETAP77: podpis paczki produkcyjnej.
    //
    // PO CO: bez tego `assembleRelease` daje app-release-unsigned.apk, ktorego Android
    // odmawia zainstalowac. Domyslnie bierzemy ten sam klucz debugowy, co panel
    // warsztatowy - to nie jest klucz do sklepu Play, tylko tyle, zeby paczka wchodzila
    // na telefon. Wlasny klucz podaje sie w local.properties.
    //
    // UWAGA PRZY PIERWSZEJ INSTALACJI: Android pozwala zaktualizowac aplikacje wylacznie
    // paczka podpisana tym samym kluczem. Jesli na telefonie stoi wersja podpisana czyms
    // innym, trzeba ja najpierw odinstalowac.
    signingConfigs {
        create("wlasny") {
            val plik = localProps.getProperty("klucz.plik", "")
            if (plik.isNotBlank() && file(plik).exists()) {
                storeFile = file(plik)
                storePassword = localProps.getProperty("klucz.haslo", "")
                keyAlias = localProps.getProperty("klucz.alias", "fwdrucik")
                keyPassword = localProps.getProperty("klucz.hasloKlucza", "")
            } else {
                storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("wlasny")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.video)
    implementation(libs.androidx.work)
    implementation(libs.androidx.datastore)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)
}
