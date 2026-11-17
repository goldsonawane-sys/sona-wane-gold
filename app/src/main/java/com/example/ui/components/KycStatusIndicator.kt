package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.KycRecord
import com.example.data.model.UserProfile
import com.example.ui.theme.AmberOrange
import com.example.ui.theme.CharcoalBlack
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldBorderSubtle
import com.example.ui.theme.LightGold
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.SecondarySilver
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber

enum class KycVisualTier {
    VERIFIED,
    PENDING,
    ACTION_REQUIRED,
    REJECTED,
    NOT_STARTED,
    LOADING,
}

data class KycStatusDisplay(
    val label: String,
    val rawStatus: String,
    val headline: String,
    val subtitle: String,
    val ctaLabel: String,
    val tier: KycVisualTier,
)

/**
 * Deterministically maps Firestore `users/{uid}.kycStatus` and `kyc_records/{uid}.status`
 * into user-facing KYC Status labels (`Verified`, `Pending`, `Action Required`, `Rejected`, `Not Verified`).
 */
object KycStatusResolver {
    fun resolve(
        kycStatus: String?,
        kycRecordStatus: String? = null,
        isLoading: Boolean = false,
    ): KycStatusDisplay {
        if (isLoading && kycStatus.isNullOrBlank() && kycRecordStatus.isNullOrBlank()) {
            return KycStatusDisplay(
                label = "Syncing...",
                rawStatus = "LOADING",
                headline = "Syncing KYC Status",
                subtitle = "Fetching latest verification status from Firestore...",
                ctaLabel = "View KYC",
                tier = KycVisualTier.LOADING,
            )
        }
        val effective = kycStatus?.trim()?.takeIf { it.isNotBlank() }
            ?: kycRecordStatus?.trim()?.takeIf { it.isNotBlank() }
            ?: "NOT_STARTED"

        return when (effective.uppercase()) {
            "APPROVED", "VERIFIED" -> KycStatusDisplay(
                label = "Verified",
                rawStatus = effective.uppercase(),
                headline = "Identity & PAN Verified",
                subtitle = "Verified in Firestore. Full gold trading, transfers, and bank payouts are unlocked.",
                ctaLabel = "View Verified KYC",
                tier = KycVisualTier.VERIFIED,
            )
            "PENDING", "UNDER_REVIEW", "SUBMITTED", "IN_PROGRESS" -> KycStatusDisplay(
                label = "Pending",
                rawStatus = effective.uppercase(),
                headline = "Verification Pending Review",
                subtitle = "Your KYC documents are stored in Firestore and currently under compliance review.",
                ctaLabel = "Track KYC Status",
                tier = KycVisualTier.PENDING,
            )
            "INFO_REQUIRED" -> KycStatusDisplay(
                label = "Action Required",
                rawStatus = "INFO_REQUIRED",
                headline = "Additional KYC Info Requested",
                subtitle = "Compliance requires updated information or clearer ID proof to approve your vault.",
                ctaLabel = "Update KYC Info",
                tier = KycVisualTier.ACTION_REQUIRED,
            )
            "REJECTED" -> KycStatusDisplay(
                label = "Rejected",
                rawStatus = "REJECTED",
                headline = "KYC Verification Declined",
                subtitle = "Review the compliance feedback below and resubmit your KYC documents.",
                ctaLabel = "Resubmit KYC",
                tier = KycVisualTier.REJECTED,
            )
            else -> KycStatusDisplay(
                label = "Not Verified",
                rawStatus = effective.uppercase(),
                headline = "KYC Not Verified Yet",
                subtitle = "Complete PAN & ID verification to unlock full bullion purchase and payout limits.",
                ctaLabel = "Complete KYC",
                tier = KycVisualTier.NOT_STARTED,
            )
        }
    }

    fun visualColor(tier: KycVisualTier): Color = when (tier) {
        KycVisualTier.VERIFIED -> SuccessGreen
        KycVisualTier.PENDING -> WarningAmber
        KycVisualTier.ACTION_REQUIRED -> AmberOrange
        KycVisualTier.REJECTED -> ErrorRed
        KycVisualTier.NOT_STARTED, KycVisualTier.LOADING -> SecondarySilver
    }

    fun visualIcon(tier: KycVisualTier): ImageVector = when (tier) {
        KycVisualTier.VERIFIED -> Icons.Default.VerifiedUser
        KycVisualTier.PENDING -> Icons.Default.HourglassTop
        KycVisualTier.ACTION_REQUIRED -> Icons.Default.ErrorOutline
        KycVisualTier.REJECTED -> Icons.Default.GppBad
        KycVisualTier.NOT_STARTED, KycVisualTier.LOADING -> Icons.Default.Shield
    }
}

@Composable
fun KycStatusIndicatorPill(
    profile: UserProfile?,
    kycRecord: KycRecord? = null,
    prefix: String = "KYC Status: ",
    modifier: Modifier = Modifier,
) {
    val display = remember(profile?.kycStatus, kycRecord?.status, profile == null) {
        KycStatusResolver.resolve(
            kycStatus = profile?.kycStatus,
            kycRecordStatus = kycRecord?.status,
            isLoading = profile == null,
        )
    }
    val tint = KycStatusResolver.visualColor(display.tier)
    val icon = KycStatusResolver.visualIcon(display.tier)

    Surface(
        shape = RoundedCornerShape(50),
        color = tint.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.55f)),
        modifier = modifier.testTag("kyc_status_pill"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "KYC Status: ${display.label}",
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "$prefix${display.label}",
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun KycStatusIndicatorCard(
    profile: UserProfile?,
    kycRecord: KycRecord? = null,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val display = remember(profile?.kycStatus, kycRecord?.status, profile == null) {
        KycStatusResolver.resolve(
            kycStatus = profile?.kycStatus,
            kycRecordStatus = kycRecord?.status,
            isLoading = profile == null,
        )
    }
    val tint = KycStatusResolver.visualColor(display.tier)
    val icon = KycStatusResolver.visualIcon(display.tier)
    val rejectionNote = kycRecord?.rejectionReason?.trim().orEmpty()

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = CharcoalBlack,
        border = BorderStroke(
            width = 1.2.dp,
            color = if (display.tier == KycVisualTier.VERIFIED || display.tier == KycVisualTier.PENDING) {
                tint.copy(alpha = 0.45f)
            } else {
                GoldBorderSubtle
            },
        ),
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onActionClick)
            .testTag("profile_kyc_status_card"),
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            tint.copy(alpha = 0.12f),
                            ObsidianCard,
                        ),
                    ),
                )
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(tint.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = "KYC Status ${display.label}",
                            tint = tint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.profile_kyc_status_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = LightGold,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = SecondarySilver,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.profile_kyc_firestore_source),
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondarySilver,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = tint.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, tint.copy(alpha = 0.6f)),
                    modifier = Modifier.testTag("profile_kyc_status_label"),
                ) {
                    Text(
                        text = display.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = tint,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = display.headline,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = PureWhite,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = display.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SecondarySilver,
            )

            if (rejectionNote.isNotBlank() && display.tier != KycVisualTier.VERIFIED) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = ErrorRed.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Compliance Note",
                            style = MaterialTheme.typography.labelSmall,
                            color = ErrorRed,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = rejectionNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = PureWhite,
                        )
                    }
                }
            }

            val metaParts = buildList {
                if (!kycRecord?.documentType.isNullOrBlank()) {
                    add("Doc: ${kycRecord?.documentType?.replace("_", " ")}")
                }
                if (!profile?.panMasked.isNullOrBlank()) {
                    add("PAN: ${profile?.panMasked}")
                }
            }
            if (metaParts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = metaParts.joinToString("  •  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = LightGold,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_kyc_status_cta"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = display.ctaLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = RoyalGold,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = display.ctaLabel,
                    tint = RoyalGold,
                )
            }
        }
    }
}
