package app.petdance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.petdance.ui.theme.Accent
import app.petdance.ui.theme.Bad
import app.petdance.ui.theme.Chip
import app.petdance.ui.theme.Good
import app.petdance.ui.theme.Line
import app.petdance.ui.theme.Muted
import app.petdance.ui.theme.Warn

@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.padding(vertical = 2.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(text, modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(text, modifier = Modifier.padding(vertical = 6.dp))
    }
}

@Composable
fun QuietButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, color = Accent)
    }
}

@Composable
fun MutedText(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Muted, style = MaterialTheme.typography.bodySmall, modifier = modifier)
}

@Composable
fun ErrorText(text: String?) {
    if (!text.isNullOrBlank()) Text(text, color = Bad, style = MaterialTheme.typography.bodySmall)
}

@Composable
fun Banner(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Chip, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) QuietButton(action, onClick = onAction)
    }
}

@Composable
fun StatusPill(text: String, tone: PillTone = PillTone.Neutral) {
    val color = when (tone) {
        PillTone.Good -> Good
        PillTone.Bad -> Bad
        PillTone.Warn -> Warn
        PillTone.Neutral -> Muted
    }
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .background(Chip, RoundedCornerShape(99.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        textAlign = TextAlign.Center,
    )
}

enum class PillTone { Neutral, Good, Bad, Warn }

@Composable
fun SectionTitle(text: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Accent)
    }
}

val Hairline = Line
