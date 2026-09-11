# -*- coding: utf-8 -*-
"""把 mq-note/*.md 转成 notes/1.html~6.html + notes.html，套用实验台同款导航。
每篇笔记带左侧目录（h2/h3 自动生成），点击跳转，滚动时高亮当前小节。

用法：在 mq-frontend 目录下执行  python tools_build_notes.py
依赖：pip install markdown
"""
import io, os, re, shutil, markdown

NOTES = [
    ('1.RabbitMQ.md',       '1', 'RabbitMQ',          '部署 · 收发消息 · 数据隔离'),
    ('2.RabbitMQ基础.md',    '2', 'RabbitMQ 基础',     '三大核心 · 交换机 · 消息转换器'),
    ('3.数据持久化.md',      '3', '数据持久化',        '持久化 · LazyQueue · Quorum'),
    ('4.生产者的可靠性.md',  '4', '生产者的可靠性',    '重试 · Confirm/Return'),
    ('5.本地消息表.md',      '5', '本地消息表',        '分布式事务 · Confirm闭环 · 定时补偿'),
    ('6.消费者的可靠性.md',  '6', '消费者的可靠性',    '确认 · 重试 · 死信兜底 · 幂等'),
    ('7.延迟消息.md',        '7', '延迟消息',          '死信交换机 · 延迟插件'),
]

ROOT = os.path.dirname(os.path.abspath(__file__))   # mq-frontend 目录
NOTE_DIR = os.path.join(os.path.dirname(ROOT), 'mq-note')


def nav_html(active, prefix=''):
    """主导航。notes/N.html 在子目录，其余页面在根目录。"""
    def a(href, label):
        cls = ' class="on"' if active == href else ''
        return '<a href="%s%s"%s>%s</a>' % (prefix, href, cls, label)
    return ('<nav class="nav" aria-label="主导航">' + a('index.html', '总览')
            + a('trade.html', '业务链路') + a('reliability.html', '消息可靠性')
            + a('consume.html', '消费结果') + a('notes.html', '学习笔记') + '</nav>')


def topbar(active, prefix=''):
    return ('<header class="topbar">\n  <div class="brand">\n'
            '    <span class="brand-mark" aria-hidden="true"></span>\n    <div>\n'
            '      <h1>MQ 实验台</h1>\n'
            '    </div>\n  </div>\n  ' + nav_html(active, prefix) +
            '\n  <div class="topbar-side">\n'
            '    <a class="mgmt" href="http://192.168.146.130:15672" target="_blank" rel="noopener">管理台</a>\n'
            '  </div>\n</header>')


def head(title, css_path):
    return ('<!DOCTYPE html>\n<html lang="zh-CN">\n<head>\n<meta charset="UTF-8">\n'
            '<meta name="viewport" content="width=device-width, initial-scale=1">\n'
            '<title>' + title + ' · MQ 实验台</title>\n'
            '<link rel="preconnect" href="https://fonts.googleapis.com">\n'
            '<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>\n'
            '<link rel="stylesheet" media="print" onload="this.media=\'all\'"\n'
            '      href="https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,500;12..96,700&family=IBM+Plex+Mono:wght@400;500&display=swap">\n'
            '<link rel="stylesheet" href="' + css_path + '">\n</head>\n<body>\n')


def convert(md_text):
    return markdown.markdown(md_text, extensions=['fenced_code', 'tables', 'nl2br', 'sane_lists'])


def build_toc(body):
    """给正文 h2/h3 加 id，生成左侧目录；返回 (带id的body, 目录html)。"""
    entries = []
    counter = [0]

    def tag(m):
        lvl, inner = m.group(1), m.group(2)
        counter[0] += 1
        hid = 'sec-%d' % counter[0]
        label = re.sub(r'<[^>]+>', '', inner).strip()
        entries.append((lvl, hid, label))
        return '<h%s id="%s">%s</h%s>' % (lvl, hid, inner, lvl)

    body2 = re.sub(r'<h([23])>(.*?)</h\1>', tag, body, flags=re.S)
    items = ''.join('<a class="lv%s" href="#%s">%s</a>' % (lv, hid, label)
                    for lv, hid, label in entries)
    if not items:
        return body2, ''
    toc = ('<aside class="toc" aria-label="目录"><div class="toc-title">目录</div>'
           + items + '</aside>')
    return body2, toc


# 滚动监听：高亮当前阅读到的小节
SCROLLSPY = (
    '<script>\n'
    '(function(){\n'
    '  var links=[].slice.call(document.querySelectorAll(\'.toc a[href^="#"]\'));\n'
    '  if(!links.length)return;\n'
    '  var map={};links.forEach(function(a){map[a.getAttribute("href").slice(1)]=a;});\n'
    '  var heads=[].slice.call(document.querySelectorAll(\'.md-body h2[id],.md-body h3[id]\'));\n'
    '  var tops=[];\n'
    '  function measure(){tops=heads.map(function(h){return h.getBoundingClientRect().top+window.scrollY;});}\n'
    '  function update(){\n'
    '    var y=window.scrollY+120,cur=null;\n'
    '    for(var i=0;i<heads.length;i++){if(tops[i]<=y)cur=heads[i].id;}\n'
    '    links.forEach(function(a){a.classList.remove("cur");});\n'
    '    if(cur&&map[cur])map[cur].classList.add("cur");\n'
    '  }\n'
    '  addEventListener("scroll",update,{passive:true});\n'
    '  addEventListener("resize",function(){measure();update();});\n'
    '  measure();update();\n'
    '})();\n</script>\n')


os.makedirs(os.path.join(ROOT, 'notes'), exist_ok=True)

# ---- 图片：把 mq-note/images/ 同步到 notes/images/（网页版相对路径一致，直接整目录拷贝）----
src_img = os.path.join(NOTE_DIR, 'images')
dst_img = os.path.join(ROOT, 'notes', 'images')
if os.path.isdir(src_img):
    if os.path.isdir(dst_img):
        shutil.rmtree(dst_img)
    shutil.copytree(src_img, dst_img)
    print('images synced:', len(os.listdir(dst_img)))

for i, (fname, num, title, sub) in enumerate(NOTES):
    md = io.open(os.path.join(NOTE_DIR, fname), encoding='utf-8').read()
    body, toc = build_toc(convert(md))
    if i > 0:
        p = NOTES[i - 1]
        prev_html = '<a class="note" href="%s.html">← 上一篇：%s. %s</a>' % (p[1], p[1], p[2])
    else:
        prev_html = '<span></span>'
    if i < len(NOTES) - 1:
        n = NOTES[i + 1]
        nxt = '<a class="note" href="%s.html">下一篇：%s. %s →</a>' % (n[1], n[1], n[2])
    else:
        nxt = '<span></span>'
    page = (head(title, '../css/style.css')
            + topbar('notes.html', prefix='../')
            + '\n<div class="note-layout">\n'
            + (toc + '\n' if toc else '')
            + '<div class="md-wrap">\n'
              '  <div class="md-head">\n    <h2>《' + num + '. ' + title + '》</h2>\n'
              '    <p class="fine">' + sub + '</p>\n  </div>\n'
              '  <article class="md-body">\n' + body + '\n  </article>\n'
              '  <div class="pn">' + prev_html + nxt + '</div>\n</div>\n</div>\n'
              '<footer class="page-foot"><span>mq-note / ' + fname + '</span>'
              '<span>MQ 实验台 · 静态渲染</span></footer>\n'
            + SCROLLSPY
            + '</body>\n</html>\n')
    out = os.path.join(ROOT, 'notes', num + '.html')
    io.open(out, 'w', encoding='utf-8', newline='\n').write(page)
    print('OK', out, len(page), 'toc entries:', toc.count('<a '))

# ---- 列表页 notes.html（在根目录，css 不带 ../）----
cards = ''
for fname, num, title, sub in NOTES:
    cards += ('  <a class="guide" href="notes/' + num + '.html"><h3>' + num + '. ' + title + '</h3>'
              '<span class="step"><i>▸</i><span>' + sub + '</span></span></a>\n')
list_page = (head('学习笔记', 'css/style.css')
             + topbar('notes.html')
             + '\n<div class="sec"><h2>配套笔记</h2><span class="fine">mq-note 目录 7 篇 · 与代码注释互相引用</span></div>\n'
               '<div class="guides" style="grid-template-columns:repeat(3,1fr)">\n' + cards + '</div>\n'
               '<footer class="page-foot"><span>mq-note / 7 篇学习笔记</span>'
               '<span>MQ 实验台 · 静态渲染</span></footer>\n</body>\n</html>\n')
io.open(os.path.join(ROOT, 'notes.html'), 'w', encoding='utf-8', newline='\n').write(list_page)
print('OK notes.html', len(list_page))
