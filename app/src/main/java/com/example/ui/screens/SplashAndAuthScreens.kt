package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.onGoogleSignInClicked
import com.example.ui.components.GoldOutlinedButton
import com.example.ui.components.GoldPrimaryButton
import com.example.ui.components.LuxuryCard
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
import com.example.ui.viewmodel.SecurityOtpState
import com.example.util.BiometricAuthManager
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val scale = remember { Animatable(0.72f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = tween(750, easing = FastOutSlowInEasing))
        alpha.animateTo(1f, animationSpec = tween(600))
        delay(850)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepBlack, CharcoalBlack, DeepBlack),
                )
            )
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .scale(scale.value)
                    .clip(CircleShape)
                    .border(2.dp, RoyalGold, CircleShape),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.swg_launcher_emblem),
                    contentDescription = "SONA-WANE GOLD Official Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "SONA-WANE GOLD",
                style = MaterialTheme.typography.displayMedium,
                color = LightGold,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(alpha.value),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "TRUST  •  GOLD  •  GROWTH",
                style = MaterialTheme.typography.labelLarge,
                color = AmberOrange,
                letterSpacing = androidx.compose.ui.unit.TextUnit(2.2f, androidx.compose.ui.unit.TextUnitType.Sp),
                modifier = Modifier.alpha(alpha.value),
            )
            Spacer(modifier = Modifier.height(36.dp))
            CircularProgressIndicator(
                color = RoyalGold,
                trackColor = ObsidianCard,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Verifying bullion vault & session...",
                style = MaterialTheme.typography.bodySmall,
                color = SecondarySilver,
            )
        }
    }
}

@Composable
fun WelcomeAndAuthScreen(
    onAuthSuccess: () -> Unit,
    onOpenAdminLogin: () -> Unit = {},
    hasSavedBiometricVault: Boolean = false,
    onBiometricQuickLogin: ((onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val biometricCapability = remember(context) { BiometricAuthManager.checkCapability(context) }

    var showOnboarding by remember { mutableStateOf(true) }
    var slideIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val slides = listOf(
        Triple(
            "24K Gold & 999 Silver Vault",
            "Invest in 24K, 22K, 18K digital gold and 999 fine silver with live verified bullion feeds and segregated holdings.",
            Icons.Default.ShowChart,
        ),
        Triple(
            "Instant Wallet, UPI & Bank Payouts",
            "Fund your digital bullion wallet seamlessly via UPI, cards, or net banking and withdraw directly to your verified Indian bank account.",
            Icons.Default.AccountBalanceWallet,
        ),
        Triple(
            "Biometric, MPIN & OTP Security",
            "Protect every vault login, sale, and payout with Android Keystore Biometrics (Fingerprint / Face), salted MPIN hashing, and 6-digit OTP verification.",
            Icons.Default.VerifiedUser,
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
            .safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.swg_launcher_emblem),
                        contentDescription = "SONA-WANE GOLD Emblem",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .border(1.dp, RoyalGold, CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("SONA-WANE GOLD", style = MaterialTheme.typography.titleMedium, color = LightGold)
                        Text("TRUST • GOLD • GROWTH", style = MaterialTheme.typography.labelSmall, color = AmberOrange)
                    }
                }
                StatusPill(text = "SANDBOX READY", color = AmberOrange)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, GoldBorderSubtle, RoundedCornerShape(20.dp)),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_swg_hero_vault),
                    contentDescription = "Luxury Gold Vault Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(DeepBlack.copy(alpha = 0.25f), DeepBlack.copy(alpha = 0.88f))
                            )
                        )
                        .padding(18.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Column {
                        Text(
                            text = "India's Luxury Digital Bullion Vault",
                            style = MaterialTheme.typography.headlineLarge,
                            color = PureWhite,
                        )
                        Text(
                            text = "Real-time market pricing • Atomic ledger • Instant PDF receipts",
                            style = MaterialTheme.typography.bodySmall,
                            color = LightGold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (showOnboarding) {
                val (title, desc, icon) = slides[slideIndex]
                LuxuryCard(highlightGold = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = RoyalGold.copy(alpha = 0.16f),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(icon, contentDescription = null, tint = RoyalGold, modifier = Modifier.size(26.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(title, style = MaterialTheme.typography.titleLarge, color = PureWhite)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(desc, style = MaterialTheme.typography.bodyMedium, color = SecondarySilver)
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            slides.indices.forEach { idx ->
                                Box(
                                    modifier = Modifier
                                        .height(8.dp)
                                        .width(if (idx == slideIndex) 24.dp else 8.dp)
                                        .clip(CircleShape)
                                        .background(if (idx == slideIndex) RoyalGold else SecondarySilver.copy(alpha = 0.35f)),
                                )
                            }
                        }
                        Row {
                            TextButton(onClick = { showOnboarding = false }) {
                                Text("Skip", color = SecondarySilver)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            GoldOutlinedButton(
                                text = if (slideIndex < slides.lastIndex) "Next" else "Get Started",
                                onClick = {
                                    if (slideIndex < slides.lastIndex) {
                                        slideIndex += 1
                                    } else {
                                        showOnboarding = false
                                    }
                                },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            LuxuryCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Sign in to Your Vault",
                        style = MaterialTheme.typography.headlineMedium,
                        color = PureWhite,
                    )
                    StatusPill(
                        text = if (biometricCapability.isUsable) "BIOMETRIC READY" else "MPIN + OTP",
                        color = if (biometricCapability.isUsable) SuccessGreen else RoyalGold,
                        icon = Icons.Default.Fingerprint,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Authenticate securely with Google Sign-In. After signing in, configure your +91 mobile profile, 4-digit MPIN, and ${if (biometricCapability.isUsable) "Fingerprint / Face unlock" else "OTP recovery"}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondarySilver,
                )
                Spacer(modifier = Modifier.height(18.dp))

                errorMessage?.takeIf { it.isNotBlank() }?.let { errText ->
                    Surface(
                        color = ErrorRed.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = errText,
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                if (hasSavedBiometricVault && biometricCapability.isUsable && onBiometricQuickLogin != null) {
                    GoldPrimaryButton(
                        text = "Biometric Quick Login (Fingerprint / Face)",
                        enabled = !isLoading,
                        icon = Icons.Default.Fingerprint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("biometric_quick_login_button"),
                        onClick = {
                            errorMessage = null
                            isLoading = true
                            onBiometricQuickLogin(
                                {
                                    isLoading = false
                                    onAuthSuccess()
                                },
                                { msg ->
                                    isLoading = false
                                    errorMessage = msg
                                },
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                GoldPrimaryButton(
                    text = if (isLoading) "Signing in..." else "Sign in with Google",
                    enabled = !isLoading,
                    icon = Icons.Default.Security,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("google_sign_in_button"),
                    onClick = {
                        errorMessage = null
                        isLoading = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                errorMessage = msg
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isLoading = false
                            },
                        )
                    },
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            GoldOutlinedButton(
                text = "Administrator Console Login",
                icon = Icons.Default.Security,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_admin_login_button"),
                onClick = onOpenAdminLogin,
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Protected by zero-trust Firestore rules, Keystore biometrics & salted MPIN encryption.",
                style = MaterialTheme.typography.labelSmall,
                color = SecondarySilver,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun MpinLockScreen(
    title: String,
    subtitle: String,
    isSetupMode: Boolean,
    showBiometricButton: Boolean = false,
    biometricErrorMessage: String? = null,
    mpinRateLimit: Pair<Int, Long> = 0 to 0L,
    otpState: SecurityOtpState = SecurityOtpState(),
    onBiometricClick: () -> Unit = {},
    onPinSubmitted: (String, (String?) -> Unit) -> Unit,
    onRequestOtp: ((String) -> Unit)? = null,
    onVerifyOtp: ((String) -> Boolean)? = null,
    onCancelOtp: (() -> Unit)? = null,
    onOtpVerifiedResetMpin: (() -> Unit)? = null,
    onForgotMpin: (() -> Unit)? = null,
) {
    var pin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var autoBiometricLaunched by remember { mutableStateOf(false) }

    var showOtpDialog by remember { mutableStateOf(false) }
    var otpInput by remember { mutableStateOf("") }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val (failedAttempts, lockoutUntilMs) = mpinRateLimit
    val lockoutRemainingSec = ((lockoutUntilMs - nowMs) / 1000L).coerceAtLeast(0L)
    val isLockedOut = !isSetupMode && lockoutRemainingSec > 0L

    LaunchedEffect(lockoutUntilMs, otpState.isActive, showOtpDialog) {
        while (lockoutUntilMs > System.currentTimeMillis() || otpState.isActive || showOtpDialog) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
        nowMs = System.currentTimeMillis()
    }

    LaunchedEffect(showBiometricButton, isSetupMode, isLockedOut) {
        if (!isSetupMode && showBiometricButton && !isLockedOut && !autoBiometricLaunched) {
            autoBiometricLaunched = true
            delay(250L)
            onBiometricClick()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = RoyalGold.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, RoyalGold),
                modifier = Modifier.size(68.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (showBiometricButton && !isSetupMode) Icons.Default.Fingerprint else Icons.Default.Lock,
                        contentDescription = "Vault Security",
                        tint = RoyalGold,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = if (isSetupMode && firstPin != null) "Confirm Your 4-Digit MPIN" else title,
                style = MaterialTheme.typography.headlineLarge,
                color = PureWhite,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isSetupMode && firstPin != null) "Re-enter the same 4-digit PIN to activate vault security." else subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = SecondarySilver,
                textAlign = TextAlign.Center,
            )

            if (isLockedOut) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = ErrorRed.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, ErrorRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Too many failed MPIN attempts. Locked for ${lockoutRemainingSec}s. You can unlock via Biometrics or reset MPIN via OTP below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            } else if (!isSetupMode && failedAttempts > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                StatusPill(
                    text = "Failed Attempts: $failedAttempts / 5",
                    color = AmberOrange,
                )
            }

            if (showBiometricButton && !isSetupMode) {
                Spacer(modifier = Modifier.height(10.dp))
                StatusPill(
                    text = "BIOMETRIC FAST-LOGIN READY",
                    color = SuccessGreen,
                    icon = Icons.Default.Fingerprint,
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { idx ->
                    val filled = idx < pin.length
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (filled) RoyalGold else CharcoalBlack)
                            .border(1.5.dp, if (filled) LightGold else SecondarySilver.copy(alpha = 0.5f), CircleShape),
                    )
                }
            }

            val visibleError = errorMsg?.takeIf { it.isNotBlank() } ?: biometricErrorMessage?.takeIf { it.isNotBlank() }
            if (!visibleError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = visibleError,
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("mpin_lock_error_text"),
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("BIO", "0", "DEL"),
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        row.forEach { key ->
                            when (key) {
                                "BIO" -> {
                                    if (showBiometricButton && !isSetupMode) {
                                        PinPadKey(onClick = onBiometricClick, testTag = "mpin_biometric_pad_btn") {
                                            Icon(Icons.Default.Fingerprint, contentDescription = "Biometric Unlock", tint = AmberOrange)
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(72.dp))
                                    }
                                }
                                "DEL" -> {
                                    PinPadKey(onClick = {
                                        if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                        errorMsg = null
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete digit", tint = SecondarySilver)
                                    }
                                }
                                else -> {
                                    PinPadKey(
                                        enabled = !isLockedOut,
                                        onClick = {
                                            if (pin.length < 4 && !isLockedOut) {
                                                pin += key
                                                errorMsg = null
                                                if (pin.length == 4) {
                                                    if (isSetupMode) {
                                                        if (firstPin == null) {
                                                            firstPin = pin
                                                            pin = ""
                                                        } else if (firstPin == pin) {
                                                            onPinSubmitted(pin) { err ->
                                                                errorMsg = err
                                                                pin = ""
                                                                firstPin = null
                                                            }
                                                        } else {
                                                            errorMsg = "MPINs did not match. Try again."
                                                            pin = ""
                                                            firstPin = null
                                                        }
                                                    } else {
                                                        onPinSubmitted(pin) { err ->
                                                            errorMsg = err
                                                            pin = ""
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    ) {
                                        Text(
                                            text = key,
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = if (isLockedOut) SecondarySilver.copy(alpha = 0.4f) else PureWhite,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showBiometricButton && !isSetupMode) {
                Spacer(modifier = Modifier.height(18.dp))
                GoldOutlinedButton(
                    text = "Unlock with Fingerprint / Face",
                    onClick = onBiometricClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mpin_biometric_unlock_button"),
                )
            }

            if (!isSetupMode && (onRequestOtp != null || onForgotMpin != null)) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = {
                        if (onRequestOtp != null && onVerifyOtp != null) {
                            otpInput = ""
                            onRequestOtp("MPIN Reset")
                            showOtpDialog = true
                        } else {
                            onForgotMpin?.invoke()
                        }
                    },
                    modifier = Modifier.testTag("forgot_mpin_otp_button"),
                ) {
                    Icon(Icons.Default.Sms, contentDescription = null, tint = AmberOrange, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Forgot MPIN? Verify via 6-Digit OTP", color = AmberOrange)
                }
            }
        }
    }

    if (showOtpDialog && onVerifyOtp != null) {
        val expiresLeftSec = ((otpState.expiresAtMs - nowMs) / 1000L).coerceAtLeast(0L)
        val resendWaitSec = ((otpState.resendAvailableAtMs - nowMs) / 1000L).coerceAtLeast(0L)

        AlertDialog(
            onDismissRequest = {
                showOtpDialog = false
                onCancelOtp?.invoke()
            },
            title = { Text("6-Digit OTP Verification") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter the 6-digit security OTP generated for your SONA-WANE GOLD vault (${otpState.purpose.ifBlank { "MPIN Reset" }}).",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondarySilver,
                    )
                    if (!otpState.sandboxOtpHint.isNullOrBlank()) {
                        Surface(
                            color = RoyalGold.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, RoyalGold.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "TEST MODE OTP: ${otpState.sandboxOtpHint}  (Also sent to Notification Inbox)",
                                style = MonoSmallStyle,
                                color = LightGold,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = otpInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) otpInput = it },
                        label = { Text("Enter 6-Digit OTP") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("otp_code_input"),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (otpState.isActive) "Expires: ${expiresLeftSec}s • ${otpState.attemptsRemaining} tries" else "OTP inactive",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondarySilver,
                        )
                        TextButton(
                            enabled = resendWaitSec == 0L,
                            onClick = { onRequestOtp?.invoke("MPIN Reset") },
                        ) {
                            Text(
                                text = if (resendWaitSec > 0L) "Resend in ${resendWaitSec}s" else "Resend OTP",
                                color = if (resendWaitSec > 0L) SecondarySilver else AmberOrange,
                            )
                        }
                    }
                    otpState.errorMessage?.takeIf { it.isNotBlank() }?.let { otpErr ->
                        Text(otpErr, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = otpInput.length == 6,
                    onClick = {
                        if (onVerifyOtp(otpInput)) {
                            showOtpDialog = false
                            otpInput = ""
                            (onOtpVerifiedResetMpin ?: onForgotMpin)?.invoke()
                        }
                    },
                    modifier = Modifier.testTag("verify_otp_confirm_btn"),
                ) {
                    Text("Verify & Reset MPIN", color = RoyalGold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showOtpDialog = false
                    onCancelOtp?.invoke()
                }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun PinPadKey(
    onClick: () -> Unit,
    enabled: Boolean = true,
    testTag: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = ObsidianCard,
        border = BorderStroke(1.dp, GoldBorderSubtle),
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
fun BiometricAppLockScreen(
    userDisplayName: String,
    userEmail: String,
    errorMessage: String?,
    onUnlockWithBiometric: () -> Unit,
    onUnlockWithDeviceCredential: (() -> Unit)? = null,
    onSetupMpinBackup: () -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val capability = remember(context) { BiometricAuthManager.checkCapability(context) }
    val hasDeviceCred = remember(context) { BiometricAuthManager.hasDeviceCredentialFallback(context) }

    LaunchedEffect(Unit) {
        delay(200L)
        if (capability.isUsable) {
            onUnlockWithBiometric()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = RoyalGold.copy(alpha = 0.16f),
                border = BorderStroke(1.5.dp, RoyalGold),
                modifier = Modifier.size(76.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Biometric App Lock",
                        tint = RoyalGold,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Unlock SONA-WANE GOLD",
                style = MaterialTheme.typography.headlineLarge,
                color = PureWhite,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (userDisplayName.isNotBlank() || userEmail.isNotBlank()) {
                Text(
                    text = userDisplayName.ifBlank { userEmail },
                    style = MaterialTheme.typography.bodyMedium,
                    color = LightGold,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            StatusPill(
                text = capability.label,
                color = if (capability.isUsable) SuccessGreen else AmberOrange,
            )

            Spacer(modifier = Modifier.height(18.dp))

            LuxuryCard(highlightGold = true) {
                Text(
                    text = "Biometric App Access Enabled",
                    style = MaterialTheme.typography.titleMedium,
                    color = LightGold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Authenticate with Fingerprint or Face Unlock via Android BiometricPrompt to access your gold & silver vault.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondarySilver,
                )

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = ErrorRed.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, ErrorRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed,
                            modifier = Modifier
                                .padding(10.dp)
                                .testTag("biometric_lock_error_text"),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                GoldPrimaryButton(
                    text = "Unlock with Fingerprint / Face",
                    onClick = onUnlockWithBiometric,
                    enabled = capability.isUsable,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("biometric_app_unlock_btn"),
                )

                if (hasDeviceCred && onUnlockWithDeviceCredential != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    GoldOutlinedButton(
                        text = "Use Device PIN / Pattern",
                        onClick = onUnlockWithDeviceCredential,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("biometric_device_cred_btn"),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onSetupMpinBackup) {
                        Text("Set Up 4-Digit MPIN", color = LightGold)
                    }
                    TextButton(onClick = onSignOut) {
                        Text("Sign Out", color = SecondarySilver)
                    }
                }
            }
        }
    }
}

