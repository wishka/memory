package ru.wishka.memory.server

/**
 * Модерация в браузере: https://<сервер>/admin. Страница входит обычным
 * API (/v1/auth/login), токен держит только в памяти вкладки; жалобы и
 * решения — /v1/admin/… (только логинам из ADMINS). Тексты жалоб выводятся
 * через textContent — разметка из них не исполняется.
 */
object AdminPage {
    val HTML = """
<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>Memory · модерация</title>
<style>
  :root { --bg:#0f1024; --card:#1b1c3a; --ink:#ecebff; --mist:#a3a1c9; --lilac:#b79cff; --pink:#ff8fb8; --ok:#7fe3b0; }
  * { box-sizing: border-box; }
  body { margin:0; background:var(--bg); color:var(--ink); font:15px/1.45 -apple-system, system-ui, sans-serif; }
  main { max-width: 860px; margin: 0 auto; padding: 24px 16px 48px; }
  h1 { font-size: 22px; margin: 0 0 4px; }
  .sub { color: var(--mist); margin: 0 0 20px; }
  .card { background: var(--card); border-radius: 16px; padding: 16px; margin: 0 0 12px; }
  input, select, button { font: inherit; border-radius: 10px; border: 1px solid #34366a; background: #14152e; color: var(--ink); padding: 9px 12px; }
  button { cursor: pointer; background: #2a2c5a; border-color: transparent; }
  button.primary { background: var(--lilac); color: #14152e; font-weight: 600; }
  button.danger { background: var(--pink); color: #14152e; font-weight: 600; }
  .row { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
  .meta { color: var(--mist); font-size: 13px; }
  .text { white-space: pre-wrap; word-break: break-word; margin: 8px 0; padding: 10px 12px; background: #14152e; border-radius: 10px; }
  .tag { display: inline-block; padding: 2px 8px; border-radius: 999px; background: #2a2c5a; font-size: 12px; }
  .err { color: var(--pink); }
  .done { color: var(--ok); }
  [hidden] { display: none !important; }
</style>
</head>
<body>
<main>
  <h1>Memory · модерация</h1>
  <p class="sub">Жалобы пользователей. Решение — в течение суток (требование App Store и Google Play).</p>

  <form id="login" class="card row">
    <input id="lg" placeholder="логин" autocomplete="username" required>
    <input id="pw" type="password" placeholder="пароль" autocomplete="current-password" required>
    <button class="primary">Войти</button>
    <span id="lerr" class="err"></span>
  </form>

  <section id="panel" hidden>
    <div class="row" style="margin-bottom:12px">
      <select id="status">
        <option value="open">Ждут решения</option>
        <option value="all">Все</option>
      </select>
      <button id="reload">Обновить</button>
      <span id="count" class="meta"></span>
    </div>
    <div id="list"></div>
  </section>
</main>
<script>
(function () {
  var token = null;
  var KINDS = { post: "Запись", comment: "Комментарий", story: "История", prism: "PRISM", message: "Сообщение", person: "Профиль" };
  var REASONS = { spam: "Спам", abuse: "Оскорбления", hate: "Ненависть", adult: "Откровенное", illegal: "Незаконное",
    minors: "Опасно для детей", self_harm: "Самоповреждение", copyright: "Авторские права", impersonation: "Выдаёт себя за другого", other: "Другое" };
  var STATUS = { open: "ждёт", dismissed: "оставлено", removed: "удалено", banned: "автор заблокирован" };

  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }

  function api(method, path, body) {
    var opts = { method: method, headers: { "Content-Type": "application/json" } };
    if (token) opts.headers.Authorization = "Bearer " + token;
    if (body) opts.body = JSON.stringify(body);
    return fetch(path, opts).then(function (r) {
      if (r.status === 401) { token = null; show(false); throw new Error("Вход истёк — войдите снова"); }
      if (!r.ok) return r.json().catch(function () { return {}; }).then(function (j) { throw new Error(j.error || ("Ошибка " + r.status)); });
      return r.status === 204 ? {} : r.json();
    });
  }

  function show(on) {
    document.getElementById("login").hidden = on;
    document.getElementById("panel").hidden = !on;
  }

  document.getElementById("login").addEventListener("submit", function (e) {
    e.preventDefault();
    var lerr = document.getElementById("lerr");
    lerr.textContent = "";
    api("POST", "/v1/auth/login", {
      login: document.getElementById("lg").value, password: document.getElementById("pw").value,
      device: { name: "Модерация", platform: "web" }
    }).then(function (t) {
      token = t.access;
      document.getElementById("pw").value = "";
      show(true);
      load();
    }).catch(function (x) { lerr.textContent = x.message; });
  });

  document.getElementById("reload").addEventListener("click", load);
  document.getElementById("status").addEventListener("change", load);

  function load() {
    var list = document.getElementById("list");
    var status = document.getElementById("status").value;
    api("GET", "/v1/admin/reports?status=" + status).then(function (j) {
      list.textContent = "";
      document.getElementById("count").textContent = j.items.length ? ("Жалоб: " + j.items.length) : "Жалоб нет ✦";
      j.items.forEach(function (r) { list.appendChild(card(r)); });
    }).catch(function (x) { list.textContent = ""; list.appendChild(el("p", "err", x.message)); });
  }

  function card(r) {
    var c = el("div", "card");
    var head = el("div", "row");
    head.appendChild(el("span", "tag", KINDS[r.kind] || r.kind));
    head.appendChild(el("strong", null, REASONS[r.reason] || r.reason));
    head.appendChild(el("span", "meta", new Date(r.created).toLocaleString("ru-RU") + " · от @" + r.reporter));
    c.appendChild(head);
    var who = "Автор: " + r.author.name + " (@" + r.author.login + ", №" + r.author.id + ")" + (r.author.banned ? " — заблокирован" : "")
      + (r.verified === false ? " — со слов жалобщика, сервер не проверил" : "");
    c.appendChild(el("div", "meta", who));
    var text = r.current != null ? r.current : r.preview;
    if (text) c.appendChild(el("div", "text", text));
    else if (r.kind !== "person") c.appendChild(el("div", "meta", "Содержимого на сервере уже нет"));
    if (r.note) c.appendChild(el("div", "meta", "Пояснение: " + r.note));
    if (r.status !== "open") {
      c.appendChild(el("div", "done", "Решено: " + (STATUS[r.status] || r.status)));
      return c;
    }
    var actions = el("div", "row");
    actions.style.marginTop = "10px";
    actions.appendChild(button("Оставить", "", "dismiss"));
    if (r.kind === "post" || r.kind === "comment" || r.kind === "story") actions.appendChild(button("Удалить", "primary", "remove"));
    if (r.verified !== false) actions.appendChild(button("Удалить и заблокировать автора", "danger", "ban"));
    c.appendChild(actions);

    function button(label, cls, action) {
      var b = el("button", cls, label);
      b.addEventListener("click", function () {
        if (action === "ban" && !confirm("Заблокировать @" + r.author.login + "? Он не сможет войти.")) return;
        api("POST", "/v1/admin/reports/" + r.id, { action: action }).then(load).catch(function (x) { alert(x.message); });
      });
      return b;
    }
    return c;
  }
})();
</script>
</body>
</html>
""".trimIndent()
}
