package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BgDropMonitorState
import com.example.data.local.SecurityPreferences
import com.example.service.GoldPriceMonitorService
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

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

    val pixels = IntArray(bmp.width * bmp.height)
    bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    val source = RGBLuminanceSource(bmp.width, bmp.height, pixels)
    val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)))

    assertEquals(token, decoded.text)
    assertEquals("SWG778899", CryptoUtils.parseQrUserId(decoded.text))
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
}
