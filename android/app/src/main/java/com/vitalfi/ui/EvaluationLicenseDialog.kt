package com.vitalfi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitalfi.R
import com.vitalfi.billing.PlayBillingManager
import com.vitalfi.billing.PlayProductUi
import com.vitalfi.settings.LicenseInfo

private val Cyan = Color(0xFF00E5FF)
private val DarkGreenBg = Color(0xFF0F1A12)
private val Amber = Color(0xFFFFB300)
private val GreenAcc = Color(0xFF00E676)

@Composable
fun EvaluationLicenseDialog(
    licenseInfo: LicenseInfo,
    billingProducts: List<PlayProductUi> = emptyList(),
    onDismiss: () -> Unit,
    onBuyProduct: (productId: String) -> Unit,
    onRestorePurchases: () -> Unit,
    onEnterKey: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkGreenBg,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = if (licenseInfo.isTrialExpired) Amber else Cyan,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.eval_dialog_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Badge de estado de días de prueba
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (licenseInfo.isTrialExpired) Color(0x22FFB300) else Color(0x2200E5FF),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (licenseInfo.isTrialExpired) Amber.copy(alpha = 0.5f) else Cyan.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (licenseInfo.isTrialExpired) {
                            stringResource(R.string.eval_badge_expired, licenseInfo.daysUsed, licenseInfo.trialDaysMax)
                        } else {
                            stringResource(R.string.eval_badge_active, licenseInfo.daysUsed, licenseInfo.trialDaysMax)
                        },
                        color = if (licenseInfo.isTrialExpired) Amber else Cyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                    )
                }

                Text(
                    text = stringResource(R.string.eval_dialog_message),
                    color = Color(0xFFDDDDDD),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )

                // Lista de productos de Google Play Billing
                if (billingProducts.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "🛒 " + stringResource(R.string.license_play_products_title),
                            color = Cyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                        billingProducts.forEach { product ->
                            Card(
                                onClick = { onBuyProduct(product.productId) },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF16281B)),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E5E38)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f).padding(end = 6.dp)) {
                                        Text(
                                            text = product.title,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                        )
                                        if (product.description.isNotBlank()) {
                                            Text(
                                                text = product.description,
                                                color = Color(0xFFAAAAAA),
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                    Text(
                                        text = product.formattedPrice,
                                        color = GreenAcc,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                    }
                }

                // Mensaje ético de rescate (Cero bloqueos)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF142418)),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text("🛡️", fontSize = 14.sp)
                        Text(
                            text = stringResource(R.string.eval_dialog_safety_notice),
                            color = Color(0xFFA5D6A7),
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (billingProducts.isEmpty()) {
                    Button(
                        onClick = { onBuyProduct(PlayBillingManager.PRODUCT_PRO_SUPPORTER) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Cyan,
                            contentColor = Color.Black,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingBag,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.eval_btn_buy_license),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedButton(
                        onClick = onEnterKey,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFB0BEC5),
                        ),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.eval_btn_enter_key),
                            fontSize = 11.sp,
                        )
                    }

                    OutlinedButton(
                        onClick = onRestorePurchases,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF80CBC4),
                        ),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.license_btn_restore),
                            fontSize = 11.sp,
                        )
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.eval_btn_continue_free),
                        color = Color(0xFF88AA88),
                        fontSize = 12.sp,
                    )
                }
            }
        },
    )
}

@Composable
fun EnterLicenseKeyDialog(
    onDismiss: () -> Unit,
    onActivate: (key: String, holder: String) -> Boolean,
) {
    var keyInput by remember { mutableStateOf("") }
    var holderInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkGreenBg,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = Cyan,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(R.string.license_enter_title),
                    color = Cyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.license_enter_desc),
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp,
                )

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = {
                        keyInput = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.license_key_label)) },
                    placeholder = { Text("VF-PRO-XXXX-XXXX") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cyan,
                        unfocusedBorderColor = Color(0xFF335533),
                        focusedLabelColor = Cyan,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = holderInput,
                    onValueChange = { holderInput = it },
                    label = { Text(stringResource(R.string.license_holder_label)) },
                    placeholder = { Text(stringResource(R.string.license_holder_placeholder)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cyan,
                        unfocusedBorderColor = Color(0xFF335533),
                        focusedLabelColor = Cyan,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                errorMessage?.let { err ->
                    Text(
                        text = err,
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val success = onActivate(keyInput, holderInput)
                    if (success) {
                        onDismiss()
                    } else {
                        errorMessage = "Clave no válida. Formato: VF-PRO-XXXX-XXXX"
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Cyan,
                    contentColor = Color.Black,
                ),
            ) {
                Text(stringResource(R.string.license_btn_activate), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.about_close), color = Color.Gray)
            }
        },
    )
}
