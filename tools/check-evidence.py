#!/usr/bin/env python3
"""Проверка, что у разбора приложено доказательство.

    python3 tools/check-evidence.py            # все q/*.md
    python3 tools/check-evidence.py q/a.md     # выборочно

В этом репозитории нет CI-стендов: архитектурное решение проверяется тем, как
оно ведёт себя на длинной дистанции, а не прогоном за минуту. Поэтому роль
зелёного check.sh играет договорённость — у каждого разбора в шапке строка

    **Доказано:** разбор на своём коде — [пример](ssot/cart-state.kt)

и файлы, на которые она ссылается, лежат рядом — в q/<slug>/. Проверяется наличие
строки и существование файлов, а не их содержимое: содержимое читает человек.

Разбор без доказательства помечается явно:

    **Доказано:** нечем — рассуждение без стенда

Такой разбор проходит проверку, но пометка видна и в файле, и в ревью: это
осознанный долг, а не забытая строка.
"""
import re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
HEADER = re.compile(r"^\*\*Доказано:\*\*\s*(.+)$", re.M)
LINK = re.compile(r"\[[^\]]*\]\(([^)]+)\)")


def check(path):
    text = path.read_text()
    m = HEADER.search(text)
    if not m:
        return [f"{path}: нет строки «**Доказано:** ...» в шапке"]
    claim = m.group(1).strip()
    if not claim:
        return [f"{path}: строка «**Доказано:**» пустая"]
    problems = []
    for href in LINK.findall(claim):
        if href.startswith("http"):
            continue
        target = (path.parent / href.split("#")[0]).resolve()
        if not target.exists():
            problems.append(f"{path}: доказательство ссылается на {href}, файла нет")
    return problems


def main():
    args = sys.argv[1:]
    # README.md — описание папки, а не разбор; в маску он попадать не должен.
    files = ([Path(a) for a in args] if args
             else [f for f in sorted(ROOT.glob("q/*.md")) if f.name != "README.md"])
    if not files:
        print("разборов нет")
        return 0
    problems = [p for f in files for p in check(f)]
    for p in problems:
        print(p, file=sys.stderr)
    print(f"проверено разборов: {len(files)}, проблем: {len(problems)}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
