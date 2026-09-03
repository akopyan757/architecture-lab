#!/usr/bin/env python3
"""Публикация разборов q/*.md в Telegra.ph.

    python3 tools/md-to-telegraph.py q/smart-cast-*.md
    python3 tools/md-to-telegraph.py --dry q/smart-cast-what-is-checked.md

Зачем: репозиторий приватный, а пост в канале должен вести на читаемый текст.
Telegra.ph открывается внутри Телеграма нативно, без встроенного браузера.

Работает в два прохода: сначала создаёт недостающие страницы (чтобы узнать их
адреса), потом перезаливает все с проставленными ссылками друг на друга.
Соответствие «файл → страница» лежит в telegraph.json и коммитится: повторный
запуск правит существующую страницу через editPage, адрес не меняется.

Токен — в TELEGRAPH_TOKEN. Без него страницы можно создать, но нельзя править,
поэтому скрипт откажется работать вслепую.
"""
import json, os, re, sys, urllib.parse, urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAP_FILE = ROOT / "telegraph.json"
API = "https://api.telegra.ph/"
AUTHOR = "Kotlin Knowledge"

# Telegra.ph принимает узкий набор тегов: ни таблиц, ни h1/h2, ни вложенных
# блоков. Таблица разворачивается в список, ## → h3, ### → h4.
INLINE = re.compile(
    r'\[([^\]]+)\]\(([^)]+)\)'          # [текст](ссылка)
    r'|\*\*([^*]+)\*\*'                 # **жирный**
    r'|(?<!\*)\*([^*\n]+)\*(?!\*)'      # *курсив*
    r'|`([^`]+)`'                       # `код`
)


def api(method, **params):
    data = urllib.parse.urlencode(params).encode()
    with urllib.request.urlopen(API + method, data) as r:
        res = json.load(r)
    if not res.get("ok"):
        sys.exit(f"Telegraph {method}: {res.get('error')}")
    return res["result"]


def load_map():
    return json.loads(MAP_FILE.read_text()) if MAP_FILE.exists() else {}


def save_map(m):
    MAP_FILE.write_text(json.dumps(m, ensure_ascii=False, indent=2) + "\n")


def inline(text, src, pages):
    """Инлайн-разметка в узлы Telegraph."""
    out, pos = [], 0
    for m in INLINE.finditer(text):
        if m.start() > pos:
            out.append(text[pos:m.start()])
        label, href, bold, italic, code = m.groups()
        if label is not None:
            out.append(link_node(label, href, src, pages))
        elif bold is not None:
            out.append({"tag": "strong", "children": inline(bold, src, pages)})
        elif italic is not None:
            out.append({"tag": "em", "children": inline(italic, src, pages)})
        else:
            out.append({"tag": "code", "children": [code]})
        pos = m.end()
    if pos < len(text):
        out.append(text[pos:])
    return out or [""]


def link_node(label, href, src, pages):
    """Внешняя ссылка остаётся ссылкой. Ссылка на соседний разбор становится
    ссылкой на его страницу. Всё остальное (код, карта) — просто жирный текст:
    в приватную репу вести некуда."""
    if href.startswith("http"):
        return {"tag": "a", "attrs": {"href": href}, "children": inline(label, src, pages)}
    target = os.path.normpath(os.path.join(os.path.dirname(src), href.split("#")[0]))
    page = pages.get(target)
    if page:
        return {"tag": "a", "attrs": {"href": page["url"]}, "children": inline(label, src, pages)}
    return {"tag": "strong", "children": inline(label, src, pages)}


def convert(path, pages):
    src = str(Path(path).relative_to(ROOT))
    lines = Path(path).read_text().split("\n")
    title = re.sub(r"[`*]", "", lines[0].lstrip("# ")).strip()
    nodes, para, bullets, table = [], [], [], []
    ordered = False

    def flush():
        nonlocal para, bullets, table, ordered
        if para:
            nodes.append({"tag": "p", "children": inline(" ".join(para), src, pages)})
            para = []
        if bullets:
            nodes.append({"tag": "ol" if ordered else "ul", "children": [
                {"tag": "li", "children": inline(b, src, pages)} for b in bullets]})
            bullets = []
            ordered = False
        if table:
            rows = [r for r in table if not set(r.replace("|", "").strip()) <= set("-: ")]
            cols = [c.strip() for c in rows[0].strip("|").split("|")]
            nodes.append({"tag": "p", "children": [
                {"tag": "em", "children": [" / ".join(cols)]}]})
            nodes.append({"tag": "ul", "children": [
                {"tag": "li", "children": inline(
                    " — ".join(f"**{c.strip()}**" if i else c.strip()
                               for i, c in enumerate(r.strip("|").split("|"))), src, pages)}
                for r in rows[1:]]})
            table = []

    i = 1
    while i < len(lines):
        ln = lines[i]
        if ln.startswith("```"):
            flush()
            i += 1
            buf = []
            while i < len(lines) and not lines[i].startswith("```"):
                buf.append(lines[i]); i += 1
            nodes.append({"tag": "pre", "children": ["\n".join(buf)]})
        elif ln.startswith("|"):
            if para or bullets:
                flush()
            table.append(ln)
        elif ln.startswith("### "):
            flush(); nodes.append({"tag": "h4", "children": inline(ln[4:].strip(), src, pages)})
        elif ln.startswith("## "):
            flush(); nodes.append({"tag": "h3", "children": inline(ln[3:].strip(), src, pages)})
        elif ln.strip() == "---":
            flush(); nodes.append({"tag": "hr"})
        elif ln.startswith("- "):
            if para or table:
                flush()
            bullets.append(ln[2:].strip())
        elif re.match(r"^\d+\. ", ln):
            if para or table:
                flush()
            ordered = True
            bullets.append(re.sub(r"^\d+\. ", "", ln).strip())
        elif not ln.strip():
            flush()
        elif bullets:
            bullets[-1] += " " + ln.strip()      # продолжение пункта списка
        else:
            para.append(ln.strip())
        i += 1
    flush()
    return title, nodes


def publish(path, pages, token, dry=False):
    src = str(Path(path).relative_to(ROOT))
    title, content = convert(path, pages)
    if dry:
        print(f"{src}: «{title}», блоков {len(content)}")
        return pages.get(src)
    body = json.dumps(content, ensure_ascii=False)
    if src in pages:
        page = api("editPage", access_token=token, path=pages[src]["path"],
                   title=title, author_name=AUTHOR, content=body)
    else:
        page = api("createPage", access_token=token, title=title,
                   author_name=AUTHOR, content=body)
    return {"path": page["path"], "url": page["url"], "title": title}


def main():
    args = [a for a in sys.argv[1:] if a != "--dry"]
    dry = "--dry" in sys.argv
    if not args:
        sys.exit(__doc__)
    token = os.environ.get("TELEGRAPH_TOKEN")
    if not token and not dry:
        sys.exit("нет TELEGRAPH_TOKEN в окружении: без него нельзя править уже "
                 "опубликованные страницы, а создавать дубли смысла нет")

    pages = load_map()
    files = [Path(a).resolve() for a in args]

    # первый проход: создать недостающие, чтобы узнать адреса для перелинковки
    for f in files:
        src = str(f.relative_to(ROOT))
        if src not in pages:
            page = publish(f, pages, token, dry)
            if page:
                pages[src] = page
                print(f"создано: {src} → {page['url']}")

    # второй проход: перезалить всё с уже известными ссылками друг на друга
    for f in files:
        src = str(f.relative_to(ROOT))
        page = publish(f, pages, token, dry)
        if page:
            pages[src] = page
            print(f"обновлено: {src} → {page['url']}")

    if not dry:
        save_map(pages)
        print(f"\nкарта страниц: {MAP_FILE.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
