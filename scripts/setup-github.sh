#!/usr/bin/env bash
#
# Однократная настройка репозитория Memory на GitHub под CI/CD:
#   • ветка develop (от main, если её ещё нет);
#   • окружение dev — для тестовых сборок из develop и демо из feature/*;
#   • окружение production — выпуски только из тегов v* и с вашим
#     подтверждением;
#   • защита main и develop: слияние только через pull request с зелёной
#     проверкой «build (debug)», без force-push и удаления.
#
# Нужен GitHub CLI под учётной записью владельца репозитория:
#   gh auth login
#   bash scripts/setup-github.sh
#
# Скрипт можно запускать повторно — он приводит настройки к описанным.
# Секреты он не задаёт: их значения есть только у вас (scripts/make-keystores.sh).

set -Eeuo pipefail

REPO="${REPO:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}"
ME_ID="$(gh api user -q .id)"
ME="$(gh api user -q .login)"
CHECKS='["build (debug)"]'

echo "Репозиторий: $REPO, владелец: $ME"

# ---------- Ветка develop ----------
if gh api "repos/$REPO/branches/develop" >/dev/null 2>&1; then
    echo "✓ develop уже есть"
else
    MAIN_SHA="$(gh api "repos/$REPO/git/ref/heads/main" -q .object.sha)"
    gh api "repos/$REPO/git/refs" -f ref=refs/heads/develop -f sha="$MAIN_SHA" >/dev/null
    echo "✓ создана develop от main ($MAIN_SHA)"
fi
gh api -X PATCH "repos/$REPO" -f default_branch=develop >/dev/null
echo "✓ ветка по умолчанию — develop (туда идут PR)"

# ---------- Окружения ----------
policy() {  # окружение, затем пары «тип:шаблон»
    local env="$1"; shift
    for rule in "$@"; do
        gh api -X POST "repos/$REPO/environments/$env/deployment-branch-policies" \
            -f name="${rule#*:}" -f type="${rule%%:*}" >/dev/null 2>&1 || true
    done
}

gh api -X PUT "repos/$REPO/environments/dev" --input - >/dev/null <<JSON
{"deployment_branch_policy": {"protected_branches": false, "custom_branch_policies": true}}
JSON
# feature/* — для демо-сборок по кнопке из любой ветки (demo.yml)
policy dev branch:develop "branch:feature/*"
echo "✓ окружение dev: develop и feature/* (тестовые и демо-сборки)"

gh api -X PUT "repos/$REPO/environments/production" --input - >/dev/null <<JSON
{
  "reviewers": [{"type": "User", "id": $ME_ID}],
  "deployment_branch_policy": {"protected_branches": false, "custom_branch_policies": true}
}
JSON
policy production tag:v*
echo "✓ окружение production: только теги v*, с подтверждением ($ME)"

# ---------- Защита веток ----------
protect() {
    local branch="$1" reviews="$2"
    gh api -X PUT "repos/$REPO/branches/$branch/protection" --input - >/dev/null <<JSON
{
  "required_status_checks": {"strict": true, "contexts": $CHECKS},
  "enforce_admins": false,
  "required_pull_request_reviews": {
    "required_approving_review_count": $reviews,
    "dismiss_stale_reviews": true
  },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "required_conversation_resolution": true
}
JSON
    echo "✓ $branch: только PR, обязательная проверка, без force-push"
}
# Пока разработчик один, обязательных одобрений 0: одобрить собственный
# PR GitHub не даёт. Появится второй — поднимите до 1 (REVIEWS=1).
protect main "${REVIEWS:-0}"
protect develop 0

# Ветки feature/* удаляются сами после слияния PR
gh api -X PATCH "repos/$REPO" -F delete_branch_on_merge=true >/dev/null
echo "✓ ветки PR удаляются после слияния"

cat <<'TEXT'

Готово. Осталось задать ключи подписи — их создаёт и печатает команды
для них scripts/make-keystores.sh (подробно — docs/CICD.md).
TEXT
