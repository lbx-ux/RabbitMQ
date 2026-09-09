'use strict';
/* 总览页：只做 KPI 轮询 + 连接状态（拓扑图和引导卡片是静态的） */
let pickedItem = null;
async function loadAll() {
  try {
    const [orders, lms, errs] = await Promise.all([
      api(PUB, "/orders"), api(PUB, "/local-messages"), api(CON, "/error/list"),
    ]);
    $('kpiPending').textContent  = orders.filter(o => o.status === 0).length;
    $('kpiPaid').textContent     = orders.filter(o => o.status === 1).length;
    $('kpiMsgErr').textContent   = errs.filter(e => e.status === 'PENDING').length;
    $('kpiMsgRetry').textContent = lms.filter(m => m.status === 0 || m.status === 2).length;
    $('liveBadge').classList.remove('err');
    $('liveText').textContent = '已同步 ' + new Date().toLocaleTimeString('zh-CN', {hour12: false});
  } catch (e) {
    $('liveBadge').classList.add('err');
    $('liveText').textContent = '服务未就绪：' + e.message;
  }
}
loadAll();
setInterval(loadAll, 3000);