package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.material3.minimumInteractiveComponentSize
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
    initialAssetType: String = "GOLD_24K",
    initialQuantity: String = "0.5",
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
    var assetType by remember(initialAssetType) {
        mutableStateOf(
            initialAssetType.takeIf { it in listOf("GOLD_24K", "GOLD_22K", "GOLD_18K", "SILVER_999", "INR") }
                ?: "GOLD_24K"
        )
    }
    var qtyInput by remember(initialQuantity) {
        mutableStateOf(initialQuantity.takeIf { it.toDoubleOrNull()?.let { v -> v > 0.0 } == true } ?: "0.5")
    }
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
        contentWindowInsets = WindowInsets.safeDrawing,
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
                            icon = Icons.Default.QrCodeScanner,
                            onClick = onOpenQrScanner,
                            modifier = Modifier.weight(1f).testTag("transfer_scan_qr_btn"),
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
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = SuccessGreen)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(recipient.displayName, style = MaterialTheme.typography.titleMedium, color = PureWhite)
                                        Text("Wallet: ${BullionQrModule.formatWalletAddress(recipient.customUserId)}", style = MonoSmallStyle, color = LightGold)
                                        Text("ID: ${recipient.customUserId}", style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
                                    }
                                }
                                TextButton(
                                    onClick = onClearRecipient,
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) { Text("Change", color = AmberOrange) }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Select Asset to Transfer", style = MaterialTheme.typography.labelLarge, color = PureWhite)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf("GOLD_24K", "GOLD_22K", "GOLD_18K", "SILVER_999", "INR").forEach { a ->
                                FilterChip(
                                    selected = assetType == a,
                                    onClick = { assetType = a },
                                    label = { Text(BullionQrModule.formatAssetLabel(a)) },
                                    modifier = Modifier.testTag("transfer_asset_chip_${a.lowercase()}"),
                                )
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
                            modifier = Modifier.fillMaxWidth().testTag("transfer_qty_input"),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        KeyValueRow("Recipient", "${recipient.displayName} (${recipient.customUserId})")
                        KeyValueRow("Transfer Fee", Formatters.formatInr(feeInr))
                        Spacer(modifier = Modifier.height(10.dp))
                        GoldPrimaryButton(
                            text = "Confirm & Send Transfer",
                            enabled = !isBusy && qtyVal > 0.0 && qtyVal <= maxAvailable + 1e-6,
                            modifier = Modifier.fillMaxWidth().testTag("transfer_confirm_btn"),
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
                    if (biometricAllowed && onVerifyBiometric != null) {
                        GoldOutlinedButton(
                            text = "Authorize with Fingerprint / Face",
                            icon = Icons.Default.Fingerprint,
                            onClick = {
                                onVerifyBiometric(
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
                    mpinError?.takeIf { it.isNotBlank() }?.let { errText ->
                        Text(errText, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    }
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
    val walletAddress = remember(customId) { BullionQrModule.formatWalletAddress(customId) }
    val displayName = profile?.fullName ?: "Sona-Wane Investor"

    var qrVersion by remember { mutableStateOf(0) }
    var maskDisplayName by remember { mutableStateOf(false) }
    var requestedAsset by remember { mutableStateOf("GOLD_24K") }
    var requestedGramsInput by remember { mutableStateOf("") }
    val parsedGrams = requestedGramsInput.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.0

    val encryptedEnvelope = remember(customId, displayName, qrVersion, maskDisplayName, requestedAsset, parsedGrams) {
        BullionQrModule.generateEncryptedPayload(
            customUserId = customId,
            displayName = if (maskDisplayName) "" else displayName,
            requestedAsset = if (requestedAsset == "ANY") "" else requestedAsset,
            requestedGrams = parsedGrams,
        )
    }
    val qrBitmap = remember(encryptedEnvelope.encryptedToken) {
        BullionQrModule.generateQrBitmap(encryptedEnvelope.encryptedToken, 580)
    }
    var copiedNotice by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = DeepBlack,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            SonaWaneTopBar(
                title = "Receive Bullion Wallet QR",
                subtitle = "Unique ZXing + AES-256-GCM Wallet Address QR",
                onBack = onBack,
            )
        },
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
                        StatusPill(text = "ZXING • AES-256-GCM", color = SuccessGreen)
                        StatusPill(text = "Nonce: ${encryptedEnvelope.nonce}", color = RoyalGold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(displayName, style = MaterialTheme.typography.headlineMedium, color = PureWhite)
                    Text(
                        text = "Wallet Address: $walletAddress",
                        style = MonoSmallStyle,
                        color = LightGold,
                        modifier = Modifier.testTag("wallet_address_qr_text"),
                    )
                    Text("User ID: $customId", style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
                    if (parsedGrams > 0.0 || requestedAsset != "ANY") {
                        Spacer(modifier = Modifier.height(6.dp))
                        val reqDesc = buildString {
                            append("Requesting ")
                            if (parsedGrams > 0.0) {
                                append(Formatters.formatGrams(parsedGrams))
                                append(" of ")
                            }
                            append(BullionQrModule.formatAssetLabel(requestedAsset))
                        }
                        StatusPill(text = reqDesc, color = AmberOrange)
                    }
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
                                contentDescription = "Unique ZXing Wallet Address QR Code for Receiving Gold and Silver",
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
                            Text("Only reveal wallet address inside encrypted token", style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
                        }
                        androidx.compose.material3.Switch(
                            checked = maskDisplayName,
                            onCheckedChange = { maskDisplayName = it },
                            modifier = Modifier.testTag("qr_mask_name_switch"),
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Other users scan this ZXing QR code to auto-fill your verified wallet address and send 24K, 22K, 18K Gold or 999 Silver transfers directly.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondarySilver,
                    )
                }
            }

            // Optional Gold & Silver Transfer Request Configuration
            LuxuryCard {
                Text("Gold & Silver Receive Options (Encoded in QR)", style = MaterialTheme.typography.titleMedium, color = LightGold)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            "GOLD_24K" to "24K Gold",
                            "GOLD_22K" to "22K Gold",
                            "GOLD_18K" to "18K Gold",
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = requestedAsset == key,
                                onClick = { requestedAsset = key },
                                label = { Text(label) },
                                modifier = Modifier.testTag("qr_asset_chip_${key.lowercase()}"),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            "SILVER_999" to "999 Silver",
                            "ANY" to "Open Transfer",
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = requestedAsset == key,
                                onClick = { requestedAsset = key },
                                label = { Text(label) },
                                modifier = Modifier.testTag("qr_asset_chip_${key.lowercase()}"),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("" to "Any g", "0.5" to "0.5g", "1" to "1g", "2" to "2g", "5" to "5g").forEach { (valStr, chipLabel) ->
                        FilterChip(
                            selected = requestedGramsInput == valStr,
                            onClick = { requestedGramsInput = valStr },
                            label = { Text(chipLabel) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = requestedGramsInput,
                    onValueChange = { requestedGramsInput = it },
                    label = { Text("Requested Gold / Silver Weight in grams (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("qr_requested_grams_input"),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                GoldOutlinedButton(
                    text = "Rotate Unique QR",
                    onClick = {
                        qrVersion++
                        copiedNotice = "Generated fresh unique ZXing QR with new IV"
                    },
                    modifier = Modifier.weight(1f).testTag("rotate_encrypted_qr_btn"),
                )
                GoldOutlinedButton(
                    text = "Copy QR Token",
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Encrypted Wallet QR", encryptedEnvelope.encryptedToken))
                        copiedNotice = "Encrypted wallet QR token copied!"
                    },
                    modifier = Modifier.weight(1f).testTag("copy_encrypted_qr_btn"),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                GoldOutlinedButton(
                    text = "Copy Wallet Address",
                    icon = Icons.Default.ContentCopy,
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Wallet Address", walletAddress))
                        copiedNotice = "Wallet address $walletAddress copied!"
                    },
                    modifier = Modifier.weight(1f).testTag("copy_wallet_address_btn"),
                )
                GoldPrimaryButton(
                    text = "Share QR Image",
                    icon = Icons.Default.Share,
                    onClick = {
                        BullionQrModule.shareEncryptedQr(
                            context = context,
                            encryptedPayload = encryptedEnvelope.encryptedToken,
                            customUserId = customId,
                        )
                    },
                    modifier = Modifier.weight(1f).testTag("share_encrypted_qr_btn"),
                )
            }

            copiedNotice?.takeIf { it.isNotBlank() }?.let { noticeText ->
                Text(noticeText, style = MaterialTheme.typography.labelMedium, color = SuccessGreen)
            }

            GoldPrimaryButton(
                text = "Scan Another User's Wallet QR",
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

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted },
    )

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            val decodedText = runCatching {
                val bmp = decodeBitmapFromUri(context, uri) ?: return@runCatching null
                BullionQrModule.decodeQrFromBitmap(bmp)
            }.getOrNull()

            if (decodedText.isNullOrBlank()) {
                scanError = "No QR code found in the selected photo. Choose a clear ZXing wallet QR screenshot."
            } else {
                val resolved = BullionQrModule.resolveUserIdOrEmpty(decodedText)
                if (resolved.isNotBlank()) {
                    scanError = null
                    onQrDetected(decodedText)
                } else {
                    scanError = "QR code failed AES-256-GCM authentication verification."
                }
            }
        },
    )

    Scaffold(
        containerColor = DeepBlack,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { SonaWaneTopBar(title = "Scan Transfer QR", subtitle = "ZXing Live Camera & Photo QR Scanner", onBack = onBack) },
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
                                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                    try {
                                        if (!scannedOnce) {
                                            val plane = imageProxy.planes.firstOrNull()
                                            if (plane != null) {
                                                val width = imageProxy.width
                                                val height = imageProxy.height
                                                val packedY = extractTightYPlane(
                                                    buffer = plane.buffer,
                                                    width = width,
                                                    height = height,
                                                    rowStride = plane.rowStride,
                                                    pixelStride = plane.pixelStride,
                                                )
                                                val source = PlanarYUVLuminanceSource(
                                                    packedY,
                                                    width,
                                                    height,
                                                    0,
                                                    0,
                                                    width,
                                                    height,
                                                    false,
                                                )
                                                val bitmap = BinaryBitmap(HybridBinarizer(source))
                                                runCatching {
                                                    val result = reader.decodeWithState(bitmap)
                                                    val rawText = result.text.orEmpty()
                                                    if (rawText.isNotBlank()) {
                                                        val resolved = BullionQrModule.resolveUserIdOrEmpty(rawText)
                                                        if (resolved.isNotBlank()) {
                                                            scannedOnce = true
                                                            previewView.post {
                                                                scanError = null
                                                                onQrDetected(rawText)
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
                                    } finally {
                                        imageProxy.close()
                                    }
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
                    Text("Grant camera permission only when scanning another user's encrypted QR code, pick a QR screenshot from gallery, or paste their Wallet Address / SWGQR2 token below.", style = MaterialTheme.typography.bodySmall, color = SecondarySilver)
                    Spacer(modifier = Modifier.height(12.dp))
                    GoldPrimaryButton(
                        text = "Grant Camera Permission",
                        onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.fillMaxWidth().testTag("grant_camera_qr_btn"),
                    )
                }
            }

            GoldOutlinedButton(
                text = "Scan QR Photo from Gallery (ZXing)",
                icon = Icons.Default.PhotoLibrary,
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.fillMaxWidth().testTag("scan_qr_gallery_btn"),
            )

            scanError?.takeIf { it.isNotBlank() }?.let { scanErrText ->
                Text(
                    text = scanErrText,
                    color = ErrorRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("qr_scan_error_text"),
                )
            }

            LuxuryCard {
                Text("Or Enter Encrypted QR Token / Wallet Address", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualCode,
                    onValueChange = {
                        manualCode = it
                        scanError = null
                    },
                    label = { Text("Token (SWGQR2...), Wallet (SWG-VAULT-...), or User ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("manual_qr_input"),
                )
                Spacer(modifier = Modifier.height(10.dp))
                GoldPrimaryButton(
                    text = "Verify & Decrypt Recipient",
                    enabled = manualCode.isNotBlank(),
                    onClick = {
                        val resolved = BullionQrModule.resolveUserIdOrEmpty(manualCode)
                        if (resolved.isNotBlank()) {
                            scanError = null
                            onQrDetected(manualCode)
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

private fun extractTightYPlane(
    buffer: java.nio.ByteBuffer,
    width: Int,
    height: Int,
    rowStride: Int,
    pixelStride: Int,
): ByteArray {
    buffer.rewind()
    val tight = ByteArray(width * height)
    if (pixelStride == 1 && rowStride == width && buffer.remaining() >= tight.size) {
        buffer.get(tight, 0, tight.size)
        return tight
    }
    val rowBuf = ByteArray(rowStride)
    var outIdx = 0
    for (row in 0 until height) {
        val toRead = minOf(buffer.remaining(), rowStride)
        if (toRead <= 0) break
        buffer.get(rowBuf, 0, toRead)
        if (pixelStride == 1) {
            val copyLen = minOf(width, toRead)
            System.arraycopy(rowBuf, 0, tight, outIdx, copyLen)
            outIdx += copyLen
        } else {
            for (col in 0 until width) {
                val idx = col * pixelStride
                if (idx < toRead) {
                    tight[outIdx++] = rowBuf[idx]
                }
            }
        }
    }
    return tight
}

private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.isMutableRequired = false
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }
}
