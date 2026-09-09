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
                sections.getValue(current!!).add(cleanNoteLine(line.removePrefix("- ")))
            }
        }
    }

    val structured = listOf("Added", "Changed", "Fixed").mapNotNull { title ->
        sections[title]?.takeIf { it.isNotEmpty() }?.let { ReleaseNoteSection(title, it.toList()) }
    }
    if (structured.isNotEmpty()) return structured

    // v0.7.1/v0.7.2 stripped markdown before it reached this renderer. Keep a fallback so
    // older/plain release bodies still show something instead of an empty "What's new" area.
    val fallback = markdown.lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .filterNot { it.startsWith("#") }
        .map { it.removePrefix("- ").removePrefix("* ") }
        .map(::cleanNoteLine)
        .filter(String::isNotBlank)
        .take(14)
        .toList()

    return if (fallback.isEmpty()) emptyList() else listOf(ReleaseNoteSection("Changes", fallback))
}

private fun cleanNoteLine(value: String): String = value
    .replace("**", "")
    .replace("`", "")
    .trim()

@Composable
internal fun V071UpdateNotes(markdown: String) {
    val sections = remember(markdown) { parseReleaseNotes(markdown) }

    V07Panel {
        Text("What's new", fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Review the changes before you update.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        if (sections.isEmpty()) {
            Text(
                "Release notes are unavailable for this build.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            sections.forEachIndexed { sectionIndex, section ->
                V07SectionLabel(section.title)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth()) {
                    section.items.forEachIndexed { itemIndex, item ->
                        Text(
                            text = "• $item",
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (itemIndex != section.items.lastIndex) Spacer(Modifier.height(9.dp))
                    }
                }
                if (sectionIndex != sections.lastIndex) Spacer(Modifier.height(20.dp))
            }
        }
    }
}
