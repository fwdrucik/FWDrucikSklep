package pl.fwdrucik.sklep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.fwdrucik.sklep.ui.ModelSklepu
import pl.fwdrucik.sklep.ui.ekrany.EkranKreatora
import pl.fwdrucik.sklep.ui.motyw.MotywSklepu

class KreatorAktywnosc : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val idProduktu = intent.getIntExtra(EKSTRA_ID, 0)

        setContent {
            MotywSklepu {
                KreatorZawartosc(
                    idProduktu = idProduktu,
                    naZamknij = { finish() },
                )
            }
        }
    }

    companion object {
        const val EKSTRA_ID = "id_produktu"
    }
}

@Composable
private fun KreatorZawartosc(
    idProduktu: Int,
    naZamknij: () -> Unit,
) {
    val model: ModelSklepu = viewModel()
    val stan by model.stan.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var aktywneId by rememberSaveable { mutableIntStateOf(idProduktu) }

    LaunchedEffect(aktywneId) {
        model.ustawAktywnyKreator(aktywneId)
    }

    LaunchedEffect(stan.blad, stan.komunikat) {
        val tekst = stan.blad ?: stan.komunikat
        if (tekst != null) {
            snackbar.showSnackbar(tekst)
            model.wyczyscKomunikaty()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
    ) { odstepy ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(odstepy)
        ) {
            val istniejacy = if (aktywneId > 0) stan.produkty.firstOrNull { it.id == aktywneId } else null
            if (!stan.kopieWczytane || (aktywneId > 0 && istniejacy == null && stan.ladowanie)) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            } else if (aktywneId > 0 && istniejacy == null) {
                androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Text("Nie udało się wczytać produktu. Sprawdź połączenie i spróbuj ponownie.")
                    androidx.compose.material3.Button(onClick = model::odswiez) { androidx.compose.material3.Text("Wczytaj ponownie") }
                    androidx.compose.material3.TextButton(onClick = { model.zachowajIZamknij(naZamknij) }) { androidx.compose.material3.Text("Wróć do produktów") }
                }
            } else {
                if (stan.ladowanie) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                EkranKreatora(
                    istniejacy = istniejacy,
                    agentPracuje = stan.agentPracuje || stan.ladowanie,
                    postepPracy = stan.komunikat,
                    bladPracy = stan.blad,
                    bladWyceny = stan.bladWyceny,
                    wyszukiwanieKategorii = stan.wyszukiwanieKategorii,
                    naSzukajKategorii = model::znajdzKategorieAllegro,
                    katalogAi = stan.katalogAi,
                    katalogAiWToku = stan.katalogWToku,
                    bladKataloguAi = stan.bladKatalogu,
                    naOdswiezModeleAi = model::odswiezKatalogAi,
                    naOpisAi = model::napiszOpisAi,
                    naZlecModelem = model::zlecModelem,
                    naCiagModelami = { foto, tekst, proporcje, dodatkowe, obraz, wideo, poZdjeciu, poFilmie ->
                        model.ciagAutomatyczny(foto, tekst, proporcje, dodatkowe, "auto", poZdjeciu, poFilmie, obraz, wideo)
                    },
                    maKluczGemini = stan.kluczGemini.isNotBlank(),
                    kopia = stan.kopieRobocze[aktywneId],
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
                        model.zapiszZeZdjeciami(produkt, cena, promo, zdjecie, dodatkowe, animacja, alt,
                            poCzesciowymZapisie = { aktywneId = it },
                        ) { _ ->
                            model.zamknijKreator()
                            naZamknij()
                        }
                    },
                    warsztatGotowy = stan.swiatloWarsztatu in listOf("zielony", "zolty", "czerwony"),
                    naZlecWarsztatowi = model::zlecWarsztatowi,
                    naWgrajPlik = model::wgrajPlikDoOgloszenia,
                    naCiagAuto = { foto, tekst, proporcje, dodatkowe, silnik, poZdjeciu, poFilmie ->
                        model.ciagAutomatyczny(foto, tekst, proporcje, dodatkowe, silnik, poZdjeciu, poFilmie)
                    },
                    naWgrajZdjecie = model::wgrajZdjecie,
                    naUsunZdjecie = model::usunZdjecie,
                    naPobierzZdjecieProduktu = model::pobierzZdjecieProduktu,
                    naImportujAllegro = { url ->
                        model.importujZAukcjiAllegro(url) { _ ->
                            model.zamknijKreator()
                            naZamknij()
                        }
                    },
                    naSynchronizujAllegro = { url, aId, nzw, cen, kat, op, opK ->
                        model.synchronizujAukcjeAllegro(url, aId, nzw, cen, kat, op, opK) { _ ->
                            model.zamknijKreator()
                            naZamknij()
                        }
                    },
                    naWyjscie = {
                        model.zachowajIZamknij(naZamknij)
                    },
                    badanieCenyWToku = stan.badanieCenyWToku,
                    sugerowanaCenaRynkowa = stan.sugerowanaCenaRynkowa,
                    sugerowanaCenaAllegro = stan.sugerowanaCenaAllegro,
                    minCenaRynkowa = stan.minCenaRynkowa,
                    maxCenaRynkowa = stan.maxCenaRynkowa,
                    wycenaZmierzona = stan.wycenaZmierzona,
                    ostrzezenieWyceny = stan.ostrzezenieWyceny,
                    sprawdzonoWyceny = stan.sprawdzonoWyceny,
                    ofertyRynkowe = stan.ofertyRynkowe,
                    naZbadajCeneRynkowa = model::zbadajCeneRynkowa,
                    naUtworzSzkicAllegro = model::utworzSzkicAllegro,
                )
            }
        }
    }
}
