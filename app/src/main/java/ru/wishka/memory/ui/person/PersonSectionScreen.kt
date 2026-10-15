package ru.wishka.memory.ui.person

import ru.wishka.memory.i18n.t
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.wishka.memory.data.User
import ru.wishka.memory.data.plural
import ru.wishka.memory.ui.AppViewModel
import ru.wishka.memory.ui.ProfileSection
import ru.wishka.memory.ui.UiState
import ru.wishka.memory.ui.components.LineIcon
import ru.wishka.memory.ui.components.ScreenTitle
import ru.wishka.memory.ui.feed.PostCard
import ru.wishka.memory.ui.components.FriendButton
import ru.wishka.memory.ui.profile.Empty
import ru.wishka.memory.ui.profile.PersonRow
import ru.wishka.memory.ui.profile.RoundIconButton
import ru.wishka.memory.ui.theme.Palette

/**
 * Записи или друзья другого человека — по нажатию на счётчик на его
 * странице. Пока сервера нет, в списке видны только знакомые люди;
 * число на счётчике — настоящее.
 */
@Composable
fun PersonSectionScreen(vm: AppViewModel, state: UiState, person: User, section: ProfileSection, contentPadding: PaddingValues) {
    val blocked = state.isBlocked(person.id)
    val posts = if (blocked || !state.canSee(person)) emptyList() else state.postsOf(person.id)
    val people = if (section == ProfileSection.Friends) state.friendsOf(person) else emptyList()
    val subtitle = when (section) {
        ProfileSection.Posts -> plural(posts.size, "запись", "записи", "записей")
        else -> plural(state.friendsOf(person).size, "друг", "друга", "друзей")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(LineIcon.Back, Palette.Ink) { vm.closePersonSection() }
                Spacer(Modifier.width(14.dp))
                ScreenTitle(section.title, "@${person.handle} · $subtitle")
            }
        }
        if (section == ProfileSection.Posts) {
            if (posts.isEmpty()) item { Empty(t("Записей пока нет.")) }
            items(posts, key = { it.id }) { post ->
                PostCard(
                    post,
                    onLike = { vm.toggleLike(post.id) },
                    onVote = { vm.votePost(post.id, it) },
                    onClosePoll = if (vm.isMine(post)) ({ vm.closePostPoll(post.id) }) else null,
                    onReport = if (vm.isMine(post)) null else ({ vm.reportPost(post) }),
                    onComments = { vm.openComments(post.id) },
                    onOpenMedia = vm::openMedia,
                )
            }
        } else {
            item { Empty(t("Показаны люди, которых вы знаете в Memory.")) }
            items(people, key = { it.id }) { other ->
                val me = other.id == state.me.id
                PersonRow(
                    other,
                    onOpen = { vm.openPerson(other.id) },
                    trailing = if (me) null else ({
                        FriendButton(state.friendState(other.id), other.name, onToggle = { vm.toggleFriend(other.id) })
                    }),
                )
            }
        }
    }
}
