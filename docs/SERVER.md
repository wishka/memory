# Сервер Memory: запуск

Сервер — папка `server/` (Kotlin, Ktor, PostgreSQL). Он исполняет тот же
контракт, что встроенный демо-сервер (`docs/openapi.yaml`,
`docs/REALTIME.md`): вход и устройства, лента, записи, «нравится»,
переписки и группы, загрузки фото и видео (tus), живой канал.
Приложение переходит с демо на него, как только в сборке задан `API_URL`.

Всё запускается одной командой Docker Compose: база, сервер и выход в
интернет с HTTPS. Три варианта, где его держать:

| | Где | Стоит | Карта | Адрес |
| --- | --- | --- | --- | --- |
| **А** | Свой компьютер (Mac, Windows, Linux, старый ноутбук, Raspberry Pi 4/5) | 0 ₽ | не нужна | `https://memory.<сеть>.ts.net` |
| **Б** | VPS у российского хостинга | помесячно | российская | свой домен или `ts.net` |
| **В** | Oracle Cloud Always Free | 0 ₽ | нужна зарубежная для проверки | свой домен или `ts.net` |

Начните с **А**: бесплатно, без карты, без домена и без белого IP.
Переехать на **Б** потом — тот же `docker compose`, данные переносятся
резервной копией (ниже).

---

## А. Свой компьютер + Tailscale Funnel

Tailscale Funnel даёт постоянный адрес `https://memory.<сеть>.ts.net` с
настоящим сертификатом и пускает к серверу из интернета без открытых
портов на роутере. Шифрование (TLS) заканчивается на вашем компьютере:
Tailscale пересылает соединение, но не видит, что внутри.

Компьютер должен быть включён, пока приложением пользуются: выключен —
телефоны работают офлайн и отправят накопленное, когда сервер вернётся.

### 1. Docker

- **Mac / Windows** — [Docker Desktop](https://www.docker.com/products/docker-desktop/).
  В настройках включите «Start Docker Desktop when you sign in».
- **Linux / Raspberry Pi** (64-битная система):
  `curl -fsSL https://get.docker.com | sh`, затем `sudo usermod -aG docker $USER` и перелогиньтесь.

Проверка: `docker compose version`.

### 2. Tailscale: аккаунт и настройки

1. Зарегистрируйтесь на [tailscale.com](https://tailscale.com) (вход через
   Google, GitHub, Apple или Microsoft; бесплатный план Personal, карта не нужна).
2. [DNS](https://login.tailscale.com/admin/dns): включите **MagicDNS** и
   ниже — **HTTPS Certificates**. Здесь же видно имя сети
   (`tail1234.ts.net`) — из него будет адрес сервера.
3. [Access controls](https://login.tailscale.com/admin/acls): в файле
   политики должен быть атрибут `funnel`. Если блока нет — добавьте:
   ```json
   "nodeAttrs": [
     { "target": ["autogroup:member"], "attr": ["funnel"] }
   ]
   ```
   и нажмите Save.
4. [Keys](https://login.tailscale.com/admin/settings/keys) → **Generate auth key**
   (Reusable и Ephemeral — выключены). Скопируйте ключ `tskey-auth-…`:
   он нужен один раз, при первом запуске.

### 3. Запуск

```bash
git clone https://github.com/wishka/memory.git
cd memory/server
cp .env.example .env
```

Откройте `server/.env` и заполните:

```bash
DB_PASSWORD=<длинная случайная строка: openssl rand -base64 24>
COMPOSE_PROFILES=funnel
TS_AUTHKEY=tskey-auth-…
TS_HOSTNAME=memory
```

`.env` в репозиторий не попадает (он в `.gitignore`) — пароли остаются
только на этом компьютере.

```bash
docker compose up -d --build
```

Первая сборка — 3–7 минут (качает Gradle и библиотеки). Дальше запуск —
секунды; после перезагрузки компьютера контейнеры поднимаются сами.

### 4. Проверка

```bash
docker compose ps                    # db, server, tailscale — running
curl http://localhost:8080/health    # {"ok":true}
docker compose logs tailscale        # адрес и выданный сертификат
```

В [списке машин](https://login.tailscale.com/admin/machines) появится
`memory`. Откройте с телефона **по мобильному интернету** (Wi-Fi выключен):

```
https://memory.<сеть>.ts.net/health
```

Должно быть `{"ok":true}`. Первый раз сертификат выпускается до минуты —
обновите страницу.

> Если с мобильного интернета адрес `*.ts.net` не открывается (у части
> российских операторов бывают ограничения на зарубежные сервисы), а с
> Wi-Fi открывается — используйте вариант **Б** с российским VPS и своим
> доменом: тот же compose, профиль `caddy`.

### 5. Приложение — на сервер

Адрес — без `/` на конце.

- **Android, локальная сборка** — `local.properties` в корне:
  ```
  api.url=https://memory.<сеть>.ts.net
  ```
- **iPhone** — `ios/Memory/Secrets.plist`, ключ `API_URL` (строка) с тем же адресом.
- **Сборки в GitHub Actions** — Settings → Secrets and variables →
  Actions → **New repository secret**: `API_URL` = адрес.

Пересоберите приложение. Пустой `API_URL` — снова встроенный демо-сервер.

Что происходит при первом входе: аккаунт, заведённый на телефоне, сервер
ещё не знает — телефон сам заводит его на сервере (тот же логин и пароль),
отправляет профиль и свой открытый ключ шифрования. Найти других людей —
поиск (от 2 букв), «Написать» — личная переписка через сервер.

---

## Б. VPS за российскую карту

Подойдёт любой VPS с **Ubuntu 24.04**, 1–2 ГБ памяти и 20 ГБ диска
(на 1 ГБ оставьте в `.env` `JAVA_OPTS=-Xmx384m`). Оплата — российской картой.

1. Создайте сервер, войдите по SSH: `ssh root@<IP>`.
2. Docker: `curl -fsSL https://get.docker.com | sh`.
3. `git clone https://github.com/wishka/memory.git && cd memory/server && cp .env.example .env`.
4. Выберите, как сервер виден снаружи:
   - **Без домена** — как в варианте А: `COMPOSE_PROFILES=funnel` и `TS_AUTHKEY`.
     Порты открывать не нужно.
   - **Свой домен** (например, `api.memory.am`): у регистратора домена —
     A-запись на IP сервера. В `.env`:
     ```bash
     COMPOSE_PROFILES=caddy
     DOMAIN=api.memory.am
     ```
     Откройте порты 80 и 443: в панели хостинга (файрвол / группы
     безопасности, если есть) и на самом сервере:
     ```bash
     ufw allow 22/tcp && ufw allow 80/tcp && ufw allow 443/tcp && ufw enable
     ```
     Caddy сам получит сертификат Let's Encrypt и будет его продлевать.
5. `docker compose up -d --build`, проверка — `https://<адрес>/health`.
6. `API_URL` — как в пункте А.5.

## В. Oracle Cloud Always Free

Бесплатно навсегда, но при регистрации Oracle проверяет банковскую
карту (зарубежную). Если карта появится:

1. [cloud.oracle.com](https://cloud.oracle.com) → Compute → Instances →
   **Create instance**: образ **Ubuntu 24.04**, форма
   **VM.Standard.A1.Flex** (Ampere, 1–4 OCPU, до 24 ГБ — в бесплатных
   пределах). Добавьте свой SSH-ключ.
2. Порты (только для профиля `caddy`; Funnel портов не требует):
   - Networking → Virtual Cloud Networks → ваша сеть → Security Lists →
     Default → **Add Ingress Rules**: Source `0.0.0.0/0`, TCP, порты `80` и `443`.
   - На самой машине образы Oracle закрывают порты в iptables:
     ```bash
     sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
     sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
     sudo netfilter-persistent save
     ```
3. Дальше — как в варианте Б с пункта 2. Все образы (Java, PostgreSQL,
   Tailscale, Caddy) собраны и для ARM.

---

## Пуши (Android)

Сервер шлёт пуши через Firebase Cloud Messaging, когда приложение закрыто:
новые сообщения, заявки и подписки, комментарии к вашим записям.

1. [Firebase](https://console.firebase.google.com) → ваш проект → ⚙
   **Настройки проекта** → **Сервисные аккаунты** → **Создать закрытый ключ**.
2. Скачанный JSON положите как `server/secrets/firebase.json` (папка — в
   `.gitignore`, в репозиторий не попадёт).
3. `docker compose up -d` — в `docker compose logs server` будет «Пуши: FCM».

Нет файла — сервер работает без пушей. iPhone — позже (нужен платный
Apple Developer для APNs), см. [PUSH.md](PUSH.md).

## Модерация

Apple и Google требуют, чтобы на жалобы пользователей отвечали (в течение
суток). Жалобы из приложения приходят на сервер, разбирать их — в браузере:

1. В `server/.env` впишите свой логин Memory: `ADMINS=wishka` (несколько —
   через запятую), затем `docker compose up -d`.
2. Откройте `https://memory.<сеть>.ts.net/admin` (или ваш домен) и войдите
   логином и паролем Memory.
3. У каждой жалобы: **Оставить**, **Удалить** (запись, комментарий,
   историю) или **Удалить и заблокировать автора** — он больше не сможет
   войти, его профиль и записи исчезнут у всех.

Ещё на сервере: блокировки (заблокированный не видит ваши записи,
истории и комментарии, не может подписаться и написать), удаление аккаунта
со всем содержимым и файлами, смена пароля (другие устройства выходят).

## Обслуживание

```bash
cd memory/server
docker compose logs -f server                 # что происходит
git pull && docker compose up -d --build      # обновить сервер
docker compose down                           # остановить (данные остаются)
```

**Резервная копия** (база и загруженные файлы):

```bash
docker compose exec -T db pg_dump -U memory memory | gzip > memory-$(date +%F).sql.gz
docker run --rm -v server_files:/data -v "$PWD":/out alpine tar czf /out/files-$(date +%F).tgz -C /data .
```

**Переезд на другую машину**: на новой — шаги запуска, затем

```bash
docker compose up -d db
gunzip -c memory-…sql.gz | docker compose exec -T db psql -U memory memory
docker run --rm -v server_files:/data -v "$PWD":/in alpine tar xzf /in/files-….tgz -C /data
docker compose up -d
```

(Имя тома — `<папка>_files`; для папки `server` это `server_files`,
проверить — `docker volume ls`.)

## Что хранит сервер

- Пароли — только отпечатки PBKDF2-SHA256 (210 000 итераций); токены
  входа — только отпечатки SHA-256. Токен доступа живёт 15 минут, токен
  обновления одноразовый. 10 неверных паролей подряд — пауза 15 минут.
- Секретные переписки — конверты (X25519 + AES-256-GCM) и зашифрованные
  файлы: ключи есть только у двух телефонов.
- Подписки (закрытый профиль — по заявке), комментарии, истории (24
  часа, «для близких» — только им, «Кто смотрел» — только автору).
- Лента — свои записи и тех, на кого подписаны; «Интересное» — записи
  открытых профилей остальных.
- Сообщества (группы с записями, витриной и сведениями; записи видят все,
  писать — участникам), PRISM (короткие видео), «Актуальное» (подборки
  своих историй — живут, пока их не удалят).
- Пока на телефонах (не на сервере): «Вместе», полка, итоги, лента
  активности.

## Устройство кода

| Файл | Что |
| --- | --- |
| `Core.kt` | Вся логика контракта: маршруты, проверки, события живого канала. Без Ktor — проверяется тестами напрямую |
| `Main.kt` | Ktor (Netty): HTTP → `Core.handle`, WebSocket `/v1/realtime` → `Hub` |
| `Store.kt` | Интерфейс хранилища; `PgStore.kt` — PostgreSQL (JDBC), `MemoryStore.kt` — в памяти |
| `resources/schema.sql` | Таблицы; применяется при каждом запуске (только `IF NOT EXISTS`) |
| `Hub.kt` | Кто сейчас на связи по WebSocket |
| `Crypto.kt` | Пароли, токены |
| `Push.kt` | Пуши: FCM HTTP v1 (JWT подписывается ключом сервисного аккаунта) |

Проверки: `./gradlew -p server test` из корня репозитория.
`CoreTest`, `SocialTest`, `ModerationTest` и `CommunityTest` — весь контракт на хранилище в памяти;
`PgStoreTest`, `PgSocialTest`, `PgModerationTest` и `PgCommunityTest` — те же проверки на PostgreSQL (если задан `TEST_DATABASE_URL`; в CI —
`.github/workflows/server.yml`, там же собирается и запускается
Docker-образ). Без Docker: `DATABASE_URL` не задан — сервер держит всё в
памяти (`./gradlew -p server run`, порт 8080).

## Звонки и эфиры

Пока демо. Для настоящих — [LiveKit Cloud](https://livekit.io/pricing)
(план Build бесплатный, без карты): сервер Memory должен будет выдавать
телефонам токены LiveKit — это отдельный шаг.
