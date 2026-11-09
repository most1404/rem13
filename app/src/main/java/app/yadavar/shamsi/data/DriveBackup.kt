package app.yadavar.shamsi.data

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import app.yadavar.shamsi.Graph
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup to the user's own Google Drive.
 *
 * Sign-in / consent: Google Identity "Authorization" API (play-services-auth). The user picks a Google account and
 * allows the narrow `drive.file` scope (the app can only see files it created itself).
 * Upload / download: plain Drive REST v3 calls with the access token, so no extra Drive client library is needed.
 * The backup is one JSON file inside a Drive folder named [FOLDER_NAME].
 *
 * Needs a one-time setup in Google Cloud Console (OAuth client of type Android for package app.yadavar.shamsi with the
 * SHA-1 of app/yadavar.jks, Drive API enabled). See README.
 */
object DriveBackup {
    private const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    private const val FOLDER_NAME = "یادآور شمسی"
    private const val FILE_NAME = "yadavar-backup.json"
    private const val FOLDER_MIME = "application/vnd.google-apps.folder"

    sealed interface AuthState {
        data class Ready(val token: String, val email: String?) : AuthState
        data class NeedsConsent(val sender: IntentSender) : AuthState
        data class Failed(val message: String) : AuthState
    }

    // ---------------------------------------------------------------- sign-in / authorization

    private fun request(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE_DRIVE_FILE)))
            .build()

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { value -> cont.resume(value) }
        addOnFailureListener { error -> cont.resumeWithException(error) }
    }

    private fun toState(result: AuthorizationResult): AuthState {
        if (result.hasResolution()) {
            val sender = result.pendingIntent?.intentSender ?: return AuthState.Failed("صفحهٔ اجازه باز نشد")
            return AuthState.NeedsConsent(sender)
        }
        val token = result.accessToken ?: return AuthState.Failed("توکن گرفته نشد")
        val email = runCatching { result.toGoogleSignInAccount()?.email }.getOrNull()
        return AuthState.Ready(token, email)
    }

    /**
     * Asks Google for an access token. When the user has already allowed the app this returns [AuthState.Ready]
     * without any screen; otherwise [AuthState.NeedsConsent] carries the account-chooser / consent screen to launch.
     */
    suspend fun authorize(context: Context): AuthState = try {
        toState(Identity.getAuthorizationClient(context).authorize(request()).awaitResult())
    } catch (error: Exception) {
        AuthState.Failed(error.message ?: "سرویس‌های گوگل در دسترس نیست")
    }

    /** Called with the data of the consent screen's result. */
    fun fromConsentResult(context: Context, data: Intent?): AuthState = try {
        toState(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))
    } catch (error: Exception) {
        AuthState.Failed(error.message ?: "ورود انجام نشد")
    }

    // ---------------------------------------------------------------- Drive REST helpers

    private class HttpResult(val code: Int, val body: String)

    private fun call(
        method: String,
        url: String,
        token: String,
        body: ByteArray? = null,
        contentType: String? = null,
        overridePatch: Boolean = false,
    ): HttpResult {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 20_000
            conn.readTimeout = 40_000
            conn.requestMethod = method
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")
            // HttpURLConnection has no reliable PATCH; Google APIs accept this override on a POST.
            if (overridePatch) conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            if (body != null) {
                conn.doOutput = true
                if (contentType != null) conn.setRequestProperty("Content-Type", contentType)
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            return HttpResult(code, text)
        } finally {
            conn.disconnect()
        }
    }

    private fun ok(result: HttpResult): String {
        if (result.code !in 200..299) throw java.io.IOException("Drive ${result.code}: ${result.body.take(200)}")
        return result.body
    }

    private fun enc(text: String): String = URLEncoder.encode(text, "UTF-8")

    private fun firstId(json: String): String? {
        val files: JSONArray = JSONObject(json).optJSONArray("files") ?: return null
        return if (files.length() > 0) files.getJSONObject(0).optString("id").ifBlank { null } else null
    }

    private fun findFolder(token: String): String? {
        val q = "name='$FOLDER_NAME' and mimeType='$FOLDER_MIME' and trashed=false"
        val url = "https://www.googleapis.com/drive/v3/files?q=${enc(q)}&fields=${enc("files(id)")}&pageSize=1"
        return firstId(ok(call("GET", url, token)))
    }

    private fun createFolder(token: String): String {
        val meta = JSONObject().put("name", FOLDER_NAME).put("mimeType", FOLDER_MIME).toString()
        val text = ok(
            call(
                "POST",
                "https://www.googleapis.com/drive/v3/files?fields=id",
                token,
                meta.toByteArray(Charsets.UTF_8),
                "application/json; charset=UTF-8",
            ),
        )
        return JSONObject(text).getString("id")
    }

    private fun findFile(token: String, folderId: String?): String? {
        val parent = if (folderId != null) " and '$folderId' in parents" else ""
        val q = "name='$FILE_NAME' and trashed=false$parent"
        val url = "https://www.googleapis.com/drive/v3/files?q=${enc(q)}&fields=${enc("files(id)")}" +
            "&orderBy=${enc("modifiedTime desc")}&pageSize=1"
        return firstId(ok(call("GET", url, token)))
    }

    private fun createFile(token: String, folderId: String, content: String) {
        val boundary = "yadavar" + System.currentTimeMillis()
        val meta = JSONObject()
            .put("name", FILE_NAME)
            .put("mimeType", "application/json")
            .put("parents", JSONArray().put(folderId))
            .toString()
        val body = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(meta).append("\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(content).append("\r\n")
            append("--").append(boundary).append("--")
        }
        ok(
            call(
                "POST",
                "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id",
                token,
                body.toByteArray(Charsets.UTF_8),
                "multipart/related; boundary=$boundary",
            ),
        )
    }

    private fun updateFile(token: String, fileId: String, content: String) {
        ok(
            call(
                "POST",
                "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media&fields=id",
                token,
                content.toByteArray(Charsets.UTF_8),
                "application/json; charset=UTF-8",
                overridePatch = true,
            ),
        )
    }

    private fun upload(token: String, content: String) {
        val folderId = findFolder(token) ?: createFolder(token)
        val fileId = findFile(token, folderId)
        if (fileId == null) createFile(token, folderId, content) else updateFile(token, fileId, content)
    }

    /** The newest backup text in Drive, or null when there is none. */
    private fun download(token: String): String? {
        val fileId = findFile(token, null) ?: return null
        return ok(call("GET", "https://www.googleapis.com/drive/v3/files/$fileId?alt=media", token))
    }

    // ---------------------------------------------------------------- public operations

    /**
     * Uploads the backup. Without [force] an empty reminder list is skipped, so a fresh install can never
     * overwrite an existing Drive backup before it has been restored.
     */
    suspend fun backup(context: Context, force: Boolean = false): Boolean {
        if (!Graph.settings.current().driveOn) return false
        if (!force && Graph.repo.all().isEmpty()) return false
        val auth = authorize(context.applicationContext)
        if (auth !is AuthState.Ready) return false
        val json = BackupManager.exportJson()
        val done = withContext(Dispatchers.IO) { runCatching { upload(auth.token, json) }.isSuccess }
        if (done) Graph.settings.setDriveAt(System.currentTimeMillis())
        return done
    }

    /** Result codes of [restore]. */
    const val RESTORE_NO_FILE = -2
    const val RESTORE_NEEDS_LOGIN = -3
    const val RESTORE_FAILED = -1

    /** Merges the Drive backup into the app (a newer reminder in the app wins). Returns the count or a negative code. */
    suspend fun restore(context: Context): Int {
        val auth = authorize(context.applicationContext)
        if (auth is AuthState.NeedsConsent) return RESTORE_NEEDS_LOGIN
        if (auth !is AuthState.Ready) return RESTORE_FAILED
        val text = withContext(Dispatchers.IO) { runCatching { download(auth.token) } }.getOrElse { return RESTORE_FAILED }
            ?: return RESTORE_NO_FILE
        return runCatching { BackupManager.importJson(text) }.getOrDefault(RESTORE_FAILED)
    }
}
