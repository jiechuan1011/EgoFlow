package com.egoflow.app.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.egoflow.app.domain.model.EnergyBlock
import com.egoflow.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 日程时间线界面
 *
 * 弹性块状流编排，支持同等级拖拽对调
 * 硬墙时段红色不可操作，奖励支线金色高亮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleTimelineScreen(
    onBack: () -> Unit,
    viewModel: ScheduleTimelineViewModel = viewModel(factory = ScheduleTimelineViewModel.Factory())
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 显示错误消息
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
                title = { Text("日程时间线", fontWeight = FontWeight.Bold) },
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
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
                            colors = CardDefaults.cardColors(
                                containerColor = AccentGold.copy(alpha = 0.15f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "已选择：${uiState.dragSource?.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                TextButton(onClick = { viewModel.clearDragSource() }) {
                                    Text("取消")
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // 显示时间轴上的每个块
                val blocks = uiState.schedulePlan?.energyBlocks ?: emptyList()
                if (blocks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "今日无排程",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
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
                        onEdit = { b -> viewModel.startEditTime(b) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    // 图例
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "图例",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LegendRow(color = HardBlockRed, label = "硬墙时段（不可操作）")
                            LegendRow(color = AccentGold, label = "主线任务（高耗）")
                            LegendRow(color = CalmGreen, label = "主线任务（低耗）")
                            LegendRow(color = RewardGold, label = "奖励支线")
                        }
                    }
                }
            }
        }
    }

    // 时间编辑对话框
    uiState.editingBlock?.let { block ->
        var hour by remember { mutableIntStateOf(uiState.pickerHour) }
        var minute by remember { mutableIntStateOf(uiState.pickerMinute) }
        AlertDialog(
            onDismissRequest = { viewModel.cancelEdit() },
            title = { Text("修改「${block.title}」时间") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("小时", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (6..23).forEach { h ->
                            FilterChip(selected = hour == h, onClick = { hour = h }, label = { Text("%02d".format(h)) })
                        }
                    }
                    Text("分钟", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0, 15, 30, 45).forEach { m ->
                            FilterChip(selected = minute == m, onClick = { minute = m }, label = { Text("%02d".format(m)) })
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { viewModel.rescheduleBlock(hour, minute) }) { Text("确认") } },
            dismissButton = { TextButton(onClick = { viewModel.cancelEdit() }) { Text("取消") } }
        )
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun TimelineBlockCard(
    block: EnergyBlock,
    isSelected: Boolean,
    isSourceMode: Boolean,
    onClick: () -> Unit,
    onEdit: (EnergyBlock) -> Unit = {}
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    val blockColor = when {
        block.isHardBlock -> HardBlockRed
        block.drainLevel == "HIGH" && block.category == "MAIN_LINE" -> AccentGold
        block.drainLevel == "LOW" && block.category == "MAIN_LINE" -> CalmGreen
        block.category == "SUB_LINE" -> RewardGold
        else -> SoftAmber
    }

    val alpha = if (isSourceMode && !isSelected) 0.5f else 1.0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clickable(enabled = !block.isHardBlock) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = blockColor.copy(alpha = if (block.isHardBlock) 0.15f else 0.1f)
        ),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder().copy(
                // mark as selected
            )
        } else null,
        shape = RoundedCornerShape(
            topStart = 12.dp,
            bottomStart = 12.dp,
            topEnd = 4.dp,
            bottomEnd = 4.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 时间线指示器
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .background(blockColor, RoundedCornerShape(2.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 时间
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(56.dp)
            ) {
                Text(
                    text = timeFormat.format(Date(block.startTime)),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = blockColor
                )
                Text(
                    text = "-",
                    style = MaterialTheme.typography.labelSmall,
                    color = blockColor
                )
                Text(
                    text = timeFormat.format(Date(block.endTime)),
                    style = MaterialTheme.typography.labelSmall,
                    color = blockColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 内容
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = block.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (block.isHardBlock) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = HardBlockRed.copy(alpha = 0.2f)
                        ) {
                            Text(
                                "刚性",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = HardBlockRed
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = blockColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = when (block.category) {
                                "MAIN_LINE" -> "主线"
                                "SUB_LINE" -> "支线奖励"
                                else -> block.category
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = blockColor
                        )
                    }
                }
            }

            // 时间编辑 + 操作指示
            if (!block.isHardBlock) {
                IconButton(onClick = { onEdit(block) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Schedule, contentDescription = "修改时间", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
                Icon(
                    imageVector = if (isSourceMode) Icons.Default.SwapHoriz else Icons.Default.DragHandle,
                    contentDescription = if (isSourceMode) "点击互换" else "选择以调换",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
