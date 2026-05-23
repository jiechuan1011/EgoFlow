package com.egoflow.app.ui.coach

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.egoflow.app.domain.model.CoachMessage
import com.egoflow.app.domain.model.CoachOption
import com.egoflow.app.domain.model.CoachOptionsGroup
import com.egoflow.app.ui.theme.*

/**
 * AI 教练对话界面 （仿 IM 聊天）
 *
 * 双通道拦截：识别用户输入是日程任务还是 App 进化需求
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatCoachScreen(
    onBack: () -> Unit,
    viewModel: ChatCoachViewModel = viewModel(factory = ChatCoachViewModel.Factory())
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // 自动滚动到底部
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI 教练", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.toggleHistory() }) {
                        Text("历史", style = MaterialTheme.typography.labelMedium)
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.inputText,
                        onValueChange = { viewModel.updateInput(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入任务或想法...") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        enabled = !uiState.isProcessing
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = { viewModel.sendMessage() },
                        enabled = uiState.inputText.isNotBlank() && !uiState.isProcessing
                    ) {
                        if (uiState.isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送"
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (uiState.showHistory) {
            HistoryPanel(
                uiState = uiState,
                viewModel = viewModel,
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(uiState.messages) { message ->
                    MessageBubble(message)
                }

                // 交互式选项卡片
                if (uiState.pendingOptions != null) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        OptionsGroupCard(
                            optionsGroup = uiState.pendingOptions!!,
                            onSelectOption = { option -> viewModel.selectOption(option) },
                            onCustomInput = { text ->
                                viewModel.updateInput(text)
                                viewModel.sendMessage()
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryPanel(
    uiState: CoachUiState,
    viewModel: ChatCoachViewModel,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("对话历史", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (uiState.historyDates.isEmpty()) {
            Text("暂无历史记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        } else {
            // 日期列表
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(uiState.historyDates) { date ->
                    val isSelected = date == uiState.selectedDate
                    Surface(
                        onClick = { viewModel.selectHistoryDate(date) },
                        shape = MaterialTheme.shapes.small,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(date, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            if (isSelected && uiState.historyMessages.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                uiState.historyMessages.takeLast(3).forEach { msg ->
                                    Text(
                                        text = if (msg.role == "coach") "🤖 ${msg.content.take(50)}${if (msg.content.length > 50) "..." else ""}"
                                        else "👤 ${msg.content.take(50)}${if (msg.content.length > 50) "..." else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { viewModel.toggleHistory() }, modifier = Modifier.fillMaxWidth()) {
            Text("返回对话")
        }
    }
}

@Composable
private fun MessageBubble(message: CoachMessage) {
    val isCoach = message.role == "coach"
    val alignment = if (isCoach) Alignment.Start else Alignment.End
    val bubbleColor = if (isCoach)
        MaterialTheme.colorScheme.surface
    else
        MaterialTheme.colorScheme.primary
    val textColor = if (isCoach)
        MaterialTheme.colorScheme.onSurface
    else
        MaterialTheme.colorScheme.onPrimary

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isCoach) Alignment.Start else Alignment.End
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isCoach) 4.dp else 16.dp,
                bottomEnd = if (isCoach) 16.dp else 4.dp
            ),
            color = bubbleColor,
            tonalElevation = if (isCoach) 2.dp else 0.dp
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = textColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun OptionsGroupCard(
    optionsGroup: CoachOptionsGroup,
    onSelectOption: (CoachOption) -> Unit,
    onCustomInput: (String) -> Unit
) {
    var showCustomInput by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 问题标题
            Text(
                text = optionsGroup.question,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 选项卡片
            optionsGroup.options.forEach { option ->
                Card(
                    onClick = { onSelectOption(option) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (!option.description.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 自定义输入
            if (optionsGroup.allowCustomInput) {
                Spacer(modifier = Modifier.height(8.dp))
                if (!showCustomInput) {
                    TextButton(onClick = { showCustomInput = true }) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("自定义输入...")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { customText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("输入你的回答...") },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = {
                                if (customText.isNotBlank()) {
                                    onCustomInput(customText)
                                    customText = ""
                                    showCustomInput = false
                                }
                            },
                            enabled = customText.isNotBlank()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                        }
                    }
                }
            }
        }
    }
}
