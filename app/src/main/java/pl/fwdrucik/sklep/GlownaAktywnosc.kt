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

    // Automatyczne przywracanie sesji kreatora nawet po ubiciu zasilania / restarcie
    LaunchedEffect(stan.aktywnyKreatorId) {
        android.util.Log.d("FW_KREATOR", "LaunchedEffect stan.aktywnyKreatorId=${stan.aktywnyKreatorId}, edytowany=$edytowany")
        if (stan.aktywnyKreatorId >= 0 && edytowany < 0) {
            edytowany = stan.aktywnyKreatorId
        }
    }

    LaunchedEffect(edytowany) {
        android.util.Log.d("FW_KREATOR", "LaunchedEffect edytowany=$edytowany, aktywnyKreatorId=${stan.aktywnyKreatorId}")
        if (edytowany >= 0 && stan.aktywnyKreatorId != edytowany) {
            model.ustawAktywnyKreator(edytowany)
        }
    }

    BackHandler(enabled = edytowany < 0) {
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

    if (edytowany >= 0) {
        EkranKreatora(
            istniejacy = if (edytowany > 0) model.znajdz(edytowany) else null,
            agentPracuje = stan.agentPracuje,
            maKluczGemini = stan.kluczGemini.isNotBlank(),
            kopia = stan.kopieRobocze[if (edytowany > 0) edytowany else 0],
            naZapiszKopie = model::zapiszKopie,
            naOdrzucKopie = model::skasujKopie,
            naOpiszZeZdjecia = model::opiszZeZdjecia,
            naPoprawZdjecie = model::poprawZdjecie,
            naPoprawOpis = model::poprawOpis,
            modeleTekstowe = stan.modeleTekstowe,
            modeleObrazowe = stan.modeleObrazowe,
            modelOpisu = stan.modelOpisu,
            modelObrazu = stan.modelObrazu,
            museDostepny = stan.museWarsztatu == "dostepne",
            forgeDziala = stan.stanForge == "dziala",
            comfyDziala = stan.stanComfy == "dziala",
            flowDziala = stan.stanFlow == "dziala",
            metaDziala = stan.stanMeta == "dziala",
            copilotDziala = stan.stanCopilot == "dziala",
            silnikAnimacji = model.silnikDo("animacja"),
            czynnosci = stan.czynnosci,
            naZapisz = { produkt, cena, promo, zdjecie, dodatkowe, animacja, alt ->
                model.zapiszZeZdjeciami(produkt, cena, promo, zdjecie, dodatkowe, animacja, alt) { nowyId ->
                    // Po zapisie nowego produktu zostajemy w kreatorze — teraz da
                    // się dodać kolejne zdjęcia, bo serwer zna już identyfikator.
                    if (edytowany == 0) edytowany = nowyId
                }
            },
            warsztatGotowy = stan.swiatloWarsztatu in listOf("zielony", "zolty", "czerwony"),
            naZlecWarsztatowi = model::zlecWarsztatowi,
            naWgrajPlik = model::wgrajPlikDoOgloszenia,
            naCiagAuto = model::ciagAutomatyczny,
            naWgrajZdjecie = model::wgrajZdjecie,
            naUsunZdjecie = model::usunZdjecie,
            naPobierzZdjecieProduktu = model::pobierzZdjecieProduktu,
            naWyjscie = {
                edytowany = -1
                model.zamknijKreator()
            },
        )
        return
    }

    val nowe = stan.zamowienia.count { it.status == "nowe" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(zakladka.etykieta)
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        pl.fwdrucik.sklep.ui.ekrany.PasekKropekStanu(
                            stanApi = stan.stanApi.kolor,
                            stanGemini = stan.stanGemini.kolor,
                            stanChmury = stan.stanChmury.kolor,
                            stanWarsztatu = stan.swiatloWarsztatu,
                            stanMuse = stan.museWarsztatu,
                            stanForge = stan.stanForge,
                            stanComfy = stan.stanComfy,
                            stanFlow = stan.stanFlow,
                            stanMeta = stan.stanMeta,
                            stanCopilot = stan.stanCopilot,
                            naKlik = { zakladka = Zakladka.Studio },
                        )
                        Spacer(Modifier.weight(1f))
                        ZegarZData()
                    }
                },
                actions = {
                    IconButton(onClick = model::wyloguj) {
                        Icon(Icons.Filled.Logout, contentDescription = "Wyloguj")
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
                FloatingActionButton(onClick = {
                    edytowany = 0
                    model.ustawAktywnyKreator(0)
                }) {
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
                    naEdycje = {
                        edytowany = it
                        model.ustawAktywnyKreator(it)
                    },
                    naStatus = model::zmienStatus,
                    naUsun = model::usun,
                    kopie = stan.kopieRobocze.values.sortedByDescending { it.zapisano },
                    naOtworzKopie = { id ->
                        edytowany = id
                        model.ustawAktywnyKreator(id)
                    },
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
