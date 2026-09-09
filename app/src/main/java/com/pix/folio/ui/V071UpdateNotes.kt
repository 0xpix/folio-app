package com.pix.folio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class ReleaseNoteSection(
    val title: String,
    val items: List<String>,
)

private fun parseReleaseNotes(markdown: String): List<ReleaseNoteSection> {
    if (markdown.isBlank()) return emptyList()

    val sections = linkedMapOf<String, MutableList<String>>()
    var current: String? = null

    markdown.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.startsWith("### ") -> {
                val title = line.removePrefix("### ").trim()
                if (title in setOf("Added", "Changed", "Fixed")) {
                    current = title
                    sections.getOrPut(title) { mutableListOf() }
                } else {
                    current = null
                }
            }

            line.startsWith("- ") && current != null -> {
                sections.getValue(current!!).add(
                    line.removePrefix("- ")
                        .replace("**", "")
                        .replace("`", "")
                        .trim()
                )
            }
        }
    }

    return listOf("Added", "Changed", "Fixed").mapNotNull { title ->
        sections[title]?.takeIf { it.isNotEmpty() }?.let { ReleaseNoteSection(title, it.toList()) }
    }
}

@Composable
internal fun V071UpdateNotes(markdown: String) {
    val sections = remember(markdown) { parseReleaseNotes(markdown) }
    if (sections.isEmpty()) return

    V07Panel {
        Text("What's new", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))

        sections.forEachIndexed { sectionIndex, section ->
            V07SectionLabel(section.title)
            Spacer(Modifier.height(6.dp))
            Column(Modifier.fillMaxWidth()) {
                section.items.forEachIndexed { itemIndex, item ->
                    Text(
                        text = "• $item",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (itemIndex != section.items.lastIndex) Spacer(Modifier.height(7.dp))
                }
            }
            if (sectionIndex != sections.lastIndex) Spacer(Modifier.height(16.dp))
        }
    }
}
