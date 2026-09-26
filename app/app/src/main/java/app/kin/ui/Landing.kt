package app.kin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.kin.Config

private val Gutter = 20.dp

/** What someone sees before connecting a wallet: one calm screen. Each topic opens on its own page. */
@Composable
fun LandingScreen(busy: Boolean, onConnect: () -> Unit, onSimulate: () -> Unit, onLearn: (LearnPage) -> Unit) {
    Column(Modifier.fillMaxSize().background(KinColors.Paper)) {
        Box(Modifier.statusBarsPadding())
        NoticeStrip("Live on ${Config.CLUSTER_LABEL}. Balances are test tokens.")
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            KinMark(28.dp)
            Spacer(Modifier.width(8.dp))
            Text("Kin", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            GraphiteButton("Connect", onConnect, enabled = !busy)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(KinColors.Steel))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Hero(busy, onConnect, onSimulate)
            Column(Modifier.padding(horizontal = Gutter).padding(top = 40.dp)) {
                KinLabel("Learn more")
                Spacer(Modifier.height(12.dp))
                LearnList(onLearn)
            }
            Footer()
        }
    }
}

@Composable
private fun Hero(busy: Boolean, onConnect: () -> Unit, onSimulate: () -> Unit) {
    Column(Modifier.padding(horizontal = Gutter).padding(top = 32.dp, bottom = 12.dp)) {
        Text("Save together.\nGet paid in turn.", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(16.dp))
        Text(
            "Kin is a savings circle on Solana. Everyone pays the same amount each round and one member takes the whole pot, until every member has had a turn.",
            style = MaterialTheme.typography.bodyLarge,
            color = KinColors.Slate,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Capability("Bonded")
            Capability("Autopay")
            Capability("Verifiable")
        }
        Spacer(Modifier.height(24.dp))
        AquaButton("Connect wallet", onConnect, enabled = !busy)
        Spacer(Modifier.height(10.dp))
        OutlineButton("See a circle run, no wallet needed", onSimulate)
        Spacer(Modifier.height(6.dp))
        LedgerArt(Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun Capability(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(KinColors.Ink))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Footer() {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp).navigationBarsPadding().padding(Gutter)) {
        Text(
            "You approve every payment in your wallet. Kin never sees your keys. Network: ${Config.CLUSTER_LABEL}.",
            style = MaterialTheme.typography.bodyMedium,
            color = KinColors.Slate,
        )
    }
}
