package dev.dazaike.cadence.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.dazaike.cadence.ui.components.CadenceButton
import dev.dazaike.cadence.ui.components.Logo
import dev.dazaike.cadence.ui.components.SkeletonBlock
import dev.dazaike.cadence.ui.theme.Cadence

/** Full-screen, centred, flat page on the theme background; the column is capped for tablets. */
@Composable
private fun OnboardingFrame(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Cadence.colors.pageBg).statusBarsPadding().navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

/** Logo tile, title, body copy and one full-width primary action. */
@Composable
private fun OnboardingMessage(title: String, body: String, action: String, onAction: () -> Unit) {
    val c = Cadence.colors
    OnboardingFrame {
        Logo(tile = 72.dp)
        Spacer(Modifier.height(28.dp))
        Text(title, style = Cadence.type.title2Medium, color = c.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(body, style = Cadence.type.bodyRegular, color = c.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(36.dp))
        CadenceButton(action, onClick = onAction, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun LoadingScreen() {
    OnboardingFrame {
        SkeletonBlock(Modifier.size(72.dp), radius = 12.dp)
        Spacer(Modifier.height(28.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(24.dp), radius = 8.dp)
        Spacer(Modifier.height(12.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(14.dp), radius = 6.dp)
        Spacer(Modifier.height(8.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.85f).height(14.dp), radius = 6.dp)
    }
}

@Composable
fun OnboardingScreen(onConnect: () -> Unit, sourceName: String = "Health Connect") {
    OnboardingMessage(
        title = "Welcome to Cadence",
        body = "A private, open-source window into your $sourceName data. Cadence only reads, and " +
            "nothing leaves your device unless you opt in to cloud sync.",
        action = "Connect $sourceName",
        onAction = onConnect,
    )
}

@Composable
fun UnavailableScreen(onInstall: () -> Unit) {
    OnboardingMessage(
        title = "Health Connect needed",
        body = "Cadence reads your data through Health Connect. Please install or update it to continue.",
        action = "Get Health Connect",
        onAction = onInstall,
    )
}

@Composable
fun SamsungHealthErrorScreen(message: String, onRetry: () -> Unit) {
    OnboardingMessage(
        title = "Samsung Health unavailable",
        body = message,
        action = "Try again",
        onAction = onRetry,
    )
}
