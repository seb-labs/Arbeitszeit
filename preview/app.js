const STORAGE_KEY = 'arbeitszeitapp-web-sim-v1';
const STANDARD_WEEK_TARGET_MINUTES = 41 * 60;
const STANDARD_WORKDAYS_PER_WEEK = 5;
const AUTO_PAUSE_THRESHOLD_MINUTES = 6 * 60;
const AUTO_PAUSE_MINUTES = 30;

const els = {
  todayView: document.getElementById('todayView'),
  historyView: document.getElementById('historyView'),
  weekView: document.getElementById('weekView'),
  monthView: document.getElementById('monthView'),
  aboutView: document.getElementById('aboutView'),
  tabs: [...document.querySelectorAll('.tab')],
  loadDemoBtn: document.getElementById('loadDemoBtn'),
  resetBtn: document.getElementById('resetBtn'),
  dayDialog: document.getElementById('dayDialog'),
  dayForm: document.getElementById('dayForm'),
  dialogTitle: document.getElementById('dialogTitle'),
  editDate: document.getElementById('editDate'),
  editWeekTarget: document.getElementById('editWeekTarget'),
  editComment: document.getElementById('editComment'),
  editClosedAt: document.getElementById('editClosedAt'),
  intervalEditor: document.getElementById('intervalEditor'),
  addIntervalBtn: document.getElementById('addIntervalBtn'),
  intervalRowTemplate: document.getElementById('intervalRowTemplate'),
};

const state = loadState();
let activeTab = 'today';
let editingDateIso = null;

function defaultState() {
  return { days: [], weekTargetOverrides: {} };
}

function loadState() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return createDemoState();
    const parsed = JSON.parse(raw);
    return normalizeState(parsed);
  } catch {
    return createDemoState();
  }
}

function saveState() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

function normalizeState(raw) {
  const base = defaultState();
  const days = Array.isArray(raw?.days) ? raw.days.map(normalizeDay).sort((a, b) => a.dateIso.localeCompare(b.dateIso)) : [];
  const weekTargetOverrides = raw?.weekTargetOverrides && typeof raw.weekTargetOverrides === 'object' ? { ...raw.weekTargetOverrides } : {};
  return { ...base, days, weekTargetOverrides };
}

function normalizeDay(day) {
  return {
    dateIso: String(day?.dateIso || todayIso()),
    intervals: Array.isArray(day?.intervals) ? day.intervals.map(normalizeInterval) : [],
    manualCorrectionComment: String(day?.manualCorrectionComment || ''),
    closedAtEpochMillis: day?.closedAtEpochMillis ?? null,
    auditTrail: Array.isArray(day?.auditTrail) ? day.auditTrail : [],
  };
}

function normalizeInterval(interval) {
  return {
    startEpochMillis: Number(interval?.startEpochMillis || Date.now()),
    endEpochMillis: interval?.endEpochMillis === null || interval?.endEpochMillis === undefined || interval?.endEpochMillis === '' ? null : Number(interval.endEpochMillis),
  };
}

function createDemoState() {
  const today = new Date();
  const todayIsoDate = todayIso();
  const day0 = dateDaysAgo(0);
  const day1 = dateDaysAgo(1);
  const day2 = dateDaysAgo(2);

  return normalizeState({
    weekTargetOverrides: {
      [weekKey(day0)]: 41 * 60,
      [weekKey(day1)]: 37 * 60,
    },
    days: [
      {
        dateIso: toIsoDate(day2),
        intervals: [
          intervalAt(toIsoDateTime(day2, 8, 0), toIsoDateTime(day2, 12, 30)),
          intervalAt(toIsoDateTime(day2, 13, 0), toIsoDateTime(day2, 17, 15)),
        ],
        manualCorrectionComment: 'Demo-Tag mit Mittagspause',
        closedAtEpochMillis: toIsoDateTime(day2, 17, 15).getTime(),
      },
      {
        dateIso: toIsoDate(day1),
        intervals: [
          intervalAt(toIsoDateTime(day1, 7, 45), toIsoDateTime(day1, 16, 30)),
        ],
        manualCorrectionComment: 'Mehr als 6 Stunden am Stück',
        closedAtEpochMillis: toIsoDateTime(day1, 16, 30).getTime(),
      },
      {
        dateIso: todayIsoDate,
        intervals: [
          intervalAt(toIsoDateTime(today, 9, 0), null),
        ],
        manualCorrectionComment: '',
      },
    ],
  });
}

function todayIso() {
  return toIsoDate(new Date());
}

function toIsoDate(date) {
  return new Date(date).toISOString().slice(0, 10);
}

function parseIsoDate(dateIso) {
  return new Date(`${dateIso}T00:00:00`);
}

function toIsoDateTime(date, hours, minutes) {
  const d = new Date(date);
  d.setHours(hours, minutes, 0, 0);
  return d;
}

function intervalAt(startDate, endDate) {
  return { startEpochMillis: startDate.getTime(), endEpochMillis: endDate ? endDate.getTime() : null };
}

function dateDaysAgo(days) {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d;
}

function addMinutes(date, minutes) {
  return new Date(date.getTime() + minutes * 60000);
}

function formatDateTime(epochMillis) {
  if (epochMillis === null || epochMillis === undefined) return '—';
  return new Intl.DateTimeFormat('de-DE', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(epochMillis));
}

function formatDate(dateIso) {
  return new Intl.DateTimeFormat('de-DE', { dateStyle: 'full' }).format(parseIsoDate(dateIso));
}

function formatMonth(ym) {
  const [year, month] = ym.split('-').map(Number);
  return new Intl.DateTimeFormat('de-DE', { month: 'long', year: 'numeric' }).format(new Date(year, month - 1, 1));
}

function formatMinutes(mins) {
  const sign = mins < 0 ? '-' : '';
  const abs = Math.abs(mins);
  const h = Math.floor(abs / 60);
  const m = abs % 60;
  return `${sign}${h} h ${String(m).padStart(2, '0')} min`;
}

function formatCompactMinutes(mins) {
  const sign = mins < 0 ? '-' : '';
  const abs = Math.abs(mins);
  const h = Math.floor(abs / 60);
  const m = abs % 60;
  return `${sign}${h}:${String(m).padStart(2, '0')} h`;
}

function todayDay() {
  return getDay(todayIso()) || { dateIso: todayIso(), intervals: [], manualCorrectionComment: '', closedAtEpochMillis: null, auditTrail: [] };
}

function getDay(dateIso) {
  return state.days.find(day => day.dateIso === dateIso) || null;
}

function upsertDay(day) {
  const idx = state.days.findIndex(d => d.dateIso === day.dateIso);
  if (idx >= 0) state.days[idx] = day;
  else state.days.push(day);
  state.days.sort((a, b) => a.dateIso.localeCompare(b.dateIso));
  saveState();
}

function removeDay(dateIso) {
  state.days = state.days.filter(day => day.dateIso !== dateIso);
  saveState();
}

function weekKey(date) {
  const target = new Date(date);
  const d = new Date(Date.UTC(target.getFullYear(), target.getMonth(), target.getDate()));
  const dayNum = d.getUTCDay() || 7;
  d.setUTCDate(d.getUTCDate() + 4 - dayNum);
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
  const weekNo = Math.ceil((((d - yearStart) / 86400000) + 1) / 7);
  return `${d.getUTCFullYear()}-W${String(weekNo).padStart(2, '0')}`;
}

function weekStart(date) {
  const d = new Date(date);
  const day = d.getDay() || 7;
  d.setDate(d.getDate() - day + 1);
  d.setHours(0, 0, 0, 0);
  return d;
}

function monthKey(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

function plannedMinutesForDate(date, weekTargetMinutes) {
  return date.getDay() >= 1 && date.getDay() <= 5 ? Math.floor(weekTargetMinutes / STANDARD_WORKDAYS_PER_WEEK) : 0;
}

function calculateManualPauses(intervals, now = Date.now()) {
  const sorted = [...intervals].sort((a, b) => a.startEpochMillis - b.startEpochMillis);
  if (sorted.length < 2) return 0;
  let total = 0;
  for (let i = 0; i < sorted.length - 1; i++) {
    const currentEnd = sorted[i].endEpochMillis ?? now;
    const nextStart = sorted[i + 1].startEpochMillis;
    const gap = Math.floor((nextStart - currentEnd) / 60000);
    if (gap > 0) total += gap;
  }
  return total;
}

function resolveIntervals(intervals, now = Date.now()) {
  return [...intervals]
    .sort((a, b) => a.startEpochMillis - b.startEpochMillis)
    .map(interval => {
      const start = interval.startEpochMillis;
      const end = interval.endEpochMillis ?? now;
      const durationMinutes = Math.max(0, Math.floor((end - start) / 60000));
      return {
        ...interval,
        start,
        end,
        durationMinutes,
        automaticPauseDeducted: durationMinutes > AUTO_PAUSE_THRESHOLD_MINUTES,
      };
    });
}

function computeDay(day, now = Date.now()) {
  const weekTarget = weekTargetMinutesForDate(parseIsoDate(day.dateIso));
  const resolved = resolveIntervals(day.intervals, now);
  const gross = resolved.reduce((sum, item) => sum + item.durationMinutes, 0);
  const manualPauseMinutes = calculateManualPauses(day.intervals, now);
  const automaticPauseMinutes = resolved.filter(i => i.automaticPauseDeducted).length * AUTO_PAUSE_MINUTES;
  const workingMinutes = Math.max(0, gross - manualPauseMinutes - automaticPauseMinutes);
  const plannedMinutes = plannedMinutesForDate(parseIsoDate(day.dateIso), weekTarget);
  return {
    date: day.dateIso,
    workingMinutes,
    manualPauseMinutes,
    automaticPauseMinutes,
    plannedMinutes,
    balanceMinutes: workingMinutes - plannedMinutes,
    intervals: resolved,
    grossMinutes: gross,
    weekTargetMinutes: weekTarget,
  };
}

function weekTargetMinutesForDate(date) {
  return state.weekTargetOverrides[weekKey(date)] ?? STANDARD_WEEK_TARGET_MINUTES;
}

function setWeekTargetForDate(date, minutes) {
  state.weekTargetOverrides[weekKey(date)] = minutes;
  saveState();
}

function weekSummaryFor(weekStartDate) {
  const key = weekKey(weekStartDate);
  const days = state.days.filter(day => weekKey(parseIsoDate(day.dateIso)) === key);
  const workingMinutes = days.reduce((sum, day) => sum + computeDay(day).workingMinutes, 0);
  const plannedMinutes = days.reduce((sum, day) => sum + plannedMinutesForDate(parseIsoDate(day.dateIso), weekTargetMinutesForDate(parseIsoDate(day.dateIso))), 0);
  return { key, days, workingMinutes, plannedMinutes, balanceMinutes: workingMinutes - plannedMinutes };
}

function monthSummaryFor(month) {
  const days = state.days.filter(day => monthKey(parseIsoDate(day.dateIso)) === month);
  const workingMinutes = days.reduce((sum, day) => sum + computeDay(day).workingMinutes, 0);
  const plannedMinutes = days.reduce((sum, day) => sum + plannedMinutesForDate(parseIsoDate(day.dateIso), weekTargetMinutesForDate(parseIsoDate(day.dateIso))), 0);
  return { month, days, workingMinutes, plannedMinutes, balanceMinutes: workingMinutes - plannedMinutes };
}

function currentStatus(day) {
  if (!day || day.intervals.length === 0) return 'Bereit';
  const last = [...day.intervals].sort((a, b) => a.startEpochMillis - b.startEpochMillis).at(-1);
  if (day.closedAtEpochMillis) return 'Beendet';
  if (last && last.endEpochMillis === null) return 'Arbeitet';
  return 'Pausiert';
}

function startWork(dateIso = todayIso()) {
  const now = Date.now();
  const day = getDay(dateIso) ?? { dateIso, intervals: [], manualCorrectionComment: '', closedAtEpochMillis: null, auditTrail: [] };
  const sorted = [...day.intervals].sort((a, b) => a.startEpochMillis - b.startEpochMillis);
  const last = sorted.at(-1);
  if (last && last.endEpochMillis === null) return;
  day.intervals.push({ startEpochMillis: now, endEpochMillis: null });
  day.closedAtEpochMillis = null;
  day.auditTrail = [...(day.auditTrail || []), auditEntry('intervals', '…', 'Arbeitsbeginn', 'Arbeitsbeginn gebucht')];
  upsertDay(day);
}

function pauseWork(dateIso = todayIso()) {
  const now = Date.now();
  const day = getDay(dateIso);
  if (!day) return;
  const sortedIndex = [...day.intervals].map((interval, idx) => ({ interval, idx })).sort((a, b) => a.interval.startEpochMillis - b.interval.startEpochMillis);
  const lastWrapper = sortedIndex.at(-1);
  if (!lastWrapper) return;
  const last = day.intervals[lastWrapper.idx];
  if (last.endEpochMillis !== null) return;
  day.intervals[lastWrapper.idx] = { ...last, endEpochMillis: now };
  day.auditTrail = [...(day.auditTrail || []), auditEntry('intervals', 'offen', 'geschlossen', 'Pause gebucht')];
  upsertDay(day);
}

function resumeWork(dateIso = todayIso()) {
  const now = Date.now();
  const day = getDay(dateIso) ?? { dateIso, intervals: [], manualCorrectionComment: '', closedAtEpochMillis: null, auditTrail: [] };
  const sorted = [...day.intervals].sort((a, b) => a.startEpochMillis - b.startEpochMillis);
  const last = sorted.at(-1);
  if (last && last.endEpochMillis === null) return;
  day.intervals.push({ startEpochMillis: now, endEpochMillis: null });
  day.closedAtEpochMillis = null;
  day.auditTrail = [...(day.auditTrail || []), auditEntry('intervals', 'Pause', 'Arbeit', 'Fortsetzen gebucht')];
  upsertDay(day);
}

function endWork(dateIso = todayIso()) {
  const now = Date.now();
  const day = getDay(dateIso) ?? { dateIso, intervals: [], manualCorrectionComment: '', closedAtEpochMillis: null, auditTrail: [] };
  const sorted = [...day.intervals].sort((a, b) => a.startEpochMillis - b.startEpochMillis);
  const last = sorted.at(-1);
  if (last && last.endEpochMillis === null) {
    const idx = day.intervals.indexOf(last);
    day.intervals[idx] = { ...last, endEpochMillis: now };
  }
  day.closedAtEpochMillis = now;
  day.auditTrail = [...(day.auditTrail || []), auditEntry('closedAt', String(day.closedAtEpochMillis ?? 'null'), String(now), 'Arbeitsende gebucht')];
  upsertDay(day);
}

function auditEntry(field, oldValue, newValue, comment) {
  return { atEpochMillis: Date.now(), field, oldValue, newValue, comment };
}

function openEditor(dateIso) {
  editingDateIso = dateIso;
  const day = getDay(dateIso) ?? { dateIso, intervals: [], manualCorrectionComment: '', closedAtEpochMillis: null, auditTrail: [] };
  const date = parseIsoDate(dateIso);
  els.dialogTitle.textContent = `Tag ${formatDate(dateIso)}`;
  els.editDate.value = dateIso;
  els.editWeekTarget.value = String(weekTargetMinutesForDate(date));
  els.editComment.value = day.manualCorrectionComment || '';
  els.editClosedAt.value = day.closedAtEpochMillis ? toDatetimeLocalValue(new Date(day.closedAtEpochMillis)) : '';
  renderIntervalEditor(day.intervals);
  els.dayDialog.showModal();
}

function renderIntervalEditor(intervals) {
  els.intervalEditor.innerHTML = '';
  const sorted = [...intervals].sort((a, b) => a.startEpochMillis - b.startEpochMillis);
  if (sorted.length === 0) {
    addIntervalRow({ startEpochMillis: Date.now(), endEpochMillis: null });
  } else {
    sorted.forEach(interval => addIntervalRow(interval));
  }
}

function addIntervalRow(interval = { startEpochMillis: Date.now(), endEpochMillis: null }) {
  const node = els.intervalRowTemplate.content.cloneNode(true);
  const row = node.querySelector('.interval-row');
  const start = row.querySelector('[data-field="start"]');
  const end = row.querySelector('[data-field="end"]');
  start.value = toDatetimeLocalValue(new Date(interval.startEpochMillis));
  end.value = interval.endEpochMillis ? toDatetimeLocalValue(new Date(interval.endEpochMillis)) : '';
  row.querySelector('[data-action="remove"]').addEventListener('click', () => row.remove());
  els.intervalEditor.appendChild(node);
}

function toDatetimeLocalValue(date) {
  const pad = n => String(n).padStart(2, '0');
  const d = new Date(date);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function fromDatetimeLocalValue(value) {
  if (!value) return null;
  return new Date(value).getTime();
}

function saveEditor() {
  const dateIso = els.editDate.value;
  if (!dateIso) return;
  const day = getDay(dateIso) ?? { dateIso, intervals: [], manualCorrectionComment: '', closedAtEpochMillis: null, auditTrail: [] };
  const intervals = [...els.intervalEditor.querySelectorAll('.interval-row')].map(row => ({
    startEpochMillis: fromDatetimeLocalValue(row.querySelector('[data-field="start"]').value) ?? Date.now(),
    endEpochMillis: fromDatetimeLocalValue(row.querySelector('[data-field="end"]').value),
  })).sort((a, b) => a.startEpochMillis - b.startEpochMillis);
  day.dateIso = dateIso;
  day.intervals = intervals;
  day.manualCorrectionComment = els.editComment.value.trim();
  day.closedAtEpochMillis = fromDatetimeLocalValue(els.editClosedAt.value);
  day.auditTrail = [...(day.auditTrail || []), auditEntry('manual_edit', 'alt', 'neu', 'Historischen Eintrag bearbeitet')];
  upsertDay(day);
  setWeekTargetForDate(parseIsoDate(dateIso), Number(els.editWeekTarget.value || STANDARD_WEEK_TARGET_MINUTES));
  els.dayDialog.close();
  editingDateIso = null;
}

function renderToday() {
  const day = todayDay();
  const computed = computeDay(day);
  const status = currentStatus(day);
  const openInterval = [...day.intervals].sort((a, b) => a.startEpochMillis - b.startEpochMillis).at(-1);
  const weekTarget = computed.weekTargetMinutes;
  const weekBalance = weekSummaryFor(weekStart(parseIsoDate(day.dateIso)));
  els.todayView.innerHTML = `
    <div class="grid two">
      <div class="card card-section stack">
        <div class="row-between">
          <div>
            <div class="eyebrow">Heute</div>
            <h2 style="margin:6px 0 0">${formatDate(day.dateIso)}</h2>
          </div>
          <span class="badge">${status}</span>
        </div>
        <div class="row-wrap">
          <button class="primary" id="btnStart">Arbeitsbeginn</button>
          <button class="secondary" id="btnPause">Pause</button>
          <button class="secondary" id="btnResume">Fortsetzen</button>
          <button class="ghost" id="btnEnd">Arbeitsende</button>
          <button class="ghost" id="btnEditToday">Tag bearbeiten</button>
        </div>
        <div class="notice ${openInterval && openInterval.endEpochMillis === null ? '' : 'warn'}">
          ${openInterval && openInterval.endEpochMillis === null ? `Laufendes Intervall seit <strong>${formatDateTime(openInterval.startEpochMillis)}</strong>.` : 'Kein offenes Intervall aktiv.'}
        </div>
        <div class="kpis">
          <div class="card kpi"><div class="label">Ist-Zeit</div><div class="value">${formatCompactMinutes(computed.workingMinutes)}</div><div class="detail">Brutto: ${formatMinutes(computed.grossMinutes)}</div></div>
          <div class="card kpi"><div class="label">Manuelle Pausen</div><div class="value">${formatCompactMinutes(computed.manualPauseMinutes)}</div><div class="detail">Aus Lücken zwischen Intervallen</div></div>
          <div class="card kpi"><div class="label">Auto-Pause</div><div class="value">${formatCompactMinutes(computed.automaticPauseMinutes)}</div><div class="detail">${computed.intervals.some(i => i.automaticPauseDeducted) ? 'Abzug angewendet' : 'Kein Abzug'}</div></div>
          <div class="card kpi"><div class="label">Tagessaldo</div><div class="value">${computed.balanceMinutes >= 0 ? '+' : ''}${formatCompactMinutes(computed.balanceMinutes)}</div><div class="detail">Soll: ${formatMinutes(computed.plannedMinutes)}</div></div>
        </div>
      </div>

      <div class="card card-section stack">
        <div class="row-between">
          <h3 style="margin:0">Arbeitsintervalle</h3>
          <span class="muted small">Wochenziel: ${formatMinutes(weekTarget)}</span>
        </div>
        <div class="list">
          ${computed.intervals.length ? computed.intervals.map((interval, idx) => `
            <div class="list-item">
              <header>
                <h4>Intervall ${idx + 1}</h4>
                <span class="badge">${interval.automaticPauseDeducted ? 'Auto-Pause' : 'Normal'}</span>
              </header>
              <div class="row-between small"><span>Start</span><strong>${formatDateTime(interval.start)}</strong></div>
              <div class="row-between small"><span>Ende</span><strong>${formatDateTime(interval.end)}</strong></div>
              <div class="row-between small"><span>Dauer</span><strong>${formatMinutes(interval.durationMinutes)}</strong></div>
            </div>`).join('') : '<div class="muted">Noch keine Arbeitsintervalle erfasst.</div>'}
        </div>
        <div class="summary-grid">
          <div class="summary-box"><div class="muted">Pausen gesamt</div><div class="num">${formatMinutes(computed.manualPauseMinutes + computed.automaticPauseMinutes)}</div></div>
          <div class="summary-box"><div class="muted">Woche</div><div class="num">${weekBalance.balanceMinutes >= 0 ? '+' : ''}${formatMinutes(weekBalance.balanceMinutes)}</div></div>
        </div>
      </div>
    </div>`;

  bindTodayActions(day.dateIso);
}

function bindTodayActions(dateIso) {
  document.getElementById('btnStart').onclick = () => { startWork(dateIso); renderAll(); };
  document.getElementById('btnPause').onclick = () => { pauseWork(dateIso); renderAll(); };
  document.getElementById('btnResume').onclick = () => { resumeWork(dateIso); renderAll(); };
  document.getElementById('btnEnd').onclick = () => { endWork(dateIso); renderAll(); };
  document.getElementById('btnEditToday').onclick = () => openEditor(dateIso);
}

function renderHistory() {
  const days = [...state.days].sort((a, b) => b.dateIso.localeCompare(a.dateIso));
  els.historyView.innerHTML = `
    <div class="card card-section stack">
      <div class="row-between">
        <div>
          <div class="eyebrow">Historie</div>
          <h2 style="margin:6px 0 0">Alle gestempelten Tage</h2>
        </div>
        <span class="muted small">${days.length} Einträge</span>
      </div>
      <div class="list">
        ${days.length ? days.map(day => {
          const computed = computeDay(day);
          return `
            <div class="list-item">
              <header>
                <div>
                  <h3>${formatDate(day.dateIso)}</h3>
                  <div class="muted small">${day.manualCorrectionComment || 'Keine Notiz'}</div>
                </div>
                <span class="badge">${computed.balanceMinutes >= 0 ? '+' : ''}${formatCompactMinutes(computed.balanceMinutes)}</span>
              </header>
              <div class="row-wrap small">
                <span>Ist: <strong>${formatMinutes(computed.workingMinutes)}</strong></span>
                <span>Soll: <strong>${formatMinutes(computed.plannedMinutes)}</strong></span>
                <span>Pausen: <strong>${formatMinutes(computed.manualPauseMinutes + computed.automaticPauseMinutes)}</strong></span>
              </div>
              <div class="row-wrap">
                <button class="secondary" data-edit="${day.dateIso}">Bearbeiten</button>
                <button class="ghost" data-delete="${day.dateIso}">Löschen</button>
              </div>
            </div>`;
        }).join('') : '<div class="muted">Noch keine Historie vorhanden. Lade Beispiel-Daten oder buche einen Tag.</div>'}
      </div>
    </div>`;
  els.historyView.querySelectorAll('[data-edit]').forEach(btn => btn.addEventListener('click', () => openEditor(btn.dataset.edit)));
  els.historyView.querySelectorAll('[data-delete]').forEach(btn => btn.addEventListener('click', () => { if (confirm('Diesen Tag löschen?')) { removeDay(btn.dataset.delete); renderAll(); } }));
}

function renderWeek() {
  const base = weekStart(parseIsoDate(todayIso()));
  const key = weekKey(base);
  const target = weekTargetMinutesForDate(base);
  const days = state.days.filter(day => weekKey(parseIsoDate(day.dateIso)) === key);
  const summary = weekSummaryFor(base);
  els.weekView.innerHTML = `
    <div class="grid two">
      <div class="card card-section stack">
        <div class="row-between">
          <div>
            <div class="eyebrow">Woche</div>
            <h2 style="margin:6px 0 0">${key}</h2>
          </div>
          <span class="badge">${formatMinutes(summary.balanceMinutes)}</span>
        </div>
        <label>
          Wochen-Sollzeit
          <input id="weekTargetInput" type="number" min="0" step="5" value="${target}" />
        </label>
        <button id="saveWeekTarget" class="primary">Wochen-Sollzeit speichern</button>
        <div class="summary-grid">
          <div class="summary-box"><div class="muted">Ist</div><div class="num">${formatMinutes(summary.workingMinutes)}</div></div>
          <div class="summary-box"><div class="muted">Soll</div><div class="num">${formatMinutes(summary.plannedMinutes)}</div></div>
          <div class="summary-box"><div class="muted">Saldo</div><div class="num">${summary.balanceMinutes >= 0 ? '+' : ''}${formatMinutes(summary.balanceMinutes)}</div></div>
        </div>
      </div>
      <div class="card card-section stack">
        <h3 style="margin:0">Tage in dieser Woche</h3>
        <div class="list">
          ${days.length ? days.map(day => {
            const computed = computeDay(day);
            return `<div class="list-item">
              <div class="row-between"><strong>${formatDate(day.dateIso)}</strong><span class="badge">${formatCompactMinutes(computed.balanceMinutes)}</span></div>
              <div class="row-wrap small"><span>Ist: <strong>${formatMinutes(computed.workingMinutes)}</strong></span><span>Soll: <strong>${formatMinutes(computed.plannedMinutes)}</strong></span></div>
            </div>`;
          }).join('') : '<div class="muted">Für diese Woche sind noch keine Einträge vorhanden.</div>'}
        </div>
      </div>
    </div>`;
  document.getElementById('saveWeekTarget').onclick = () => {
    const value = Number(document.getElementById('weekTargetInput').value);
    if (!Number.isFinite(value)) return;
    setWeekTargetForDate(base, value);
    renderAll();
  };
}

function renderMonth() {
  const month = monthKey(new Date());
  const summary = monthSummaryFor(month);
  const monthDays = [...state.days].filter(day => monthKey(parseIsoDate(day.dateIso)) === month).sort((a, b) => a.dateIso.localeCompare(b.dateIso));
  els.monthView.innerHTML = `
    <div class="grid two">
      <div class="card card-section stack">
        <div class="row-between">
          <div>
            <div class="eyebrow">Monat</div>
            <h2 style="margin:6px 0 0">${formatMonth(month)}</h2>
          </div>
          <span class="badge">${formatCompactMinutes(summary.balanceMinutes)}</span>
        </div>
        <div class="summary-grid">
          <div class="summary-box"><div class="muted">Ist</div><div class="num">${formatMinutes(summary.workingMinutes)}</div></div>
          <div class="summary-box"><div class="muted">Soll</div><div class="num">${formatMinutes(summary.plannedMinutes)}</div></div>
          <div class="summary-box"><div class="muted">Saldo</div><div class="num">${summary.balanceMinutes >= 0 ? '+' : ''}${formatMinutes(summary.balanceMinutes)}</div></div>
        </div>
      </div>
      <div class="card card-section stack">
        <h3 style="margin:0">Monatswerte</h3>
        <div class="list">
          ${monthDays.length ? monthDays.map(day => {
            const computed = computeDay(day);
            return `<div class="list-item">
              <div class="row-between"><strong>${formatDate(day.dateIso)}</strong><span class="badge">${formatCompactMinutes(computed.balanceMinutes)}</span></div>
              <div class="muted small">Wochenziel: ${formatMinutes(computed.weekTargetMinutes)}</div>
            </div>`;
          }).join('') : '<div class="muted">Für diesen Monat sind noch keine Einträge vorhanden.</div>'}
        </div>
      </div>
    </div>`;
}

function renderAbout() {}

function renderAll() {
  renderToday();
  renderHistory();
  renderWeek();
  renderMonth();
  renderAbout();
}

function switchTab(tab) {
  activeTab = tab;
  els.tabs.forEach(btn => btn.classList.toggle('active', btn.dataset.tab === tab));
  els.todayView.classList.toggle('hidden', tab !== 'today');
  els.historyView.classList.toggle('hidden', tab !== 'history');
  els.weekView.classList.toggle('hidden', tab !== 'week');
  els.monthView.classList.toggle('hidden', tab !== 'month');
  els.aboutView.classList.toggle('hidden', tab !== 'about');
}

els.tabs.forEach(btn => btn.addEventListener('click', () => switchTab(btn.dataset.tab)));
els.loadDemoBtn.addEventListener('click', () => {
  const demo = createDemoState();
  state.days = demo.days;
  state.weekTargetOverrides = demo.weekTargetOverrides;
  saveState();
  renderAll();
});
els.resetBtn.addEventListener('click', () => {
  if (!confirm('Alle lokalen Simulationsdaten löschen?')) return;
  state.days = [];
  state.weekTargetOverrides = {};
  saveState();
  renderAll();
});
els.addIntervalBtn.addEventListener('click', () => addIntervalRow());
els.dayForm.addEventListener('submit', event => {
  event.preventDefault();
  saveEditor();
  renderAll();
});

window.addEventListener('keydown', event => {
  if (event.key === 'Escape' && els.dayDialog.open) els.dayDialog.close();
});

renderAll();
switchTab('today');
