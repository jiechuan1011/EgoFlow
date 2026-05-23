package com.egoflow.app.ui.milestone

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.egoflow.app.data.repository.Milestone
import com.egoflow.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestoneScreen(
    onBack: () -> Unit,
    viewModel: MilestoneViewModel = viewModel(factory = MilestoneViewModel.Factory())
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearSavedFlag()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("重要时间节点", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddDialog() }) {
                Icon(Icons.Default.Add, contentDescription = "添加")
            }
        }
    ) { padding ->
        if (uiState.milestones.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Event,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "暂无重要时间节点",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "点击右下角 + 添加考试、截止日期或事件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.milestones, key = { it.id }) { milestone ->
                    MilestoneCard(
                        milestone = milestone,
                        onEdit = { viewModel.showEditDialog(milestone) },
                        onDelete = { viewModel.deleteMilestone(milestone.id) }
                    )
                }
            }
        }
    }

    // 添加/编辑对话框
    if (uiState.showAddDialog) {
        MilestoneFormDialog(
            editing = uiState.editingMilestone,
            onDismiss = { viewModel.hideDialog() },
            onConfirm = { title, date, type, note, time ->
                viewModel.saveMilestone(title, date, type, note, time)
            }
        )
    }
}

@Composable
private fun MilestoneCard(
    milestone: Milestone,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val typeColor = when (milestone.type) {
        "EXAM" -> AccentGold
        "DEADLINE" -> HardBlockRed
        "EVENT" -> MediumBlue
        else -> SoftAmber
    }

    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 类型图标
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = typeColor.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (milestone.type) {
                            "EXAM" -> Icons.Default.School
                            "DEADLINE" -> Icons.Default.Alarm
                            "EVENT" -> Icons.Default.Event
                            else -> Icons.Default.Info
                        },
                        contentDescription = null,
                        tint = typeColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = milestone.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = typeColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = milestone.typeLabel,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = typeColor
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = buildString {
                            append(milestone.date)
                            if (!milestone.time.isNullOrBlank()) {
                                append(" ${milestone.time}")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                if (!milestone.note.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = milestone.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = HardBlockRed.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MilestoneFormDialog(
    editing: Milestone?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, date: String, type: String, note: String, time: String?) -> Unit
) {
    var title by remember { mutableStateOf(editing?.title ?: "") }
    var date by remember { mutableStateOf(editing?.date ?: "") }
    var time by remember { mutableStateOf(editing?.time ?: "") }
    var selectedType by remember { mutableStateOf(editing?.type ?: "EXAM") }
    var note by remember { mutableStateOf(editing?.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing != null) "编辑节点" else "添加节点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    placeholder = { Text("如：期中考试、大作业截止...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 类型选择
                Text("类型", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "EXAM" to "考试",
                        "DEADLINE" to "截止日期",
                        "EVENT" to "事件",
                        "OTHER" to "其他"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = selectedType == value,
                            onClick = { selectedType = value },
                            label = { Text(label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("日期 (YYYY-MM-DD)") },
                    placeholder = { Text("如：2026-06-15") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("具体时间（可选）") },
                    placeholder = { Text("如：14:30") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    placeholder = { Text("补充说明...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        title, date, selectedType, note,
                        time.ifBlank { null }
                    )
                },
                enabled = title.isNotBlank() && date.isNotBlank()
            ) {
                Text(if (editing != null) "保存" else "添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
