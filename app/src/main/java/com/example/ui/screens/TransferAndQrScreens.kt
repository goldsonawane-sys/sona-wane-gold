package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.AppConfig
import com.example.data.model.DirectoryEntry
import com.example.data.model.UserProfile
import com.example.data.model.UserTransfer
import com.example.data.model.WalletHoldings
import com.example.ui.components.GoldOutlinedButton
import com.example.ui.components.GoldPrimaryButton
import com.example.ui.components.KeyValueRow
import com.example.ui.components.LuxuryCard
import com.example.ui.components.SonaWaneTopBar
import com.example.ui.components.StatusPill
import com.example.ui.components.statusColorFor
import com.example.ui.theme.AmberOrange
import com.example.ui.theme.DeepBlack
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.LightGold
import com.example.ui.theme.MonoSmallStyle
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.SecondarySilver
import com.example.ui.theme.SuccessGreen
import com.example.util.BullionQrModule
import com.example.util.Formatters
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

@Composable
fun TransferScreen(
    profile: UserProfile?,
    wallet: WalletHoldings?,
    appConfig: AppConfig,
    recipient: DirectoryEntry?,
    outgoingTransfers: List<UserTransfer>,
    incomingTransfers: List<UserTransfer>,
    initialLookupQuery: String = "",
    isBusy: Boolean,
    onLookupRecipient: (String) -> Unit,
    onClearRecipient: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onVerifyMpin: (String) -> Boolean,
    onVerifyBiometric: ((onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit)? = null,
    onSetupMpin: () -> Unit,
    onExecuteTransfer: (DirectoryEntry, assetType: String, quantity: Double) -> Unit,
    onBack: () -> Unit,
) {
    var queryInput by remember(initialLookupQuery) { mutableStateOf(initialLookupQuery) }
    var assetType by remember { mutableStateOf("GOLD_24K") }
    var qtyInput by remember { mutableStateOf("0.5") }
    var showMpinDialog by remember { mutableStateOf(false) }
    var mpinInput by remember { mutableStateOf("") }
    var mpinError by remember { mutableStateOf<String?>(null) }

    val w = wallet ?: WalletHoldings()
    val maxAvailable = when (assetType) {
        "INR" -> w.availableInr
        "GOLD_24K" -> w.gold24kGrams
        "GOLD_22K" -> w.gold22kGrams
        "GOLD_18K" -> w.gold18kGrams
        else -> w.silver999Grams
    }
    val qtyVal = qtyInput.toDoubleOrNull() ?: 0.0
    val feeInr = if (assetType == "INR") qtyVal * (appConfig.transferFeePercent / 100.0) else 0.0
    val allTransfers = (outgoingTransfers + incomingTransfers).sortedByDescending { it.createdAt }

    Scaffold(
        containerColor = DeepBlack,
        topBar = { SonaWaneTopBar(title = "User-to-User Transfer", subtitle = "Atomic Verified Bullion & Wallet Transfers", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                LuxuryCard(highlightGold = true) {
                    Text("Find Verified Recipient", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = queryInput,
                        onValueChange = { queryInput = it },
                        label = { Text("Enter User ID (SWG...) or 10-digit Mobile") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("transfer_lookup_input"),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        GoldPrimaryButton(
                            text = "Verify User",
                            icon = Icons.Default.Search,
                            enabled = !isBusy && queryInput.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            onClick = { onLookupRecipient(queryInput) },
                        )
                        GoldOutlinedButton(
                            text = "Scan QR",
                            onClick = onOpenQrScanner,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (recipient != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            color = SuccessGreen.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, SuccessGreen),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = SuccessGreen)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(recipient.displayName, style = MaterialTheme.typography.titleMedium, color = PureWhite)
                                        Text("ID: ${recipient.customUserId}", style = MonoSmallStyle, color = LightGold)
                                    }
                                }
                                TextButton(onClick = onClearRecipient) { Text("Change", color = AmberOrange) }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Select Asset to Transfer", style = MaterialTheme.typography.labelLarge, color = PureWhite)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf("GOLD_24K", "GOLD_22K", "GOLD_18K", "SILVER_999", "INR").forEach { a ->
                                FilterChip(selected = assetType == a, onClick = { assetType = a }, label = { Text(a.replace("_", " ")) })
                            }
                        }
                        Text(
                            text = "Available: ${if (assetType == "INR") Formatters.formatInr(maxAvailable) else Formatters.formatGrams(maxAvailable)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = LightGold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = qtyInput,
                            onValueChange = { qtyInput = it },
                            label = { Text(if (assetType == "INR") "Amount (₹)" else "Quantity (grams)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        KeyValueRow("Recipient", "${recipient.displayName} (${recipient.customUserId})")
                        KeyValueRow("Transfer Fee", Formatters.formatInr(feeInr))
                        Spacer(modifier = Modifier.height(10.dp))
                        GoldPrimaryButton(
                            text = "Confirm & Send Transfer",
                            enabled = !isBusy && qtyVal > 0.0 && qtyVal <= maxAvailable + 1e-6,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (profile?.mpinHash.isNullOrBlank()) onSetupMpin() else showMpinDialog = true
                            },
                        )
                    }
                }
            }

            item {
                Text("Transfer History", style = MaterialTheme.typography.titleMedium, color = PureWhite)
            }
            if (allTransfers.isEmpty()) {
                item {
                    LuxuryCard {
                        Text("No transfers yet.", style = MaterialTheme.typography.bodyMedium, color = SecondarySilver)
                    }
                }
            } else {
                items(allTransfers, key = { it.id }) { trf ->
                    val isSent = trf.senderUid == profile?.userId
                    LuxuryCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = if (isSent) "Sent to ${trf.recipientName}" else "Received from ${trf.senderName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PureWhite,
                                )
                                Text("${trf.transferCode} • ${trf.assetType}", style = MonoSmallStyle, color = SecondarySilver)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (trf.assetType == "INR") Formatters.formatInr(trf.quantity) else Formatters.formatGrams(trf.quantity),
                                    style = MonoSmallStyle,
                                    color = if (isSent) AmberOrange else SuccessGreen,
                                )
                                StatusPill(text = trf.status, color = statusColorFor(trf.status))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMpinDialog && recipient != null) {
        val biometricAllowed = profile?.biometricEnabled == true && onVerifyBiometric != null
        AlertDialog(
            onDismissRequest = { showMpinDialog = false },
            title = { Text("Authorize Transfer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sending ${if (assetType == "INR") Formatters.formatInr(qtyVal) else Formatters.formatGrams(qtyVal)} ($assetType) to ${recipient.displayName}.")
                    OutlinedTextField(
                        value = mpinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) mpinInput = it },
                        label = { Text("4-Digit MPIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (biometricAllowed) {
                        GoldOutlinedButton(
                            text = "Authorize with Fingerprint / Face",
                            icon = Icons.Default.Fingerprint,
                            onClick = {
                                onVerifyBiometric!!(
                                    {
                                        showMpinDialog = false
                                        mpinInput = ""
                                        mpinError = null
                                        onExecuteTransfer(recipient, assetType, qtyVal)
                                    },
                                    { err -> mpinError = err },
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("transfer_biometric_confirm_btn"),
                        )
                    }
                    if (!mpinError.isNullOrBlank()) Text(mpinError!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (onVerifyMpin(mpinInput)) {
                        showMpinDialog = false
                        mpinInput = ""
                        mpinError = null
                        onExecuteTransfer(recipient, assetType, qtyVal)
                    } else {
                        mpinError = "Incorrect MPIN"
                    }
                }) { Text("Authorize", color = RoyalGold) }
            },
            dismissButton = { TextButton(onClick = { showMpinDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun MyQrScreen(
    profile: UserProfile?,
    onOpenScanner: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val customId = profile?.customUserId ?: "SWG000000"
    val displayName = profile?.fullName ?: "Sona-Wane Investor"

    var qrVersion by remember { mutableStateOf(0) }
    var maskDisplayName by remember { mutableStateOf(false) }
    val encryptedEnvelope = remember(customId, displayName, qrVersion, maskDisplayName) {
        com.example.util.BullionQrModule.generateEncryptedPayload(
            customUserId = customId,
            displayName = if (maskDisplayName) "" else displayName,
        )
    }
    val qrBitmap = remember(encryptedEnvelope.encryptedToken) {
        com.example.util.BullionQrModule.generateQrBitmap(encryptedEnvelope.encryptedToken, 580)
    }
    var copiedNotice by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = DeepBlack,
        topBar = { SonaWaneTopBar(title = "My Encrypted Bullion QR", subtitle = "AES-256-GCM Protected P2P Transfer ID", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LuxuryCard(highlightGold = true) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusPill(text = "AES-256-GCM", color = SuccessGreen)
                        StatusPill(text = "Nonce: ${encryptedEnvelope.nonce}", color = RoyalGold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(displayName, style = MaterialTheme.typography.headlineMedium, color = PureWhite)
                    Text("User ID: $customId", style = MonoSmallStyle, color = LightGold)
                    Spacer(modifier = Modifier.height(14.dp))
                    if (qrBitmap != null) {
                        Box(
                            modifier = Modifier
                                .size(244.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .border(2.dp, RoyalGold, RoundedCornerShape(16.dp))
                                .padding(12.dp)
                                .testTag("encrypted_qr_image_box"),
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Encrypted SONA-WANE GOLD QR Code",
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Token: ${encryptedEnvelope.encryptedToken.take(18)}...${encryptedEnvelope.encryptedToken.takeLast(8)}",
                        style = MonoSmallStyle,
                        color = LightGold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mask Name in Encrypted QR", style = MaterialTheme.typography.bodySmall, color = PureWhite)
                            Text("Only reveal User ID inside encrypted token", style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
                        }
                        androidx.compose.material3.Switch(
                            checked = maskDisplayName,
                            onCheckedChange = { maskDisplayName = it },
                            modifier = Modifier.testTag("qr_mask_name_switch"),
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Your User ID is AES-256-GCM encrypted with a unique random IV. Plain QR readers cannot inspect your identity or bank/PAN details.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondarySilver,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                GoldOutlinedButton(
                    text = "Rotate QR Key",
                    onClick = {
                        qrVersion++
                        copiedNotice = "Rotated with a fresh unique IV"
                    },
                    modifier = Modifier.weight(1f).testTag("rotate_encrypted_qr_btn"),
                )
                GoldOutlinedButton(
                    text = "Copy Encrypted Token",
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Encrypted Bullion QR", encryptedEnvelope.encryptedToken))
                        copiedNotice = "Encrypted QR token copied!"
                    },
                    modifier = Modifier.weight(1f).testTag("copy_encrypted_qr_btn"),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                GoldOutlinedButton(
                    text = "Copy User ID",
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("User ID", customId))
                        copiedNotice = "User ID copied!"
                    },
                    modifier = Modifier.weight(1f),
                )
                GoldPrimaryButton(
                    text = "Share QR Image",
                    icon = Icons.Default.Share,
                    onClick = {
                        com.example.util.BullionQrModule.shareEncryptedQr(
                            context = context,
                            encryptedPayload = encryptedEnvelope.encryptedToken,
                            customUserId = customId,
                        )
                    },
                    modifier = Modifier.weight(1f).testTag("share_encrypted_qr_btn"),
                )
            }

            if (!copiedNotice.isNullOrBlank()) {
                Text(copiedNotice!!, style = MaterialTheme.typography.labelMedium, color = SuccessGreen)
            }

            GoldPrimaryButton(
                text = "Scan Another User's Encrypted QR",
                icon = Icons.Default.QrCodeScanner,
                onClick = onOpenScanner,
                modifier = Modifier.fillMaxWidth().testTag("open_qr_scanner_btn"),
            )
        }
    }
}

@Composable
fun QrScannerScreen(
    onQrDetected: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var manualCode by remember { mutableStateOf("") }
    var scannedOnce by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted },
    )

    Scaffold(
        containerColor = DeepBlack,
        topBar = { SonaWaneTopBar(title = "Scan Transfer QR", subtitle = "Decrypts SONA-WANE Encrypted QR", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (hasCameraPermission) {
                val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
                DisposableEffect(Unit) {
                    onDispose { cameraExecutor.shutdown() }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.5.dp, RoyalGold, RoundedCornerShape(18.dp)),
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }
                                val reader = MultiFormatReader().apply {
                                    setHints(
                                        mapOf(
                                            com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE),
                                            com.google.zxing.DecodeHintType.TRY_HARDER to true,
                                        )
                                    )
                                }
                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                    if (!scannedOnce) {
                                        val plane = imageProxy.planes.firstOrNull()
                                        if (plane != null) {
                                            val buffer = plane.buffer
                                            val bytes = ByteArray(buffer.remaining())
                                            buffer.get(bytes)
                                            val source = PlanarYUVLuminanceSource(
                                                bytes,
                                                imageProxy.width,
                                                imageProxy.height,
                                                0,
                                                0,
                                                imageProxy.width,
                                                imageProxy.height,
                                                false,
                                            )
                                            val bitmap = BinaryBitmap(HybridBinarizer(source))
                                            runCatching {
                                                val result = reader.decodeWithState(bitmap)
                                                val rawText = result.text.orEmpty()
                                                if (rawText.isNotBlank()) {
                                                    val resolved = com.example.util.BullionQrModule.resolveUserIdOrEmpty(rawText)
                                                    if (resolved.isNotBlank()) {
                                                        scannedOnce = true
                                                        previewView.post {
                                                            scanError = null
                                                            onQrDetected(resolved)
                                                        }
                                                    } else {
                                                        previewView.post {
                                                            scanError = "QR code failed authentication tag verification."
                                                        }
                                                    }
                                                }
                                            }
                                            reader.reset()
                                        }
                                    }
                                    imageProxy.close()
                                }
                                runCatching {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        analysis,
                                    )
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                    )
                }
            } else {
                LuxuryCard {
                    Text("Camera Permission Required for QR Scanning", style = MaterialTheme.typography.titleMedium, color = LightGold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Grant camera permission only when scanning another user's encrypted QR code, or paste their User ID / SWGQR2 token below.", style = MaterialTheme.typography.bodySmall, color = SecondarySilver)
                    Spacer(modifier = Modifier.height(12.dp))
                    GoldPrimaryButton(
                        text = "Grant Camera Permission",
                        onClick = { launcher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (!scanError.isNullOrBlank()) {
                Text(scanError!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
            }

            LuxuryCard {
                Text("Or Enter Encrypted QR Token / User ID", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualCode,
                    onValueChange = {
                        manualCode = it
                        scanError = null
                    },
                    label = { Text("Encrypted Token (SWGQR2...) or User ID (SWG...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("manual_qr_input"),
                )
                Spacer(modifier = Modifier.height(10.dp))
                GoldPrimaryButton(
                    text = "Verify & Decrypt Recipient",
                    enabled = manualCode.isNotBlank(),
                    onClick = {
                        val resolved = com.example.util.BullionQrModule.resolveUserIdOrEmpty(manualCode)
                        if (resolved.isNotBlank()) {
                            scanError = null
                            onQrDetected(resolved)
                        } else {
                            scanError = "Invalid or tampered encrypted QR token. Ask recipient to rotate their QR."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("verify_manual_qr_btn"),
                )
            }
        }
    }
}
