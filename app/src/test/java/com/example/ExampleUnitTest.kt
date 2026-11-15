package com.example

import com.example.data.model.AppConfig
import com.example.data.model.AuditLog
import com.example.data.model.DualMetalTrendSeries
import com.example.data.model.FirestoreGoldSummary
import com.example.data.model.LiveMarketRates
import com.example.data.model.MarketPoint
import com.example.data.model.MarketPriceHistoryEntry
import com.example.data.model.WalletHoldings
import com.example.data.remote.MarketRateService
import com.example.data.repository.AuditLogManager
import com.example.data.repository.SonaWaneRepository
import com.example.service.GoldPriceMonitorService
import com.example.ui.components.PortfolioValuationCalculator
import com.example.ui.components.computeGoldHistoryStats
import com.example.ui.screens.GoldAmountBasis
import com.example.ui.screens.GoldCalculatorInputMode
import com.example.ui.screens.GoldPurchaseCalculator
import com.example.ui.screens.GoldPurchaseFormat
import com.example.util.BullionQrModule
import com.example.util.Formatters
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
}
