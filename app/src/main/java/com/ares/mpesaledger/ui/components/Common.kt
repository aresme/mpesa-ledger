package com.ares.mpesaledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.mpesaledger.ui.theme.AresBlack
import com.ares.mpesaledger.ui.theme.AresGreen
import com.ares.mpesaledger.ui.theme.AresGreenContainer
import com.ares.mpesaledger.ui.theme.AresMuted
import com.ares.mpesaledger.ui.theme.AresSurface
import com.ares.mpesaledger.ui.theme.AresSurfaceHighest
import com.ares.mpesaledger.ui.theme.LocalDemoMode
import com.ares.mpesaledger.ui.theme.Num

/** Top bar used by every screen; shows the DEMO pill whenever the demo database is active. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (LocalDemoMode.current) {
                    Spacer(Modifier.size(10.dp))
                    DemoBadge()
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            }
        },
        actions = actions,
        windowInsets = WindowInsets(0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = AresBlack),
    )
}

/**
 * A big amount that shrinks to fit its width instead of wrapping or clipping, so seven-figure
 * balances still fit on a 320dp-wide budget phone.
 */
@Composable
fun HeroAmount(text: String, color: Color, modifier: Modifier = Modifier, style: TextStyle = Num.hero) {
    BasicText(
        text = text,
        style = style.copy(color = color),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = style.fontSize),
        modifier = modifier,
    )
}

@Composable
fun DemoBadge() {
    Text(
        "DEMO",
        color = AresBlack,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier
            .background(AresGreen, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = AresSurface)
    val inner: @Composable ColumnScope.() -> Unit = { Column(Modifier.padding(16.dp), content = content) }
    if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = colors, content = inner)
    else Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = colors, content = inner)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Round initial avatar for a counterparty or category. */
@Composable
fun InitialAvatar(text: String, modifier: Modifier = Modifier, size: Int = 40, highlight: Boolean = false) {
    val initial = text.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•"
    Box(
        modifier
            .size(size.dp)
            .background(if (highlight) AresGreenContainer else AresSurfaceHighest, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = MaterialTheme.typography.titleSmall, color = if (highlight) AresGreen else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun MonthSwitcher(label: String, canGoNext: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month") }
        Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        IconButton(onClick = onNext, enabled = canGoNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month") }
    }
}

/** Empty / onboarding-style message with up to two actions. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    primaryAction: Pair<String, () -> Unit>? = null,
    secondaryAction: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(64.dp).background(AresGreenContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = AresGreen, modifier = Modifier.size(30.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = AresMuted, textAlign = TextAlign.Center)
        if (primaryAction != null) {
            Spacer(Modifier.height(4.dp))
            Button(onClick = primaryAction.second, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(primaryAction.first) }
        }
        if (secondaryAction != null) {
            OutlinedButton(
                onClick = secondaryAction.second,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            ) { Text(secondaryAction.first) }
        }
    }
}

/** A tappable settings-style row: icon, title, optional subtitle and trailing content. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = AresGreen,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AresMuted)
        }
        trailing()
    }
}

@Composable
fun CountBadge(count: Int) {
    if (count <= 0) return
    Text(
        count.toString(),
        color = AresBlack,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.background(AresGreen, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

