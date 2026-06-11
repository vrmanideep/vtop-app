package com.vtop.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.vtop.ui.legal.LegalDocumentType
import com.vtop.ui.legal.MarkdownAssetLoader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocumentScreen(
    type: LegalDocumentType,
    onBack: () -> Unit
) {

    val context = LocalContext.current

    val markdownContent = remember(type) {
        MarkdownAssetLoader.loadMarkdown(
            context = context,
            assetFile = type.assetFile
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(type.title)
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(
                        rememberScrollState()
                    )
                    .padding(16.dp)
        ) {

            Markdown(
                content = markdownContent
            )
        }
    }
}