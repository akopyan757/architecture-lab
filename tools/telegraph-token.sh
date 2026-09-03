#!/usr/bin/env bash
# Заводит аккаунт Telegra.ph и раскладывает токен по местам.
#
#     bash tools/telegraph-token.sh
#
# Токен не печатается никогда: он уезжает в ~/.telegraph-token и в секрет
# TELEGRAPH_TOKEN репозитория. Локальная копия обязательна — секреты GitHub
# на чтение недоступны, записал и всё.
#
# Скрипт устроен по шагам и падает на первом же расхождении, потому что
# наивный однострочник curl|python|gh при пустом ответе молча записывает
# пустую строку, а сломанный секрет хуже отсутствующего: проверка «секрет
# есть» проходит, а заливка падает позже и непонятно почему.
set -euo pipefail

REPO="${REPO:-akopyan757/architecture-lab}"
FILE="$HOME/.telegraph-architecture-lab-token"
DESKTOP_COPY="$HOME/Desktop/telegraph-architecture-lab-token.txt"
SHORT_NAME="architecture-lab"
AUTHOR="Architecture Knowledge"   # совпадает с AUTHOR в md-to-telegraph.py

if [ -s "$FILE" ]; then
  echo "В $FILE уже что-то лежит. Удали файл руками, если правда нужен новый"
  echo "аккаунт: старые страницы под прежним токеном станут нередактируемыми."
  exit 1
fi

body=$(mktemp)
trap 'rm -f "$body"' EXIT

code=$(curl -sS -o "$body" -w '%{http_code}' --max-time 30 \
  --get "https://api.telegra.ph/createAccount" \
  --data-urlencode "short_name=$SHORT_NAME" \
  --data-urlencode "author_name=$AUTHOR")

if [ "$code" != 200 ]; then
  echo "createAccount ответил HTTP $code, тело:"
  cat "$body"; echo
  exit 1
fi

# Токен вынимается и сразу уходит в файл: на stdout он не попадает.
python3 - "$body" "$FILE" <<'PY'
import json, pathlib, sys

raw = pathlib.Path(sys.argv[1]).read_text()
if not raw.strip():
    sys.exit("createAccount вернул пустой ответ")
try:
    res = json.loads(raw)
except json.JSONDecodeError:
    sys.exit(f"createAccount вернул не JSON:\n{raw[:400]}")
if not res.get("ok"):
    sys.exit(f"createAccount отказал: {res.get('error')}")

token = res["result"].get("access_token", "")
if not token.strip():
    sys.exit("в ответе нет access_token")

pathlib.Path(sys.argv[2]).write_text(token)
print(f"токен получен, длина {len(token)} символов")
PY

chmod 600 "$FILE"

# Проверяем, что токен рабочий, до того как класть его в секреты.
if ! curl -sS --max-time 30 --get "https://api.telegra.ph/getAccountInfo" \
     --data-urlencode "access_token=$(cat "$FILE")" | grep -q '"ok":true'; then
  echo "токен записан в $FILE, но getAccountInfo его не принял — в секреты не кладу"
  exit 1
fi

gh secret set TELEGRAPH_TOKEN -R "$REPO" < "$FILE"

# Вторая копия на десктопе: единственная страховка от потери. Секрет GitHub на
# чтение недоступен, а без токена страницы Telegra.ph правятся никогда.
cp "$FILE" "$DESKTOP_COPY"
chmod 600 "$DESKTOP_COPY"

echo
echo "Готово:"
echo "  $FILE          (chmod 600, не потеряй — из секретов не достать)"
echo "  $DESKTOP_COPY  (вторая копия)"
echo "  секрет TELEGRAPH_TOKEN в $REPO"
