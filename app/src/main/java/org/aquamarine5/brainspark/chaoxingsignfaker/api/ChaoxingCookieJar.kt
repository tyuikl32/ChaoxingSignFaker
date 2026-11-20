/*
 * Copyright (c) 2026, @aquamarine5 (@海蓝色的咕咕鸽). All Rights Reserved.
 * Author: aquamarine5@163.com (Github: https://github.com/aquamarine5) and Brainspark (previously RenegadeCreation)
 * Repository: https://github.com/aquamarine5/ChaoxingSignFaker
 */

package org.aquamarine5.brainspark.chaoxingsignfaker.api

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.aquamarine5.brainspark.chaoxingsignfaker.datastore.HttpCookie

class ChaoxingCookieJar : CookieJar {
    private val chaoxingCookies = linkedMapOf<String, Cookie>()
    private val otherHostCookies = mutableMapOf<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!url.host.endsWith("chaoxing.com")) {
            otherHostCookies[url.host] = cookies
            return
        }
        if (url.encodedPath == "/fanyalogin") chaoxingCookies.clear()
        cookies.forEach { cookie -> chaoxingCookies[cookie.name] = cookie }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        if (url.host.endsWith("chaoxing.com")) chaoxingCookies.values.toList()
        else otherHostCookies[url.host] ?: listOf()

    fun snapshotChaoxingCookies(): List<HttpCookie> =
        chaoxingCookies.values.map { cookie ->
            HttpCookie.newBuilder()
                .setName(cookie.name)
                .setValue(cookie.value)
                .setHost(cookie.domain.removePrefix("."))
                .build()
        }
}
