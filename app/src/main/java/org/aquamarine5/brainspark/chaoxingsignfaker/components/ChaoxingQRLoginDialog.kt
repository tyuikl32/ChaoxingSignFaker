/*
 * Copyright (c) 2026, @aquamarine5 (@海蓝色的咕咕鸽). All Rights Reserved.
 * Author: aquamarine5@163.com (Github: https://github.com/aquamarine5) and Brainspark (previously RenegadeCreation)
 * Repository: https://github.com/aquamarine5/ChaoxingSignFaker
 */

package org.aquamarine5.brainspark.chaoxingsignfaker.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.aquamarine5.brainspark.chaoxingsignfaker.api.ChaoxingHttpClient
import org.aquamarine5.brainspark.chaoxingsignfaker.api.ChaoxingQRLoginState

private const val QR_LOGIN_POLL_INTERVAL = 3000L
private const val QR_LOGIN_MAX_POLL_COUNT = 50

@Composable
fun ChaoxingQRLoginDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    var qrCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var stateText by remember { mutableStateOf("正在获取登录二维码…") }
    var isRetryAvailable by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTrigger) {
        qrCodeBitmap = null
        isRetryAvailable = false
        stateText = "正在获取登录二维码…"
        val session = runCatching { ChaoxingHttpClient.beginQRLogin(context) }
            .getOrElse { throwable ->
                stateText = throwable.message ?: "无法获取登录二维码"
                isRetryAvailable = true
                return@LaunchedEffect
            }
        runCatching { session.loadQRCode() }
            .onSuccess { bitmap ->
                qrCodeBitmap = bitmap
                stateText = "请使用学习通 App 扫码登录"
            }
            .onFailure { throwable ->
                stateText = throwable.message ?: "无法加载登录二维码"
                isRetryAvailable = true
                return@LaunchedEffect
            }
        repeat(QR_LOGIN_MAX_POLL_COUNT) {
            delay(QR_LOGIN_POLL_INTERVAL)
            val state = runCatching { session.poll() }
                .getOrElse { throwable ->
                    stateText = throwable.message ?: "扫码登录状态获取失败"
                    isRetryAvailable = true
                    return@LaunchedEffect
                }
            when (state) {
                is ChaoxingQRLoginState.WaitingForScan -> Unit
                is ChaoxingQRLoginState.Scanned -> {
                    stateText = "${state.nickname ?: "已扫码"}，请在手机上确认登录"
                }

                is ChaoxingQRLoginState.Confirmed -> {
                    stateText = "登录成功，正在获取账号信息…"
                    runCatching { ChaoxingHttpClient.finishQRLogin(context, session) }
                        .onSuccess {
                            onSuccess()
                            onDismiss()
                        }
                        .onFailure { throwable ->
                            stateText = throwable.message ?: "登录失败"
                            isRetryAvailable = true
                        }
                    return@LaunchedEffect
                }

                is ChaoxingQRLoginState.Expired -> {
                    stateText = "二维码已失效"
                    isRetryAvailable = true
                    return@LaunchedEffect
                }

                is ChaoxingQRLoginState.Cancelled -> {
                    stateText = "已在手机上取消登录"
                    isRetryAvailable = true
                    return@LaunchedEffect
                }

                is ChaoxingQRLoginState.Failure -> {
                    stateText = state.message
                    isRetryAvailable = true
                    return@LaunchedEffect
                }
            }
        }
        stateText = "二维码已失效"
        isRetryAvailable = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("扫码登录") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                qrCodeBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "登录二维码",
                        modifier = Modifier.size(240.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(stateText, textAlign = TextAlign.Center)
            }
        },
        confirmButton = {
            if (isRetryAvailable) {
                TextButton(onClick = { refreshTrigger++ }) {
                    Text("重新获取")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
