package com.example

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.local.GoldDropInAppAlert
import com.example.data.local.SecurityPreferences
import com.example.data.model.TransactionRecord
import com.example.data.remote.MarketRateService
import com.example.data.repository.AdminRepository
import com.example.data.repository.SonaWaneRepository
import com.example.service.GoldPriceMonitorService
import com.example.ui.components.GoldOutlinedButton
import com.example.ui.components.LuxuryCard
import com.example.ui.components.OrangeActionButton
import com.example.ui.components.StatusPill
import com.example.ui.screens.AdminControlPanelScreen
import com.example.ui.screens.AdminKycReviewScreen
import com.example.ui.screens.AdminLoginScreen
import com.example.ui.screens.BankAccountsScreen
import com.example.ui.screens.BullionTradeScreen
import com.example.ui.screens.GoldPurchaseCalculatorScreen
import com.example.ui.screens.HelpSupportScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.KycVerificationScreen
import com.example.ui.screens.MarketAlertsScreen
import com.example.ui.screens.MarketScreen
import com.example.ui.screens.MpinLockScreen
import com.example.ui.screens.MyQrScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.QrScannerScreen
import com.example.ui.screens.ReferralsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.TransactionDetailDialog
import com.example.ui.screens.TransactionHistoryScreen
import com.example.ui.screens.TransferScreen
import com.example.ui.screens.WalletScreen
import com.example.ui.screens.WelcomeAndAuthScreen
import com.example.util.BiometricAuthManager
import com.example.util.Formatters
import com.example.ui.theme.AmberOrange
import com.example.ui.theme.CharcoalBlack
import com.example.ui.theme.DeepBlack
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.LightGold
import com.example.ui.theme.MonoSmallStyle
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.SecondarySilver
import com.example.ui.theme.SonaWaneGoldTheme
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SonaWaneGoldTheme {
                val context = LocalContext.current
                val appContext = context.applicationContext
                val scope = rememberCoroutineScope()
                val credentialManager = remember { CredentialManager.create(context) }
                val securityPrefs = remember { SecurityPreferences(appContext) }

                val currentUser by remember { Firebase.auth.authStateFlow() }
                    .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
                val adminPortalMode by remember { securityPrefs.adminPortalModeFlow }
                    .collectAsStateWithLifecycle(initialValue = false)

                var showSplash by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    if (!adminPortalMode) {
                        attemptAutoSignIn(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {},
                            onUnauthenticated = {},
                            scope = scope,
                        )
                    }
                }

                if (showSplash) {
                    SplashScreen(onFinished = { showSplash = false })
                } else if (adminPortalMode) {
                    AdminPortalShell(
                        uid = currentUser?.uid,
                        email = currentUser?.email,
                        onLaunchBiometric = { title, subtitle, onSuccess, onError ->
                            BiometricAuthManager.promptBiometric(
                                activity = this@MainActivity,
                                title = title,
                                subtitle = subtitle,
                                onSuccess = onSuccess,
                                onError = onError,
                            )
                        },
                        onSignOutAdmin = {
                            signOutUser(credentialManager = credentialManager, scope = scope)
                        },
                        onExitToCustomerMode = {
                            scope.launch { securityPrefs.setAdminPortalMode(false) }
                        },
                    )
                } else if (currentUser == null) {
                    WelcomeAndAuthScreen(
                        onAuthSuccess = {},
                        onOpenAdminLogin = {
                            scope.launch { securityPrefs.setAdminPortalMode(true) }
                        },
                    )
                } else {
                    val user = currentUser!!
                    key(user.uid) {
                        AuthenticatedAppShell(
                            uid = user.uid,
                            email = user.email,
                            displayName = user.displayName,
                            onLaunchBiometric = { title, subtitle, onSuccess, onError ->
                                BiometricAuthManager.promptBiometric(
                                    activity = this@MainActivity,
                                    title = title,
                                    subtitle = subtitle,
                                    onSuccess = onSuccess,
                                    onError = onError,
                                )
                            },
                            onOpenIsolatedAdminPortal = {
                                scope.launch { securityPrefs.setAdminPortalMode(true) }
                            },
                            onSignOut = {
                                scope.launch { securityPrefs.setAdminPortalMode(false) }
                                signOutUser(credentialManager = credentialManager, scope = scope)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminPortalShell(
    uid: String?,
    email: String?,
    onLaunchBiometric: (title: String, subtitle: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onSignOutAdmin: () -> Unit,
    onExitToCustomerMode: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext

    val adminVm: AdminViewModel = viewModel(
        key = "admin_portal_${uid ?: "unauth"}",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AdminViewModel(
                    adminRepo = AdminRepository(appContext),
                    userRepo = SonaWaneRepository(appContext),
                    prefs = SecurityPreferences(appContext),
                ) as T
            }
        },
    )

    val adminUser by adminVm.adminUser.collectAsStateWithLifecycle()
    val eligibilityChecked by adminVm.eligibilityChecked.collectAsStateWithLifecycle()
    val adminVerified by adminVm.adminVerified.collectAsStateWithLifecycle()
    val adminStatusMessage by adminVm.statusMessage.collectAsStateWithLifecycle()
    val authErrorMessage by adminVm.authErrorMessage.collectAsStateWithLifecycle()
    val adminBusy by adminVm.isBusy.collectAsStateWithLifecycle()
    val adminRateLimit by adminVm.adminRateLimit.collectAsStateWithLifecycle()
    val allUsers by adminVm.allUsers.collectAsStateWithLifecycle()
    val allTransactions by adminVm.allTransactions.collectAsStateWithLifecycle()
    val allKyc by adminVm.allKyc.collectAsStateWithLifecycle()
    val allTickets by adminVm.allTickets.collectAsStateWithLifecycle()
    val allTransfers by adminVm.allTransfers.collectAsStateWithLifecycle()
    val admins by adminVm.admins.collectAsStateWithLifecycle()
    val auditLogs by adminVm.auditLogs.collectAsStateWithLifecycle()
    val appConfig by adminVm.appConfig.collectAsStateWithLifecycle()

    var showKycReviewScreen by remember { mutableStateOf(false) }

    LaunchedEffect(uid, adminVerified) {
        adminVm.checkEligibility()
        if (!adminVerified) showKycReviewScreen = false
    }

    BackHandler {
        when {
            showKycReviewScreen -> showKycReviewScreen = false
            adminVerified -> adminVm.lockAdminPortal()
            else -> onExitToCustomerMode()
        }
    }

    if (uid == null || !adminVerified) {
        AdminLoginScreen(
            currentEmail = email,
            eligibilityChecked = eligibilityChecked,
            adminUser = adminUser,
            isBusy = adminBusy,
            authErrorMessage = authErrorMessage,
            adminRateLimit = adminRateLimit,
            onGoogleVerified = { adminVm.checkEligibility() },
            onEnrollCredentials = { opId, pass, confirm, bio ->
                adminVm.enrollAdminCredentials(opId, pass, confirm, bio)
            },
            onAuthenticateCredentials = { opId, pass ->
                adminVm.authenticateAdminCredentials(opId, pass)
            },
            onLaunchBiometricAdmin = { ok, err ->
                onLaunchBiometric(
                    "SONA-WANE Admin 2FA",
                    "Confirm Fingerprint or Face to unlock Admin Console",
                    ok,
                    err,
                )
            },
            onBiometricAdminUnlocked = { adminVm.unlockAdminWithBiometric() },
            onClearAuthError = { adminVm.clearAuthError() },
            onSignOutAdmin = {
                adminVm.lockAdminPortal()
                onSignOutAdmin()
            },
            onReturnToCustomerFlow = {
                adminVm.lockAdminPortal()
                onExitToCustomerMode()
            },
        )
    } else if (showKycReviewScreen && adminUser?.hasPermission("KYC") == true) {
        AdminKycReviewScreen(
            adminUser = adminUser,
            allKyc = allKyc,
            statusMessage = adminStatusMessage,
            isBusy = adminBusy,
            onClearStatus = { adminVm.clearStatus() },
            onReviewKyc = { k, st, reason -> adminVm.reviewKyc(k, st, reason) },
            onBack = { showKycReviewScreen = false },
        )
    } else {
        AdminControlPanelScreen(
            adminUser = adminUser,
            adminVerified = adminVerified,
            statusMessage = adminStatusMessage,
            isBusy = adminBusy,
            allUsers = allUsers,
            allTransactions = allTransactions,
            allKyc = allKyc,
            allTickets = allTickets,
            allTransfers = allTransfers,
            admins = admins,
            auditLogs = auditLogs,
            appConfig = appConfig,
            onLockAdmin = { adminVm.lockAdminPortal() },
            onSignOutAdmin = {
                adminVm.lockAdminPortal()
                onSignOutAdmin()
            },
            onClearStatus = { adminVm.clearStatus() },
            onUpdateUserStatus = { u, st, reason -> adminVm.updateUserStatus(u, st, reason) },
            onReviewKyc = { k, st, reason -> adminVm.reviewKyc(k, st, reason) },
            onProcessWithdrawal = { tx, approve, utr, reason -> adminVm.processWithdrawal(tx, approve, utr, reason) },
            onReplyTicket = { t, txt, st -> adminVm.replyTicket(t, txt, st) },
            onPublishAnnouncement = { t, m, p -> adminVm.publishAnnouncement(t, m, p) },
            onUpdateAppConfig = { cfg, reason -> adminVm.updateAppConfig(cfg, reason) },
            onUpsertAdminRole = { targetUid, em, name, role, perms, opId, initPass, active ->
                adminVm.upsertAdminRole(targetUid, em, name, role, perms, opId, initPass, active)
            },
            onResetAdminCredentials = { target, opId, pass ->
                adminVm.resetAdminCredentials(target, opId, pass)
            },
            onDeleteAdminRole = { target -> adminVm.deleteAdminRole(target) },
            onRotateOwnCredentials = { opId, cur, next, bio ->
                adminVm.rotateOwnAdminCredentials(opId, cur, next, bio)
            },
            onUpdateOwnBiometric = { enabled -> adminVm.updateOwnBiometricSetting(enabled) },
            onBuildCsv = { cat -> adminVm.buildTransactionsCsv(cat) },
            onBuildAuditCsv = { filteredLogs -> adminVm.buildAuditLogsCsv(filteredLogs) },
            onOpenKycReviewScreen = { showKycReviewScreen = true },
            onBackToCustomerApp = {
                adminVm.lockAdminPortal()
                onExitToCustomerMode()
            },
        )
    }
}

private data class BottomNavTab(
    val route: String,
    val baseRoute: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun AuthenticatedAppShell(
    uid: String,
    email: String?,
    displayName: String?,
    onLaunchBiometric: (title: String, subtitle: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onOpenIsolatedAdminPortal: () -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext

    val mainVm: MainViewModel = viewModel(
        key = "main_$uid",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(
                    repository = SonaWaneRepository(appContext),
                    marketService = MarketRateService(),
                    prefs = SecurityPreferences(appContext),
                    currentUserId = uid,
                    currentEmail = email,
                    currentDisplayName = displayName,
                    appContext = appContext,
                ) as T
            }
        },
    )

    val adminEligibilityVm: AdminViewModel = viewModel(
        key = "admin_check_$uid",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AdminViewModel(
                    adminRepo = AdminRepository(appContext),
                    userRepo = SonaWaneRepository(appContext),
                    prefs = SecurityPreferences(appContext),
                ) as T
            }
        },
    )

    val isInitializing by mainVm.isInitializing.collectAsStateWithLifecycle()
    val profile by mainVm.profile.collectAsStateWithLifecycle()
    val wallet by mainVm.wallet.collectAsStateWithLifecycle()
    val ledger by mainVm.ledger.collectAsStateWithLifecycle()
    val transactions by mainVm.transactions.collectAsStateWithLifecycle()
    val bankAccounts by mainVm.bankAccounts.collectAsStateWithLifecycle()
    val kycRecord by mainVm.kycRecord.collectAsStateWithLifecycle()
    val devices by mainVm.devices.collectAsStateWithLifecycle()
    val loginHistory by mainVm.loginHistory.collectAsStateWithLifecycle()
    val priceAlerts by mainVm.priceAlerts.collectAsStateWithLifecycle()
    val notifications by mainVm.notifications.collectAsStateWithLifecycle()
    val announcements by mainVm.announcements.collectAsStateWithLifecycle()
    val supportTickets by mainVm.supportTickets.collectAsStateWithLifecycle()
    val referrals by mainVm.referrals.collectAsStateWithLifecycle()
    val outgoingTransfers by mainVm.outgoingTransfers.collectAsStateWithLifecycle()
    val incomingTransfers by mainVm.incomingTransfers.collectAsStateWithLifecycle()
    val appConfig by mainVm.appConfig.collectAsStateWithLifecycle()
    val marketRates by mainVm.marketRates.collectAsStateWithLifecycle()
    val chartPoints by mainVm.chartPoints.collectAsStateWithLifecycle()
    val dualTrendSeries by mainVm.dualTrendSeries.collectAsStateWithLifecycle()
    val chartLoading by mainVm.chartLoading.collectAsStateWithLifecycle()
    val marketHistory30d by mainVm.marketHistory30d.collectAsStateWithLifecycle()
    val marketHistoryLoading by mainVm.marketHistoryLoading.collectAsStateWithLifecycle()
    val marketHistoryError by mainVm.marketHistoryError.collectAsStateWithLifecycle()
    val firestoreGoldSummary by mainVm.firestoreGoldSummary.collectAsStateWithLifecycle()
    val mpinUnlocked by mainVm.mpinUnlocked.collectAsStateWithLifecycle()
    val walletUnlocked by mainVm.walletUnlocked.collectAsStateWithLifecycle()
    val walletBiometricLock by mainVm.walletBiometricLock.collectAsStateWithLifecycle()
    val tradeBiometricConfirm by mainVm.tradeBiometricConfirm.collectAsStateWithLifecycle()
    val biometricAppAccess by mainVm.biometricAppAccess.collectAsStateWithLifecycle()
    val isBusy by mainVm.isBusy.collectAsStateWithLifecycle()
    val actionBanner by mainVm.actionBanner.collectAsStateWithLifecycle()
    val recipientLookup by mainVm.recipientLookup.collectAsStateWithLifecycle()
    val otpState by mainVm.otpState.collectAsStateWithLifecycle()
    val mpinRateLimit by mainVm.mpinRateLimit.collectAsStateWithLifecycle()
    val bgDropMonitor by mainVm.bgDropMonitor.collectAsStateWithLifecycle()

    val adminUser by adminEligibilityVm.adminUser.collectAsStateWithLifecycle()

    var showMpinSetupScreen by remember { mutableStateOf(false) }
    var inspectedTx by remember { mutableStateOf<TransactionRecord?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionBanner) {
        val banner = actionBanner
        if (banner != null) {
            if (banner.receiptTx != null) {
                inspectedTx = banner.receiptTx
            }
            snackbarHostState.showSnackbar(banner.message)
            mainVm.dismissBanner()
        }
    }

    BackHandler(enabled = isBusy) {
        // Prevent navigating away while an atomic financial transaction is in flight
    }

    if (isInitializing) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepBlack),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = RoyalGold)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Loading your SONA-WANE GOLD vault...", color = LightGold)
            }
        }
        return
    }

    // Account Suspension / Block Gate
    val accStatus = profile?.accountStatus ?: "ACTIVE"
    if (accStatus == "BLOCKED" || accStatus == "SUSPENDED") {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepBlack)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            LuxuryCard(highlightGold = true) {
                Text("Account $accStatus", style = MaterialTheme.typography.headlineMedium, color = ErrorRed)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Your account access has been $accStatus by SONA-WANE GOLD compliance. Contact ${appConfig.supportEmail} for assistance.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureWhite,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OrangeActionButton(text = "Sign Out", onClick = onSignOut, modifier = Modifier.fillMaxWidth())
            }
        }
        return
    }

    // MPIN Setup / Reset Screen
    if (showMpinSetupScreen) {
        BackHandler { showMpinSetupScreen = false }
        MpinLockScreen(
            title = if (profile?.mpinHash.isNullOrBlank()) "Create 4-Digit MPIN" else "Reset 4-Digit MPIN",
            subtitle = "Your MPIN secures bullion sales, withdrawals & transfers.",
            isSetupMode = true,
            onPinSubmitted = { pin, _ ->
                mainVm.setupMpin(pin) {
                    showMpinSetupScreen = false
                }
            },
        )
        return
    }

    // MPIN / Biometric App Access Lock Gate (when configured and not yet unlocked)
    if ((!profile?.mpinHash.isNullOrBlank() || biometricAppAccess) && !mpinUnlocked) {
        if (!profile?.mpinHash.isNullOrBlank()) {
            MpinLockScreen(
                title = "Unlock SONA-WANE GOLD",
                subtitle = "Enter your 4-digit MPIN or use Biometrics to access your vault.",
                isSetupMode = false,
                showBiometricButton = profile?.biometricEnabled == true || biometricAppAccess,
                mpinRateLimit = mpinRateLimit,
                otpState = otpState,
                onBiometricClick = {
                    onLaunchBiometric(
                        "SONA-WANE GOLD Vault Unlock",
                        "Verify your Fingerprint or Face to unlock vault",
                        { mainVm.unlockMpinSession(viaBiometric = true) },
                        {},
                    )
                },
                onPinSubmitted = { pin, onErr ->
                    if (mainVm.verifyMpin(pin)) {
                        mainVm.unlockMpinSession(viaBiometric = false)
                    } else {
                        onErr("Incorrect MPIN. Try again.")
                    }
                },
                onRequestOtp = { purpose -> mainVm.requestSecurityOtp(purpose) },
                onVerifyOtp = { code -> mainVm.verifySecurityOtp(code) },
                onCancelOtp = { mainVm.cancelSecurityOtp() },
                onOtpVerifiedResetMpin = { showMpinSetupScreen = true },
                onForgotMpin = { showMpinSetupScreen = true },
            )
        } else {
            LaunchedEffect(Unit) {
                onLaunchBiometric(
                    "SONA-WANE GOLD App Access",
                    "Verify your Fingerprint or Face ID to open your bullion vault",
                    { mainVm.unlockMpinSession(viaBiometric = true) },
                    {},
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DeepBlack)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                LuxuryCard(highlightGold = true) {
                    Text("Biometric App Access", style = MaterialTheme.typography.headlineMedium, color = LightGold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Biometric authentication is enabled in Settings. Verify with Fingerprint or Face ID to enter SONA-WANE GOLD.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PureWhite,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    GoldOutlinedButton(
                        text = "Unlock with Fingerprint / Face",
                        modifier = Modifier.fillMaxWidth().testTag("biometric_app_unlock_btn"),
                        onClick = {
                            onLaunchBiometric(
                                "SONA-WANE GOLD App Access",
                                "Verify your Fingerprint or Face ID to enter",
                                { mainVm.unlockMpinSession(viaBiometric = true) },
                                {},
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(
                        onClick = { showMpinSetupScreen = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text("Set Up 4-Digit MPIN Backup", color = SecondarySilver)
                    }
                }
            }
        }
        return
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "home"

    val bottomTabs = remember {
        listOf(
            BottomNavTab("home", "home", "Home", Icons.Default.Home),
            BottomNavTab("gold?sell=false", "gold", "Gold", Icons.Default.MonetizationOn),
            BottomNavTab("silver?sell=false", "silver", "Silver", Icons.Default.Diamond),
            BottomNavTab("wallet?dialog=NONE", "wallet", "Wallet", Icons.Default.AccountBalanceWallet),
            BottomNavTab("profile", "profile", "Profile", Icons.Default.Person),
        )
    }
    val showBottomBar = bottomTabs.any { currentRoute.startsWith(it.baseRoute) }

    Scaffold(
        containerColor = DeepBlack,
        contentWindowInsets = WindowInsets.navigationBars,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = ObsidianCard,
                    contentColor = LightGold,
                    actionColor = AmberOrange,
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = CharcoalBlack,
                    contentColor = RoyalGold,
                ) {
                    bottomTabs.forEach { tab ->
                        val selected = currentRoute.startsWith(tab.baseRoute)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(tab.icon, contentDescription = tab.label)
                            },
                            label = {
                                Text(tab.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = RoyalGold,
                                selectedTextColor = LightGold,
                                unselectedIconColor = SecondarySilver,
                                unselectedTextColor = SecondarySilver,
                                indicatorColor = RoyalGold.copy(alpha = 0.16f),
                            ),
                            modifier = Modifier.testTag("nav_${tab.baseRoute}"),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val activeDropAlert = bgDropMonitor.pendingInAppAlert
            AnimatedVisibility(visible = activeDropAlert != null) {
                activeDropAlert?.let { alert ->
                    InAppGoldDropAlertBanner(
                        alert = alert,
                        onBuyNow = {
                            mainVm.dismissInAppGoldDropAlert()
                            navController.navigate("gold?sell=false")
                        },
                        onOpenSettings = {
                            mainVm.dismissInAppGoldDropAlert()
                            navController.navigate("settings")
                        },
                        onDismiss = { mainVm.dismissInAppGoldDropAlert() },
                    )
                }
            }

            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
            composable("home") {
                HomeDashboardScreen(
                    profile = profile,
                    wallet = wallet,
                    rates = marketRates,
                    firestoreGoldSummary = firestoreGoldSummary,
                    chartPoints = chartPoints,
                    dualTrendSeries = dualTrendSeries,
                    marketHistory30d = marketHistory30d,
                    chartLoading = chartLoading || marketHistoryLoading,
                    marketHistoryError = marketHistoryError,
                    announcements = announcements,
                    appConfig = appConfig,
                    unreadNotifications = notifications.count { !it.isRead },
                    onRefreshRates = { mainVm.refreshMarketRates() },
                    onRefreshFirestoreGold = { mainVm.refreshFirestoreGoldSummary() },
                    onSelectChart = { isGold, range -> mainVm.loadMarketChart(isGold, range) },
                    onSelectTrendRange = { range -> mainVm.loadDualTrend(range) },
                    onSyncFirestoreHistory = { mainVm.syncFirestoreMarketHistory(force = true) },
                    onBuyGold = { navController.navigate("gold?sell=false") },
                    onSellGold = { navController.navigate("gold?sell=true") },
                    onBuySilver = { navController.navigate("silver?sell=false") },
                    onSellSilver = { navController.navigate("silver?sell=true") },
                    onAddMoney = { navController.navigate("wallet?dialog=ADD") },
                    onWithdrawMoney = { navController.navigate("wallet?dialog=WITHDRAW") },
                    onTransfer = { navController.navigate("transfer?q=") },
                    onOpenQr = { navController.navigate("my_qr") },
                    onOpenHistory = { navController.navigate("history") },
                    onOpenMarket = { navController.navigate("market") },
                    onOpenAlerts = { navController.navigate("market_alerts") },
                    onOpenCalculator = { navController.navigate("gold_calculator") },
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenNotifications = { navController.navigate("notifications") },
                    onOpenProfile = { navController.navigate("profile") },
                    transactions = transactions,
                    onViewReceipt = { inspectedTx = it },
                )
            }

            composable(
                route = "gold?sell={sell}",
                arguments = listOf(navArgument("sell") { type = NavType.BoolType; defaultValue = false }),
            ) { entry ->
                val sell = entry.arguments?.getBoolean("sell") ?: false
                BullionTradeScreen(
                    isGold = true,
                    initialSellTab = sell,
                    profile = profile,
                    wallet = wallet,
                    rates = marketRates,
                    appConfig = appConfig,
                    bankAccounts = bankAccounts,
                    transactions = transactions,
                    isBusy = isBusy,
                    requireBiometricForTrades = tradeBiometricConfirm,
                    onBiometricTradeAuthorized = { action -> mainVm.recordTradeBiometricAuth(action) },
                    onBuy = { purity, grams, rate, base, gst, charges, total, method ->
                        mainVm.buyBullion(true, purity, grams, rate, base, gst, charges, total, method)
                    },
                    onSell = { purity, grams, sellRate, deductions, toWallet, bank ->
                        mainVm.sellBullion(true, purity, grams, sellRate, deductions, toWallet, bank)
                    },
                    onVerifyMpin = { mainVm.verifyMpin(it) },
                    onVerifyBiometric = { ok, err ->
                        onLaunchBiometric("Authorize Gold Trade", "Confirm Fingerprint or Face to execute gold order", ok, err)
                    },
                    onSetupMpin = { showMpinSetupScreen = true },
                    onViewReceipt = { inspectedTx = it },
                )
            }

            composable(
                route = "silver?sell={sell}",
                arguments = listOf(navArgument("sell") { type = NavType.BoolType; defaultValue = false }),
            ) { entry ->
                val sell = entry.arguments?.getBoolean("sell") ?: false
                BullionTradeScreen(
                    isGold = false,
                    initialSellTab = sell,
                    profile = profile,
                    wallet = wallet,
                    rates = marketRates,
                    appConfig = appConfig,
                    bankAccounts = bankAccounts,
                    transactions = transactions,
                    isBusy = isBusy,
                    requireBiometricForTrades = tradeBiometricConfirm,
                    onBiometricTradeAuthorized = { action -> mainVm.recordTradeBiometricAuth(action) },
                    onBuy = { purity, grams, rate, base, gst, charges, total, method ->
                        mainVm.buyBullion(false, purity, grams, rate, base, gst, charges, total, method)
                    },
                    onSell = { purity, grams, sellRate, deductions, toWallet, bank ->
                        mainVm.sellBullion(false, purity, grams, sellRate, deductions, toWallet, bank)
                    },
                    onVerifyMpin = { mainVm.verifyMpin(it) },
                    onVerifyBiometric = { ok, err ->
                        onLaunchBiometric("Authorize Silver Trade", "Confirm Fingerprint or Face to execute silver order", ok, err)
                    },
                    onSetupMpin = { showMpinSetupScreen = true },
                    onViewReceipt = { inspectedTx = it },
                )
            }

            composable(
                route = "wallet?dialog={dialog}",
                arguments = listOf(navArgument("dialog") { type = NavType.StringType; defaultValue = "NONE" }),
            ) { entry ->
                val dlg = entry.arguments?.getString("dialog")?.takeIf { it == "ADD" || it == "WITHDRAW" }
                WalletScreen(
                    profile = profile,
                    wallet = wallet,
                    ledger = ledger,
                    transactions = transactions,
                    bankAccounts = bankAccounts,
                    rates = marketRates,
                    appConfig = appConfig,
                    isBusy = isBusy,
                    initialDialog = dlg,
                    walletUnlocked = walletUnlocked,
                    walletBiometricLock = walletBiometricLock,
                    onUnlockWalletWithBiometric = { ok, err ->
                        onLaunchBiometric(
                            "Unlock SONA-WANE Wallet",
                            "Confirm Fingerprint or Face to access your bullion & INR wallet",
                            {
                                mainVm.unlockWalletSession(viaBiometric = true)
                                ok()
                            },
                            err,
                        )
                    },
                    onUnlockWalletWithMpin = { mainVm.unlockWalletSession(viaBiometric = false) },
                    onLockWallet = { mainVm.lockWalletSession() },
                    onToggleWalletBiometricLock = { enabled -> mainVm.setWalletBiometricLock(enabled) },
                    onEnableAccountBiometrics = {
                        onLaunchBiometric(
                            "Activate Biometric Wallet Lock",
                            "Scan Fingerprint or Face to enable biometric wallet & trade protection",
                            { mainVm.updatePreferences(biometric = true) },
                            {},
                        )
                    },
                    onDeposit = { amount, method, simFail -> mainVm.depositMoney(amount, method, simFail) },
                    onWithdraw = { amount, bank -> mainVm.requestWithdrawal(amount, bank) },
                    onVerifyMpin = { mainVm.verifyMpin(it) },
                    onVerifyBiometric = { ok, err ->
                        onLaunchBiometric("Authorize Bank Withdrawal", "Confirm Fingerprint or Face to initiate payout", ok, err)
                    },
                    onSetupMpin = { showMpinSetupScreen = true },
                    onOpenBankAccounts = { navController.navigate("bank_accounts") },
                    onOpenTransfer = { navController.navigate("transfer?q=") },
                    onOpenFullHistory = { navController.navigate("history") },
                    onViewReceipt = { inspectedTx = it },
                )
            }

            composable("profile") {
                ProfileScreen(
                    profile = profile,
                    kycRecord = kycRecord,
                    devices = devices,
                    loginHistory = loginHistory,
                    appConfig = appConfig,
                    isAdminEligible = adminUser != null,
                    isBusy = isBusy,
                    onUpdateProfile = { fn, mob, cc, em, dob, addr, st, city, pin, pan, photo ->
                        mainVm.updateProfile(fn, mob, cc, em, dob, addr, st, city, pin, pan, photo)
                    },
                    onSubmitKyc = { fn, mob, pan, aadhaar, docType, docUri, addr, st, city, pin ->
                        mainVm.submitKyc(fn, mob, pan, aadhaar, docType, docUri, addr, st, city, pin)
                    },
                    onVerifyMpin = { mainVm.verifyMpin(it) },
                    onToggleBiometric = { enabled ->
                        if (enabled) {
                            onLaunchBiometric(
                                "Activate Biometric Login",
                                "Scan your Fingerprint or Face ID to enable vault biometric authentication",
                                { mainVm.updatePreferences(biometric = true) },
                                {},
                            )
                        } else {
                            mainVm.updatePreferences(biometric = false)
                        }
                    },
                    walletBiometricLock = walletBiometricLock,
                    tradeBiometricConfirm = tradeBiometricConfirm,
                    onToggleWalletBiometricLock = { enabled -> mainVm.setWalletBiometricLock(enabled) },
                    onToggleTradeBiometricConfirm = { enabled -> mainVm.setTradeBiometricConfirm(enabled) },
                    onChangeMpin = { showMpinSetupScreen = true },
                    onLockSession = { mainVm.lockSession() },
                    onRevokeOtherDevices = { mainVm.revokeOtherDevices() },
                    onOpenBankAccounts = { navController.navigate("bank_accounts") },
                    onOpenReferrals = { navController.navigate("referrals") },
                    onOpenMyQr = { navController.navigate("my_qr") },
                    onOpenMarketAlerts = { navController.navigate("market_alerts") },
                    onOpenNotifications = { navController.navigate("notifications") },
                    onOpenSupport = { navController.navigate("support") },
                    onRequestAccountDeletion = {
                        mainVm.createSupportTicket(
                            category = "Account Security",
                            subject = "Account Deletion Compliance Request",
                            description = "User ${profile?.customUserId} requested vault closure & account deletion review.",
                            attachmentUri = null,
                        )
                    },
                    onOpenKycFlow = { navController.navigate("kyc_verification") },
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenAdminPortal = onOpenIsolatedAdminPortal,
                    onSignOut = onSignOut,
                )
            }

            composable("market") {
                MarketScreen(
                    rates = marketRates,
                    chartPoints = chartPoints,
                    dualTrendSeries = dualTrendSeries,
                    marketHistory30d = marketHistory30d,
                    chartLoading = chartLoading || marketHistoryLoading,
                    marketHistoryError = marketHistoryError,
                    alerts = priceAlerts,
                    onBack = { navController.popBackStack() },
                    onRefresh = { mainVm.refreshMarketRates() },
                    onLoadChart = { isGold, range -> mainVm.loadMarketChart(isGold, range) },
                    onSelectTrendRange = { range -> mainVm.loadDualTrend(range) },
                    onSyncFirestoreHistory = { mainVm.syncFirestoreMarketHistory(force = true) },
                    onCreateAlert = { metal, target, dir, freq, notes ->
                        mainVm.createPriceAlert(metal, target, dir, freq, notes)
                    },
                    onToggleAlert = { alert, active -> mainVm.togglePriceAlert(alert, active) },
                    onDeleteAlert = { mainVm.deletePriceAlert(it) },
                    onOpenAlertsCenter = { navController.navigate("market_alerts") },
                )
            }

            composable("market_alerts") {
                MarketAlertsScreen(
                    profile = profile,
                    rates = marketRates,
                    alerts = priceAlerts,
                    bgDropMonitor = bgDropMonitor,
                    onBack = { navController.popBackStack() },
                    onRefreshRates = { mainVm.refreshMarketRates() },
                    onCheckAlertsNow = { mainVm.checkAlertsNow() },
                    onConfigureBgDropMonitor = { enabled, threshold ->
                        mainVm.configureBackgroundDropMonitor(enabled, threshold)
                    },
                    onRunBgDropCheckNow = { mainVm.runBackgroundDropCheckNow() },
                    onCreateAlert = { metal, target, dir, freq, notes ->
                        mainVm.createPriceAlert(metal, target, dir, freq, notes)
                    },
                    onUpdateAlert = { id, target, dir, freq, active, notes ->
                        mainVm.updatePriceAlert(id, target, dir, freq, active, notes)
                    },
                    onToggleAlert = { alert, active -> mainVm.togglePriceAlert(alert, active) },
                    onDeleteAlert = { mainVm.deletePriceAlert(it) },
                    onEnablePriceAlertPrefs = { enabled -> mainVm.updatePreferences(priceAlerts = enabled) },
                )
            }

            composable("kyc_verification") {
                KycVerificationScreen(
                    profile = profile,
                    kycRecord = kycRecord,
                    isBusy = isBusy,
                    onBack = { navController.popBackStack() },
                    onSubmitKyc = { fn, mob, pan, aadhaar, docType, docUri, addr, st, city, pin, frontB64, backB64, method, score, decl ->
                        mainVm.submitKyc(
                            fullName = fn,
                            mobile = mob,
                            pan = pan,
                            aadhaarLast4 = aadhaar,
                            docType = docType,
                            docUri = docUri,
                            address = addr,
                            state = st,
                            city = city,
                            pincode = pin,
                            documentFrontBase64 = frontB64,
                            documentBackBase64 = backB64,
                            captureMethod = method,
                            scanQualityScore = score,
                            declarationAccepted = decl,
                        )
                    },
                )
            }

            composable("bank_accounts") {
                BankAccountsScreen(
                    bankAccounts = bankAccounts,
                    hasMpin = !profile?.mpinHash.isNullOrBlank(),
                    biometricEnabled = profile?.biometricEnabled == true,
                    onVerifyMpin = { mainVm.verifyMpin(it) },
                    onVerifyBiometric = { ok, err ->
                        onLaunchBiometric("Remove Bank Account", "Confirm Fingerprint or Face to unlink payout account", ok, err)
                    },
                    onAddBank = { holder, bankName, accNo, ifsc, primary ->
                        mainVm.addBankAccount(holder, bankName, accNo, ifsc, primary)
                    },
                    onSetPrimary = { mainVm.setPrimaryBank(it) },
                    onRemoveBank = { mainVm.removeBank(it) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = "transfer?q={q}",
                arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                val initialQ = entry.arguments?.getString("q").orEmpty()
                TransferScreen(
                    profile = profile,
                    wallet = wallet,
                    appConfig = appConfig,
                    recipient = recipientLookup,
                    outgoingTransfers = outgoingTransfers,
                    incomingTransfers = incomingTransfers,
                    initialLookupQuery = initialQ,
                    isBusy = isBusy,
                    onLookupRecipient = { mainVm.lookupRecipient(it) },
                    onClearRecipient = { mainVm.clearRecipient() },
                    onOpenQrScanner = { navController.navigate("qr_scanner") },
                    onVerifyMpin = { mainVm.verifyMpin(it) },
                    onVerifyBiometric = { ok, err ->
                        onLaunchBiometric("Authorize Transfer", "Confirm Fingerprint or Face to transfer assets", ok, err)
                    },
                    onSetupMpin = { showMpinSetupScreen = true },
                    onExecuteTransfer = { rec, asset, qty -> mainVm.executeTransfer(rec, asset, qty) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable("my_qr") {
                MyQrScreen(
                    profile = profile,
                    onOpenScanner = { navController.navigate("qr_scanner") },
                    onBack = { navController.popBackStack() },
                )
            }

            composable("qr_scanner") {
                QrScannerScreen(
                    onQrDetected = { raw ->
                        navController.popBackStack()
                        mainVm.lookupRecipient(raw)
                        navController.navigate("transfer?q=$raw") {
                            launchSingleTop = true
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            composable("history") {
                TransactionHistoryScreen(
                    transactions = transactions,
                    userName = profile?.fullName ?: "Customer",
                    onBack = { navController.popBackStack() },
                    onOpenSupport = { navController.navigate("support") },
                )
            }

            composable("notifications") {
                NotificationsScreen(
                    profile = profile,
                    notifications = notifications,
                    announcements = announcements,
                    onMarkRead = { mainVm.markNotificationRead(it) },
                    onDelete = { mainVm.deleteNotification(it) },
                    onClearAll = { mainVm.clearNotifications() },
                    onUpdatePrefs = { sound, vib, alerts ->
                        mainVm.updatePreferences(sound = sound, vibration = vib, priceAlerts = alerts)
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            composable("support") {
                HelpSupportScreen(
                    appConfig = appConfig,
                    tickets = supportTickets,
                    isBusy = isBusy,
                    onCreateTicket = { cat, sub, desc, uri -> mainVm.createSupportTicket(cat, sub, desc, uri) },
                    onReplyTicket = { t, txt, close -> mainVm.replySupportTicket(t, txt, close) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable("referrals") {
                ReferralsScreen(
                    profile = profile,
                    appConfig = appConfig,
                    referrals = referrals,
                    isBusy = isBusy,
                    onApplyCode = { mainVm.applyReferralCode(it) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable("gold_calculator") {
                GoldPurchaseCalculatorScreen(
                    profile = profile,
                    wallet = wallet,
                    firestoreGoldSummary = firestoreGoldSummary,
                    fallbackRates = marketRates,
                    appConfig = appConfig,
                    isBusy = isBusy,
                    requireBiometricForTrades = tradeBiometricConfirm,
                    onBack = { navController.popBackStack() },
                    onRefreshFirestoreRate = { mainVm.refreshFirestoreGoldSummary() },
                    onVerifyMpin = { mainVm.verifyMpin(it) },
                    onVerifyBiometric = { ok, err ->
                        onLaunchBiometric("Authorize Gold Purchase", "Confirm Fingerprint or Face to complete payment", ok, err)
                    },
                    onBiometricTradeAuthorized = { action -> mainVm.recordTradeBiometricAuth(action) },
                    onTriggerPayment = { purity, grams, rate, base, gst, charges, total, method ->
                        mainVm.buyBullion(true, purity, grams, rate, base, gst, charges, total, method)
                    },
                )
            }

            composable("settings") {
                SettingsScreen(
                    profile = profile,
                    biometricAppAccess = biometricAppAccess,
                    biometricTxConfirm = tradeBiometricConfirm,
                    walletBiometricLock = walletBiometricLock,
                    rates = marketRates,
                    firestoreGoldSummary = firestoreGoldSummary,
                    bgDropMonitor = bgDropMonitor,
                    onConfigureGoldMonitor = { enabled, threshold, purity ->
                        mainVm.configureBackgroundDropMonitor(enabled, threshold, purity)
                    },
                    onRunGoldMonitorCheckNow = {
                        mainVm.runBackgroundDropCheckNow()
                    },
                    onToggleBiometricMaster = { enable ->
                        if (enable) {
                            onLaunchBiometric(
                                "Enable Biometric Authentication",
                                "Scan Fingerprint or Face ID to enable app access & transaction confirmation",
                                { mainVm.setBiometricAuthenticationMaster(true) },
                                {},
                            )
                        } else {
                            mainVm.setBiometricAuthenticationMaster(false)
                        }
                    },
                    onToggleBiometricAppAccess = { enable ->
                        if (enable) {
                            onLaunchBiometric(
                                "Enable Biometric App Access",
                                "Confirm Fingerprint or Face ID to secure app launch",
                                { mainVm.setBiometricAppAccess(true) },
                                {},
                            )
                        } else {
                            mainVm.setBiometricAppAccess(false)
                        }
                    },
                    onToggleBiometricTxConfirm = { enable ->
                        if (enable) {
                            onLaunchBiometric(
                                "Enable Biometric Transaction Confirmation",
                                "Confirm Fingerprint or Face ID to secure gold & silver trades",
                                { mainVm.setTradeBiometricConfirm(true) },
                                {},
                            )
                        } else {
                            mainVm.setTradeBiometricConfirm(false)
                        }
                    },
                    onToggleWalletBiometricLock = { enable ->
                        mainVm.setWalletBiometricLock(enable)
                    },
                    onUpdateNotificationPrefs = { sound, vib, alerts, announcements ->
                        mainVm.updatePreferences(
                            sound = sound,
                            vibration = vib,
                            priceAlerts = alerts,
                            announcements = announcements,
                        )
                    },
                    onChangeMpin = { showMpinSetupScreen = true },
                    onLockVaultNow = { mainVm.lockSession() },
                    onOpenPriceAlerts = { navController.navigate("market_alerts") },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        }
    }

    if (inspectedTx != null) {
        TransactionDetailDialog(
            tx = inspectedTx!!,
            userName = profile?.fullName ?: "Customer",
            onDismiss = { inspectedTx = null },
            onContactSupport = {
                inspectedTx = null
                navController.navigate("support")
            },
        )
    }
}

@Composable
private fun InAppGoldDropAlertBanner(
    alert: GoldDropInAppAlert,
    onBuyNow: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        color = ObsidianCard,
        border = BorderStroke(1.5.dp, AmberOrange),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("in_app_gold_drop_alert"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = AmberOrange,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusPill(
                        text = stringResource(R.string.in_app_drop_alert_badge),
                        color = AmberOrange,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = GoldPriceMonitorService.purityLabel(alert.purity),
                        style = MaterialTheme.typography.titleSmall,
                        color = LightGold,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("in_app_gold_drop_dismiss_icon"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.in_app_drop_alert_dismiss),
                        tint = SecondarySilver,
                    )
                }
            }

            Text(
                text = alert.message.ifBlank {
                    "${GoldPriceMonitorService.purityLabel(alert.purity)} dropped to ${Formatters.formatInr(alert.observedRateInr)}/g (below ${Formatters.formatInr(alert.thresholdInr)}/g)."
                },
                style = MaterialTheme.typography.bodySmall,
                color = PureWhite,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onBuyNow,
                    border = BorderStroke(1.dp, RoyalGold),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LightGold),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("in_app_gold_drop_buy_btn"),
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.in_app_drop_alert_buy_cta))
                }
                TextButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("in_app_gold_drop_settings_btn"),
                ) {
                    Text("Settings", color = SecondarySilver)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("in_app_gold_drop_dismiss_btn"),
                ) {
                    Text(stringResource(R.string.in_app_drop_alert_dismiss), color = AmberOrange)
                }
            }
        }
    }
}

