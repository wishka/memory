# CI/CD Memory

## Ветки

```
feature/*  ─PR─▶  develop  ─PR─▶  main  ─тег vX.Y.Z─▶  выпуск
                     │                                   │
                     └─▶ dev-сборка (APK для тестов)     └─▶ GitHub Release (APK + AAB)
hotfix/*   ─PR─▶  main (срочное исправление), затем main → develop
```

| Ветка | Зачем | Кто пишет |
|---|---|---|
| `main` | то, что выпущено или готово к выпуску | только PR из `develop` или `hotfix/*` |
| `develop` | текущая разработка, ветка по умолчанию | только PR из `feature/*`, `fix/*` |
| `feature/<что>` | новая функция | вы, напрямую |
| `fix/<что>` | исправление в разработке | вы, напрямую |
| `hotfix/<что>` | срочное исправление выпуска | от `main`, PR в `main` |

Прямой push в `main` и `develop` запрещён (защита веток): только PR с
зелёной проверкой **build (debug)**. Ветки PR удаляются после слияния.

## Что запускается

| Workflow | Когда | Что делает |
|---|---|---|
| **CI** (`ci.yml`) | каждый PR, push в `main`, из других workflow | сборка debug, юнит-тесты, lint (пока не блокирует), APK и отчёты в артефактах |
| **Dev build** (`dev-build.yml`) | слияние в `develop` | CI → APK, подписанный общим ключом разработки → пре-релиз **dev** |
| **Demo build** (`demo.yml`) | кнопка Run workflow, любая ветка | CI → APK → пре-релиз **demo** (`memory-demo.apk`) |
| **Release** (`release.yml`) | тег `vX.Y.Z` на `main` | CI → ваше подтверждение → подписанные APK и AAB → GitHub Release |
| **iOS** (`ios.yml`) | PR и push в `main` / `develop`, если менялась `ios/` | сборка и юнит-тесты на симуляторе iPhone (macOS), проверочная сборка под iPhone без подписи |

**Dev-сборка** всегда лежит по одной ссылке:

```
https://github.com/<владелец>/memory/releases/download/dev/memory-dev.apk
```

Она ставится как отдельное приложение `ru.wishka.memory.debug` и обновляется
поверх себя, если задан общий ключ разработки (см. ниже).

**Демо из любой ветки:** Actions → **Demo build** → Run workflow → выбрать
ветку. Ссылка для телефона:

```
https://github.com/<владелец>/memory/releases/download/demo/memory-demo.apk
```

Вход без регистрации: логин `demo`, пароль `memory2026`.

**Выпуск:**

```bash
git checkout main && git pull
git tag v0.2.0 && git push origin v0.2.0
```

Код версии считается из тега: `X*10000 + Y*100 + Z` (v1.2.3 → 10203) и
обязан расти. Задание ждёт вашего подтверждения в разделе Actions
(окружение production).

## Первоначальная настройка (один раз)

1. Привязать репозиторий и отправить `main`:
   ```bash
   git remote add origin https://github.com/wishka/memory.git
   git push -u origin main
   ```
2. Ветка `develop`, окружения и защита веток:
   ```bash
   gh auth login
   bash scripts/setup-github.sh
   git fetch origin && git checkout -b develop origin/develop
   ```
3. Ключи подписи и секреты:
   ```bash
   bash scripts/make-keystores.sh ~/keys/memory
   ```
   Скрипт создаст `memory-release.jks` и `memory-dev.jks` и напечатает
   команды `gh secret set …` — выполните их.

**Ключ выпуска храните как зеницу ока:** без него обновления не встанут
поверх установленных версий, а в магазине придётся публиковать приложение
заново.

## Секреты

| Окружение | Секрет | Что это |
|---|---|---|
| production | `ANDROID_KEYSTORE_BASE64` | `memory-release.jks` в base64 |
| production | `ANDROID_KEYSTORE_PASSWORD` | пароль хранилища |
| production | `ANDROID_KEY_ALIAS` | `memory` |
| production | `ANDROID_KEY_PASSWORD` | пароль ключа |
| dev | `DEV_KEYSTORE_BASE64` | `memory-dev.jks` в base64 |
| dev | `DEV_KEYSTORE_PASSWORD` | пароль |

Без секретов dev сборка всё равно публикуется, но подписывается случайным
ключом машины CI — перед установкой придётся удалять прежнюю.

## Дальше

- **RuStore**: когда появится аккаунт разработчика, в `release.yml`
  добавится шаг загрузки AAB через API RuStore (ключ — секрет production).
- **Сервер**: когда у Memory появится бэкенд, к этой схеме добавятся
  окружения staging/production для него — как в angelsheart.
