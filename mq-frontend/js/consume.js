'use strict';
/* 消费结果页：积分流水 / 短信记录 / 幂等记录 三张表 */
async function loadAll() {
  try {
    const recs = await api(CON, "/consume/records");
    renderPoints(recs.points);
    renderSms(recs.sms);
    renderIdem(recs.consumed);
    $("liveBadge").classList.remove("err");
    $("liveText").textContent = "已同步 " + new Date().toLocaleTimeString("zh-CN", {hour12: false});
  } catch (e) {
    $("liveBadge").classList.add("err");
    $("liveText").textContent = "服务未就绪：" + e.message;
  }
}

function renderPoints(rows) {
  $("ptBody").innerHTML = rows.length ? rows.map(r =>
    '<tr><td class="mono">' + esc(r.orderNo) + '</td>'
    + '<td class="num" style="color:' + (r.points >= 0 ? "var(--green)" : "var(--red)") + '">'
    + (r.points > 0 ? "+" : "") + r.points + '</td>'
    + '<td>' + esc(r.type) + '</td></tr>').join("")
    : '<tr><td colspan="3" class="empty">暂无 — 支付后积分服务 +100</td></tr>';
}

function renderSms(rows) {
  $("smsBody").innerHTML = rows.length ? rows.map(r =>
    '<tr><td class="mono">' + esc(r.mobile) + '</td>'
    + '<td>' + (r.status === 'SENT' ? '<span class="badge b-ok">SENT</span>' : '<span class="badge b-bad">FAILED</span>') + '</td>'
    + '<td class="reason" title="' + esc(r.content) + '">' + esc(r.content) + '</td></tr>').join("")
    : '<tr><td colspan="3" class="empty">暂无 — 黑名单手机号会 FAILED 并重试 3 次</td></tr>';
}

function renderIdem(rows) {
  $("idemBody").innerHTML = rows.length ? rows.slice(0, 30).map(r =>
    '<tr><td class="mono">' + esc(r.messageId) + '</td>'
    + '<td class="num">' + esc((r.consumeTime || "").replace("T", " ").slice(0, 19)) + '</td></tr>').join("")
    : '<tr><td colspan="2" class="empty">暂无 — 每条消息消费成功后落一条记录</td></tr>';
}

loadAll();
setupPolling(loadAll);
