package com.hfremovalsadelaide.invoices.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hfremovalsadelaide.invoices.ui.theme.SageOutline

/**
 * Single, stable layout used on every screen size.
 *
 * Phones in portrait show a bottom bar; wide windows (tablets, phones in landscape) show a side
 * rail. Only the navigation surface changes: [content] always sits at the same position in the
 * composition, so switching layouts on rotation does NOT dispose it. That matters because the
 * content hosts the WebView, and recreating it would reload the page and lose typed input.
 */
@Composable
fun AdaptiveShellFrame(
    useRail: Boolean,
    rail: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        if (useRail) {
            rail()
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(SageOutline)
                    .padding(end = 1.dp)
            )
        }

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = snackbarHost,
            bottomBar = { if (!useRail) bottomBar() },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            content = content
        )
    }
}
