/*
 * Copyright (c) 2026, @aquamarine5 (@海蓝色的咕咕鸽). All Rights Reserved.
 * Author: aquamarine5@163.com (Github: https://github.com/aquamarine5) and Brainspark (previously RenegadeCreation)
 * Repository: https://github.com/aquamarine5/ChaoxingSignFaker
 */

package org.aquamarine5.brainspark.chaoxingsignfaker.components

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import org.aquamarine5.brainspark.chaoxingsignfaker.R
import org.aquamarine5.brainspark.chaoxingsignfaker.api.ChaoxingDeviceInfoHelper
import org.aquamarine5.brainspark.chaoxingsignfaker.api.ChaoxingHttpClient
import org.aquamarine5.brainspark.chaoxingsignfaker.utilities.LocalSnackbarHostState
import org.aquamarine5.brainspark.chaoxingsignfaker.utilities.chaoxingDataStore
import org.aquamarine5.brainspark.chaoxingsignfaker.utilities.displaySnackbar

private fun String.truncated(maxLength: Int = 64): String =
    if (length <= maxLength) this else take(maxLength) + "…（共 $length 字符）"

@Composable
fun DeviceInfoDiagnosticCard() {
    var isShowDialog by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current

    Button(
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
            isShowDialog = true
        },
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9AA8C7))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.ic_info),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    "设备信息诊断",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    lineHeight = 18.sp
                )
                Text(
                    "查看当前实际会上报的客户端信息、设备标识和登录返回的 clientId 解析结果。",
                    fontSize = 10.sp,
                    lineHeight = 12.sp
                )
            }
        }
    }

    if (isShowDialog) {
        DeviceInfoDiagnosticDialog(onDismissRequest = { isShowDialog = false })
    }
}

@Composable
private fun DeviceInfoDiagnosticDialog(onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    var items by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(Unit) {
        items = runCatching {
            val loginSession = context.chaoxingDataStore.data.first().loginSession
            val snapshot = ChaoxingDeviceInfoHelper.getDeviceInfoSnapshot(context)
            val clientId = ChaoxingHttpClient.instance?.userEntity?.clientId
            val clientIdInfo = clientId?.let { ChaoxingDeviceInfoHelper.decryptClientId(it) }
            buildList {
                add("客户端包名" to chaoxingClientInfo.packageName)
                add(
                    "客户端已安装" to if (ChaoxingDeviceInfoHelper.isChaoxingClientInstalled(context))
                        "是" else "否（登录可能无法上报设备信息）"
                )
                add("uniqueId（会话）" to loginSession.uniqueId.ifEmpty { "(空)" })
                add(
                    "UserAgent" to (
                        ChaoxingHttpClient.instance?.userAgent
                            ?: chaoxingClientInfo.userAgentTemplate
                        )
                )
                add("型号 / 品牌" to "${Build.MODEL} / ${Build.BRAND}")
                add("系统版本" to Build.VERSION.RELEASE)
                add("FINGERPRINT" to Build.FINGERPRINT)
                add("ANDROID_ID（本 App）" to (snapshot.getString("android_id") ?: ""))
                add(
                    "deviceUniqueId" to (snapshot.getString("deviceUniqueId") ?: "") +
                            "\n（同时作为 cdid 和 device_id）"
                )
                add(
                    "上报的 APK 版本" to "${
                        snapshot.getString("app_ver")
                    } (${snapshot.getString("versionCode")})"
                )
                add("上报的 APK 签名摘要" to (snapshot.getString("signatures") ?: "").truncated())
                add(
                    "当前 deviceCode" to
                            loginSession.deviceCode.takeIf { it.isNotEmpty() }?.truncated()
                                .orEmpty().ifEmpty { "(空)" }
                )
                add(
                    "设备码状态" to when {
                        loginSession.deviceCode.isEmpty() -> "无设备码"
                        loginSession.isDeviceCodeDuplicatedSignBefore -> "曾因重复签到被回退"
                        loginSession.isNotRandomizedDeviceCode -> "统一设备码"
                        else -> "固定随机设备码"
                    }
                )
                add("clientId" to (clientId?.truncated() ?: "无（服务端未返回）"))
                add("clientId → cid" to (clientIdInfo?.getString("cid") ?: "无"))
                add("clientId → sc" to (clientIdInfo?.getString("sc") ?: "无"))
            }
        }.getOrElse { throwable ->
            listOf("读取失败" to (throwable.message ?: "未知错误"))
        }
    }

    val reportText = items.joinToString("\n") { (label, value) -> "$label: $value" }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("设备信息诊断") },
        text = {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    items.forEach { (label, value) ->
                        Text(label, fontSize = 11.sp, color = Color.Gray)
                        Text(
                            value,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                runCatching {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("设备信息", reportText))
                }.onSuccess {
                    snackbarHostState.displaySnackbar("已复制", coroutineScope)
                }.onFailure {
                    snackbarHostState.displaySnackbar("复制失败", coroutineScope)
                }
            }) { Text("复制全部") }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("关闭") }
        }
    )
}
