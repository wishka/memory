package ru.wishka.memory

import androidx.compose.foundation.layout.statusBarsPadding
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.SideEffect
import ru.wishka.memory.i18n.t
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.wishka.memory.data.ProfileInfo
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.together.TogetherScreen
import ru.wishka.memory.ui.groups.GroupScreen
import ru.wishka.memory.ui.groups.GroupSettingsScreen
import ru.wishka.memory.ui.groups.GroupsScreen
import ru.wishka.memory.ui.person.PersonScreen
import ru.wishka.memory.ui.prism.PrismScreen
import ru.wishka.memory.ui.components.CommentsSheet
import ru.wishka.memory.ui.components.MediaViewer
import ru.wishka.memory.data.StateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import ru.wishka.memory.ui.auth.AuthScreen
import ru.wishka.memory.ui.auth.AuthViewModel
import ru.wishka.memory.ui.stories.StoriesScreen
import ru.wishka.memory.ui.Tab
import ru.wishka.memory.ui.chats.ChatScreen
import ru.wishka.memory.ui.chats.ChatsScreen
import ru.wishka.memory.ui.components.BottomBar
import ru.wishka.memory.ui.components.CosmicBackground
import ru.wishka.memory.ui.feed.FeedScreen
import ru.wishka.memory.ui.profile.ProfileScreen
import ru.wishka.memory.ui.profile.ProfileSectionScreen
import ru.wishka.memory.ui.profile.MusicScreen
import ru.wishka.memory.ui.profile.StickerStudioScreen
import ru.wishka.memory.ui.profile.RecapScreen
import ru.wishka.memory.ui.profile.ShelfScreen
import ru.wishka.memory.ui.prism.create.PrismCreateFlow
import ru.wishka.memory.ui.ProfileSection
import ru.wishka.memory.ui.settings.AccountActions
import ru.wishka.memory.ui.settings.SettingsScreen
import ru.wishka.memory.data.DevicePrefs
import ru.wishka.memory.ui.splash.AtomSplash
import ru.wishka.memory.ui.theme.MemoryTheme
import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.flow.MutableStateFlow
import ru.wishka.memory.data.Nudge
import ru.wishka.memory.push.AppVisibility
import ru.wishka.memory.push.MemoryNotifications
import ru.wishka.memory.push.PushBus
import ru.wishka.memory.ui.components.NudgeBanner
import ru.wishka.memory.ui.components.ConsentGate
import ru.wishka.memory.ui.components.LegalDocViewer
import ru.wishka.memory.ui.components.ReportDialog
import ru.wishka.memory.data.LegalDoc
import ru.wishka.memory.data.SoundPlayer
import ru.wishka.memory.data.SoundPrefs
import ru.wishka.memory.ui.components.EchoDialog
import ru.wishka.memory.ui.components.InviteDialog
import ru.wishka.memory.ui.auth.OnboardingScreen
import ru.wishka.memory.data.Invite
import ru.wishka.memory.data.Invites
import ru.wishka.memory.data.LinkTarget
import ru.wishka.memory.data.Links
import ru.wishka.memory.data.MediaFiles
import ru.wishka.memory.ui.components.LinkActions
import ru.wishka.memory.ui.components.LocalLinkActions
import androidx.compose.runtime.CompositionLocalProvider

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        AppVisibility.foreground = true
        ru.wishka.memory.net.NetWorker.cancel(applicationContext)
    }

    override fun onStop() {
        super.onStop()
        AppVisibility.foreground = false
        ru.wishka.memory.telemetry.TelemetrySetup.flush()
        // Неотправленное уйдёт и при закрытом приложении
        ru.wishka.memory.net.NetWorker.schedule(applicationContext)
    }

    /** Нажали на уведомление, когда приложение уже открыто. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        MemoryNotifications.fromIntent(intent)?.let { openedNudge.value = it }
        intent.data?.toString()?.let(Invites::parse)?.let { openedInvite.value = it }
        intent.data?.toString()?.let(Links::parse)?.let { openedLink.value = it }
    }

    companion object {
        /** Уведомление, по которому открыли приложение, — его откроет AppRoot. */
        val openedNudge = MutableStateFlow<Nudge?>(null)

        /** Ссылка-приглашение, по которой открыли приложение: её разберёт MainScreen после входа. */
        val openedInvite = MutableStateFlow<Invite?>(null)

        /** Ссылка на запись, профиль, PRISM, группу или тег — откроется после входа. */
        val openedLink = MutableStateFlow<LinkTarget?>(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Только при запуске нажатием на уведомление: не при повороте и не из «Недавних»
        val fromHistory = (intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromHistory) {
            MemoryNotifications.fromIntent(intent)?.let { openedNudge.value = it }
            intent?.data?.toString()?.let(Invites::parse)?.let { openedInvite.value = it }
            intent?.data?.toString()?.let(Links::parse)?.let { openedLink.value = it }
        }
        // Рисуем под системными панелями; значки панелей — светлые
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Язык, размер текста и оформление — до первого экрана, чтобы не мигали
        DevicePrefs(applicationContext).load()
        ru.wishka.memory.ui.theme.ThemeState.systemDark =
            (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) != android.content.res.Configuration.UI_MODE_NIGHT_NO
        setContent {
            MemoryTheme {
                // Значки строки состояния — тёмные на светлом, светлые на тёмном
                val light = ru.wishka.memory.ui.theme.ThemeState.light
                LaunchedEffect(light) {
                    val transparent = android.graphics.Color.TRANSPARENT
                    val style = if (light) SystemBarStyle.light(transparent, transparent) else SystemBarStyle.dark(transparent)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                AppRoot()
            }
        }
    }
}

/** Что сейчас на экране: заставка, вход или само приложение. */
private enum class Stage { Splash, Onboarding, Auth, Consent, Main }

@Composable
fun AppRoot(vm: AppViewModel = viewModel(), authVm: AuthViewModel = viewModel()) {
    val auth by authVm.state.collectAsState()
    // rememberSaveable: при повороте или возврате в приложение заставка
    // не показывается заново — только при холодном запуске
    var splashDone by rememberSaveable { mutableStateOf(false) }

    // Вошедший человек — в профиль и в свои записи; его записи, переписка
    // и закладки — из файла на телефоне
    val account = auth.account
    val context = LocalContext.current
    val store = remember(context) { StateStore(context.applicationContext) }
    val device = remember(context) { DevicePrefs(context.applicationContext) }
    // Есть ли сеть: пропала — очередь ждёт, появилась — отправляет
    val netMonitor = remember(context) { ru.wishka.memory.net.NetMonitor(context.applicationContext) }
    androidx.compose.runtime.DisposableEffect(netMonitor) {
        netMonitor.start()
        onDispose { netMonitor.stop() }
    }
    LaunchedEffect(netMonitor) { netMonitor.online.collect { vm.setDeviceOnline(it) } }
    // Короткие сообщения модели («Слишком часто…») — тостом
    val vmState by vm.state.collectAsState()
    val notice = vmState.notice
    LaunchedEffect(notice) {
        notice?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.dismissNotice()
        }
    }
    // Знакомство «выберите любимое» — один раз на телефоне, до регистрации
    var onboarded by rememberSaveable { mutableStateOf(device.onboarded) }
    var registerNext by rememberSaveable { mutableStateOf(false) }
    var loadedFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(account) {
        loadedFor = null
        if (account != null) {
            // Ключ сквозного шифрования — в зашифрованном хранилище телефона
            vm.attachKeys(ru.wishka.memory.data.auth.SecurePrefs(context.applicationContext))
            vm.setAccount(account.username, account.displayName, account.info)
            val me = vm.state.value.me
            val saved = withContext(Dispatchers.IO) { store.load(account.username, me) }
            saved?.let(vm::restore)
            // Любимое из знакомства — только на полку нового аккаунта (сохранённого ещё нет).
            // Вошли в старый — выбор ждёт следующей регистрации на этом телефоне.
            if (saved == null) {
                device.pendingPicks().takeIf { it.isNotEmpty() }?.let { picks ->
                    vm.addFavorites(picks)
                    device.clearPicks()
                }
            }
            loadedFor = account.username
            // Истории друзей и их «Актуальное» — в демо появляются сами
            vm.ensureDemoStories()
            // Сохранённое на месте — можно считать итоги и оживлять демо
            vm.checkRecapReady()
            vm.startDemoLife()
            // Сеть аккаунта: очередь, загрузки и сохранённое — в своей базе
            vm.setMediaQuality(device.mediaQuality)
            vm.setDemoOffline(device.demoOffline)
            vm.uploadCleanup = ru.wishka.memory.net.MediaCompressor::cleanup
            vm.deviceName = android.os.Build.MODEL.orEmpty().ifEmpty { "Android" }
            // Плашка «Войдите на сервер» проверяет пароль здесь, на телефоне
            val localAuth = ru.wishka.memory.data.auth.LocalAuth(ru.wishka.memory.data.auth.SecurePrefs(context.applicationContext))
            vm.passwordCheck = { password -> localAuth.checkPasswordOf(account.username, password) }
            vm.attachNet(
                ru.wishka.memory.net.NetSetup.forAccount(
                    context, account.username, { vm.state.value.me }, System::currentTimeMillis,
                    quality = { vm.state.value.mediaQuality }, unmetered = { netMonitor.unmetered.value },
                    tokens = ru.wishka.memory.data.auth.SecurePrefs(context.applicationContext),
                ),
            )
            vm.startNetLoop()
            // Токен пушей — серверу (новый токен от Firebase — тоже)
            ru.wishka.memory.push.PushTokens.listener = { token ->
                android.os.Handler(android.os.Looper.getMainLooper()).post { vm.setPushToken(token) }
            }
            vm.setPushToken(ru.wishka.memory.push.PushTokens.current(context.applicationContext))
            // Вход на сервер (демо — сразу) и синхронизация профиля между телефонами
            vm.startSession(account.username, authVm.takePassword(), fresh = saved == null)
            // Для отправки в фоне: кто вошёл
            device.setLastAccount(account.username, me.id, me.name)
        } else {
            device.setLastAccount(null)
            vm.reset()
        }
    }
    // Любое изменение — в файл, через полсекунды тишины: при наборе
    // комментария не пишем на диск после каждой буквы
    LaunchedEffect(loadedFor) {
        val login = loadedFor ?: return@LaunchedEffect
        vm.state.collectLatest { s ->
            delay(500)
            val snapshot = vm.snapshot()
            withContext(Dispatchers.IO) { runCatching { store.save(login, snapshot, s.me) } }
        }
    }

    // Открыли приложение по ссылке-приглашению — кто позвал и к чему.
    // Только когда сохранённое уже на месте: иначе у старого аккаунта без
    // записей (до restore) приглашение сочли бы регистрацией по ссылке.
    // Пока не вошли — ссылка ждёт в openedInvite.
    LaunchedEffect(loadedFor) {
        if (loadedFor == null) return@LaunchedEffect
        MainActivity.openedLink.collect { link ->
            if (link != null) {
                MainActivity.openedLink.value = null
                if (!vm.openLink(link)) Toast.makeText(context, t("Это недоступно на этом телефоне — возможно, запись удалили или профиль закрыт."), Toast.LENGTH_LONG).show()
            }
        }
    }
    LaunchedEffect(loadedFor) {
        if (loadedFor == null) return@LaunchedEffect
        MainActivity.openedInvite.collect { invite ->
            if (invite != null) {
                vm.openInvite(invite)
                MainActivity.openedInvite.value = null
            }
        }
    }

    // Заставка держится, пока проверяется сохранённая сессия: иначе на
    // долю секунды мелькнул бы экран входа у уже вошедшего человека
    // Документы (соглашение, правила, согласие на обработку) не приняты в
    // текущей версии — сначала они, потом приложение
    val needsConsent = account != null && remember(account.username, auth.consentTick) { !authVm.hasConsent(account.username) }
    val stage = when {
        !splashDone || auth.checking -> Stage.Splash
        account == null && !onboarded -> Stage.Onboarding
        account == null -> Stage.Auth
        needsConsent -> Stage.Consent
        else -> Stage.Main
    }
    // Заставка, знакомство, вход и документы — на ночном небе: всегда тёмные
    SideEffect { ru.wishka.memory.ui.theme.ThemeState.stageDark = stage != Stage.Main }
    Crossfade(targetState = stage, animationSpec = tween(500), label = "stage") { s ->
        when (s) {
            Stage.Splash -> AtomSplash(onFinished = { splashDone = true })
            Stage.Onboarding -> OnboardingScreen(
                onDone = { picks ->
                    device.savePicks(picks)
                    device.setOnboarded()
                    registerNext = true
                    onboarded = true
                },
                onSkip = {
                    device.setOnboarded()
                    onboarded = true
                },
            )
            Stage.Auth -> AuthScreen(authVm, auth, startRegistering = registerNext)
            Stage.Consent -> ConsentGate(onAccept = authVm::acceptDocuments, onLogout = authVm::logout)
            Stage.Main -> MainScreen(
                vm,
                onSaveProfile = authVm::saveProfile,
                account = AccountActions(
                    logout = authVm::logout,
                    // Сначала проверка на телефоне, потом сервер, потом телефон: пароли не разойдутся
                    changePassword = { current, new, repeat ->
                        authVm.checkPasswordChange(current, new, repeat)
                            ?: vm.changeServerPassword(current, new)
                            ?: authVm.changePassword(current, new, repeat)
                    },
                    deleteAccount = deleteAccount@{ password ->
                        val login = account?.username
                        if (!authVm.checkPassword(password)) return@deleteAccount ru.wishka.memory.i18n.t("Пароль не подходит")
                        // Сначала — на сервере (нет связи — не удаляем нигде)
                        vm.deleteOnServer(password)?.let { return@deleteAccount it }
                        val error = authVm.deleteAccount(password)
                        if (error == null && login != null) {
                            // База сети ещё открыта — закрыть, потом стирать
                            (vm.net.cache as? ru.wishka.memory.net.SqliteCache)?.close()
                            withContext(Dispatchers.IO) {
                                store.delete(login)
                                // И сохранённое для офлайна, очередь, незаконченные загрузки
                                ru.wishka.memory.net.NetSetup.deleteAccount(context, login)
                            }
                            ru.wishka.memory.data.ChatKeys(ru.wishka.memory.data.auth.SecurePrefs(context.applicationContext)).forget(login)
                        }
                        error
                    },
                    data = ru.wishka.memory.ui.settings.DataActions(
                        fileName = { ru.wishka.memory.ui.settings.exportFileName(account?.username.orEmpty()) },
                        mediaBytes = {
                            withContext(Dispatchers.IO) {
                                ru.wishka.memory.data.DataExport.mediaBytes(ru.wishka.memory.data.DataExport.files(vm.snapshot(), vm.state.value.me.avatar))
                            }
                        },
                        export = { uri, withMedia ->
                            val acc = account
                            if (acc == null) null else withContext(Dispatchers.IO) {
                                runCatching {
                                    val snap = vm.snapshot()
                                    val me = vm.state.value.me
                                    val (version, at) = authVm.consentInfo()
                                    val info = ru.wishka.memory.data.AccountExport(
                                        acc.username, me.name, vm.profileInfo(), authVm.email(), authVm.emailConfirmed(),
                                        version, at, authVm.recoveryLeft(),
                                    )
                                    ru.wishka.memory.data.Telemetry.track(ru.wishka.memory.data.AppEvent.DataExported)
                                    context.contentResolver.openOutputStream(uri)!!.use { out ->
                                        ru.wishka.memory.data.DataExport.write(
                                            out, info, StateStore.exportJson(snap, me),
                                            ru.wishka.memory.data.DataExport.files(snap, me.avatar), withMedia, System.currentTimeMillis(),
                                        )
                                    }
                                }.getOrNull()
                            }
                        },
                    ),
                    recovery = ru.wishka.memory.ui.auth.RecoveryActions(
                        left = authVm::recoveryLeft,
                        regenerate = authVm::regenerateRecoveryCodes,
                        email = authVm::email,
                        emailConfirmed = authVm::emailConfirmed,
                        setEmail = authVm::setEmail,
                        confirmEmail = authVm::confirmEmail,
                        removeEmail = authVm::removeEmail,
                        demoCode = authVm::demoCode,
                    ),
                ),
            )
        }
    }
    // Коды восстановления — сразу после регистрации, поверх любого экрана
    auth.recoveryCodes?.takeIf { it.isNotEmpty() }?.let { ru.wishka.memory.ui.auth.RecoveryCodesDialog(it, authVm::dismissRecoveryCodes) }
}

@Composable
private fun MainScreen(vm: AppViewModel, onSaveProfile: (String, ProfileInfo) -> Unit, account: AccountActions) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    // Уведомления: приложение открыто — плашкой сверху, свёрнуто — системным
    LaunchedEffect(vm) {
        vm.nudges.collect { nudge ->
            if (AppVisibility.foreground) {
                vm.showBanner(nudge)
                // Плашка звучит тем же звуком, что и пуш этого вида
                SoundPlayer.play(context.applicationContext, SoundPrefs(context.applicationContext).get(nudge.kind))
            } else MemoryNotifications.show(context.applicationContext, nudge)
        }
    }
    // Пуш с сервера, пока приложение открыто, — той же плашкой
    LaunchedEffect(vm) {
        PushBus.incoming.collect { vm.notify(it) }
    }
    // Выключатели уведомлений — и для пушей с сервера (их принимает служба без экрана)
    LaunchedEffect(state.notifications) {
        PushBus.saveSettings(context.applicationContext, state.notifications)
    }
    // Открыли приложение нажатием на уведомление — туда, о чём оно
    LaunchedEffect(vm) {
        MainActivity.openedNudge.collect { nudge ->
            if (nudge != null) {
                vm.openNudge(nudge)
                MainActivity.openedNudge.value = null
            }
        }
    }
    // Исчезающие сообщения: раз в 10 секунд убираем истёкшие (и их файлы)
    LaunchedEffect(Unit) {
        while (true) {
            vm.sweepExpired().forEach { MediaFiles.delete(it) }
            delay(10_000)
        }
    }
    // Подписка Memory+ сверяется с часами: закончилась — тариф бесплатный
    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshPlan()
            delay(60_000)
        }
    }
    // Android 13+: разрешение на уведомления — один раз, при первом входе
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !MemoryNotifications.permitted(context)) {
            val prefs = context.getSharedPreferences("device", android.content.Context.MODE_PRIVATE)
            if (!prefs.getBoolean("askedNotifications", false)) {
                prefs.edit().putBoolean("askedNotifications", true).apply()
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    val prefs = remember(context) { DevicePrefs(context.applicationContext) }
    // Один раз — спросить про отчёты о сбоях и статистику (если сборка умеет их отправлять)
    var askTelemetry by remember { mutableStateOf(ru.wishka.memory.telemetry.TelemetrySetup.hasKey && !ru.wishka.memory.telemetry.TelemetrySetup.asked) }
    LaunchedEffect(Unit) { ru.wishka.memory.data.Telemetry.track(ru.wishka.memory.data.AppEvent.AppOpen) }
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: ""
    }

    // «Назад»: просмотр фото → обсуждение → переписка → страница человека
    // → группа → лента → выход
    val canGoBack = state.viewing != null || state.openProduct != null || state.commentsPostId != null || state.commentsPrismId != null ||
        state.openChatId != null || state.openPersonId != null || state.profileSection != null || state.settingsPage != null ||
        state.creatingPrism || state.activityOpen || state.storyViewer != null || state.storyComposer || state.call != null ||
        state.repostingPostId != null || state.sharingPostId != null || state.watchingLiveId != null || state.liveStarting || state.searchOpen || state.openPostId != null || state.editingPostId != null ||
        state.openGroupId != null || state.tab != Tab.Feed
    BackHandler(enabled = canGoBack) { vm.back() }

    // Что всегда ночное — PRISM, эфир, звонок, история, фото на весь экран — тёмное и в светлой теме
    // Переписка с «космическим» фоном — тоже: светлый текст шапки на тёмной картинке
    val darkBackdrop = state.openChat?.let { (it.backdrop ?: state.backdrop).style != ru.wishka.memory.data.BackdropStyle.Plain.id } ?: false
    val overlayDark = state.tab == Tab.Prism || state.viewing != null || state.creatingPrism || state.watchingLiveId != null || darkBackdrop ||
        state.storyViewer != null || state.storyComposer || state.call != null
    SideEffect { ru.wishka.memory.ui.theme.ThemeState.overlayDark = overlayDark }

    // #теги, @упоминания и ссылки в тексте; ссылка на запись и правка — в меню любой карточки
    val links = remember(vm, state.me.id) {
        LinkActions(
            meId = state.me.id,
            onTag = vm::openTag,
            onMention = vm::openHandle,
            onUrl = { url ->
                val target = Links.parse(url)
                if (target != null) {
                    if (!vm.openLink(target)) Toast.makeText(context, t("Это недоступно на этом телефоне — возможно, запись удалили или профиль закрыт."), Toast.LENGTH_LONG).show()
                } else runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            suggest = vm::mentionCandidates,
            onShareLink = { post ->
                ru.wishka.memory.data.Telemetry.track(ru.wishka.memory.data.AppEvent.LinkShared)
                MediaFiles.share(context, Links.shareText(post), emptyList())
            },
            onCopyLink = { post -> ru.wishka.memory.ui.auth.copyText(context, "Memory", Links.post(post.id), t("Ссылка скопирована")) },
            onEdit = { post -> vm.startEdit(post.id) },
            onRepost = { post -> vm.openRepost(post.id) },
            onSendToChat = { post -> vm.openSendToChat(post.id) },
            onOpenRef = { ref ->
                if (!vm.openPostRef(ref)) Toast.makeText(context, t("Это недоступно на этом телефоне — возможно, запись удалили или профиль закрыт."), Toast.LENGTH_LONG).show()
            },
            onPerson = vm::openPerson,
            onUntagMe = { post -> vm.untagMe(post.id) },
        )
    }
    val sync = ru.wishka.memory.ui.components.SyncInfo(state.netStatus) { owner -> state.uploadProgress(owner) }
    CompositionLocalProvider(LocalLinkActions provides links, ru.wishka.memory.ui.components.LocalSync provides sync) {
    Box(modifier = Modifier.fillMaxSize()) {
        CosmicBackground()

        // Отступы списков: сверху — под строку состояния, снизу — под
        // парящую панель вкладок, чтобы последняя карточка не пряталась
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp
        val padding = PaddingValues(top = top, bottom = bottom)

        val openChat = state.openChat
        val openPerson = state.openPerson
        val openGroup = state.openGroup
        val openPost = state.openPostId?.let { state.findPost(it) }
        if (openChat != null) {
            ChatScreen(vm, openChat, state.chats.filter { it.id != openChat.id }, blocked = state.isBlocked(openChat.peer.id))
        } else {
        when {
            // Активность и поиск — поверх вкладки; с них можно уйти на человека или группу
            state.activityOpen -> ru.wishka.memory.ui.activity.ActivityScreen(vm, state, padding)
            openPost != null && openPerson == null && openGroup == null ->
                ru.wishka.memory.ui.feed.PostScreen(vm, state, openPost, padding)
            state.searchOpen && openPerson == null && openGroup == null -> ru.wishka.memory.ui.search.SearchScreen(vm, state, padding)
            // Страница человека и группы — поверх вкладки, панель вкладок остаётся
            openPerson != null -> PersonScreen(vm, state, openPerson, padding)
            openGroup != null && state.groupSettingsOpen -> GroupSettingsScreen(vm, openGroup, padding)
            openGroup != null -> GroupScreen(vm, state, openGroup, padding)
            else -> AnimatedContent(
                targetState = state.tab,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                label = "tab",
            ) { tab ->
                when (tab) {
                    Tab.Feed -> FeedScreen(vm, state, padding)
                    Tab.Stories -> StoriesScreen(vm, state, padding)
                    Tab.Prism -> PrismScreen(vm, state, padding)
                    Tab.Chats -> ChatsScreen(vm, state, padding)
                    Tab.Groups -> GroupsScreen(vm, state, padding)
                    Tab.Together -> TogetherScreen(vm, state, padding)
                    Tab.Profile -> {
                        val section = state.profileSection
                        val settings = state.settingsPage
                        when {
                            settings != null -> SettingsScreen(vm, state, settings, padding, version, prefs, account)
                            section == ProfileSection.Music -> MusicScreen(vm, state, padding)
                            section == ProfileSection.Stickers -> StickerStudioScreen(vm, state, padding)
                            section == ProfileSection.Recap -> RecapScreen(vm, state, padding)
                            section == ProfileSection.Shelf -> ShelfScreen(vm, state, padding)
                            section == ProfileSection.Premium -> ru.wishka.memory.ui.premium.PremiumScreen(vm, state, padding)
                            section != null -> ProfileSectionScreen(vm, state, section, padding)
                            else -> ProfileScreen(vm, state, padding, onSaveProfile)
                        }
                    }
                }
            }
        }

        BottomBar(
            selected = state.tab,
            unread = state.unreadTotal,
            onSelect = vm::selectTab,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        )
        }

        // Обсуждение — шторкой поверх любого экрана
        state.commentsPostId?.let { id ->
            state.findPost(id)?.let { post ->
                CommentsSheet(
                    total = post.comments,
                    comments = post.commentList.filter { it.author.id !in state.blocked && it.id !in state.reportedComments },
                    myId = state.me.id,
                    onSend = { text, replyTo, sticker -> vm.addComment(id, text, replyTo, sticker) },
                    onDelete = { vm.deleteComment(id, it) },
                    onReport = { cid -> post.commentList.firstOrNull { it.id == cid }?.let(vm::reportComment) },
                    onLike = { vm.toggleCommentLike(id, it) },
                    onAuthor = { userId -> vm.closeComments(); vm.openPerson(userId) },
                    onDismiss = vm::closeComments,
                    stickers = state.stickers,
                    // К своей записи — фильтр: скрытые слова, оскорбления, спам
                    hiddenOf = { c -> if (post.author.id == state.me.id) state.hiddenReason(c.text, c.author.id) else null },
                    closed = post.commentsOff,
                    onToggleClosed = if (post.author.id == state.me.id) ({ vm.toggleComments(id) }) else null,
                )
            }
        }
        state.commentsPrismId?.let { id ->
            state.findPrism(id)?.let { prism ->
                CommentsSheet(
                    total = prism.comments,
                    comments = prism.commentList.filter { it.author.id !in state.blocked && it.id !in state.reportedComments },
                    myId = state.me.id,
                    onSend = { text, replyTo, sticker -> vm.addPrismComment(id, text, replyTo, sticker) },
                    onDelete = { vm.deletePrismComment(id, it) },
                    onReport = { cid -> prism.commentList.firstOrNull { it.id == cid }?.let(vm::reportComment) },
                    onLike = { vm.togglePrismCommentLike(id, it) },
                    onAuthor = { userId -> vm.closeComments(); vm.openPerson(userId) },
                    onDismiss = vm::closeComments,
                    stickers = state.stickers,
                )
            }
        }
        // Шторка «Это в Memory+» — поверх всего, где бы ни нажали
        state.paywall?.let { feature -> ru.wishka.memory.ui.premium.PaywallSheet(vm, state, feature) }
        state.banner?.let { nudge ->
            NudgeBanner(
                nudge,
                onOpen = { vm.openNudge(nudge) },
                onDismiss = vm::dismissBanner,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        state.echo?.let { EchoDialog(vm, state, it) }
        state.openedInvite?.let { InviteDialog(vm, it) }
        state.viewing?.let { MediaViewer(it, onClose = vm::closeMedia, onPerson = { id -> vm.closeMedia(); vm.openPerson(id) }) }
        state.reporting?.let { target ->
            ReportDialog(
                target,
                onSend = { reason, note, block ->
                    vm.report(target, reason, note, block)
                    Toast.makeText(context, t("Спасибо. Жалоба ушла модераторам, у вас это скрыто."), Toast.LENGTH_LONG).show()
                },
                onDismiss = vm::cancelReport,
                onRules = { vm.openLegal(LegalDoc.Community) },
            )
        }
        state.legalDoc?.let { LegalDocViewer(it, onClose = vm::closeLegal) }
        // Свой PRISM — камера и редактор во весь экран, поверх всего
        if (state.creatingPrism) PrismCreateFlow(vm, state)
        // Истории — просмотр и новая — во весь экран
        if (state.storyComposer) ru.wishka.memory.ui.stories.StoryComposerScreen(vm, state)
        if (state.storyViewer != null) ru.wishka.memory.ui.stories.StoryViewerScreen(vm, state)
        // «Нет сети» — поверх всего, кроме звонка
        ru.wishka.memory.ui.components.NetBanner(
            state.netStatus, state.outboxCount,
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 4.dp),
        )
        state.call?.let { ru.wishka.memory.ui.calls.CallScreen(vm, state, it) }
        // Эфир — во весь экран, поверх всего
        state.watchingLive?.let { ru.wishka.memory.ui.live.LiveScreen(vm, state, it) }
        if (state.liveStarting) ru.wishka.memory.ui.live.LiveStartDialog(vm)
        state.liveSummary?.let { ru.wishka.memory.ui.live.LiveSummaryDialog(vm, it) }
        state.editingPostId?.let { id -> state.findPost(id)?.let { ru.wishka.memory.ui.feed.EditPostSheet(vm, it) } }
        state.repostingPostId?.let { id -> state.findPost(id)?.let { ru.wishka.memory.ui.feed.RepostSheet(vm, it) } }
        state.sharingPostId?.let { id -> state.findPost(id)?.let { ru.wishka.memory.ui.feed.SendToChatSheet(vm, state, it) } }
        if (askTelemetry && state.banner == null) ru.wishka.memory.ui.settings.TelemetryConsentDialog { askTelemetry = false }
    }
    }
}
