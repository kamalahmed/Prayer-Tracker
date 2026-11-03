package com.kamalahmed.prayerapp.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kamalahmed.prayerapp.core.location.City
import com.kamalahmed.prayerapp.core.location.CityDatabase
import com.kamalahmed.prayerapp.ui.common.ListRow
import com.kamalahmed.prayerapp.ui.common.RowDivider
import com.kamalahmed.prayerapp.ui.theme.AccentGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Searchable offline world city list (GeoNames). Shows up to [limit] results. */
@Composable
fun CitySearch(selectedLabel: String?, onPick: (City) -> Unit, limit: Int = 30) {
    val context = LocalContext.current.applicationContext
    var query by rememberSaveable { mutableStateOf("") }
    val results by produceState<List<City>?>(null, query) {
        if (query.isNotBlank()) delay(150) // debounce typing
        value = withContext(Dispatchers.Default) { CityDatabase.get(context).search(query, limit) }
    }
    OutlinedTextField(
        value = query, onValueChange = { query = it }, singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        label = { Text("Search cities worldwide") },
        supportingText = { Text("e.g. Sylhet, or \"Springfield, US\"") },
        modifier = Modifier.fillMaxWidth().padding(12.dp),
    )
    val list = results
    when {
        list == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
        list.isEmpty() -> Text("No city found. Try another spelling or enter coordinates below.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp))
        else -> list.forEachIndexed { i, c ->
            if (i > 0) RowDivider()
            ListRow(
                title = listOf(c.flag, c.name).filter { it.isNotEmpty() }.joinToString(" "),
                subtitle = c.display.removePrefix(c.name).removePrefix(", ") + " · ${c.tz}",
                onClick = { onPick(c) }, showChevron = false,
            ) {
                if (selectedLabel == c.label) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = AccentGreen)
            }
        }
    }
}

/** Manual latitude/longitude entry; time zone is taken from the nearest city. */
@Composable
fun ManualCoordinates(onApply: (Double, Double) -> Unit) {
    var lat by rememberSaveable { mutableStateOf("") }
    var lng by rememberSaveable { mutableStateOf("") }
    val la = lat.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
    val lo = lng.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
    Column(Modifier.padding(12.dp)) {
        Row {
            OutlinedTextField(lat, { lat = it }, label = { Text("Latitude") }, singleLine = true,
                isError = lat.isNotBlank() && la == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f).padding(end = 6.dp))
            OutlinedTextField(lng, { lng = it }, label = { Text("Longitude") }, singleLine = true,
                isError = lng.isNotBlank() && lo == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f).padding(start = 6.dp))
        }
        Button(onClick = { if (la != null && lo != null) onApply(la, lo) }, enabled = la != null && lo != null,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Use these coordinates") }
    }
}
