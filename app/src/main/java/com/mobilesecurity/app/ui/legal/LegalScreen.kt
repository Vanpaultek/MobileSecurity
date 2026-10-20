package com.mobilesecurity.app.ui.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen() {
    val sections = LegalContent.sections
    var selected by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selected) {
            sections.forEachIndexed { index, section ->
                Tab(
                    selected = index == selected,
                    onClick = { selected = index },
                    text = { Text(section.title) }
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RenderMarkdown(sections[selected].body)
        }
    }
}

@Composable
private fun RenderMarkdown(markdown: String) {
    val lines = markdown.lines()
    for (line in lines) {
        when {
            line.startsWith("# ") -> Text(
                line.removePrefix("# "),
                style = MaterialTheme.typography.headlineMedium
            )
            line.startsWith("## ") -> Text(
                line.removePrefix("## "),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 12.dp)
            )
            line.startsWith("- ") -> Text(
                "• ${line.removePrefix("- ")}",
                style = MaterialTheme.typography.bodyMedium
            )
            line.isBlank() -> {}
            else -> Text(line, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
