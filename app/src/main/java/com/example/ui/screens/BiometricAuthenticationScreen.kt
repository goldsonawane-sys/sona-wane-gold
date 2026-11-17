package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.GoldOutlinedButton
import com.example.ui.components.GoldPrimaryButton
import com.example.ui.components.LuxuryCard
import com.example.ui.components.SonaWaneTopBar
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberOrange
import com.example.ui.theme.CharcoalBlack
import com.example.ui.theme.DeepBlack
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldBorderSubtle
import com.example.ui.theme.LightGold
import com.example.ui.theme.MonoSmallStyle
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.SecondarySilver
import com.example.ui.theme.SuccessGreen
import com.example.util.BiometricAuthManager
import com.example.util.BiometricCapability
import com.example.util.BiometricLoginManager
import com.example.util.ProtectedFeature
import kotlinx.coroutines.delay

/**
 * High-security action scopes guarded by AndroidX BiometricPrompt.
 */
enum class SensitiveActionScope(
    val title: String,
    val chipLabel: String,
    val subtitle: String,
    val promptTitle: String,
    val promptSubtitle: String,
    val feature: ProtectedFeature,
    val icon: ImageVector,
) {
    WALLET_VAULT(
        title = "Wallet Vault Access",
        chipLabel = "Wallet Vault",
        subtitle = "Authenticate to unlock INR balances, digital gold & silver holdings, and private ledger.",
        promptTitle = "Unlock Digital Wallet Vault",
        promptSubtitle = "Verify your Fingerprint or Face via BiometricPrompt to access wallet balances",
        feature = ProtectedFeature.WALLET_ACCESS,
        icon = Icons.Default.AccountBalanceWallet,
    ),
    INVESTMENT_ORDER(
        title = "Investment Order Shield",
        chipLabel = "Investments",
        subtitle = "Authorize high-value digital gold & silver buy, sell, and SIP investment executions.",
        promptTitle = "Authorize Bullion Investment",
        promptSubtitle = "Confirm Fingerprint or Face via BiometricPrompt to authorize investment order",
        feature = ProtectedFeature.TRADING_EXECUTION,
        icon = Icons.AutoMirrored.Filled.TrendingUp,
    ),
    PAYOUT_TRANSFER(
        title = "Withdrawals & Transfers",
        chipLabel = "Payouts & P2P",
        subtitle = "Protect bank payout requests, linked bank changes, and peer-to-peer bullion transfers.",
        promptTitle = "Authorize Payout & Transfer",
        promptSubtitle = "Verify Fingerprint or Face via BiometricPrompt to release funds or bullion",
        feature = ProtectedFeature.TRADING_EXECUTION,
        icon = Icons.Default.Security,
    );

    companion object {
        fun fromArg(raw: String?): SensitiveActionScope {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: WALLET_VAULT
        }
    }
}

/**
 * Dedicated Jetpack Compose Biometric Authentication Screen powered by AndroidX BiometricPrompt
 * to secure sensitive investment actions (gold/silver orders) and wallet actions (vault access,
 * withdrawals, transfers). Supports Strong/Weak biometrics, Device Credential fallback, and 4-digit
 * MPIN backup with smooth state feedback.
 */
@Composable
fun BiometricAuthenticationScreen(
    scope: SensitiveActionScope = SensitiveActionScope.WALLET_VAULT,
    actionSummary: String? = null,
    userDisplayName: String = "",
    biometricCapability: BiometricCapability? = null,
    hasDeviceCredentialFallback: Boolean? = null,
    hasMpinFallback: Boolean = false,
    walletLockEnabled: Boolean = true,
    tradeConfirmEnabled: Boolean = true,
    isWalletAuthorized: Boolean = false,
    isTradeAuthorized: Boolean = false,
    isAuthenticating: Boolean = false,
    errorMessage: String? = null,
    statusMessage: String? = null,
    showTopBar: Boolean = true,
    allowScopeSwitching: Boolean = true,
    autoPromptOnLaunch: Boolean = false,
    rootTestTag: String = "biometric_auth_screen",
    promptButtonTestTag: String = "biometric_auth_prompt_btn",
    mpinInputTestTag: String = "biometric_mpin_fallback_input",
    mpinButtonTestTag: String = "biometric_mpin_fallback_btn",
    onSelectScope: ((SensitiveActionScope) -> Unit)? = null,
    onAuthenticateBiometric: () -> Unit,
    onAuthenticateDeviceCredential: (() -> Unit)? = null,
    onVerifyMpin: ((String) -> Boolean)? = null,
    onMpinSuccess: (() -> Unit)? = null,
    onLockSessions: (() -> Unit)? = null,
    onToggleWalletLock: ((Boolean) -> Unit)? = null,
    onToggleTradeConfirm: ((Boolean) -> Unit)? = null,
    onSetupMpin: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val resolvedCapability = biometricCapability ?: remember(context) {
        BiometricLoginManager.checkBiometricCapability(context)
    }
    val resolvedDeviceCred = hasDeviceCredentialFallback ?: remember(context) {
        BiometricAuthManager.hasDeviceCredentialFallback(context)
    }

    var localPin by remember { mutableStateOf("") }
    var localPinError by remember { mutableStateOf<String?>(null) }

    val scopeAuthorized = when (scope) {
        SensitiveActionScope.WALLET_VAULT -> isWalletAuthorized
        SensitiveActionScope.INVESTMENT_ORDER,
        SensitiveActionScope.PAYOUT_TRANSFER -> isTradeAuthorized
    }

    LaunchedEffect(autoPromptOnLaunch, scope) {
        if (autoPromptOnLaunch && !scopeAuthorized && resolvedCapability.isUsable) {
            delay(180L)
            onAuthenticateBiometric()
        }
    }

    val ringColor by animateColorAsState(
        targetValue = when {
            !errorMessage.isNullOrBlank() || !localPinError.isNullOrBlank() -> ErrorRed
            scopeAuthorized -> SuccessGreen
            resolvedCapability.isUsable -> RoyalGold
            else -> AmberOrange
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "biometricRingColor",
    )
    val badgeScale by animateFloatAsState(
        targetValue = if (scopeAuthorized) 1.06f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "biometricBadgeScale",
    )

    val combinedError = errorMessage?.takeIf { it.isNotBlank() } ?: localPinError

    val contentBody: @Composable (Modifier) -> Unit = { containerModifier ->
        Box(
            modifier = containerModifier
                .fillMaxSize()
                .background(DeepBlack)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ringColor.copy(alpha = 0.14f),
                                Color.Transparent,
                            ),
                            center = androidx.compose.ui.geometry.Offset(size.width / 2f, 220f),
                            radius = size.minDimension * 0.72f,
                        ),
                    )
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .testTag(rootTestTag),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (allowScopeSwitching && onSelectScope != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .testTag("biometric_scope_selector_row"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SensitiveActionScope.entries.forEach { entry ->
                            val selected = entry == scope
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    localPinError = null
                                    onSelectScope(entry)
                                },
                                label = { Text(entry.chipLabel) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = entry.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoyalGold,
                                    selectedLabelColor = DeepBlack,
                                    selectedLeadingIconColor = DeepBlack,
                                    containerColor = ObsidianCard,
                                    labelColor = PureWhite,
                                    iconColor = RoyalGold,
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("biometric_scope_chip_${entry.name.lowercase()}"),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Concentric Vault Emblem
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(116.dp)
                        .scale(badgeScale),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ringColor.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, ringColor.copy(alpha = 0.35f)),
                        modifier = Modifier.size(116.dp),
                    ) {}
                    Surface(
                        shape = CircleShape,
                        color = ringColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.8.dp, ringColor),
                        modifier = Modifier.size(84.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (scopeAuthorized) Icons.Default.VerifiedUser else Icons.Default.Fingerprint,
                                contentDescription = stringResource(R.string.biometric_auth_emblem_cd),
                                tint = ringColor,
                                modifier = Modifier.size(44.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = scope.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = PureWhite,
                    textAlign = TextAlign.Center,
                )
                if (userDisplayName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = userDisplayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = LightGold,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = scope.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondarySilver,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Status & Cryptographic Capability Pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusPill(
                        text = if (scopeAuthorized) "VERIFIED SESSION" else "AUTH REQUIRED",
                        color = if (scopeAuthorized) SuccessGreen else RoyalGold,
                        icon = if (scopeAuthorized) Icons.Default.CheckCircle else Icons.Default.Lock,
                    )
                    StatusPill(
                        text = when (resolvedCapability) {
                            BiometricCapability.STRONG_READY -> "CLASS 3 BIOMETRIC"
                            BiometricCapability.WEAK_READY -> "BIOMETRIC READY"
                            BiometricCapability.NOT_ENROLLED -> "NOT ENROLLED"
                            else -> "CREDENTIAL FALLBACK"
                        },
                        color = if (resolvedCapability.isUsable) SuccessGreen else AmberOrange,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Primary Challenge Card
                LuxuryCard(highlightGold = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = RoyalGold,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.biometric_auth_prompt_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = LightGold,
                                )
                                Text(
                                    text = if (resolvedCapability == BiometricCapability.STRONG_READY) {
                                        "Hardware-backed AndroidKeyStore AES-256-GCM"
                                    } else {
                                        resolvedCapability.label
                                    },
                                    style = MonoSmallStyle,
                                    color = SecondarySilver,
                                )
                            }
                        }
                    }

                    if (!actionSummary.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = DeepBlack,
                            border = BorderStroke(1.dp, RoyalGold.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("biometric_action_summary_box"),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "PROTECTED ACTION",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RoyalGold,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = actionSummary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PureWhite,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = !statusMessage.isNullOrBlank()) {
                        if (!statusMessage.isNullOrBlank()) {
                            Column {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    color = SuccessGreen.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, SuccessGreen),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("biometric_auth_status_banner"),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = SuccessGreen,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = statusMessage,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PureWhite,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = !combinedError.isNullOrBlank()) {
                        if (!combinedError.isNullOrBlank()) {
                            Column {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    color = ErrorRed.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, ErrorRed),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("biometric_auth_error_banner"),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = ErrorRed,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = combinedError,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ErrorRed,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    GoldPrimaryButton(
                        text = when {
                            isAuthenticating -> "Awaiting BiometricPrompt..."
                            scopeAuthorized -> "Re-verify with Fingerprint / Face"
                            else -> "Verify with Fingerprint / Face"
                        },
                        icon = Icons.Default.Fingerprint,
                        enabled = resolvedCapability.isUsable && !isAuthenticating,
                        onClick = {
                            localPinError = null
                            onAuthenticateBiometric()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(promptButtonTestTag),
                    )

                    if (resolvedDeviceCred && onAuthenticateDeviceCredential != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        GoldOutlinedButton(
                            text = "Use Device PIN / Pattern",
                            icon = Icons.Default.Key,
                            enabled = !isAuthenticating,
                            onClick = {
                                localPinError = null
                                onAuthenticateDeviceCredential()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("biometric_device_cred_auth_btn"),
                        )
                    }

                    if (hasMpinFallback && onVerifyMpin != null && onMpinSuccess != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = GoldBorderSubtle)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Fallback 4-Digit Vault MPIN",
                            style = MaterialTheme.typography.labelLarge,
                            color = LightGold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = localPin,
                            onValueChange = { value ->
                                if (value.length <= 4 && value.all { it.isDigit() }) {
                                    localPin = value
                                    localPinError = null
                                }
                            },
                            label = { Text("Enter 4-digit MPIN") },
                            leadingIcon = {
                                Icon(Icons.Default.Password, contentDescription = null, tint = RoyalGold)
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(mpinInputTestTag),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        GoldOutlinedButton(
                            text = "Authorize with MPIN",
                            enabled = localPin.length == 4 && !isAuthenticating,
                            onClick = {
                                if (onVerifyMpin(localPin)) {
                                    localPin = ""
                                    localPinError = null
                                    onMpinSuccess()
                                } else {
                                    localPinError = "Incorrect 4-digit MPIN. Please try again."
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(mpinButtonTestTag),
                        )
                    } else if (!hasMpinFallback && onSetupMpin != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = onSetupMpin,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Text("Configure 4-Digit MPIN Backup", color = LightGold)
                        }
                    }

                    if (!resolvedCapability.isUsable) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Enroll Fingerprint or Face Unlock in Android Device Settings, or authorize via Device PIN / MPIN.",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberOrange,
                        )
                    }
                }

                // Sensitive Protection Policies (optional hub mode)
                if (onToggleWalletLock != null || onToggleTradeConfirm != null || onLockSessions != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    LuxuryCard {
                        Text(
                            text = "Sensitive Action Policies",
                            style = MaterialTheme.typography.titleMedium,
                            color = LightGold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Control which wallet and investment actions invoke Android BiometricPrompt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondarySilver,
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (onToggleWalletLock != null) {
                            BiometricPolicyRow(
                                icon = Icons.Default.AccountBalanceWallet,
                                title = "Wallet Vault Biometric Lock",
                                subtitle = "Protect INR balances, holdings & ledger",
                                checked = walletLockEnabled,
                                onCheckedChange = onToggleWalletLock,
                                switchTag = "biometric_screen_wallet_lock_switch",
                            )
                        }

                        if (onToggleWalletLock != null && onToggleTradeConfirm != null) {
                            HorizontalDivider(
                                color = GoldBorderSubtle,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }

                        if (onToggleTradeConfirm != null) {
                            BiometricPolicyRow(
                                icon = Icons.AutoMirrored.Filled.TrendingUp,
                                title = "Investment Order Biometric Confirm",
                                subtitle = "Require BiometricPrompt on Gold & Silver trades",
                                checked = tradeConfirmEnabled,
                                onCheckedChange = onToggleTradeConfirm,
                                switchTag = "biometric_screen_trade_confirm_switch",
                            )
                        }

                        if (onLockSessions != null && (isWalletAuthorized || isTradeAuthorized)) {
                            Spacer(modifier = Modifier.height(12.dp))
                            GoldOutlinedButton(
                                text = "Lock Authorized Sessions Now",
                                icon = Icons.Default.Lock,
                                onClick = onLockSessions,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("biometric_lock_sessions_btn"),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showTopBar) {
        Scaffold(
            containerColor = DeepBlack,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                SonaWaneTopBar(
                    title = stringResource(R.string.biometric_auth_screen_title),
                    subtitle = "Android BiometricPrompt Security",
                    onBack = onBack,
                )
            },
        ) { padding ->
            contentBody(Modifier.padding(padding))
        }
    } else {
        contentBody(Modifier)
    }
}

@Composable
private fun BiometricPolicyRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.Transparent, RoundedCornerShape(10.dp))
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = RoyalGold, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, color = PureWhite)
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DeepBlack,
                checkedTrackColor = RoyalGold,
                uncheckedThumbColor = SecondarySilver,
                uncheckedTrackColor = CharcoalBlack,
            ),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag(switchTag),
        )
    }
}
