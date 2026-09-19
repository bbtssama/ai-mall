/* ==========================================================================
   ai-mall 开发手册 · 共享脚本
   零依赖 / file:// 可直接运行（不使用 fetch，导航数据内联为 JS 变量）
   职责：注入侧边栏、生成页内目录、全展开/收起、回到顶部
   ========================================================================== */

/* --------------------------------------------------------------------------
   ★ 导航的唯一权威定义。新增页面只改这里。
   -------------------------------------------------------------------------- */
var HANDBOOK_NAV = [
  { group: '开始', items: [
    { id: 'index', file: 'index.html', num: '起', title: '驾驶舱', sub: '5 分钟建立全局印象' }
  ]},
  { group: '一 · 结构与业务链路', items: [
    { id: '01', file: '01-全景与架构.html', num: '01', title: '全景与架构', sub: '项目是什么 / 演进史 / 域划分 / 一次请求的旅程' },
    { id: '02', file: '02-用户与商品.html', num: '02', title: '用户与商品', sub: 'Sa-Token 鉴权 / SKU / 购物车' },
    { id: '03', file: '03-订单与交易.html', num: '03', title: '订单与交易', sub: '防超卖 / 支付闭环 / 限量发售' },
    { id: '04', file: '04-内容社区.html', num: '04', title: '内容社区', sub: 'Feed 游标分页 / 点赞 / 全文检索' }
  ]},
  { group: '二 · 技术主题（面试王牌）', items: [
    { id: '05', file: '05-高并发与可靠性.html', num: '05', title: '高并发与可靠性', sub: '缓存三兄弟 / 幂等 / 削峰 / MQ 可靠性 / 降级' }
  ]},
  { group: '三 · 扩展与了解层', items: [
    { id: '06', file: '06-AI模块.html', num: '06', title: 'AI 模块', sub: 'Function Calling / RAG / TTS' },
    { id: '07', file: '07-工程基建与服务化.html', num: '07', title: '工程基建与服务化', sub: 'Flyway / traceId / 网关 / Nacos / 熔断' }
  ]},
  { group: '四 · 冲刺', items: [
    { id: '08', file: '08-面试速查与自查.html', num: '08', title: '面试速查与自查', sub: '口径红线 / 弹药库 / 技术债' }
  ]}
];

/* 页码 → 扁平顺序，用于「上一页 / 下一页」 */
function handbookFlatNav() {
  var out = [];
  HANDBOOK_NAV.forEach(function (g) { g.items.forEach(function (it) { out.push(it); }); });
  return out;
}

/* --------------------------------------------------------------------------
   侧边栏
   -------------------------------------------------------------------------- */
function renderSidebar() {
  var host = document.getElementById('sidebar');
  if (!host) return;
  var cur = document.body.getAttribute('data-page') || '';

  var html = ''
    + '<div class="sb-brand">'
    +   '<div class="t">AI 种草商城 · 开发手册</div>'
    +   '<div class="s">ai-mall / 初级 Java 后端求职</div>'
    + '</div>';

  HANDBOOK_NAV.forEach(function (g) {
    html += '<div class="sb-group">' + g.group + '</div>';
    g.items.forEach(function (it) {
      var active = (it.id === cur) ? ' active' : '';
      html += '<a class="sb-item' + active + '" href="' + it.file + '">'
            +   '<span class="sb-num">' + it.num + '</span>'
            +   '<span class="sb-txt"><span class="a">' + it.title + '</span>'
            +   '<span class="b">' + it.sub + '</span></span>'
            + '</a>';
    });
  });

  host.innerHTML = html;
}

/* --------------------------------------------------------------------------
   页内目录（自动抓 h2，写入 .toc 容器）
   -------------------------------------------------------------------------- */
function slugify(s) {
  return s.trim().toLowerCase()
          .replace(/[\s\u3000]+/g, '-')
          .replace(/[^\w\u4e00-\u9fa5\-]/g, '')
          .replace(/-+/g, '-').replace(/^-|-$/g, '') || 'sec';
}

function buildToc() {
  var host = document.querySelector('.toc');
  if (!host) return;
  var scope = host.getAttribute('data-scope');
  var root = scope ? document.querySelector(scope) : document.querySelector('.main');
  if (!root) return;

  var hs = root.querySelectorAll('h2');
  if (hs.length < 2) { host.style.display = 'none'; return; }

  var used = {}, items = '';
  Array.prototype.forEach.call(hs, function (h) {
    var txt = h.textContent.replace(/\s+/g, ' ').trim();
    var id = h.id || slugify(txt);
    if (used[id]) { used[id]++; id = id + '-' + used[id]; } else { used[id] = 1; }
    h.id = id;
    items += '<li><a href="#' + id + '">' + txt + '</a></li>';
  });

  var head = host.querySelector('.toc-h');
  host.innerHTML = (head ? head.outerHTML : '<div class="toc-h">本页目录</div>')
                 + '<ol>' + items + '</ol>';
}

/* --------------------------------------------------------------------------
   上一页 / 下一页
   -------------------------------------------------------------------------- */
function renderPageNav() {
  var host = document.querySelector('.page-nav');
  if (!host) return;
  var flat = handbookFlatNav();
  var cur = document.body.getAttribute('data-page') || '';
  var i = -1;
  flat.forEach(function (it, k) { if (it.id === cur) i = k; });
  if (i < 0) { host.style.display = 'none'; return; }

  var prev = i > 0 ? flat[i - 1] : null;
  var next = i < flat.length - 1 ? flat[i + 1] : null;
  var html = '';

  if (prev) {
    html += '<a class="prev" href="' + prev.file + '">'
          +   '<div class="pn-dir">上一页</div><div class="pn-t">' + prev.title + '</div></a>';
  } else { html += '<span style="flex:1"></span>'; }

  if (next) {
    html += '<a class="next" href="' + next.file + '">'
          +   '<div class="pn-dir">下一页</div><div class="pn-t">' + next.title + '</div></a>';
  }
  host.innerHTML = html;
}

/* --------------------------------------------------------------------------
   工具条：展开全部 / 收起全部 / 只看结论
   -------------------------------------------------------------------------- */
function renderToolbar() {
  var host = document.querySelector('.toolbar');
  if (!host) return;

  var isTop = document.body.getAttribute('data-page') === 'index';

  /* 只有本页确实存在可折叠的技术点时，才提供展开/收起 */
  if (document.querySelectorAll('details.lvl').length > 0) {
    var b1 = document.createElement('button');
    b1.className = 'btn'; b1.type = 'button'; b1.textContent = '展开全部明细';
    b1.onclick = function () {
      document.querySelectorAll('details.lvl').forEach(function (d) { d.open = true; });
    };

    var b2 = document.createElement('button');
    b2.className = 'btn'; b2.type = 'button'; b2.textContent = '收起全部（只看结论）';
    b2.onclick = function () {
      document.querySelectorAll('details.lvl').forEach(function (d) { d.open = false; });
      window.scrollTo({ top: 0, behavior: 'smooth' });
    };

    host.appendChild(b1); host.appendChild(b2);
  }

  /* 无任何按钮时隐藏整条工具条，避免留空白 */
  if (host.children.length === 0) { host.style.display = 'none'; return; }

  if (!isTop) {
    var b3 = document.createElement('button');
    b3.className = 'btn'; b3.type = 'button'; b3.textContent = '回到本页目录';
    b3.onclick = function () {
      var t = document.querySelector('.toc') || document.querySelector('.page-head');
      if (t) window.scrollTo({ top: t.offsetTop - 16, behavior: 'smooth' });
    };
    host.appendChild(b3);
  }
}

/* --------------------------------------------------------------------------
   回到顶部
   -------------------------------------------------------------------------- */
function renderTopButton() {
  var btn = document.createElement('button');
  btn.className = 'totop'; btn.type = 'button'; btn.title = '回到顶部';
  btn.textContent = '↑';
  btn.onclick = function () { window.scrollTo({ top: 0, behavior: 'smooth' }); };
  document.body.appendChild(btn);

  window.addEventListener('scroll', function () {
    if (window.scrollY > 400) { btn.classList.add('show'); } else { btn.classList.remove('show'); }
  }, { passive: true });
}

/* --------------------------------------------------------------------------
   移动端菜单
   -------------------------------------------------------------------------- */
function renderMenuButton() {
  var btn = document.createElement('button');
  btn.className = 'menu-btn'; btn.type = 'button'; btn.textContent = '≡';
  btn.onclick = function () {
    var sb = document.getElementById('sidebar');
    if (sb) sb.classList.toggle('open');
  };
  document.body.appendChild(btn);

  document.addEventListener('click', function (e) {
    var sb = document.getElementById('sidebar');
    if (!sb) return;
    if (e.target.closest && e.target.closest('.sb-item')) { sb.classList.remove('open'); }
  });
}

/* --------------------------------------------------------------------------
   启动
   -------------------------------------------------------------------------- */
document.addEventListener('DOMContentLoaded', function () {
  renderSidebar();
  buildToc();
  renderToolbar();
  renderPageNav();
  renderTopButton();
  renderMenuButton();
});
