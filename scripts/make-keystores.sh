#!/usr/bin/env bash
#
# Создаёт два ключа подписи и печатает команды, которые кладут их в
# секреты GitHub:
#   • memory-release.jks — ключ выпусков (окружение production);
#   • memory-dev.jks     — общий ключ тестовых сборок (окружение dev).
#
#   bash scripts/make-keystores.sh ~/keys/memory
#
# ВАЖНО: ключ выпуска — один на всю жизнь приложения. Потеряете его —
# обновления больше не встанут поверх установленных версий, а в магазине
# придётся публиковать приложение заново. Сохраните файл и пароль в
# менеджере паролей и в резервной копии. В репозиторий ключи не кладутся
# (*.jks в .gitignore).

set -Eeuo pipefail

DIR="${1:?Укажите папку для ключей вне репозитория, например ~/keys/memory}"
mkdir -p "$DIR"
command -v keytool >/dev/null || { echo "Нужен keytool (входит в JDK 17)" >&2; exit 1; }

gen_pass() { head -c 64 /dev/urandom | base64 | LC_ALL=C tr -dc 'A-Za-z0-9' | cut -c1-24; }

make_key() {  # файл, псевдоним, пароль, имя; код 1 — ключ уже был
    if [ -f "$1" ]; then echo "• $1 уже есть — не трогаю"; return 1; fi
    keytool -genkeypair -v -keystore "$1" -alias "$2" -keyalg RSA -keysize 4096 -validity 10000 \
        -storepass "$3" -keypass "$3" -dname "CN=$4, O=Memory, C=RU" >/dev/null 2>&1
    echo "✓ создан $1"
}

REL_PASS="$(gen_pass)"; DEV_PASS="$(gen_pass)"
# Если ключ уже был, пароль к нему знаете только вы — в командах будет заглушка
make_key "$DIR/memory-release.jks" memory "$REL_PASS" "Memory" || REL_PASS='<пароль ключа выпуска>'
make_key "$DIR/memory-dev.jks" dev "$DEV_PASS" "Memory dev" || DEV_PASS='<пароль ключа разработки>'

B64="base64"; base64 --help 2>&1 | grep -q -- '-w' && B64="base64 -w0"

cat <<TEXT

Пароли — выпуск: $REL_PASS, разработка: $DEV_PASS.
Сохраните их и сами файлы .jks в менеджере паролей и резервной копии.

Команды для секретов (выполнять из папки репозитория):

  gh secret set ANDROID_KEYSTORE_BASE64   --env production --body "\$($B64 < "$DIR/memory-release.jks")"
  gh secret set ANDROID_KEYSTORE_PASSWORD --env production --body '$REL_PASS'
  gh secret set ANDROID_KEY_ALIAS         --env production --body 'memory'
  gh secret set ANDROID_KEY_PASSWORD      --env production --body '$REL_PASS'

  gh secret set DEV_KEYSTORE_BASE64       --env dev --body "\$($B64 < "$DIR/memory-dev.jks")"
  gh secret set DEV_KEYSTORE_PASSWORD     --env dev --body '$DEV_PASS'
TEXT
