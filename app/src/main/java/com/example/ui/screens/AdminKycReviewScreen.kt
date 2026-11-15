package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.AdminUser
import com.example.data.model.KycRecord
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
import com.example.util.Formatters

private val REJECTION_PRESET_REASONS = listOf(
    "Blurry or unreadable ID document image",
    "PAN number mismatch with applicant name",
    "Cropped or incomplete document edges",
    "Back side document image missing or unclear",
    "Expired or invalid identity document",
)

@Composable
fun AdminKycReviewScreen(
    adminUser: AdminUser?,
    allKyc: List<KycRecord>,
    statusMessage: String?,
    isBusy: Boolean,
    onClearStatus: () -> Unit,
    onReviewKyc: (KycRecord, String, String) -> Unit,
    onBack: () -> Unit,
) {
    val pendingCount = remember(allKyc) {
        allKyc.count { it.status == "PENDING" || it.status == "UNDER_REVIEW" }
    }

    Scaffold(
        containerColor = DeepBlack,
        topBar = {
            SonaWaneTopBar(
                title = "Pending KYC Review",
                subtitle = "$pendingCount awaiting review • ${adminUser?.operatorId?.ifBlank { adminUser.role } ?: "Admin"}",
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!statusMessage.isNullOrBlank()) {
                Surface(
                    color = RoyalGold.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, RoyalGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = LightGold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onClearStatus) { Text("Dismiss", color = AmberOrange) }
                    }
                }
            }

            AdminKycReviewQueueContent(
                allKyc = allKyc,
                isBusy = isBusy,
                onReviewKyc = onReviewKyc,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminKycReviewQueueContent(
    allKyc: List<KycRecord>,
    isBusy: Boolean,
    onReviewKyc: (KycRecord, String, String) -> Unit,
    modifier: Modifier = Modifier,
    onLaunchFullScreen: (() -> Unit)? = null,
) {
    var statusFilter by remember { mutableStateOf("PENDING") }
    var searchQuery by remember { mutableStateOf("") }
    var inspectedKyc by remember { mutableStateOf<KycRecord?>(null) }
    var decisionTarget by remember { mutableStateOf<Pair<KycRecord, String>?>(null) }

    val pendingCount = remember(allKyc) {
        allKyc.count { it.status == "PENDING" || it.status == "UNDER_REVIEW" }
    }
    val infoReqCount = remember(allKyc) { allKyc.count { it.status == "INFO_REQUIRED" } }
    val approvedCount = remember(allKyc) { allKyc.count { it.status == "APPROVED" } }
    val rejectedCount = remember(allKyc) { allKyc.count { it.status == "REJECTED" } }

    val filteredKyc = remember(allKyc, statusFilter, searchQuery) {
        allKyc.filter { item ->
            val statusOk = when (statusFilter) {
                "PENDING" -> item.status == "PENDING" || item.status == "UNDER_REVIEW"
                "ALL" -> true
                else -> item.status == statusFilter
            }
            val q = searchQuery.trim()
            val queryOk = q.isBlank() ||
                item.userName.contains(q, ignoreCase = true) ||
                item.panNumber.contains(q, ignoreCase = true) ||
                item.mobileNumber.contains(q, ignoreCase = true) ||
                item.city.contains(q, ignoreCase = true) ||
                item.documentType.contains(q, ignoreCase = true)
            statusOk && queryOk
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LuxuryCard(highlightGold = pendingCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Compliance Verification Queue",
                            style = MaterialTheme.typography.titleMedium,
                            color = LightGold,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Inspect CameraX / uploaded ID photos and verify PAN & Aadhaar details before approval.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondarySilver,
                        )
                    }
                    if (onLaunchFullScreen != null) {
                        IconButton(
                            onClick = onLaunchFullScreen,
                            modifier = Modifier.testTag("launch_fullscreen_kyc_button"),
                        ) {
                            Icon(Icons.Default.OpenInFull, contentDescription = "Open Full-Screen KYC Queue", tint = RoyalGold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    KycStatBadge("Pending", "$pendingCount", if (pendingCount > 0) AmberOrange else SuccessGreen, Modifier.weight(1f))
                    KycStatBadge("Approved", "$approvedCount", SuccessGreen, Modifier.weight(1f))
                    KycStatBadge("Rejected", "$rejectedCount", ErrorRed, Modifier.weight(1f))
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search applicant name, PAN, mobile or city") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = RoyalGold) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = SecondarySilver)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_kyc_search_input"),
            )
        }

        item {
            val filters = listOf(
                "PENDING" to "Pending ($pendingCount)",
                "INFO_REQUIRED" to "Info Req ($infoReqCount)",
                "APPROVED" to "Approved ($approvedCount)",
                "REJECTED" to "Rejected ($rejectedCount)",
                "ALL" to "All (${allKyc.size})",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filters.forEach { (key, label) ->
                    FilterChip(
                        selected = statusFilter == key,
                        onClick = { statusFilter = key },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RoyalGold.copy(alpha = 0.22f),
                            selectedLabelColor = LightGold,
                        ),
                        modifier = Modifier.testTag("kyc_filter_${key.lowercase()}"),
                    )
                }
            }
        }

        if (filteredKyc.isEmpty()) {
            item {
                LuxuryCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (statusFilter == "PENDING") {
                                "No pending KYC submissions in queue."
                            } else {
                                "No KYC submissions match the selected filter."
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = PureWhite,
                        )
                        Text(
                            text = "New CameraX or uploaded document submissions will appear here immediately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondarySilver,
                        )
                    }
                }
            }
        } else {
            items(filteredKyc, key = { it.userId }) { kyc ->
                AdminKycSubmissionCard(
                    kyc = kyc,
                    isBusy = isBusy,
                    onInspectDocs = { inspectedKyc = kyc },
                    onApprove = { decisionTarget = kyc to "APPROVED" },
                    onRequestInfo = { decisionTarget = kyc to "INFO_REQUIRED" },
                    onReject = { decisionTarget = kyc to "REJECTED" },
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    if (inspectedKyc != null) {
        KycDocumentInspectorDialog(
            kyc = inspectedKyc!!,
            isBusy = isBusy,
            onDismiss = { inspectedKyc = null },
            onApprove = {
                val target = inspectedKyc!!
                inspectedKyc = null
                decisionTarget = target to "APPROVED"
            },
            onReject = {
                val target = inspectedKyc!!
                inspectedKyc = null
                decisionTarget = target to "REJECTED"
            },
        )
    }

    if (decisionTarget != null) {
        val (targetKyc, targetStatus) = decisionTarget!!
        KycReviewDecisionDialog(
            kyc = targetKyc,
            newStatus = targetStatus,
            isBusy = isBusy,
            onDismiss = { decisionTarget = null },
            onConfirm = { reason ->
                onReviewKyc(targetKyc, targetStatus, reason)
                decisionTarget = null
            },
        )
    }
}

@Composable
private fun KycStatBadge(
    label: String,
    count: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = CharcoalBlack,
        border = BorderStroke(1.dp, GoldBorderSubtle),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(count, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
        }
    }
}

@Composable
private fun AdminKycSubmissionCard(
    kyc: KycRecord,
    isBusy: Boolean,
    onInspectDocs: () -> Unit,
    onApprove: () -> Unit,
    onRequestInfo: () -> Unit,
    onReject: () -> Unit,
) {
    val frontBmp = remember(kyc.documentFrontBase64) { decodeBase64Bitmap(kyc.documentFrontBase64) }
    val backBmp = remember(kyc.documentBackBase64) { decodeBase64Bitmap(kyc.documentBackBase64) }
    val hasUriFallback = frontBmp == null && backBmp == null && !kyc.documentRefUri.isNullOrBlank()
    val hasAnyDocument = frontBmp != null || backBmp != null || hasUriFallback
    val isPending = kyc.status == "PENDING" || kyc.status == "UNDER_REVIEW"

    LuxuryCard(highlightGold = isPending) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Badge, contentDescription = null, tint = RoyalGold, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(kyc.userName, style = MaterialTheme.typography.titleMedium, color = PureWhite)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("PAN: ${kyc.panNumber} • Aadhaar: XXXX-${kyc.aadhaarLast4}", style = MonoSmallStyle, color = LightGold)
                Text("Mobile: ${kyc.mobileNumber} • Doc: ${kyc.documentType.replace("_", " ")}", style = MaterialTheme.typography.bodySmall, color = SecondarySilver)
                Text("${kyc.addressLine}, ${kyc.city}, ${kyc.state} (${kyc.pincode})", style = MaterialTheme.typography.bodySmall, color = SecondarySilver)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${kyc.captureMethod.replace("_", " ")} • Clarity ${kyc.scanQualityScore}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (kyc.scanQualityScore >= 60) SuccessGreen else AmberOrange,
                    )
                    Text(
                        text = "• ${Formatters.formatTimestamp(kyc.updatedAt ?: kyc.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondarySilver,
                    )
                }
            }
            StatusPill(text = kyc.status.replace("_", " "), color = statusColorFor(kyc.status))
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (hasAnyDocument) {
            Text("Uploaded Document Images (Tap to Inspect & Zoom)", style = MaterialTheme.typography.labelSmall, color = LightGold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                frontBmp?.let { bmp ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(112.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, RoyalGold, RoundedCornerShape(10.dp))
                            .clickable(onClick = onInspectDocs)
                            .testTag("kyc_front_preview_${kyc.userId}"),
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "KYC Front Document for ${kyc.userName}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Surface(
                            color = DeepBlack.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp),
                        ) {
                            Text("FRONT ID", style = MaterialTheme.typography.labelSmall, color = LightGold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Surface(
                            color = DeepBlack.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp),
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Inspect Front ID", tint = LightGold, modifier = Modifier.padding(4.dp).size(14.dp))
                        }
                    }
                }
                backBmp?.let { bmp ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(112.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, GoldBorderSubtle, RoundedCornerShape(10.dp))
                            .clickable(onClick = onInspectDocs)
                            .testTag("kyc_back_preview_${kyc.userId}"),
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "KYC Back Document for ${kyc.userName}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Surface(
                            color = DeepBlack.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp),
                        ) {
                            Text("BACK ID", style = MaterialTheme.typography.labelSmall, color = LightGold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                if (hasUriFallback) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(112.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, RoyalGold, RoundedCornerShape(10.dp))
                            .clickable(onClick = onInspectDocs),
                    ) {
                        AsyncImage(
                            model = kyc.documentRefUri,
                            contentDescription = "Uploaded KYC Document URI",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Surface(
                            color = DeepBlack.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp),
                        ) {
                            Text("UPLOADED DOC", style = MaterialTheme.typography.labelSmall, color = LightGold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ErrorRed.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "No document image attached to this submission.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                    )
                }
            }
        }

        if (!kyc.rejectionReason.isNullOrBlank() && kyc.status != "APPROVED") {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = AmberOrange.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, AmberOrange.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Prior Review Note: ${kyc.rejectionReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AmberOrange,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (hasAnyDocument) {
                GoldOutlinedButton(
                    text = "Inspect",
                    icon = Icons.Default.DocumentScanner,
                    onClick = onInspectDocs,
                    modifier = Modifier
                        .weight(0.9f)
                        .testTag("kyc_inspect_${kyc.userId}"),
                )
            }
            GoldPrimaryButton(
                text = "Approve",
                icon = Icons.Default.CheckCircle,
                enabled = !isBusy,
                onClick = onApprove,
                modifier = Modifier
                    .weight(1f)
                    .testTag("kyc_approve_${kyc.userId}"),
            )
            OrangeActionButton(
                text = "Reject",
                enabled = !isBusy,
                onClick = onReject,
                modifier = Modifier
                    .weight(0.95f)
                    .testTag("kyc_reject_${kyc.userId}"),
            )
        }
        if (kyc.status != "INFO_REQUIRED") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    enabled = !isBusy,
                    onClick = onRequestInfo,
                    modifier = Modifier.testTag("kyc_inforeq_${kyc.userId}"),
                ) {
                    Text("Request Resubmission", color = SecondarySilver, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun KycDocumentInspectorDialog(
    kyc: KycRecord,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val frontBmp = remember(kyc.documentFrontBase64) { decodeBase64Bitmap(kyc.documentFrontBase64) }
    val backBmp = remember(kyc.documentBackBase64) { decodeBase64Bitmap(kyc.documentBackBase64) }
    var selectedSide by remember { mutableStateOf(if (frontBmp != null) "FRONT" else if (backBmp != null) "BACK" else "URI") }
    var zoomScale by remember { mutableFloatStateOf(1f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = DeepBlack,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, RoyalGold),
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .heightIn(max = 720.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("KYC Document Inspector", style = MaterialTheme.typography.titleMedium, color = LightGold)
                        Text("${kyc.userName} • ${kyc.documentType.replace("_", " ")}", style = MaterialTheme.typography.bodySmall, color = SecondarySilver)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Inspector", tint = PureWhite)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (frontBmp != null) {
                            FilterChip(
                                selected = selectedSide == "FRONT",
                                onClick = { selectedSide = "FRONT" },
                                label = { Text("Front Side") },
                            )
                        }
                        if (backBmp != null) {
                            FilterChip(
                                selected = selectedSide == "BACK",
                                onClick = { selectedSide = "BACK" },
                                label = { Text("Back Side") },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(1f to "1x", 1.5f to "1.5x", 2.2f to "2.2x").forEach { (scale, label) ->
                            FilterChip(
                                selected = zoomScale == scale,
                                onClick = { zoomScale = scale },
                                label = { Text(label) },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(270.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CharcoalBlack)
                        .border(1.dp, GoldBorderSubtle, RoundedCornerShape(14.dp))
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center,
                ) {
                    when (selectedSide) {
                        "FRONT" -> frontBmp?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Full Front ID Image",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(270.dp)
                                    .graphicsLayer(scaleX = zoomScale, scaleY = zoomScale),
                            )
                        }
                        "BACK" -> backBmp?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Full Back ID Image",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(270.dp)
                                    .graphicsLayer(scaleX = zoomScale, scaleY = zoomScale),
                            )
                        }
                        else -> if (!kyc.documentRefUri.isNullOrBlank()) {
                            AsyncImage(
                                model = kyc.documentRefUri,
                                contentDescription = "Uploaded ID URI",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(270.dp)
                                    .graphicsLayer(scaleX = zoomScale, scaleY = zoomScale),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = ObsidianCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GoldBorderSubtle),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        KeyValueRow("Applicant", kyc.userName, boldValue = true)
                        KeyValueRow("PAN Number", kyc.panNumber, valueColor = LightGold, boldValue = true)
                        KeyValueRow("Aadhaar Last 4", "XXXX-${kyc.aadhaarLast4}")
                        KeyValueRow("Address", "${kyc.addressLine}, ${kyc.city} - ${kyc.pincode}")
                        KeyValueRow("Capture Quality", "${kyc.captureMethod.replace("_", " ")} (${kyc.scanQualityScore}%)")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OrangeActionButton(
                        text = "Reject with Reason",
                        enabled = !isBusy,
                        onClick = onReject,
                        modifier = Modifier.weight(1f),
                    )
                    GoldPrimaryButton(
                        text = "Approve KYC",
                        icon = Icons.Default.CheckCircle,
                        enabled = !isBusy,
                        onClick = onApprove,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KycReviewDecisionDialog(
    kyc: KycRecord,
    newStatus: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val isReasonMandatory = newStatus == "REJECTED" || newStatus == "INFO_REQUIRED"
    var reason by remember(kyc.userId, newStatus) {
        mutableStateOf(if (isReasonMandatory) "" else "Verified ID documents & PAN details")
    }
    val trimmed = reason.trim()
    val isReasonValid = !isReasonMandatory || trimmed.length >= 5

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        title = {
            Text(
                text = when (newStatus) {
                    "APPROVED" -> "Approve KYC for ${kyc.userName}?"
                    "INFO_REQUIRED" -> "Request Resubmission: ${kyc.userName}"
                    else -> "Reject KYC for ${kyc.userName}?"
                },
                color = if (newStatus == "REJECTED") ErrorRed else LightGold,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "PAN: ${kyc.panNumber} • Aadhaar: XXXX-${kyc.aadhaarLast4}",
                    style = MonoSmallStyle,
                    color = SecondarySilver,
                )
                if (isReasonMandatory) {
                    Text(
                        text = "Select a compliance preset or enter a mandatory rejection reason (min 5 characters):",
                        style = MaterialTheme.typography.bodySmall,
                        color = LightGold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        REJECTION_PRESET_REASONS.forEach { preset ->
                            FilterChip(
                                selected = trimmed == preset,
                                onClick = { reason = preset },
                                label = { Text(preset, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= 280) reason = it },
                    label = {
                        Text(
                            if (isReasonMandatory) "Rejection Reason * (Mandatory)"
                            else "Verification Remarks (Optional)"
                        )
                    },
                    placeholder = {
                        Text(
                            if (isReasonMandatory) "e.g. Blurry PAN card image; resubmit clearly."
                            else "Verified ID documents"
                        )
                    },
                    isError = isReasonMandatory && trimmed.length < 5,
                    supportingText = {
                        if (isReasonMandatory && trimmed.length < 5) {
                            Text("Rejection reason is mandatory (at least 5 characters).", color = ErrorRed)
                        } else {
                            Text("${reason.length}/280 characters", color = SecondarySilver)
                        }
                    },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("kyc_rejection_reason_input"),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isReasonValid && !isBusy,
                onClick = { onConfirm(trimmed) },
                modifier = Modifier.testTag("kyc_confirm_decision_button"),
            ) {
                Text(
                    text = when (newStatus) {
                        "APPROVED" -> "Confirm Approval"
                        "INFO_REQUIRED" -> "Send Request"
                        else -> "Confirm Reject"
                    },
                    color = when {
                        !isReasonValid -> SecondarySilver
                        newStatus == "REJECTED" -> ErrorRed
                        else -> RoyalGold
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SecondarySilver)
            }
        },
    )
}
