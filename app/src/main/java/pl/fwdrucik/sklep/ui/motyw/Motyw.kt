package pl.fwdrucik.sklep.ui.motyw

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Kolory przepisane z assets/style.css sklepu.
 *
 * To ta sama firma i ten sam katalog produktów, więc aplikacja ma wyglądać jak
 * przedłużenie strony, a nie jak osobne narzędzie. Stąd stalowe tło warsztatu
 * i cyjan jako kolor działania — dokładnie te wartości co na fwdrucik.pl.
 */
private val Stal = Color(0xFF0B0D10)
private val StalJasna = Color(0xFF14171C)
private val Panel = Color(0xFF1A1E24)
private val Linia = Color(0xFF2A2F38)
private val Niebieski = Color(0xFF2EC2FF)
private val NiebieskiCiemny = Color(0xFF1A7FA8)
private val Pomaranczowy = Color(0xFFFF8A3D)
private val Czerwony = Color(0xFFE5433A)
private val Tekst = Color(0xFFE7EBF0)
private val Przygaszony = Color(0xFF9AA4B2)

private val Ciemny = darkColorScheme(
    primary = Niebieski,
    onPrimary = Stal,
    primaryContainer = NiebieskiCiemny,
    onPrimaryContainer = Tekst,
    secondary = Pomaranczowy,
    onSecondary = Stal,
    background = Stal,
    onBackground = Tekst,
    surface = StalJasna,
    onSurface = Tekst,
    surfaceVariant = Panel,
    onSurfaceVariant = Przygaszony,
    outline = Linia,
    error = Czerwony,
    onError = Color.White,
)

/**
 * Aplikacja jest zawsze ciemna — niezależnie od ustawienia telefonu.
 *
 * To nie jest lenistwo, tylko decyzja: fwdrucik.pl nie ma wersji jasnej, więc
 * jasny wariant aplikacji wyglądałby jak cudzy program. Do tego okno aktywności
 * ma ciemne tło z motywu XML; podpięcie jasnej palety Material dawało czarny
 * tekst na ciemnym tle i ekran logowania był nie do odczytania.
 *
 * `Surface` jest tu po to, żeby tło pomalowało cały ekran, a nie tylko obszar
 * zajęty przez treść.
 */
@Composable
fun MotywSklepu(tresc: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Ciemny) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            content = tresc,
        )
    }
}
