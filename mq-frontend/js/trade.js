'use strict';
/* 业务链路页：下单 / 支付 / 退款 / 订单列表 / 本地消息表 */
let PRODUCTS = [];
let pickedItem = null;

async function loadAll() {
  try {
    const [products, orders, lms] = await Promise.all([
      api(PUB, "/products"), api(PUB, "/orders"), api(PUB, "/local-messages"),
    ]);
    PRODUCTS = products;
    renderProducts(products);
    if (pickedItem == null) {
      const ok = products.find(p => p.stock > 0);
      pickedItem = ok ? ok.id : (products[0] && products[0].id);
    }
    renderPicked();
    renderOrders(orders, products);
    renderLms(lms);
    $("liveBadge").classList.remove("err");
    $("liveText").textContent = "已同步 " + new Date().toLocaleTimeString("zh-CN", {hour12: false});
  } catch (e) {
    $("liveBadge").classList.add("err");
    $("liveText").textContent = "服务未就绪：" + e.message;
  }
}

function renderProducts(products) {
  $("productRow").innerHTML = products.map(p =>
    '<button type="button" class="pcard ' + (p.stock <= 0 ? "off" : "") + '" data-item="' + p.id + '"'
    + (p.stock <= 0 ? " disabled" : "") + '><b>' + esc(p.name) + '</b><span>' + money(p.price)
    + ' · 库存 ' + p.stock + '</span></button>').join("");
}

function renderPicked() {
  document.querySelectorAll(".pcard").forEach(b => {
    const on = Number(b.dataset.item) === pickedItem;
    b.classList.toggle("on", on);
    b.setAttribute("aria-pressed", on);
  });
}

function renderOrders(orders, products) {
  $("orderBody").innerHTML = orders.length ? orders.map(o => {
    const p = products.find(x => x.id === o.itemId);
    return '<tr data-no="' + esc(o.orderNo) + '" title="点击填入订单号" style="cursor:pointer">'
      + '<td class="mono">' + esc(o.orderNo) + '</td>'
      + '<td>' + esc(p ? p.name : o.itemId) + ' ×' + o.count + '</td>'
      + '<td class="num">' + money(o.totalAmount) + '</td>'
      + '<td>' + badge(ORDER_STATUS, o.status) + '</td></tr>';
  }).join("") : '<tr><td colspan="4" class="empty">还没有订单 — 选商品点「下单」，10 秒内不支付可看自动取消</td></tr>';
}

function renderLms(lms) {
  $("lmBody").innerHTML = lms.length ? lms.slice(0, 30).map(m =>
    '<tr title="' + esc(m.content) + '"><td class="mono">' + esc(m.messageId) + '</td>'
    + '<td>' + badge(LM_STATUS, m.status) + '</td><td class="num">' + (m.retryCount ?? 0) + '</td></tr>').join("")
    : '<tr><td colspan="3" class="empty">暂无消息 — 支付一笔后出现，看 SENDING → CONFIRMED 的流转</td></tr>';
  $("lmStatusFine").textContent = lms.some(m => m.status === 0)
    ? "发送中(SENDING)说明 Confirm 回执未到，30s 后补偿任务会扫描重发。" : "";
}

/* ---------- 交互 ---------- */
document.addEventListener("click", e => {
  const card = e.target.closest(".pcard");
  if (card) { pickedItem = Number(card.dataset.item); renderPicked(); return; }
  const tr = e.target.closest("tr[data-no]");
  if (tr) { $("fOrderNo").value = tr.dataset.no; queryOrder(); }
});

$("btnOrder").addEventListener("click", async () => {
  const btn = $("btnOrder"); busy(btn, true);
  try {
    const o = await api(PUB, "/order?userId=" + $("fUser").value + "&itemId=" + pickedItem + "&count=" + $("fCount").value, {method: "POST"});
    log("下单成功 " + o.orderNo + " — 10 秒内不支付将自动取消（延迟消息已发）", "log-ok");
    $("fOrderNo").value = o.orderNo;
    await loadAll();
  } catch (e) { log("下单失败：" + e.message, "log-bad"); }
  finally { busy(btn, false); }
});

$("btnPay").addEventListener("click", async () => {
  const no = $("fOrderNo").value.trim();
  if (!no) return log("先填订单号（或点订单表一行）", "log-warn");
  const btn = $("btnPay"); busy(btn, true);
  try {
    const r = await api(PUB, "/pay/" + no + "?userId=" + $("fUser").value + "&mobile=" + $("fMobile").value, {method: "POST"});
    log("支付成功，消息 " + r.messageId + " 已投递 — 本地消息表 → Confirm → 三服务异步消费", "log-ok");
    if ($("fMobile").value === "13800000000") log("黑名单手机号：短信服务将重试 3 次（1s/2s/4s）后进 error.queue", "log-warn");
    await loadAll();
  } catch (e) { log("支付失败：" + e.message, "log-bad"); }
  finally { busy(btn, false); }
});

$("btnRefund").addEventListener("click", async () => {
  const no = $("fOrderNo").value.trim();
  if (!no) return log("先填订单号", "log-warn");
  try { log("退款：" + await api(PUB, "/refund/" + no, {method: "POST"}), "log-ok"); await loadAll(); }
  catch (e) { log("退款失败：" + e.message, "log-bad"); }
});

async function queryOrder() {
  const no = $("fOrderNo").value.trim();
  if (!no) return;
  try { $("orderDetail").textContent = JSON.stringify(await api(PUB, "/order/" + no), null, 2); }
  catch (e) { $("orderDetail").textContent = "查询失败：" + e.message; }
}
$("btnQuery").addEventListener("click", queryOrder);

$("btnCompensate").addEventListener("click", async () => {
  try { log(await api(PUB, "/compensate", {method: "POST"}), "log-ok"); await loadAll(); }
  catch (e) { log("补偿失败：" + e.message, "log-bad"); }
});

loadAll();
setInterval(loadAll, 3000);
