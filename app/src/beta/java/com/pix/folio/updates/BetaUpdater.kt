package com.pix.folio.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.pix.folio.BuildConfig
import com.pix.folio.data.BetaRelease
import com.pix.folio.data.FolioVersion
import com.pix.folio.data.UpdateStatus
import com.pix.folio.data.UpdateUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest
import java.time.Instant

object BetaUpdater {
    private const val RELEASES_API = "https://api.github.com/repos/0xpix/folio-android/releases?per_page=30"
    private const val USER_AGENT_PREFIX = "Folio-Beta-Updater/"

    sealed interface InstallResult {
        data object Started : InstallResult
        data object PermissionRequested : InstallResult
        data class Error(val message: String) : InstallResult
    }

    suspend fun check(currentVersion: String = BuildConfig.VERSION_NAME): UpdateUiState =
        withContext(Dispatchers.IO) {
            val checkedAt = System.currentTimeMillis()
            runCatching {
                val releases = JSONArray(getText(RELEASES_API, currentVersion))
                val parsed = buildList {
                    for (index in 0 until releases.length()) {
                        val json = releases.optJSONObject(index) ?: continue
                        if (json.optBoolean("draft", false)) continue
                        val tag = json.optString("tag_name").trim()
                        val version = tag.removePrefix("v").removePrefix("V")
                        if (version.isBlank() || !FolioVersion.hasNumericVersion(version)) continue

                        val assets = json.optJSONArray("assets") ?: JSONArray()
                        val candidates = buildList<Pair<String, String>> {
                            for (i in 0 until assets.length()) {
                                val asset = assets.optJSONObject(i) ?: continue
                                val name = asset.optString("name")
                                val url = asset.optString("browser_download_url")
                                if (name.endsWith(".apk", true) && url.isNotBlank()) add(name to url)
                            }
                        }
                        val selected = candidates.firstOrNull { it.first.contains(tag, true) }
                            ?: candidates.firstOrNull { it.first.contains("beta", true) }
                            ?: candidates.firstOrNull()

                        val checksumUrl = selected?.let { apk ->
                            (0 until assets.length())
                                .mapNotNull { assets.optJSONObject(it) }
                                .firstOrNull { it.optString("name") == "${apk.first}.sha256" }
                                ?.optString("browser_download_url")
                                ?.takeIf(String::isNotBlank)
                        }

                        add(
                            BetaRelease(
                                tagName = tag,
                                versionName = version,
                                title = json.optString("name").ifBlank { "Folio $tag" },
                                notes = cleanNotes(json.optString("body")),
                                publishedAt = json.optString("published_at").takeIf(String::isNotBlank)
                                    ?.let { runCatching { Instant.parse(it) }.getOrNull() },
                                htmlUrl = json.optString("html_url"),
                                apkName = selected?.first,
                                apkUrl = selected?.second,
                                checksumUrl = checksumUrl,
                            )
                        )
                    }
                }

                val newest = parsed
                    .filter { FolioVersion.compare(it.versionName, currentVersion) > 0 }
                    .maxWithOrNull { a, b -> FolioVersion.compare(a.versionName, b.versionName) }

                if (newest == null) {
                    UpdateUiState(UpdateStatus.UP_TO_DATE, checkedAtMillis = checkedAt)
                } else {
                    UpdateUiState(UpdateStatus.AVAILABLE, release = newest, checkedAtMillis = checkedAt)
                }
            }.getOrElse { error ->
                UpdateUiState(UpdateStatus.ERROR, error = friendlyError(error), checkedAtMillis = checkedAt)
            }
        }

    suspend fun download(context: Context, release: BetaRelease): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = release.apkUrl ?: error("This release does not contain an APK")
                val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
                updateDir.listFiles()?.filter(File::isFile)?.forEach(File::delete)
                val apk = File(updateDir, safeFileName(release.apkName ?: "folio-${release.tagName}.apk"))
                downloadTo(url, apk, release.versionName)

                release.checksumUrl?.let { checksum ->
                    val expected = getText(checksum, release.versionName).trim().substringBefore(' ').lowercase()
                    check(expected.length == 64 && expected == sha256(apk)) {
                        "Downloaded APK checksum did not match the GitHub release"
                    }
                }
                verifyApk(context, apk)
                apk
            }
        }

    fun install(context: Context, apk: File): InstallResult {
        if (!apk.isFile) return InstallResult.Error("The downloaded APK is missing")
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return InstallResult.PermissionRequested
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            InstallResult.Started
        }.getOrElse { InstallResult.Error(it.message ?: "Could not open the Android installer") }
    }

    fun openRelease(context: Context, release: BetaRelease) {
        if (release.htmlUrl.isBlank()) return
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    @Suppress("DEPRECATION")
    private fun verifyApk(context: Context, apk: File) {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getPackageArchiveInfo(apk.absolutePath, 0)
        } ?: error("Downloaded file is not a valid Android APK")

        check(info.packageName == context.packageName) {
            "Downloaded APK belongs to ${info.packageName}, not ${context.packageName}"
        }
        val downloadedCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
        check(downloadedCode > BuildConfig.VERSION_CODE.toLong()) { "Downloaded APK is not newer than this Folio beta" }
    }

    private fun cleanNotes(value: String): String = if (value.isBlank()) {
        "Folio beta improvements."
    } else {
        value.lineSequence()
            .map { it.trim().removePrefix("### ").removePrefix("## ").removePrefix("# ").removePrefix("- ").removePrefix("* ") }
            .filter(String::isNotBlank)
            .take(10)
            .joinToString("\n")
            .take(1_500)
    }

    private fun friendlyError(error: Throwable): String = when (error) {
        is UnknownHostException -> "Couldn't reach GitHub. Check your connection."
        is SocketTimeoutException -> "GitHub took too long to respond."
        else -> error.message?.takeIf(String::isNotBlank) ?: "Couldn't check for updates."
    }

    private fun getText(url: String, version: String): String {
        val connection = open(url, version)
        return try { connection.inputStream.bufferedReader().use { it.readText() } }
        finally { connection.disconnect() }
    }

    private fun downloadTo(url: String, destination: File, version: String) {
        val connection = open(url, version)
        try { connection.inputStream.use { input -> destination.outputStream().buffered().use(input::copyTo) } }
        finally { connection.disconnect() }
    }

    private fun open(url: String, version: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "$USER_AGENT_PREFIX$version")
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        connection.connect()
        if (connection.responseCode !in 200..299) {
            val code = connection.responseCode
            connection.disconnect()
            error(
                when (code) {
                    404 -> "Folio releases are not publicly reachable. The GitHub beta updater needs a public repository."
                    403 -> "GitHub rate limit reached. Try again later."
                    else -> "GitHub returned HTTP $code"
                }
            )
        }
        return connection
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun safeFileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "folio-beta.apk" }
}
