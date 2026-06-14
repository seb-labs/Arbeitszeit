
package de.seb.arbeitszeitapp.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.seb.arbeitszeitapp.data.DaySummary
import de.seb.arbeitszeitapp.data.GeofenceConfig
import de.seb.arbeitszeitapp.data.GeofenceRegistrationState
import de.seb.arbeitszeitapp.data.PermissionSnapshot
import de.seb.arbeitszeitapp.data.SessionSource
import de.seb.arbeitszeitapp.data.WorkSessionEntity
import de.seb.arbeitszeitapp.data.WorkSessionSlice
import de.seb.arbeitszeitapp.domain.TimeCalculator
import de.seb.arbeitszeitapp.notifications.WorkTimeAlertScheduler
import de.seb.arbeitszeitapp.ui.theme.ArbeitszeitTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val DayFormatter = DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy", Locale.GERMANY)
private val MonthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMANY)
private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private const val SHORT_SESSION_WARNING_MINUTES = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkTimeApp(viewModel: WorkTimeViewModel = viewModel()) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val geofenceState by viewModel.geofenceState.collectAsStateWithLifecycle()
    val geofenceConfig by viewModel.geofenceConfig.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val zoneId = remember { ZoneId.systemDefault() }

    val notificationPermissionGranted = remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionGranted.value = granted
    }

    LaunchedEffect(Unit) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var monthSelection by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var uiRefreshTick by remember { mutableIntStateOf(0) }
    var manualDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var manualStart by rememberSaveable { mutableStateOf("08:00") }
    var manualEnd by rememberSaveable { mutableStateOf("16:30") }
    var manualNote by rememberSaveable { mutableStateOf("") }
    var editingSession by remember { mutableStateOf<WorkSessionEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<WorkSessionEntity?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        uiRefreshTick++
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) uiRefreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissions = remember(uiRefreshTick, context) { readPermissionSnapshot(context) }

    LaunchedEffect(permissions, geofenceConfig) {
        if (permissions.fineLocationGranted && permissions.locationServicesEnabled && permissions.playServicesAvailable && geofenceConfig != null) {
            viewModel.refreshGeofence()
        } else {
            viewModel.resetMessage()
        }
    }

    LaunchedEffect(sessions) {
        viewModel.syncWorkTimeAlerts()
        WorkTimeAlertScheduler.ensureChannel(context)
    }

    ArbeitszeitTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            androidx.compose.material3.Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Arbeitszeit") },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                },
                bottomBar = {
                    Card(Modifier.fillMaxWidth()) {
                        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
                            tab(selectedTab == 0, "Heute", { selectedTab = 0 })
                            tab(selectedTab == 1, "Manuelle Korrektur", { selectedTab = 1 })
                            tab(selectedTab == 2, "Woche", { selectedTab = 2 })
                            tab(selectedTab == 3, "Monat", { selectedTab = 3 })
                            tab(selectedTab == 4, "Arbeitsort", { selectedTab = 4 })
                        }
                    }
                },
            ) { padding ->
                when (selectedTab) {
                    0 -> TodayTab(
                        modifier = Modifier.padding(padding),
                        sessions = sessions,
                        permissions = permissions,
                        geofenceState = geofenceState,
                        geofenceConfig = geofenceConfig,
                        onRequestFineLocation = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                        onOpenAppSettings = { context.openAppSettings() },
                        onOpenLocationSettings = { context.openLocationSettings() },
                        onOpenWorkLocationTab = { selectedTab = 4 },
                        zoneId = zoneId,
                    )

                    1 -> CorrectionTab(
                        modifier = Modifier.padding(padding),
                        sessions = sessions,
                        onAddManual = { date, start, end, note ->
                            viewModel.addManualSession(date, start, end, note)
                            uiRefreshTick++
                        },
                        onEditSession = { editingSession = it },
                        onDeleteSession = { deleteCandidate = it },
                        manualDate = manualDate,
                        manualStart = manualStart,
                        manualEnd = manualEnd,
                        manualNote = manualNote,
                        onManualDateChange = { manualDate = it },
                        onManualStartChange = { manualStart = it },
                        onManualEndChange = { manualEnd = it },
                        onManualNoteChange = { manualNote = it },
                        zoneId = zoneId,
                    )

                    2 -> WeekTab(
                        modifier = Modifier.padding(padding),
                        sessions = sessions,
                        zoneId = zoneId,
                        weekAnchor = LocalDate.now(),
                    )

                    3 -> MonthTab(
                        modifier = Modifier.padding(padding),
                        sessions = sessions,
                        zoneId = zoneId,
                        month = monthSelection,
                        onPreviousMonth = { monthSelection = monthSelection.minusMonths(1) },
                        onNextMonth = { monthSelection = monthSelection.plusMonths(1) },
                    )

                    4 -> WorkLocationTab(
                        modifier = Modifier.padding(padding),
                        currentConfig = geofenceConfig,
                        geofenceState = geofenceState,
                        onSaveConfig = { latitude, longitude ->
                            viewModel.saveGeofenceConfig(latitude, longitude)
                            uiRefreshTick++
                        },
                        onClearConfig = {
                            viewModel.clearGeofenceConfig()
                            uiRefreshTick++
                        },
                        onRefreshGeofence = { viewModel.refreshGeofence() },
                    )
            }
        }
    }
}

    editingSession?.let { session ->
        SessionEditorDialog(
            session = session,
            onDismiss = { editingSession = null },
            onSave = { updatedStart, updatedEnd, updatedNote ->
                viewModel.updateSession(session.id, updatedStart, updatedEnd, updatedNote, session.source)
                editingSession = null
                uiRefreshTick++
            },
        )
    }

    deleteCandidate?.let { session ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Session löschen?") },
            text = { Text("${formatSessionLabel(session)} wird endgültig entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(session)
                    deleteCandidate = null
                    uiRefreshTick++
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Abbrechen") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun tab(selected: Boolean, label: String, onClick: () -> Unit) {
    Tab(selected = selected, onClick = onClick, text = { Text(label) })
}

@Composable
private fun TodayTab(
    modifier: Modifier,
    sessions: List<WorkSessionEntity>,
    permissions: PermissionSnapshot,
    geofenceState: GeofenceRegistrationState?,
    geofenceConfig: GeofenceConfig?,
    onRequestFineLocation: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenWorkLocationTab: () -> Unit,
    zoneId: ZoneId,
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val summary = TimeCalculator.todaySummary(sessions, today, zoneId = zoneId)
    val openSession = TimeCalculator.currentOpenAutoSession(sessions)
    val openDuration = openSession?.let { TimeCalculator.openSessionDurationMinutes(it) }
    val alertDebugState = remember(sessions, zoneId) { WorkTimeAlertScheduler.debugState(context, sessions, zoneId) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusCard(
            permissions = permissions,
            geofenceState = geofenceState,
            geofenceConfig = geofenceConfig,
            onRequestFineLocation = onRequestFineLocation,
            onOpenAppSettings = onOpenAppSettings,
            onOpenLocationSettings = onOpenLocationSettings,
            onOpenWorkLocationTab = onOpenWorkLocationTab,
        )

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Heute", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(if (openSession != null) "Am Arbeitsort erkannt" else "Nicht am Arbeitsort erkannt", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(DayFormatter.format(today), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (openDuration != null) {
                    Text("Laufende Session seit ${formatMinutes(openDuration)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Aktuell keine offene automatische Session.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        metricCard("Heutige Bruttozeit", formatMinutes(summary.totalMinutes), "Automatisch aus Sessions berechnet", workDurationAccent(summary.totalMinutes))
        metricCard("Heutige Nettozeit", formatMinutes(summary.netMinutes), "Automatisch aus Sessions berechnet", workDurationAccent(summary.netMinutes))
        metricCard("Anzahl Sessions", summary.sessions.size.toString(), "AUTO/MANUAL gemischt", MaterialTheme.colorScheme.secondary)

        DebugAlertCard(alertDebugState)

        if (summary.sessions.isEmpty()) {
            EmptyHint("Heute wurden noch keine Sessions erfasst.")
        } else {
            Text("Heutige Sessions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            summary.sessions.forEach { slice ->
                SessionSliceCard(slice, sessionWarning = shortSessionWarning(slice), zoneId = zoneId)
            }
        }

    }
}

@Composable
private fun StatusCard(
    permissions: PermissionSnapshot,
    geofenceState: GeofenceRegistrationState?,
    geofenceConfig: GeofenceConfig?,
    onRequestFineLocation: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenWorkLocationTab: () -> Unit,
) {
    val warnings = buildList {
        if (geofenceConfig == null) add("Arbeitsort ist noch nicht eingerichtet.")
        if (permissions.requiresBackgroundPermission && !permissions.backgroundLocationGranted) add("Hintergrundstandort fehlt.")
        if (!permissions.locationServicesEnabled) add("Standortdienste sind deaktiviert.")
        if (!permissions.playServicesAvailable) add("Google Play-Dienste sind nicht verfügbar.")
        when (geofenceState) {
            is GeofenceRegistrationState.Error -> add("Geofence-Fehler: ${geofenceState.message}")
            GeofenceRegistrationState.LocationDisabled -> add("Geofence kann ohne Standortdienste nicht aktiv sein.")
            GeofenceRegistrationState.PlayServicesMissing -> add("Geofence benötigt Google Play-Dienste.")
            is GeofenceRegistrationState.MissingPermissions -> add("Geofence ist nicht aktiv, weil Rechte fehlen.")
            GeofenceRegistrationState.Registered -> Unit
            GeofenceRegistrationState.MissingConfiguration -> add("Arbeitsort ist noch nicht eingerichtet.")
            null -> add("Geofence-Status wird geprüft.")
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Berechtigungen & Geofence", fontWeight = FontWeight.SemiBold)
            if (warnings.isEmpty()) {
                Text("Alles bereit: Geofence kann registriert werden.")
            } else {
                warnings.forEach { Text("• $it") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (!permissions.fineLocationGranted) {
                    Button(onClick = onRequestFineLocation) { Text("Standort erlauben") }
                }
                if (!permissions.locationServicesEnabled) {
                    Button(onClick = onOpenLocationSettings) { Text("Standortdienste") }
                }
                if (permissions.fineLocationGranted && permissions.locationServicesEnabled && permissions.requiresBackgroundPermission && !permissions.backgroundLocationGranted) {
                    Button(onClick = onOpenAppSettings) { Text("Hintergrundzugriff") }
                }
                Button(onClick = onOpenWorkLocationTab) { Text("Arbeitsort") }
            }
        }
    }
}

@Composable
private fun CorrectionTab(
    modifier: Modifier,
    sessions: List<WorkSessionEntity>,
    onAddManual: (LocalDate, LocalTime, LocalTime, String?) -> Unit,
    onEditSession: (WorkSessionEntity) -> Unit,
    onDeleteSession: (WorkSessionEntity) -> Unit,
    manualDate: String,
    manualStart: String,
    manualEnd: String,
    manualNote: String,
    onManualDateChange: (String) -> Unit,
    onManualStartChange: (String) -> Unit,
    onManualEndChange: (String) -> Unit,
    onManualNoteChange: (String) -> Unit,
    zoneId: ZoneId,
) {
    val sorted = sessions.sortedByDescending { it.startTimestamp }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Manuelle Korrektur", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Neue Session hinzufügen", fontWeight = FontWeight.SemiBold)
                var formError by remember { mutableStateOf<String?>(null) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = manualDate, onValueChange = onManualDateChange, label = { Text("Datum YYYY-MM-DD") }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = manualStart, onValueChange = onManualStartChange, label = { Text("Start HH:mm") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = manualEnd, onValueChange = onManualEndChange, label = { Text("Ende HH:mm") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = manualNote, onValueChange = onManualNoteChange, label = { Text("Notiz optional") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    runCatching {
                        val date = LocalDate.parse(manualDate.trim())
                        val start = parseTime(manualStart)
                        val end = parseTime(manualEnd)
                        onAddManual(date, start, end, manualNote.ifBlank { null })
                    }.onFailure { formError = it.message ?: "Ungültige Eingabe" }
                }) { Text("Session hinzufügen") }
                formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }

        if (sorted.isEmpty()) {
            EmptyHint("Noch keine Sessions gespeichert.")
        } else {
            Text("Alle Sessions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            sorted.forEach { session ->
                SessionCard(session = session, zoneId = zoneId, onEdit = { onEditSession(session) }, onDelete = { onDeleteSession(session) })
            }
        }
    }
}

@Composable
private fun WorkLocationTab(
    modifier: Modifier,
    currentConfig: GeofenceConfig?,
    geofenceState: GeofenceRegistrationState?,
    onSaveConfig: (Double, Double) -> Unit,
    onClearConfig: () -> Unit,
    onRefreshGeofence: () -> Unit,
) {
    var latitudeText by rememberSaveable(currentConfig) { mutableStateOf(currentConfig?.latitude?.toString().orEmpty()) }
    var longitudeText by rememberSaveable(currentConfig) { mutableStateOf(currentConfig?.longitude?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentConfig) {
        latitudeText = currentConfig?.latitude?.toString().orEmpty()
        longitudeText = currentConfig?.longitude?.toString().orEmpty()
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Arbeitsort", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Koordinate eingeben", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = latitudeText,
                    onValueChange = { latitudeText = it },
                    label = { Text("Latitude") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = longitudeText,
                    onValueChange = { longitudeText = it },
                    label = { Text("Longitude") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        runCatching {
                            val latitude = latitudeText.trim().replace(',', '.').toDouble()
                            val longitude = longitudeText.trim().replace(',', '.').toDouble()
                            require(latitude in -90.0..90.0) { "Latitude muss zwischen -90 und 90 liegen." }
                            require(longitude in -180.0..180.0) { "Longitude muss zwischen -180 und 180 liegen." }
                            onSaveConfig(latitude, longitude)
                            error = null
                        }.onFailure { error = it.message ?: "Ungültige Koordinate" }
                    }) { Text("Speichern") }
                    TextButton(onClick = {
                        latitudeText = ""
                        longitudeText = ""
                        onClearConfig()
                        error = null
                    }) { Text("Löschen") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Aktueller Status", fontWeight = FontWeight.SemiBold)
                Text(
                    when (currentConfig) {
                        null -> "Noch keine Arbeitsort-Koordinate gesetzt."
                        else -> "Latitude ${currentConfig.latitude}, Longitude ${currentConfig.longitude}"
                    },
                )
                Text(
                    when (geofenceState) {
                        GeofenceRegistrationState.Registered -> "Geofence ist registriert."
                        GeofenceRegistrationState.MissingConfiguration -> "Für die Registrierung fehlt noch eine Koordinate."
                        is GeofenceRegistrationState.MissingPermissions -> "Geofence braucht noch Berechtigungen."
                        GeofenceRegistrationState.LocationDisabled -> "Standortdienste sind deaktiviert."
                        GeofenceRegistrationState.PlayServicesMissing -> "Google Play-Dienste fehlen."
                        is GeofenceRegistrationState.Error -> "Fehler: ${geofenceState.message}"
                        null -> "Geofence-Status wird geprüft."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(onClick = onRefreshGeofence) { Text("Geofence neu registrieren") }
    }
}

@Composable
private fun WeekTab(modifier: Modifier, sessions: List<WorkSessionEntity>, zoneId: ZoneId, weekAnchor: LocalDate) {
    val summary = TimeCalculator.weekSummary(sessions, weekAnchor, zoneId = zoneId)
    val maxDay = summary.days.maxOfOrNull { maxOf(it.totalMinutes, it.netMinutes) }?.coerceAtLeast(1) ?: 1
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Woche", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            metricCard("Wochensumme brutto", formatMinutes(summary.totalMinutes), "Montag bis Sonntag", workDurationAccent(summary.totalMinutes), modifier = Modifier.weight(1f))
            metricCard("Wochensumme netto", formatMinutes(summary.netMinutes), "Abzüglich Wegzeit und ggf. Pause", workDurationAccent(summary.netMinutes), modifier = Modifier.weight(1f))
        }
        summary.days.forEach { day ->
            DayBarCard(day = day, zoneId = zoneId, maxMinutes = maxDay)
        }
    }
}

@Composable
private fun MonthTab(
    modifier: Modifier,
    sessions: List<WorkSessionEntity>,
    zoneId: ZoneId,
    month: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val summary = TimeCalculator.monthSummary(sessions, month, zoneId = zoneId)
    val maxDay = summary.days.maxOfOrNull { maxOf(it.totalMinutes, it.netMinutes) }?.coerceAtLeast(1) ?: 1
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Monat", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onPreviousMonth) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Text(" Zurück") }
                TextButton(onClick = onNextMonth) { Text("Weiter "); Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
            }
        }
        Text(month.format(MonthFormatter), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            metricCard("Monatssumme brutto", formatMinutes(summary.totalMinutes), "Tageswerte unten", workDurationAccent(summary.totalMinutes), modifier = Modifier.weight(1f))
            metricCard("Monatssumme netto", formatMinutes(summary.netMinutes), "Abzüglich Wegzeit und ggf. Pause", workDurationAccent(summary.netMinutes), modifier = Modifier.weight(1f))
        }
        summary.days.forEach { day ->
            DayBarCard(day = day, zoneId = zoneId, maxMinutes = maxDay)
        }
    }
}

@Composable
private fun DayBarCard(day: DaySummary, zoneId: ZoneId, maxMinutes: Int) {
    val grossColor = workDurationAccent(day.totalMinutes)
    val netColor = workDurationAccent(day.netMinutes)
    Card {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(day.date.format(DayFormatter), fontWeight = FontWeight.SemiBold)
                Text("${formatMinutes(day.totalMinutes)} / ${formatMinutes(day.netMinutes)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Brutto ${formatMinutes(day.totalMinutes)}", color = grossColor, fontWeight = FontWeight.SemiBold)
            LinearBar(value = day.totalMinutes, maxValue = maxMinutes, fillColor = grossColor, barHeight = 8.dp)
            Text("Netto ${formatMinutes(day.netMinutes)}", color = netColor, fontWeight = FontWeight.SemiBold)
            LinearBar(value = day.netMinutes, maxValue = maxMinutes, fillColor = netColor, barHeight = 8.dp)
        }
    }
}

@Composable
private fun SessionCard(session: WorkSessionEntity, zoneId: ZoneId, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(formatSessionLabel(session), fontWeight = FontWeight.SemiBold)
                    Text(if (session.source == SessionSource.AUTO) "Automatisch" else "Manuell", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilterChip(selected = true, onClick = {}, label = { Text(session.source.name) })
            }
            Text("Start: ${formatTime(session.startTimestamp, zoneId)}")
            Text("Ende: ${session.endTimestamp?.let { formatTime(it, zoneId) } ?: "offen"}")
            Text("Dauer: ${formatMinutes(durationMinutes(session, Instant.now().toEpochMilli()))}")
            if (!session.note.isNullOrBlank()) Text("Notiz: ${session.note}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Icon(Icons.Default.Edit, null); Text(" Bearbeiten") }
                TextButton(onClick = onDelete) { Icon(Icons.Default.Delete, null); Text(" Löschen") }
            }
        }
    }
}

@Composable
private fun SessionSliceCard(slice: WorkSessionSlice, sessionWarning: String?, zoneId: ZoneId) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${formatTime(slice.displayStartTimestamp, zoneId)} – ${formatTime(slice.displayEndTimestamp, zoneId)}", fontWeight = FontWeight.SemiBold)
                Text(formatMinutes(slice.durationMinutes), color = MaterialTheme.colorScheme.primary)
            }
            Text("Quelle: ${slice.source}")
            if (slice.clipped) Text("Teilabschnitt des Tages", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!slice.note.isNullOrBlank()) Text("Notiz: ${slice.note}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            sessionWarning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun SessionEditorDialog(
    session: WorkSessionEntity,
    onDismiss: () -> Unit,
    onSave: (Long, Long?, String?) -> Unit,
) {
    val zoneId = ZoneId.systemDefault()
    var dateText by remember(session.id) { mutableStateOf(Instant.ofEpochMilli(session.startTimestamp).atZone(zoneId).toLocalDate().toString()) }
    var startText by remember(session.id) { mutableStateOf(formatTime(session.startTimestamp, zoneId)) }
    var endText by remember(session.id) { mutableStateOf(session.endTimestamp?.let { formatTime(it, zoneId) } ?: "") }
    var noteText by remember(session.id) { mutableStateOf(session.note.orEmpty()) }
    var error by remember(session.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Session bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quelle: ${session.source}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = dateText, onValueChange = { dateText = it }, label = { Text("Datum YYYY-MM-DD") })
                OutlinedTextField(value = startText, onValueChange = { startText = it }, label = { Text("Start HH:mm") })
                OutlinedTextField(value = endText, onValueChange = { endText = it }, label = { Text("Ende HH:mm, leer für offen") })
                OutlinedTextField(value = noteText, onValueChange = { noteText = it }, label = { Text("Notiz") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    val date = LocalDate.parse(dateText.trim())
                    val start = parseTime(startText)
                    val end = endText.trim().takeIf { it.isNotBlank() }?.let { parseTime(it) }
                    val startTs = date.atTime(start).atZone(zoneId).toInstant().toEpochMilli()
                    val endTs = end?.let { date.atTime(it).atZone(zoneId).toInstant().toEpochMilli() }
                    require(endTs == null || endTs > startTs) { "Ende muss nach dem Start liegen." }
                    onSave(startTs, endTs, noteText.ifBlank { null })
                }.onFailure { error = it.message ?: "Ungültige Eingabe" }
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun EmptyHint(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Text(text, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WorkDurationLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        LegendItem(color = MaterialTheme.colorScheme.primary, label = "Brutto & Netto: Farbverlauf je nach Zeit")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(99.dp)),
        )
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LinearBar(value: Int, maxValue: Int, fillColor: Color, barHeight: Dp = 10.dp) {
    val ratio = if (maxValue <= 0) 0f else (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(99.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(ratio)
                .height(barHeight)
                .background(fillColor, RoundedCornerShape(99.dp)),
        )
    }
}

@Composable
private fun workDurationAccent(minutes: Int): Color = when {
    minutes >= 10 * 60 -> workTenPlusColor()
    minutes >= 9 * 60 -> workNinePlusColor()
    else -> MaterialTheme.colorScheme.primary
}

private fun workNinePlusColor(): Color = Color(0xFF4C2A85)

private fun workTenPlusColor(): Color = Color(0xFFE91E88)

@Composable
private fun metricCard(title: String, value: String, subtitle: String, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accent)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DebugAlertCard(state: WorkTimeAlertScheduler.DebugState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Debug: Arbeitszeit-Warnungen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Heute: ${formatMinutes(state.todayMinutes)}")
            Text("Gestern: ${formatMinutes(state.yesterdayMinutes)}")
            Text("Offene AUTO-Session: ${if (state.openAutoSession) "ja" else "nein"}")
            Text("7,5h-Regel aktiv: ${if (state.earlyLeaveReminderEnabled) "ja" else "nein"}")
            Text("7,5h erreicht: ${if (state.sevenPointFiveReached) "ja" else "nein"}")
            Text("7,5h bereits gemeldet: ${if (state.sevenPointFiveNotified) "ja" else "nein"}")
            Text("8,5h bereits gemeldet: ${if (state.eightPointFiveNotified) "ja" else "nein"}")
            Text("9h bereits gemeldet: ${if (state.nineNotified) "ja" else "nein"}")
            Text("9,5h bereits gemeldet: ${if (state.ninePointFiveNotified) "ja" else "nein"}")
            Text("10h bereits gemeldet: ${if (state.tenNotified) "ja" else "nein"}")
            Text("Prefs-Datum ist heute: ${if (state.storedDateMatchesToday) "ja" else "nein"}")
            Text(
                "7,5h sollte jetzt auslösen: ${if (state.shouldNotifySevenPointFiveNow) "ja" else "nein"}",
                color = if (state.shouldNotifySevenPointFiveNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatSessionLabel(session: WorkSessionEntity): String {
    val date = Instant.ofEpochMilli(session.startTimestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    return "${date.format(DayFormatter)} · ${session.source}"
}

private fun formatTime(timestamp: Long, zoneId: ZoneId): String = Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalTime().format(TimeFormatter)

private fun durationMinutes(session: WorkSessionEntity, nowMillis: Long): Int {
    val end = session.endTimestamp ?: nowMillis
    return ((end - session.startTimestamp) / 60_000L).toInt().coerceAtLeast(0)
}

private fun shortSessionWarning(slice: WorkSessionSlice): String? {
    return if (slice.source == SessionSource.AUTO && slice.durationMinutes in 1 until SHORT_SESSION_WARNING_MINUTES) {
        "Achtung: sehr kurze automatische Session, kann ein Vorbeifahr-Event sein."
    } else null
}

private fun formatMinutes(minutes: Int): String {
    val abs = kotlin.math.abs(minutes)
    val hours = abs / 60
    val mins = abs % 60
    return if (hours == 0) "%02d min".format(mins) else "%d h %02d min".format(hours, mins)
}

private fun parseTime(text: String): LocalTime {
    val normalized = text.trim()
    if (normalized.contains(":")) return LocalTime.parse(normalized, TimeFormatter)
    val digits = normalized.filter(Char::isDigit)
    require(digits.length in 3..4) { "Bitte eine Uhrzeit angeben." }
    val padded = digits.padStart(4, '0')
    return LocalTime.of(padded.substring(0, 2).toInt(), padded.substring(2, 4).toInt())
}

private fun readPermissionSnapshot(context: Context): PermissionSnapshot {
    val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val backgroundGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    val locationManager = context.getSystemService(android.location.LocationManager::class.java)
    val servicesEnabled = locationManager?.isLocationEnabled ?: false
    val playServicesAvailable = com.google.android.gms.common.GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == com.google.android.gms.common.ConnectionResult.SUCCESS
    return PermissionSnapshot(fineGranted, backgroundGranted, servicesEnabled, playServicesAvailable)
}

private fun Context.openLocationSettings() {
    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}
