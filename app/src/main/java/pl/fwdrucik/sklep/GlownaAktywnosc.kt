package pl.fwdrucik.sklep

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Sell
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.fwdrucik.sklep.ui.ModelSklepu
import pl.fwdrucik.sklep.ui.ekrany.EkranInstrukcji
import pl.fwdrucik.sklep.ui.ekrany.KontrolkaWarsztatu
import pl.fwdrucik.sklep.ui.ekrany.EkranKreatora
import pl.fwdrucik.sklep.ui.ekrany.EkranLogowania
import pl.fwdrucik.sklep.ui.ekrany.EkranMagazynu
import pl.fwdrucik.sklep.ui.ekrany.EkranProduktow
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
    Instrukcja("Pomoc"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Aplikacja(startowyEkran: String?) {
    val model: ModelSklepu = viewModel()
    val stan by model.stan.collectAsStateWithLifecycle()

    var zakladka by remember {
        mutableStateOf(
            // Powiadomienie o zamówieniu ma otwierać zamówienia, a nie katalog.
            if (startowyEkran == "zamowienia") Zakladka.Zamowienia else Zakladka.Produkty
        )
    }
    var edytowany by remember { mutableIntStateOf(-1) }
    val snackbar = remember { SnackbarHostState() }

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
            naOpiszZeZdjecia = model::opiszZeZdjecia,
            naPoprawZdjecie = model::poprawZdjecie,
            naZapisz = { produkt, cena, promo, zdjecie, alt ->
                model.zapiszZeZdjeciem(produkt, cena, promo, zdjecie, alt) { nowyId ->
                    // Po zapisie nowego produktu zostajemy w kreatorze — teraz da
                    // się dodać kolejne zdjęcia, bo serwer zna już identyfikator.
                    if (edytowany == 0) edytowany = nowyId
                }
            },
            warsztatGotowy = stan.swiatloWarsztatu in listOf("zielony", "zolty", "czerwony"),
            naZlecWarsztatowi = model::zlecWarsztatowi,
            naWgrajZdjecie = model::wgrajZdjecie,
            naUsunZdjecie = model::usunZdjecie,
            naWyjscie = { edytowany = -1 },
        )
        return
    }

    val nowe = stan.zamowienia.count { it.status == "nowe" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(zakladka.etykieta) },
                actions = {
                    KontrolkaWarsztatu(
                        swiatlo = stan.swiatloWarsztatu,
                        opis = stan.opisWarsztatu,
                        naDotkniecie = { zakladka = Zakladka.Instrukcja },
                    )
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
                        label = { Text(pozycja.etykieta) },
                        icon = {
                            val ikona = when (pozycja) {
                                Zakladka.Produkty -> Icons.Filled.Sell
                                Zakladka.Magazyn -> Icons.Filled.Inventory2
                                Zakladka.Zamowienia -> Icons.Filled.Receipt
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
                FloatingActionButton(onClick = { edytowany = 0 }) {
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
                    naEdycje = { edytowany = it },
                    naStatus = model::zmienStatus,
                    naUsun = model::usun,
                )
                Zakladka.Magazyn -> EkranMagazynu(
                    produkty = stan.produkty,
                    naZmianeStanu = model::ustawStan,
                )
                Zakladka.Zamowienia -> EkranZamowien(
                    zamowienia = stan.zamowienia,
                    naZmianeStatusu = model::zmienStatusZamowienia,
                )
                Zakladka.Instrukcja -> EkranInstrukcji(
                    kluczGemini = stan.kluczGemini,
                    adresWarsztatu = stan.adresWarsztatu,
                    swiatloWarsztatu = stan.swiatloWarsztatu,
                    opisWarsztatu = stan.opisWarsztatu,
                    chmura = stan.chmura,
                    naZapiszKlucz = model::zapiszKluczGemini,
                    naZapiszAdres = model::zapiszAdresWarsztatu,
                    naZapiszChmure = model::zapiszChmure,
                )
            }
        }
    }
}
