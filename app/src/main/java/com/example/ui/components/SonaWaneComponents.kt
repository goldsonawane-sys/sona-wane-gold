package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.MarketPoint
import com.example.ui.theme.AmberOrange
import com.example.ui.theme.CharcoalBlack
import com.example.ui.theme.DarkGold
import com.example.ui.theme.DeepBlack
import com.example.ui.theme.ElevatedObsidian
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldBorderSubtle
import com.example.ui.theme.LightGold
import com.example.ui.theme.MonoSmallStyle
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.SecondarySilver
import com.example.ui.theme.SilverAccent
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber

val GoldGradientBrush = Brush.linearGradient(
    colors = listOf(DarkGold, RoyalGold, LightGold, AmberOrange),
)

val SilverGradientBrush = Brush.linearGradient(
    colors = listOf(Color(0xFF6E7781), SilverAccent, Color(0xFFA0AAB8)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonaWaneTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = DeepBlack,
            titleContentColor = PureWhite,
        ),
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate back",
                        tint = RoyalGold,
                    )
                }
            }
        },
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = RoyalGold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        actions = { actions() },
    )
}

@Composable
fun LuxuryCard(
    modifier: Modifier = Modifier,
    highlightGold: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlightGold) CharcoalBlack else ObsidianCard,
        ),
        border = BorderStroke(
            width = if (highlightGold) 1.2.dp else 1.dp,
            color = if (highlightGold) RoyalGold.copy(alpha = 0.55f) else GoldBorderSubtle,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content,
        )
    }
}

@Composable
fun GoldPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = RoyalGold,
            contentColor = DeepBlack,
            disabledContainerColor = DarkGold.copy(alpha = 0.35f),
            disabledContentColor = SecondarySilver,
        ),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun OrangeActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AmberOrange,
            contentColor = DeepBlack,
            disabledContainerColor = AmberOrange.copy(alpha = 0.3f),
        ),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GoldOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, RoyalGold),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = LightGold),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color = RoyalGold,
    icon: ImageVector? = null,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun EnvironmentModeBadge(mode: String) {
    val isTest = mode != "LIVE"
    StatusPill(
        text = if (isTest) "TEST MODE • SANDBOX" else "LIVE MODE",
        color = if (isTest) AmberOrange else SuccessGreen,
        icon = if (isTest) Icons.Default.ErrorOutline else Icons.Default.Verified,
    )
}

@Composable
fun statusColorFor(status: String): Color {
    return when (status.uppercase()) {
        "SUCCESSFUL", "APPROVED", "COMPLETED", "VERIFIED", "ACTIVE", "RESOLVED", "CREDITED" -> SuccessGreen
        "FAILED", "REJECTED", "BLOCKED", "SUSPENDED", "REVERSED" -> ErrorRed
        "PENDING", "PROCESSING", "UNDER_REVIEW", "IN_PROGRESS", "WAITING_FOR_USER", "OPEN", "INFO_REQUIRED", "ELIGIBLE" -> WarningAmber
        else -> SecondarySilver
    }
}

@Composable
fun BullionPriceChart(
    points: List<MarketPoint>,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ElevatedObsidian),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Waiting for verified market price history...",
                style = MaterialTheme.typography.bodySmall,
                color = SecondarySilver,
            )
        }
        return
    }

    val minVal = points.minOf { it.price }
    val maxVal = points.maxOf { it.price }
    val range = (maxVal - minVal).takeIf { it > 0.001 } ?: 1.0

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ElevatedObsidian)
                .border(1.dp, GoldBorderSubtle, RoundedCornerShape(14.dp))
                .padding(14.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val stepX = w / (points.size - 1).coerceAtLeast(1)

                // Subtle horizontal guide lines
                listOf(0.25f, 0.5f, 0.75f).forEach { frac ->
                    drawLine(
                        color = Color.White.copy(alpha = 0.07f),
                        start = Offset(0f, h * frac),
                        end = Offset(w, h * frac),
                        strokeWidth = 1f,
                    )
                }

                val path = Path()
                val fillPath = Path()
                points.forEachIndexed { idx, pt ->
                    val x = idx * stepX
                    val y = h - (((pt.price - minVal) / range).toFloat() * h * 0.85f + h * 0.08f)
                    if (idx == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, h)
                        fillPath.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                    if (idx == points.lastIndex) {
                        fillPath.lineTo(x, h)
                        fillPath.close()
                    }
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.30f), Color.Transparent),
                    ),
                )
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 4.5f, cap = StrokeCap.Round),
                )

                val lastY = h - (((points.last().price - minVal) / range).toFloat() * h * 0.85f + h * 0.08f)
                drawCircle(
                    color = lineColor,
                    radius = 7f,
                    center = Offset((points.size - 1) * stepX, lastY),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(points.first().label, style = MonoSmallStyle, color = SecondarySilver)
            Text("Low: ₹${"%.0f".format(minVal)}", style = MonoSmallStyle, color = SecondarySilver)
            Text("High: ₹${"%.0f".format(maxVal)}", style = MonoSmallStyle, color = LightGold)
            Text(points.last().label, style = MonoSmallStyle, color = SecondarySilver)
        }
    }
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    valueColor: Color = PureWhite,
    boldValue: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = SecondarySilver)
        Text(
            text = value,
            style = if (boldValue) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontWeight = if (boldValue) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
