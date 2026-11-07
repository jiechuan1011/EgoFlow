package com.egoflow.app.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.egoflow.app.data.entity.TaskEntity
import com.egoflow.app.domain.model.EnergyBlock
import com.egoflow.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleTimelineScreen(
    onBack: () -> Unit,
    viewModel: ScheduleTimelineViewModel = viewModel(factory = ScheduleTimelineViewModel.Factory())
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // 每次页面获取焦点时重新加载数据（确保 Chat 确认排程后及时刷新）
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 在 Composable 层计算支线任务过滤（避免在 LazyListScope 中调用 remember）
    val blocks = uiState.schedulePlan?.energyBlocks ?: emptyList()
    val allBlockTitles = remember(blocks) {
        blocks.map { it.title.trim().lowercase(Locale.CHINA) }.toSet()
    }
    val subLineTasks = uiState.schedulePlan?.subLineTasks ?: emptyList()
    val visibleSubLineTasks = remember(subLineTasks, allBlockTitles) {
        subLineTasks.filter { task ->
            val taskTitle = task.title.trim().lowercase(Locale.CHINA)
            allBlockTitles.none { blockTitle ->
                blockTitle.contains(taskTitle) || taskTitle.contains(blockTitle)
            }
        }
    }

    LaunchedEffect(uiState.swapErrorMessage) {
        uiState.swapErrorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("时间线", fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 日期标题
                item {
                    Text(
                        text = uiState.selectedDate,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                // 拖拽提示
                if (uiState.dragSource != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = AccentGold.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("已选择：${uiState.dragSource?.title}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                TextButton(onClick = { viewModel.clearDragSource() }) { Text("取消") }
                            }
                        }
                    }
                }

                // 时间线块
                if (blocks.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                                Spacer(Modifier.height(8.dp))
                                Text("今日无主线排程", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    }
                }

                items(blocks) { block ->
                    TimelineBlockCard(
                        block = block,
                        isSelected = uiState.dragSource?.taskId == block.taskId,
                        isSourceMode = uiState.dragSource != null,
                        onClick = {
                            if (uiState.dragSource != null) {
                                viewModel.swapWith(block)
                            } else if (!block.isHardBlock) {
                                viewModel.selectDragSource(block)
                            }
                        },
                        onEdit = { viewModel.startEditTime(it) },
                        onDelete = { viewModel.deleteBlock(it) }
                    )
                }

                // === 支线 TodoList（含自动消费隐藏 + 左滑删除） ===
                if (visibleSubLineTasks.isNotEmpty()) {
                    item { Spacer(Modifier.height(16.dp)) }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = RewardGold.copy(alpha = 0.1f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.List, contentDescription = null, tint = RewardGold, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("支线任务", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = RewardGold)
                                }
                                Spacer(Modifier.height(8.dp))
                                visibleSubLineTasks.forEach { task ->
                                    SubLineTaskRow(
                                        task = task,
                                        onToggle = { viewModel.toggleSubLineTask(task.id) },
                                        onDelete = { viewModel.deleteSubLineTask(task.id) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 图例
                item {
                    Spacer(Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("图例", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            LegendRow(color = HardBlockRed, label = "硬墙时段（不可拖拽）")
                            LegendRow(color = AccentGold, label = "主线任务（高耗）")
                            LegendRow(color = CalmGreen, label = "主线任务（低耗）")
                            LegendRow(color = RewardGold, label = "支线任务")
                        }
                    }
                }
            }
        }
    }

    // 时间编辑对话框 — 起止双滚轮
    uiState.editingBlock?.let { block ->
        var startHour by remember { mutableIntStateOf(uiState.pickerHour) }
        var startMinute by remember { mutableIntStateOf(uiState.pickerMinute) }
        var endHour by remember { mutableIntStateOf(uiState.pickerEndHour) }
        var endMinute by remember { mutableIntStateOf(uiState.pickerEndMinute) }
        AlertDialog(
            onDismissRequest = { viewModel.cancelEdit() },
            title = { Text("修改「${block.title}」时间") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(4.dp))
                    Text("开始时间", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NumberWheel(0..23, startHour, { startHour = it }, Modifier.width(64.dp))
                        Text(":", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 2.dp))
                        NumberWheel(0..59, startMinute, { startMinute = it }, Modifier.width(64.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("结束时间", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NumberWheel(0..23, endHour, { endHour = it }, Modifier.width(64.dp))
                        Text(":", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 2.dp))
                        NumberWheel(0..59, endMinute, { endMinute = it }, Modifier.width(64.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("上下滑动选择", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            },
            confirmButton = { Button(onClick = { viewModel.rescheduleBlock(startHour, startMinute, endHour, endMinute) }) { Text("确认") } },
            dismissButton = { TextButton(onClick = { viewModel.cancelEdit() }) { Text("取消") } }
        )
    }
}

// ===== 辅助组件 =====

@Composable
private fun TimelineBlockCard(
    block: EnergyBlock,
    isSelected: Boolean,
    isSourceMode: Boolean,
    onClick: () -> Unit,
    onEdit: (EnergyBlock) -> Unit,
    onDelete: (EnergyBlock) -> Unit
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val blockColor = when {
        block.category == "BREAK" -> SoftAmber
        block.drainLevel == "HIGH" && block.category == "MAIN_LINE" -> AccentGold
        block.drainLevel == "LOW" && block.category == "MAIN_LINE" -> CalmGreen
        block.category == "SUB_LINE" -> RewardGold
        block.isHardBlock -> HardBlockRed
        else -> SoftAmber
    }

    Card(
        modifier = Modifier.fillMaxWidth()
            .alpha(if (isSourceMode && !isSelected) 0.5f else 1.0f)
            .clickable(enabled = !block.isHardBlock) { onClick() },
        colors = CardDefaults.cardColors(containerColor = blockColor.copy(alpha = if (block.isHardBlock) 0.15f else 0.1f)),
        shape = RoundedCornerShape(12.dp, 4.dp, 12.dp, 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 时间线指示器
            Box(Modifier.width(4.dp).height(48.dp).background(blockColor, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(12.dp))

            // 时间
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                Text(timeFormat.format(Date(block.startTime)), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = blockColor)
                Text("-", style = MaterialTheme.typography.labelSmall, color = blockColor)
                Text(timeFormat.format(Date(block.endTime)), style = MaterialTheme.typography.labelSmall, color = blockColor)
            }
            Spacer(Modifier.width(12.dp))

            // 内容
            Column(modifier = Modifier.weight(1f)) {
                Text(block.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (block.isHardBlock) {
                        Surface(shape = RoundedCornerShape(4.dp), color = HardBlockRed.copy(alpha = 0.2f)) {
                            Text("刚性", modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall, color = HardBlockRed)
                        }
                    }
                    Surface(shape = RoundedCornerShape(4.dp), color = blockColor.copy(alpha = 0.2f)) {
                        Text(
                            when (block.category) {
                                "MAIN_LINE" -> "主线"
                                "SUB_LINE" -> "支线"
                                else -> block.category
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall, color = blockColor
                        )
                    }
                }
            }

            // 删除按钮
            IconButton(onClick = { onDelete(block) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = HardBlockRed.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
            // 时间编辑按钮
            IconButton(onClick = { onEdit(block) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Schedule, contentDescription = "修改时间", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
            // 拖拽互换
            if (!block.isHardBlock) {
                Icon(
                    if (isSourceMode) Icons.Default.SwapHoriz else Icons.Default.DragHandle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SubLineTaskRow(
    task: TaskEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = task.status == "DONE",
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = CalmGreen)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (task.status == "DONE") FontWeight.Normal else FontWeight.Medium,
                color = if (task.status == "DONE") MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${task.estimatedMinutes}分钟",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "删除", tint = HardBlockRed.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * 数字滚轮选择器 — 上下滑动选择数字，中间高亮
 */
@Composable
private fun NumberWheel(
    range: IntRange,
    initialValue: Int,
    onValueChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeightDp = 36.dp
    val visibleItems = 5
    val totalHeight = itemHeightDp * visibleItems
    val initialIndex = (range.indexOf(initialValue).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val coroutineScope = rememberCoroutineScope()

    val selectedIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.isNotEmpty()) {
                val center = info.viewportEndOffset / 2
                info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - center) }
                    ?.index?.coerceIn(0, range.last) ?: initialIndex
            } else initialIndex
        }
    }

    LaunchedEffect(selectedIndex) {
        onValueChanged(range.elementAtOrNull(selectedIndex) ?: initialValue)
    }

    Box(modifier = modifier.height(totalHeight), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(itemHeightDp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(6.dp)))

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = (totalHeight - itemHeightDp) / 2)
        ) {
            itemsIndexed(range.toList()) { index, value ->
                val isCenter = index == selectedIndex
                Text(
                    "%02d".format(value),
                    modifier = Modifier.fillMaxWidth().height(itemHeightDp)
                        .clickable { coroutineScope.launch { listState.animateScrollToItem(index) } },
                    textAlign = TextAlign.Center,
                    fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                    fontSize = if (isCenter) 20.sp else 14.sp,
                    color = if (isCenter) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(Modifier.size(12.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
