'use strict';
/* Notification Center demo client. Talks only to the real backend: REST for state, STOMP/WebSocket for live pushes.
   All user-supplied text is rendered with textContent (never innerHTML). */

const state = {
  token: null, user: null, expiresAt: null,
  page: 0, size: 10, totalPages: 0,
  latest: [], templates: [], ws: null, reconnectDelay: 1000, reconnectTimer: null, loggedIn: false,
};
const $ = (id) => document.getElementById(id);

/* ---------- tiny STOMP 1.2 client over WebSocket ---------- */
class StompClient {
  constructor(url, headers, handlers) { this.url = url; this.headers = headers; this.h = handlers; this.buffer = ''; this.subs = 0; }
  connect() {
    this.ws = new WebSocket(this.url);
    this.ws.onopen = () => this.send('CONNECT', { 'accept-version': '1.2', 'heart-beat': '0,0', ...this.headers });
    this.ws.onmessage = (ev) => this.onData(typeof ev.data === 'string' ? ev.data : '');
    this.ws.onclose = () => this.h.onClose();
    this.ws.onerror = () => { /* onclose follows */ };
  }
  send(command, headers = {}, body = '') {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;
    let frame = command + '\n';
    for (const [k, v] of Object.entries(headers)) frame += `${k}:${v}\n`;
    this.ws.send(frame + '\n' + body + '\0');
  }
  subscribe(destination) { this.send('SUBSCRIBE', { id: 'sub-' + (this.subs++), destination, ack: 'auto' }); }
  publish(destination, obj) { this.send('SEND', { destination, 'content-type': 'application/json' }, JSON.stringify(obj)); }
  close() { try { this.ws && this.ws.close(); } catch (e) { /* ignore */ } }
  onData(chunk) {
    this.buffer += chunk;
    let idx;
    while ((idx = this.buffer.indexOf('\0')) >= 0) {
      const raw = this.buffer.slice(0, idx).replace(/^\n+/, '');
      this.buffer = this.buffer.slice(idx + 1);
      if (raw) this.onFrame(raw);
    }
  }
  onFrame(raw) {
    const split = raw.indexOf('\n\n');
    const head = (split >= 0 ? raw.slice(0, split) : raw).split('\n');
    const body = split >= 0 ? raw.slice(split + 2) : '';
    const command = head.shift();
    const headers = {};
    for (const line of head) { const i = line.indexOf(':'); if (i > 0 && !(line.slice(0, i) in headers)) headers[line.slice(0, i)] = line.slice(i + 1); }
    if (command === 'CONNECTED') this.h.onConnected();
    else if (command === 'MESSAGE') this.h.onMessage(body);
    else if (command === 'ERROR') this.h.onError(headers.message || 'STOMP error');
  }
}

/* ---------- REST helper ---------- */
async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  if (state.token) headers.Authorization = 'Bearer ' + state.token;
  let res;
  try { res = await fetch(path, { ...options, headers }); }
  catch (e) { throw new Error('Cannot reach the server. Is the API running?'); }
  if (res.status === 401 && state.token) { logout('Session expired, please sign in again.'); throw new Error('Unauthorized'); }
  if (res.status === 204) return null;
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    const detail = data && data.details && data.details.length ? ' (' + data.details.join('; ') + ')' : '';
    const corr = data && data.correlationId ? ' [ref ' + data.correlationId.slice(0, 8) + ']' : '';
    throw new Error(((data && data.message) || 'Request failed') + detail + corr);
  }
  return data;
}

/* ---------- auth ---------- */
let registerMode = false;
function setAuthMode(register) {
  registerMode = register;
  $('tab-login').classList.toggle('active', !register);
  $('tab-register').classList.toggle('active', register);
  $('email-row').hidden = !register;
  $('auth-submit').textContent = register ? 'Create account' : 'Sign in';
}
async function submitAuth() {
  const username = $('auth-username').value.trim().toLowerCase();
  const password = $('auth-password').value;
  const err = $('auth-error'); err.hidden = true;
  try {
    const body = registerMode ? { username, email: $('auth-email').value.trim(), password } : { username, password };
    const data = await api(registerMode ? '/api/v1/auth/register' : '/api/v1/auth/login', { method: 'POST', body: JSON.stringify(body) });
    sessionStorage.setItem('nc.token', data.token);
    startSession(data.token, data.user);
  } catch (e) { err.textContent = e.message; err.hidden = false; }
}
function startSession(token, user) {
  state.token = token; state.user = user; state.loggedIn = true;
  $('auth-view').hidden = true; $('app-view').hidden = false;
  $('whoami').textContent = user.username + (user.role === 'ADMIN' ? ' (admin)' : '');
  loadTemplates(); loadPrefs(); refresh(); connectWs();
}
function logout(message) {
  state.loggedIn = false; state.token = null; state.user = null;
  sessionStorage.removeItem('nc.token');
  clearTimeout(state.reconnectTimer);
  if (state.ws) { state.ws.h.onClose = () => {}; state.ws.close(); state.ws = null; }
  $('app-view').hidden = true; $('auth-view').hidden = false; $('dropdown').hidden = true;
  const err = $('auth-error'); err.textContent = message || ''; err.hidden = !message;
}
function userIdFromToken(token) {
  try { return JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).sub; } catch (e) { return null; }
}

/* ---------- websocket ---------- */
function setConn(kind, text) { const c = $('conn'); c.className = 'conn conn-' + kind; c.textContent = text; }
function connectWs() {
  if (!state.loggedIn) return;
  setConn('wait', 'connecting…');
  const proto = location.protocol === 'https:' ? 'wss://' : 'ws://';
  state.ws = new StompClient(proto + location.host + '/ws', { Authorization: 'Bearer ' + state.token }, {
    onConnected: () => {
      state.reconnectDelay = 1000;
      state.ws.subscribe('/user/queue/notifications');
      setConn('on', 'live');
      refresh(); // anything created while we were offline is fetched from the durable store
    },
    onMessage: (body) => { try { onPush(JSON.parse(body)); } catch (e) { /* ignore malformed frame */ } },
    onError: (msg) => { setConn('off', 'rejected: ' + msg); },
    onClose: () => {
      if (!state.loggedIn) return;
      setConn('off', 'disconnected, retrying…');
      state.reconnectTimer = setTimeout(connectWs, state.reconnectDelay);
      state.reconnectDelay = Math.min(state.reconnectDelay * 2, 15000);
    },
  });
  state.ws.connect();
}
function onPush(n) {
  toast(n);
  state.ws.publish('/app/ack', { notificationId: n.id }); // lets the worker mark the delivery DELIVERED
  refresh();
}

/* ---------- rendering ---------- */
function fmt(iso) { return new Date(iso).toLocaleString(); }
function renderItem(n, withActions) {
  const li = document.createElement('li');
  li.className = 'nitem ' + (n.read ? 'read' : 'unread');
  const dot = document.createElement('span'); dot.className = 'dot ' + n.category; li.appendChild(dot);
  const box = document.createElement('div'); box.className = 'nbody';
  const s = document.createElement('div'); s.className = 'nsubject'; s.textContent = n.subject; box.appendChild(s);
  const t = document.createElement('div'); t.className = 'ntext'; t.textContent = n.body; box.appendChild(t);
  const m = document.createElement('div'); m.className = 'nmeta';
  for (const part of [fmt(n.createdAt), n.category, n.status]) { const c = document.createElement('span'); c.className = 'chip'; c.textContent = part; m.appendChild(c); }
  if (n.archived) { const c = document.createElement('span'); c.className = 'chip'; c.textContent = 'ARCHIVED'; m.appendChild(c); }
  box.appendChild(m); li.appendChild(box);
  if (withActions) {
    const a = document.createElement('div'); a.className = 'nactions';
    if (!n.read) { const b = document.createElement('button'); b.textContent = 'Mark read'; b.onclick = (e) => { e.stopPropagation(); markRead(n.id); }; a.appendChild(b); }
    if (!n.archived) { const d = document.createElement('button'); d.textContent = 'Archive'; d.onclick = (e) => { e.stopPropagation(); archive(n.id); }; a.appendChild(d); }
    li.appendChild(a);
  }
  li.onclick = () => { if (!n.read) markRead(n.id); };
  return li;
}
async function refresh() { await Promise.all([loadUnread(), loadLatest(), loadHistory()]); }
async function loadUnread() {
  try { const r = await api('/api/v1/notifications/unread-count'); const b = $('badge'); b.textContent = r.count > 99 ? '99+' : r.count; b.hidden = r.count === 0; } catch (e) { /* shown elsewhere */ }
}
async function loadLatest() {
  try {
    const p = await api('/api/v1/notifications?page=0&size=8');
    const list = $('dd-list'); list.replaceChildren(...p.content.map((n) => renderItem(n, false)));
    $('dd-empty').hidden = p.content.length > 0;
  } catch (e) { /* ignore */ }
}
async function loadHistory() {
  const st = $('history-state'); const list = $('history-list');
  st.hidden = false; st.textContent = 'Loading…';
  const q = new URLSearchParams({ page: state.page, size: state.size, status: $('f-status').value, includeArchived: $('f-archived').checked });
  if ($('f-category').value) q.set('category', $('f-category').value);
  try {
    const p = await api('/api/v1/notifications?' + q);
    state.totalPages = p.totalPages;
    list.replaceChildren(...p.content.map((n) => renderItem(n, true)));
    st.hidden = p.content.length > 0; st.textContent = 'No notifications match these filters.';
    $('page-info').textContent = `Page ${p.page + 1} of ${Math.max(p.totalPages, 1)} (${p.totalElements} total)`;
    $('prev').disabled = p.page <= 0; $('next').disabled = p.page + 1 >= p.totalPages;
  } catch (e) { list.replaceChildren(); st.hidden = false; st.textContent = 'Could not load notifications: ' + e.message; }
}
async function markRead(id) { try { await api(`/api/v1/notifications/${id}/read`, { method: 'PATCH' }); refresh(); } catch (e) { toastText(e.message, 'ERROR'); } }
async function archive(id) { try { await api(`/api/v1/notifications/${id}`, { method: 'DELETE' }); refresh(); } catch (e) { toastText(e.message, 'ERROR'); } }
async function readAll() { try { await api('/api/v1/notifications/read-all', { method: 'PATCH' }); refresh(); } catch (e) { toastText(e.message, 'ERROR'); } }

function toast(n) { toastText(n.subject, n.category); }
function toastText(text, category) {
  const el = document.createElement('div'); el.className = 'toast ' + (category || 'INFO'); el.textContent = text;
  $('toasts').appendChild(el); setTimeout(() => el.remove(), 5000);
}

/* ---------- preferences ---------- */
const PREF_FIELDS = { inAppEnabled: 'p-inApp', emailEnabled: 'p-email', infoEnabled: 'p-info', successEnabled: 'p-success', warningEnabled: 'p-warning', errorEnabled: 'p-error' };
async function loadPrefs() {
  try { const p = await api('/api/v1/preferences'); for (const [k, id] of Object.entries(PREF_FIELDS)) $(id).checked = !!p[k]; }
  catch (e) { $('p-msg').textContent = 'Could not load preferences: ' + e.message; }
}
async function savePrefs() {
  const body = {}; for (const [k, id] of Object.entries(PREF_FIELDS)) body[k] = $(id).checked;
  try { await api('/api/v1/preferences', { method: 'PUT', body: JSON.stringify(body) }); $('p-msg').textContent = 'Saved.'; }
  catch (e) { $('p-msg').textContent = 'Save failed: ' + e.message; }
}

/* ---------- developer panel ---------- */
async function loadTemplates() {
  try {
    state.templates = await api('/api/v1/templates');
    const sel = $('d-template');
    sel.replaceChildren(new Option('Custom message', ''));
    for (const t of state.templates) sel.appendChild(new Option(t.id + (t.mandatory ? ' (mandatory)' : ''), t.id));
  } catch (e) { $('d-msg').textContent = 'Could not load templates: ' + e.message; }
}
function onTemplateChange() {
  const t = state.templates.find((x) => x.id === $('d-template').value);
  $('d-custom').hidden = !!t;
  const box = $('d-vars'); box.replaceChildren();
  if (!t) return;
  for (const v of t.variables) {
    const label = document.createElement('label'); label.textContent = 'Variable: ' + v;
    const input = document.createElement('input'); input.dataset.var = v; input.maxLength = 500; label.appendChild(input); box.appendChild(label);
  }
}
async function sendNotification() {
  const msg = $('d-msg'); msg.textContent = 'Sending…';
  const channels = []; if ($('d-inapp').checked) channels.push('IN_APP'); if ($('d-email').checked) channels.push('EMAIL');
  const recipients = $('d-recipients').value.split(',').map((s) => s.trim()).filter(Boolean);
  const body = { channels };
  if (recipients.length) body.recipients = recipients;
  const tpl = $('d-template').value;
  if (tpl) { body.templateId = tpl; body.variables = {}; document.querySelectorAll('#d-vars input').forEach((i) => { body.variables[i.dataset.var] = i.value; }); }
  else { body.subject = $('d-subject').value; body.body = $('d-body').value; body.category = $('d-category').value; }
  const headers = {}; const key = $('d-key').value.trim(); if (key) headers['Idempotency-Key'] = key;
  try {
    const r = await api('/api/v1/notifications', { method: 'POST', headers, body: JSON.stringify(body) });
    msg.textContent = r.duplicate ? 'Duplicate request: returned the notifications created earlier (nothing new created).'
      : `Created ${r.notifications.length} notification(s). Status: ${r.notifications.map((n) => n.status).join(', ')}.`;
  } catch (e) { msg.textContent = 'Failed: ' + e.message; }
}

/* ---------- wiring ---------- */
function showView(name) {
  for (const v of ['history', 'prefs', 'dev']) $('view-' + v).hidden = v !== name;
  document.querySelectorAll('.main-tabs .tab').forEach((t) => t.classList.toggle('active', t.dataset.view === name));
}
$('tab-login').onclick = () => setAuthMode(false);
$('tab-register').onclick = () => setAuthMode(true);
$('auth-submit').onclick = submitAuth;
$('auth-password').addEventListener('keydown', (e) => { if (e.key === 'Enter') submitAuth(); });
$('logout').onclick = () => logout();
$('bell').onclick = () => { const d = $('dropdown'); d.hidden = !d.hidden; $('bell').setAttribute('aria-expanded', String(!d.hidden)); };
document.addEventListener('click', (e) => { if (!e.target.closest('.bell-wrap')) $('dropdown').hidden = true; });
$('dd-readall').onclick = readAll;
document.querySelectorAll('.main-tabs .tab').forEach((t) => { t.onclick = () => showView(t.dataset.view); });
for (const id of ['f-category', 'f-status', 'f-archived']) $(id).onchange = () => { state.page = 0; loadHistory(); };
$('f-reload').onclick = () => refresh();
$('prev').onclick = () => { if (state.page > 0) { state.page--; loadHistory(); } };
$('next').onclick = () => { if (state.page + 1 < state.totalPages) { state.page++; loadHistory(); } };
$('p-save').onclick = savePrefs;
$('d-template').onchange = onTemplateChange;
$('d-send').onclick = sendNotification;

(function boot() {
  const token = sessionStorage.getItem('nc.token');
  const sub = token && userIdFromToken(token);
  if (token && sub) {
    // Validate the stored token by calling the API; falls back to the login screen if it expired.
    state.token = token;
    api('/api/v1/preferences').then(() => {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      startSession(token, { id: payload.sub, username: payload.username, role: payload.role });
    }).catch(() => { state.token = null; sessionStorage.removeItem('nc.token'); });
  }
})();
