#!/usr/bin/env python3
"""Превью и проверка поста до отправки в канал.

Пост в канал необратим: правится через `editMessageText`, удаляется руками.
Увидеть его можно было только после публикации — этот скрипт показывает пост
до неё и заодно ловит то, на чём Telegram молча портит текст или отвечает
`can't parse entities`.

    python3 tools/post-preview.py posts/value-class.html
    python3 tools/post-preview.py posts/value-class.html --open

Проверяется:
  * теги вне списка, который понимает Bot API, — Telegram откажет целиком;
  * незакрытые и перекрещенные теги;
  * длина больше лимита сообщения;
  * голый URL в тексте — по договорённости ссылка вешается на название вопроса;
  * ссылка на telegra.ph, которой нет в telegraph.json, — обычно опечатка
    в адресе или страница ещё не залита.

Рендер кладётся в posts/preview.html и в git не коммитится.
Только stdlib, Python 3.12+.
"""

from __future__ import annotations

import argparse
import html
import json
import pathlib
import re
import sys
import webbrowser
from html.parser import HTMLParser

# Ровно то, что понимает Telegram Bot API в parse_mode=HTML. Всё остальное
# (<div>, <p>, <br>, <ul>) — ошибка запроса, а не «просто не отрендерится».
ALLOWED_TAGS = {
    "b", "strong", "i", "em", "u", "ins", "s", "strike", "del",
    "a", "code", "pre", "blockquote", "span", "tg-spoiler",
}
# Теги, которые закрывать не нужно.
VOID_TAGS: set[str] = set()

TELEGRAM_MESSAGE_LIMIT = 4096

# Голый URL: http(s) вне атрибута href. Ищем в тексте, а не в разметке.
BARE_URL_RE = re.compile(r"https?://\S+")
TELEGRAPH_RE = re.compile(r"https?://telegra\.ph/\S+")


class PostParser(HTMLParser):
    """Разбирает пост, копит текст и ошибки разметки."""

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.stack: list[tuple[str, int]] = []
        self.errors: list[str] = []
        self.text_parts: list[str] = []
        self.links: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag not in ALLOWED_TAGS:
            self.errors.append(
                f"строка {self.getpos()[0]}: тег <{tag}> Telegram не понимает — "
                f"запрос упадёт с «can't parse entities»"
            )
            return
        if tag == "a":
            href = dict(attrs).get("href")
            if not href:
                self.errors.append(f"строка {self.getpos()[0]}: <a> без href")
            else:
                self.links.append(href)
                if not href.startswith(("http://", "https://", "tg://")):
                    self.errors.append(
                        f"строка {self.getpos()[0]}: href={href!r} — не абсолютная ссылка"
                    )
        if tag not in VOID_TAGS:
            self.stack.append((tag, self.getpos()[0]))

    def handle_endtag(self, tag: str) -> None:
        if tag not in ALLOWED_TAGS:
            return
        if not self.stack:
            self.errors.append(f"строка {self.getpos()[0]}: закрыт </{tag}>, который не открывали")
            return
        open_tag, line = self.stack.pop()
        if open_tag != tag:
            self.errors.append(
                f"строка {self.getpos()[0]}: закрыт </{tag}>, "
                f"а открыт был <{open_tag}> на строке {line} — теги перекрещены"
            )

    def handle_data(self, data: str) -> None:
        self.text_parts.append(data)

    def finish(self) -> None:
        for tag, line in self.stack:
            self.errors.append(f"строка {line}: <{tag}> не закрыт")


def check(source: str, telegraph: dict) -> tuple[list[str], list[str], str]:
    """Возвращает (ошибки, предупреждения, видимый текст)."""
    parser = PostParser()
    parser.feed(source)
    parser.close()
    parser.finish()

    errors = list(parser.errors)
    warnings: list[str] = []
    text = "".join(parser.text_parts)

    # Лимит считается по видимому тексту с разметкой: Telegram меряет исходную
    # строку запроса, поэтому берём длину того, что реально уйдёт.
    if len(source) > TELEGRAM_MESSAGE_LIMIT:
        errors.append(
            f"пост длиннее лимита: {len(source)} символов при {TELEGRAM_MESSAGE_LIMIT} — "
            f"Telegram обрежет или откажет"
        )

    for match in BARE_URL_RE.finditer(text):
        errors.append(
            f"голый URL в тексте: {match.group(0)[:60]} — "
            f"ссылка вешается на название вопроса, а не кладётся строкой"
        )

    known = {entry["url"].rstrip("/") for entry in telegraph.values() if entry.get("url")}
    for href in parser.links:
        if TELEGRAPH_RE.fullmatch(href) and href.rstrip("/") not in known:
            warnings.append(
                f"ссылка {href} не найдена в telegraph.json — "
                f"страница ещё не залита или в адресе опечатка"
            )

    return errors, warnings, text


PREVIEW_TEMPLATE = """<!doctype html>
<meta charset="utf-8">
<title>Превью поста — {name}</title>
<style>
  :root {{ color-scheme: light dark; }}
  body {{
    margin: 0; padding: 32px 16px;
    font: 16px/1.42 -apple-system, "Segoe UI", Roboto, sans-serif;
    background: var(--bg); color: var(--fg);
    --bg: #e6ebee; --fg: #000; --bubble: #fff; --link: #168acd; --meta: #8d969c;
    --code-bg: rgba(0,0,0,.06);
  }}
  body.dark {{
    --bg: #0e1621; --fg: #fff; --bubble: #182533; --link: #6ab3f3; --meta: #6d7f8f;
    --code-bg: rgba(255,255,255,.1);
  }}
  .wrap {{ max-width: 560px; margin: 0 auto; }}
  .bubble {{
    background: var(--bubble); border-radius: 12px; padding: 10px 12px 22px;
    box-shadow: 0 1px 2px rgba(0,0,0,.16); white-space: pre-wrap; word-wrap: break-word;
    position: relative;
  }}
  .bubble a {{ color: var(--link); text-decoration: none; }}
  .bubble a:hover {{ text-decoration: underline; }}
  .bubble code, .bubble pre {{
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: .93em;
    background: var(--code-bg); border-radius: 4px; padding: 0 3px;
  }}
  .bubble pre {{ display: block; padding: 8px 10px; white-space: pre; overflow-x: auto; }}
  .bubble blockquote {{
    margin: 4px 0; padding-left: 10px; border-left: 3px solid var(--link);
  }}
  .time {{ position: absolute; right: 12px; bottom: 6px; font-size: 12px; color: var(--meta); }}
  .bar {{
    max-width: 560px; margin: 0 auto 12px; display: flex; gap: 12px;
    align-items: center; color: var(--meta); font-size: 13px;
  }}
  button {{ font: inherit; padding: 3px 10px; border-radius: 6px; cursor: pointer;
            border: 1px solid var(--meta); background: transparent; color: inherit; }}
  .issues {{ max-width: 560px; margin: 20px auto 0; font-size: 13px; }}
  .issues li {{ margin: 4px 0; }}
  .err {{ color: #e0484c; }}
  .warn {{ color: #d08b2a; }}
</style>
<div class="bar">
  <button onclick="document.body.classList.toggle('dark')">тема</button>
  <span>{chars} симв. из {limit}</span>
  <span>{name}</span>
</div>
<div class="wrap">
  <div class="bubble">{post}<span class="time">{time}</span></div>
</div>
{issues}
"""


def render(path: pathlib.Path, source: str, issues_html: str) -> pathlib.Path:
    out = path.parent / "preview.html"
    out.write_text(
        PREVIEW_TEMPLATE.format(
            name=html.escape(path.name),
            post=source.strip(),
            chars=len(source),
            limit=TELEGRAM_MESSAGE_LIMIT,
            time="12:00",
            issues=issues_html,
        ),
        encoding="utf-8",
    )
    return out


def issues_block(errors: list[str], warnings: list[str]) -> str:
    if not errors and not warnings:
        return '<div class="issues">Проверка чистая.</div>'
    rows = "".join(f'<li class="err">✗ {html.escape(e)}</li>' for e in errors)
    rows += "".join(f'<li class="warn">! {html.escape(w)}</li>' for w in warnings)
    return f'<div class="issues"><ul>{rows}</ul></div>'


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("post", help="файл поста, например posts/value-class.html")
    parser.add_argument("--open", action="store_true", help="открыть превью в браузере")
    parser.add_argument("--telegraph", default="telegraph.json")
    args = parser.parse_args(argv)

    path = pathlib.Path(args.post)
    if not path.is_file():
        print(f"нет файла {path}", file=sys.stderr)
        return 2
    source = path.read_text(encoding="utf-8")

    telegraph_path = pathlib.Path(args.telegraph)
    telegraph = json.loads(telegraph_path.read_text()) if telegraph_path.is_file() else {}

    errors, warnings, text = check(source, telegraph)
    out = render(path, source, issues_block(errors, warnings))

    print(f"{len(source)} символов из {TELEGRAM_MESSAGE_LIMIT}, видимого текста {len(text)}")
    print(f"превью: {out}")
    for w in warnings:
        print(f"  ! {w}")
    for e in errors:
        print(f"  ✗ {e}", file=sys.stderr)

    if args.open:
        webbrowser.open(out.resolve().as_uri())

    if errors:
        print(f"\nне отправлять: {len(errors)} ошибок", file=sys.stderr)
        return 1
    print("проверка чистая — можно показывать пользователю и отправлять")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
