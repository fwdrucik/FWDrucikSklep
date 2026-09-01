package pl.fwdrucik.sklep

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.fwdrucik.sklep.ui.ModelSklepu
import pl.fwdrucik.sklep.ui.ekrany.EkranInstrukcji
import pl.fwdrucik.sklep.ui.ekrany.KontrolkaWarsztatu
import pl.fwdrucik.sklep.ui.ekrany.ZegarZData
import pl.fwdrucik.sklep.ui.ekrany.EkranKreatora
import pl.fwdrucik.sklep.ui.ekrany.EkranLogowania
import pl.fwdrucik.sklep.ui.ekrany.EkranMagazynu
import pl.fwdrucik.sklep.ui.ekrany.EkranProduktow
import pl.fwdrucik.sklep.ui.ekrany.EkranStudia
import pl.fwdrucik.sklep.ui.ekrany.EkranZamowien
import pl.fwdrucik.sklep.ui.motyw.MotywSklepu

class GlownaAktywnosc : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startowyEkran = intent?.getStringExtra("ekran")

        setContent {
            MotywSklepu {
                Aplikacja(startowyEkran)
            }
        }
    }
}

private enum class Zakladka(val etykieta: String) {
    Produkty("Produkty"),
    Magazyn("Magazyn"),
    Zamowienia("Zamówienia"),
    // Studio = stan silnikow + kolejka do karty. Wczesniej zylo to w kropkach
    // na pasku i w Pomocy: kropka mowila „cos nie gra", nie mowila ile sie czeka.
    Studio("Studio"),
    Instrukcja("Pomoc"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Aplikacja(startowyEkran: String?) {
    val model: ModelSklepu = viewModel()
    val stan by model.stan.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    var zakladka by rememberSaveable {
        mutableStateOf(
            // Powiadomienie o zamówieniu ma otwierać zamówienia, a nie katalog.
            if (startowyEkran == "zamowienia") Zakladka.Zamowienia else Zakladka.Produkty
        )
    }
    var edytowany by rememberSaveable { mutableIntStateOf(-1) }
    var ostatnieWcisniecieWstecz by rememberSaveable { mutableLongStateOf(0L) }
    val snackbar = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val uprawnienia = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            uprawnienia.add(Manifest.permission.POST_NOTIFICATIONS)
            uprawnienia.add(Manifest.permission.READ_MEDIA_IMAGES)
            uprawnienia.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            uprawnienia.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            uprawnienia.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(uprawnienia.toTypedArray())
    }

    // Automatyczne przywracanie sesji kreatora nawet po ubiciu zasilania / restarcie
    LaunchedEffect(stan.aktywnyKreatorId) {
        android.util.Log.d("FW_KREATOR", "LaunchedEffect stan.aktywnyKreatorId=${stan.aktywnyKreatorId}, edytowany=$edytowany")
        if (stan.aktywnyKreatorId >= 0 && edytowany < 0) {
            edytowany = stan.aktywnyKreatorId
        }
    }
    BackHandler(enabled = true) {
        if (zakladka != Zakladka.Produkty) {
            zakladka = Zakladka.Produkty
        } else {
            val teraz = System.currentTimeMillis()
            if (teraz - ostatnieWcisniecieWstecz < 2000) {
                activity?.finish()
            } else {
                ostatnieWcisniecieWstecz = teraz
                Toast.makeText(
                    context,
                    "Naciśnij wstecz ponownie, aby wyjść z aplikacji",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val pytajOPowiadomienia = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(stan.zalogowany) {
        if (stan.zalogowany && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Pytamy dopiero po zalogowaniu — przed nim powiadomienia i tak nie
            // mają o czym powiadamiać, a prośba na powitanie bywa odrzucana.
            pytajOPowiadomienia.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(stan.blad, stan.komunikat) {
        val tekst = stan.blad ?: stan.komunikat
        if (tekst != null) {
            snackbar.showSnackbar(tekst)
            model.wyczyscKomunikaty()
        }
    }

    if (!stan.zalogowany) {
        EkranLogowania(ladowanie = stan.ladowanie, naZaloguj = model::zaloguj)
        return
    }

    val otworzKreator = { id: Int ->
        context.startActivity(
            android.content.Intent(context, KreatorAktywnosc::class.java).apply {
                putExtra(KreatorAktywnosc.EKSTRA_ID, id)
            }
        )
    }

    val nowe = stan.zamowienia.count { it.status == "nowe" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(zakladka.etykieta)
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        KontrolkaWarsztatu(
                            swiatlo = stan.swiatloWarsztatu,
                            opis = stan.opisWarsztatu,
                            naDotkniecie = { zakladka = Zakladka.Studio },
                        )
                    }
                },
                actions = {
                    ZegarZData()
                    IconButton(onClick = model::wyloguj) {
                        Icon(Icons.Filled.Logout, contentDescription = "Wyloguj ze sklepu")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Zakladka.entries.forEach { pozycja ->
                    NavigationBarItem(
                        selected = zakladka == pozycja,
                        onClick = { zakladka = pozycja },
                        // Podpis tylko pod wybrana zakladka.
                        //
                        // PO CO: piec pozycji na telefonie to okolo 70 dp na
                        // kazda, a „Zamowienia" tam nie wchodzi — Compose lamal
                        // slowa i pasek pokazywal „Zamow / ienia", „Produkt / y".
                        // Ikona bez podpisu jest czytelniejsza niz podpis
                        // przelamany w polowie wyrazu.
                        alwaysShowLabel = false,
                        label = { Text(pozycja.etykieta, maxLines = 1) },
                        icon = {
                            val ikona = when (pozycja) {
                                Zakladka.Produkty -> Icons.Filled.Sell
                                Zakladka.Magazyn -> Icons.Filled.Inventory2
                                Zakladka.Zamowienia -> Icons.Filled.Receipt
                                Zakladka.Studio -> Icons.Filled.Memory
                                Zakladka.Instrukcja -> Icons.Filled.HelpOutline
                            }
                            if (pozycja == Zakladka.Zamowienia && nowe > 0) {
                                BadgedBox(badge = { Badge { Text(nowe.toString()) } }) {
                                    Icon(ikona, contentDescription = null)
                                }
                            } else {
                                Icon(ikona, contentDescription = null)
                            }
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            if (zakladka == Zakladka.Produkty) {
                FloatingActionButton(onClick = { otworzKreator(0) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Dodaj produkt")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { odstepy ->
        Column(Modifier.padding(odstepy)) {
            if (stan.ladowanie) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            when (zakladka) {
                Zakladka.Produkty -> EkranProduktow(
                    produkty = stan.produkty,
                    naEdycje = { otworzKreator(it) },
                    naStatus = model::zmienStatus,
                    naUsun = model::usun,
                    kopie = stan.kopieRobocze.values.sortedByDescending { it.zapisano },
                    naOtworzKopie = { otworzKreator(it) },
                    naUsunKopie = model::skasujKopie,
                )
                Zakladka.Magazyn -> EkranMagazynu(
                    produkty = stan.produkty,
                    naZmianeStanu = model::ustawStan,
                )
                Zakladka.Zamowienia -> EkranZamowien(
                    zamowienia = stan.zamowienia,
                    naZmianeStatusu = model::zmienStatusZamowienia,
                )
                Zakladka.Studio -> EkranStudia(
                    stanForge = stan.stanForge,
                    stanComfy = stan.stanComfy,
                    stanFlow = stan.stanFlow,
                    stanMeta = stan.stanMeta,
                    stanCopilot = stan.stanCopilot,
                    stanMuse = stan.museWarsztatu,
                    opisWarsztatu = stan.opisWarsztatu,
                    swiatloWarsztatu = stan.swiatloWarsztatu,
                    kolejka = stan.kolejkaWarsztatu,
                    zadaniaWToku = stan.zadaniaWToku,
                )
                Zakladka.Instrukcja -> EkranInstrukcji(
                    kluczGemini = stan.kluczGemini,
                    adresWarsztatu = stan.adresWarsztatu,
                    swiatloWarsztatu = stan.swiatloWarsztatu,
                    opisWarsztatu = stan.opisWarsztatu,
                    chmura = stan.chmura,
                    museWarsztatu = stan.museWarsztatu,
                    stanForge = stan.stanForge,
                    stanComfy = stan.stanComfy,
                    stanFlow = stan.stanFlow,
                    stanMeta = stan.stanMeta,
                    stanCopilot = stan.stanCopilot,
                    silnikOpisu = stan.silnikOpisu,
                    silnikZdjecia = stan.silnikZdjecia,
                    silnikAnimacji = stan.silnikAnimacji,
                    naZapiszSilnik = model::zapiszSilnik,
                    modelOpisu = stan.modelOpisu,
                    modelObrazu = stan.modelObrazu,
                    modeleTekstowe = stan.modeleTekstowe,
                    modeleObrazowe = stan.modeleObrazowe,
                    naZapiszModele = model::zapiszModele,
                    stanApi = stan.stanApi,
                    stanGemini = stan.stanGemini,
                    stanChmury = stan.stanChmury,
                    naZapiszKlucz = model::zapiszKluczGemini,
                    naZapiszAdres = model::zapiszAdresWarsztatu,
                    naZapiszChmure = model::zapiszChmure,
                    naSprawdzApi = model::sprawdzApi,
                    naSprawdzGemini = model::sprawdzGemini,
                    naSprawdzChmure = model::sprawdzChmure,
                )
            }
        }
    }
}
