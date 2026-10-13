package ru.wishka.memory.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ru.wishka.memory.ui.Snapshot
import java.io.File

/**
 * Записи, переписка, комментарии, группы и закладки — на телефоне, в
 * файле files/state-<логин>.json. Пока у Memory нет сервера, так своё не
 * пропадает после перезапуска. У каждого аккаунта на телефоне свой файл.
 *
 * Люди хранятся номером: при чтении номер превращается обратно в человека
 * из демо-данных, «я» — в вошедшего человека.
 */
class StateStore(private val context: Context) {

    private fun file(login: String) = File(context.filesDir, "state-${login.filter { it.isLetterOrDigit() || it in "._" }}.json")

    fun load(login: String, me: User): Snapshot? = runCatching {
        val f = file(login)
        if (!f.exists()) return null
        val root = JSONObject(f.readText())
        if (root.optInt("version") != VERSION) return null
        Codec(me).snapshot(root)
    }.getOrNull()

    /** Запись — во временный файл и замена целиком: оборванная запись не испортит сохранённое. */
    fun save(login: String, snapshot: Snapshot, me: User) {
        val target = file(login)
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(Codec(me).json(snapshot).toString())
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    /** Удалить сохранённое аккаунта — при удалении аккаунта. */
    fun delete(login: String) {
        file(login).delete()
    }

    private class Codec(private val me: User) {
        private fun person(id: Int): User = if (id == me.id) me else Demo.person(id) ?: User(id, "Неизвестный", "unknown")

        fun json(s: Snapshot): JSONObject = JSONObject()
            .put("version", VERSION)
            .put("nextId", s.nextId)
            .put("notify", notify(s.notifications))
            .put("privacy", privacy(s.privacy))
            .put("blocked", JSONArray(s.blocked.toList()))
            .put("tracks", JSONArray(s.tracks.map { track(it) }))
            .put("playlists", JSONArray(s.playlists.map { playlist(it) }))
            .put("reportedComments", JSONArray(s.reportedComments.toList()))
            .put("stickers", JSONArray(s.stickers.map { sticker(it) }))
            .put("profilePins", JSONArray(s.profilePins))
            .put("following", JSONArray(s.following.toList()))
            .put("posts", JSONArray(s.posts.map { post(it) }))
            .put("chats", JSONArray(s.chats.map { chat(it) }))
            .put("groups", JSONArray(s.groups.map { group(it) }))
            .put("prisms", JSONArray(s.prisms.map { prism(it) }))
            .put("checkInTemplates", JSONArray(s.checkInTemplates.map { template(it) }))
            .put("followRequests", JSONArray(s.followRequests))
            .put("sentRequests", JSONArray(s.sentRequests.toList()))
            .put("recapNotified", JSONArray(s.recapNotified.toList()))
            .put("together", s.together?.let { together(it) } ?: JSONObject.NULL)
            .put("shelfMarks", JSONArray(s.shelfMarks.map { m ->
                JSONObject().put("activity", m.activity).put("subject", m.subject).put("rating", m.rating).put("status", m.status).put("at", m.at)
            }))
            .put("dismissedRecs", JSONArray(s.dismissedRecs.toList()))
            .put("growth", JSONArray(s.growth.map { e -> JSONObject().put("type", e.type.name).put("at", e.at).put("detail", e.detail) }))
            .put("invitedBy", s.invitedBy?.let { i ->
                JSONObject().put("code", i.code).put("action", i.action.id).put("targetId", i.targetId).put("from", i.from)
            } ?: JSONObject.NULL)
            .put("memorySeen", s.memorySeen)
            .put("stickerPacks", JSONArray(s.stickerPacks.map { JSONObject().put("id", it.id).put("name", it.name) }))
            .put("backdrop", backdrop(s.backdrop))
            .put("recentGifs", JSONArray(s.recentGifs.map { attachment(it) }))
            .put("reports", JSONArray(s.reports.map { report(it) }))
            .put("guideRead", JSONArray(s.guideRead.toList()))
            .put("guideHidden", s.guideHidden)
            .put("subscription", JSONObject()
                .put("tier", s.subscription.tier.id).put("period", s.subscription.period.id)
                .put("until", s.subscription.until).put("trial", s.subscription.trial)
                .put("autoRenew", s.subscription.autoRenew).put("trialUsed", s.subscription.trialUsed))

        fun snapshot(o: JSONObject) = Snapshot(
            posts = o.getJSONArray("posts").objects().map { post(it) },
            chats = o.getJSONArray("chats").objects().map { chat(it) },
            groups = o.getJSONArray("groups").objects().map { group(it) },
            prisms = o.getJSONArray("prisms").objects().map { prism(it) },
            // «following» теперь — друзья. Старые «одобренные подписчики» (approvedFollowers) — тоже друзья
            following = o.getJSONArray("following").let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } +
                o.optJSONArray("approvedFollowers").ints(),
            // Старый файл: только общий выключатель «notifications»
            notifications = o.optJSONObject("notify")?.let { notify(it) }
                ?: Notifications(all = o.optBoolean("notifications", true)),
            nextId = o.optLong("nextId", 10_000),
            privacy = o.optJSONObject("privacy")?.let { privacy(it) } ?: Privacy(),
            blocked = o.optJSONArray("blocked")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: emptySet(),
            tracks = o.optJSONArray("tracks").objects().map { track(it) },
            playlists = o.optJSONArray("playlists").objects().map { playlist(it) },
            stickers = o.optJSONArray("stickers").objects().map { sticker(it) },
            profilePins = o.optJSONArray("profilePins")?.let { a -> (0 until a.length()).map { a.getLong(it) } } ?: emptyList(),
            reportedComments = o.optJSONArray("reportedComments")?.let { a -> (0 until a.length()).map { a.getLong(it) }.toSet() } ?: emptySet(),
            checkInTemplates = o.optJSONArray("checkInTemplates").objects().map { template(it) },
            followRequests = o.optJSONArray("followRequests").ints(),
            sentRequests = o.optJSONArray("sentRequests").ints().toSet(),
            recapNotified = o.optJSONArray("recapNotified")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
            together = o.optJSONObject("together")?.let { runCatching { together(it) }.getOrNull() },
            shelfMarks = o.optJSONArray("shelfMarks").objects().map { m ->
                ShelfMark(m.optString("activity", "movie"), m.optString("subject"), m.optInt("rating"), m.optString("status"), m.optLong("at"))
            },
            dismissedRecs = o.optJSONArray("dismissedRecs")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
            growth = o.optJSONArray("growth").objects().mapNotNull { e ->
                GrowthEventType.entries.firstOrNull { it.name == e.optString("type") }?.let { GrowthEvent(it, e.optLong("at"), e.optString("detail")) }
            },
            invitedBy = o.optJSONObject("invitedBy")?.let { i ->
                Invite(i.optString("code"), InviteAction.of(i.optString("action")), i.optString("targetId"), i.optString("from"))
            },
            memorySeen = o.optString("memorySeen"),
            stickerPacks = o.optJSONArray("stickerPacks").objects().map { StickerPack(it.optString("id"), it.optString("name")) },
            backdrop = o.optJSONObject("backdrop")?.let { backdrop(it) } ?: Backdrop.DEFAULT,
            recentGifs = o.optJSONArray("recentGifs").objects().map { attachment(it) },
            reports = o.optJSONArray("reports").objects().map { report(it) },
            guideRead = o.optJSONArray("guideRead")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
            guideHidden = o.optBoolean("guideHidden", false),
            subscription = o.optJSONObject("subscription")?.let { p ->
                Subscription(
                    PlanTier.of(p.optString("tier")), BillingPeriod.of(p.optString("period")), p.optLong("until"),
                    p.optBoolean("trial"), p.optBoolean("autoRenew"), p.optBoolean("trialUsed"),
                )
            } ?: Subscription(),
        )

        private fun report(r: Report) = JSONObject()
            .put("id", r.id).put("kind", r.target.kind.id).put("targetId", r.target.id)
            .put("authorId", r.target.authorId).put("authorName", r.target.authorName)
            .put("chatId", r.target.chatId).put("preview", r.target.preview)
            .put("reason", r.reason.id).put("note", r.note).put("at", r.createdAt)

        private fun report(o: JSONObject) = Report(
            o.optLong("id"),
            ReportTarget(
                ReportKind.of(o.optString("kind")), o.optLong("targetId"), o.optInt("authorId"),
                o.optString("authorName"), o.optInt("chatId"), o.optString("preview"),
            ),
            ReportReason.of(o.optString("reason")), o.optString("note"), o.optLong("at"),
        )

        // ---------- Вместе ----------

        private fun strings(l: List<String>) = JSONArray(l)
        private fun JSONArray?.strs(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }
        private fun latLon(p: LatLon) = JSONArray().put(p.lat).put(p.lon)
        private fun latLon(a: JSONArray) = LatLon(a.getDouble(0), a.getDouble(1))
        private fun JSONObject.optAttachment(key: String) = optJSONObject(key)?.let { attachment(it) }

        private fun stroke(d: DrawStroke) = JSONObject()
            .put("c", d.color).put("w", d.width.toDouble()).put("e", d.eraser)
            .put("p", JSONArray().apply { d.points.forEach { (x, y) -> put(x.toDouble()); put(y.toDouble()) } })

        private fun stroke(o: JSONObject): DrawStroke {
            val a = o.getJSONArray("p")
            val pts = (0 until a.length() / 2).map { a.getDouble(it * 2).toFloat() to a.getDouble(it * 2 + 1).toFloat() }
            return DrawStroke(pts, o.getInt("c"), o.getDouble("w").toFloat(), o.optBoolean("e"))
        }

        private fun together(t: TogetherState): JSONObject = JSONObject()
            .put("balance", t.balance)
            .put("myTeach", strings(t.myTeach)).put("myWant", strings(t.myWant))
            .put("anchor", latLon(t.anchor)).put("anchored", t.anchored)
            .put("goals", JSONArray(t.goals.map { g ->
                JSONObject().put("id", g.id).put("emoji", g.emoji).put("title", g.title).put("description", g.description)
                    .put("unit", g.unit).put("target", g.target).put("total", g.total).put("participants", g.participants)
                    .put("metric", g.metric.name).put("joined", g.joined).put("mine", g.mine).put("authorId", g.authorId)
            }))
            .put("offers", JSONArray(t.offers.map { o ->
                JSONObject().put("personId", o.personId).put("teaches", strings(o.teaches)).put("wants", strings(o.wants)).put("about", o.about)
            }))
            .put("deals", JSONArray(t.deals.map { d ->
                JSONObject().put("id", d.id).put("personId", d.personId).put("give", d.give).put("get", d.get)
                    .put("minutes", d.minutes).put("status", d.status.name).put("createdAt", d.createdAt)
            }))
            .put("journeys", JSONArray(t.journeys.map { j ->
                JSONObject().put("id", j.id).put("authorId", j.authorId).put("emoji", j.emoji).put("title", j.title)
                    .put("days", j.days).put("createdAt", j.createdAt)
                    .put("entries", JSONArray(j.entries.map { e ->
                        JSONObject().put("id", e.id).put("day", e.day).put("text", e.text).put("createdAt", e.createdAt)
                            .put("media", JSONArray(e.media.map { attachment(it) }))
                            .put("tips", JSONArray(e.tips.map { tp ->
                                JSONObject().put("id", tp.id).put("authorId", tp.authorId).put("text", tp.text)
                                    .put("helpful", tp.helpful).put("markedByMe", tp.markedByMe).put("createdAt", tp.createdAt)
                            }))
                    }))
            }))
            .put("missions", JSONArray(t.missions.map { m ->
                JSONObject().put("id", m.id).put("emoji", m.emoji).put("title", m.title).put("description", m.description)
                    .put("target", m.target).put("joined", m.joined).put("unlockKm", m.unlockKm).put("authorId", m.authorId)
                    .put("finds", JSONArray(m.finds.map { f ->
                        JSONObject().put("id", f.id).put("authorId", f.authorId).put("note", f.note).put("at", latLon(f.at))
                            .put("photo", f.photo?.let { attachment(it) } ?: JSONObject.NULL).put("createdAt", f.createdAt)
                    }))
            }))
            .put("caches", JSONArray(t.caches.map { c ->
                JSONObject().put("id", c.id).put("authorId", c.authorId).put("title", c.title).put("hint", c.hint)
                    .put("at", latLon(c.at)).put("difficulty", c.difficulty).put("size", c.size).put("createdAt", c.createdAt)
                    .put("foundByMe", c.foundByMe)
                    .put("logs", JSONArray(c.logs.map { l ->
                        JSONObject().put("id", l.id).put("authorId", l.authorId).put("text", l.text).put("createdAt", l.createdAt)
                    }))
            }))
            .put("outings", JSONArray(t.outings.map { o ->
                JSONObject().put("id", o.id).put("organizerId", o.organizerId).put("emoji", o.emoji).put("title", o.title)
                    .put("description", o.description).put("place", o.place).put("startsAt", o.startsAt).put("capacity", o.capacity)
                    .put("participants", JSONArray(o.participants)).put("at", o.at?.let { latLon(it) } ?: JSONObject.NULL)
            }))
            .put("drawings", JSONArray(t.drawings.map { d ->
                JSONObject().put("id", d.id).put("title", d.title).put("maxTurns", d.maxTurns)
                    .put("waitingFor", d.waitingFor ?: JSONObject.NULL).put("createdAt", d.createdAt)
                    .put("turns", JSONArray(d.turns.map { tr ->
                        JSONObject().put("authorId", tr.authorId).put("strokes", JSONArray(tr.strokes.map { stroke(it) }))
                    }))
            }))
            .put("shares", JSONArray(t.shares.map { i ->
                JSONObject().put("id", i.id).put("kind", i.kind.name).put("title", i.title).put("description", i.description)
                    .put("ownerId", i.ownerId).put("holderId", i.holderId).put("status", i.status.name)
                    .put("requestedBy", i.requestedBy ?: JSONObject.NULL).put("history", JSONArray(i.history))
                    .put("helpers", JSONArray(i.helpers)).put("photo", i.photo?.let { attachment(it) } ?: JSONObject.NULL)
                    .put("createdAt", i.createdAt)
            }))

        private fun together(o: JSONObject) = TogetherState(
            balance = o.optInt("balance", 60),
            myTeach = o.optJSONArray("myTeach").strs(),
            myWant = o.optJSONArray("myWant").strs(),
            anchor = o.optJSONArray("anchor")?.let { latLon(it) } ?: LatLon.DEFAULT,
            anchored = o.optBoolean("anchored"),
            goals = o.optJSONArray("goals").objects().map { g ->
                CommunityGoal(
                    g.getString("id"), g.optString("emoji"), g.optString("title"), g.optString("description"), g.optString("unit"),
                    g.optDouble("target", 1.0), g.optDouble("total", 0.0), g.optInt("participants"),
                    GoalMetric.entries.firstOrNull { it.name == g.optString("metric") } ?: GoalMetric.Other,
                    g.optBoolean("joined"), g.optDouble("mine", 0.0), g.optInt("authorId", 0),
                )
            },
            offers = o.optJSONArray("offers").objects().map { f ->
                SkillOffer(f.getInt("personId"), f.optJSONArray("teaches").strs(), f.optJSONArray("wants").strs(), f.optString("about"))
            },
            deals = o.optJSONArray("deals").objects().map { d ->
                SkillDeal(
                    d.getLong("id"), d.getInt("personId"), d.optString("give"), d.optString("get"), d.optInt("minutes", 30),
                    DealStatus.entries.firstOrNull { it.name == d.optString("status") } ?: DealStatus.Proposed, d.optLong("createdAt"),
                )
            },
            journeys = o.optJSONArray("journeys").objects().map { j ->
                Journey(
                    j.getLong("id"), j.getInt("authorId"), j.optString("emoji"), j.optString("title"), j.optInt("days", 30),
                    j.optJSONArray("entries").objects().map { e ->
                        JourneyEntry(
                            e.getLong("id"), e.optInt("day", 1), e.optString("text"),
                            e.optJSONArray("media").objects().map { attachment(it) }, e.optLong("createdAt"),
                            e.optJSONArray("tips").objects().map { tp ->
                                Tip(tp.getLong("id"), tp.getInt("authorId"), tp.optString("text"), tp.optInt("helpful"), tp.optBoolean("markedByMe"), tp.optLong("createdAt"))
                            },
                        )
                    },
                    j.optLong("createdAt"),
                )
            },
            missions = o.optJSONArray("missions").objects().map { m ->
                Mission(
                    m.getString("id"), m.optString("emoji"), m.optString("title"), m.optString("description"), m.optInt("target", 5),
                    m.optBoolean("joined"),
                    m.optJSONArray("finds").objects().map { f ->
                        MissionFind(f.getLong("id"), f.getInt("authorId"), f.optString("note"), latLon(f.getJSONArray("at")), f.optAttachment("photo"), f.optLong("createdAt"))
                    },
                    m.optDouble("unlockKm", 0.0), m.optInt("authorId", 0),
                )
            },
            caches = o.optJSONArray("caches").objects().map { c ->
                Cache(
                    c.getLong("id"), c.getInt("authorId"), c.optString("title"), c.optString("hint"), latLon(c.getJSONArray("at")),
                    c.optInt("difficulty", 2), c.optString("size"), c.optLong("createdAt"),
                    c.optJSONArray("logs").objects().map { l -> CacheLog(l.getLong("id"), l.getInt("authorId"), l.optString("text"), l.optLong("createdAt")) },
                    c.optBoolean("foundByMe"),
                )
            },
            outings = o.optJSONArray("outings").objects().map { u ->
                Outing(
                    u.getLong("id"), u.getInt("organizerId"), u.optString("emoji"), u.optString("title"), u.optString("description"),
                    u.optString("place"), u.optLong("startsAt"), u.optInt("capacity", 10), u.optJSONArray("participants").ints(),
                    u.optJSONArray("at")?.let { latLon(it) },
                )
            },
            drawings = o.optJSONArray("drawings").objects().map { d ->
                CollabDrawing(
                    d.getLong("id"), d.optString("title"), d.optInt("maxTurns", 4),
                    d.optJSONArray("turns").objects().map { tr -> DrawingTurn(tr.getInt("authorId"), tr.optJSONArray("strokes").objects().map { stroke(it) }) },
                    if (d.isNull("waitingFor")) null else d.optInt("waitingFor"), d.optLong("createdAt"),
                )
            },
            shares = o.optJSONArray("shares").objects().map { i ->
                ShareItem(
                    i.getLong("id"), ShareKind.entries.firstOrNull { it.name == i.optString("kind") } ?: ShareKind.Book,
                    i.optString("title"), i.optString("description"), i.getInt("ownerId"), i.getInt("holderId"),
                    ShareStatus.entries.firstOrNull { it.name == i.optString("status") } ?: ShareStatus.Available,
                    if (i.isNull("requestedBy")) null else i.optInt("requestedBy"),
                    i.optJSONArray("history").ints(), i.optJSONArray("helpers").ints(), i.optAttachment("photo"), i.optLong("createdAt"),
                )
            },
        )

        private fun template(t: CheckInTemplate) = JSONObject()
            .put("id", t.id).put("emoji", t.emoji).put("title", t.title).put("unit", t.unit).put("hint", t.hint)

        private fun template(o: JSONObject) = CheckInTemplate(
            o.getString("id"), o.optString("emoji", "⭐"), o.optString("title"), o.optString("unit"), o.optString("hint"),
        )

        private fun checkIn(c: CheckIn) = JSONObject()
            .put("activity", c.activity).put("emoji", c.emoji).put("title", c.title)
            .put("subject", c.subject).put("amount", c.amount).put("unit", c.unit)
            .put("rating", c.rating).put("status", c.status).put("minutes", c.minutes)

        private fun checkIn(o: JSONObject) = CheckIn(
            o.getString("activity"), o.optString("emoji"), o.optString("title"),
            o.optString("subject"), o.optDouble("amount", 0.0), o.optString("unit"),
            rating = o.optInt("rating", 0), status = o.optString("status"), minutes = o.optInt("minutes", 0),
        )

        private fun track(t: MusicTrack) = JSONObject()
            .put("id", t.id).put("title", t.title).put("artist", t.artist).put("path", t.path)
            .put("durationMs", t.durationMs).put("addedAt", t.addedAt)
            .put("license", t.license).put("sourceUrl", t.sourceUrl)

        private fun track(o: JSONObject) = MusicTrack(
            o.getString("id"), o.optString("title"), o.optString("artist"), o.getString("path"),
            o.optLong("durationMs"), o.optLong("addedAt"),
            license = o.optString("license"), sourceUrl = o.optString("sourceUrl"),
        )

        private fun sticker(t: Sticker) = JSONObject()
            .put("id", t.id).put("path", t.path).put("kind", t.kind.name).put("createdAt", t.createdAt)
            .put("animation", t.animation).put("frames", JSONArray(t.frames)).put("fps", t.fps).put("packId", t.packId)

        private fun sticker(o: JSONObject) = Sticker(
            o.getString("id"), o.getString("path"),
            StickerKind.entries.firstOrNull { it.name == o.optString("kind") } ?: StickerKind.Badge,
            o.optLong("createdAt"),
            o.optString("animation"), o.optJSONArray("frames").strs(), o.optInt("fps"), o.optString("packId"),
        )

        private fun playlist(p: Playlist) = JSONObject()
            .put("id", p.id).put("name", p.name).put("trackIds", JSONArray(p.trackIds))

        private fun playlist(o: JSONObject) = Playlist(
            o.getLong("id"), o.optString("name"),
            o.optJSONArray("trackIds")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
        )

        private fun notify(n: Notifications) = JSONObject()
            .put("all", n.all).put("messages", n.messages).put("reactions", n.reactions)
            .put("groups", n.groups).put("follows", n.follows)
            .put("friends", n.friends).put("recap", n.recap).put("prism", n.prism)

        private fun notify(o: JSONObject) = Notifications(
            all = o.optBoolean("all", true), messages = o.optBoolean("messages", true),
            reactions = o.optBoolean("reactions", true), groups = o.optBoolean("groups", true),
            follows = o.optBoolean("follows", true),
            friends = o.optBoolean("friends", true),
            recap = o.optBoolean("recap", true),
            prism = o.optBoolean("prism", true),
        )

        private fun privacy(p: Privacy) = JSONObject()
            .put("profileInfo", p.profileInfo.name).put("followLists", p.followLists.name)
            .put("messages", p.messages.name).put("comments", p.comments.name)
            .put("showOnline", p.showOnline).put("showBirthday", p.showBirthday)
            .put("closedProfile", p.closedProfile).put("personalRecs", p.personalRecs)

        private fun privacy(o: JSONObject) = Privacy(
            profileInfo = audience(o.optString("profileInfo")),
            followLists = audience(o.optString("followLists")),
            messages = audience(o.optString("messages")),
            comments = audience(o.optString("comments")),
            showOnline = o.optBoolean("showOnline", true),
            showBirthday = o.optBoolean("showBirthday", true),
            closedProfile = o.optBoolean("closedProfile", false),
            personalRecs = o.optBoolean("personalRecs", true),
        )

        private fun audience(name: String) = Audience.entries.firstOrNull { it.name == name } ?: Audience.Everyone

        private fun attachment(a: Attachment) = JSONObject()
            .put("id", a.id).put("kind", a.kind.name).put("path", a.path)
            .put("name", a.name).put("size", a.size).put("mime", a.mime)
            .put("voice", a.voice).put("durationMs", a.durationMs).put("waveform", JSONArray(a.waveform))
            .put("sticker", a.sticker)
            .put("animation", a.animation).put("frames", JSONArray(a.frames)).put("fps", a.fps)

        private fun attachment(o: JSONObject) = Attachment(
            o.getString("id"), MediaKind.valueOf(o.getString("kind")), o.getString("path"),
            o.optString("name"), o.optLong("size"), o.optString("mime"),
            voice = o.optBoolean("voice"), durationMs = o.optLong("durationMs"),
            waveform = o.optJSONArray("waveform").ints(), sticker = o.optBoolean("sticker"),
            animation = o.optString("animation"), frames = o.optJSONArray("frames").strs(), fps = o.optInt("fps"),
        )

        private fun comment(c: Comment) = JSONObject()
            .put("id", c.id).put("author", c.author.id).put("text", c.text)
            .put("minutesAgo", c.minutesAgo).put("createdAt", c.createdAt)
            .put("likes", c.likes).put("liked", c.liked).put("replyTo", c.replyTo)
            .put("sticker", c.sticker?.let { attachment(it) } ?: JSONObject.NULL)

        private fun comment(o: JSONObject) = Comment(
            o.getLong("id"), person(o.getInt("author")), o.getString("text"),
            o.optInt("minutesAgo"), o.optLong("createdAt"),
            likes = o.optInt("likes"), liked = o.optBoolean("liked"), replyTo = o.optString("replyTo"),
            sticker = o.optJSONObject("sticker")?.let { attachment(it) },
        )

        private fun post(p: Post) = JSONObject()
            .put("id", p.id).put("author", p.author.id).put("text", p.text)
            .put("minutesAgo", p.minutesAgo).put("artSeed", p.artSeed ?: JSONObject.NULL)
            .put("likes", p.likes).put("liked", p.liked).put("comments", p.comments)
            .put("createdAt", p.createdAt)
            .put("checkIn", p.checkIn?.let { checkIn(it) } ?: JSONObject.NULL)
            .put("poll", p.poll?.let { poll(it) } ?: JSONObject.NULL)
            .put("media", JSONArray(p.media.map { attachment(it) }))
            .put("commentList", JSONArray(p.commentList.map { comment(it) }))

        private fun post(o: JSONObject) = Post(
            id = o.getLong("id"),
            author = person(o.getInt("author")),
            text = o.getString("text"),
            minutesAgo = o.optInt("minutesAgo"),
            artSeed = if (o.isNull("artSeed")) null else o.getInt("artSeed"),
            likes = o.optInt("likes"),
            liked = o.optBoolean("liked"),
            comments = o.optInt("comments"),
            media = o.optJSONArray("media").objects().map { attachment(it) },
            commentList = o.optJSONArray("commentList").objects().map { comment(it) },
            createdAt = o.optLong("createdAt"),
            checkIn = o.optJSONObject("checkIn")?.let { checkIn(it) },
            poll = o.optJSONObject("poll")?.let { poll(it) },
        )

        private fun message(m: Message) = JSONObject()
            .put("id", m.id).put("fromMe", m.fromMe).put("text", m.text).put("time", m.time)
            .put("attachments", JSONArray(m.attachments.map { attachment(it) }))
            .put("replyTo", m.replyTo?.let { JSONObject().put("messageId", it.messageId).put("author", it.author).put("preview", it.preview) } ?: JSONObject.NULL)
            .put("forwardedFrom", m.forwardedFrom ?: JSONObject.NULL)
            .put("poll", m.poll?.let { poll(it) } ?: JSONObject.NULL)
            .put("place", m.place?.let { JSONObject().put("lat", it.lat).put("lon", it.lon).put("title", it.title) } ?: JSONObject.NULL)
            .put("product", m.product?.let {
                JSONObject().put("groupId", it.groupId).put("productId", it.productId).put("title", it.title)
                    .put("price", it.price).put("photo", it.photo).put("seed", it.seed)
            } ?: JSONObject.NULL)

        private fun message(o: JSONObject) = Message(
            o.getLong("id"), o.getBoolean("fromMe"), o.getString("text"), o.getString("time"),
            o.optJSONArray("attachments").objects().map { attachment(it) },
            replyTo = o.optJSONObject("replyTo")?.let { ReplyRef(it.getLong("messageId"), it.optString("author"), it.optString("preview")) },
            forwardedFrom = if (o.isNull("forwardedFrom")) null else o.optString("forwardedFrom"),
            poll = o.optJSONObject("poll")?.let { poll(it) },
            place = o.optJSONObject("place")?.let { SharedPlace(it.optDouble("lat", 0.0), it.optDouble("lon", 0.0), it.optString("title")) },
            product = o.optJSONObject("product")?.let {
                ProductRef(it.optInt("groupId"), it.optLong("productId"), it.optString("title"), it.optLong("price"), it.optString("photo"), it.optInt("seed"))
            },
        )

        private fun poll(p: Poll) = JSONObject()
            .put("id", p.id).put("question", p.question).put("multiple", p.multiple).put("closed", p.closed)
            .put("mine", JSONArray(p.mine.toList()))
            .put("options", JSONArray(p.options.map { JSONObject().put("text", it.text).put("votes", it.votes) }))

        private fun poll(o: JSONObject) = Poll(
            o.optLong("id"), o.optString("question"),
            o.optJSONArray("options").objects().map { PollOption(it.optString("text"), it.optInt("votes")) },
            o.optBoolean("multiple"), o.optJSONArray("mine").ints().toSet(), o.optBoolean("closed"),
        )

        fun backdrop(b: Backdrop) = JSONObject().put("style", b.style).put("photo", b.photo).put("dim", b.dim.toDouble())

        fun backdrop(o: JSONObject) = Backdrop(o.optString("style", BackdropStyle.Cosmos.id), o.optString("photo"), o.optDouble("dim", 0.35).toFloat())

        private fun chat(c: Chat) = JSONObject()
            .put("id", c.id).put("peer", c.peer.id).put("unread", c.unread)
            .put("pinnedId", c.pinnedId ?: JSONObject.NULL)
            .put("onTop", c.onTop).put("muted", c.muted)
            .put("backdrop", c.backdrop?.let { backdrop(it) } ?: JSONObject.NULL)
            .put("messages", JSONArray(c.messages.map { message(it) }))

        private fun chat(o: JSONObject) = Chat(
            o.getInt("id"), person(o.getInt("peer")),
            o.getJSONArray("messages").objects().map { message(it) },
            unread = o.optInt("unread"),
            pinnedId = if (o.isNull("pinnedId")) null else o.getLong("pinnedId"),
            onTop = o.optBoolean("onTop"),
            muted = o.optBoolean("muted"),
            backdrop = o.optJSONObject("backdrop")?.let { backdrop(it) },
        )

        private fun group(g: Group) = JSONObject()
            .put("id", g.id).put("name", g.name).put("description", g.description).put("topic", g.topic)
            .put("members", g.members).put("seed", g.seed).put("joined", g.joined).put("mine", g.mine)
            .put("muted", g.muted)
            .put("cover", g.cover?.let { attachment(it) } ?: JSONObject.NULL)
            .put("pinnedPostId", g.pinnedPostId ?: JSONObject.NULL)
            .put("onlyOwnerPosts", g.onlyOwnerPosts)
            .put("ownerId", g.ownerId)
            .put("info", JSONObject().put("about", g.info.about).put("website", g.info.website).put("phone", g.info.phone)
                .put("email", g.info.email).put("address", g.info.address).put("hours", g.info.hours))
            .put("shop", JSONArray(g.shop.map { product(it) }))
            .put("backdrop", g.backdrop?.let { backdrop(it) } ?: JSONObject.NULL)
            .put("accent", g.accent)
            .put("posts", JSONArray(g.posts.map { post(it) }))

        private fun group(o: JSONObject) = Group(
            o.getInt("id"), o.getString("name"), o.optString("description"), o.optString("topic"),
            o.optInt("members"), o.optInt("seed"), o.optBoolean("joined"), o.optBoolean("mine"),
            o.getJSONArray("posts").objects().map { post(it) },
            muted = o.optBoolean("muted"),
            cover = o.optJSONObject("cover")?.let { attachment(it) },
            pinnedPostId = if (o.isNull("pinnedPostId")) null else o.optLong("pinnedPostId"),
            onlyOwnerPosts = o.optBoolean("onlyOwnerPosts"),
            ownerId = o.optInt("ownerId"),
            info = o.optJSONObject("info")?.let {
                GroupInfo(it.optString("about"), it.optString("website"), it.optString("phone"), it.optString("email"), it.optString("address"), it.optString("hours"))
            } ?: GroupInfo(),
            shop = o.optJSONArray("shop").objects().map { product(it) },
            backdrop = o.optJSONObject("backdrop")?.let { backdrop(it) },
            accent = o.optString("accent"),
        )

        private fun product(p: Product) = JSONObject()
            .put("id", p.id).put("title", p.title).put("price", p.price).put("description", p.description)
            .put("photos", JSONArray(p.photos.map { attachment(it) })).put("category", p.category)
            .put("inStock", p.inStock).put("url", p.url).put("seed", p.seed)

        private fun product(o: JSONObject) = Product(
            o.optLong("id"), o.optString("title"), o.optLong("price"), o.optString("description"),
            o.optJSONArray("photos").objects().map { attachment(it) }, o.optString("category"),
            o.optBoolean("inStock", true), o.optString("url"), o.optInt("seed"),
        )

        private fun prism(p: Prism) = JSONObject()
            .put("id", p.id).put("author", p.author.id).put("caption", p.caption).put("music", p.music)
            .put("seed", p.seed).put("likes", p.likes).put("comments", p.comments).put("liked", p.liked)
            .put("durationMs", p.durationMs).put("saved", p.saved)
            .put("video", p.video?.let { attachment(it) } ?: JSONObject.NULL)
            .put("cover", p.cover?.let { attachment(it) } ?: JSONObject.NULL)
            .put("createdAt", p.createdAt)
            .put("commentList", JSONArray(p.commentList.map { comment(it) }))

        private fun prism(o: JSONObject) = Prism(
            o.getLong("id"), person(o.getInt("author")), o.getString("caption"), o.optString("music"),
            o.optInt("seed"), o.optInt("likes"), o.optInt("comments"), o.optBoolean("liked"),
            o.optInt("durationMs", 9_000), o.optBoolean("saved"),
            o.optJSONArray("commentList").objects().map { comment(it) },
            video = o.optJSONObject("video")?.let { attachment(it) },
            cover = o.optJSONObject("cover")?.let { attachment(it) },
            createdAt = o.optLong("createdAt"),
        )

        private fun JSONArray?.ints(): List<Int> =
            if (this == null) emptyList() else (0 until length()).map { getInt(it) }

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
    }

    private companion object {
        /** Меняется, когда меняется устройство файла: старый файл тогда не читается. */
        const val VERSION = 1
    }
}
