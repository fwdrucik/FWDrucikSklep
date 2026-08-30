package pl.fwdrucik.sklep.ui.ekrany

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun EkranLogowania(
    ladowanie: Boolean,
    naZaloguj: (String, String) -> Unit,
) {
    var login by remember { mutableStateOf("") }
    var haslo by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Sklep F.W. DRUCIK", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Zaloguj się kontem administratora ze strony fwdrucik.pl.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )

        OutlinedTextField(
            value = login,
            onValueChange = { login = it },
            label = { Text("Login albo e-mail") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = haslo,
            onValueChange = { haslo = it },
            label = { Text("Hasło") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )

        Button(
            onClick = { naZaloguj(login.trim(), haslo) },
            enabled = !ladowanie && login.isNotBlank() && haslo.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) {
            if (ladowanie) {
                CircularProgressIndicator(
                    Modifier.padding(end = 10.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text("Zaloguj")
        }

        Text(
            "Aplikacja łączy się z fwdrucik.pl. Bez internetu nie da się nic zmienić " +
                "w katalogu — wszystkie dane siedzą na serwerze, nie w telefonie.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 28.dp),
        )
    }
}
