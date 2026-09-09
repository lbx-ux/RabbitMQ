'use strict';
/* 消息可靠性页：本地消息表（发送端 Confirm）+ 错误消息（消费端兜底）+ 三张幂等表 */
async function loadAll() {
  try {
    const [lms, errs, recs] = await Promise.all([
      api(PUB, "/local-messages"), api(CON, "/error/list"), api(CON, "/consume/records"),
    ]);
    renderLms(lms);
    renderErrs(errs);
    renderIdem(recs.consumed);
    $("liveBadge").classList.remove("err");
    $("liveText").textContent = "已同步 " + new Date().toLocaleTimeString("zh-CN", {hour12: false});
  } catch (e) {
    $("liveBadge").classList.add("err");
    $("liveText").textContent = "服务未就绪：" + e.message;
  }
}

function renderLms(lms) {
  $("lmBody").innerHTML = lms.length ? lms.slice(0, 30).map(m =>
    '<tr title="' + esc(m.content) + '"><td class="mono">' + esc(m.messageId) + '</td>'
    + '<td>' + badge(LM_STATUS, m.status) + '</td>'
    + '<td class="num">' + (m.retryCount ?? 0) + '</td></tr>').join("")
    : '<tr><td colspan="3" class="empty">暂无 — 支付一笔后出现，看 SENDING → CONFIRMED 流转</td></tr>';
  $("lmStatusFine").textContent = lms.some(m => m.status === 0)
    ? "发送中(SENDING)说明 Confirm 回执未到，30s 后补偿任务会扫描重发。" : "";
}

function renderErrs(errs) {
  $("errBody").innerHTML = errs.length ? errs.map(e => {
    let orderNo = "";
    try { orderNo = JSON.parse(e.content).orderNo || ""; } catch (_) {}
    return '<tr><td class="num">' + e.id + '</td>'
      + '<td class="mono">' + esc(orderNo) + '</td>'
      + '<td title="' + esc(e.failReason) + '" class="reason">' + esc(e.failReason) + '</td>'
      + '<td>' + (e.status === 'PENDING' ? '<span class="badge b-warn">PENDING</span>' : '<span class="badge b-dim">REPLAYED</span>') + '</td>'
      + '<td>' + (e.status === 'PENDING' ? '<button class="btn" data-replay="' + e.id + '">重放</button>' : '') + '</td></tr>';
  }).join("") : '<tr><td colspan="5" class="empty">暂无 — 去「业务链路」页用黑名单手机号 13800000000 支付即可触发</td></tr>';
}

function renderIdem(recs) {
  $("idemBody").innerHTML = recs.length ? recs.slice(0, 30).map(r =>
    '<tr><td class="mono">' + esc(r.messageId) + '</td>'
    + '<td class="num">' + esc((r.consumeTime || "").replace("T", " ").slice(0, 19)) + '</td></tr>').join("")
    : '<tr><td colspan="2" class="empty">暂无 — 每条消息消费成功后落一条记录</td></tr>';
}

/* ---------- 重放按钮 ---------- */
document.addEventListener("click", async e => {
  const b = e.target.closest("button[data-replay]");
  if (!b) return;
  busy(b, true);
  try {
    log(await api(CON, "/error/replay/" + b.dataset.replay, {method: "POST"}), "log-ok");
    log("重放消息已发回原交换机，去「消费结果」页看重新消费的结果", "log-info");
    await loadAll();
  } catch (e2) { log("重放失败：" + e2.message, "log-bad"); busy(b, false); }
});

$("btnCompensate").addEventListener("click", async () => {
  try { log(await api(PUB, "/compensate", {method: "POST"}), "log-ok"); await loadAll(); }
  catch (e) { log("补偿失败：" + e.message, "log-bad"); }
});

loadAll();
setInterval(loadAll, 3000);
