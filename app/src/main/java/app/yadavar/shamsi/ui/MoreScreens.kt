package app.yadavar.shamsi.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.ui.text.style.TextAlign
import java.util.Calendar
import java.util.Date
import app.yadavar.shamsi.jalali.toJalali
import app.yadavar.shamsi.model.faNum
import app.yadavar.shamsi.model.formatLongDate
import app.yadavar.shamsi.model.formatTime
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import app.yadavar.shamsi.alarm.AlarmScheduler
import app.yadavar.shamsi.alarm.AlarmSound
import app.yadavar.shamsi.widget.WidgetBackgrounds
import app.yadavar.shamsi.model.Reminder
import app.yadavar.shamsi.model.rowMeta
import app.yadavar.shamsi.model.sectionOf
import kotlinx.coroutines.delay

@Composable
fun WidgetTab(padding: PaddingValues, reminders: List<Reminder>, font: String, onFont: (String) -> Unit) {
    val open = reminders.filter { !it.completed }.take(8)
    val titleSize = when (font) {
        "sm" -> 14.sp
        "lg" -> 22.sp
        else -> 18.sp
    }
    val metaSize = when (font) {
        "sm" -> 12.sp
        "lg" -> 16.sp
        else -> 14.sp
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("ویجت صفحهٔ اصلی", style = MaterialTheme.typography.headlineSmall)
        Text("روی صفحهٔ اصلی انگشت را نگه دار، ویجت‌ها را باز کن و «یادآور شمسی» را بگذار. تیک عنوان، یادآور را تمام می‌کند. اندازهٔ نوشته همین‌جا عوض می‌شود.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("sm" to "کوچک", "md" to "متوسط", "lg" to "بزرگ").forEach { (id, label) ->
                FilterChip(selected = font == id, onClick = { onFont(id) }, label = { Text(label) })
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .padding(4.dp),
        ) {
            Text("یادآور شمسی", fontSize = titleSize, color = MaterialTheme.colorScheme.primary)
            if (open.isEmpty()) Text("یادآوری نیست", fontSize = metaSize)
            open.forEach { reminder ->
                Text(reminder.title.ifBlank { "بدون عنوان" }, fontSize = titleSize)
                val meta = rowMeta(reminder, sectionOf(reminder), null)
                if (meta.isNotBlank()) Text(meta, fontSize = metaSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (reminder.expanded) {
                    reminder.items.take(3).forEach { item ->
                        Text(
                            "${if (item.done) "☑" else "☐"} ${item.title}",
                            fontSize = metaSize,
                            textDecoration = if (item.done) TextDecoration.LineThrough else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

/** A choice chip with a small circle of the color itself in front of the color's name. */
@Composable
private fun ColorChip(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, Color(0x55000000), CircleShape),
            )
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsTab(padding: PaddingValues, settings: app.yadavar.shamsi.model.Settings, vm: HomeViewModel) {
    val context = LocalContext.current
    val exact = AlarmScheduler.canExact(context)
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0.1"
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("تنظیمات", style = MaterialTheme.typography.headlineSmall)
        SectionTitle("ظاهر")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system" to "خودکار", "light" to "روشن", "dark" to "تیره").forEach { (id, label) ->
                FilterChip(selected = settings.theme == id, onClick = { vm.setTheme(id) }, label = { Text(label) })
            }
        }
        HorizontalDivider()
        SectionTitle("رنگ برنامه")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AccentOptions.forEach { option ->
                ColorChip(option.label, option.color, settings.accent == option.id) { vm.setAccent(option.id) }
            }
        }
        HorizontalDivider()
        SectionTitle("اندازهٔ نوشته‌های برنامه")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextSizeOptions.forEach { option ->
                FilterChip(
                    selected = settings.textSize == option.id,
                    onClick = { vm.setTextSize(option.id) },
                    label = { Text(option.label) },
                )
            }
        }
        Text(
            "نمونه: فردا ساعت ۹ صبح قرص",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        SectionTitle("اندازهٔ نوشتهٔ ویجت")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("sm" to "کوچک", "md" to "متوسط", "lg" to "بزرگ").forEach { (id, label) ->
                FilterChip(selected = settings.widgetFont == id, onClick = { vm.setFont(id) }, label = { Text(label) })
            }
        }
        HorizontalDivider()
        SectionTitle("رنگ پس‌زمینهٔ ویجت")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetBackgrounds.forEach { option ->
                ColorChip(option.label, option.swatch, settings.widgetBg == option.id) { vm.setWidgetBg(option.id) }
            }
        }
        HorizontalDivider()
        AlarmSoundSection(settings, vm)
        HorizontalDivider()
        BackupSection(settings, vm)
        HorizontalDivider()
        Text(if (exact) "زنگ دقیق روشن است." else "برای زنگ سر وقت دقیق، اجازهٔ هشدار دقیق را بده.")
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= 31) {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                )
            }
        }) { Text("اجازهٔ زنگ دقیق") }
        OutlinedButton(onClick = {
            if (Build.VERSION.SDK_INT >= 33) {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    },
                )
            }
        }) { Text("اعلان‌ها") }
        HorizontalDivider()
        Text(
            "یادآورها روی همین گوشی می‌مانند. زنگ با AlarmManager است و بعد از خاموش و روشن شدن دوباره تنظیم می‌شود.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        // Last items of the page, centered: the developers, and right under them the version.
        Text(
            "برنامه نویسان: محمد یگانه - مصطفی ابراهیمی",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "ورژن ${faNum(versionName)}",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun fileLabel(context: Context, uri: Uri): String {
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    return name ?: "فایل پشتیبان"
}

private fun backupTime(millis: Long): String {
    val moment = Date(millis)
    val cal = Calendar.getInstance()
    cal.time = moment
    return formatLongDate(toJalali(moment)) + " ساعت " + formatTime(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}

@Composable
private fun BackupSection(settings: app.yadavar.shamsi.model.Settings, vm: HomeViewModel) {
    val context = LocalContext.current
    val chosen = settings.backupUri.isNotBlank()
    val create = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        vm.setBackupFile(uri.toString())
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        vm.restoreFrom(uri)
    }
    Text("پشتیبان‌گیری خودکار", style = MaterialTheme.typography.titleMedium)
    Text(
        "یک فایل پشتیبان انتخاب کن. چند ثانیه بعد از هر تغییر، همهٔ یادآورها (از جمله انجام‌شده‌ها) خودکار در آن ذخیره می‌شود. فایل بیرون از برنامه است و با حذف برنامه از بین نمی‌رود.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (chosen) {
        Text("فایل: " + fileLabel(context, Uri.parse(settings.backupUri)))
    }
    Text(
        if (settings.backupAt > 0L) "آخرین پشتیبان: " + backupTime(settings.backupAt) else "هنوز پشتیبان گرفته نشده است.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = {
        create.launch(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "yadavar-backup.json")
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                )
            },
        )
    }) { Text(if (chosen) "تغییر فایل پشتیبان" else "انتخاب فایل پشتیبان") }
    if (chosen) {
        OutlinedButton(onClick = { vm.backupNow() }) { Text("پشتیبان‌گیری هم‌اکنون") }
    }
    OutlinedButton(onClick = {
        restore.launch(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            },
        )
    }) { Text("بازیابی از فایل پشتیبان") }

    HorizontalDivider()
    DriveSection(settings, vm)
}

@Composable
private fun DriveSection(settings: app.yadavar.shamsi.model.Settings, vm: HomeViewModel) {
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) vm.driveConsentResult(result.data)
    }
    Text("پشتیبان‌گیری در Google Drive", style = MaterialTheme.typography.titleMedium)
    Text(
        "با حساب گوگل وارد شو تا چند ثانیه بعد از هر تغییر، پشتیبان یادآورها خودکار در پوشهٔ «یادآور شمسی» در Google Drive خودت ذخیره شود. " +
            "با نصب دوباره یا گوشی تازه، همان حساب را وارد کن تا یادآورها برگردند. برنامه فقط به فایل‌های خودش در Drive دسترسی دارد.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (settings.driveOn) {
        Text("حساب: " + settings.driveEmail.ifBlank { "گوگل (متصل)" })
        Text(
            if (settings.driveAt > 0L) "آخرین پشتیبان Drive: " + backupTime(settings.driveAt) else "هنوز در Drive پشتیبان گرفته نشده است.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { vm.driveBackupNow() }) { Text("ذخیره در Drive هم‌اکنون") }
        OutlinedButton(onClick = { vm.driveRestore() }) { Text("بازیابی از Drive") }
        OutlinedButton(onClick = { vm.driveDisconnect() }) { Text("قطع اتصال حساب گوگل") }
    } else {
        Button(onClick = {
            vm.driveConnect { sender -> consent.launch(IntentSenderRequest.Builder(sender).build()) }
        }) { Text("ورود با حساب گوگل") }
    }
}

private fun displayName(context: Context, uri: Uri): String {
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    return name ?: "آهنگ انتخاب‌شده"
}

@Composable
private fun AlarmSoundSection(settings: app.yadavar.shamsi.model.Settings, vm: HomeViewModel) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }

    fun stopPreview() {
        player?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        player = null
        playing = false
    }

    DisposableEffect(Unit) { onDispose { stopPreview() } }
    LaunchedEffect(playing) {
        if (playing) {
            delay(8_000)
            stopPreview()
        }
    }

    val pickRingtone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) return@rememberLauncherForActivityResult
        val uri = IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        stopPreview()
        if (uri == null) {
            vm.setAlarmSound("", "")
        } else {
            val title = runCatching { RingtoneManager.getRingtone(context, uri).getTitle(context) }.getOrNull()
            vm.setAlarmSound(uri.toString(), title ?: "زنگ انتخاب‌شده")
        }
    }
    val pickSong = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        stopPreview()
        vm.setAlarmSound(uri.toString(), displayName(context, uri))
    }

    Text("صدای هشدار")
    Text(
        settings.alarmSoundName.ifBlank { "پیش‌فرض (آلارم گوشی)" },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "صدای هشدار")
                if (settings.alarmSound.isNotBlank()) {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(settings.alarmSound))
                }
            }
            runCatching { pickRingtone.launch(intent) }
        }) { Text("زنگ‌های گوشی") }
        OutlinedButton(onClick = { runCatching { pickSong.launch(arrayOf("audio/*")) } }) { Text("آهنگ از حافظه") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            if (playing) {
                stopPreview()
            } else {
                val created = AlarmSound.create(context, settings.alarmSound)
                if (created != null) {
                    runCatching { created.setVolume(1f, 1f) }
                    runCatching { created.start() }
                    player = created
                    playing = true
                }
            }
        }) { Text(if (playing) "توقف" else "پخش آزمایشی") }
        if (settings.alarmSound.isNotBlank()) {
            OutlinedButton(onClick = {
                stopPreview()
                vm.setAlarmSound("", "")
            }) { Text("بازگشت به پیش‌فرض") }
        }
    }
    Text("مدت پخش")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(10 to "۱۰ ثانیه", 30 to "۳۰ ثانیه", 60 to "۶۰ ثانیه").forEach { (seconds, label) ->
            FilterChip(
                selected = settings.alarmSeconds == seconds,
                onClick = { vm.setAlarmSeconds(seconds) },
                label = { Text(label) },
            )
        }
    }
    Text(
        "صدا ملایم شروع می‌شود و تا پایان این مدت پخش می‌شود. با «انجام شد»، «به تعویق» یا «بستن» زودتر قطع می‌شود.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
