package com.example.taskmanager

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

// ═══════════════════════════════════════════════════════════════════
//  Constants
// ═══════════════════════════════════════════════════════════════════

private const val STATE_CHANGED_ACTION = "com.example.taskmanager.STATE_CHANGED"

// ═══════════════════════════════════════════════════════════════════
//  1) Data Models
// ═══════════════════════════════════════════════════════════════════

@Serializable
enum class Priority { HIGH, MEDIUM, LOW }

@Serializable
data class Reminder(
    val id: String,
    val label: String,
    val timestamp: Long,
    val fired: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class TaskItem(
    val id: String,
    val title: String,
    val desc: String = "",
    val priority: Priority = Priority.MEDIUM,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val reminders: List<Reminder> = emptyList(),
)

@Serializable
data class AppState(
    val tasks: List<TaskItem> = emptyList(),
)

// ═══════════════════════════════════════════════════════════════════
//  2) Storage
// ═══════════════════════════════════════════════════════════════════

object TaskStorage {
    private const val FILE_NAME = "tasks.json"
    private val lock = Any()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun load(context: Context): AppState = synchronized(lock) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return AppState()
            json.decodeFromString<AppState>(file.readText())
        } catch (_: Exception) {
            AppState()
        }
    }

    fun save(context: Context, state: AppState) = synchronized(lock) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(json.encodeToString(state))
        } catch (_: Exception) {}
    }
}

// ═══════════════════════════════════════════════════════════════════
//  3) Jalali Calendar (ported from jalaali-js)
// ═══════════════════════════════════════════════════════════════════

object JalaliCalendar {

    private val BREAKS = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
        1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    )

    data class JalCalResult(val leap: Int, val gy: Int, val march: Int)
    data class JDate(val jy: Int, val jm: Int, val jd: Int)
    data class GDate(val gy: Int, val gm: Int, val gd: Int)

    fun jalCal(jy: Int): JalCalResult {
        val gy = jy + 621
        var leapJ = -14
        var jp = BREAKS[0]
        var jump = 0
        var i = 1
        while (i < BREAKS.size) {
            val jm = BREAKS[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += (jump / 33) * 8 + ((jump % 33) / 4)
            jp = jm
            i++
        }
        var n = jy - jp
        leapJ += (n / 33) * 8 + (((n % 33) + 3) / 4)
        if ((jump % 33) == 4 && (jump - n) == 4) leapJ++
        val leapG = (gy / 4) - (((gy / 100) + 1) * 3 / 4) - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
        var leap = (((n + 1) % 33) - 1) % 4
        if (leap == -1) leap = 4
        return JalCalResult(leap, gy, march)
    }

    fun g2d(gy: Int, gm: Int, gd: Int): Long {
        val inner = (gm - 8) / 6
        var d = ((gy + inner + 100100).toLong() * 1461L) / 4L
        d += (153L * ((gm + 9) % 12) + 2L) / 5L
        d += gd.toLong() - 34840408L
        val inner2 = (gy + 100100 + inner).toLong()
        d = d - ((inner2 / 100L) * 3L) / 4L + 752L
        return d
    }

    fun d2g(jdn: Long): GDate {
        var j = 4L * jdn + 139361631L
        val inner = (4L * jdn + 183187720L) / 146097L
        val y = inner * 3L
        val res = y / 4L
        j += res * 4L - 3908L
        val i = (j % 1461L) / 4L * 5L + 308L
        val gd = ((i % 153L) / 5L + 1L).toInt()
        val gm = ((i / 153L) % 12L + 1L).toInt()
        val gy = (j / 1461L - 100100L + (8L - gm) / 6L).toInt()
        return GDate(gy, gm, gd)
    }

    fun j2d(jy: Int, jm: Int, jd: Int): Long {
        val r = jalCal(jy)
        return g2d(r.gy, 3, r.march) + (jm - 1).toLong() * 31L -
                (jm / 7).toLong() * (jm - 7) + jd - 1
    }

    fun d2j(jdn: Long): JDate {
        val gy = d2g(jdn).gy
        var jy = gy - 621
        val r = jalCal(jy)
        val jdn1f = g2d(gy, 3, r.march)
        var k = jdn - jdn1f
        if (k >= 0) {
            if (k <= 185L) {
                val jm = 1 + (k / 31L).toInt()
                val jd = (k % 31L).toInt() + 1
                return JDate(jy, jm, jd)
            } else {
                k -= 186L
            }
        } else {
            jy -= 1
            k += 179L
            if (r.leap == 1) k += 1L
        }
        val jm = 7 + (k / 30L).toInt()
        val jd = (k % 30L).toInt() + 1
        return JDate(jy, jm, jd)
    }

    fun monthLength(jy: Int, jm: Int): Int {
        if (jm <= 6) return 31
        if (jm <= 11) return 30
        return if (jalCal(jy).leap == 0) 30 else 29
    }

    fun toMillis(jy: Int, jm: Int, jd: Int, hh: Int = 0, mm: Int = 0): Long {
        val g = d2g(j2d(jy, jm, jd))
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(g.gy, g.gm - 1, g.gd, hh, mm, 0)
        return cal.timeInMillis
    }

    fun fromMillis(millis: Long): JDate {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        return d2j(
            g2d(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
//  4) Formatting
// ═══════════════════════════════════════════════════════════════════

object Fmt {

    val JMONTHS = listOf(
        "Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
        "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand"
    )
    val DOW = listOf(
        "Saturday", "Sunday", "Monday", "Tuesday",
        "Wednesday", "Thursday", "Friday"
    )
    val DOW_SHORT = listOf("Sa", "Su", "Mo", "Tu", "We", "Th", "Fr")

    fun clock(hh: Int, mm: Int): String = "%02d:%02d".format(hh, mm)

    fun fullJalali(jy: Int, jm: Int, jd: Int): String = "$jd ${JMONTHS[jm - 1]} $jy"

    fun dowName(millis: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val idx = when (dow) {
            Calendar.SATURDAY -> 0
            Calendar.SUNDAY -> 1
            Calendar.MONDAY -> 2
            Calendar.TUESDAY -> 3
            Calendar.WEDNESDAY -> 4
            Calendar.THURSDAY -> 5
            Calendar.FRIDAY -> 6
            else -> 0
        }
        return DOW[idx]
    }

    fun relativeDayLabel(jy: Int, jm: Int, jd: Int): String? {
        val now = System.currentTimeMillis()
        val today = JalaliCalendar.fromMillis(now)
        if (jy == today.jy && jm == today.jm && jd == today.jd) return "Today"

        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val tom = JalaliCalendar.fromMillis(cal.timeInMillis)
        if (jy == tom.jy && jm == tom.jm && jd == tom.jd) return "Tomorrow"

        cal.timeInMillis = now
        cal.add(Calendar.DAY_OF_MONTH, 2)
        val da = JalaliCalendar.fromMillis(cal.timeInMillis)
        if (jy == da.jy && jm == da.jm && jd == da.jd) return "Day After"

        cal.timeInMillis = now
        cal.add(Calendar.DAY_OF_MONTH, -1)
        val yst = JalaliCalendar.fromMillis(cal.timeInMillis)
        if (jy == yst.jy && jm == yst.jm && jd == yst.jd) return "Yesterday"

        return null
    }

    fun relTime(ts: Long): String {
        val diff = (System.currentTimeMillis() - ts) / 1000
        return when {
            diff < 60 -> "Just now"
            diff < 3600 -> "${diff / 60} min ago"
            diff < 86400 -> "${diff / 3600} h ago"
            diff < 604800 -> "${diff / 86400} d ago"
            else -> SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(ts))
        }
    }

    data class Countdown(val txt: String, val cls: String)

    fun countdown(ts: Long): Countdown {
        val diff = ts - System.currentTimeMillis()
        if (diff <= 0) return Countdown("Passed", "past")
        val mins = ((diff + 59999L) / 60000L).toInt()
        if (mins < 1) return Countdown("Less than 1 min", "now")
        if (mins < 60) return Countdown("$mins min left", "soon")
        val hours = (diff / 3600000L).toInt()
        if (hours < 24) return Countdown("$hours h left", "soon")
        val days = (diff / 86400000L).toInt()
        if (days < 30) return Countdown("$days d left", "soon")
        val months = days / 30
        return Countdown("$months mo left", "")
    }

    data class WhenInfo(val main: String, val sub: String, val isRel: Boolean)

    fun reminderWhen(ts: Long): WhenInfo {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ts
        val j = JalaliCalendar.fromMillis(ts)
        val rel = relativeDayLabel(j.jy, j.jm, j.jd)
        val clockStr = clock(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        val dow = dowName(ts)
        val fullDate = fullJalali(j.jy, j.jm, j.jd)
        return if (rel != null) {
            WhenInfo("$rel – $clockStr", "$dow, $fullDate", true)
        } else {
            WhenInfo("$dow – $clockStr", fullDate, false)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  5) Scheduler
// ═══════════════════════════════════════════════════════════════════

object ReminderScheduler {
    const val ACTION_REMINDER = "com.example.taskmanager.ACTION_REMINDER"
    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_REMINDER_ID = "extra_reminder_id"
    const val EXTRA_TASK_TITLE = "extra_task_title"
    const val EXTRA_REMINDER_LABEL = "extra_reminder_label"

    private fun requestCode(reminderId: String): Int = reminderId.hashCode()

    fun schedule(context: Context, task: TaskItem, reminder: Reminder) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_TASK_TITLE, task.title)
            putExtra(EXTRA_REMINDER_LABEL, reminder.label)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode(reminder.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else true

        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.timestamp, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.timestamp, pi)
            }
        } catch (_: SecurityException) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.timestamp, pi)
            } catch (_: Exception) {}
        }
    }

    fun cancel(context: Context, reminderId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode(reminderId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
        }
    }

    fun rescheduleAll(context: Context) {
        val state = TaskStorage.load(context)
        val now = System.currentTimeMillis()
        state.tasks.forEach { task ->
            task.reminders.forEach { r ->
                if (!r.fired && r.timestamp > now) {
                    schedule(context, task, r)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  6) Notifications
// ═══════════════════════════════════════════════════════════════════

object Notifications {
    const val CHANNEL_ID = "task_reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Task Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alarms for your task reminders"
            enableLights(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 220, 90, 220, 90, 420)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)

            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            setSound(alarmUri, attrs)
        }
        nm.createNotificationChannel(channel)
    }

    fun showReminder(
        context: Context,
        taskId: String,
        taskTitle: String,
        label: String,
        reminderId: String,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannel(context)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val contentPi = PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("task_id", taskId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenPi = PendingIntent.getActivity(
            context,
            reminderId.hashCode() + 7777,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION
                putExtra("task_id", taskId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markDonePi = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode() + 1111,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderReceiver.ACTION_MARK_DONE
                putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId)
                putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(label)
            .setContentText("Task \"$taskTitle\" is due now")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Task \"$taskTitle\" is due now")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPi)
            .setFullScreenIntent(fullScreenPi, true)
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setOnlyAlertOnce(false)
            .addAction(0, "Mark as done", markDonePi)
            .build()

        try {
            nm.notify(reminderId.hashCode(), notif)
        } catch (_: SecurityException) {}
    }
}

// ═══════════════════════════════════════════════════════════════════
//  7) Broadcast Receivers
// ═══════════════════════════════════════════════════════════════════

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MARK_DONE = "com.example.taskmanager.ACTION_MARK_DONE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ReminderScheduler.ACTION_REMINDER -> handleReminder(context, intent)
            ACTION_MARK_DONE -> handleMarkDone(context, intent)
        }
    }

    private fun handleReminder(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID) ?: return
        val reminderId = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID) ?: return
        val taskTitle = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_TITLE) ?: ""
        val label = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_LABEL) ?: "Reminder"

        val state = TaskStorage.load(context)
        val updated = state.tasks.map { t ->
            if (t.id != taskId) t
            else t.copy(reminders = t.reminders.map { r ->
                if (r.id == reminderId) r.copy(fired = true) else r
            })
        }
        TaskStorage.save(context, AppState(updated))

        Notifications.showReminder(context, taskId, taskTitle, label, reminderId)

        context.sendBroadcast(
            Intent(STATE_CHANGED_ACTION).setPackage(context.packageName)
        )
    }

    private fun handleMarkDone(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID) ?: return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID)?.let {
            nm.cancel(it.hashCode())
        }

        val state = TaskStorage.load(context)
        val updated = state.tasks.map { t ->
            if (t.id == taskId) t.copy(done = true) else t
        }
        TaskStorage.save(context, AppState(updated))

        context.sendBroadcast(
            Intent(STATE_CHANGED_ACTION).setPackage(context.packageName)
        )
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                ReminderScheduler.rescheduleAll(context)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  8) Main Activity
// ═══════════════════════════════════════════════════════════════════

class MainActivity : ComponentActivity() {

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.ensureChannel(this)

        setContent {
            TaskManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppColors.Background
                ) {
                    val vm: TaskViewModel = viewModel()
                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(
                                this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!granted) {
                                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }
                    TaskApp(vm)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  9) ViewModel
// ═══════════════════════════════════════════════════════════════════

class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val _tasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val tasks: StateFlow<List<TaskItem>> = _tasks.asStateFlow()

    private val _toasts = MutableSharedFlow<ToastData>(extraBufferCapacity = 8)
    val toasts: SharedFlow<ToastData> = _toasts.asSharedFlow()

    private val _nowTick = MutableStateFlow(System.currentTimeMillis())
    val nowTick: StateFlow<Long> = _nowTick.asStateFlow()

    data class ToastData(val title: String, val body: String, val important: Boolean)

    private var receiverRegistered = false

    private val stateChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshFromDisk()
        }
    }

    init {
        _tasks.value = TaskStorage.load(app).tasks
        ReminderScheduler.rescheduleAll(app)
        registerReceiver()

        viewModelScope.launch {
            while (true) {
                _nowTick.value = System.currentTimeMillis()
                delay(30_000L)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (receiverRegistered) {
            try {
                getApplication<Application>().unregisterReceiver(stateChangedReceiver)
            } catch (_: Exception) {}
            receiverRegistered = false
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getApplication<Application>().registerReceiver(
                    stateChangedReceiver,
                    IntentFilter(STATE_CHANGED_ACTION),
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                getApplication<Application>().registerReceiver(
                    stateChangedReceiver,
                    IntentFilter(STATE_CHANGED_ACTION)
                )
            }
            receiverRegistered = true
        } catch (_: Exception) {}
    }

    private fun refreshFromDisk() {
        val loaded = TaskStorage.load(getApplication())
        _tasks.value = loaded.tasks
    }

    private fun persist() {
        TaskStorage.save(getApplication(), AppState(_tasks.value))
    }

    fun emitToast(title: String, body: String, important: Boolean = false) {
        _toasts.tryEmit(ToastData(title, body, important))
    }

    fun addTask(title: String, desc: String, priority: Priority) {
        val t = TaskItem(
            id = UUID.randomUUID().toString(),
            title = title,
            desc = desc,
            priority = priority,
        )
        _tasks.update { it + t }
        persist()
    }

    fun updateTask(id: String, title: String, desc: String, priority: Priority) {
        _tasks.update { list ->
            list.map { if (it.id == id) it.copy(title = title, desc = desc, priority = priority) else it }
        }
        persist()
    }

    fun deleteTask(id: String) {
        _tasks.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    fun toggleDone(id: String) {
        _tasks.update { list ->
            list.map { if (it.id == id) it.copy(done = !it.done) else it }
        }
        persist()
    }

    fun addReminder(taskId: String, label: String, timestamp: Long) {
        val ctx = getApplication<Application>()
        val newReminder = Reminder(
            id = UUID.randomUUID().toString(),
            label = label.ifBlank { "Reminder" },
            timestamp = timestamp,
        )
        var added: Pair<TaskItem, Reminder>? = null
        _tasks.update { list ->
            list.map { t ->
                if (t.id == taskId) {
                    val upd = t.copy(reminders = (t.reminders + newReminder).sortedBy { it.timestamp })
                    added = upd to newReminder
                    upd
                } else t
            }
        }
        added?.let { (task, rem) ->
            if (rem.timestamp > System.currentTimeMillis()) {
                ReminderScheduler.schedule(ctx, task, rem)
            }
        }
        persist()
    }

    fun deleteReminder(taskId: String, reminderId: String) {
        ReminderScheduler.cancel(getApplication(), reminderId)
        _tasks.update { list ->
            list.map { t ->
                if (t.id == taskId) t.copy(reminders = t.reminders.filterNot { it.id == reminderId })
                else t
            }
        }
        persist()
    }
}

// ═══════════════════════════════════════════════════════════════════
//  10) Theme & Colors
// ═══════════════════════════════════════════════════════════════════

object AppColors {
    val Background = Color(0xFF0B0B0F)
    val Surface = Color(0xFF15151C)
    val Surface2 = Color(0xFF1C1C25)
    val Line = Color(0x14FFFFFF)
    val Text = Color(0xFFF3F3F7)
    val Muted = Color(0xFF8B8B99)
    val Placeholder = Color(0xFF55555F)
    val Accent = Color(0xFF7C5CFF)
    val Accent2 = Color(0xFF4DABFF)
    val High = Color(0xFFFF4D6D)
    val Med = Color(0xFFFFB020)
    val Low = Color(0xFF3DDC97)
}

@Composable
fun TaskManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AppColors.Accent,
            secondary = AppColors.Accent2,
            background = AppColors.Background,
            surface = AppColors.Surface,
            onPrimary = Color.White,
            onBackground = AppColors.Text,
            onSurface = AppColors.Text,
        )
    ) {
        CompositionLocalProvider(
            LocalContentColor provides AppColors.Text
        ) { content() }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  11) Root composable
// ═══════════════════════════════════════════════════════════════════

private sealed interface SheetTarget {
    data object None : SheetTarget
    data class TaskEditor(val taskId: String?) : SheetTarget
    data class ReminderEditor(val taskId: String) : SheetTarget
    data class Actions(val taskId: String) : SheetTarget
}

private data class ToastDataWrapper(
    val id: String,
    val title: String,
    val body: String,
    val important: Boolean,
)

@Composable
fun TaskApp(vm: TaskViewModel) {
    val tasks by vm.tasks.collectAsState()
    val tick by vm.nowTick.collectAsState()

    var sheet by remember { mutableStateOf<SheetTarget>(SheetTarget.None) }

    val toasts = remember { mutableStateListOf<ToastDataWrapper>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.toasts.collect { data ->
            val id = UUID.randomUUID().toString()
            toasts.add(ToastDataWrapper(id, data.title, data.body, data.important))
            scope.launch {
                delay(if (data.important) 7000L else 3200L)
                toasts.removeAll { it.id == id }
            }
        }
    }

    val sorted = remember(tasks, tick) { tasks.sortedWith(taskComparator) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            TopBar(tasks)
            if (sorted.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 6.dp, bottom = 140.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(sorted, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            now = tick,
                            onToggle = { vm.toggleDone(task.id) },
                            onMenu = { sheet = SheetTarget.Actions(task.id) },
                            onAddReminder = { sheet = SheetTarget.ReminderEditor(task.id) },
                            onDeleteReminder = { rid -> vm.deleteReminder(task.id, rid) },
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 20.dp, bottom = 22.dp)
                .size(58.dp)
                .shadow(20.dp, RoundedCornerShape(20.dp), clip = false)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(listOf(AppColors.Accent, AppColors.Accent2))
                )
                .clickable { sheet = SheetTarget.TaskEditor(null) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "New task",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            toasts.forEach { t ->
                ToastView(
                    title = t.title,
                    body = t.body,
                )
            }
        }
    }

    when (val s = sheet) {
        SheetTarget.None -> {}
        is SheetTarget.TaskEditor -> {
            val existing = s.taskId?.let { id -> tasks.find { it.id == id } }
            TaskEditorSheet(
                existing = existing,
                onDismiss = { sheet = SheetTarget.None },
                onSave = { title, desc, priority ->
                    if (existing == null) vm.addTask(title, desc, priority)
                    else vm.updateTask(existing.id, title, desc, priority)
                    sheet = SheetTarget.None
                },
            )
        }
        is SheetTarget.ReminderEditor -> {
            val task = tasks.find { it.id == s.taskId }
            if (task != null) {
                ReminderEditorSheet(
                    task = task,
                    onDismiss = { sheet = SheetTarget.None },
                    onSave = { label, timestamp ->
                        if (timestamp <= System.currentTimeMillis() - 60_000L) {
                            vm.emitToast("Invalid time", "Reminder time is in the past.")
                        } else {
                            vm.addReminder(task.id, label, timestamp)
                        }
                        sheet = SheetTarget.None
                    },
                )
            } else sheet = SheetTarget.None
        }
        is SheetTarget.Actions -> {
            val task = tasks.find { it.id == s.taskId }
            if (task != null) {
                ActionsSheet(
                    task = task,
                    onDismiss = { sheet = SheetTarget.None },
                    onAddReminder = { sheet = SheetTarget.ReminderEditor(task.id) },
                    onEdit = { sheet = SheetTarget.TaskEditor(task.id) },
                    onDelete = {
                        vm.deleteTask(task.id)
                        sheet = SheetTarget.None
                    },
                )
            } else sheet = SheetTarget.None
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  12) Sorting
// ═══════════════════════════════════════════════════════════════════

private val taskComparator = Comparator<TaskItem> { a, b ->
    if (a.done != b.done) return@Comparator if (a.done) 1 else -1
    if (a.priority != b.priority) {
        return@Comparator priorityWeight(a.priority) - priorityWeight(b.priority)
    }
    val now = System.currentTimeMillis()
    val aNext = a.reminders.filter { it.timestamp > now }.minOfOrNull { it.timestamp }
    val bNext = b.reminders.filter { it.timestamp > now }.minOfOrNull { it.timestamp }
    when {
        aNext != null && bNext != null -> aNext.compareTo(bNext)
        aNext != null -> -1
        bNext != null -> 1
        else -> b.createdAt.compareTo(a.createdAt)
    }
}

private fun priorityWeight(p: Priority): Int = when (p) {
    Priority.HIGH -> 0
    Priority.MEDIUM -> 1
    Priority.LOW -> 2
}

private fun priorityLabel(p: Priority): String = when (p) {
    Priority.HIGH -> "High Priority"
    Priority.MEDIUM -> "Medium Priority"
    Priority.LOW -> "Low Priority"
}

private fun priorityColor(p: Priority): Color = when (p) {
    Priority.HIGH -> AppColors.High
    Priority.MEDIUM -> AppColors.Med
    Priority.LOW -> AppColors.Low
}

// ═══════════════════════════════════════════════════════════════════
//  13) Top Bar
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun TopBar(tasks: List<TaskItem>) {
    val context = LocalContext.current

    val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "My Tasks",
                color = AppColors.Text,
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold
            )
            val subtitle = when {
                tasks.isEmpty() -> "No tasks added yet"
                tasks.none { !it.done } -> "All tasks done"
                else -> {
                    val n = tasks.count { !it.done }
                    if (n == 1) "1 task pending" else "$n tasks pending"
                }
            }
            Text(
                subtitle,
                color = AppColors.Muted,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, AppColors.Line, RoundedCornerShape(14.dp))
                .clickable {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            val activity = context as? ComponentActivity
                            if (activity != null) {
                                ActivityCompat.requestPermissions(
                                    activity,
                                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                    1001
                                )
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (notifGranted) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                contentDescription = "Notifications",
                tint = if (notifGranted) AppColors.Text else AppColors.Muted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  14) Empty state
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 30.dp)
            .padding(top = 90.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            AppColors.Accent.copy(alpha = 0.2f),
                            AppColors.Accent2.copy(alpha = 0.12f),
                        )
                    )
                )
                .border(1.dp, AppColors.Accent.copy(alpha = 0.25f), RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Notifications,
                contentDescription = null,
                tint = Color(0xFFA99BFF),
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("No tasks yet", color = AppColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap the + button to create your first task,\nset a priority and add a reminder.",
            color = AppColors.Muted,
            fontSize = 13.5.sp,
            textAlign = TextAlign.Center,
            lineHeight = 26.sp,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
//  15) Task Card
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun TaskCard(
    task: TaskItem,
    now: Long,
    onToggle: () -> Unit,
    onMenu: () -> Unit,
    onAddReminder: () -> Unit,
    onDeleteReminder: (String) -> Unit,
) {
    val pc = priorityColor(task.priority)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.05f),
                        Color.White.copy(alpha = 0.02f),
                    )
                )
            )
            .border(1.dp, AppColors.Line, RoundedCornerShape(20.dp))
            .alpha(if (task.done) 0.5f else 1f)
            .padding(14.dp)
    ) {
        Row {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(pc.copy(alpha = 0.9f))
            )
            Spacer(Modifier.width(11.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (task.done) Brush.linearGradient(
                                    listOf(AppColors.Accent, AppColors.Accent2)
                                ) else Brush.linearGradient(
                                    listOf(Color.Transparent, Color.Transparent)
                                )
                            )
                            .border(
                                2.dp,
                                if (task.done) Color.Transparent else Color.White.copy(alpha = 0.22f),
                                CircleShape
                            )
                            .clickable { onToggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (task.done) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Done",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            task.title,
                            color = AppColors.Text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 23.sp,
                            textDecoration = if (task.done) TextDecoration.LineThrough else null,
                        )
                        if (task.desc.isNotBlank()) {
                            Spacer(Modifier.height(5.dp))
                            Text(
                                task.desc,
                                color = Color(0xFFA3A3B2),
                                fontSize = 13.sp,
                                lineHeight = 22.sp,
                            )
                        }
                        Spacer(Modifier.height(9.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(pc.copy(alpha = 0.16f))
                                    .padding(horizontal = 9.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    priorityLabel(task.priority),
                                    color = when (task.priority) {
                                        Priority.HIGH -> Color(0xFFFF92A6)
                                        Priority.MEDIUM -> Color(0xFFFFC861)
                                        Priority.LOW -> Color(0xFF71E9B3)
                                    },
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                Fmt.relTime(task.createdAt),
                                color = AppColors.Muted,
                                fontSize = 11.sp,
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onMenu() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Options",
                            tint = AppColors.Muted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (task.reminders.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        task.reminders.sortedBy { it.timestamp }.forEach { r ->
                            ReminderRow(
                                reminder = r,
                                now = now,
                                onDelete = { onDeleteReminder(r.id) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.14f),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onAddReminder() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = AppColors.Muted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Add Reminder",
                            color = AppColors.Muted,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  16) Reminder Row
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ReminderRow(
    reminder: Reminder,
    now: Long,
    onDelete: () -> Unit,
) {
    val whenInfo = remember(reminder.timestamp) { Fmt.reminderWhen(reminder.timestamp) }
    val cd = remember(reminder.timestamp, now) { Fmt.countdown(reminder.timestamp) }
    val isPast = reminder.timestamp <= now

    val borderColor: Color = when {
        isPast -> Color.White.copy(alpha = 0.055f)
        cd.cls == "now" -> AppColors.Accent.copy(alpha = 0.5f)
        cd.cls == "soon" -> AppColors.Med.copy(alpha = 0.35f)
        else -> Color.White.copy(alpha = 0.055f)
    }
    val bgColor: Color = when {
        isPast -> Color.White.copy(alpha = 0.04f)
        cd.cls == "now" -> AppColors.Accent.copy(alpha = 0.12f)
        cd.cls == "soon" -> AppColors.Med.copy(alpha = 0.07f)
        else -> Color.White.copy(alpha = 0.04f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .alpha(if (isPast) 0.5f else 1f)
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    when {
                        isPast -> SolidColor(Color.White.copy(alpha = 0.06f))
                        cd.cls == "now" -> SolidColor(AppColors.Accent.copy(alpha = 0.5f))
                        cd.cls == "soon" -> SolidColor(AppColors.Med.copy(alpha = 0.18f))
                        else -> SolidColor(AppColors.Accent.copy(alpha = 0.16f))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = null,
                tint = when {
                    isPast -> Color(0xFF7A7A8A)
                    cd.cls == "now" -> Color.White
                    cd.cls == "soon" -> Color(0xFFFFC861)
                    else -> Color(0xFFB7A6FF)
                },
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                reminder.label.ifBlank { "Reminder" },
                color = AppColors.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    whenInfo.main,
                    color = Color(0xFFD4D4E0),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (whenInfo.isRel) {
                    Text(
                        whenInfo.sub,
                        color = Color(0xFF7D7D90),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            when (cd.cls) {
                                "now" -> AppColors.Accent.copy(alpha = 0.2f)
                                "soon" -> AppColors.Med.copy(alpha = 0.16f)
                                "past" -> Color.White.copy(alpha = 0.06f)
                                else -> Color.White.copy(alpha = 0.07f)
                            }
                        )
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        cd.txt,
                        color = when (cd.cls) {
                            "now" -> Color(0xFFC4B5FD)
                            "soon" -> Color(0xFFFFC861)
                            "past" -> Color(0xFF7A7A8A)
                            else -> Color(0xFFB0B0C0)
                        },
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(9.dp))
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Delete reminder",
                tint = Color(0xFF5F5F70),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  17) Sheets — Task Editor
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditorSheet(
    existing: TaskItem?,
    onDismiss: () -> Unit,
    onSave: (String, String, Priority) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var desc by remember { mutableStateOf(existing?.desc ?: "") }
    var priority by remember { mutableStateOf(existing?.priority ?: Priority.MEDIUM) }
    var titleError by remember { mutableStateOf(false) }

    val shakeOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun triggerShake() {
        scope.launch {
            shakeOffset.snapTo(0f)
            val frames = listOf(-8f, 8f, -6f, 6f, -3f, 3f, 0f)
            for (f in frames) {
                shakeOffset.animateTo(f, tween(40))
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.Surface,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        dragHandle = { SheetHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 22.dp)
                .imePadding()
        ) {
            Text(
                if (existing == null) "New Task" else "Edit Task",
                color = AppColors.Text,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Set a title and priority",
                color = AppColors.Muted,
                fontSize = 12.5.sp,
                modifier = Modifier.padding(bottom = 18.dp)
            )

            FieldLabel("Title")
            StyledTextField(
                value = title,
                onValueChange = {
                    title = it.take(90)
                    titleError = false
                },
                placeholder = "e.g. Call the client",
                singleLine = true,
                errorBorder = titleError,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = shakeOffset.value }
            )

            Spacer(Modifier.height(16.dp))
            FieldLabel("Description (optional)")
            StyledTextField(
                value = desc,
                onValueChange = { desc = it.take(400) },
                placeholder = "More details...",
                singleLine = false,
                minHeight = 84.dp,
            )

            Spacer(Modifier.height(16.dp))
            FieldLabel("Priority")
            PrioritySegment(priority) { priority = it }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetButton(
                    text = "Cancel",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
                SheetButton(
                    text = "Save",
                    primary = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val t = title.trim()
                        if (t.isEmpty()) {
                            titleError = true
                            triggerShake()
                        } else {
                            onSave(t, desc.trim(), priority)
                        }
                    },
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  18) Sheets — Actions
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionsSheet(
    task: TaskItem,
    onDismiss: () -> Unit,
    onAddReminder: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.Surface,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        dragHandle = { SheetHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 22.dp)
        ) {
            Text(
                task.title,
                color = AppColors.Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            ActionRow(
                icon = { Icon(Icons.Filled.Notifications, null, tint = AppColors.Muted, modifier = Modifier.size(18.dp)) },
                label = "Add Reminder",
                onClick = onAddReminder,
            )
            ActionRow(
                icon = { Icon(Icons.Filled.Edit, null, tint = AppColors.Muted, modifier = Modifier.size(18.dp)) },
                label = "Edit Task",
                onClick = onEdit,
            )
            ActionRow(
                icon = { Icon(Icons.Filled.Delete, null, tint = Color(0xFFFF8FA3), modifier = Modifier.size(18.dp)) },
                label = "Delete Task",
                danger = true,
                onClick = onDelete,
            )
            Spacer(Modifier.height(12.dp))
            SheetButton(
                text = "Close",
                primary = false,
                modifier = Modifier.fillMaxWidth(),
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: @Composable () -> Unit,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = if (danger) Color(0xFFFF8FA3) else AppColors.Text,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
//  19) Sheets — Reminder Editor
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderEditorSheet(
    task: TaskItem,
    onDismiss: () -> Unit,
    onSave: (label: String, timestamp: Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val initialMillis = remember {
        val c = Calendar.getInstance()
        c.add(Calendar.HOUR_OF_DAY, 1)
        c.set(Calendar.MINUTE, ((c.get(Calendar.MINUTE) / 5) * 5 + 5) % 60)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.timeInMillis
    }

    var label by remember { mutableStateOf("") }
    val initialJ = remember { JalaliCalendar.fromMillis(initialMillis) }
    var jy by remember { mutableIntStateOf(initialJ.jy) }
    var jm by remember { mutableIntStateOf(initialJ.jm) }
    var jd by remember { mutableIntStateOf(initialJ.jd) }
    var hh by remember {
        mutableIntStateOf(
            Calendar.getInstance().apply { timeInMillis = initialMillis }
                .get(Calendar.HOUR_OF_DAY)
        )
    }
    var mm by remember {
        mutableIntStateOf(
            Calendar.getInstance().apply { timeInMillis = initialMillis }
                .get(Calendar.MINUTE)
        )
    }

    fun updateFromMillis(millis: Long) {
        val j = JalaliCalendar.fromMillis(millis)
        jy = j.jy; jm = j.jm; jd = j.jd
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        hh = cal.get(Calendar.HOUR_OF_DAY)
        mm = cal.get(Calendar.MINUTE)
    }

    val selectedTs = remember(jy, jm, jd, hh, mm) {
        JalaliCalendar.toMillis(jy, jm, jd, hh, mm)
    }

    val preview = remember(selectedTs) {
        val w = Fmt.reminderWhen(selectedTs)
        "${w.main}  ·  ${w.sub}"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.Surface,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        dragHandle = { SheetHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 22.dp)
                .imePadding()
        ) {
            Text(
                "New Reminder",
                color = AppColors.Text,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                task.title,
                color = AppColors.Muted,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 18.dp)
            )

            FieldLabel("Label (optional)")
            StyledTextField(
                value = label,
                onValueChange = { label = it.take(40) },
                placeholder = "e.g. Follow-up call",
                singleLine = true,
            )

            Spacer(Modifier.height(16.dp))
            FieldLabel("Quick Select")
            QuickChips(
                onPick = { offsetDays ->
                    val c = Calendar.getInstance()
                    c.add(Calendar.DAY_OF_MONTH, offsetDays)
                    c.set(Calendar.HOUR_OF_DAY, hh)
                    c.set(Calendar.MINUTE, mm)
                    c.set(Calendar.SECOND, 0)
                    c.set(Calendar.MILLISECOND, 0)
                    updateFromMillis(c.timeInMillis)
                }
            )

            Spacer(Modifier.height(4.dp))
            JalaliCalendarGrid(
                jy = jy, jm = jm, jd = jd,
                onSelect = { d -> jd = d },
                onPrevMonth = {
                    jm -= 1
                    if (jm < 1) { jm = 12; jy -= 1 }
                    val maxD = JalaliCalendar.monthLength(jy, jm)
                    if (jd > maxD) jd = maxD
                },
                onNextMonth = {
                    jm += 1
                    if (jm > 12) { jm = 1; jy += 1 }
                    val maxD = JalaliCalendar.monthLength(jy, jm)
                    if (jd > maxD) jd = maxD
                },
            )

            Spacer(Modifier.height(14.dp))
            FieldLabel("Reminder time")

            TimeWheelPicker(
                hour = hh,
                minute = mm,
                onHourChange = { hh = it },
                onMinuteChange = { mm = it },
            )

            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                AppColors.Accent.copy(alpha = 0.15f),
                                AppColors.Accent2.copy(alpha = 0.08f),
                            )
                        )
                    )
                    .border(
                        1.dp,
                        AppColors.Accent.copy(alpha = 0.22f),
                        RoundedCornerShape(15.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = Color(0xFFB7A6FF),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Selected:  ",
                        color = Color(0xFFD8D0FF),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        preview,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetButton(
                    text = "Cancel",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
                SheetButton(
                    text = "Save Reminder",
                    primary = true,
                    modifier = Modifier.weight(1f),
                    onClick = { onSave(label.trim(), selectedTs) },
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  20) Jalali Calendar Grid
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun JalaliCalendarGrid(
    jy: Int, jm: Int, jd: Int,
    onSelect: (Int) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val todayJ = remember { JalaliCalendar.fromMillis(System.currentTimeMillis()) }
    val monthLen = remember(jy, jm) { JalaliCalendar.monthLength(jy, jm) }
    val firstCol = remember(jy, jm) {
        val millis = JalaliCalendar.toMillis(jy, jm, 1)
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> 0
            Calendar.SUNDAY -> 1
            Calendar.MONDAY -> 2
            Calendar.TUESDAY -> 3
            Calendar.WEDNESDAY -> 4
            Calendar.THURSDAY -> 5
            Calendar.FRIDAY -> 6
            else -> 0
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable { onPrevMonth() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.ChevronLeft,
                    contentDescription = "Previous month",
                    tint = AppColors.Text,
                    modifier = Modifier.size(15.dp)
                )
            }

            Text(
                "${Fmt.JMONTHS[jm - 1]} $jy",
                color = AppColors.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable { onNextMonth() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "Next month",
                    tint = AppColors.Text,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Fmt.DOW_SHORT.forEach { d ->
                Text(
                    d,
                    color = AppColors.Muted,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp)
                )
            }
        }

        val rows = (firstCol + monthLen + 6) / 7
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            for (r in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    for (c in 0 until 7) {
                        val cellIdx = r * 7 + c
                        val day = cellIdx - firstCol + 1
                        if (day < 1 || day > monthLen) {
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val isToday = todayJ.jy == jy && todayJ.jm == jm && todayJ.jd == day
                            val isSel = day == jd
                            DayCell(
                                day = day,
                                selected = isSel,
                                today = isToday,
                                onClick = { onSelect(day) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    selected: Boolean,
    today: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) {
        Brush.linearGradient(listOf(AppColors.Accent, AppColors.Accent2))
    } else {
        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(11.dp))
            .background(bg)
            .then(
                if (today && !selected) {
                    Modifier.border(
                        1.5.dp,
                        AppColors.Accent2.copy(alpha = 0.5f),
                        RoundedCornerShape(11.dp)
                    )
                } else Modifier
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            day.toString(),
            color = when {
                selected -> Color.White
                today -> AppColors.Accent2
                else -> AppColors.Text
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
//  21) Time Wheel Picker
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun TimeWheelPicker(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .height(46.dp)
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            AppColors.Accent.copy(alpha = 0.16f),
                            AppColors.Accent2.copy(alpha = 0.10f),
                        )
                    )
                )
                .border(
                    1.dp,
                    AppColors.Accent.copy(alpha = 0.30f),
                    RoundedCornerShape(12.dp)
                )
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            WheelColumn(
                count = 24,
                value = hour,
                onValueChange = onHourChange,
                modifier = Modifier.width(86.dp)
            )
            Text(
                ":",
                color = Color(0xFF3D3D4A),
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            WheelColumn(
                count = 60,
                value = minute,
                onValueChange = onMinuteChange,
                modifier = Modifier.width(86.dp)
            )
        }
    }
}

@Composable
private fun WheelColumn(
    count: Int,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemHeight = 46.dp
    val visibleCount = 5
    val safeValue = value.coerceIn(0, count - 1)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = safeValue
    )
    val scope = rememberCoroutineScope()

    val centeredIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) {
                safeValue
            } else {
                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                info.visibleItemsInfo.minByOrNull {
                    abs((it.offset + it.size / 2f) - center)
                }?.index ?: safeValue
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collect {
                if (centeredIndex != value) onValueChange(centeredIndex)
            }
    }

    LaunchedEffect(value) {
        if (listState.layoutInfo.visibleItemsInfo.isEmpty()) return@LaunchedEffect
        if (!listState.isScrollInProgress) {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            val current = info.visibleItemsInfo.minByOrNull {
                abs((it.offset + it.size / 2f) - center)
            }?.index
            if (current != null && current != value) {
                listState.animateScrollToItem(value)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.height(itemHeight * visibleCount),
        contentPadding = PaddingValues(vertical = itemHeight * (visibleCount / 2)),
        flingBehavior = rememberSnapFlingBehavior(listState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(count) { index ->
            val distance = abs(index - centeredIndex)
            val alpha = when (distance) {
                0 -> 1f
                1 -> 0.55f
                2 -> 0.28f
                else -> 0.12f
            }
            val scale = if (distance == 0) 1.08f else 1f

            Box(
                modifier = Modifier
                    .height(itemHeight)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        scope.launch { listState.animateScrollToItem(index) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = index.toString().padStart(2, '0'),
                    color = Color.White.copy(alpha = alpha),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  22) Small UI pieces
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.18f))
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        color = AppColors.Muted,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun StyledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    errorBorder: Boolean = false,
    minHeight: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = when {
        errorBorder -> AppColors.High
        focused -> AppColors.Accent.copy(alpha = 0.65f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    val bg = when {
        focused -> AppColors.Accent.copy(alpha = 0.07f)
        else -> Color.White.copy(alpha = 0.05f)
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        interactionSource = interaction,
        cursorBrush = SolidColor(AppColors.Accent),
        textStyle = TextStyle(
            color = AppColors.Text,
            fontSize = 15.sp,
        ),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (minHeight > 0.dp) Modifier.heightIn(min = minHeight) else Modifier)
                    .clip(RoundedCornerShape(15.dp))
                    .background(bg)
                    .border(1.dp, borderColor, RoundedCornerShape(15.dp))
                    .padding(horizontal = 15.dp, vertical = 13.dp),
                contentAlignment = if (minHeight > 0.dp) Alignment.TopStart else Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        color = AppColors.Placeholder,
                        fontSize = 15.sp,
                    )
                }
                inner()
            }
        }
    )
}

@Composable
private fun PrioritySegment(
    current: Priority,
    onChange: (Priority) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PriorityItem("High", current == Priority.HIGH, Priority.HIGH) { onChange(Priority.HIGH) }
        PriorityItem("Medium", current == Priority.MEDIUM, Priority.MEDIUM) { onChange(Priority.MEDIUM) }
        PriorityItem("Low", current == Priority.LOW, Priority.LOW) { onChange(Priority.LOW) }
    }
}

@Composable
private fun RowScope.PriorityItem(
    label: String,
    active: Boolean,
    p: Priority,
    onClick: () -> Unit,
) {
    val bg = when {
        active && p == Priority.HIGH -> AppColors.High.copy(alpha = 0.18f)
        active && p == Priority.MEDIUM -> AppColors.Med.copy(alpha = 0.18f)
        active && p == Priority.LOW -> AppColors.Low.copy(alpha = 0.18f)
        active -> Color.White.copy(alpha = 0.1f)
        else -> Color.Transparent
    }
    val fg = when {
        active && p == Priority.HIGH -> Color(0xFFFF92A6)
        active && p == Priority.MEDIUM -> Color(0xFFFFC861)
        active && p == Priority.LOW -> Color(0xFF71E9B3)
        active -> AppColors.Text
        else -> AppColors.Muted
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = fg,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun QuickChips(onPick: (Int) -> Unit) {
    var selected by remember { mutableStateOf<String?>(null) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ChipItem("Today", selected == "today") {
            selected = "today"
            onPick(0)
        }
        ChipItem("Tomorrow", selected == "tomorrow") {
            selected = "tomorrow"
            onPick(1)
        }
        ChipItem("Day After", selected == "dayafter") {
            selected = "dayafter"
            onPick(2)
        }
        ChipItem("Next Week", selected == "week") {
            selected = "week"
            onPick(7)
        }
    }
}

@Composable
private fun RowScope.ChipItem(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (active) AppColors.Accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f)
    val border = if (active) AppColors.Accent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.07f)
    val fg = if (active) Color(0xFFC4B5FD) else Color(0xFFC3C3D0)

    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 9.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun SheetButton(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (primary)
                    Modifier.background(
                        Brush.linearGradient(
                            listOf(AppColors.Accent, AppColors.Accent2)
                        )
                    )
                else Modifier.background(Color.White.copy(alpha = 0.06f))
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (primary) Color.White else AppColors.Muted,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
//  23) Toast
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ToastView(
    title: String,
    body: String,
) {
    val alpha = remember { Animatable(0f) }
    val translateY = remember { Animatable(-16f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(300))
        translateY.animateTo(0f, tween(300))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha.value
                translationY = translateY.value
            }
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF21C1C26))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    Brush.linearGradient(
                        listOf(AppColors.Accent, AppColors.Accent2)
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = AppColors.Text,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                body,
                color = Color(0xFFA3A3B2),
                fontSize = 12.5.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Utilities for composition
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun useNowTicker(intervalMs: Long = 30_000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(intervalMs)
        }
    }
    return now
}

// ═══════════════════════════════════════════════════════════════════
//  Previews
// ═══════════════════════════════════════════════════════════════════

@androidx.compose.ui.tooling.preview.Preview(
    name = "Empty State",
    showBackground = true,
    backgroundColor = 0xFF0B0B0F,
    showSystemUi = false,
)
@Composable
private fun PreviewEmptyState() {
    TaskManagerTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AppColors.Background,
        ) {
            EmptyState()
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Task Cards",
    showBackground = true,
    backgroundColor = 0xFF0B0B0F,
    showSystemUi = false,
)
@Composable
private fun PreviewTaskCards() {
    val now = System.currentTimeMillis()
    val tasks = listOf(
        TaskItem(
            id = "s1",
            title = "Call the client",
            desc = "Discuss the project timeline and deliverables",
            priority = Priority.HIGH,
            reminders = listOf(
                Reminder(
                    id = "r1",
                    label = "Follow-up call",
                    timestamp = now + 2 * 60 * 60 * 1000L,
                )
            ),
        ),
        TaskItem(
            id = "s2",
            title = "Review pull request",
            desc = "Check the authentication flow changes",
            priority = Priority.MEDIUM,
        ),
        TaskItem(
            id = "s3",
            title = "Water the plants",
            priority = Priority.LOW,
            done = true,
        ),
    )
    TaskManagerTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AppColors.Background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                tasks.forEach { t ->
                    TaskCard(
                        task = t,
                        now = now,
                        onToggle = {},
                        onMenu = {},
                        onAddReminder = {},
                        onDeleteReminder = { _ -> },
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Top Bar",
    showBackground = true,
    backgroundColor = 0xFF0B0B0F,
    showSystemUi = false,
)
@Composable
private fun PreviewTopBar() {
    val sample = listOf(
        TaskItem(id = "1", title = "Task A", done = false),
        TaskItem(id = "2", title = "Task B", done = true),
        TaskItem(id = "3", title = "Task C", done = false),
    )
    TaskManagerTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AppColors.Background,
        ) {
            TopBar(sample)
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Jalali Calendar",
    showBackground = true,
    backgroundColor = 0xFF0B0B0F,
    showSystemUi = false,
)
@Composable
private fun PreviewJalaliCalendar() {
    val today = JalaliCalendar.fromMillis(System.currentTimeMillis())
    TaskManagerTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AppColors.Background,
        ) {
            Box(modifier = Modifier.padding(20.dp)) {
                JalaliCalendarGrid(
                    jy = today.jy,
                    jm = today.jm,
                    jd = today.jd,
                    onSelect = { _ -> },
                    onPrevMonth = {},
                    onNextMonth = {},
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Time Wheel",
    showBackground = true,
    backgroundColor = 0xFF0B0B0F,
    showSystemUi = false,
)
@Composable
private fun PreviewTimeWheel() {
    TaskManagerTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AppColors.Background,
        ) {
            Box(modifier = Modifier.padding(20.dp)) {
                TimeWheelPicker(
                    hour = 14,
                    minute = 30,
                    onHourChange = { _ -> },
                    onMinuteChange = { _ -> },
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Sheet Buttons",
    showBackground = true,
    backgroundColor = 0xFF0B0B0F,
    showSystemUi = false,
)
@Composable
private fun PreviewSheetButtons() {
    TaskManagerTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AppColors.Background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SheetButton(
                        text = "Cancel",
                        primary = false,
                        modifier = Modifier.weight(1f),
                        onClick = {},
                    )
                    SheetButton(
                        text = "Save",
                        primary = true,
                        modifier = Modifier.weight(1f),
                        onClick = {},
                    )
                }
                PrioritySegment(Priority.MEDIUM) { _ -> }
                QuickChips { _ -> }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Toasts",
    showBackground = true,
    backgroundColor = 0xFF0B0B0F,
    showSystemUi = false,
)
@Composable
private fun PreviewToasts() {
    TaskManagerTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AppColors.Background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToastView(
                    title = "Follow-up call",
                    body = "Task \"Call the client\" is due now",
                )
                ToastView(
                    title = "Meeting",
                    body = "Task \"Team sync\" starts in 10 minutes",
                )
            }
        }
    }
}