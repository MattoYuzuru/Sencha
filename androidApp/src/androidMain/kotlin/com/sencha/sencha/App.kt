@file:Suppress("MagicNumber")

package com.sencha.sencha

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sencha.sencha.core.data.InMemoryModelCatalog
import com.sencha.sencha.core.model.ModelCapability
import com.sencha.sencha.core.model.ModelDescriptor
import com.sencha.sencha.core.model.ModelId

@Composable
@Preview
fun App() {
    val catalog = remember {
        InMemoryModelCatalog(
            listOf(
                ModelDescriptor(
                    id = ModelId("local-llm"),
                    displayName = "Local LLM",
                    capabilities = setOf(ModelCapability.LLM),
                ),
                ModelDescriptor(
                    id = ModelId("vision-proxy"),
                    displayName = "Vision Proxy",
                    capabilities = setOf(ModelCapability.VISION),
                ),
            )
        )
    }
    val models = remember { catalog.listModels() }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Sencha", style = MaterialTheme.typography.headlineMedium)
            Text("Models available: ${models.size}")
            models.forEach { model ->
                Text("- ${model.displayName}")
            }
        }
    }
}
