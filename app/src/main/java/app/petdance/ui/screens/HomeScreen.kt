package app.petdance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.petdance.data.PetRepository
import app.petdance.data.TemplateDto
import app.petdance.domain.categoryLabel
import app.petdance.domain.formatDuration
import app.petdance.domain.petRank
import app.petdance.ui.SessionViewModel
import app.petdance.ui.components.Banner
import app.petdance.ui.components.ErrorText
import app.petdance.ui.components.LoadingBox
import app.petdance.ui.components.MutedText
import app.petdance.ui.components.QuietButton
import app.petdance.ui.theme.AppBackground
import app.petdance.ui.theme.Card
import app.petdance.ui.theme.Chip
import app.petdance.ui.theme.Ink
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel

class HomeViewModel(private val repo: PetRepository) : ViewModel() {
    var templates by mutableStateOf<List<TemplateDto>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun load() {
        viewModelScope.launch {
            loading = templates.isEmpty()
            error = null
            try {
                templates = repo.templates()
            } catch (e: Exception) {
                error = e.message ?: "模板加载失败"
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun HomeScreen(
    session: SessionViewModel,
    onOpen: (TemplateDto) -> Unit,
    onWorks: () -> Unit,
    onAccount: () -> Unit,
    onBuy: () -> Unit,
) {
    val vm: HomeViewModel = koinViewModel()
    val repo: PetRepository = koinInject()
    val state by session.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        session.refreshMeta()
        session.refreshUser()
        vm.load()
    }
    val sorted = vm.templates.sortedWith(
        compareBy<TemplateDto>({ petRank(state.effectivePet, it.petScope) }, { it.sort }),
    )
    val hero = sorted.firstOrNull()
    val category = state.interest ?: "all"
    val grid = sorted.filter { category == "all" || it.category == category }
    val balance = state.user?.credits?.balance
    val cheapest = state.meta?.cheapestCreditCost
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("模板", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            QuietButton("作品", onClick = onWorks)
            QuietButton(if (balance == null) "登录" else "$balance 点", onClick = onAccount)
        }
        if (state.meta?.maintenance == true) {
            Banner(state.meta?.maintenanceMessage?.ifBlank { null } ?: "生成通道维护中，请稍后再试")
        }
        hero?.let { item ->
            TemplateCard(
                title = item.title,
                cover = repo.media(item.coverUrl),
                badge = formatDuration(item.durationSec),
                meta = "主推 · ${item.creditCost} 点",
                tall = false,
                onClick = { onOpen(item) },
            )
        }
        if (balance != null && cheapest != null && balance < cheapest) {
            Banner("点数不够做下一条", "去购买", onBuy)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "推荐", "dance" to "跳舞", "comedy" to "搞怪", "daily" to "日常").forEach { (id, label) ->
                FilterChip(selected = category == id, onClick = {
                    session.setInterest(if (id == "all") null else id)
                }, label = { Text(label) })
            }
        }
        ErrorText(vm.error)
        when {
            vm.loading && vm.templates.isEmpty() -> LoadingBox()
            grid.isEmpty() -> MutedText("这一类还没有上架模板")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(grid, key = { it.id }) { item ->
                    TemplateCard(
                        title = item.title,
                        cover = repo.media(item.coverUrl),
                        badge = formatDuration(item.durationSec),
                        meta = "${formatDuration(item.durationSec)} · ${item.creditCost} 点 · ${categoryLabel(item.category)}",
                        tall = true,
                        onClick = { onOpen(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(
    title: String,
    cover: String?,
    badge: String,
    meta: String,
    tall: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).background(Card).clickable(onClick = onClick),
    ) {
        Box {
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(if (tall) 0.8f else 1.7f),
                )
            } else {
                Box(Modifier.fillMaxWidth().aspectRatio(if (tall) 0.8f else 1.7f).background(Chip))
            }
            Text(
                badge,
                color = Ink,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(AppBackground, RoundedCornerShape(99.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            MutedText(meta)
        }
    }
}
