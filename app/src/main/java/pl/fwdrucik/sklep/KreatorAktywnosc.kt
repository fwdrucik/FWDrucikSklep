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

    LaunchedEffect(Unit) {
        model.ustawAktywnyKreator(idProduktu)
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
            val istniejacy = if (idProduktu > 0) stan.produkty.firstOrNull { it.id == idProduktu } else null
            if (idProduktu > 0 && istniejacy == null && (stan.ladowanie || stan.produkty.isEmpty())) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            } else {
                if (stan.ladowanie) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                EkranKreatora(
                    istniejacy = istniejacy,
                    agentPracuje = stan.agentPracuje,
                    maKluczGemini = stan.kluczGemini.isNotBlank(),
                    kopia = stan.kopieRobocze[if (idProduktu > 0) idProduktu else 0],
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
                        model.zapiszZeZdjeciami(produkt, cena, promo, zdjecie, dodatkowe, animacja, alt) { _ ->
                            model.zamknijKreator()
                            naZamknij()
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
                        model.zamknijKreator()
                        naZamknij()
                    },
                )
            }
        }
    }
}
