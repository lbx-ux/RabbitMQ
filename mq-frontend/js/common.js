/* ============================================================
   MQ 实验台 —— 公共逻辑：后端地址、请求封装、日志、徽章工具
   publisher :8080（业务接口） / consumer :8083（消费结果+错误管理）
   ============================================================ */
'use strict';

const PUB = 'http://localhost:8080';
const CON = 'http://localhost:8083';
const $ = id => document.getElementById(id);

/* ---------- 日志面板（每页底部都有） ---------- */
function log(msg, cls = '') {
  const box = $('logBox');
  if (!box) return;                    // 本页无日志面板时静默跳过
  const t = new Date().toLocaleTimeString('zh-CN', {hour12: false});
  const line = document.createElement('div');
  line.className = 'log-line';
  line.innerHTML = '<span class="t"></span><span class="' + cls + '"></span>';
  line.lastChild.textContent = msg;
  line.firstChild.textContent = t;
  box.prepend(line);
  while (box.children.length > 200) box.lastChild.remove();   // 上限 200 行防卡顿
}
const clearLog = () => { const b = $('logBox'); if (b) b.innerHTML = ''; };
document.addEventListener('DOMContentLoaded', () => {
  const btn = $('btnClearLog');
  if (btn) btn.addEventListener('click', clearLog);
});

/* ---------- 请求封装（统一错误提示） ---------- */
async function api(base, path, opts) {
  const res = await fetch(base + path, opts);
  const text = await res.text();
  let data = text;
  try { data = JSON.parse(text); } catch (_) {}
  if (!res.ok) {
    const msg = (data && data.message) ? data.message
      : (typeof data === 'string' ? data.slice(0, 200) : 'HTTP ' + res.status);
    throw new Error(msg);
  }
  return data;
}

/* ---------- 状态徽章 ---------- */
const ORDER_STATUS = {0:['b-warn','待支付'], 1:['b-ok','已支付'], 2:['b-bad','已取消'], 3:['b-info','已退款']};
const LM_STATUS    = {0:['b-warn','发送中'], 1:['b-ok','已确认'], 2:['b-bad','失败'], 3:['b-bad','死信']};
const badge = (map, s) => {
  const pair = map[s] || ['b-dim', s];
  return '<span class="badge ' + pair[0] + '">' + pair[1] + '</span>';
};
const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const money = v => '¥' + (v / 100).toFixed(2);
const busy = (btn, on) => { btn.disabled = on; };

/* ---------- 轮询控制：间隔用户可选（3s/10s/关闭），选择记忆在 localStorage ---------- */
const poll = {timer: null, ms: 3000};
function applyPoll(fn) {
  if (poll.timer) clearInterval(poll.timer);
  poll.timer = poll.ms > 0 ? setInterval(fn, poll.ms) : null;
  const sel = $('pollSel'), foot = $('footPoll');
  if (sel) sel.value = String(poll.ms);
  if (foot) foot.textContent = poll.ms > 0
    ? '数据每 ' + (poll.ms / 1000) + 's 自动刷新'
    : '自动刷新已关闭，点「刷新」更新';
}
function setupPolling(fn) {
  const saved = Number(localStorage.getItem('pollMs'));
  poll.ms = [3000, 10000, 0].includes(saved) ? saved : 3000;
  const sel = $('pollSel'), btn = $('btnRefresh');
  if (sel) sel.addEventListener('change', () => {
    poll.ms = Number(sel.value);
    localStorage.setItem('pollMs', sel.value);
    applyPoll(fn);
  });
  if (btn) btn.addEventListener('click', () => fn());
  applyPoll(fn);
}
