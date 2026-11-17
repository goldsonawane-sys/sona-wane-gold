package com.example

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.example.data.local.BgDropMonitorState
import com.example.data.local.PriceAlertWorkerConfig
import com.example.data.local.SecurityPreferences
import com.example.data.model.DualMetalTrendSeries
import com.example.data.model.FirestoreGoldSummary
import com.example.data.model.KycRecord
import com.example.data.model.LiveMarketRates
import com.example.data.model.MarketPoint
import com.example.data.model.MarketPriceHistoryEntry
import com.example.data.model.PriceAlert
import com.example.data.model.TransactionRecord
import com.example.data.model.UserProfile
import com.example.data.model.WalletHoldings
import com.example.data.repository.AdminRepository
import com.example.data.repository.FirestoreService
import com.example.data.repository.RbacCheckResult
import com.example.service.FirestoreThresholdDataSource
import com.example.service.FirestoreThresholdSnapshot
import com.example.service.GoldPriceMonitorService
import com.example.service.PriceAlertManager
import com.example.service.TargetEvaluationOutcome
import com.example.ui.components.GoldTradingPanel
import com.example.ui.components.KycStatusIndicatorCard
import com.example.ui.components.MarketTrendsDashboard
import com.example.ui.components.PortfolioSummaryWidget
import com.example.ui.components.TransactionHistoryView
import com.example.ui.screens.AdminLoginScreen
import com.example.ui.screens.AlertsScreen
import com.example.ui.screens.BiometricAuthenticationScreen
import com.example.ui.screens.MpinLockScreen
import com.example.ui.screens.MyQrScreen
import com.example.ui.screens.PriceTrackingScreen
import com.example.ui.screens.QrScannerScreen
import com.example.ui.screens.SensitiveActionScope
import com.example.ui.screens.WalletScreen
import com.example.ui.theme.SonaWaneGoldTheme
import com.example.worker.PriceAlertCheckWorker
import com.example.util.BiometricAuthManager
import com.example.util.BiometricCapability
import com.example.util.BiometricLoginManager
import com.example.util.BullionQrModule
import com.example.util.CryptoUtils
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Sona-Wane Gold", appName)
  }

  @Test
  fun `biometric wallet and trade protection flags default true and toggle cleanly`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = SecurityPreferences(context)
    assertTrue(prefs.walletBiometricLockFlow.first())
    assertTrue(prefs.tradeBiometricConfirmFlow.first())

    prefs.setWalletBiometricLock(false)
    prefs.setTradeBiometricConfirm(false)
    assertFalse(prefs.walletBiometricLockFlow.first())
    assertFalse(prefs.tradeBiometricConfirmFlow.first())

    prefs.setWalletBiometricLock(true)
    prefs.setTradeBiometricConfirm(true)
    assertTrue(prefs.walletBiometricLockFlow.first())
    assertTrue(prefs.tradeBiometricConfirmFlow.first())
  }

  @Test
  fun `background gold drop monitor threshold and check status persist`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = SecurityPreferences(context)

    prefs.setBgDropMonitorConfig(enabled = true, threshold24kInr = 7650.0)
    val armed = prefs.bgDropMonitorFlow.first()
    assertTrue(armed.enabled)
    assertEquals(7650.0, armed.threshold24kInr, 0.01)
    assertEquals("ARMED", armed.lastStatus)

    prefs.recordBgDropCheck(
      observedRateInr = 7610.0,
      status = "TRIGGERED",
      checkedAtMs = 1_700_000_000_000L,
      notifiedAtMs = 1_700_000_000_000L,
    )
    val afterCheck = prefs.bgDropMonitorFlow.first()
    assertEquals(7610.0, afterCheck.lastObservedRateInr, 0.01)
    assertEquals("TRIGGERED", afterCheck.lastStatus)
    assertEquals(1_700_000_000_000L, afterCheck.lastNotifiedEpochMs)
  }

  @Test
  fun `zxing encrypted qr bitmap encodes and decodes back to verified user id`() {
    val token = CryptoUtils.buildQrPayload("SWG778899", "Priya Bullion")
    val bitmap = BullionQrModule.generateQrBitmap(token, 320)
    assertNotNull(bitmap)
    val bmp = bitmap!!
    assertEquals(320, bmp.width)
    assertEquals(320, bmp.height)

    val decodedText = BullionQrModule.decodeQrFromBitmap(bmp)
    assertNotNull(decodedText)
    assertEquals(token, decodedText)
    assertEquals("SWG778899", CryptoUtils.parseQrUserId(decodedText!!))
  }

  @Test
  fun `biometric app access and transaction confirmation persist in DataStore`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = SecurityPreferences(context)

    assertFalse(prefs.biometricAppAccessFlow.first())
    assertTrue(prefs.tradeBiometricConfirmFlow.first())

    prefs.setBiometricAppAccess(true)
    assertTrue(prefs.biometricAppAccessFlow.first())

    prefs.setBiometricAuthenticationSettings(appAccessEnabled = true, txConfirmEnabled = false)
    assertTrue(prefs.biometricAppAccessFlow.first())
    assertFalse(prefs.tradeBiometricConfirmFlow.first())

    prefs.setBiometricAuthenticationSettings(appAccessEnabled = false, txConfirmEnabled = true)
    assertFalse(prefs.biometricAppAccessFlow.first())
    assertTrue(prefs.tradeBiometricConfirmFlow.first())
  }

  @Test
  fun `gold price monitor service triggers in-app alert when rate drops below settings threshold`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = SecurityPreferences(context)
    val service = GoldPriceMonitorService(context, prefs)

    prefs.setBgDropMonitorConfig(enabled = true, threshold24kInr = 8000.0, purity = "GOLD_24K")
    val armedState: BgDropMonitorState = prefs.bgDropMonitorFlow.first()
    assertTrue(armedState.enabled)
    assertEquals("GOLD_24K", armedState.purity)

    val result = service.evaluateAndDispatch(
      config = armedState,
      observedRateInr = 7820.0,
      playSound = false,
      vibrate = false,
      forceNotify = false,
      nowMs = 1_700_000_500_000L,
    )

    assertEquals("DROP_TRIGGERED", result.outcome.status)
    assertTrue(result.inAppAlertSaved)

    val updated = prefs.bgDropMonitorFlow.first()
    assertEquals("DROP_TRIGGERED", updated.lastStatus)
    assertEquals(7820.0, updated.lastObservedRateInr, 0.01)
    assertNotNull(updated.pendingInAppAlert)
    assertEquals(7820.0, updated.pendingInAppAlert!!.observedRateInr, 0.01)
    assertEquals(8000.0, updated.pendingInAppAlert!!.thresholdInr, 0.01)

    prefs.dismissPendingDropAlert()
    val dismissed = prefs.bgDropMonitorFlow.first()
    assertEquals(null, dismissed.pendingInAppAlert)
  }

  @Test
  fun `biometric capability check handles environment safely and vault uid persists`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val cap = BiometricAuthManager.checkCapability(context)
    assertTrue(cap.label.isNotBlank())

    val prefs = SecurityPreferences(context)
    prefs.setBiometricAppAccess(true)
    prefs.setBiometricVaultUid("uid_bio_123")
    assertTrue(prefs.biometricAppAccessFlow.first())
    assertEquals("uid_bio_123", prefs.biometricVaultUidFlow.first())

    prefs.setBiometricAppAccess(false)
    prefs.setBiometricVaultUid(null)
    assertFalse(prefs.biometricAppAccessFlow.first())
    assertEquals(null, prefs.biometricVaultUidFlow.first())
  }

  @Test
  fun `zxing wallet address qr encodes gold receive request and resolves transfer prefill`() {
    val envelope = BullionQrModule.generateEncryptedPayload(
      customUserId = "SWG334455",
      displayName = "Meera Vault",
      requestedAsset = "GOLD_22K",
      requestedGrams = 5.25,
    )
    val bitmap = BullionQrModule.generateQrBitmap(envelope.encryptedToken, 360)
    assertNotNull(bitmap)
    val bmp = bitmap!!
    assertEquals(360, bmp.width)
    assertEquals(360, bmp.height)

    val decodedText = BullionQrModule.decodeQrFromBitmap(bmp)
    assertNotNull(decodedText)

    val prefill = BullionQrModule.resolveTransferPrefill(decodedText!!)
    assertNotNull(prefill)
    assertTrue(prefill!!.isEncryptedVerified)
    assertEquals("SWG334455", prefill.userId)
    assertEquals("SWG-VAULT-SWG334455", prefill.walletAddress)
    assertEquals("Meera Vault", prefill.displayName)
    assertEquals("GOLD_22K", prefill.requestedAsset)
    assertEquals(5.25, prefill.requestedGrams, 0.0001)
  }

  @Test
  fun `kyc status indicator card displays Verified and Pending labels from Firestore user profile`() {
    var currentProfile = UserProfile(
      userId = "uid_1",
      customUserId = "SWG101010",
      fullName = "Aarav SonaWane",
      kycStatus = "APPROVED",
      panMasked = "ABCDE****F",
    )
    var currentKyc: KycRecord? = KycRecord(
      userId = "uid_1",
      status = "APPROVED",
      documentType = "PAN_CARD",
    )

    composeRule.setContent {
      SonaWaneGoldTheme {
        KycStatusIndicatorCard(
          profile = currentProfile,
          kycRecord = currentKyc,
          onActionClick = {},
        )
      }
    }

    composeRule.onNodeWithTag("profile_kyc_status_card").assertIsDisplayed()
    composeRule.onNodeWithText("KYC Status").assertIsDisplayed()
    composeRule.onNodeWithText("Verified").assertIsDisplayed()
    composeRule.onNodeWithText("Identity & PAN Verified").assertIsDisplayed()
  }

  @Test
  fun `kyc status indicator card displays Pending when Firestore profile is UNDER_REVIEW`() {
    composeRule.setContent {
      SonaWaneGoldTheme {
        KycStatusIndicatorCard(
          profile = UserProfile(
            userId = "uid_2",
            customUserId = "SWG202020",
            fullName = "Priya Gold",
            kycStatus = "UNDER_REVIEW",
          ),
          kycRecord = KycRecord(
            userId = "uid_2",
            status = "UNDER_REVIEW",
            documentType = "AADHAAR",
          ),
          onActionClick = {},
        )
      }
    }

    composeRule.onNodeWithText("KYC Status").assertIsDisplayed()
    composeRule.onNodeWithText("Pending").assertIsDisplayed()
    composeRule.onNodeWithText("Verification Pending Review").assertIsDisplayed()
  }

  @Test
  fun `transaction history view displays gold and silver trades with completed pending and failed indicators`() {
    val fixedDate = com.google.firebase.Timestamp(java.util.Date(1_763_280_000_000L))
    val expectedDateText = com.example.util.Formatters.formatTimestamp(fixedDate)
    val trades = listOf(
      TransactionRecord(
        id = "tx_gold_done",
        txCode = "SWG-G101",
        category = "GOLD_BUY",
        metalPurity = "24K",
        grams = 2.0,
        ratePerGram = 8100.0,
        totalAmount = 16686.0,
        status = "SUCCESSFUL",
        createdAt = fixedDate,
      ),
      TransactionRecord(
        id = "tx_silver_wait",
        txCode = "SWG-S102",
        category = "SILVER_SELL",
        metalPurity = "999",
        grams = 50.0,
        ratePerGram = 96.0,
        totalAmount = 4800.0,
        status = "PENDING",
        createdAt = fixedDate,
      ),
      TransactionRecord(
        id = "tx_gold_fail",
        txCode = "SWG-G103",
        category = "GOLD_BUY",
        metalPurity = "22K",
        grams = 1.0,
        ratePerGram = 7450.0,
        totalAmount = 7673.5,
        status = "FAILED",
        rejectionReason = "Payment gateway timeout",
        createdAt = fixedDate,
      ),
      TransactionRecord(
        id = "tx_deposit_ignored",
        txCode = "SWG-D104",
        category = "DEPOSIT",
        totalAmount = 10000.0,
        status = "SUCCESSFUL",
        createdAt = fixedDate,
      ),
    )

    composeRule.setContent {
      SonaWaneGoldTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          TransactionHistoryView(
            transactions = trades,
            onSelectTransaction = {},
            onViewAllHistory = {},
          )
        }
      }
    }

    composeRule.onNodeWithTag("transaction_history_view").assertIsDisplayed()
    composeRule.onNodeWithText("Completed (1)").assertIsDisplayed()
    composeRule.onNodeWithText("Pending (1)").assertIsDisplayed()
    composeRule.onNodeWithText("Failed (1)").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("trade_history_row_tx_gold_done").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("trade_history_row_tx_silver_wait").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("trade_history_row_tx_gold_fail").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("Payment gateway timeout").performScrollTo().assertIsDisplayed()

    // Filter to Sell only -> only the SILVER_SELL transaction remains visible with Date, Amount & Status
    composeRule.onNodeWithText("Recent Gold & Silver Trades").performScrollTo()
    composeRule.onNodeWithTag("tx_history_side_chip_sell").performScrollTo().performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("trade_history_row_tx_silver_wait").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText(expectedDateText).performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("trade_history_row_tx_gold_done").assertDoesNotExist()
  }

  @Test
  fun `market trends dashboard displays external financial api vico charts and toggles compare mode`() {
    val apiSeries = DualMetalTrendSeries(
      goldPoints = listOf(
        MarketPoint("11-01", 7920.0),
        MarketPoint("11-05", 7990.0),
        MarketPoint("11-10", 8060.0),
        MarketPoint("LIVE", 8140.0),
      ),
      silverPoints = listOf(
        MarketPoint("11-01", 91.5),
        MarketPoint("11-05", 93.0),
        MarketPoint("11-10", 95.0),
        MarketPoint("LIVE", 96.4),
      ),
      rangeKey = "30D",
    )
    val rates = LiveMarketRates(
      gold24kBuyPerGram = 8140.0,
      silver999BuyPerGram = 96.4,
      isLive = true,
      sourceName = "api.gold-api.com + open.er-api.com",
    )

    composeRule.setContent {
      SonaWaneGoldTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          MarketTrendsDashboard(
            historyEntries = emptyList(),
            rates = rates,
            isLoading = false,
            onSyncHistory = {},
            externalTrendSeries = apiSeries,
            autoFetchExternalApi = false,
          )
        }
      }
    }

    composeRule.onNodeWithTag("market_trends_dashboard").assertIsDisplayed()
    composeRule.onNodeWithText("LIVE API").assertIsDisplayed()
    composeRule.onNodeWithTag("vico_gold_30d_chart").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("vico_silver_30d_chart").performScrollTo().assertIsDisplayed()

    composeRule.onNodeWithText("LIVE API").performScrollTo()
    composeRule.onNodeWithTag("trends_mode_compare").performScrollTo().performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("vico_compare_30d_chart").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `biometric login manager checks capability and tracks wallet and trade session state`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = BiometricLoginManager(context, SecurityPreferences(context))

    val capability = manager.getCapability()
    assertTrue(capability.label.isNotBlank())

    assertFalse(manager.walletSessionAuthorized.value)
    manager.markWalletAuthorized()
    assertTrue(manager.walletSessionAuthorized.value)
    assertFalse(
      manager.evaluateWalletPolicy(
        userBiometricEnabled = true,
        walletLockEnabled = true,
        hasMpin = true,
      ).requiresAuthentication
    )

    manager.lockWalletSession()
    assertFalse(manager.walletSessionAuthorized.value)
    assertTrue(
      manager.evaluateWalletPolicy(
        userBiometricEnabled = true,
        walletLockEnabled = true,
        hasMpin = true,
      ).requiresAuthentication
    )

    assertFalse(manager.isTradeSessionValid(nowMs = 1_700_000_000_000L))
    manager.markTradeAuthorized(nowMs = 1_700_000_000_000L)
    assertTrue(manager.isTradeSessionValid(nowMs = 1_700_000_060_000L))
    assertFalse(manager.isTradeSessionValid(nowMs = 1_700_000_200_000L))
  }

  @Test
  fun `gold trading panel syncs amount and weight inputs and executes buy and sell transactions`() {
    var boughtPurity: String? = null
    var boughtGrams = 0.0
    var boughtTotal = 0.0
    var soldPurity: String? = null
    var soldGrams = 0.0

    val rates = LiveMarketRates(
      gold24kBuyPerGram = 8000.0,
      gold24kSellPerGram = 7800.0,
      gold22kBuyPerGram = 7333.33,
      gold18kBuyPerGram = 6000.0,
      isLive = true,
    )
    val wallet = WalletHoldings(
      availableInr = 25000.0,
      gold24kGrams = 1.5,
    )

    composeRule.setContent {
      SonaWaneGoldTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          GoldTradingPanel(
            wallet = wallet,
            rates = rates,
            gstPercent = 3.0,
            biometricRequired = false,
            hasMpin = false,
            onBuyGold = { purity, grams, _, _, _, _, total, _ ->
              boughtPurity = purity
              boughtGrams = grams
              boughtTotal = total
            },
            onSellGold = { purity, grams, _, _, _, _ ->
              soldPurity = purity
              soldGrams = grams
            },
          )
        }
      }
    }

    composeRule.onNodeWithTag("gold_trading_panel").assertIsDisplayed()
    // Update Weight -> 2.0g should recalculate total = 2 * 8000 * 1.03 = 16480.00
    composeRule.onNodeWithTag("gold_panel_weight_input").performScrollTo().performTextReplacement("2.0")
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("gold_panel_execute_btn").performScrollTo().performClick()
    composeRule.waitForIdle()

    assertEquals("24K", boughtPurity)
    assertEquals(2.0, boughtGrams, 0.001)
    assertEquals(16480.0, boughtTotal, 0.5)

    // Switch to Sell Gold tab, tap Sell Max (1.5g), execute sale
    composeRule.onNodeWithTag("gold_panel_sell_tab").performScrollTo().performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("gold_panel_sell_max").performScrollTo().performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("gold_panel_execute_btn").performScrollTo().performClick()
    composeRule.waitForIdle()

    assertEquals("24K", soldPurity)
    assertEquals(1.5, soldGrams, 0.001)
  }

  @Test
  fun `price alert manager schedules workmanager persists gold and silver targets and posts local notifications`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    if (!WorkManager.isInitialized()) {
      WorkManager.initialize(app, Configuration.Builder().build())
    }

    val prefs = SecurityPreferences(app)
    val manager = PriceAlertManager(app, prefs)

    // Clear any prior targets and configure 24K Gold + 999 Silver targets
    prefs.savePriceAlertTargets(emptyList())
    val goldTarget = manager.setTargetPriceAlert(
      metal = "GOLD_24K",
      targetPriceInr = 8100.0,
      direction = "BELOW",
      frequency = "ONCE",
      notes = "Festival buy",
      id = "pa_test_gold",
    )
    val silverTarget = manager.setTargetPriceAlert(
      metal = "SILVER_999",
      targetPriceInr = 95.0,
      direction = "ABOVE",
      frequency = "DAILY",
      notes = "Breakout watch",
      id = "pa_test_silver",
    )

    val saved = manager.targetsFlow.first()
    assertEquals(2, saved.size)
    assertEquals(setOf(goldTarget.id, silverTarget.id), saved.map { it.id }.toSet())

    // Verify WorkManager periodic job is enqueued
    val infos = WorkManager.getInstance(app)
      .getWorkInfosForUniqueWork(PriceAlertManager.UNIQUE_PERIODIC_WORK_NAME)
      .get()
    assertTrue("Expected periodic PriceAlertCheckWorker enqueued", infos.isNotEmpty())
    assertTrue(
      infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
    )

    // Trigger evaluation with spot rates hitting BOTH gold (< 8100) and silver (> 95)
    val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.cancelAll()

    val batch = manager.checkAllTargetsAndNotify(
      ratesOverride = LiveMarketRates(
        gold24kBuyPerGram = 7990.0,
        silver999BuyPerGram = 97.5,
        isLive = true,
      ),
      playSound = false,
      vibrate = false,
      nowMs = 1_700_000_000_000L,
    )

    assertEquals(2, batch.evaluatedCount)
    assertEquals(2, batch.triggeredCount)
    assertEquals(2, batch.notificationsDispatched)

    val posted = Shadows.shadowOf(nm).allNotifications
    assertEquals(2, posted.size)

    // ONCE gold target should now auto-disable while DAILY silver target remains active
    val afterRun = manager.targetsFlow.first().associateBy { it.id }
    assertFalse(afterRun["pa_test_gold"]!!.enabled)
    assertEquals(1, afterRun["pa_test_gold"]!!.triggerCount)
    assertTrue(afterRun["pa_test_silver"]!!.enabled)
    assertEquals(1, afterRun["pa_test_silver"]!!.triggerCount)
  }

  @Test
  fun `biometric authentication screen secures investment and wallet actions via prompt and mpin fallback`() {
    val promptedScopes = mutableListOf<SensitiveActionScope>()
    var tradeAuthorized = false
    var toggledWalletLock = true

    composeRule.setContent {
      var currentScope by remember { mutableStateOf(SensitiveActionScope.WALLET_VAULT) }
      var isWalletAuth by remember { mutableStateOf(false) }
      var isTradeAuth by remember { mutableStateOf(false) }
      var statusMsg by remember { mutableStateOf<String?>(null) }

      SonaWaneGoldTheme {
        BiometricAuthenticationScreen(
          scope = currentScope,
          actionSummary = when (currentScope) {
            SensitiveActionScope.WALLET_VAULT -> "INR & Bullion Wallet Vault"
            SensitiveActionScope.INVESTMENT_ORDER -> "Buy 2g 24K Gold • ₹16,480.00"
            SensitiveActionScope.PAYOUT_TRANSFER -> "Bank Payout • ₹5,000.00"
          },
          userDisplayName = "Aarav SonaWane",
          biometricCapability = BiometricCapability.STRONG_READY,
          hasDeviceCredentialFallback = true,
          hasMpinFallback = true,
          walletLockEnabled = toggledWalletLock,
          tradeConfirmEnabled = true,
          isWalletAuthorized = isWalletAuth,
          isTradeAuthorized = isTradeAuth,
          statusMessage = statusMsg,
          showTopBar = true,
          allowScopeSwitching = true,
          onSelectScope = {
            currentScope = it
            statusMsg = null
          },
          onAuthenticateBiometric = {
            promptedScopes.add(currentScope)
            if (currentScope == SensitiveActionScope.WALLET_VAULT) {
              isWalletAuth = true
            } else {
              isTradeAuth = true
              tradeAuthorized = true
            }
            statusMsg = "${currentScope.title} authorized via BiometricPrompt"
          },
          onVerifyMpin = { pin -> pin == "1234" },
          onMpinSuccess = {
            isWalletAuth = true
            statusMsg = "Authorized via MPIN"
          },
          onToggleWalletLock = { toggledWalletLock = it },
          onToggleTradeConfirm = {},
        )
      }
    }

    composeRule.onNodeWithTag("biometric_auth_screen").assertIsDisplayed()
    composeRule.onNodeWithText("Wallet Vault Access").assertIsDisplayed()
    composeRule.onNodeWithTag("biometric_auth_prompt_btn").performScrollTo().performClick()
    composeRule.waitForIdle()

    assertEquals(listOf(SensitiveActionScope.WALLET_VAULT), promptedScopes)
    composeRule.onNodeWithTag("biometric_auth_status_banner").performScrollTo().assertIsDisplayed()

    // Switch to Investment Order scope and authorize sensitive bullion investment
    composeRule.onNodeWithTag("biometric_scope_selector_row").performScrollTo()
    composeRule.onNodeWithTag("biometric_scope_chip_investment_order").performScrollTo().performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("Buy 2g 24K Gold • ₹16,480.00").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("biometric_auth_prompt_btn").performScrollTo().performClick()
    composeRule.waitForIdle()

    assertTrue(tradeAuthorized)
    assertEquals(listOf(SensitiveActionScope.WALLET_VAULT, SensitiveActionScope.INVESTMENT_ORDER), promptedScopes)

    // Test MPIN fallback wrong-pin error display
    composeRule.onNodeWithTag("biometric_mpin_fallback_input").performScrollTo().performTextReplacement("9999")
    composeRule.onNodeWithTag("biometric_mpin_fallback_btn").performScrollTo().performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("biometric_auth_error_banner").performScrollTo().assertIsDisplayed()

    // Test policy toggle
    composeRule.onNodeWithTag("biometric_screen_wallet_lock_switch").performScrollTo().performClick()
    composeRule.waitForIdle()
    assertFalse(toggledWalletLock)
  }

  @Test
  fun `price tracking screen renders firestore gold and silver vico charts and supports metal compare and window filters`() {
    var refreshed = false
    val firestoreHistory = (1..10).map { day ->
      val d = day.toString().padStart(2, '0')
      MarketPriceHistoryEntry(
        id = "2026-11-$d",
        dateStr = "2026-11-$d",
        label = "11-$d",
        gold24kPerGram = 8000.0 + day * 45.0,
        gold22kPerGram = (8000.0 + day * 45.0) * (22.0 / 24.0),
        gold18kPerGram = (8000.0 + day * 45.0) * (18.0 / 24.0),
        silver999PerGram = 94.0 + day * 1.1,
        source = "FIRESTORE_SPOT",
        timestampEpochMs = 1_793_500_000_000L + day * 86_400_000L,
      )
    }

    composeRule.setContent {
      SonaWaneGoldTheme {
        PriceTrackingScreen(
          historyEntries = firestoreHistory,
          rates = LiveMarketRates(
            gold24kBuyPerGram = 8450.0,
            silver999BuyPerGram = 105.0,
            isLive = true,
          ),
          onBack = {},
          onRefreshFromFirestore = { refreshed = true },
        )
      }
    }

    composeRule.onNodeWithTag("price_tracking_screen").assertIsDisplayed()
    composeRule.onNodeWithText("FIRESTORE LIVE").assertIsDisplayed()
    composeRule.onNodeWithTag("price_tracking_vico_single_chart").assertIsDisplayed()

    // Switch to 999 Silver single-metal Vico trend
    composeRule.onNodeWithTag("price_tracking_metal_SILVER_999").performScrollTo().performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("999 Fine Silver").assertIsDisplayed()
    composeRule.onNodeWithTag("price_tracking_vico_single_chart").assertIsDisplayed()

    // Switch to normalized Gold vs Silver dual-series Vico comparison chart
    composeRule.onNodeWithTag("price_tracking_metal_COMPARE").performScrollTo().performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("price_tracking_vico_compare_chart").assertIsDisplayed()

    // Switch time window to 7D
    composeRule.onNodeWithTag("price_tracking_window_7D").performScrollTo().performClick()
    composeRule.waitForIdle()
    // Refresh button triggers Firestore sync callback (before scrolling LazyColumn)
    composeRule.onNodeWithTag("price_tracking_refresh_btn").performClick()
    assertTrue(refreshed)

    // Scroll LazyColumn down to verify period analytics heading & Firestore daily log row
    composeRule.onNodeWithTag("price_tracking_screen")
      .performScrollToNode(hasText("7 Days Gold & Silver Analytics"))
    composeRule.onNodeWithText("7 Days Gold & Silver Analytics").assertIsDisplayed()

    composeRule.onNodeWithTag("price_tracking_screen")
      .performScrollToNode(hasTestTag("price_tracking_row_2026-11-10"))
    composeRule.onNodeWithTag("price_tracking_row_2026-11-10").assertIsDisplayed()
  }

  @Test
  fun `workmanager price alert check worker pulls firestore thresholds and prices triggers notifications and updates firestore`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    if (!WorkManager.isInitialized()) {
      WorkManager.initialize(app, Configuration.Builder().build())
    }

    val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.cancelAll()

    val triggeredIds = mutableListOf<String>()
    val observedUpdates = mutableListOf<Pair<String, Double>>()

    val fakeFirestore = object : FirestoreThresholdDataSource {
      override suspend fun fetchSnapshot(): FirestoreThresholdSnapshot {
        return FirestoreThresholdSnapshot(
          authenticatedUid = "uid_worker_test",
          alerts = listOf(
            PriceAlert(
              id = "fs_gold_24k_drop",
              userId = "uid_worker_test",
              metal = "GOLD_24K",
              targetPriceInr = 8000.0,
              direction = "BELOW",
              frequency = "ONCE",
              isActive = true,
              notes = "Dip order",
            ),
            PriceAlert(
              id = "fs_silver_999_rally",
              userId = "uid_worker_test",
              metal = "SILVER_999",
              targetPriceInr = 97.0,
              direction = "ABOVE",
              frequency = "DAILY",
              isActive = true,
              notes = "Silver breakout",
            ),
            PriceAlert(
              id = "fs_gold_22k_unhit",
              userId = "uid_worker_test",
              metal = "GOLD_22K",
              targetPriceInr = 7000.0,
              direction = "BELOW",
              frequency = "ONCE",
              isActive = true,
            ),
          ),
          latestMarketRates = LiveMarketRates(
            gold24kBuyPerGram = 7950.0,
            gold22kBuyPerGram = 7287.5,
            gold18kBuyPerGram = 5962.5,
            silver999BuyPerGram = 98.5,
            sourceName = "Firestore market_price_history",
            isLive = true,
          ),
          priceAlertsEnabled = true,
          soundEnabled = false,
          vibrationEnabled = false,
        )
      }

      override suspend fun onAlertTriggered(
        uid: String,
        alert: PriceAlert,
        outcome: TargetEvaluationOutcome,
      ) {
        assertEquals("uid_worker_test", uid)
        triggeredIds.add(alert.id)
      }

      override suspend fun onAlertObserved(
        uid: String,
        alertId: String,
        observedPriceInr: Double,
      ) {
        assertEquals("uid_worker_test", uid)
        observedUpdates.add(alertId to observedPriceInr)
      }
    }

    val prefs = SecurityPreferences(app)
    prefs.savePriceAlertTargets(emptyList())

    PriceAlertCheckWorker.testManagerFactory = { ctx ->
      PriceAlertManager(
        context = ctx,
        prefs = prefs,
        firestoreSource = fakeFirestore,
      )
    }

    try {
      // 1) Verify immediate WorkManager request enqueueing via PriceAlertManager
      val manager = PriceAlertManager(context = app, prefs = prefs, firestoreSource = fakeFirestore)
      manager.runImmediateCheck(preferFirestoreRates = true)
      val queued = WorkManager.getInstance(app)
        .getWorkInfosForUniqueWork(PriceAlertManager.UNIQUE_IMMEDIATE_WORK_NAME)
        .get()
      assertTrue(queued.isNotEmpty())

      // 2) Execute PriceAlertCheckWorker deterministically via TestListenableWorkerBuilder
      val worker = TestListenableWorkerBuilder<PriceAlertCheckWorker>(app)
        .setInputData(workDataOf(PriceAlertCheckWorker.KEY_PREFER_FIRESTORE_RATES to true))
        .build()
      val result = worker.doWork()

      assertTrue(result is ListenableWorker.Result.Success)
      val output = (result as ListenableWorker.Result.Success).outputData
      assertEquals(3, output.getInt(PriceAlertCheckWorker.KEY_OUT_EVALUATED_COUNT, -1))
      assertEquals(2, output.getInt(PriceAlertCheckWorker.KEY_OUT_TRIGGERED_COUNT, -1))
      assertEquals(2, output.getInt(PriceAlertCheckWorker.KEY_OUT_NOTIFICATIONS_DISPATCHED, -1))
      assertTrue(output.getBoolean(PriceAlertCheckWorker.KEY_OUT_FIRESTORE_SOURCE, false))
      assertEquals(7950.0, output.getDouble(PriceAlertCheckWorker.KEY_OUT_GOLD_24K_INR, 0.0), 0.01)
      assertEquals(98.5, output.getDouble(PriceAlertCheckWorker.KEY_OUT_SILVER_999_INR, 0.0), 0.01)

      // Both 24K Gold and 999 Silver notifications were pushed to NotificationManager
      val posted = Shadows.shadowOf(nm).allNotifications
      assertEquals(2, posted.size)

      // Firestore triggered updates fired for Gold & Silver; observed price synced for unhit 22K Gold
      assertEquals(setOf("fs_gold_24k_drop", "fs_silver_999_rally"), triggeredIds.toSet())
      assertEquals(listOf("fs_gold_22k_unhit" to 7287.5), observedUpdates)
    } finally {
      PriceAlertCheckWorker.testManagerFactory = null
    }
  }

  @Test
  fun `bullion qr module decodes generated 999 silver bitmap via zxing decodeQrFromBitmap`() {
    val envelope = BullionQrModule.generateEncryptedPayload(
      customUserId = "SWG889900",
      displayName = "Kavita Vault",
      requestedAsset = "SILVER_999",
      requestedGrams = 25.0,
    )
    val bitmap = BullionQrModule.generateQrBitmap(envelope.encryptedToken, 420)
    assertNotNull(bitmap)

    val decodedRaw = BullionQrModule.decodeQrFromBitmap(bitmap!!)
    assertEquals(envelope.encryptedToken, decodedRaw)

    val prefill = BullionQrModule.resolveTransferPrefill(decodedRaw!!)
    assertNotNull(prefill)
    assertTrue(prefill!!.isEncryptedVerified)
    assertEquals("SWG889900", prefill.userId)
    assertEquals("SWG-VAULT-SWG889900", prefill.walletAddress)
    assertEquals("SILVER_999", prefill.requestedAsset)
    assertEquals(25.0, prefill.requestedGrams, 0.0001)
  }

  @Test
  fun `my qr screen generates unique wallet qr supports 999 silver and opens scanner`() {
    var scannerOpened = false
    val profile = UserProfile(
      userId = "uid_qr_1",
      customUserId = "SWG445566",
      fullName = "Vikram Bullion",
    )

    composeRule.setContent {
      SonaWaneGoldTheme {
        MyQrScreen(
          profile = profile,
          onOpenScanner = { scannerOpened = true },
          onBack = {},
        )
      }
    }

    composeRule.onNodeWithTag("wallet_address_qr_text").assertIsDisplayed()
    composeRule.onNodeWithText("Wallet Address: SWG-VAULT-SWG445566").assertIsDisplayed()
    composeRule.onNodeWithTag("encrypted_qr_image_box").performScrollTo().assertIsDisplayed()

    // Select 999 Silver & set 5g
    composeRule.onNodeWithText("Gold & Silver Receive Options (Encoded in QR)").performScrollTo()
    composeRule.onNodeWithTag("qr_asset_chip_silver_999").performScrollTo().performClick()
    composeRule.onNodeWithTag("qr_requested_grams_input").performScrollTo().performTextReplacement("5")
    composeRule.waitForIdle()
    val expectedReqText = "Requesting ${com.example.util.Formatters.formatGrams(5.0)} of 999 Silver"
    composeRule.onNodeWithTag("wallet_address_qr_text").performScrollTo()
    composeRule.onNodeWithText(expectedReqText).performScrollTo().assertIsDisplayed()

    // Rotate unique QR nonce
    composeRule.onNodeWithTag("rotate_encrypted_qr_btn").performScrollTo().performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("Generated fresh unique ZXing QR with new IV").performScrollTo().assertIsDisplayed()

    // Navigate to ZXing Scanner screen
    composeRule.onNodeWithTag("open_qr_scanner_btn").performScrollTo().performClick()
    assertTrue(scannerOpened)
  }

  @Test
  fun `qr scanner screen rejects tampered token and verifies encrypted silver p2p transfer token`() {
    var detectedPayload: String? = null
    val validEnv = BullionQrModule.generateEncryptedPayload(
      customUserId = "SWG717171",
      displayName = "Neha Sona",
      requestedAsset = "SILVER_999",
      requestedGrams = 10.0,
    )
    val tamperedChars = validEnv.encryptedToken.toCharArray()
    val idx = tamperedChars.lastIndex - 3
    tamperedChars[idx] = if (tamperedChars[idx] == 'A') 'B' else 'A'
    val tamperedToken = String(tamperedChars)

    composeRule.setContent {
      SonaWaneGoldTheme {
        QrScannerScreen(
          onQrDetected = { detectedPayload = it },
          onBack = {},
        )
      }
    }

    composeRule.onNodeWithTag("scan_qr_gallery_btn").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("manual_qr_input").performScrollTo().performTextReplacement(tamperedToken)
    composeRule.onNodeWithTag("verify_manual_qr_btn").performScrollTo().performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("qr_scan_error_text").performScrollTo().assertIsDisplayed()
    assertEquals(null, detectedPayload)

    // Now supply valid encrypted SILVER_999 token
    composeRule.onNodeWithTag("manual_qr_input").performScrollTo().performTextReplacement(validEnv.encryptedToken)
    composeRule.onNodeWithTag("verify_manual_qr_btn").performScrollTo().performClick()
    composeRule.waitForIdle()

    assertEquals(validEnv.encryptedToken, detectedPayload)
  }

  @Test
  fun `investment wallet screen enforces biometric gate and unlocks on successful authentication`() {
    var unlocked by mutableStateOf(false)
    var bioShouldSucceed by mutableStateOf(false)
    var bioPromptCount = 0
    val profile = UserProfile(
      userId = "uid_wallet_bio",
      customUserId = "SWG303030",
      fullName = "Aarav Investor",
      mpinHash = "hashed_1234",
      biometricEnabled = true,
    )

    composeRule.setContent {
      SonaWaneGoldTheme {
        WalletScreen(
          profile = profile,
          wallet = WalletHoldings(availableInr = 25000.0, gold24kGrams = 3.0),
          ledger = emptyList(),
          transactions = emptyList(),
          bankAccounts = emptyList(),
          rates = LiveMarketRates(gold24kBuyPerGram = 8000.0, gold24kSellPerGram = 7800.0),
          appConfig = com.example.data.model.AppConfig(),
          isBusy = false,
          walletUnlocked = unlocked,
          walletBiometricLock = true,
          biometricAppAccess = true,
          biometricCapabilityOverride = BiometricCapability.STRONG_READY,
          onUnlockWalletWithBiometric = { onOk, onErr ->
            bioPromptCount++
            if (bioShouldSucceed) {
              unlocked = true
              onOk()
            } else {
              onErr("Tap Biometric Unlock or use MPIN")
            }
          },
          onUnlockWalletWithMpin = { unlocked = true },
          onDeposit = { _, _, _ -> },
          onWithdraw = { _, _ -> },
          onVerifyMpin = { it == "1234" },
          onSetupMpin = {},
          onOpenBankAccounts = {},
          onOpenTransfer = {},
          onOpenFullHistory = {},
          onViewReceipt = {},
        )
      }
    }

    composeRule.onNodeWithTag("wallet_biometric_gate_card").assertIsDisplayed()
    // Tapping biometric unlock button while biometric prompt is cancelled displays error feedback
    composeRule.onNodeWithTag("wallet_biometric_unlock_btn").performScrollTo().performClick()
    composeRule.waitForIdle()
    assertFalse(unlocked)
    assertTrue(bioPromptCount >= 1)

    // Unlock via MPIN fallback while biometric prompt is cancelled
    composeRule.onNodeWithTag("wallet_mpin_unlock_input").performScrollTo().performTextReplacement("1234")
    composeRule.onNodeWithTag("wallet_mpin_unlock_btn").performScrollTo().performClick()
    composeRule.waitForIdle()

    assertTrue(unlocked)
    composeRule.onNodeWithText("Digital Bullion Wallet").assertIsDisplayed()

    // Re-lock with biometric succeeding -> autoPromptOnLaunch automatically unlocks wallet
    val beforeRelockCount = bioPromptCount
    unlocked = false
    bioShouldSucceed = true
    composeRule.waitForIdle()
    composeRule.mainClock.advanceTimeBy(300L)
    composeRule.waitForIdle()

    assertTrue(unlocked)
    assertTrue(bioPromptCount > beforeRelockCount)
    composeRule.onNodeWithText("Digital Bullion Wallet").assertIsDisplayed()
  }

  @Test
  fun `mpin lock screen offers fast biometric login and displays biometric errors`() {
    var biometricClickedCount = 0

    composeRule.setContent {
      SonaWaneGoldTheme {
        MpinLockScreen(
          title = "Unlock SONA-WANE GOLD",
          subtitle = "Enter your 4-digit MPIN or use Biometrics to access your vault.",
          isSetupMode = false,
          showBiometricButton = true,
          biometricErrorMessage = "Sensor busy, tap again or enter MPIN.",
          onBiometricClick = { biometricClickedCount++ },
          onPinSubmitted = { _, _ -> },
        )
      }
    }

    composeRule.onNodeWithText("BIOMETRIC FAST-LOGIN READY").assertIsDisplayed()
    composeRule.onNodeWithTag("mpin_lock_error_text").assertIsDisplayed()
    composeRule.onNodeWithTag("mpin_biometric_unlock_button").performScrollTo().performClick()
    composeRule.waitForIdle()

    assertTrue(biometricClickedCount >= 1)
  }

  @Test
  fun `portfolio summary widget displays combined gold and silver holdings value on dashboard`() {
    var buyGoldClicked = false
    var buySilverClicked = false
    val wallet = WalletHoldings(
      availableInr = 5000.0,
      gold24kGrams = 2.0,
      gold22kGrams = 1.0,
      gold18kGrams = 0.5,
      silver999Grams = 10.0,
      totalInvestedGoldInr = 25000.0,
      totalInvestedSilverInr = 850.0,
    )
    val fsGold = FirestoreGoldSummary(
      gold24kPerGram = 8000.0,
      gold22kPerGram = 7400.0,
      gold18kPerGram = 6000.0,
      isLoading = false,
      isFromFirestore = true,
    )
    val rates = LiveMarketRates(silver999SellPerGram = 95.0)

    composeRule.setContent {
      SonaWaneGoldTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          PortfolioSummaryWidget(
            wallet = wallet,
            firestoreGoldSummary = fsGold,
            fallbackRates = rates,
            onBuyGold = { buyGoldClicked = true },
            onBuySilver = { buySilverClicked = true },
          )
        }
      }
    }

    composeRule.waitForIdle()
    composeRule.onNodeWithTag("portfolio_summary_widget").assertIsDisplayed()
    composeRule.onNodeWithTag("portfolio_summary_total_value").assertTextContains("27,350", substring = true)
    composeRule.onNodeWithTag("portfolio_summary_gold_value").assertTextContains("26,400", substring = true)
    composeRule.onNodeWithTag("portfolio_summary_silver_value").assertTextContains("950", substring = true)

    composeRule.onNodeWithTag("portfolio_widget_buy_gold_btn").performScrollTo().performClick()
    composeRule.onNodeWithTag("portfolio_widget_buy_silver_btn").performScrollTo().performClick()
    composeRule.waitForIdle()

    assertTrue(buyGoldClicked)
    assertTrue(buySilverClicked)
  }

  @Test
  fun `alerts screen manages workmanager worker settings and price thresholds`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    if (!WorkManager.isInitialized()) {
      WorkManager.initialize(app, Configuration.Builder().build())
    }
    val prefs = SecurityPreferences(app)
    prefs.setPriceAlertWorkerConfig(intervalMinutes = 30L, preferFirestoreRates = false, masterAlertsEnabled = true)
    val persistedCfg = prefs.priceAlertWorkerConfigFlow.first()
    assertEquals(30L, persistedCfg.intervalMinutes)
    assertFalse(persistedCfg.preferFirestoreRates)
    assertTrue(persistedCfg.masterAlertsEnabled)

    var latestWorkerCfg by mutableStateOf(PriceAlertWorkerConfig(15L, true, true))
    var manualWorkerChecked = false
    var createdAlert: Triple<String, Double, String>? = null
    var toggledAlertId: Pair<String, Boolean>? = null
    var deletedAlertId: String? = null
    var bgMonitorConfigured: Pair<Double, String>? = null

    val savedAlerts = listOf(
      PriceAlert(
        id = "alert_gold_1",
        userId = "uid_alerts",
        metal = "GOLD_24K",
        targetPriceInr = 7900.0,
        direction = "BELOW",
        frequency = "ONCE",
        isActive = true,
        notes = "Buy dip",
      ),
    )

    composeRule.setContent {
      SonaWaneGoldTheme {
        AlertsScreen(
          profile = UserProfile(userId = "uid_alerts", notifPriceAlerts = true, notifSoundEnabled = true, notifVibrationEnabled = true),
          rates = LiveMarketRates(
            gold24kBuyPerGram = 8100.0,
            gold22kBuyPerGram = 7425.0,
            gold18kBuyPerGram = 6075.0,
            silver999BuyPerGram = 98.0,
            isLive = true,
          ),
          alerts = savedAlerts,
          bgDropMonitor = BgDropMonitorState(enabled = true, purity = "GOLD_24K", threshold24kInr = 7950.0),
          workerConfig = latestWorkerCfg,
          onBack = {},
          onRefreshRates = {},
          onCheckAlertsNow = { manualWorkerChecked = true },
          onUpdateWorkerConfig = { interval, preferFs, master ->
            latestWorkerCfg = PriceAlertWorkerConfig(interval, preferFs, master)
          },
          onConfigureBgDropMonitor = { _, threshold, purity ->
            bgMonitorConfigured = threshold to purity
          },
          onCreateAlert = { metal, target, dir, _, _ ->
            createdAlert = Triple(metal, target, dir)
          },
          onUpdateAlert = { _, _, _, _, _, _ -> },
          onToggleAlert = { alert, active -> toggledAlertId = alert.id to active },
          onDeleteAlert = { deletedAlertId = it },
        )
      }
    }

    composeRule.waitForIdle()
    composeRule.onNodeWithTag("alerts_screen").assertIsDisplayed()
    composeRule.onNodeWithTag("alerts_worker_settings_card").assertIsDisplayed()

    // Change WorkManager interval to 60m & trigger immediate worker check
    composeRule.onNodeWithTag("alerts_interval_60").performClick()
    composeRule.waitForIdle()
    assertEquals(60L, latestWorkerCfg.intervalMinutes)

    composeRule.onNodeWithTag("alerts_screen").performScrollToNode(hasTestTag("alerts_run_worker_now_btn"))
    composeRule.onNodeWithTag("alerts_run_worker_now_btn").performClick()
    assertTrue(manualWorkerChecked)

    // Configure quick Gold Dip Monitor threshold
    composeRule.onNodeWithTag("alerts_screen").performScrollToNode(hasTestTag("alerts_bg_drop_threshold_input"))
    composeRule.onNodeWithTag("alerts_bg_drop_threshold_input").performTextReplacement("7880")
    composeRule.onNodeWithTag("alerts_screen").performScrollToNode(hasTestTag("alerts_bg_drop_save_btn"))
    composeRule.onNodeWithTag("alerts_bg_drop_save_btn").performClick()
    assertEquals(7880.0 to "GOLD_24K", bgMonitorConfigured)

    // Create a 999 Silver threshold above 105
    composeRule.onNodeWithTag("alerts_screen").performScrollToNode(hasTestTag("alerts_metal_SILVER_999"))
    composeRule.onNodeWithTag("alerts_metal_SILVER_999").performClick()
    composeRule.onNodeWithTag("alerts_screen").performScrollToNode(hasTestTag("alerts_target_price_input"))
    composeRule.onNodeWithTag("alerts_target_price_input").performTextReplacement("105.00")
    composeRule.onNodeWithTag("alerts_screen").performScrollToNode(hasTestTag("alerts_dir_ABOVE"))
    composeRule.onNodeWithTag("alerts_dir_ABOVE").performClick()
    composeRule.onNodeWithTag("alerts_screen").performScrollToNode(hasTestTag("alerts_create_threshold_btn"))
    composeRule.onNodeWithTag("alerts_create_threshold_btn").performClick()
    assertEquals(Triple("SILVER_999", 105.0, "ABOVE"), createdAlert)

    // Toggle & delete saved threshold item
    composeRule.onNodeWithTag("alerts_screen").performScrollToNode(hasTestTag("alerts_toggle_alert_gold_1"))
    composeRule.onNodeWithTag("alerts_toggle_alert_gold_1").performClick()
    assertEquals("alert_gold_1" to false, toggledAlertId)

    composeRule.onNodeWithTag("alerts_screen").performScrollToNode(hasTestTag("alerts_delete_alert_gold_1"))
    composeRule.onNodeWithTag("alerts_delete_alert_gold_1").performClick()
    assertEquals("alert_gold_1", deletedAlertId)
  }

  @Test
  fun `firestore service resolves named database id from app resources`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals(
      "ai-studio-android-sonawane-3bad5ba9-1ba6-446a-acfb-58b1d8b514a7",
      FirestoreService.resolveDatabaseId(context),
    )
  }

  @Test
  fun `admin login screen displays Access Denied with Logout and stays open without auto closing`() {
    var loggedOut = false
    var returnedToCustomer = false

    composeRule.setContent {
      SonaWaneGoldTheme {
        AdminLoginScreen(
          currentEmail = "unauthorized@example.com",
          eligibilityChecked = true,
          adminUser = null,
          rbacState = RbacCheckResult.AccessDenied(
            reason = "Your administrator account is currently inactive (isActive = false).",
            email = "unauthorized@example.com",
          ),
          isBusy = false,
          authErrorMessage = null,
          adminRateLimit = 0 to 0L,
          onGoogleVerified = {},
          onEnrollCredentials = { _, _, _, _ -> },
          onAuthenticateCredentials = { _, _ -> },
          onLaunchBiometricAdmin = { _, _ -> },
          onBiometricAdminUnlocked = {},
          onClearAuthError = {},
          onSignOutAdmin = { loggedOut = true },
          onReturnToCustomerFlow = { returnedToCustomer = true },
        )
      }
    }

    composeRule.waitForIdle()
    composeRule.onNodeWithTag("admin_access_denied_title").assertIsDisplayed()
    composeRule.onNodeWithText("Access Denied").assertIsDisplayed()
    composeRule.onNodeWithText("Signed in as: unauthorized@example.com").assertIsDisplayed()
    assertFalse("Must NOT auto-return to customer flow", returnedToCustomer)
    assertFalse("Must NOT auto-logout", loggedOut)

    composeRule.onNodeWithTag("admin_unauthorized_sign_out_button").performScrollTo().performClick()
    assertTrue(loggedOut)
  }

  @Test
  fun `admin login screen displays RBAC Firestore permission error cleanly with retry and logout`() {
    var retried = false
    var loggedOut = false

    composeRule.setContent {
      SonaWaneGoldTheme {
        AdminLoginScreen(
          currentEmail = "goldsonawane@gmail.com",
          eligibilityChecked = true,
          adminUser = null,
          rbacState = RbacCheckResult.Error(
            message = "Firestore permission denied while verifying RBAC access (PERMISSION_DENIED).",
            isPermissionDenied = true,
          ),
          isBusy = false,
          authErrorMessage = null,
          adminRateLimit = 0 to 0L,
          onGoogleVerified = {},
          onRetryRbacCheck = { retried = true },
          onEnrollCredentials = { _, _, _, _ -> },
          onAuthenticateCredentials = { _, _ -> },
          onLaunchBiometricAdmin = { _, _ -> },
          onBiometricAdminUnlocked = {},
          onClearAuthError = {},
          onSignOutAdmin = { loggedOut = true },
          onReturnToCustomerFlow = {},
        )
      }
    }

    composeRule.waitForIdle()
    composeRule.onNodeWithTag("admin_rbac_error_title").assertIsDisplayed()
    composeRule.onNodeWithText("Permission Denied").assertIsDisplayed()
    composeRule.onNodeWithTag("admin_rbac_retry_button").performScrollTo().performClick()
    assertTrue(retried)
    composeRule.onNodeWithTag("admin_rbac_error_logout_button").performScrollTo().performClick()
    assertTrue(loggedOut)
  }
}
