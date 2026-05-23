package com.egoflow.app.ui.focus

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.egoflow.app.ui.theme.*

/**
 * 认知防火墙主界面
 *
 * 核心设计：全景隐藏，局部绝对聚焦
 * 同一时刻只显示一个当前精力块 + 大字番茄钟倒计时
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusFirewallScreen(
    onNavigateToCoach: () -> Unit,
    onNavigateToTimeline: () -> Unit,
    onNavigateToEvolution: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSchedule: () -> Unit = {},
    viewModel: FocusFirewallViewModel = viewModel(factory = FocusFirewallViewModel.Factory())
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showOverflowMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EgoFlow", fontWeight = FontWeight.Bold, maxLines = 1) },
                actions = {
                    IconButton(onClick = onNavigateToTimeline) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "日程")
                    }
                    IconButton(onClick = onNavigateToCoach) {
                        Icon(Icons.Default.Chat, contentDescription = "教练")
                    }
                    IconButton(onClick = onNavigateToEvolution) {
                        Icon(Icons.Default.AutoGraph, contentDescription = "进化")
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("任务池") },
                                onClick = { showOverflowMenu = false; viewModel.toggleTaskPool() },
                                leadingIcon = { Icon(Icons.Default.List, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("课程表") },
                                onClick = { showOverflowMenu = false; onNavigateToSchedule() },
                                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("设置") },
                                onClick = { showOverflowMenu = false; onNavigateToSettings() },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val block = uiState.currentBlock

                    if (block != null) {
                        // 当前精力块标题
                        Text(
                            text = block.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 类别标签
                        val categoryColor = when (block.category) {
                            "MAIN_LINE" -> AccentGold
                            "SUB_LINE" -> RewardGold
                            else -> SoftAmber
                        }
                        val categoryLabel = when (block.category) {
                            "MAIN_LINE" -> "主线任务"
                            "SUB_LINE" -> "奖励支线"
                            else -> ""
                        }
                        Text(
                            text = categoryLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = categoryColor
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // 大字番茄钟倒计时
                        if (uiState.isPomodoroMode) {
                            Box(
                                modifier = Modifier
                                    .size(260.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val minutes = uiState.timerSeconds / 60
                                    val seconds = uiState.timerSeconds % 60
                                    Text(
                                        text = "%02d:%02d".format(minutes, seconds),
                                        fontSize = 56.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (uiState.isTimerRunning) "专注中..." else "已暂停",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            // 计时控制按钮
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (uiState.isTimerRunning) {
                                    FilledTonalButton(
                                        onClick = { viewModel.pausePomodoro() },
                                        modifier = Modifier.height(56.dp)
                                    ) {
                                        Icon(Icons.Default.Pause, contentDescription = "暂停")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("暂停")
                                    }
                                } else {
                                    FilledTonalButton(
                                        onClick = { viewModel.resumePomodoro() },
                                        modifier = Modifier.height(56.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "继续")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("继续")
                                    }
                                }
                                OutlinedButton(
                                    onClick = { viewModel.skipCurrentBlock() },
                                    modifier = Modifier.height(56.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = HardBlockRed
                                    )
                                ) {
                                    Text("跳过")
                                }
                            }
                        } else {
                            // 未开始计时 —— 显示开始按钮
                            Box(
                                modifier = Modifier
                                    .size(260.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val durationMinutes = (block.endTime - block.startTime) / 60000
                                    Text(
                                        text = "${durationMinutes}分钟",
                                        fontSize = 48.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "点击开始专注",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            Button(
                                onClick = { viewModel.startPomodoro() },
                                modifier = Modifier
                                    .height(56.dp)
                                    .widthIn(min = 200.dp),
                                shape = RoundedCornerShape(28.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "开始")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("开始专注", fontSize = 18.sp)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            TextButton(onClick = { viewModel.completeWithoutTimer() }) {
                                Text("已完成（不计时）")
                            }
                        }
                    } else {
                        // 无当前任务 —— 空闲状态
                        Box(
                            modifier = Modifier
                                .size(260.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.FreeBreakfast,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "当前无待办",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedButton(
                            onClick = onNavigateToCoach
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("去添加任务")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 进度指示
                    if (block != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = CalmGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "今日主线: ${uiState.completedMainLineMinutes}分钟 / 180分钟",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            // 任务池侧面板（从右滑出）
            AnimatedVisibility(
                visible = uiState.showTaskPool,
                enter = slideInHorizontally(),
                exit = slideOutHorizontally(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                TaskPoolPanel(
                    tasks = uiState.poolTasks,
                    onDismiss = { viewModel.toggleTaskPool() }
                )
            }
        }
    }
}

@Composable
private fun TaskPoolPanel(
    tasks: List<com.egoflow.app.data.entity.TaskEntity>,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "任务池",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Divider()

            Spacer(modifier = Modifier.height(8.dp))

            if (tasks.isEmpty()) {
                Text(
                    text = "任务池为空",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                tasks.forEach { task ->
                    TaskPoolItem(task)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun TaskPoolItem(task: com.egoflow.app.data.entity.TaskEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when (task.category) {
                        "MAIN_LINE" -> AccentGold.copy(alpha = 0.2f)
                        else -> RewardGold.copy(alpha = 0.2f)
                    }
                ) {
                    Text(
                        text = if (task.category == "MAIN_LINE") "主线" else "支线",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text(
                    text = "${task.estimatedMinutes}分钟",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
