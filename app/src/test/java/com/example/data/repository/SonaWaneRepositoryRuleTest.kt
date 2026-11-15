package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SonaWaneRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun depositAndBuySellGold_updatesWalletAndTransactionsAtomically() = runBlocking {
        val uid = signInTestUser("alice_${UUID.randomUUID().toString().take(6)}@test.com")
        val repo = SonaWaneRepository(firestore, auth)

        withTimeout(TIMEOUT_MS) {
            repo.ensureUserProvisioned(uid, "alice@test.com", "Alice Investor", "dev_test_1")
            val dep = repo.depositMoney(10000.0, "UPI", "TEST")
            assertEquals("SUCCESSFUL", dep.status)

            val buy = repo.buyMetal(
                isGold = true,
                purity = "24K",
                grams = 1.0,
                ratePerGram = 7800.0,
                baseAmount = 7800.0,
                gstAmount = 234.0,
                chargesAmount = 0.0,
                totalAmount = 8034.0,
                paymentMethod = "WALLET",
                mode = "TEST",
            )
            assertEquals("SUCCESSFUL", buy.status)

            val wallet = repo.observeWallet(uid).first { it != null && it.gold24kGrams >= 1.0 }
            assertNotNull(wallet)
            assertEquals(1966.0, wallet!!.availableInr, 0.01)
            assertEquals(1.0, wallet.gold24kGrams, 0.0001)

            val sell = repo.sellMetal(
                isGold = true,
                purity = "24K",
                grams = 0.5,
                sellRatePerGram = 7700.0,
                deductionsInr = 0.0,
                creditToWallet = true,
                bankAccount = null,
                mode = "TEST",
            )
            assertEquals("SUCCESSFUL", sell.status)

            val updatedWallet = repo.observeWallet(uid).first { it != null && it.gold24kGrams < 0.6 }
            assertEquals(0.5, updatedWallet!!.gold24kGrams, 0.0001)
            assertEquals(1966.0 + 3850.0, updatedWallet.availableInr, 0.01)
        }
    }

    @Test
    fun atomicTransfer_senderDebitsAndRecipientClaims() = runBlocking {
        val aliceEmail = "alice_tr_${UUID.randomUUID().toString().take(6)}@test.com"
        val bobEmail = "bob_tr_${UUID.randomUUID().toString().take(6)}@test.com"

        val bobUid = signInTestUser(bobEmail)
        val bobRepo = SonaWaneRepository(firestore, auth)
        val bobProfile = withTimeout(TIMEOUT_MS) {
            bobRepo.ensureUserProvisioned(bobUid, bobEmail, "Bob Receiver", "dev_bob")
        }

        val aliceUid = signInTestUser(aliceEmail)
        val aliceRepo = SonaWaneRepository(firestore, auth)
        withTimeout(TIMEOUT_MS) {
            aliceRepo.ensureUserProvisioned(aliceUid, aliceEmail, "Alice Sender", "dev_alice")
            aliceRepo.depositMoney(5000.0, "UPI", "TEST")
            val recipientDir = aliceRepo.lookupRecipient(bobProfile.customUserId)
            assertNotNull(recipientDir)
            val transfer = aliceRepo.executeTransfer(
                senderName = "Alice Sender",
                recipient = recipientDir!!,
                assetType = "INR",
                quantity = 1500.0,
                feeInr = 0.0,
                mode = "TEST",
            )
            assertEquals("COMPLETED", transfer.status)
        }

        // Switch to Bob and claim
        signInTestUser(bobEmail)
        withTimeout(TIMEOUT_MS) {
            val incoming = bobRepo.observeIncomingTransfers(bobUid).first { it.isNotEmpty() }
            bobRepo.claimIncomingTransfer(incoming.first(), "TEST")
            val bobWallet = bobRepo.observeWallet(bobUid).first { it != null && it.availableInr >= 1500.0 }
            assertEquals(1500.0, bobWallet!!.availableInr, 0.01)
        }
    }

    @Test
    fun crossUserWalletRead_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_iso_${UUID.randomUUID().toString().take(6)}@test.com")
        val aliceRepo = SonaWaneRepository(firestore, auth)
        withTimeout(TIMEOUT_MS) {
            aliceRepo.ensureUserProvisioned(aliceUid, "alice@test.com", "Alice", "dev_a")
        }

        signInTestUser("bob_iso_${UUID.randomUUID().toString().take(6)}@test.com")
        val result = runCatching {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("wallets").document("primary").get().await()
            }
        }
        assertTrue(result.isFailure)
        val firestoreErr = result.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreErr?.code)
    }

    @Test
    fun observeTransactions_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = SonaWaneRepository(firestore, auth)
        val result = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeTransactions("some_uid").first()
            }
        }
        assertTrue(result.isFailure)
        val firestoreErr = result.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreErr?.code)
    }

    @Test
    fun advancedMarketAlerts_and_biometricLoginHistory_persistUnderRules() = runBlocking {
        val uid = signInTestUser("alerts_${UUID.randomUUID().toString().take(6)}@test.com")
        val repo = SonaWaneRepository(firestore, auth)

        withTimeout(TIMEOUT_MS) {
            repo.ensureUserProvisioned(uid, "alerts@test.com", "Alert Investor", "dev_alert")
            repo.saveMpin(uid, "2580")
            repo.updateSecurityAndNotifPrefs(
                uid = uid,
                biometricEnabled = true,
                priceAlertsEnabled = true,
            )
            repo.recordLoginEvent(uid, method = "BIOMETRIC_UNLOCK", status = "SUCCESS")

            val history = repo.observeLoginHistory(uid).first { it.any { e -> e.method == "BIOMETRIC_UNLOCK" } }
            assertTrue(history.any { it.method == "BIOMETRIC_UNLOCK" && it.status == "SUCCESS" })

            repo.createPriceAlert(
                uid = uid,
                metal = "GOLD_18K",
                targetPriceInr = 5900.0,
                direction = "CROSSES",
                frequency = "EVERY_HOUR",
                notes = "18K jewelry target",
                currentObservedPrice = 5880.0,
            )
            val createdAlerts = repo.observePriceAlerts(uid).first { it.isNotEmpty() }
            val alert = createdAlerts.first()
            assertEquals("GOLD_18K", alert.metal)
            assertEquals("CROSSES", alert.direction)
            assertEquals("EVERY_HOUR", alert.frequency)
            assertEquals(true, alert.isActive)

            repo.markPriceAlertTriggered(uid, alert, 5910.0)
            val triggered = repo.observePriceAlerts(uid).first { list ->
                list.firstOrNull { it.id == alert.id }?.triggerCount == 1
            }.first { it.id == alert.id }
            assertEquals(true, triggered.isActive) // EVERY_HOUR stays active after trigger
            assertEquals(5910.0, triggered.lastTriggeredPrice, 0.01)

            repo.togglePriceAlert(uid, alert.id, false)
            val paused = repo.observePriceAlerts(uid).first { list ->
                list.firstOrNull { it.id == alert.id }?.isActive == false
            }.first { it.id == alert.id }
            assertEquals(false, paused.isActive)

            repo.updatePriceAlert(uid, alert.id, 6000.0, "ABOVE", "ONCE", true, "Updated target")
            val updated = repo.observePriceAlerts(uid).first { list ->
                list.firstOrNull { it.id == alert.id }?.frequency == "ONCE"
            }.first { it.id == alert.id }
            assertEquals(6000.0, updated.targetPriceInr, 0.01)
            assertEquals("ABOVE", updated.direction)
            assertEquals(true, updated.isActive)

            repo.deletePriceAlert(uid, alert.id)
        }
    }

    @Test
    fun adminCredentialsAndRbacManagement_enforcesUniqueCredentialsAndAuditTrail() = runBlocking {
        signInVerifiedTestUser(AdminRepository.BOOTSTRAPPED_SUPER_ADMIN_EMAIL)
        val adminRepo = AdminRepository(firestore, auth)

        withTimeout(TIMEOUT_MS) {
            val bootstrapped = adminRepo.verifyAdminEligibility()
            assertNotNull(bootstrapped)
            assertEquals("SUPER_ADMIN", bootstrapped!!.role)

            val enrolled = adminRepo.enrollAdminCredentials(
                admin = bootstrapped,
                operatorId = "SWG-ROOT-91",
                passcode = "GoldAdmin#2026",
                biometricEnabled = true,
            )
            assertEquals("SWG-ROOT-91", enrolled.operatorId)
            assertEquals(true, enrolled.biometricAdminEnabled)

            val badLogin = adminRepo.verifyAndRecordAdminLogin(
                admin = enrolled,
                enteredOperatorId = "SWG-ROOT-91",
                enteredPasscode = "WrongPass123",
            )
            assertEquals(false, badLogin)

            val goodLogin = adminRepo.verifyAndRecordAdminLogin(
                admin = enrolled,
                enteredOperatorId = "swg-root-91",
                enteredPasscode = "GoldAdmin#2026",
            )
            assertEquals(true, goodLogin)

            val targetUid = "rbac_mgr_${UUID.randomUUID().toString().take(8)}"
            adminRepo.upsertAdminRole(
                superAdmin = enrolled,
                targetUid = targetUid,
                email = "kyc.lead@sonawanegold.in",
                displayName = "KYC Lead",
                role = AdminRole.KYC_MANAGER,
                permissions = listOf("KYC", "USERS", "REPORTS"),
                operatorId = "SWG-KYC-07",
                initialPasscode = "KycPass#77",
                isActive = true,
            )

            val admins = adminRepo.observeAdmins().first { list -> list.any { it.uid == targetUid } }
            val createdMgr = admins.first { it.uid == targetUid }
            assertEquals("SWG-KYC-07", createdMgr.operatorId)
            assertEquals("KYC_MANAGER", createdMgr.role)
            assertTrue(createdMgr.hasPermission("KYC"))
            assertTrue(createdMgr.hasPermission("REPORTS"))

            adminRepo.resetAdminCredentials(
                superAdmin = enrolled,
                targetAdmin = createdMgr,
                newOperatorId = "SWG-KYC-09",
                newPasscode = "Rotated#99",
            )

            val updatedMgr = adminRepo.observeAdmins().first { list ->
                list.firstOrNull { it.uid == targetUid }?.operatorId == "SWG-KYC-09"
            }.first { it.uid == targetUid }
            assertTrue(
                adminRepo.verifyAndRecordAdminLogin(
                    admin = updatedMgr,
                    enteredOperatorId = "SWG-KYC-09",
                    enteredPasscode = "Rotated#99",
                )
            )

            adminRepo.deleteAdminRole(superAdmin = enrolled, targetAdmin = updatedMgr)
            val afterDelete = adminRepo.observeAdmins().first { list -> list.none { it.uid == targetUid } }
            assertTrue(afterDelete.none { it.uid == targetUid })

            val logs = adminRepo.observeAuditLogs().first { list ->
                list.any { it.action == "ADMIN_PORTAL_LOGIN" } &&
                    list.any { it.action == "REVOKE_ADMIN_ACCESS" }
            }
            assertTrue(logs.any { it.action == "ADMIN_LOGIN_FAILED" })
            assertTrue(logs.any { it.action == "RESET_ADMIN_CREDENTIALS" })
        }
    }

    @Test
    fun cameraXKycSubmission_and_adminReview_persistsCaptureAndApprovesStatus() = runBlocking {
        val userEmail = "kyc_${UUID.randomUUID().toString().take(6)}@test.com"
        val userUid = signInTestUser(userEmail)
        val userRepo = SonaWaneRepository(firestore, auth)

        withTimeout(TIMEOUT_MS) {
            userRepo.ensureUserProvisioned(userUid, userEmail, "Kyc Investor", "dev_kyc")
            userRepo.submitKyc(
                uid = userUid,
                userName = "Kyc Investor",
                mobileNumber = "9876543210",
                panNumber = "ABCDE1234F",
                aadhaarLast4 = "4321",
                documentType = "PAN_CARD",
                documentRefUri = null,
                addressLine = "42 Zaveri Bazaar",
                state = "Maharashtra",
                city = "Mumbai",
                pincode = "400002",
                documentFrontBase64 = "CAMERAX_FRONT_THUMB_BASE64",
                documentBackBase64 = "CAMERAX_BACK_THUMB_BASE64",
                captureMethod = "CAMERAX_SCAN",
                scanQualityScore = 88,
                declarationAccepted = true,
            )

            val savedKyc = userRepo.observeKycRecord(userUid).first { it != null }
            assertNotNull(savedKyc)
            assertEquals("UNDER_REVIEW", savedKyc!!.status)
            assertEquals("CAMERAX_SCAN", savedKyc.captureMethod)
            assertEquals(88, savedKyc.scanQualityScore)
            assertEquals("CAMERAX_FRONT_THUMB_BASE64", savedKyc.documentFrontBase64)
            assertEquals(true, savedKyc.declarationAccepted)
        }

        // Verified admin reviews the CameraX KYC capture
        signInVerifiedTestUser(AdminRepository.BOOTSTRAPPED_SUPER_ADMIN_EMAIL)
        val adminRepo = AdminRepository(firestore, auth)
        withTimeout(TIMEOUT_MS) {
            val admin = adminRepo.verifyAdminEligibility()!!
            val kycItem = adminRepo.observeAllKyc().first { list -> list.any { it.userId == userUid } }
                .first { it.userId == userUid }
            adminRepo.reviewKyc(admin, kycItem, "APPROVED", "Verified clear CameraX scan")
            val reviewed = adminRepo.observeAllKyc().first { list ->
                list.firstOrNull { it.userId == userUid }?.status == "APPROVED"
            }.first { it.userId == userUid }
            assertEquals("APPROVED", reviewed.status)
        }
    }

    @Test
    fun adminKycRejection_requiresMandatoryReason_andStoresReasonForUser() = runBlocking {
        val userEmail = "kyc_rej_${UUID.randomUUID().toString().take(6)}@test.com"
        val userUid = signInTestUser(userEmail)
        val userRepo = SonaWaneRepository(firestore, auth)

        withTimeout(TIMEOUT_MS) {
            userRepo.ensureUserProvisioned(userUid, userEmail, "Reject Applicant", "dev_kyc_rej")
            userRepo.submitKyc(
                uid = userUid,
                userName = "Reject Applicant",
                mobileNumber = "9876543210",
                panNumber = "ABCDE1234F",
                aadhaarLast4 = "8899",
                documentType = "PAN_CARD",
                documentRefUri = null,
                addressLine = "18 Mint Road",
                state = "Maharashtra",
                city = "Mumbai",
                pincode = "400001",
                documentFrontBase64 = "CAMERAX_BLURRY_FRONT",
                documentBackBase64 = null,
                captureMethod = "CAMERAX_SCAN",
                scanQualityScore = 32,
                declarationAccepted = true,
            )
        }

        signInVerifiedTestUser(AdminRepository.BOOTSTRAPPED_SUPER_ADMIN_EMAIL)
        val adminRepo = AdminRepository(firestore, auth)
        withTimeout(TIMEOUT_MS) {
            val admin = adminRepo.verifyAdminEligibility()!!
            val kycItem = adminRepo.observeAllKyc().first { list -> list.any { it.userId == userUid } }
                .first { it.userId == userUid }

            // Blank rejection reason must be rejected
            val blankResult = runCatching {
                adminRepo.reviewKyc(admin, kycItem, "REJECTED", "   ")
            }
            assertTrue(blankResult.isFailure)
            assertTrue(blankResult.exceptionOrNull() is IllegalArgumentException)

            // Non-blank rejection reason succeeds and persists
            adminRepo.reviewKyc(admin, kycItem, "REJECTED", "Blurry PAN card photo; text unreadable")
            val rejected = adminRepo.observeAllKyc().first { list ->
                list.firstOrNull { it.userId == userUid }?.status == "REJECTED"
            }.first { it.userId == userUid }
            assertEquals("REJECTED", rejected.status)
            assertEquals("Blurry PAN card photo; text unreadable", rejected.rejectionReason)
        }
    }

    @Test
    fun marketPriceHistory_seedAndObserve_persists30DaysAndDeniesUnauthenticated() = runBlocking {
        // Unauthenticated observation must fail with PERMISSION_DENIED
        auth.signOut()
        val unauthRepo = SonaWaneRepository(firestore, auth)
        val unauthAttempt = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) { unauthRepo.observeMarketPriceHistory(30).first() }
        }
        assertTrue(unauthAttempt.isFailure)

        // Authenticated user seeds 30-day snapshots and observes them from Firestore
        val email = "mkt_${UUID.randomUUID().toString().take(6)}@test.com"
        signInTestUser(email)
        val repo = SonaWaneRepository(firestore, auth)
        val baseCal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 12)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val sdfFull = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val sdfShort = java.text.SimpleDateFormat("MM-dd", java.util.Locale.US)
        val entries = (29 downTo 0).map { offset ->
            val c = (baseCal.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, -offset) }
            val g24 = 7800.0 + (29 - offset) * 4.5
            val s999 = 92.0 + (29 - offset) * 0.15
            com.example.data.model.MarketPriceHistoryEntry(
                dateStr = sdfFull.format(c.time),
                label = sdfShort.format(c.time),
                gold24kPerGram = g24,
                gold22kPerGram = g24 * 22.0 / 24.0,
                gold18kPerGram = g24 * 18.0 / 24.0,
                silver999PerGram = s999,
                source = "TEST_SPOT_SEED",
                timestampEpochMs = c.timeInMillis,
            )
        }

        withTimeout(TIMEOUT_MS) {
            repo.seedMissingMarketHistory(entries)
            val observed = repo.observeMarketPriceHistory(30).first { it.size >= 30 }
            assertEquals(30, observed.size)
            assertTrue(observed.first().timestampEpochMs < observed.last().timestampEpochMs)
            assertTrue(observed.last().gold24kPerGram > observed.first().gold24kPerGram)
            assertTrue(observed.last().silver999PerGram > observed.first().silver999PerGram)
        }
    }

    @Test
    fun auditLogManager_capturesUserApprovalRoleAssignmentAndConfigDiff_andEnforcesImmutability() = runBlocking {
        // Non-admin user cannot read or write audit_logs
        val nonAdminUid = signInTestUser("nonadmin_${UUID.randomUUID().toString().take(6)}@test.com")
        val auditManager = AuditLogManager(firestore, auth)
        val nonAdminRead = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) { auditManager.fetchAuditLogsOnce() }
        }
        assertTrue(nonAdminRead.isFailure)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, nonAdminRead.exceptionOrNull()?.unwrapFirestoreException()?.code)

        // Signed-in super admin captures user approval, role assignment, and real AppConfig diff
        signInVerifiedTestUser(AdminRepository.BOOTSTRAPPED_SUPER_ADMIN_EMAIL)
        val adminRepo = AdminRepository(firestore, auth, auditManager)
        withTimeout(TIMEOUT_MS) {
            val admin = adminRepo.verifyAdminEligibility()!!

            val approvalLog = auditManager.captureUserApproval(
                admin = admin,
                isKycDecision = true,
                targetUserId = nonAdminUid,
                targetLabel = "Compliance Applicant",
                previousStatus = "UNDER_REVIEW",
                newStatus = "APPROVED",
                reason = "PAN and Aadhaar verified via AuditLogManager test",
            )
            assertTrue(approvalLog.id.startsWith("aud_"))

            val initialConfig = com.example.data.model.AppConfig(
                maintenanceMode = false,
                paymentMode = "TEST",
                gstPercent = 3.0,
            )
            adminRepo.updateAppConfig(admin, initialConfig, "Seed initial config")
            val updatedConfig = initialConfig.copy(
                maintenanceMode = true,
                paymentMode = "LIVE",
                gstPercent = 3.25,
            )
            adminRepo.updateAppConfig(admin, updatedConfig, "Enable live mode & GST update")

            val roleLog = auditManager.captureRoleAssignment(
                actingAdmin = admin,
                action = "UPSERT_ADMIN_ROLE",
                targetUid = "mgr_${UUID.randomUUID().toString().take(6)}",
                targetEmail = "auditor@sonawanegold.in",
                previousState = "NONE",
                newState = "READ_ONLY_AUDITOR perms=REPORTS active=true",
                reason = "Onboard external compliance auditor",
            )

            val fetched = auditManager.observeAuditLogs().first { list ->
                list.any { it.id == approvalLog.id } &&
                    list.any { it.id == roleLog.id } &&
                    list.any { it.action == "UPDATE_APP_CONFIG" && it.newValue.contains("gst=3.25") }
            }
            val cfgLog = fetched.first { it.action == "UPDATE_APP_CONFIG" && it.newValue.contains("gst=3.25") }
            assertTrue(cfgLog.previousValue.contains("mode=TEST"))
            assertTrue(cfgLog.previousValue.contains("maint=false"))
            assertTrue(cfgLog.newValue.contains("mode=LIVE"))
            assertTrue(cfgLog.newValue.contains("maint=true"))

            // Append-only immutability: update and delete must both be denied even for super admin
            val updateAttempt = runCatching {
                firestore.collection("audit_logs").document(approvalLog.id)
                    .update("reason", "Tampered reason").await()
            }
            assertTrue(updateAttempt.isFailure)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, updateAttempt.exceptionOrNull()?.unwrapFirestoreException()?.code)

            val deleteAttempt = runCatching {
                firestore.collection("audit_logs").document(approvalLog.id).delete().await()
            }
            assertTrue(deleteAttempt.isFailure)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, deleteAttempt.exceptionOrNull()?.unwrapFirestoreException()?.code)
        }
    }

    @Test
    fun priceSummary_observeAndFetchLiveGoldRatesFromFirestore_returns24k22k18kAndDeniesUnauth() = runBlocking {
        // Ensure clean market_price_history state in emulator (admin-only delete)
        signInVerifiedTestUser(AdminRepository.BOOTSTRAPPED_SUPER_ADMIN_EMAIL)
        withTimeout(TIMEOUT_MS) {
            val existingDocs = firestore.collection("market_price_history").get().await()
            existingDocs.documents.forEach { it.reference.delete().await() }
        }

        // Unauthenticated fetch must fail
        auth.signOut()
        val unauthRepo = SonaWaneRepository(firestore, auth)
        val unauthFetch = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) { unauthRepo.fetchLatestGoldSummaryOnce() }
        }
        assertTrue(unauthFetch.isFailure)

        // Authenticated user seeds latest 2 days in market_price_history and verifies live 24K/22K/18K rates
        signInTestUser("pricesum_${UUID.randomUUID().toString().take(6)}@test.com")
        val repo = SonaWaneRepository(firestore, auth)
        val baseCal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 14)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val sdfFull = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val sdfShort = java.text.SimpleDateFormat("MM-dd", java.util.Locale.US)
        val prevCal = (baseCal.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
        val todayCal = baseCal.clone() as java.util.Calendar

        val entries = listOf(
            com.example.data.model.MarketPriceHistoryEntry(
                dateStr = sdfFull.format(prevCal.time),
                label = sdfShort.format(prevCal.time),
                gold24kPerGram = 8000.0,
                gold22kPerGram = 7333.33,
                gold18kPerGram = 6000.0,
                silver999PerGram = 96.0,
                source = "TEST_PREV_DAY",
                timestampEpochMs = prevCal.timeInMillis,
            ),
            com.example.data.model.MarketPriceHistoryEntry(
                dateStr = sdfFull.format(todayCal.time),
                label = sdfShort.format(todayCal.time),
                gold24kPerGram = 8240.0,
                gold22kPerGram = 7553.33,
                gold18kPerGram = 6180.0,
                silver999PerGram = 98.0,
                source = "TEST_LIVE_SPOT",
                timestampEpochMs = todayCal.timeInMillis,
            ),
        )

        withTimeout(TIMEOUT_MS) {
            val seeded = repo.seedMissingMarketHistory(entries)
            assertEquals(2, seeded)
            val observed = repo.observeLiveGoldSummary().first { it.isFromFirestore && it.gold24kPerGram >= 8240.0 }
            assertEquals(8240.0, observed.gold24kPerGram, 0.01)
            assertEquals(7553.33, observed.gold22kPerGram, 0.01)
            assertEquals(6180.0, observed.gold18kPerGram, 0.01)
            assertEquals(3.0, observed.changePercent, 0.05)

            val fetched = repo.fetchLatestGoldSummaryOnce()
            assertTrue(fetched.isFromFirestore)
            assertEquals(8240.0, fetched.gold24kPerGram, 0.01)
            assertEquals(7553.33, fetched.gold22kPerGram, 0.01)
            assertEquals(6180.0, fetched.gold18kPerGram, 0.01)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15000L
        const val FLOW_TIMEOUT_MS = 12000L
    }
}

