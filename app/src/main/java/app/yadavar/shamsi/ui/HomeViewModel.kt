package app.yadavar.shamsi.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yadavar.shamsi.Graph
import app.yadavar.shamsi.data.Actions
import app.yadavar.shamsi.data.BackupManager
import app.yadavar.shamsi.data.DriveBackup
import app.yadavar.shamsi.model.Reminder
import app.yadavar.shamsi.model.Repeat
import app.yadavar.shamsi.model.blankReminder
import app.yadavar.shamsi.voice.ParsedSpeech
import app.yadavar.shamsi.widget.YadavarWidget
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Tab { Home, Widget, Settings }

private const val DEFAULT_GROUP = "personal"

class HomeViewModel : ViewModel() {
    val reminders = Graph.repo.reminders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = Graph.settings.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), app.yadavar.shamsi.model.Settings())

    var editing by mutableStateOf<Reminder?>(null)
    var spoken by mutableStateOf("")
    var tab by mutableStateOf(Tab.Home)
    var filter by mutableStateOf("all")
    var notice by mutableStateOf<String?>(null)

    fun openNew() {
        spoken = ""
        editing = blankReminder(DEFAULT_GROUP)
    }

    fun open(reminder: Reminder) {
        spoken = ""
        editing = reminder
    }

    fun closeEditor() {
        editing = null
        spoken = ""
    }

    /** Opens the editor already filled from the spoken sentence, so nothing has to be typed again. */
    fun openParsed(parsed: ParsedSpeech, sentence: String) {
        spoken = sentence
        val base = blankReminder(DEFAULT_GROUP)
        editing = base.copy(
            title = parsed.title.ifBlank { sentence.trim() },
            jy = parsed.jy,
            jm = parsed.jm,
            jd = parsed.jd,
            hour = parsed.hour,
            minute = parsed.minute,
            alert = true,
            repeat = Repeat.of(parsed.repeatKind ?: "none", 1),
            anchorDay = parsed.jd,
        )
    }

    fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { notice = "انجام نشد" }
        }
    }

    fun save(reminder: Reminder) = run {
        Actions.save(reminder)
        closeEditor()
    }

    fun setTheme(value: String) = run { Graph.settings.setTheme(value) }
    fun setAccent(value: String) = run { Graph.settings.setAccent(value) }
    fun setFont(value: String) = run {
        Graph.settings.setFont(value)
        YadavarWidget().updateAll(Graph.appContext)
    }

    fun setBackupFile(uri: String) = run {
        Graph.settings.setBackupUri(uri)
        val ok = BackupManager.autoBackup(Graph.appContext, force = true)
        notice = if (ok) "فایل پشتیبان آماده شد" else "نوشتن در فایل پشتیبان انجام نشد"
    }

    fun backupNow() = run {
        val ok = BackupManager.autoBackup(Graph.appContext, force = true)
        notice = if (ok) "پشتیبان‌گیری انجام شد" else "پشتیبان‌گیری نشد؛ فایل پشتیبان را دوباره انتخاب کن"
    }

    fun restoreFrom(uri: android.net.Uri) = run {
        val count = runCatching { BackupManager.importFrom(Graph.appContext, uri) }.getOrDefault(-1)
        notice = if (count >= 0) "${app.yadavar.shamsi.model.faNum(count)} یادآور بازیابی شد" else "فایل پشتیبان معتبر نیست"
    }

    fun setWidgetBg(value: String) = run {
        Graph.settings.setWidgetBg(value)
        YadavarWidget().updateAll(Graph.appContext)
    }

    fun setTextSize(value: String) = run { Graph.settings.setTextSize(value) }

    // ---- Google Drive backup ----

    /** Starts the Google sign-in. [openConsent] opens the account chooser / consent screen when one is needed. */
    fun driveConnect(openConsent: (android.content.IntentSender) -> Unit) = run {
        when (val auth = DriveBackup.authorize(Graph.appContext)) {
            is DriveBackup.AuthState.Ready -> finishDriveConnect(auth)
            is DriveBackup.AuthState.NeedsConsent -> openConsent(auth.sender)
            is DriveBackup.AuthState.Failed -> notice = "ورود به حساب گوگل انجام نشد؛ اینترنت و سرویس‌های گوگل را بررسی کن"
        }
    }

    /** Result of the Google consent screen. */
    fun driveConsentResult(data: android.content.Intent?) = run {
        when (val auth = DriveBackup.fromConsentResult(Graph.appContext, data)) {
            is DriveBackup.AuthState.Ready -> finishDriveConnect(auth)
            is DriveBackup.AuthState.NeedsConsent -> notice = "اجازهٔ دسترسی کامل نشد"
            is DriveBackup.AuthState.Failed -> notice = "ورود به حساب گوگل انجام نشد"
        }
    }

    private suspend fun finishDriveConnect(auth: DriveBackup.AuthState.Ready) {
        Graph.settings.setDrive(true, auth.email ?: "")
        // A backup that already lives in Drive is merged in first (a newer reminder in the app wins),
        // so signing in on a fresh install brings the old reminders back instead of overwriting them.
        val restored = DriveBackup.restore(Graph.appContext)
        val saved = DriveBackup.backup(Graph.appContext)
        notice = when {
            restored > 0 -> "حساب گوگل متصل شد و ${app.yadavar.shamsi.model.faNum(restored)} یادآور از Drive بازیابی شد"
            saved -> "حساب گوگل متصل شد و پشتیبان در Drive ذخیره شد"
            else -> "حساب گوگل متصل شد"
        }
    }

    fun driveBackupNow() = run {
        val ok = DriveBackup.backup(Graph.appContext, force = true)
        notice = if (ok) "پشتیبان در Google Drive ذخیره شد" else "ذخیره در Drive انجام نشد؛ اینترنت را بررسی کن"
    }

    fun driveRestore() = run {
        val count = DriveBackup.restore(Graph.appContext)
        notice = when {
            count >= 0 -> "${app.yadavar.shamsi.model.faNum(count)} یادآور از Drive بازیابی شد"
            count == DriveBackup.RESTORE_NO_FILE -> "در Drive پشتیبانی پیدا نشد"
            count == DriveBackup.RESTORE_NEEDS_LOGIN -> "اول دوباره با حساب گوگل وارد شو"
            else -> "بازیابی از Drive انجام نشد"
        }
    }

    fun driveDisconnect() = run {
        Graph.settings.setDrive(false, "")
        notice = "اتصال به Google Drive قطع شد"
    }

    fun setAlarmSound(uri: String, name: String) = run { Graph.settings.setAlarmSound(uri, name) }
    fun setAlarmSeconds(value: Int) = run { Graph.settings.setAlarmSeconds(value) }
}
