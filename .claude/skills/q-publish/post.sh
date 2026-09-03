#!/usr/bin/env bash
# Отправка поста в телеграм-канал через Bot API.
#   post.sh <файл-с-html>                — новый пост
#   post.sh <файл-с-html> --edit <msg_id> — правка существующего
# Нужны TELEGRAM_BOT_TOKEN и TELEGRAM_CHANNEL в окружении.
set -eu

file="${1:?укажи файл с текстом поста}"
: "${TELEGRAM_BOT_TOKEN:?нет TELEGRAM_BOT_TOKEN в окружении}"
: "${TELEGRAM_CHANNEL:?нет TELEGRAM_CHANNEL в окружении (@канал или chat_id)}"

# секрет мог приехать с переводом строки или пробелами — в URL это ломает всё
token=$(printf '%s' "$TELEGRAM_BOT_TOKEN" | tr -d '[:space:]')
chat=$(printf '%s' "$TELEGRAM_CHANNEL" | tr -d '[:space:]')

case "$token" in
  *:*) ;;
  *) echo "TELEGRAM_BOT_TOKEN не похож на токен: ожидается '<id>:<секрет>'" >&2; exit 1 ;;
esac

api="https://api.telegram.org/bot${token}"

# предполёт: убедиться, что адресат — канал, а не переписка с ботом.
# id канала всегда отрицательный и начинается с -100; положительное число —
# это пользователь или бот, туда пост уйти не должен.
chat_info=$(curl -sS "$api/getChat" --data-urlencode "chat_id=${chat}")
python3 - "$chat_info" <<'PY_CHECK'
import json, sys
d = json.loads(sys.argv[1])
if not d.get("ok"):
    sys.exit(f"адресат не найден: {d.get('description')} — проверь TELEGRAM_CHANNEL")
r = d["result"]
if r.get("type") != "channel":
    sys.exit(f"адресат не канал, а {r.get('type')}: "
             f"{r.get('title') or r.get('username')} — в TELEGRAM_CHANNEL нужен id канала (-100...)")
print(f"канал: {r.get('title')}")
PY_CHECK

method="sendMessage"
extra=()

if [ "${2:-}" = "--edit" ]; then
  method="editMessageText"
  extra=(--data-urlencode "message_id=${3:?укажи message_id}")
fi

resp=$(curl -sS "$api/$method" \
  --data-urlencode "chat_id=${chat}" \
  --data-urlencode "parse_mode=HTML" \
  --data-urlencode "disable_web_page_preview=true" \
  --data-urlencode "text@${file}" \
  ${extra[@]+"${extra[@]}"})

if command -v python3 >/dev/null; then
  python3 - "$resp" <<'PY'
import json, sys
d = json.loads(sys.argv[1])
if d.get("ok"):
    print("отправлено, message_id =", d["result"]["message_id"])
else:
    desc = d.get("description", "")
    hint = ""
    if "Not Found" in desc:
        hint = " — путь с токеном не распознан: TELEGRAM_BOT_TOKEN пуст или в нём лишние символы (кавычки, префикс bot)"
    elif "Unauthorized" in desc:
        hint = " — форма токена верная, но он отозван или с опечаткой"
    elif "chat not found" in desc:
        hint = " — неверный TELEGRAM_CHANNEL или бот не админ канала"
    elif "not enough rights" in desc:
        hint = " — бот в канале, но без права публикации"
    print("ОШИБКА:", desc + hint, file=sys.stderr); sys.exit(1)
PY
else
  echo "$resp"
fi
