/*
 * Copyright (c) 2026, @aquamarine5 (@海蓝色的咕咕鸽). All Rights Reserved.
 * Author: aquamarine5@163.com (Github: https://github.com/aquamarine5) and Brainspark (previously RenegadeCreation)
 * Repository: https://github.com/aquamarine5/ChaoxingSignFaker
 */

package org.aquamarine5.brainspark.chaoxingsignfaker.api

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.alibaba.fastjson2.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.aquamarine5.brainspark.chaoxingsignfaker.utilities.ChaoxingParseDataException
import org.aquamarine5.brainspark.chaoxingsignfaker.utilities.checkResponseThrowException
import org.aquamarine5.brainspark.chaoxingsignfaker.utilities.requirePredictable

sealed interface ChaoxingQRLoginState {
    data object WaitingForScan : ChaoxingQRLoginState
    data class Scanned(val nickname: String?, val uid: Int?) : ChaoxingQRLoginState
    data object Confirmed : ChaoxingQRLoginState
    data object Expired : ChaoxingQRLoginState
    data object Cancelled : ChaoxingQRLoginState
    data class Failure(val message: String) : ChaoxingQRLoginState
}

class ChaoxingQRLoginSession internal constructor(
    internal val okHttpClient: OkHttpClient
) {
    class ChaoxingQRLoginException(message: String, throwable: Throwable? = null) :
        ChaoxingParseDataException(message, throwable)

    private var uuid: String = ""
    private var enc: String = ""

    suspend fun refresh(): Unit = withContext(Dispatchers.IO) {
        okHttpClient.newCall(
            Request.Builder().post(FormBody.Builder().build()).url(URL_REFRESH_QR).build()
        ).execute().use { response ->
            response.checkResponseThrowException()
            val jsonResult = JSONObject.parseObject(response.body.string())
            val newUuid = jsonResult.getString("uuid")
            val newEnc = jsonResult.getString("enc")
            requirePredictable(!newUuid.isNullOrEmpty() && !newEnc.isNullOrEmpty()) {
                "无法获取登录二维码"
            }
            uuid = newUuid
            enc = newEnc
        }
    }

    suspend fun loadQRCode(): Bitmap = withContext(Dispatchers.IO) {
        requirePredictable(uuid.isNotEmpty()) { "登录二维码尚未创建" }
        okHttpClient.newCall(
            Request.Builder().get().url(
                URL_CREATE_QR.newBuilder()
                    .addQueryParameter("uuid", uuid)
                    .addQueryParameter("fid", "-1")
                    .build()
            ).build()
        ).execute().use { response ->
            response.checkResponseThrowException()
            val bitmap = response.body.byteStream().use { stream ->
                BitmapFactory.decodeStream(stream)
            }
            requirePredictable(bitmap != null) { "登录二维码图片解析失败" }
            bitmap
        }
    }

    suspend fun poll(): ChaoxingQRLoginState = withContext(Dispatchers.IO) {
        requirePredictable(uuid.isNotEmpty()) { "登录二维码尚未创建" }
        okHttpClient.newCall(
            Request.Builder().post(
                FormBody.Builder()
                    .add("enc", enc)
                    .add("uuid", uuid)
                    .add("doubleFactorLogin", "0")
                    .add("forbidotherlogin", "0")
                    .build()
            ).url(URL_AUTH_STATUS).build()
        ).execute().use { response ->
            response.checkResponseThrowException()
            val jsonResult = JSONObject.parseObject(response.body.string())
            if (jsonResult.getBooleanValue("status")) return@use ChaoxingQRLoginState.Confirmed
            when (jsonResult.getString("type")) {
                "2" -> ChaoxingQRLoginState.Expired
                "4" -> ChaoxingQRLoginState.Scanned(
                    jsonResult.getString("nickname"),
                    jsonResult.getInteger("uid")
                )

                "6" -> ChaoxingQRLoginState.Cancelled
                "7" -> ChaoxingQRLoginState.Failure(jsonResult.getString("mes") ?: "扫码登录失败")
                "1" -> ChaoxingQRLoginState.Failure(jsonResult.getString("mes") ?: "扫码登录参数错误")
                else -> ChaoxingQRLoginState.WaitingForScan
            }
        }
    }

    companion object {
        private val URL_REFRESH_QR =
            "https://passport2.chaoxing.com/refreshQRCode".toHttpUrl()
        private val URL_CREATE_QR = "https://passport2.chaoxing.com/createqr".toHttpUrl()
        private val URL_AUTH_STATUS =
            "https://passport2.chaoxing.com/getauthstatus/v2".toHttpUrl()
    }
}
