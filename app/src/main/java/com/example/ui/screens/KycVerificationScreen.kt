package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.KycRecord
import com.example.data.model.UserProfile
import com.example.ui.components.GoldOutlinedButton
import com.example.ui.components.GoldPrimaryButton
import com.example.ui.components.KeyValueRow
import com.example.ui.components.LuxuryCard
import com.example.ui.components.OrangeActionButton
import com.example.ui.components.SonaWaneTopBar
import com.example.ui.components.StatusPill
import com.example.ui.components.statusColorFor
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs

@Composable
fun KycVerificationScreen(
    profile: UserProfile?,
    kycRecord: KycRecord?,
    isBusy: Boolean,
    onBack: () -> Unit,
    onSubmitKyc: (
        fullName: String,
        mobile: String,
        pan: String,
        aadhaarLast4: String,
        docType: String,
        docUri: String?,
        address: String,
        state: String,
        city: String,
        pincode: String,
        documentFrontBase64: String?,
        documentBackBase64: String?,
        captureMethod: String,
        scanQualityScore: Int,
        declarationAccepted: Boolean,
    ) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isApproved = profile?.kycStatus == "APPROVED"

    var currentStep by remember { mutableIntStateOf(if (isApproved) 3 else 1) }

    // Step 1 fields
    var fullName by remember(kycRecord, profile) {
        mutableStateOf(kycRecord?.userName?.ifBlank { profile?.fullName.orEmpty() } ?: profile?.fullName.orEmpty())
    }
    var mobile by remember(kycRecord, profile) {
        mutableStateOf(kycRecord?.mobileNumber?.ifBlank { profile?.mobileNumber.orEmpty() } ?: profile?.mobileNumber.orEmpty())
    }
    var pan by remember(kycRecord) { mutableStateOf(kycRecord?.panNumber.orEmpty()) }
    var aadhaarLast4 by remember(kycRecord) { mutableStateOf(kycRecord?.aadhaarLast4.orEmpty()) }
    var docType by remember(kycRecord) { mutableStateOf(kycRecord?.documentType ?: "PAN_CARD") }
    var address by remember(kycRecord, profile) {
        mutableStateOf(kycRecord?.addressLine?.ifBlank { profile?.address.orEmpty() } ?: profile?.address.orEmpty())
    }
    var city by remember(kycRecord, profile) {
        mutableStateOf(kycRecord?.city?.ifBlank { profile?.city.orEmpty() } ?: profile?.city.orEmpty())
    }
    var state by remember(kycRecord, profile) {
        mutableStateOf(kycRecord?.state?.ifBlank { profile?.state.orEmpty() } ?: profile?.state.orEmpty())
    }
    var pincode by remember(kycRecord, profile) {
        mutableStateOf(kycRecord?.pincode?.ifBlank { profile?.pincode.orEmpty() } ?: profile?.pincode.orEmpty())
    }

    // Step 2 CameraX ID Capture fields
    var frontUri by remember(kycRecord) { mutableStateOf(kycRecord?.documentRefUri) }
    var frontBase64 by remember(kycRecord) { mutableStateOf(kycRecord?.documentFrontBase64) }
    var backBase64 by remember(kycRecord) { mutableStateOf(kycRecord?.documentBackBase64) }
    var captureMethod by remember(kycRecord) { mutableStateOf(kycRecord?.captureMethod ?: "CAMERAX_SCAN") }
    var scanQualityScore by remember(kycRecord) { mutableIntStateOf(kycRecord?.scanQualityScore ?: 0) }

    var captureSide by remember { mutableStateOf("FRONT") } // FRONT | BACK
    var useFrontCamera by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    var liveQualityScore by remember { mutableIntStateOf(0) }
    var liveScanHint by remember { mutableStateOf("Position ID inside the golden frame") }
    var isCapturing by remember { mutableStateOf(false) }

    // Step 3 Declaration & Error
    var declarationAccepted by remember(kycRecord) { mutableStateOf(kycRecord?.declarationAccepted ?: false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted },
    )

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                val encoded = encodeUriToCompressedBase64(context, uri)
                if (encoded != null) {
                    if (captureSide == "FRONT") {
                        frontBase64 = encoded
                        frontUri = uri.toString()
                    } else {
                        backBase64 = encoded
                    }
                    captureMethod = "PHOTO_PICKER"
                    scanQualityScore = maxOf(scanQualityScore, 78)
                    errorMsg = null
                } else {
                    errorMsg = "Could not decode selected ID image."
                }
            }
        },
    )

    val panValid = remember(pan) { Regex("^[A-Z]{5}[0-9]{4}[A-Z]$").matches(pan.trim()) }
    val frontBitmap = remember(frontBase64) { decodeBase64Bitmap(frontBase64) }
    val backBitmap = remember(backBase64) { decodeBase64Bitmap(backBase64) }

    Scaffold(
        containerColor = DeepBlack,
        topBar = {
            SonaWaneTopBar(
                title = "KYC & ID Verification",
                subtitle = if (isApproved) "Compliance Verified" else "Step $currentStep of 3 • CameraX ID Scan",
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // KYC Status Banner
            LuxuryCard(highlightGold = isApproved) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Compliance Status", style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
                        Text(
                            text = profile?.kycStatus ?: "NOT_STARTED",
                            style = MaterialTheme.typography.titleMedium,
                            color = LightGold,
                        )
                    }
                    StatusPill(
                        text = profile?.kycStatus ?: "NOT_STARTED",
                        color = statusColorFor(profile?.kycStatus ?: "NOT_STARTED"),
                    )
                }
                if (!kycRecord?.rejectionReason.isNullOrBlank() && !isApproved) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Admin Note: ${kycRecord?.rejectionReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                    )
                }
            }

            if (isApproved) {
                ApprovedKycSummaryCard(
                    kycRecord = kycRecord,
                    profile = profile,
                    frontBitmap = frontBitmap,
                    backBitmap = backBitmap,
                )
            } else {
                // 3-Step Stepper Indicator
                KycStepperBar(
                    currentStep = currentStep,
                    onSelectStep = { target ->
                        if (target < currentStep) {
                            errorMsg = null
                            currentStep = target
                        }
                    },
                )

                when (currentStep) {
                    1 -> {
                        LuxuryCard {
                            Text("Step 1: Identity, PAN & Residential Details", style = MaterialTheme.typography.titleMedium, color = LightGold)
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = fullName,
                                    onValueChange = { fullName = it },
                                    label = { Text("Legal Full Name (as on PAN) *") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("kyc_name_input"),
                                )
                                OutlinedTextField(
                                    value = mobile,
                                    onValueChange = { mobile = it.filter { c -> c.isDigit() }.take(10) },
                                    label = { Text("Registered 10-Digit Mobile *") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("kyc_mobile_input"),
                                )
                                OutlinedTextField(
                                    value = pan,
                                    onValueChange = { pan = it.uppercase().take(10) },
                                    label = { Text("PAN Number (e.g. ABCDE1234F) *") },
                                    supportingText = {
                                        Text(
                                            text = if (pan.isEmpty()) "10-character alphanumeric PAN"
                                            else if (panValid) "Valid PAN format"
                                            else "Format: 5 letters + 4 digits + 1 letter",
                                            color = if (panValid) SuccessGreen else AmberOrange,
                                        )
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("kyc_pan_input"),
                                )
                                OutlinedTextField(
                                    value = aadhaarLast4,
                                    onValueChange = { aadhaarLast4 = it.filter { c -> c.isDigit() }.take(4) },
                                    label = { Text("Aadhaar Last 4 Digits *") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("kyc_aadhaar_input"),
                                )

                                Text("Select Government ID Document", style = MaterialTheme.typography.labelLarge, color = LightGold)
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    listOf("PAN_CARD", "AADHAAR", "PASSPORT", "DRIVING_LICENSE").forEach { d ->
                                        FilterChip(
                                            selected = docType == d,
                                            onClick = { docType = d },
                                            label = { Text(d.replace("_", " ")) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = RoyalGold,
                                                selectedLabelColor = DeepBlack,
                                            ),
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = address,
                                    onValueChange = { address = it },
                                    label = { Text("Residential Address *") },
                                    modifier = Modifier.fillMaxWidth().testTag("kyc_address_input"),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = city,
                                        onValueChange = { city = it },
                                        label = { Text("City *") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("kyc_city_input"),
                                    )
                                    OutlinedTextField(
                                        value = state,
                                        onValueChange = { state = it },
                                        label = { Text("State *") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("kyc_state_input"),
                                    )
                                }
                                OutlinedTextField(
                                    value = pincode,
                                    onValueChange = { pincode = it.filter { c -> c.isDigit() }.take(6) },
                                    label = { Text("6-Digit PIN Code *") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("kyc_pincode_input"),
                                )
                            }
                        }

                        if (!errorMsg.isNullOrBlank()) {
                            Text(errorMsg!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                        }

                        GoldPrimaryButton(
                            text = "Continue to ID Scanner (Step 2)",
                            icon = Icons.Default.DocumentScanner,
                            modifier = Modifier.fillMaxWidth().testTag("kyc_step1_next_btn"),
                            onClick = {
                                if (fullName.trim().length < 2) {
                                    errorMsg = "Enter your legal full name."
                                    return@GoldPrimaryButton
                                }
                                if (mobile.length != 10) {
                                    errorMsg = "Enter a valid 10-digit mobile number."
                                    return@GoldPrimaryButton
                                }
                                if (!panValid) {
                                    errorMsg = "Enter a valid 10-character PAN (e.g. ABCDE1234F)."
                                    return@GoldPrimaryButton
                                }
                                if (aadhaarLast4.length != 4) {
                                    errorMsg = "Enter the last 4 digits of your Aadhaar."
                                    return@GoldPrimaryButton
                                }
                                if (address.trim().length < 3 || city.trim().length < 2 || state.trim().length < 2 || pincode.length != 6) {
                                    errorMsg = "Complete residential address, city, state, and 6-digit PIN."
                                    return@GoldPrimaryButton
                                }
                                errorMsg = null
                                currentStep = 2
                            },
                        )
                    }

                    2 -> {
                        LuxuryCard(highlightGold = true) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text("Step 2: CameraX ID Scanner", style = MaterialTheme.typography.titleMedium, color = LightGold)
                                    Text(
                                        text = "Document: ${docType.replace("_", " ")} • Side: $captureSide",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SecondarySilver,
                                    )
                                }
                                StatusPill(
                                    text = if (liveQualityScore >= 60) "QUALITY ${liveQualityScore}%" else "SCANNING",
                                    color = if (liveQualityScore >= 60) SuccessGreen else AmberOrange,
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            // Front / Back side switcher
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = captureSide == "FRONT",
                                    onClick = { captureSide = "FRONT" },
                                    label = { Text(if (frontBase64 != null) "Front (Captured)" else "Front Side *") },
                                    modifier = Modifier.testTag("kyc_side_front_chip"),
                                )
                                FilterChip(
                                    selected = captureSide == "BACK",
                                    onClick = { captureSide = "BACK" },
                                    label = { Text(if (backBase64 != null) "Back (Captured)" else "Back Side (Optional)") },
                                    modifier = Modifier.testTag("kyc_side_back_chip"),
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            val activeBitmap = if (captureSide == "FRONT") frontBitmap else backBitmap
                            if (activeBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(1.5.dp, SuccessGreen, RoundedCornerShape(16.dp)),
                                ) {
                                    Image(
                                        bitmap = activeBitmap.asImageBitmap(),
                                        contentDescription = "Captured $captureSide ID document",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    Surface(
                                        color = DeepBlack.copy(alpha = 0.78f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                                    ) {
                                        Text(
                                            text = "Captured • ${scanQualityScore}% clarity",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SuccessGreen,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                GoldOutlinedButton(
                                    text = "Retake $captureSide Scan",
                                    icon = Icons.Default.Refresh,
                                    onClick = {
                                        if (captureSide == "FRONT") {
                                            frontBase64 = null
                                            frontUri = null
                                        } else {
                                            backBase64 = null
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("kyc_retake_btn"),
                                )
                            } else if (hasCameraPermission) {
                                CameraXDocumentScannerView(
                                    lifecycleOwner = lifecycleOwner,
                                    useFrontCamera = useFrontCamera,
                                    torchEnabled = torchEnabled,
                                    isCapturing = isCapturing,
                                    liveQualityScore = liveQualityScore,
                                    liveScanHint = liveScanHint,
                                    onAnalysisUpdate = { score, hint ->
                                        liveQualityScore = score
                                        liveScanHint = hint
                                    },
                                    onToggleTorch = { torchEnabled = !torchEnabled },
                                    onFlipCamera = { useFrontCamera = !useFrontCamera },
                                    onCaptureStarted = { isCapturing = true; errorMsg = null },
                                    onImageCaptured = { uri, b64 ->
                                        isCapturing = false
                                        if (captureSide == "FRONT") {
                                            frontUri = uri.toString()
                                            frontBase64 = b64
                                        } else {
                                            backBase64 = b64
                                        }
                                        captureMethod = "CAMERAX_SCAN"
                                        scanQualityScore = maxOf(liveQualityScore, 72)
                                    },
                                    onCaptureError = { err ->
                                        isCapturing = false
                                        errorMsg = err
                                    },
                                )
                            } else {
                                Surface(
                                    color = CharcoalBlack,
                                    border = BorderStroke(1.dp, GoldBorderSubtle),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = RoyalGold, modifier = Modifier.size(40.dp))
                                        Text("Camera Permission Needed for ID Scanning", style = MaterialTheme.typography.titleSmall, color = PureWhite)
                                        Text(
                                            "SONA-WANE GOLD uses CameraX on-device framing and clarity scoring to scan your ${docType.replace("_", " ")}.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SecondarySilver,
                                        )
                                        GoldPrimaryButton(
                                            text = "Grant Camera Permission",
                                            icon = Icons.Default.CameraAlt,
                                            onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                            modifier = Modifier.fillMaxWidth().testTag("kyc_grant_camera_btn"),
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            GoldOutlinedButton(
                                text = "Or Select from Gallery (Photo Picker)",
                                icon = Icons.Default.PhotoLibrary,
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().testTag("kyc_photo_picker_btn"),
                            )
                        }

                        if (!errorMsg.isNullOrBlank()) {
                            Text(errorMsg!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            GoldOutlinedButton(
                                text = "Back",
                                onClick = { errorMsg = null; currentStep = 1 },
                                modifier = Modifier.weight(0.45f),
                            )
                            GoldPrimaryButton(
                                text = "Review & Submit (Step 3)",
                                enabled = frontBase64 != null,
                                onClick = {
                                    if (frontBase64 == null) {
                                        errorMsg = "Capture or upload the Front side of your ID document first."
                                    } else {
                                        errorMsg = null
                                        currentStep = 3
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("kyc_step2_next_btn"),
                            )
                        }
                    }

                    3 -> {
                        LuxuryCard(highlightGold = true) {
                            Text("Step 3: Review & Submit to Firestore", style = MaterialTheme.typography.titleMedium, color = LightGold)
                            Spacer(modifier = Modifier.height(10.dp))
                            KeyValueRow("Legal Name", fullName)
                            KeyValueRow("Mobile", "+91 $mobile")
                            KeyValueRow("PAN Number", pan)
                            KeyValueRow("Aadhaar Suffix", "XXXX-XXXX-$aadhaarLast4")
                            KeyValueRow("Document Type", docType.replace("_", " "))
                            KeyValueRow("Capture Method", captureMethod.replace("_", " "))
                            KeyValueRow("Scan Quality", "${scanQualityScore}% clarity", valueColor = SuccessGreen)
                            KeyValueRow("Location", "$city, $state - $pincode")
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = GoldBorderSubtle)
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                if (frontBitmap != null) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("ID Front Capture", style = MaterialTheme.typography.labelSmall, color = LightGold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Image(
                                            bitmap = frontBitmap.asImageBitmap(),
                                            contentDescription = "ID Front Preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(110.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .border(1.dp, RoyalGold, RoundedCornerShape(10.dp)),
                                        )
                                    }
                                }
                                if (backBitmap != null) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("ID Back Capture", style = MaterialTheme.typography.labelSmall, color = LightGold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Image(
                                            bitmap = backBitmap.asImageBitmap(),
                                            contentDescription = "ID Back Preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(110.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .border(1.dp, GoldBorderSubtle, RoundedCornerShape(10.dp)),
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = declarationAccepted,
                                    onCheckedChange = { declarationAccepted = it },
                                    colors = CheckboxDefaults.colors(checkedColor = RoyalGold, checkmarkColor = DeepBlack),
                                    modifier = Modifier.testTag("kyc_declaration_checkbox"),
                                )
                                Text(
                                    text = "I confirm the scanned ID document is authentic and belongs to me for bullion KYC compliance.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PureWhite,
                                )
                            }
                        }

                        if (!errorMsg.isNullOrBlank()) {
                            Text(errorMsg!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            GoldOutlinedButton(
                                text = "Edit Scan",
                                onClick = { errorMsg = null; currentStep = 2 },
                                modifier = Modifier.weight(0.45f),
                            )
                            OrangeActionButton(
                                text = if (isBusy) "Submitting..." else "Submit KYC for Review",
                                enabled = !isBusy && declarationAccepted && frontBase64 != null,
                                icon = Icons.Default.VerifiedUser,
                                onClick = {
                                    onSubmitKyc(
                                        fullName,
                                        mobile,
                                        pan,
                                        aadhaarLast4,
                                        docType,
                                        frontUri,
                                        address,
                                        state,
                                        city,
                                        pincode,
                                        frontBase64,
                                        backBase64,
                                        captureMethod,
                                        scanQualityScore,
                                        declarationAccepted,
                                    )
                                    onBack()
                                },
                                modifier = Modifier.weight(1f).testTag("kyc_submit_final_btn"),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun KycStepperBar(
    currentStep: Int,
    onSelectStep: (Int) -> Unit,
) {
    val steps = listOf("Details", "CameraX Scan", "Review")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { idx, label ->
            val stepNum = idx + 1
            val active = stepNum == currentStep
            val completed = stepNum < currentStep
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when {
                    active -> RoyalGold.copy(alpha = 0.20f)
                    completed -> SuccessGreen.copy(alpha = 0.16f)
                    else -> ObsidianCard
                },
                border = BorderStroke(
                    1.dp,
                    when {
                        active -> RoyalGold
                        completed -> SuccessGreen
                        else -> GoldBorderSubtle
                    },
                ),
                onClick = { onSelectStep(stepNum) },
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (completed) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(if (active) RoyalGold else CharcoalBlack),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "$stepNum",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) DeepBlack else SecondarySilver,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) LightGold else if (completed) SuccessGreen else SecondarySilver,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraXDocumentScannerView(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    useFrontCamera: Boolean,
    torchEnabled: Boolean,
    isCapturing: Boolean,
    liveQualityScore: Int,
    liveScanHint: String,
    onAnalysisUpdate: (score: Int, hint: String) -> Unit,
    onToggleTorch: () -> Unit,
    onFlipCamera: () -> Unit,
    onCaptureStarted: () -> Unit,
    onImageCaptured: (uri: Uri, compressedBase64: String) -> Unit,
    onCaptureError: (String) -> Unit,
) {
    val context = LocalContext.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    DisposableEffect(torchEnabled, boundCamera) {
        runCatching { boundCamera?.cameraControl?.enableTorch(torchEnabled) }
        onDispose {}
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(1.5.dp, if (liveQualityScore >= 60) SuccessGreen else RoyalGold, RoundedCornerShape(18.dp)),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> PreviewView(ctx) },
                update = { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        imageCapture = capture

                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        var lastUiUpdateMs = 0L
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdateMs >= 350L) {
                                lastUiUpdateMs = now
                                val (score, hint) = evaluateFrameQuality(imageProxy)
                                previewView.post { onAnalysisUpdate(score, hint) }
                            }
                            imageProxy.close()
                        }

                        val selector = if (useFrontCamera) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }

                        runCatching {
                            cameraProvider.unbindAll()
                            val cam = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                selector,
                                preview,
                                capture,
                                analysis,
                            )
                            boundCamera = cam
                            cam.cameraControl.enableTorch(torchEnabled)
                        }.onFailure { err ->
                            previewView.post {
                                onCaptureError("Camera unavailable: ${err.localizedMessage ?: "Use Photo Picker below"}")
                            }
                        }
                    }, ContextCompat.getMainExecutor(context))
                },
            )

            // Alignment Frame Guide Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp)
                    .border(2.dp, LightGold.copy(alpha = 0.85f), RoundedCornerShape(12.dp)),
            )

            // Live quality indicator bar at top
            Surface(
                color = DeepBlack.copy(alpha = 0.78f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
            ) {
                Text(
                    text = "$liveScanHint ($liveQualityScore%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (liveQualityScore >= 60) SuccessGreen else LightGold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }

            // Torch & Camera Switch controls at top-right
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            ) {
                IconButton(onClick = onToggleTorch) {
                    Icon(
                        imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Toggle flash",
                        tint = if (torchEnabled) AmberOrange else PureWhite,
                    )
                }
                IconButton(onClick = onFlipCamera) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Switch camera",
                        tint = PureWhite,
                    )
                }
            }
        }

        GoldPrimaryButton(
            text = if (isCapturing) "Capturing ID Scan..." else "Capture ID Document",
            enabled = !isCapturing && imageCapture != null,
            icon = Icons.Default.CameraAlt,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("kyc_camerax_capture_btn"),
            onClick = {
                val cap = imageCapture ?: return@GoldPrimaryButton
                onCaptureStarted()
                val outFile = File(context.cacheDir, "kyc_scan_${System.currentTimeMillis()}.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(outFile).build()
                cap.takePicture(
                    outputOptions,
                    cameraExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            val savedUri = outputFileResults.savedUri ?: Uri.fromFile(outFile)
                            val b64 = encodeFileToCompressedBase64(outFile)
                            ContextCompat.getMainExecutor(context).execute {
                                if (b64 != null) {
                                    onImageCaptured(savedUri, b64)
                                } else {
                                    onCaptureError("Failed to compress captured image.")
                                }
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            ContextCompat.getMainExecutor(context).execute {
                                onCaptureError(exception.localizedMessage ?: "Camera capture failed")
                            }
                        }
                    },
                )
            },
        )
    }
}

@Composable
private fun ApprovedKycSummaryCard(
    kycRecord: KycRecord?,
    profile: UserProfile?,
    frontBitmap: Bitmap?,
    backBitmap: Bitmap?,
) {
    LuxuryCard {
        Text("Verified Identity Profile", style = MaterialTheme.typography.titleMedium, color = SuccessGreen)
        Spacer(modifier = Modifier.height(8.dp))
        KeyValueRow("Legal Name", kycRecord?.userName ?: profile?.fullName.orEmpty())
        KeyValueRow("PAN", kycRecord?.panNumber ?: profile?.panMasked.orEmpty())
        KeyValueRow("Aadhaar Suffix", "XXXX-${kycRecord?.aadhaarLast4 ?: "****"}")
        KeyValueRow("Document", kycRecord?.documentType?.replace("_", " ") ?: "PAN CARD")
        KeyValueRow("Scan Method", kycRecord?.captureMethod?.replace("_", " ") ?: "CAMERAX SCAN")
        KeyValueRow("Location", "${kycRecord?.city.orEmpty()}, ${kycRecord?.state.orEmpty()} (${kycRecord?.pincode.orEmpty()})")
        if (frontBitmap != null || backBitmap != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                frontBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Verified Front ID",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).height(110.dp).clip(RoundedCornerShape(10.dp)),
                    )
                }
                backBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Verified Back ID",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).height(110.dp).clip(RoundedCornerShape(10.dp)),
                    )
                }
            }
        }
    }
}

private fun evaluateFrameQuality(imageProxy: androidx.camera.core.ImageProxy): Pair<Int, String> {
    val plane = imageProxy.planes.firstOrNull() ?: return 50 to "Align ID card"
    val buffer = plane.buffer
    val total = buffer.remaining()
    if (total <= 0) return 50 to "Align ID card"
    var sumLuma = 0L
    var edgeEnergy = 0L
    var prev = 0
    var count = 0
    val step = maxOf(1, total / 1200)
    var idx = 0
    while (idx < total) {
        val y = buffer.get(idx).toInt() and 0xFF
        sumLuma += y
        if (count > 0) edgeEnergy += abs(y - prev)
        prev = y
        count++
        idx += step
    }
    val avgLuma = if (count > 0) sumLuma / count else 128L
    val avgEdge = if (count > 1) edgeEnergy / (count - 1) else 10L
    val lumaScore = when {
        avgLuma < 45 -> 30
        avgLuma > 235 -> 45
        else -> 50
    }
    val sharpnessScore = (avgEdge * 2).coerceIn(10, 50).toInt()
    val totalScore = (lumaScore + sharpnessScore).coerceIn(15, 99)
    val hint = when {
        avgLuma < 45 -> "Low light — turn on Flash"
        avgLuma > 235 -> "High glare — tilt ID slightly"
        totalScore >= 65 -> "Good lighting • Ready to capture"
        else -> "Hold steady inside frame"
    }
    return totalScore to hint
}

private fun encodeFileToCompressedBase64(file: File): String? {
    return runCatching {
        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
        val raw = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        compressBitmapToBase64(raw)
    }.getOrNull()
}

private fun encodeUriToCompressedBase64(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            val raw = BitmapFactory.decodeStream(stream, null, opts) ?: return null
            compressBitmapToBase64(raw)
        }
    }.getOrNull()
}

private fun compressBitmapToBase64(bitmap: Bitmap): String {
    val maxSide = 440
    val scale = minOf(maxSide.toFloat() / bitmap.width.coerceAtLeast(1), maxSide.toFloat() / bitmap.height.coerceAtLeast(1), 1f)
    val scaled = if (scale < 1f) {
        val matrix = Matrix().apply { postScale(scale, scale) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 60, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

fun decodeBase64Bitmap(base64: String?): Bitmap? {
    if (base64.isNullOrBlank()) return null
    return runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
