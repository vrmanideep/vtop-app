package com.vtop.ui.legal

import android.content.Context

object MarkdownAssetLoader {

    fun loadMarkdown(
        context: Context,
        assetFile: String
    ): String {

        return try {

            val rawText = context.assets
                .open(assetFile)
                .bufferedReader()
                .use { it.readText() }

            // FIX: Compose Markdown aggressively collapses empty lines.
            // This detects 3 or more newlines and replaces them with an
            // invisible Zero-Width Space (\u200B) paragraph.
            // This forces the Compose UI to render a physical blank line.
            rawText.replace(Regex("(\\r?\\n){3,}"), "\n\n\u200B\n\n")

        } catch (e: Exception) {

            """
            # Error
            
            Failed to load document.
            
            ${e.message}
            """.trimIndent()
        }
    }
}