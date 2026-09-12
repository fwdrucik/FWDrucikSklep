package pl.fwdrucik.sklep

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import org.junit.Assert.*
import org.junit.Test
import pl.fwdrucik.sklep.ui.ekrany.pamietajZapytanieWyceny
import pl.fwdrucik.sklep.ui.ekrany.pasujeDoZapytania
import kotlin.coroutines.EmptyCoroutineContext

class PamiecZapytaniaWycenyTest {
    private val zapytanie = "Miska\nżywica\n20 cm\nzywica"

    @Test fun odtworzenieKompozycjiZachowujeDostepDoWynikuBezNowegoWyszukiwania() {
        val zapis = wKompozycji { pamiec -> pamiec.value = zapytanie }
        wKompozycji(zapis) { odtworzona ->
            // To rzeczywisty warunek widoczności źródeł i przycisku użycia ceny w kreatorze.
            assertTrue(odtworzona.pasujeDoZapytania("Miska\nżywica\n20 cm\nzywica"))
            listOf("Wazon\nżywica\n20 cm\nzywica", "Miska\nceramika\n20 cm\nzywica",
                "Miska\nżywica\n30 cm\nzywica", "Miska\nżywica\n20 cm\ndrewno").forEach { zmienione ->
                assertFalse(zmienione, odtworzona.pasujeDoZapytania(zmienione))
            }
        }
    }

    @Test fun nowaKompozycjaBezZapisanegoZapytaniaNieUjawniaStaregoWyniku() {
        wKompozycji { pamiec -> assertFalse(pamiec.pasujeDoZapytania(zapytanie)) }
    }

    private fun wKompozycji(zapis: Map<String, List<Any?>>? = null, sprawdz: (MutableState<String>) -> Unit): Map<String, List<Any?>> {
        val rejestr = SaveableStateRegistry(zapis) { true }
        val recomposer = Recomposer(EmptyCoroutineContext)
        val kompozycja = Composition(object : AbstractApplier<Unit>(Unit) {
            override fun insertTopDown(index: Int, instance: Unit) = Unit
            override fun insertBottomUp(index: Int, instance: Unit) = Unit
            override fun remove(index: Int, count: Int) = Unit
            override fun move(from: Int, to: Int, count: Int) = Unit
            override fun onClear() = Unit
        }, recomposer)
        try {
            lateinit var pamiec: MutableState<String>
            kompozycja.setContent {
                CompositionLocalProvider(LocalSaveableStateRegistry provides rejestr) {
                    pamiec = pamietajZapytanieWyceny(7)
                }
            }
            sprawdz(pamiec)
            return rejestr.performSave()
        } finally {
            kompozycja.dispose()
            recomposer.cancel()
        }
    }
}
