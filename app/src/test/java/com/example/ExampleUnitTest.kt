package com.example

import com.example.data.model.AppConfig
import com.example.data.model.AuditLog
import com.example.data.model.DualMetalTrendSeries
import com.example.data.model.FirestoreGoldSummary
import com.example.data.model.LiveMarketRates
import com.example.data.model.MarketPoint
import com.example.data.model.MarketPriceHistoryEntry
import com.example.data.model.TransactionRecord
import com.example.data.model.WalletHoldings
import com.example.data.local.PriceAlertTarget
import com.example.data.remote.MarketRateService
import com.example.data.repository.AdminRepository
import com.example.data.repository.AuditLogManager
import com.example.data.repository.FirestoreService
import com.example.data.repository.SonaWaneRepository
import com.example.service.GoldPriceMonitorService
import com.example.service.PriceAlertManager
import com.example.ui.components.GoldTradeInputFocus
import com.example.ui.components.GoldTradeSide
import com.example.ui.components.GoldTradingPanelCalculator
import com.example.ui.components.KycStatusResolver
import com.example.ui.components.KycVisualTier
import com.example.ui.components.MarketTrendsApiSeriesBuilder
import com.example.ui.components.PortfolioSummaryCalculator
import com.example.ui.components.PortfolioValuationCalculator
import com.example.ui.components.TradeStatusResolver
import com.example.ui.components.TradeStatusTier
import com.example.ui.components.computeGoldHistoryStats
import com.example.ui.screens.GoldAmountBasis
import com.example.ui.screens.GoldCalculatorInputMode
import com.example.ui.screens.GoldPurchaseCalculator
import com.example.ui.screens.GoldPurchaseFormat
import com.example.ui.screens.PriceTrackingCalculator
import com.example.ui.screens.PriceTrackingMetal
import com.example.ui.screens.PriceTrackingWindow
import com.example.util.BiometricAuthManager
import com.example.util.BiometricCapability
import com.example.util.BiometricLoginManager
import com.example.util.BullionQrModule
import com.example.util.Formatters
import com.example.util.ProtectedFeature
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun dualMetalTrend_liveTickUpdatesHighLowAvgAndNormalizedPct() {
    val service = MarketRateService()
    val seed = DualMetalTrendSeries(
      goldPoints = listOf(
        MarketPoint("10-15", 7500.0),
        MarketPoint("10-30", 7650.0),
        MarketPoint("LIVE", 7700.0),
      ),
      silverPoints = listOf(
        MarketPoint("10-15", 90.0),
        MarketPoint("10-30", 92.0),
        MarketPoint("LIVE", 93.0),
      ),
      rangeKey = "30D",
    )

    val updated = service.updateDualSeriesWithLiveTick(
      existing = seed,
      goldSpot = 7800.0,
      silverSpot = 94.5,
    )

    assertEquals(3, updated.goldPoints.size)
    assertTrue(updated.goldPoints.last().label.startsWith("LIVE "))
    assertEquals(7800.0, updated.gold30dHigh, 0.01)
    assertEquals(7500.0, updated.gold30dLow, 0.01)
    assertEquals(4.0, updated.gold30dChangePct, 0.01)
    assertEquals(0.0, updated.goldNormalizedPct.first().price, 0.01)
    assertEquals(4.0, updated.goldNormalizedPct.last().price, 0.01)

    assertEquals(94.5, updated.silver30dHigh, 0.01)
    assertEquals(90.0, updated.silver30dLow, 0.01)
    assertEquals(5.0, updated.silver30dChangePct, 0.01)
  }

  @Test
  fun bullionQrModule_encryptsUniquelyAndDetectsTampering() {
    val env1 = BullionQrModule.generateEncryptedPayload("SWG904210", "Aarav SonaWane", 1_700_000_000L)
    val env2 = BullionQrModule.generateEncryptedPayload("SWG904210", "Aarav SonaWane", 1_700_000_000L)

    assertTrue(env1.encryptedToken.startsWith("SWGQR2."))
    assertTrue(env2.encryptedToken.startsWith("SWGQR2."))
    assertTrue("Token must fit Firestore rule <= 200 chars", env1.encryptedToken.length <= 200)
    assertNotEquals("Unique IV/nonce must create distinct QR tokens", env1.encryptedToken, env2.encryptedToken)
    assertNotEquals(env1.nonce, env2.nonce)

    val decrypted = BullionQrModule.decryptQrPayload(env1.encryptedToken)
    assertNotNull(decrypted)
    assertEquals("SWG904210", decrypted!!.userId)
    assertEquals("Aarav SonaWane", decrypted.displayName)
    assertEquals(env1.nonce, decrypted.nonce)
    assertEquals("SWG904210", BullionQrModule.resolveUserIdOrEmpty(env1.encryptedToken))

    // Mutate one character in the ciphertext/tag region and ensure GCM verification fails
    val chars = env1.encryptedToken.toCharArray()
    val tamperIndex = chars.lastIndex - 2
    chars[tamperIndex] = if (chars[tamperIndex] == 'A') 'B' else 'A'
    val tampered = String(chars)
    assertNull(BullionQrModule.decryptQrPayload(tampered))
    assertEquals("", BullionQrModule.resolveUserIdOrEmpty(tampered))

    // Legacy deep link and plain user ID still resolve cleanly
    assertEquals("SWG112233", BullionQrModule.resolveUserIdOrEmpty("sonawanegold://transfer?id=SWG112233"))
    assertEquals("SWG112233", BullionQrModule.resolveUserIdOrEmpty("SWG112233"))
  }

  @Test
  fun marketRateService_builds30DayFirestoreSnapshots() = kotlinx.coroutines.runBlocking {
    val service = MarketRateService()
    val entries = service.build30DayHistorySnapshots(goldCurrentPerGram = 7850.0, silverCurrentPerGram = 94.5)

    assertEquals(30, entries.size)
    assertTrue(entries.all { it.dateStr.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")) })
    assertTrue(entries.all { it.gold24kPerGram > 0.0 && it.gold22kPerGram > 0.0 && it.gold18kPerGram > 0.0 && it.silver999PerGram > 0.0 })
    assertEquals(7850.0, entries.last().gold24kPerGram, 0.01)
    assertEquals(94.5, entries.last().silver999PerGram, 0.01)
    for (i in 1 until entries.size) {
      assertTrue(entries[i].timestampEpochMs > entries[i - 1].timestampEpochMs)
    }
  }

  @Test
  fun auditLogManager_buildsConfigDiff_classifiesActions_andFormatsComplianceCsv() {
    val prev = AppConfig(maintenanceMode = false, paymentMode = "TEST", gstPercent = 3.0, goldSpreadPercent = 1.5)
    val next = prev.copy(maintenanceMode = true, paymentMode = "LIVE", gstPercent = 3.25)

    val (prevDiff, nextDiff) = AuditLogManager.buildConfigDiff(prev, next)
    assertTrue(prevDiff.contains("maint=false"))
    assertTrue(prevDiff.contains("mode=TEST"))
    assertTrue(prevDiff.contains("gst=3.0"))
    assertFalse("Unchanged field should not appear in diff", prevDiff.contains("goldSpread"))
    assertTrue(nextDiff.contains("maint=true"))
    assertTrue(nextDiff.contains("mode=LIVE"))
    assertTrue(nextDiff.contains("gst=3.25"))

    assertEquals(AuditLogManager.AuditCategory.KYC_USERS, AuditLogManager.classifyAction("KYC_APPROVED"))
    assertEquals(AuditLogManager.AuditCategory.ROLES_AUTH, AuditLogManager.classifyAction("UPSERT_ADMIN_ROLE"))
    assertEquals(AuditLogManager.AuditCategory.CONFIG, AuditLogManager.classifyAction("UPDATE_APP_CONFIG"))
    assertEquals(AuditLogManager.AuditCategory.FINANCE, AuditLogManager.classifyAction("WITHDRAWAL_SUCCESSFUL"))

    val sanitized = AuditLogManager.sanitizeField("  Line 1\nLine 2\t  ", maxLength = 50, fallback = "NONE")
    assertEquals("Line 1 Line 2", sanitized)

    val csv = AuditLogManager.buildAuditComplianceCsv(
      listOf(
        AuditLog(
          id = "aud_123",
          adminUid = "adm_1",
          adminEmail = "goldsonawane@gmail.com",
          adminRole = "SUPER_ADMIN",
          action = "UPDATE_APP_CONFIG",
          affectedCollection = "app_config",
          affectedRecordId = "settings",
          previousValue = "mode=TEST, maint=false",
          newValue = "mode=LIVE, maint=true",
          reason = "Production cutover, phase \"A\"",
          deviceInfo = "Pixel 8 [SDK 34] [SWG-ADM-ROOT]",
        )
      )
    )
    assertTrue(csv.startsWith("LogId,Timestamp,AdminUid,AdminEmail,AdminRole,Action,AffectedCollection,AffectedRecordId,PreviousValue,NewValue,Reason,DeviceInfo\n"))
    assertTrue(csv.contains("\"mode=TEST, maint=false\""))
    assertTrue(csv.contains("\"Production cutover, phase \"\"A\"\"\""))
  }

  @Test
  fun priceSummary_buildGoldSummaryFromEntries_computes24k22k18kAndDelta() {
    val empty = SonaWaneRepository.buildGoldSummaryFromEntries(emptyList())
    assertFalse(empty.isFromFirestore)
    assertFalse(empty.isLoading)
    assertEquals(0.0, empty.gold24kPerGram, 0.01)

    val prev = MarketPriceHistoryEntry(
      id = "2026-05-01",
      dateStr = "2026-05-01",
      gold24kPerGram = 8000.0,
      gold22kPerGram = 7333.33,
      gold18kPerGram = 6000.0,
      silver999PerGram = 95.0,
      source = "TEST_SPOT",
      timestampEpochMs = 1_777_600_000_000L,
    )
    val latest = MarketPriceHistoryEntry(
      id = "2026-05-02",
      dateStr = "2026-05-02",
      gold24kPerGram = 8200.0,
      gold22kPerGram = 7516.67,
      gold18kPerGram = 6150.0,
      silver999PerGram = 96.5,
      source = "FIRESTORE_SPOT",
      timestampEpochMs = 1_777_686_400_000L,
    )

    val summary = SonaWaneRepository.buildGoldSummaryFromEntries(listOf(prev, latest))
    assertTrue(summary.isFromFirestore)
    assertFalse(summary.isLoading)
    assertEquals(8200.0, summary.gold24kPerGram, 0.01)
    assertEquals(7516.67, summary.gold22kPerGram, 0.01)
    assertEquals(6150.0, summary.gold18kPerGram, 0.01)
    assertEquals(8000.0, summary.previous24kPerGram, 0.01)
    assertEquals(2.5, summary.changePercent, 0.01)
    assertEquals("2026-05-02", summary.dateStr)
    assertEquals("FIRESTORE_SPOT", summary.source)

    // Derived 22K/18K fallback when omitted in snapshot
    val derived = SonaWaneRepository.buildGoldSummaryFromEntries(
      listOf(
        MarketPriceHistoryEntry(
          id = "2026-05-03",
          dateStr = "2026-05-03",
          gold24kPerGram = 9600.0,
          gold22kPerGram = 0.0,
          gold18kPerGram = 0.0,
          silver999PerGram = 100.0,
          timestampEpochMs = 1_777_772_800_000L,
        )
      )
    )
    assertEquals(8800.0, derived.gold22kPerGram, 0.01)
    assertEquals(7200.0, derived.gold18kPerGram, 0.01)
    assertEquals(0.0, derived.changePercent, 0.01)
  }

  @Test
  fun goldPurchaseCalculator_computesWeightAmountTaxesAndChargesAccurately() {
    // By weight: 2g @ 8000/g digital vault (0% charge) + 3% GST = 16000 + 480 = 16480
    val byWeightDigital = GoldPurchaseCalculator.calculateQuote(
      purity = "24K",
      ratePerGram = 8000.0,
      rawInput = 2.0,
      inputMode = GoldCalculatorInputMode.BY_WEIGHT,
      amountBasis = GoldAmountBasis.TOTAL_BUDGET,
      format = GoldPurchaseFormat.DIGITAL_VAULT,
      gstPercent = 3.0,
    )
    assertEquals(2.0, byWeightDigital.grams, 0.0001)
    assertEquals(16000.0, byWeightDigital.baseAmountInr, 0.01)
    assertEquals(0.0, byWeightDigital.chargesAmountInr, 0.01)
    assertEquals(480.0, byWeightDigital.gstAmountInr, 0.01)
    assertEquals(16480.0, byWeightDigital.totalPayableInr, 0.01)

    // By weight: 2g @ 8000/g minted bar (1.5% minting charge) + 3% GST on (base + charges)
    val byWeightMint = GoldPurchaseCalculator.calculateQuote(
      purity = "22K",
      ratePerGram = 8000.0,
      rawInput = 2.0,
      inputMode = GoldCalculatorInputMode.BY_WEIGHT,
      amountBasis = GoldAmountBasis.TOTAL_BUDGET,
      format = GoldPurchaseFormat.MINTED_BAR,
      gstPercent = 3.0,
    )
    assertEquals(16000.0, byWeightMint.baseAmountInr, 0.01)
    assertEquals(240.0, byWeightMint.chargesAmountInr, 0.01)
    assertEquals(487.20, byWeightMint.gstAmountInr, 0.01)
    assertEquals(16727.20, byWeightMint.totalPayableInr, 0.01)

    // By total budget inclusive of GST: 10300 total -> base 10000, GST 300, grams 1.25
    val byTotalBudget = GoldPurchaseCalculator.calculateQuote(
      purity = "24K",
      ratePerGram = 8000.0,
      rawInput = 10300.0,
      inputMode = GoldCalculatorInputMode.BY_AMOUNT,
      amountBasis = GoldAmountBasis.TOTAL_BUDGET,
      format = GoldPurchaseFormat.DIGITAL_VAULT,
      gstPercent = 3.0,
    )
    assertEquals(1.25, byTotalBudget.grams, 0.0001)
    assertEquals(10000.0, byTotalBudget.baseAmountInr, 0.01)
    assertEquals(300.0, byTotalBudget.gstAmountInr, 0.01)
    assertEquals(10300.0, byTotalBudget.totalPayableInr, 0.01)
  }

  @Test
  fun goldPriceHistoryStats_computes30DayHighLowAvgAndChange() {
    val stats = computeGoldHistoryStats(listOf(7800.0, 7950.0, 8190.0))
    assertEquals(8190.0, stats.latest, 0.01)
    assertEquals(8190.0, stats.high, 0.01)
    assertEquals(7800.0, stats.low, 0.01)
    assertEquals(7980.0, stats.avg, 0.01)
    assertEquals(5.0, stats.changePct, 0.01)
    assertTrue(stats.minY < stats.low)
    assertTrue(stats.maxY > stats.high)
  }

  @Test
  fun portfolioSummaryCard_computesGoldGramsAndLocalCurrencyValuationFromFirestoreRates() {
    val wallet = WalletHoldings(
      gold24kGrams = 2.0,
      gold22kGrams = 1.0,
      gold18kGrams = 0.5,
      silver999Grams = 10.0,
      totalInvestedGoldInr = 25000.0,
    )
    val fsSummary = FirestoreGoldSummary(
      gold24kPerGram = 8000.0,
      gold22kPerGram = 7350.0,
      gold18kPerGram = 6000.0,
      isLoading = false,
      isFromFirestore = true,
      dateStr = "2026-11-15",
    )
    val liveRates = LiveMarketRates(
      silver999SellPerGram = 95.0,
      usdToInr = 85.0,
    )

    val valuation = PortfolioValuationCalculator.compute(wallet, fsSummary, liveRates)
    assertEquals(3.5, valuation.totalGoldGrams, 0.0001)
    // 2 * 8000 + 1 * 7350 + 0.5 * 6000 = 16000 + 7350 + 3000 = 26350
    assertEquals(26350.0, valuation.totalGoldMarketValueInr, 0.01)
    assertEquals(1350.0, valuation.goldProfitLossInr, 0.01)
    assertEquals(5.4, valuation.goldPnlPercent, 0.05)
    assertEquals(950.0, valuation.silverMarketValueInr, 0.01)
    assertEquals(27300.0, valuation.combinedPortfolioValueInr, 0.01)
    assertTrue(valuation.isFromFirestore)

    // Local currency conversion check (INR 1:1 and US USD conversion)
    val inrFormatted = Formatters.formatLocalCurrency(26350.0, usdToInr = 85.0, locale = Locale("en", "IN"))
    assertTrue(inrFormatted.contains("26,350"))

    val usdConverted = Formatters.convertInrToLocalCurrency(26350.0, usdToInr = 85.0, locale = Locale.US)
    assertEquals(310.0, usdConverted, 0.01)
    val usdFormatted = Formatters.formatLocalCurrency(26350.0, usdToInr = 85.0, locale = Locale.US)
    assertTrue(usdFormatted.contains("310.00"))
  }

  @Test
  fun goldPriceMonitorService_evaluatesDropsCooldownsAndPurityRates() {
    val liveRates = LiveMarketRates(
      gold24kBuyPerGram = 7850.0,
      gold22kBuyPerGram = 7195.0,
      gold18kBuyPerGram = 5887.5,
      isLive = true,
    )
    val fsSummary = FirestoreGoldSummary(
      gold24kPerGram = 7800.0,
      gold22kPerGram = 7150.0,
      gold18kPerGram = 5850.0,
      isFromFirestore = true,
    )

    assertEquals(7850.0, GoldPriceMonitorService.resolveMonitoredGoldRate("GOLD_24K", liveRates, fsSummary, preferFirestore = false), 0.01)
    assertEquals(7150.0, GoldPriceMonitorService.resolveMonitoredGoldRate("GOLD_22K", liveRates, fsSummary, preferFirestore = true), 0.01)

    // Above threshold -> MONITORING_OK
    val ok = GoldPriceMonitorService.evaluateThresholdDrop(
      purity = "GOLD_24K",
      liveRateInr = 8050.0,
      thresholdInr = 8000.0,
      enabled = true,
      lastNotifiedEpochMs = 0L,
      nowMs = 1_700_000_000_000L,
    )
    assertFalse(ok.droppedBelowThreshold)
    assertFalse(ok.shouldNotify)
    assertEquals("MONITORING_OK", ok.status)

    // Drops below threshold -> DROP_TRIGGERED
    val triggered = GoldPriceMonitorService.evaluateThresholdDrop(
      purity = "GOLD_24K",
      liveRateInr = 7840.0,
      thresholdInr = 8000.0,
      enabled = true,
      lastNotifiedEpochMs = 0L,
      nowMs = 1_700_000_000_000L,
    )
    assertTrue(triggered.droppedBelowThreshold)
    assertTrue(triggered.shouldNotify)
    assertEquals("DROP_TRIGGERED", triggered.status)
    assertEquals(160.0, triggered.dropAmountInr, 0.01)
    assertEquals(2.0, triggered.dropPercent, 0.01)
    assertTrue(triggered.alertTitle.contains("24K Gold Drop Alert"))
    assertTrue(triggered.alertMessage.contains("Settings threshold"))

    // Within cooldown -> COOLDOWN_ACTIVE
    val cooldown = GoldPriceMonitorService.evaluateThresholdDrop(
      purity = "GOLD_24K",
      liveRateInr = 7820.0,
      thresholdInr = 8000.0,
      enabled = true,
      lastNotifiedEpochMs = 1_700_000_000_000L,
      nowMs = 1_700_000_600_000L, // +10 min < 30 min cooldown
    )
    assertTrue(cooldown.droppedBelowThreshold)
    assertFalse(cooldown.shouldNotify)
    assertEquals("COOLDOWN_ACTIVE", cooldown.status)
  }

  @Test
  fun biometricAuthManager_evaluatesBackgroundAutoLockPolicy() {
    val bgTime = 1_700_000_000_000L

    // Should NOT lock if prompt itself triggered ON_STOP/ON_RESUME
    assertFalse(
      BiometricAuthManager.shouldAutoLockAfterBackground(
        requiresAppLock = true,
        isCurrentlyUnlocked = true,
        lastBackgroundedAtMs = bgTime,
        resumedAtMs = bgTime + 30_000L,
        biometricPromptInFlight = true,
      )
    )

    // Should NOT lock within grace period (< 15s)
    assertFalse(
      BiometricAuthManager.shouldAutoLockAfterBackground(
        requiresAppLock = true,
        isCurrentlyUnlocked = true,
        lastBackgroundedAtMs = bgTime,
        resumedAtMs = bgTime + 5_000L,
        biometricPromptInFlight = false,
      )
    )

    // SHOULD lock once background elapsed >= 15s when app lock is active
    assertTrue(
      BiometricAuthManager.shouldAutoLockAfterBackground(
        requiresAppLock = true,
        isCurrentlyUnlocked = true,
        lastBackgroundedAtMs = bgTime,
        resumedAtMs = bgTime + 20_000L,
        biometricPromptInFlight = false,
      )
    )

    // Should NOT lock when user has neither MPIN nor biometric app access enabled
    assertFalse(
      BiometricAuthManager.shouldAutoLockAfterBackground(
        requiresAppLock = false,
        isCurrentlyUnlocked = true,
        lastBackgroundedAtMs = bgTime,
        resumedAtMs = bgTime + 60_000L,
        biometricPromptInFlight = false,
      )
    )
  }

  @Test
  fun bullionQrModule_walletAddressAndGoldRequest_encodesAndPrefillsCleanly() {
    assertEquals("SWG-VAULT-SWG904210", BullionQrModule.formatWalletAddress("swg904210"))
    assertEquals("SWG-VAULT-SWG904210", BullionQrModule.formatWalletAddress("SWG-VAULT-SWG904210"))
    assertEquals("SWG904210", BullionQrModule.resolveUserIdOrEmpty("SWG-VAULT-SWG904210"))

    val env = BullionQrModule.generateEncryptedPayload(
      customUserId = "SWG904210",
      displayName = "Aarav SonaWane",
      epochSec = 1_700_000_000L,
      requestedAsset = "GOLD_24K",
      requestedGrams = 2.5,
    )
    assertEquals("SWG904210", env.userId)
    assertEquals("SWG-VAULT-SWG904210", env.walletAddress)
    assertEquals("GOLD_24K", env.requestedAsset)
    assertEquals(2.5, env.requestedGrams, 0.0001)
    assertTrue(env.encryptedToken.startsWith("SWGQR2."))

    val decrypted = BullionQrModule.decryptQrPayload(env.encryptedToken)
    assertNotNull(decrypted)
    assertEquals("SWG-VAULT-SWG904210", decrypted!!.walletAddress)
    assertEquals("GOLD_24K", decrypted.requestedAsset)
    assertEquals(2.5, decrypted.requestedGrams, 0.0001)

    val prefill = BullionQrModule.resolveTransferPrefill(env.encryptedToken)
    assertNotNull(prefill)
    assertTrue(prefill!!.isEncryptedVerified)
    assertEquals("SWG904210", prefill.userId)
    assertEquals("SWG-VAULT-SWG904210", prefill.walletAddress)
    assertEquals("GOLD_24K", prefill.requestedAsset)
    assertEquals(2.5, prefill.requestedGrams, 0.0001)

    val plainAddrPrefill = BullionQrModule.resolveTransferPrefill("SWG-VAULT-SWG904210")
    assertNotNull(plainAddrPrefill)
    assertFalse(plainAddrPrefill!!.isEncryptedVerified)
    assertEquals("SWG904210", plainAddrPrefill.userId)
    assertEquals("SWG-VAULT-SWG904210", plainAddrPrefill.walletAddress)
  }

  @Test
  fun kycStatusResolver_mapsFirestoreStatusesToVerifiedPendingAndActionLabels() {
    val approved = KycStatusResolver.resolve(kycStatus = "APPROVED")
    assertEquals("Verified", approved.label)
    assertEquals(KycVisualTier.VERIFIED, approved.tier)

    val verified = KycStatusResolver.resolve(kycStatus = "VERIFIED")
    assertEquals("Verified", verified.label)
    assertEquals(KycVisualTier.VERIFIED, verified.tier)

    val underReview = KycStatusResolver.resolve(kycStatus = "UNDER_REVIEW")
    assertEquals("Pending", underReview.label)
    assertEquals(KycVisualTier.PENDING, underReview.tier)

    val pending = KycStatusResolver.resolve(kycStatus = "PENDING")
    assertEquals("Pending", pending.label)
    assertEquals(KycVisualTier.PENDING, pending.tier)

    val infoReq = KycStatusResolver.resolve(kycStatus = "INFO_REQUIRED")
    assertEquals("Action Required", infoReq.label)
    assertEquals(KycVisualTier.ACTION_REQUIRED, infoReq.tier)

    val rejected = KycStatusResolver.resolve(kycStatus = "REJECTED")
    assertEquals("Rejected", rejected.label)
    assertEquals(KycVisualTier.REJECTED, rejected.tier)

    val notStarted = KycStatusResolver.resolve(kycStatus = "NOT_STARTED")
    assertEquals("Not Verified", notStarted.label)
    assertEquals(KycVisualTier.NOT_STARTED, notStarted.tier)

    val loading = KycStatusResolver.resolve(kycStatus = null, kycRecordStatus = null, isLoading = true)
    assertEquals("Syncing...", loading.label)
    assertEquals(KycVisualTier.LOADING, loading.tier)

    val fallbackFromRecord = KycStatusResolver.resolve(kycStatus = "", kycRecordStatus = "UNDER_REVIEW")
    assertEquals("Pending", fallbackFromRecord.label)
    assertEquals(KycVisualTier.PENDING, fallbackFromRecord.tier)
  }

  @Test
  fun tradeStatusResolver_mapsCompletedPendingFailedAndFiltersBullionTrades() {
    assertEquals("Completed", TradeStatusResolver.resolveStatus("SUCCESSFUL").label)
    assertEquals(TradeStatusTier.COMPLETED, TradeStatusResolver.resolveStatus("COMPLETED").tier)
    assertEquals("Pending", TradeStatusResolver.resolveStatus("PENDING").label)
    assertEquals(TradeStatusTier.PENDING, TradeStatusResolver.resolveStatus("PROCESSING").tier)
    assertEquals("Failed", TradeStatusResolver.resolveStatus("FAILED").label)
    assertEquals(TradeStatusTier.FAILED, TradeStatusResolver.resolveStatus("REJECTED").tier)

    val items = listOf(
      TransactionRecord(id = "t1", category = "GOLD_BUY", status = "SUCCESSFUL", grams = 1.5, totalAmount = 12000.0),
      TransactionRecord(id = "t2", category = "SILVER_SELL", status = "PENDING", grams = 20.0, totalAmount = 1900.0),
      TransactionRecord(id = "t3", category = "GOLD_SELL", status = "FAILED", grams = 0.5, totalAmount = 3900.0),
      TransactionRecord(id = "t4", category = "DEPOSIT", status = "SUCCESSFUL", totalAmount = 5000.0),
    )

    val allBullion = TradeStatusResolver.filterTrades(items, metalFilter = "ALL", statusFilter = "ALL")
    assertEquals(3, allBullion.size)
    assertFalse(allBullion.any { it.category == "DEPOSIT" })

    val goldOnly = TradeStatusResolver.filterTrades(items, metalFilter = "GOLD", statusFilter = "ALL")
    assertEquals(listOf("t1", "t3"), goldOnly.map { it.id })

    val buyOnly = TradeStatusResolver.filterTrades(items, metalFilter = "ALL", statusFilter = "ALL", tradeSideFilter = "BUY")
    assertEquals(listOf("t1"), buyOnly.map { it.id })

    val sellOnly = TradeStatusResolver.filterTrades(items, metalFilter = "ALL", statusFilter = "ALL", tradeSideFilter = "SELL")
    assertEquals(listOf("t2", "t3"), sellOnly.map { it.id })

    val failedOnly = TradeStatusResolver.filterTrades(items, metalFilter = "ALL", statusFilter = "FAILED")
    assertEquals(listOf("t3"), failedOnly.map { it.id })
  }

  @Test
  fun marketTrendsApiSeriesBuilder_mergesExternalApiSeriesAndLiveSpot() {
    val apiTrend = DualMetalTrendSeries(
      goldPoints = listOf(
        MarketPoint("11-01", 7900.0),
        MarketPoint("11-08", 8020.0),
        MarketPoint("LIVE", 8100.0),
      ),
      silverPoints = listOf(
        MarketPoint("11-01", 92.0),
        MarketPoint("11-08", 94.5),
        MarketPoint("LIVE", 96.0),
      ),
      rangeKey = "30D",
    )
    val liveRates = LiveMarketRates(
      gold24kBuyPerGram = 8150.0,
      silver999BuyPerGram = 97.2,
      isLive = true,
      sourceName = "api.gold-api.com (XAU/XAG Spot) + open.er-api.com (USD/INR)",
    )

    val merged = MarketTrendsApiSeriesBuilder.buildUnifiedSeries(
      externalTrend = apiTrend,
      historyEntries = emptyList(),
      liveRates = liveRates,
      windowDays = 30,
    )
    assertEquals(3, merged.size)
    assertEquals(8150.0, merged.last().gold24kPerGram, 0.01)
    assertEquals(97.2, merged.last().silver999PerGram, 0.01)
    assertTrue(merged.first().source.contains("External Financial API"))

    val sliced = MarketTrendsApiSeriesBuilder.buildUnifiedSeries(
      externalTrend = apiTrend,
      historyEntries = emptyList(),
      liveRates = liveRates,
      windowDays = 2,
    )
    assertEquals(2, sliced.size)
    assertEquals(8020.0, sliced.first().gold24kPerGram, 0.01)
  }

  @Test
  fun biometricLoginManager_evaluatesWalletTradingAndAppLoginPolicies() {
    val walletLocked = BiometricLoginManager.buildFeaturePolicy(
      feature = ProtectedFeature.WALLET_ACCESS,
      capability = BiometricCapability.STRONG_READY,
      hasDeviceCredential = true,
      userBiometricEnabled = true,
      featureLockEnabled = true,
      hasMpin = true,
      alreadyAuthorizedInSession = false,
    )
    assertTrue(walletLocked.requiresAuthentication)
    assertTrue(walletLocked.canPromptBiometric)
    assertTrue(walletLocked.canFallbackToMpin)

    val walletAlreadyUnlocked = BiometricLoginManager.buildFeaturePolicy(
      feature = ProtectedFeature.WALLET_ACCESS,
      capability = BiometricCapability.STRONG_READY,
      hasDeviceCredential = true,
      userBiometricEnabled = true,
      featureLockEnabled = true,
      hasMpin = true,
      alreadyAuthorizedInSession = true,
    )
    assertFalse(walletAlreadyUnlocked.requiresAuthentication)

    val tradingPerOrder = BiometricLoginManager.buildFeaturePolicy(
      feature = ProtectedFeature.TRADING_EXECUTION,
      capability = BiometricCapability.WEAK_READY,
      hasDeviceCredential = false,
      userBiometricEnabled = true,
      featureLockEnabled = true,
      hasMpin = false,
      alreadyAuthorizedInSession = true,
    )
    assertTrue(tradingPerOrder.requiresAuthentication)
    assertTrue(tradingPerOrder.canPromptBiometric)
  }

  @Test
  fun goldTradingPanelCalculator_syncsAmountAndWeightForBuyAndSell() {
    val rates = LiveMarketRates(
      gold24kBuyPerGram = 8000.0,
      gold24kSellPerGram = 7800.0,
      gold22kBuyPerGram = 7333.33,
      gold18kBuyPerGram = 6000.0,
    )
    val wallet = WalletHoldings(
      availableInr = 10000.0,
      gold24kGrams = 1.5,
      gold22kGrams = 0.5,
    )

    // Buy 2g of 24K Gold @ 8000/g + 3% GST -> base 16000, gst 480, total 16480 -> exceeds 10k wallet if WALLET chosen
    val buyByWeight = GoldTradingPanelCalculator.buildQuote(
      side = GoldTradeSide.BUY,
      purity = "24K",
      rawAmountText = "",
      rawWeightText = "2.0",
      lastEdited = GoldTradeInputFocus.WEIGHT,
      rates = rates,
      wallet = wallet,
      gstPercent = 3.0,
      paymentMethod = "WALLET",
    )
    assertEquals(2.0, buyByWeight.grams, 0.0001)
    assertEquals(16000.0, buyByWeight.baseAmountInr, 0.01)
    assertEquals(480.0, buyByWeight.gstAmountInr, 0.01)
    assertEquals(16480.0, buyByWeight.totalAmountInr, 0.01)
    assertTrue(buyByWeight.insufficientWallet)
    assertFalse(buyByWeight.exceedsHoldings)

    // Buy ₹8,240 inclusive of 3% GST -> base 8000, grams 1.0
    val weightSynced = GoldTradingPanelCalculator.weightFromAmount(
      amountInr = 8240.0,
      ratePerGram = 8000.0,
      gstPercent = 3.0,
      side = GoldTradeSide.BUY,
    )
    assertEquals(1.0, weightSynced, 0.0001)

    // Sell 2.0g when owned 1.5g -> exceedsHoldings = true
    val sellOverBalance = GoldTradingPanelCalculator.buildQuote(
      side = GoldTradeSide.SELL,
      purity = "24K",
      rawAmountText = "",
      rawWeightText = "2.0",
      lastEdited = GoldTradeInputFocus.WEIGHT,
      rates = rates,
      wallet = wallet,
      gstPercent = 3.0,
      paymentMethod = "WALLET",
    )
    assertTrue(sellOverBalance.exceedsHoldings)
    assertEquals(15600.0, sellOverBalance.totalAmountInr, 0.01)
  }

  @Test
  fun priceAlertManager_evaluatesGoldAndSilverTargetsThresholdsAndCooldowns() {
    val rates = LiveMarketRates(
      gold24kBuyPerGram = 7950.0,
      gold22kBuyPerGram = 7287.5,
      gold18kBuyPerGram = 5962.5,
      silver999BuyPerGram = 98.5,
      isLive = true,
    )

    assertEquals(7950.0, PriceAlertManager.resolveMetalPrice(rates, "GOLD_24K"), 0.01)
    assertEquals(7287.5, PriceAlertManager.resolveMetalPrice(rates, "GOLD_22K"), 0.01)
    assertEquals(5962.5, PriceAlertManager.resolveMetalPrice(rates, "GOLD_18K"), 0.01)
    assertEquals(98.5, PriceAlertManager.resolveMetalPrice(rates, "SILVER_999"), 0.01)

    // Gold 24K drops below 8000 -> triggered
    val goldBelowTarget = PriceAlertTarget(
      id = "pa_gold_1",
      metal = "GOLD_24K",
      targetPriceInr = 8000.0,
      direction = "BELOW",
      frequency = "ONCE",
      enabled = true,
      notes = "Wedding dip",
    )
    val goldOutcome = PriceAlertManager.evaluateTarget(goldBelowTarget, rates, nowMs = 1_700_000_000_000L)
    assertTrue(goldOutcome.thresholdReached)
    assertTrue(goldOutcome.shouldNotify)
    assertEquals("TRIGGERED", goldOutcome.status)
    assertTrue(goldOutcome.notificationTitle.contains("24K Gold"))
    assertTrue(goldOutcome.notificationBody.contains("dropped below"))
    assertTrue(goldOutcome.notificationBody.contains("Wedding dip"))

    // Silver 999 rises above 95 -> triggered
    val silverAboveTarget = PriceAlertTarget(
      id = "pa_silver_1",
      metal = "SILVER_999",
      targetPriceInr = 95.0,
      direction = "ABOVE",
      frequency = "EVERY_HOUR",
      enabled = true,
    )
    val silverOutcome = PriceAlertManager.evaluateTarget(silverAboveTarget, rates, nowMs = 1_700_000_000_000L)
    assertTrue(silverOutcome.thresholdReached)
    assertTrue(silverOutcome.shouldNotify)
    assertTrue(silverOutcome.notificationTitle.contains("999 Silver"))
    assertTrue(silverOutcome.notificationBody.contains("rose above"))

    // Within EVERY_HOUR cooldown (20 min later) -> COOLDOWN, shouldNotify = false
    val cooldownOutcome = PriceAlertManager.evaluateTarget(
      silverAboveTarget.copy(lastNotifiedEpochMs = 1_700_000_000_000L, triggerCount = 1),
      rates,
      nowMs = 1_700_001_200_000L,
    )
    assertTrue(cooldownOutcome.thresholdReached)
    assertFalse(cooldownOutcome.shouldNotify)
    assertEquals("COOLDOWN", cooldownOutcome.status)

    // Crosses check when moving from 94.0 -> 98.5 across 96.0
    assertTrue(
      PriceAlertManager.isThresholdReached(
        currentPrice = 98.5,
        targetPrice = 96.0,
        direction = "CROSSES",
        previousObserved = 94.0,
      )
    )
    assertFalse(
      PriceAlertManager.isThresholdReached(
        currentPrice = 8100.0,
        targetPrice = 8000.0,
        direction = "BELOW",
      )
    )
  }

  @Test
  fun priceTrackingCalculator_extractsMetalsSlicesWindowsAndComputesStatsAndCompare() {
    val entries = (1..12).map { day ->
      val d = day.toString().padStart(2, '0')
      MarketPriceHistoryEntry(
        id = "2026-11-$d",
        dateStr = "2026-11-$d",
        label = "11-$d",
        gold24kPerGram = 8000.0 + (day - 1) * 100.0,
        gold22kPerGram = if (day % 2 == 0) (8000.0 + (day - 1) * 100.0) * (22.0 / 24.0) else 0.0,
        gold18kPerGram = if (day % 2 == 0) (8000.0 + (day - 1) * 100.0) * (18.0 / 24.0) else 0.0,
        silver999PerGram = 100.0 + (day - 1) * 2.0,
        source = "FIRESTORE_SPOT",
        timestampEpochMs = 1_793_500_000_000L + day * 86_400_000L,
      )
    }

    // Extract with 22K / 18K fallback on odd days
    val day1 = entries.first()
    assertEquals(8000.0, PriceTrackingCalculator.extractPrice(day1, PriceTrackingMetal.GOLD_24K), 0.01)
    assertEquals(8000.0 * (22.0 / 24.0), PriceTrackingCalculator.extractPrice(day1, PriceTrackingMetal.GOLD_22K), 0.01)
    assertEquals(8000.0 * (18.0 / 24.0), PriceTrackingCalculator.extractPrice(day1, PriceTrackingMetal.GOLD_18K), 0.01)
    assertEquals(100.0, PriceTrackingCalculator.extractPrice(day1, PriceTrackingMetal.SILVER_999), 0.01)

    // 7D slice returns newest 7 entries sorted chronologically
    val window7d = PriceTrackingCalculator.sliceWindow(entries.shuffled(), PriceTrackingWindow.D7)
    assertEquals(7, window7d.size)
    assertEquals("2026-11-06", window7d.first().dateStr)
    assertEquals("2026-11-12", window7d.last().dateStr)

    // Gold 24K stats over 7D: 8500 -> 9100 (+7.0588%), high 9100, low 8500, avg 8800
    val goldStats = PriceTrackingCalculator.computeStats(window7d, PriceTrackingMetal.GOLD_24K)
    assertEquals(9100.0, goldStats.latestPrice, 0.01)
    assertEquals(9100.0, goldStats.periodHigh, 0.01)
    assertEquals(8500.0, goldStats.periodLow, 0.01)
    assertEquals(8800.0, goldStats.periodAverage, 0.01)
    assertEquals(((9100.0 - 8500.0) / 8500.0) * 100.0, goldStats.changePct, 0.01)
    assertEquals(9100.0 / 122.0, goldStats.goldSilverRatio, 0.05)

    // Normalized compare series starts at 0% for both metals
    val (goldPct, silverPct) = PriceTrackingCalculator.buildNormalizedCompareSeries(window7d)
    assertEquals(7, goldPct.size)
    assertEquals(7, silverPct.size)
    assertEquals(0.0, goldPct.first(), 0.001)
    assertEquals(0.0, silverPct.first(), 0.001)
    assertEquals(((9100.0 - 8500.0) / 8500.0) * 100.0, goldPct.last(), 0.01)
    assertEquals(((122.0 - 110.0) / 110.0) * 100.0, silverPct.last(), 0.01)
  }

  @Test
  fun sonaWaneRepository_buildsLiveMarketRatesFromFirestoreGoldAndSilverEntries() {
    assertNull(SonaWaneRepository.buildLiveMarketRatesFromEntries(emptyList()))

    val day1 = MarketPriceHistoryEntry(
      id = "2026-11-15",
      dateStr = "2026-11-15",
      gold24kPerGram = 8000.0,
      silver999PerGram = 95.0,
      source = "FIRESTORE_SPOT",
      timestampEpochMs = 1_794_700_000_000L,
    )
    val day2 = MarketPriceHistoryEntry(
      id = "2026-11-16",
      dateStr = "2026-11-16",
      gold24kPerGram = 8200.0,
      gold22kPerGram = 0.0, // exercises fallback 22K/18K derivation
      gold18kPerGram = 0.0,
      silver999PerGram = 98.8,
      source = "FIRESTORE_SPOT_SYNC",
      timestampEpochMs = 1_794_786_400_000L,
    )

    val rates = SonaWaneRepository.buildLiveMarketRatesFromEntries(listOf(day1, day2))
    assertNotNull(rates)
    rates!!
    assertTrue(rates.isLive)
    assertEquals(8200.0, rates.gold24kBuyPerGram, 0.01)
    assertEquals(8200.0 * (22.0 / 24.0), rates.gold22kBuyPerGram, 0.01)
    assertEquals(8200.0 * (18.0 / 24.0), rates.gold18kBuyPerGram, 0.01)
    assertEquals(98.8, rates.silver999BuyPerGram, 0.01)
    assertEquals(2.5, rates.goldChangePct, 0.01)
    assertEquals(4.0, rates.silverChangePct, 0.01)
    assertEquals("FIRESTORE_SPOT_SYNC", rates.sourceName)
  }

  @Test
  fun bullionQrModule_supportsSilver999TransferRequestAndAssetLabels() {
    assertEquals("24K Gold", BullionQrModule.formatAssetLabel("GOLD_24K"))
    assertEquals("22K Gold", BullionQrModule.formatAssetLabel("GOLD_22K"))
    assertEquals("18K Gold", BullionQrModule.formatAssetLabel("GOLD_18K"))
    assertEquals("999 Silver", BullionQrModule.formatAssetLabel("SILVER_999"))
    assertEquals("INR Wallet", BullionQrModule.formatAssetLabel("INR"))

    val env = BullionQrModule.generateEncryptedPayload(
      customUserId = "SWG556677",
      displayName = "Rohan Bullion",
      epochSec = 1_795_000_000L,
      requestedAsset = "SILVER_999",
      requestedGrams = 50.0,
    )
    assertEquals("SWG-VAULT-SWG556677", env.walletAddress)
    assertEquals("SILVER_999", env.requestedAsset)
    assertEquals(50.0, env.requestedGrams, 0.0001)

    val prefill = BullionQrModule.resolveTransferPrefill(env.encryptedToken)
    assertNotNull(prefill)
    assertTrue(prefill!!.isEncryptedVerified)
    assertEquals("SWG556677", prefill.userId)
    assertEquals("SWG-VAULT-SWG556677", prefill.walletAddress)
    assertEquals("SILVER_999", prefill.requestedAsset)
    assertEquals(50.0, prefill.requestedGrams, 0.0001)
  }

  @Test
  fun portfolioSummaryCalculator_computesGoldAndSilverTotalsAllocationAndEmptyState() {
    val wallet = WalletHoldings(
      availableInr = 5000.0,
      gold24kGrams = 2.0,
      gold22kGrams = 1.0,
      gold18kGrams = 0.5,
      silver999Grams = 10.0,
      totalInvestedGoldInr = 25000.0,
      totalInvestedSilverInr = 850.0,
    )
    val fsSummary = FirestoreGoldSummary(
      gold24kPerGram = 8000.0,
      gold22kPerGram = 7400.0,
      gold18kPerGram = 6000.0,
      isLoading = false,
      isFromFirestore = true,
    )
    val rates = LiveMarketRates(silver999SellPerGram = 95.0)

    val snapshot = PortfolioSummaryCalculator.calculate(wallet, fsSummary, rates)
    // Gold: 2*8000 + 1*7400 + 0.5*6000 = 26400; Silver: 10*95 = 950; Total = 27350
    assertEquals(26400.0, snapshot.totalGoldValueInr, 0.01)
    assertEquals(950.0, snapshot.totalSilverValueInr, 0.01)
    assertEquals(27350.0, snapshot.totalHoldingsValueInr, 0.01)
    assertEquals(32350.0, snapshot.totalNetWorthWithCashInr, 0.01)
    assertEquals(25850.0, snapshot.totalInvestedBullionInr, 0.01)
    assertEquals(1500.0, snapshot.unrealizedPnlInr, 0.01)
    assertEquals(100.0f, snapshot.goldSharePercent + snapshot.silverSharePercent, 0.05f)
    assertTrue(snapshot.goldSharePercent > 96.0f)
    assertTrue(snapshot.isFromFirestore)
    assertTrue(snapshot.hasLiveRates)

    // History fallback for silver when live rates are unavailable
    val historyFallback = PortfolioSummaryCalculator.calculate(
      wallet = WalletHoldings(silver999Grams = 5.0),
      firestoreGoldSummary = fsSummary,
      fallbackRates = null,
      marketHistory30d = listOf(MarketPriceHistoryEntry(silver999PerGram = 102.0)),
    )
    assertEquals(510.0, historyFallback.totalSilverValueInr, 0.01)
    assertEquals(102.0, historyFallback.silverRatePerGramInr, 0.01)

    // Empty vault
    val empty = PortfolioSummaryCalculator.calculate(null, FirestoreGoldSummary(), null)
    assertEquals(0.0, empty.totalHoldingsValueInr, 0.001)
    assertEquals(0f, empty.goldSharePercent, 0.001f)
    assertEquals(0f, empty.silverSharePercent, 0.001f)
  }

  @Test
  fun firestoreService_usesProvisionedNamedDatabaseId() {
    assertEquals(
      "ai-studio-android-sonawane-3bad5ba9-1ba6-446a-acfb-58b1d8b514a7",
      FirestoreService.resolveDatabaseId(),
    )
  }

  @Test
  fun adminRepository_parseAdminMapSafely_handlesMissingAndNullFieldsWithoutCrashing() {
    // Console-provisioned Super Admin doc shape (missing displayName, createdAt, updatedAt)
    val superAdminDoc = mapOf<String, Any?>(
      "uid" to "kOw19wargLb891tm19oToOFCE0t2",
      "email" to "goldsonawane@gmail.com",
      "role" to "SUPER_ADMIN",
      "isActive" to true,
      "permissions" to listOf("ALL", "USERS", "KYC", "FINANCE", "WITHDRAWALS", "SUPPORT", "SETTINGS", "ROLES", "REPORTS"),
      "operatorId" to "SWG-A001",
    )
    val parsedSuper = AdminRepository.parseAdminMapSafely("kOw19wargLb891tm19oToOFCE0t2", superAdminDoc)
    assertEquals("kOw19wargLb891tm19oToOFCE0t2", parsedSuper.uid)
    assertEquals("goldsonawane@gmail.com", parsedSuper.email)
    assertEquals("SUPER_ADMIN", parsedSuper.role)
    assertTrue(parsedSuper.isActive)
    assertEquals(9, parsedSuper.permissions.size)
    assertEquals("SWG-A001", parsedSuper.operatorId)
    assertEquals("goldsonawane", parsedSuper.displayName)
    assertNull(parsedSuper.createdAt)
    assertNull(parsedSuper.updatedAt)

    // Completely sparse/null fields should default safely (role="", permissions=[], isActive=false)
    val sparseDoc = mapOf<String, Any?>(
      "email" to null,
      "displayName" to null,
      "role" to null,
      "permissions" to null,
      "createdAt" to "2026-11-17T00:00:00Z", // wrong type instead of Timestamp
      "updatedAt" to listOf("not_a_timestamp"),
    )
    val parsedSparse = AdminRepository.parseAdminMapSafely("doc_sparse", sparseDoc)
    assertEquals("doc_sparse", parsedSparse.uid)
    assertEquals("", parsedSparse.email)
    assertEquals("Admin Operator", parsedSparse.displayName)
    assertEquals("", parsedSparse.role)
    assertTrue(parsedSparse.permissions.isEmpty())
    assertFalse(parsedSparse.isActive)
    assertNull(parsedSparse.createdAt)
    assertNull(parsedSparse.updatedAt)
  }
}
