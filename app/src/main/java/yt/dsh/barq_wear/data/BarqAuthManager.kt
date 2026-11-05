package yt.dsh.barq_wear.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class QrAuthSession(
    val appAuthHash: String,
    val loginChallenge: String,
    val loginPageUrl: String,
    val codeVerifier: String,
    val state: String,
    val qrPayload: String,
    val qrBitmap: Bitmap
)

sealed class QrPollResult {
    object Pending : QrPollResult()
    object ApprovedAndLoggedIn : QrPollResult()
    object Rejected : QrPollResult()
    data class Error(val message: String) : QrPollResult()
}

class MemoryCookieJar : CookieJar {
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    fun clear() {
        cookieStore.clear()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val domainKey = url.host
        val existing = cookieStore.getOrPut(domainKey) { mutableListOf() }
        synchronized(existing) {
            for (newCookie in cookies) {
                existing.removeAll { it.name == newCookie.name && it.path == newCookie.path }
                existing.add(newCookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val result = mutableListOf<Cookie>()
        for ((host, list) in cookieStore) {
            if (url.host == host || url.host.endsWith(".$host")) {
                synchronized(list) {
                    list.removeAll { it.expiresAt < now }
                    result.addAll(list.filter { it.matches(url) })
                }
            }
        }
        return result
    }
}

class BarqAuthManager(private val context: Context) {

    companion object {
        const val AUTH_HOST = "https://auth.barq.app"
        const val ACCOUNTS_HOST = "https://accounts.barq.app"
        const val REDIRECT_URI = "https://web.barq.app/login/callback"
        const val CLIENT_ID = "barq-web"
        const val SCOPES = "openid offline_access profile barq:api"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel Watch 2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        private const val PREFS_NAME = "barq_wear_auth"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_SAVED_PLACE_JSON = "saved_place_json"
        private const val KEY_MY_PROFILE_UUID = "my_profile_uuid"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_CACHED_NEARBY_JSON = "cached_nearby_profiles_json"
        private const val KEY_CACHED_NEARBY_TS = "cached_nearby_timestamp"
        private const val KEY_AD_ONBOARDING_ASKED = "ad_nsfw_onboarding_asked"
        private const val KEY_AD_MODE_ENABLED = "ad_mode_enabled"
        private const val KEY_NSFW_ENABLED = "nsfw_enabled"
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val refreshMutex = Mutex()
    private val cookieJar = MemoryCookieJar()

    private val userAgentInterceptor = Interceptor { chain ->
        val req = chain.request().newBuilder()
            .header("User-Agent", USER_AGENT)
            .build()
        chain.proceed(req)
    }

    private val httpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(userAgentInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val noRedirectClient = httpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun isLoggedIn(): Boolean {
        return !prefs.getString(KEY_ACCESS_TOKEN, null).isNullOrBlank() ||
            !prefs.getString(KEY_REFRESH_TOKEN, null).isNullOrBlank()
    }

    fun logout() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_MY_PROFILE_UUID)
            .remove(KEY_AD_ONBOARDING_ASKED)
            .apply()
        cookieJar.clear()
        runCatching {
            yt.dsh.barq_wear.sync.BarqSyncScheduler.cancelSync(context)
            yt.dsh.barq_wear.complication.BarqComplicationService.requestComplicationUpdate(context)
        }
    }

    fun hasAskedAdNsfwOnboarding(): Boolean {
        return prefs.getBoolean(KEY_AD_ONBOARDING_ASKED, false)
    }

    fun setAskedAdNsfwOnboarding(asked: Boolean) {
        prefs.edit().putBoolean(KEY_AD_ONBOARDING_ASKED, asked).apply()
    }

    fun isAdModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_AD_MODE_ENABLED, false)
    }

    fun setAdModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AD_MODE_ENABLED, enabled).apply()
    }

    fun isNsfwEnabled(): Boolean {
        return prefs.getBoolean(KEY_NSFW_ENABLED, false)
    }

    fun setNsfwEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NSFW_ENABLED, enabled).apply()
    }

    fun saveSelectedPlace(place: Place) {
        prefs.edit()
            .putString(KEY_SAVED_PLACE_JSON, gson.toJson(place))
            .apply()
    }

    fun getSavedPlace(): Place? {
        val raw = prefs.getString(KEY_SAVED_PLACE_JSON, null) ?: return null
        return runCatching { gson.fromJson(raw, Place::class.java) }.getOrNull()
    }

    fun saveMyProfileUuid(uuid: String) {
        if (uuid.isNotBlank()) {
            prefs.edit().putString(KEY_MY_PROFILE_UUID, uuid.trim()).apply()
        }
    }

    fun getMyProfileUuid(): String? {
        return prefs.getString(KEY_MY_PROFILE_UUID, null)?.takeIf { it.isNotBlank() }
    }

    fun saveAppLanguageCode(code: String) {
        prefs.edit().putString(KEY_APP_LANGUAGE, code.trim()).apply()
    }

    fun getAppLanguageCode(): String? {
        return prefs.getString(KEY_APP_LANGUAGE, null)?.takeIf { it.isNotBlank() }
    }

    fun saveCachedNearbyProfiles(profiles: List<OverviewProfile>) {
        val topSlice = profiles.take(6)
        prefs.edit()
            .putString(KEY_CACHED_NEARBY_JSON, gson.toJson(topSlice))
            .putLong(KEY_CACHED_NEARBY_TS, System.currentTimeMillis())
            .apply()
        runCatching {
            yt.dsh.barq_wear.complication.BarqComplicationService.requestComplicationUpdate(context)
        }
    }

    fun getCachedNearbyProfiles(maxAgeMs: Long = 30L * 60L * 1000L): List<OverviewProfile>? {
        val ts = prefs.getLong(KEY_CACHED_NEARBY_TS, 0L)
        if (ts <= 0L || System.currentTimeMillis() - ts > maxAgeMs) return null
        val raw = prefs.getString(KEY_CACHED_NEARBY_JSON, null) ?: return null
        return runCatching {
            val listType = object : TypeToken<List<OverviewProfile>>() {}.type
            gson.fromJson<List<OverviewProfile>>(raw, listType)
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun getAnyCachedNearbyProfiles(): List<OverviewProfile> {
        val raw = prefs.getString(KEY_CACHED_NEARBY_JSON, null) ?: return emptyList()
        return runCatching {
            val listType = object : TypeToken<List<OverviewProfile>>() {}.type
            gson.fromJson<List<OverviewProfile>>(raw, listType)
        }.getOrNull().orEmpty()
    }

    fun saveTokens(accessToken: String, refreshToken: String?, expiresInSeconds: Long = 3600L) {
        val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken.trim())
            if (!refreshToken.isNullOrBlank()) {
                putString(KEY_REFRESH_TOKEN, refreshToken.trim())
            }
            putLong(KEY_EXPIRES_AT, expiresAt)
            apply()
        }
    }

    suspend fun getValidAccessToken(forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val access = prefs.getString(KEY_ACCESS_TOKEN, null)
            val refresh = prefs.getString(KEY_REFRESH_TOKEN, null)
            val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
            val now = System.currentTimeMillis()

            if (!forceRefresh && !access.isNullOrBlank() && (expiresAt == 0L || now + 60_000L < expiresAt)) {
                return@withLock access
            }

            if (!refresh.isNullOrBlank()) {
                val refreshed = refreshTokensInternal(refresh)
                if (refreshed != null) {
                    return@withLock refreshed
                }
            }

            access
        }
    }

    private fun refreshTokensInternal(refreshToken: String): String? {
        return try {
            val formBody = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", CLIENT_ID)
                .add("refresh_token", refreshToken)
                .build()

            val request = Request.Builder()
                .url("$AUTH_HOST/oauth2/token")
                .post(formBody)
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) return null
                val json = JsonParser.parseString(bodyStr).asJsonObject
                val newAccess = json.get("access_token")?.asString ?: return null
                val newRefresh = json.get("refresh_token")?.asString ?: refreshToken
                val expiresIn = json.get("expires_in")?.asLong ?: 3600L
                saveTokens(newAccess, newRefresh, expiresIn)
                newAccess
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun loginWithManualToken(rawInput: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val trimmed = rawInput.trim().removePrefix("Bearer ").removePrefix("bearer ").trim()
            if (trimmed.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Pusty token"))
            }

            // Check if user pasted JSON (e.g. from localStorage persist:web_auth or token object)
            if (trimmed.startsWith("{")) {
                val obj = JsonParser.parseString(trimmed).asJsonObject
                val tokenElement = if (obj.has("token")) {
                    val rawTokenField = obj.get("token")
                    if (rawTokenField.isJsonPrimitive && rawTokenField.asJsonPrimitive.isString) {
                        JsonParser.parseString(rawTokenField.asString).asJsonObject
                    } else {
                        rawTokenField.asJsonObject
                    }
                } else {
                    obj
                }
                val access = tokenElement.get("accessToken")?.asString
                    ?: tokenElement.get("access_token")?.asString
                val refresh = tokenElement.get("refreshToken")?.asString
                    ?: tokenElement.get("refresh_token")?.asString

                if (!refresh.isNullOrBlank() && access.isNullOrBlank()) {
                    refreshTokensInternal(refresh)
                        ?: return@withContext Result.failure(IllegalStateException("Failed to refresh token (ory_rt)"))
                    return@withContext Result.success(Unit)
                } else if (!access.isNullOrBlank()) {
                    saveTokens(access, refresh, 86400L * 7)
                    return@withContext Result.success(Unit)
                }
            }

            // Check if user pasted an Ory refresh token (ory_rt_...)
            if (trimmed.startsWith("ory_rt_")) {
                refreshTokensInternal(trimmed)
                    ?: return@withContext Result.failure(IllegalStateException("Failed to exchange Refresh Token for Access Token"))
                return@withContext Result.success(Unit)
            }

            // Otherwise treat as access token (ory_at_... or JWT)
            saveTokens(trimmed, null, 86400L * 30)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun startQrAuthSession(): Result<QrAuthSession> = withContext(Dispatchers.IO) {
        try {
            cookieJar.clear()
            val codeVerifier = generateRandomUrlSafeString(48)
            val codeChallenge = sha256Base64Url(codeVerifier)
            val state = generateRandomUrlSafeString(18)

            val authUrl = "$AUTH_HOST/oauth2/auth".toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("client_id", CLIENT_ID)
                .addQueryParameter("redirect_uri", REDIRECT_URI)
                .addQueryParameter("response_type", "code")
                .addQueryParameter("scope", SCOPES)
                .addQueryParameter("state", state)
                .addQueryParameter("code_challenge", codeChallenge)
                .addQueryParameter("code_challenge_method", "S256")
                .build()

            val request = Request.Builder()
                .url(authUrl)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val html = response.body?.string().orEmpty()
                val finalUrl = response.request.url.toString()

                val hashMatch = Regex("""appAuthHash\s*:\s*"([^"]+)"""").find(html)
                val challengeMatch = Regex("""loginChallenge\s*:\s*"([^"]+)"""").find(html)

                val appAuthHash = hashMatch?.groupValues?.get(1)
                    ?: return@withContext Result.failure(
                        IllegalStateException("Nie znaleziono appAuthHash na stronie logowania (kod HTTP ${response.code})")
                    )

                val loginChallenge = challengeMatch?.groupValues?.get(1)
                    ?: response.request.url.queryParameter("login_challenge")
                    ?: return@withContext Result.failure(
                        IllegalStateException("Nie znaleziono loginChallenge")
                    )

                val qrPayload = "barq-auth;$appAuthHash"
                val qrBitmap = generateQrBitmap(qrPayload, 360)

                Result.success(
                    QrAuthSession(
                        appAuthHash = appAuthHash,
                        loginChallenge = loginChallenge,
                        loginPageUrl = finalUrl,
                        codeVerifier = codeVerifier,
                        state = state,
                        qrPayload = qrPayload,
                        qrBitmap = qrBitmap
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pollQrApproval(session: QrAuthSession): QrPollResult = withContext(Dispatchers.IO) {
        try {
            val refreshUrl = "$ACCOUNTS_HOST/login/refresh?hash=${Uri.encode(session.appAuthHash)}"
            val request = Request.Builder()
                .url(refreshUrl)
                .header("Referer", session.loginPageUrl)
                .header("Accept", "application/json")
                .get()
                .build()

            val approved: Boolean? = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bodyStr = response.body?.string().orEmpty()
                val json = JsonParser.parseString(bodyStr).asJsonObject
                val el = json.get("approved")
                if (el == null || el.isJsonNull) null else el.asBoolean
            }

            when (approved) {
                null -> QrPollResult.Pending
                false -> QrPollResult.Rejected
                true -> completeApprovedQrLogin(session)
            }
        } catch (e: Exception) {
            QrPollResult.Pending
        }
    }

    private fun completeApprovedQrLogin(session: QrAuthSession): QrPollResult {
        try {
            val formBody = FormBody.Builder()
                .add("login_challenge", session.loginChallenge)
                .build()

            val approveReq = Request.Builder()
                .url("$ACCOUNTS_HOST/login?/approve")
                .header("Origin", ACCOUNTS_HOST)
                .header("Referer", session.loginPageUrl)
                .post(formBody)
                .build()

            val nextUrl: String? = noRedirectClient.newCall(approveReq).execute().use { resp ->
                extractRedirectLocation(resp)
            }

            if (nextUrl.isNullOrBlank()) {
                return QrPollResult.Error("Missing redirect after QR approval")
            }

            var authCode: String? = null
            var currentUrl: String = nextUrl

            for (hop in 0 until 12) {
                if (currentUrl.startsWith(REDIRECT_URI)) {
                    val uri = Uri.parse(currentUrl)
                    authCode = uri.getQueryParameter("code")
                    break
                }

                val stepReq = Request.Builder()
                    .url(currentUrl)
                    .get()
                    .build()

                noRedirectClient.newCall(stepReq).execute().use { stepResp ->
                    val redirectLoc = extractRedirectLocation(stepResp)
                    if (!redirectLoc.isNullOrBlank()) {
                        currentUrl = redirectLoc
                    } else {
                        val bodyHtml = stepResp.body?.string().orEmpty()
                        if (currentUrl.contains("/consent")) {
                            val consentChallenge = Regex("""consentChallenge\s*:\s*"([^"]+)"""").find(bodyHtml)?.groupValues?.get(1)
                                ?: Uri.parse(currentUrl).getQueryParameter("consent_challenge")
                                ?: return QrPollResult.Error("Brak consent_challenge na ekranie zgody")

                            val consentForm = FormBody.Builder()
                                .add("consent_challenge", consentChallenge)
                                .add("grant_scope", "openid")
                                .add("grant_scope", "offline_access")
                                .add("grant_scope", "profile")
                                .add("grant_scope", "barq:api")
                                .add("remember", "on")
                                .build()

                            val consentPost = Request.Builder()
                                .url("$ACCOUNTS_HOST/consent?/approve")
                                .header("Origin", ACCOUNTS_HOST)
                                .header("Referer", currentUrl)
                                .post(consentForm)
                                .build()

                            noRedirectClient.newCall(consentPost).execute().use { consentResp ->
                                val afterConsentLoc = extractRedirectLocation(consentResp)
                                    ?: return QrPollResult.Error("Brak przekierowania po zatwierdzeniu zgody")
                                currentUrl = afterConsentLoc
                            }
                        } else {
                            return QrPollResult.Error("Nieoczekiwany krok autoryzacji: $currentUrl")
                        }
                    }
                }
            }

            if (authCode.isNullOrBlank()) {
                return QrPollResult.Error("Nie otrzymano kodu autoryzacyjnego OAuth2")
            }

            val tokenForm = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", CLIENT_ID)
                .add("redirect_uri", REDIRECT_URI)
                .add("code", authCode)
                .add("code_verifier", session.codeVerifier)
                .build()

            val tokenReq = Request.Builder()
                .url("$AUTH_HOST/oauth2/token")
                .header("Accept", "application/json")
                .post(tokenForm)
                .build()

            httpClient.newCall(tokenReq).execute().use { tokenResp ->
                val tokenBody = tokenResp.body?.string().orEmpty()
                if (!tokenResp.isSuccessful) {
                    return QrPollResult.Error("Błąd wymiany tokenu (${tokenResp.code})")
                }
                val json = JsonParser.parseString(tokenBody).asJsonObject
                val accessToken = json.get("access_token")?.asString
                    ?: return QrPollResult.Error("Brak access_token w odpowiedzi")
                val refreshToken = json.get("refresh_token")?.asString
                val expiresIn = json.get("expires_in")?.asLong ?: 3600L

                saveTokens(accessToken, refreshToken, expiresIn)
                return QrPollResult.ApprovedAndLoggedIn
            }
        } catch (e: Exception) {
            return QrPollResult.Error(e.message ?: "Błąd autoryzacji")
        }
    }

    private fun extractRedirectLocation(response: okhttp3.Response): String? {
        val headerLoc = response.header("Location")
        if (!headerLoc.isNullOrBlank()) {
            return response.request.url.resolve(headerLoc)?.toString() ?: headerLoc
        }
        val bodyStr = response.peekBody(16384).string()
        if (bodyStr.trimStart().startsWith("{")) {
            return try {
                val json = JsonParser.parseString(bodyStr).asJsonObject
                json.get("location")?.asString
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    private fun generateRandomUrlSafeString(byteLength: Int): String {
        val bytes = ByteArray(byteLength)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun sha256Base64Url(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateQrBitmap(content: String, sizePx: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }
}

