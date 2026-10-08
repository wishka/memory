# Как вносить изменения

1. Новая ветка от `develop`:
   ```bash
   git checkout develop && git pull
   git checkout -b feature/chat-photos
   ```
2. Коммиты — короткие, по-русски, в повелительном или описательном виде:
   «Чаты: отправка фото», «Исправить падение ленты при пустом списке».
3. Перед отправкой — локальная проверка (или просто дождитесь CI):
   ```bash
   ./gradlew assembleDebug testDebugUnitTest
   ```
4. Отправить ветку и открыть PR в `develop`:
   ```bash
   git push -u origin feature/chat-photos
   gh pr create --base develop --fill
   ```
5. CI зелёный → слияние (`gh pr merge --merge --auto` вольёт сам, когда
   проверки пройдут). После слияния dev-сборка появится в выпуске **dev**.
6. Готово к выпуску → PR `develop` → `main`, затем тег `vX.Y.Z` на `main`.

Подробно о ветках, сборках и выпусках — [docs/CICD.md](docs/CICD.md).
