package app.yadavar.shamsi.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.yadavar.shamsi.CrashActivity
import app.yadavar.shamsi.CrashLog
import app.yadavar.shamsi.Graph
import app.yadavar.shamsi.data.Actions
import app.yadavar.shamsi.jalali.toJalali
import app.yadavar.shamsi.model.Reminder
import app.yadavar.shamsi.model.Section
import app.yadavar.shamsi.model.Settings
import app.yadavar.shamsi.model.TimeFilter
import app.yadavar.shamsi.model.filterReminders
import app.yadavar.shamsi.model.dateOf
import app.yadavar.shamsi.model.SECTION_LABEL
import app.yadavar.shamsi.model.formatLongDate
import app.yadavar.shamsi.model.rowMeta
import app.yadavar.shamsi.model.sectionOf
import app.yadavar.shamsi.voice.parseReminderSpeech
import java.util.Date
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    /** Reminder id requested by a widget tap; the screen opens its edit form once the list is loaded. */
    private val openRequest = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRequest.value = intent.getStringExtra(EXTRA_OPEN_ID)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (CrashLog.read(this) != null || !Graph.isReady()) {
            startActivity(Intent(this, CrashActivity::class.java))
            finish()
            return
        }
        openRequest.value = intent?.getStringExtra(EXTRA_OPEN_ID)
        enableEdgeToEdge()
        setContent {
            val settings by Graph.settings.flow.collectAsStateWithLifecycle(initialValue = Settings())
            YadavarTheme(settings.theme, settings.accent, settings.textSize) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AppScreen(openRequest)
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_ID = "open_reminder_id"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen(openRequest: MutableState<String?>, vm: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(openRequest.value, reminders) {
        val wanted = openRequest.value ?: return@LaunchedEffect
        val target = reminders.firstOrNull { it.id == wanted }
        if (target != null) {
            vm.tab = Tab.Home
            vm.open(target)
            openRequest.value = null
        } else if (reminders.isNotEmpty()) {
            openRequest.value = null
        }
    }
    val speech = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val said = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return@rememberLauncherForActivityResult
        vm.openParsed(parseReminderSpeech(said), said)
    }
    val askNotification = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(vm.notice) {
        val text = vm.notice ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.notice = null
    }

    /**
     * Voice input. With internet the system's normal (online) speech recognizer is used. Without internet the same
     * system recognizer is asked to prefer its offline engine (the downloaded language pack of the speech service
     * or of another offline recognizer app installed on the phone).
     */
    fun startVoice() {
        val online = isOnline(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "یادآور را بگو")
            if (!online) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        runCatching { speech.launch(intent) }.onFailure {
            vm.notice = if (online) {
                "تشخیص گفتار روی این گوشی نیست"
            } else {
                "اینترنت نیست و تشخیص گفتار آفلاین پیدا نشد؛ بستهٔ زبان فارسی آفلاین را در تنظیمات صدای گوگل دانلود کن"
            }
        }
    }

    // Open / close of the editor fades softly instead of jumping.
    AnimatedContent(
        targetState = vm.editing,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
        label = "editor",
    ) { target ->
        if (target != null) {
            val isNew = reminders.none { it.id == target.id }
            EditorScreen(
                initial = target,
                initialSentence = vm.spoken,
                onClose = vm::closeEditor,
                onSave = vm::save,
                onDelete = if (isNew) null else ({ vm.run { Actions.delete(target.id); vm.closeEditor() } }),
            )
        } else {
            MainScaffold(vm = vm, reminders = reminders, settings = settings, snackbar = snackbar, onVoice = { startVoice() })
        }
    }
}

private fun isOnline(context: android.content.Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = manager.activeNetwork ?: return false
    val caps = manager.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

@Composable
private fun MainScaffold(
    vm: HomeViewModel,
    reminders: List<Reminder>,
    settings: Settings,
    snackbar: SnackbarHostState,
    onVoice: () -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            AnimatedVisibility(
                visible = vm.tab == Tab.Home,
                enter = scaleIn(tween(200)) + fadeIn(tween(200)),
                exit = scaleOut(tween(150)) + fadeOut(tween(150)),
            ) {
                // Modern FABs in the bottom corner: a small tonal microphone above the main "+" button.
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SmallFloatingActionButton(
                        onClick = onVoice,
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Icon(Icons.Outlined.Mic, contentDescription = "گفتن یادآور")
                    }
                    FloatingActionButton(
                        onClick = vm::openNew,
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "یادآور جدید")
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                listOf(
                    Triple(Tab.Home, "یادآورها", Icons.Filled.Home),
                    Triple(Tab.Widget, "ویجت", Icons.Filled.Widgets),
                    Triple(Tab.Settings, "تنظیمات", Icons.Filled.Tune),
                ).forEach { (tab, label, icon) ->
                    NavigationBarItem(
                        selected = vm.tab == tab,
                        onClick = { vm.tab = tab },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        // Soft fade + a small slide between the bottom-navigation tabs (the app is right-to-left).
        AnimatedContent(
            targetState = vm.tab,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) -1 else 1
                (fadeIn(tween(240)) + slideInHorizontally(tween(240)) { width -> dir * width / 10 }) togetherWith
                    (fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { width -> -dir * width / 10 })
            },
            label = "tabs",
        ) { tab ->
            when (tab) {
                Tab.Home -> HomeTab(
                    padding = padding,
                    reminders = reminders,
                    filter = vm.filter,
                    onFilter = { vm.filter = it },
                    onOpen = vm::open,
                    onComplete = { vm.run { if (it.completed) Actions.restore(it.id) else Actions.complete(it.id) } },
                    onExpand = { vm.run { Actions.toggleExpanded(it.id) } },
                    onToggleItem = { reminder, item -> vm.run { Actions.toggleItem(reminder.id, item) } },
                )
                Tab.Widget -> WidgetTab(padding, reminders, settings.widgetFont, vm::setFont)
                Tab.Settings -> SettingsTab(padding, settings, vm)
            }
        }
    }
}

@Composable
private fun HomeTab(
    padding: PaddingValues,
    reminders: List<Reminder>,
    filter: String,
    onFilter: (String) -> Unit,
    onOpen: (Reminder) -> Unit,
    onComplete: (Reminder) -> Unit,
    onExpand: (Reminder) -> Unit,
    onToggleItem: (Reminder, String) -> Unit,
) {
    val today = remember { toJalali(Date()) }
    var query by rememberSaveable { mutableStateOf("") }
    val searching = query.isNotBlank()
    // While searching, every active reminder is searched (not only the selected time filter).
    val activeFilter = if (searching && filter != "done") "all" else filter
    val timeFilter = TimeFilter.of(activeFilter)
    val base: List<Reminder> = if (activeFilter == "done") {
        reminders.filter { it.completed }.sortedByDescending { it.completedAt ?: 0L }
    } else {
        filterReminders(reminders, timeFilter)
    }
    val rows: List<Reminder> = if (searching) {
        // Every word of the query must be found somewhere in the title, the items, the date or the meta line.
        val words = normalizeSearch(query).split(' ').filter { it.isNotBlank() }
        base.filter { reminder ->
            val haystack = searchText(reminder)
            words.all { haystack.contains(it) }
        }
    } else {
        base
    }
    val grouped = if (activeFilter == "all") {
        Section.entries.map { section -> section to rows.filter { sectionOf(it) == section } }.filter { it.second.isNotEmpty() }
    } else {
        emptyList()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            // Only the Jalali date is shown at the top (the "یادآور شمسی" subtitle was removed).
            Text(formatLongDate(today), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                placeholder = { Text("جستجو در عنوان، موارد، تاریخ و ساعت") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searching) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "پاک کردن جستجو")
                        }
                    }
                },
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (TimeFilter.entries.map { it.id to it.label } + ("done" to "انجام‌شده")).forEach { (id, label) ->
                    FilterChip(selected = filter == id, onClick = { onFilter(id) }, label = { Text(label) })
                }
            }
        }
        if (activeFilter == "all") {
            grouped.forEach { (section, list) ->
                item(key = "section-${section.name}") {
                    Text(
                        SECTION_LABEL.getValue(section),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                items(list, key = { it.id }) { reminder ->
                    ReminderRow(reminder, section, onOpen, onComplete, onExpand, onToggleItem, Modifier.animateItem())
                }
            }
            if (grouped.isEmpty()) {
                item { Text(if (searching) "نتیجه‌ای پیدا نشد." else "هنوز یادآوری نیست. دکمهٔ + را بزن.") }
            }
        } else {
            items(rows, key = { it.id }) { reminder ->
                ReminderRow(
                    reminder,
                    if (reminder.completed) null else sectionOf(reminder),
                    onOpen,
                    onComplete,
                    onExpand,
                    onToggleItem,
                    Modifier.animateItem(),
                )
            }
            if (rows.isEmpty()) item { Text(if (searching) "نتیجه‌ای پیدا نشد." else "چیزی در این فهرست نیست.") }
        }
    }
}

/**
 * Makes Arabic and Persian letter variants and digits equal, and half-spaces plain spaces,
 * so a search finds both spellings ("۱۸" = "18", "ي" = "ی").
 */
private fun normalizeSearch(text: String): String {
    val out = StringBuilder(text.length)
    for (ch in text.trim().lowercase()) {
        out.append(
            when (ch) {
                'ي' -> 'ی'
                'ك' -> 'ک'
                '\u200c' -> ' '
                in '۰'..'۹' -> '0' + (ch - '۰')
                in '٠'..'٩' -> '0' + (ch - '٠')
                else -> ch
            },
        )
    }
    return out.toString()
}

/** Everything of a reminder the search looks at, normalized: title, items, date (words and numbers), time, repeat, place. */
private fun searchText(reminder: Reminder): String {
    val parts = mutableListOf<String>()
    parts += reminder.title
    reminder.items.forEach { parts += it.title }
    val date = dateOf(reminder)
    if (date != null) {
        parts += formatLongDate(date)
        parts += "${date.jy}/${date.jm}/${date.jd}"
        parts += "${date.jy}/${date.jm.toString().padStart(2, '0')}/${date.jd.toString().padStart(2, '0')}"
        parts += SECTION_LABEL.getValue(sectionOf(reminder))
    }
    val hour = reminder.hour
    val minute = reminder.minute
    if (hour != null && minute != null) {
        parts += "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
    }
    parts += rowMeta(reminder, null, null)
    parts += reminder.location
    return parts.filter { it.isNotBlank() }.joinToString("\n") { normalizeSearch(it) }
}

private val OverdueColor = Color(0xFFE05A5A)
private val TodayColor = Color(0xFF1E9E6A)

@Composable
private fun ReminderRow(
    reminder: Reminder,
    section: Section?,
    onOpen: (Reminder) -> Unit,
    onComplete: (Reminder) -> Unit,
    onExpand: (Reminder) -> Unit,
    onToggleItem: (Reminder, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = if (reminder.completed) null else section ?: sectionOf(reminder)
    // Overdue: soft red. Today: green. Everything else keeps the normal colors.
    val metaColor = when (status) {
        Section.Overdue -> OverdueColor
        Section.Today -> TodayColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val ringColor = when (status) {
        Section.Overdue -> OverdueColor
        Section.Today -> TodayColor
        else -> MaterialTheme.colorScheme.primary
    }
    // Tick animation: the circle fills and pops first; the reminder is completed a moment later,
    // then the row fades out of the list (animateItem). The state resets as soon as the data changes.
    var ticked by remember(reminder.id, reminder.updatedAt, reminder.completed) { mutableStateOf(false) }
    LaunchedEffect(ticked) {
        if (ticked) {
            delay(420)
            onComplete(reminder)
        }
    }
    val checked = reminder.completed || ticked
    val fill by animateColorAsState(if (checked) ringColor else Color.Transparent, tween(220), label = "tickFill")
    val pop by animateFloatAsState(
        targetValue = if (ticked) 1.18f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tickPop",
    )
    Card(
        onClick = { onOpen(reminder) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = pop
                            scaleY = pop
                        }
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(fill)
                        .border(2.dp, ringColor, CircleShape)
                        .clickable { if (reminder.completed) onComplete(reminder) else ticked = true },
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedVisibility(
                        visible = checked,
                        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(tween(120)),
                        exit = fadeOut(tween(100)),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        reminder.title.ifBlank { "بدون عنوان" },
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (reminder.completed || ticked) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = rowMeta(reminder, section, null)
                    if (meta.isNotBlank()) {
                        Text(meta, color = metaColor, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (reminder.items.isNotEmpty()) {
                Text(
                    if (reminder.expanded) "بستن موارد" else "موارد (${app.yadavar.shamsi.model.faNum(reminder.items.size)})",
                    modifier = Modifier.padding(top = 8.dp).clickable { onExpand(reminder) },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
                if (reminder.expanded) {
                    reminder.items.forEach { item ->
                        Row(
                            modifier = Modifier.padding(top = 6.dp).clickable { onToggleItem(reminder, item.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (item.done) "☑" else "☐")
                            Text(
                                item.title,
                                modifier = Modifier.padding(start = 8.dp),
                                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                                color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}
