const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-sonawane-3bad5ba9-1ba6-446a-acfb-58b1d8b514a7";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "admin_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function getDb(context) {
  return context.firestore({ databaseId: DATABASE_ID });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: denied across all collections", async () => {
  const unauthDb = getDb(testEnv.unauthenticatedContext());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("transactions").get());
  await assertFails(unauthDb.collection("kyc_records").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("admins").doc(ADMIN_UID).get());
  await assertFails(unauthDb.collection("app_config").doc("settings").get());
});

test("Authenticated owner: can create profile, wallet, and immutable ledger", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }));
  const now = new Date();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      customUserId: "SWG100001",
      fullName: "Alice Sharma",
      mobileNumber: "9876543210",
      countryCode: "+91",
      kycStatus: "NOT_STARTED",
      accountStatus: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("wallets").doc("primary").set({
      userId: ALICE_UID,
      availableInr: 5000.0,
      pendingInr: 0.0,
      lockedInr: 0.0,
      gold24kGrams: 1.25,
      gold22kGrams: 0.0,
      gold18kGrams: 0.0,
      silver999Grams: 10.0,
      totalInvestedGoldInr: 9500.0,
      totalInvestedSilverInr: 950.0,
      updatedAt: now,
    })
  );

  const ledgerRef = aliceDb.collection("users").doc(ALICE_UID).collection("ledger").doc("led_1");
  await assertSucceeds(
    ledgerRef.set({
      userId: ALICE_UID,
      entryType: "CREDIT_INR",
      assetType: "INR",
      deltaAmount: 5000.0,
      balanceAfter: 5000.0,
      referenceId: "ORD_001",
      idempotencyKey: "idem_001",
      description: "Sandbox UPI deposit",
      mode: "TEST",
      createdAt: now,
    })
  );

  // Ledger must be immutable
  await assertFails(ledgerRef.update({ deltaAmount: 99999.0 }));
  await assertFails(ledgerRef.delete());
});

test("Cross-user isolation: Alice cannot read or modify Bob's wallet, profile, or KYC", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = getDb(ctx);
    await adminDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      customUserId: "SWG200002",
      fullName: "Bob Verma",
      kycStatus: "NOT_STARTED",
      accountStatus: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("users").doc(BOB_UID).collection("wallets").doc("primary").set({
      userId: BOB_UID,
      availableInr: 1000.0,
      pendingInr: 0.0,
      lockedInr: 0.0,
      gold24kGrams: 0.5,
      gold22kGrams: 0.0,
      gold18kGrams: 0.0,
      silver999Grams: 0.0,
      totalInvestedGoldInr: 4000.0,
      totalInvestedSilverInr: 0.0,
      updatedAt: now,
    });
    await adminDb.collection("kyc_records").doc(BOB_UID).set({
      userId: BOB_UID,
      userName: "Bob Verma",
      mobileNumber: "9123456789",
      panNumber: "ABCDE1234F",
      aadhaarLast4: "4321",
      documentType: "PAN_CARD",
      addressLine: "MG Road",
      state: "Maharashtra",
      city: "Pune",
      pincode: "411001",
      status: "PENDING",
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("wallets").doc("primary").get());
  await assertFails(aliceDb.collection("kyc_records").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).update({ fullName: "Hacked" }));
});

test("Privilege escalation & Email spoofing tests: must reject", async () => {
  const now = new Date();
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      customUserId: "SWG100001",
      fullName: "Alice",
      kycStatus: "NOT_STARTED",
      accountStatus: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    })
  );

  // User cannot self-approve KYC or inject ghost fields
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({ kycStatus: "APPROVED", updatedAt: now }));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({ isAdmin: true, updatedAt: now }));
  await assertFails(aliceDb.collection("admins").doc(ALICE_UID).set({
    uid: ALICE_UID, email: "alice@example.com", displayName: "Alice", role: "SUPER_ADMIN",
    permissions: ["ALL"], isActive: true, createdAt: now, updatedAt: now
  }));
  await assertFails(aliceDb.collection("audit_logs").doc("log_1").set({
    adminUid: ALICE_UID, adminEmail: "alice@example.com", adminRole: "SUPER_ADMIN",
    action: "FAKE", affectedCollection: "users", affectedRecordId: "x",
    previousValue: "", newValue: "", reason: "test", deviceInfo: "dev", createdAt: now
  }));

  // Spoofed admin email with email_verified: false must fail
  const spoofedAdminDb = getDb(testEnv.authenticatedContext("spoof_1", {
    email: "goldsonawane@gmail.com",
    email_verified: false,
  }));
  await assertFails(spoofedAdminDb.collection("kyc_records").get());
  await assertFails(spoofedAdminDb.collection("app_config").doc("settings").set({ appName: "Hacked" }));
});

test("Verified bootstrapped admin: can manage KYC, app_config, and audit_logs", async () => {
  const now = new Date();
  const adminDb = getDb(testEnv.authenticatedContext(ADMIN_UID, {
    email: "goldsonawane@gmail.com",
    email_verified: true,
  }));

  await assertSucceeds(adminDb.collection("app_config").doc("settings").set({
    appName: "SONA-WANE GOLD",
    paymentMode: "TEST",
    gstPercent: 3.0,
    updatedAt: now,
  }));

  await assertSucceeds(adminDb.collection("audit_logs").doc("audit_1").set({
    adminUid: ADMIN_UID,
    adminEmail: "goldsonawane@gmail.com",
    adminRole: "SUPER_ADMIN",
    action: "UPDATE_CONFIG",
    affectedCollection: "app_config",
    affectedRecordId: "settings",
    previousValue: "none",
    newValue: "TEST",
    reason: "Initial setup",
    deviceInfo: "Pixel",
    createdAt: now,
  }));
});

test("Client query alignment & atomic transfers", async () => {
  const now = new Date();
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  const bobDb = getDb(testEnv.authenticatedContext(BOB_UID));

  await assertSucceeds(
    aliceDb.collection("transactions").doc("tx_1").set({
      userId: ALICE_UID,
      txCode: "SWG-TX-1001",
      category: "GOLD_BUY",
      metalPurity: "24K",
      grams: 1.0,
      ratePerGram: 7850.0,
      baseAmount: 7850.0,
      gstAmount: 235.5,
      chargesAmount: 0.0,
      totalAmount: 8085.5,
      paymentMethod: "UPI",
      gatewayOrderId: "ORD_1001",
      gatewayRef: "REF_1001",
      status: "SUCCESSFUL",
      mode: "TEST",
      idempotencyKey: "idem_tx_1",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Scoped query passes, unscoped query fails
  await assertSucceeds(aliceDb.collection("transactions").where("userId", "==", ALICE_UID).get());
  await assertFails(aliceDb.collection("transactions").get());

  // Atomic transfer: Alice sends, Bob claims
  const transferRef = aliceDb.collection("transfers").doc("tr_1");
  await assertSucceeds(transferRef.set({
    senderUid: ALICE_UID,
    senderName: "Alice",
    recipientUid: BOB_UID,
    recipientName: "Bob",
    transferCode: "SWG-TRF-001",
    assetType: "GOLD_24K",
    quantity: 0.25,
    feeInr: 0.0,
    status: "COMPLETED",
    recipientCredited: false,
    idempotencyKey: "idem_tr_1",
    createdAt: now,
  }));

  // Bob claims transfer
  await assertSucceeds(bobDb.collection("transfers").doc("tr_1").update({ recipientCredited: true }));
  // Bob cannot double-claim or alter quantity
  await assertFails(bobDb.collection("transfers").doc("tr_1").update({ recipientCredited: false }));
});

test("Advanced market alerts: frequency, direction, update validation, and isolation", async () => {
  const now = new Date();
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  const bobDb = getDb(testEnv.authenticatedContext(BOB_UID));

  const alertRef = aliceDb.collection("users").doc(ALICE_UID).collection("price_alerts").doc("alt_1");
  await assertSucceeds(
    alertRef.set({
      userId: ALICE_UID,
      metal: "GOLD_22K",
      targetPriceInr: 7250.5,
      direction: "CROSSES",
      frequency: "EVERY_HOUR",
      isActive: true,
      notes: "Buy dip target",
      triggerCount: 0,
      lastTriggeredPrice: 0.0,
      lastObservedPrice: 7210.0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    alertRef.update({
      triggerCount: 1,
      lastTriggeredPrice: 7255.0,
      lastObservedPrice: 7255.0,
      triggeredAt: now,
      updatedAt: now,
    })
  );

  // Invalid frequency must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("price_alerts").doc("alt_bad_freq").set({
      userId: ALICE_UID,
      metal: "SILVER_999",
      targetPriceInr: 95.0,
      direction: "ABOVE",
      frequency: "EVERY_SECOND",
      isActive: true,
      triggerCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Cross-user alert access must fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("price_alerts").doc("alt_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("price_alerts").doc("alt_1").update({ isActive: false }));
});

test("RBAC & unique admin credentials: isolated credential updates and anti-escalation", async () => {
  const now = new Date();
  const superAdminDb = getDb(
    testEnv.authenticatedContext(ADMIN_UID, {
      email: "goldsonawane@gmail.com",
      email_verified: true,
    })
  );
  const bobDb = getDb(
    testEnv.authenticatedContext(BOB_UID, {
      email: "bob.finance@example.com",
      email_verified: true,
    })
  );
  const aliceDb = getDb(
    testEnv.authenticatedContext(ALICE_UID, {
      email: "alice@example.com",
      email_verified: true,
    })
  );

  // Bootstrapped super admin creates own admin doc and assigns Bob as FINANCE_MANAGER
  await assertSucceeds(
    superAdminDb.collection("admins").doc(ADMIN_UID).set({
      uid: ADMIN_UID,
      email: "goldsonawane@gmail.com",
      displayName: "Primary Super Admin",
      operatorId: "SWG-ADM-ROOT",
      role: "SUPER_ADMIN",
      permissions: ["ALL", "USERS", "ROLES"],
      isActive: true,
      biometricAdminEnabled: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    superAdminDb.collection("admins").doc(BOB_UID).set({
      uid: BOB_UID,
      email: "bob.finance@example.com",
      displayName: "Bob Finance",
      operatorId: "SWG-ADM-BOB1",
      role: "FINANCE_MANAGER",
      permissions: ["FINANCE", "WITHDRAWALS", "REPORTS"],
      isActive: true,
      biometricAdminEnabled: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Standard user Alice cannot assign or read admin docs
  await assertFails(aliceDb.collection("admins").doc(BOB_UID).get());
  await assertFails(
    aliceDb.collection("admins").doc(ALICE_UID).set({
      uid: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      role: "ADMIN",
      permissions: ["USERS"],
      isActive: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Active Finance Manager Bob can enroll/update his own unique credentials & lastLoginAt
  await assertSucceeds(
    bobDb.collection("admins").doc(BOB_UID).update({
      operatorId: "SWG-FIN-99",
      adminPinHash: "salt123:hash456",
      biometricAdminEnabled: true,
      lastLoginAt: now,
      updatedAt: now,
    })
  );

  // Finance Manager Bob CANNOT escalate his own role or permissions
  await assertFails(
    bobDb.collection("admins").doc(BOB_UID).update({
      role: "SUPER_ADMIN",
      updatedAt: now,
    })
  );
  await assertFails(
    bobDb.collection("admins").doc(BOB_UID).update({
      permissions: ["ALL", "ROLES"],
      updatedAt: now,
    })
  );

  // Primary Super Admin document cannot be deleted; secondary admin can be revoked
  await assertFails(superAdminDb.collection("admins").doc(ADMIN_UID).delete());
  await assertSucceeds(superAdminDb.collection("admins").doc(BOB_UID).delete());
});

test("CameraX KYC submission: capture fields, bounds, and admin review", async () => {
  const now = new Date();
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }));
  const adminDb = getDb(testEnv.authenticatedContext(ADMIN_UID, {
    email: "goldsonawane@gmail.com",
    email_verified: true,
  }));

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      customUserId: "SWG300003",
      fullName: "Alice Sharma",
      kycStatus: "NOT_STARTED",
      accountStatus: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid CameraX scan KYC submission
  await assertSucceeds(
    aliceDb.collection("kyc_records").doc(ALICE_UID).set({
      userId: ALICE_UID,
      userName: "Alice Sharma",
      mobileNumber: "9876543210",
      panNumber: "ABCDE1234F",
      aadhaarLast4: "5678",
      documentType: "PAN_CARD",
      documentFrontBase64: "/9j/4AAQSkZJRgABAQAAAQABAAD/",
      documentBackBase64: "/9j/4AAQSkZJRgABAQAAAQABAAD/",
      captureMethod: "CAMERAX_SCAN",
      scanQualityScore: 89,
      verificationStep: 3,
      declarationAccepted: true,
      addressLine: "12 Zaveri Bazaar",
      state: "Maharashtra",
      city: "Mumbai",
      pincode: "400002",
      status: "PENDING",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Invalid scanQualityScore > 100 must fail
  await assertFails(
    aliceDb.collection("kyc_records").doc(ALICE_UID).update({
      scanQualityScore: 150,
      updatedAt: now,
    })
  );

  // Admin can inspect CameraX submission and approve it
  await assertSucceeds(adminDb.collection("kyc_records").doc(ALICE_UID).get());
  await assertSucceeds(
    adminDb.collection("kyc_records").doc(ALICE_UID).update({
      status: "APPROVED",
      reviewedByAdminId: ADMIN_UID,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({
      kycStatus: "APPROVED",
      updatedAt: now,
    })
  );
});

test("Market price history collection: auth required, validated create, admin-only update/delete", async () => {
  const now = new Date();
  const unauthDb = getDb(testEnv.unauthenticatedContext());
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  const adminDb = getDb(testEnv.authenticatedContext(ADMIN_UID, {
    email: "goldsonawane@gmail.com",
    email_verified: true,
  }));

  // Unauthenticated read/write denied
  await assertFails(unauthDb.collection("market_price_history").get());
  await assertFails(unauthDb.collection("market_price_history").doc("2026-11-14").get());

  // Signed-in user can create valid snapshot if missing
  await assertSucceeds(
    aliceDb.collection("market_price_history").doc("2026-11-14").set({
      dateStr: "2026-11-14",
      label: "11-14",
      gold24kPerGram: 7850.0,
      gold22kPerGram: 7195.8,
      gold18kPerGram: 5887.5,
      silver999PerGram: 94.5,
      source: "VERIFIED_SPOT_SNAPSHOT",
      timestampEpochMs: 1794643200000,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Invalid dateStr mismatch or negative price fails
  await assertFails(
    aliceDb.collection("market_price_history").doc("2026-11-13").set({
      dateStr: "2026-11-12",
      label: "11-13",
      gold24kPerGram: 7800.0,
      gold22kPerGram: 7150.0,
      gold18kPerGram: 5850.0,
      silver999PerGram: 94.0,
      source: "VERIFIED_SPOT_SNAPSHOT",
      timestampEpochMs: 1794556800000,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Regular user can list/read, cannot overwrite or delete existing history
  await assertSucceeds(aliceDb.collection("market_price_history").get());
  await assertFails(
    aliceDb.collection("market_price_history").doc("2026-11-14").update({
      gold24kPerGram: 100.0,
      updatedAt: now,
    })
  );
  await assertFails(aliceDb.collection("market_price_history").doc("2026-11-14").delete());

  // Verified admin can update or delete
  await assertSucceeds(
    adminDb.collection("market_price_history").doc("2026-11-14").update({
      gold24kPerGram: 7865.0,
      updatedAt: now,
    })
  );
  await assertSucceeds(adminDb.collection("market_price_history").doc("2026-11-14").delete());
});


