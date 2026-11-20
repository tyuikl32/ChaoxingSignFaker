/*
 * Copyright (c) 2026, @aquamarine5 (@海蓝色的咕咕鸽). All Rights Reserved.
 * Author: aquamarine5@163.com (Github: https://github.com/aquamarine5) and Brainspark (previously RenegadeCreation)
 * Repository: https://github.com/aquamarine5/ChaoxingSignFaker
 */

package org.aquamarine5.brainspark.chaoxingsignfaker.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.aquamarine5.brainspark.chaoxingsignfaker.R
import org.aquamarine5.brainspark.chaoxingsignfaker.api.ChaoxingDeviceInfoHelper
import org.aquamarine5.brainspark.chaoxingsignfaker.utilities.LocalSnackbarHostState
import org.aquamarine5.brainspark.chaoxingsignfaker.utilities.chaoxingDataStore
import org.aquamarine5.brainspark.chaoxingsignfaker.utilities.displaySnackbar

private const val MAX_DEVICE_CODE_LENGTH = 256

@Composable
fun DeviceCodeCard() {
    var isShowDialog by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current

    Button(
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
            isShowDialog = true
        },
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9BCB8C))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.ic_tablet_smartphone_check),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    "设备码",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    lineHeight = 18.sp
                )
                Text(
                    "查看和修改签到时使用的设备码。填入学习通或学在西电官方客户端的设备码后，学习通会认为是同一台设备。",
                    fontSize = 10.sp,
                    lineHeight = 12.sp
                )
            }
        }
    }

    if (isShowDialog) {
        DeviceCodeDialog(onDismissRequest = { isShowDialog = false })
    }
}

@Composable
private fun DeviceCodeDialog(onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val hapticFeedback = LocalHapticFeedback.current
    var deviceCode by remember { mutableStateOf("") }
    var isNotRandomizedDeviceCode by remember { mutableStateOf(false) }
    var isDeviceCodeDuplicatedBefore by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loginSession = context.chaoxingDataStore.data.first().loginSession
        deviceCode = loginSession.deviceCode
        isNotRandomizedDeviceCode = loginSession.isNotRandomizedDeviceCode
        isDeviceCodeDuplicatedBefore = loginSession.isDeviceCodeDuplicatedSignBefore
    }

    fun saveDeviceCode(code: String, isConsistent: Boolean) {
        if (isSaving) return
        if (code.length > MAX_DEVICE_CODE_LENGTH) {
            snackbarHostState.displaySnackbar("设备码过长", coroutineScope)
            return
        }
        isSaving = true
        hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
        coroutineScope.launch {
            runCatching {
                ChaoxingDeviceInfoHelper.updateLoginSessionDeviceCode(context, code, isConsistent)
            }.onSuccess {
                deviceCode = code
                isNotRandomizedDeviceCode = isConsistent && code.isNotEmpty()
                isDeviceCodeDuplicatedBefore = false
                snackbarHostState.displaySnackbar("设备码已保存", coroutineScope)
                onDismissRequest()
            }.onFailure {
                snackbarHostState.displaySnackbar(it.message ?: "保存设备码失败", coroutineScope)
            }
            isSaving = false
        }
    }

    val statusText = when {
        deviceCode.isEmpty() -> "当前没有设备码，签到请求不会携带 deviceCode 参数。"
        isDeviceCodeDuplicatedBefore -> "此设备码曾因「同一设备不允许重复签到」被自动回退过。"
        isNotRandomizedDeviceCode -> "当前使用统一设备码。"
        else -> "当前使用固定的随机设备码。"
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismissRequest() },
        title = { Text("设备码") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(statusText, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = deviceCode,
                    onValueChange = { deviceCode = it.trim() },
                    enabled = !isSaving,
                    singleLine = false,
                    label = { Text("设备码") },
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    TextButton(
                        enabled = !isSaving,
                        onClick = {
                            coroutineScope.launch {
                                runCatching {
                                    ChaoxingDeviceInfoHelper.getLocalMachineDeviceCode(context)
                                }.onSuccess { generated ->
                                    if (generated.isEmpty()) {
                                        snackbarHostState.displaySnackbar(
                                            "本机设备码获取失败，OAID 不可用",
                                            coroutineScope
                                        )
                                    } else {
                                        deviceCode = generated
                                    }
                                }
                            }
                        }
                    ) { Text("使用本机设备码") }
                    TextButton(
                        enabled = !isSaving,
                        onClick = {
                            deviceCode = ChaoxingDeviceInfoHelper.randomizedDeviceCode()
                        }
                    ) { Text("随机") }
                    TextButton(
                        enabled = !isSaving,
                        onClick = { deviceCode = "" }
                    ) { Text("清空") }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "「统一设备码」指学习通认为是同一台设备的标识。若此设备码已被其他账号或设备使用，签到可能被判定为同一设备重复签到。",
                    fontSize = 11.sp,
                    lineHeight = 13.sp
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving,
                onClick = { saveDeviceCode(deviceCode, true) }
            ) { Text(if (isSaving) "保存中" else "保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest, enabled = !isSaving) { Text("取消") }
        }
    )
}
