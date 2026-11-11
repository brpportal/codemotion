package com.brpportal.codemotion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun EffectsGallery(
    effects: List<EffectDef>,
    selected: MutableList<String>,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(12.dp))
            Text(
                "Effects Gallery",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            "${selected.size} effects selected — New Script me yehi IDs jayenge.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        if (effects.isEmpty()) {
            Text("Catalog load nahi hua (assets/effects.json missing).")
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize()
        ) {
            items(effects) { fx ->
                Card(Modifier.padding(6.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        EffectPreview(fx, Modifier.fillMaxWidth().height(104.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(fx.name, fontWeight = FontWeight.Bold)
                        Text(
                            "${fx.category} • ${fx.defaultDurationMs}ms",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(fx.description, style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selected.contains(fx.id),
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (!selected.contains(fx.id)) selected.add(fx.id)
                                    } else {
                                        selected.remove(fx.id)
                                    }
                                }
                            )
                            Text("Script me add", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
