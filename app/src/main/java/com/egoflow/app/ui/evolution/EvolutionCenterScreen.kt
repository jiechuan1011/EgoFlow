package com.egoflow.app.ui.evolution

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
import com.egoflow.app.data.entity.EvolutionBacklogEntity
import com.egoflow.app.ui.theme.*

/**
 * 进化中心界面
 *
 * 展示进化蓄水池条目、配置面板，支持导出月度蓝图
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvolutionCenterScreen(
    onBack: () -> Unit,
    viewModel: EvolutionCenterViewModel = viewModel(factory = EvolutionCenterViewModel.Factory())
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showExportDialog by remember { mutableStateOf(false) }
    var exportedContent by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("进化中心", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        exportedContent = viewModel.exportBlueprint()
                        showExportDialog = true
                    }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "导出蓝图")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 过滤器标签
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ALL" to "全部", "USER_PROMPT" to "用户需求", "AI_DIAGNOSIS" to "AI诊断").forEach { (key, label) ->
                    FilterChip(
                        selected = uiState.selectedFilter == key,
                        onClick = { viewModel.setFilter(key) },
                        label = { Text(label) }
                    )
                }
            }

            // 系统配置面板
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "当前调度配置",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    uiState.configOverrides.forEach { (key, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = value.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 进化条目列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filteredEntries = if (uiState.selectedFilter == "ALL") {
                    uiState.entries
                } else {
                    uiState.entries.filter { it.source == uiState.selectedFilter }
                }

                if (filteredEntries.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "暂无进化条目",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                items(filteredEntries) { entry ->
                    EvolutionEntryCard(
                        entry = entry,
                        onImplement = { viewModel.markImplemented(entry.id) },
                        onDeprecate = { viewModel.markDeprecated(entry.id) }
                    )
                }
            }
        }
    }

    // 导出对话框
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("月度进化蓝图") },
            text = {
                SelectionContainer {
                    Text(
                        text = exportedContent,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

@Composable
private fun EvolutionEntryCard(
    entry: EvolutionBacklogEntity,
    onImplement: () -> Unit,
    onDeprecate: () -> Unit
) {
    val sourceIcon = when (entry.source) {
        "USER_PROMPT" -> Icons.Default.Person
        "AI_DIAGNOSIS" -> Icons.Default.Psychology
        else -> Icons.Default.Info
    }
    val categoryColor = when (entry.category) {
        "FEATURE_REQ" -> AccentGold
        "UI_UX" -> MediumBlue
        "TECH_STACK" -> CalmGreen
        else -> SoftAmber
    }
    val statusColor = when (entry.status) {
        "PENDING" -> SoftAmber
        "IMPLEMENTED" -> CalmGreen
        "DEPRECATED" -> HardBlockRed
        else -> SoftAmber
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        sourceIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (entry.source) {
                            "USER_PROMPT" -> "用户反馈"
                            "AI_DIAGNOSIS" -> "AI 诊断"
                            else -> entry.source
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = entry.status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = entry.rawContent,
                style = MaterialTheme.typography.bodyMedium
            )

            if (!entry.aiRefinedSpec.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "细化: ${entry.aiRefinedSpec}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = categoryColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = entry.category,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = categoryColor
                    )
                }
            }

            if (entry.status == "PENDING") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onImplement) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("已实现")
                    }
                    TextButton(onClick = onDeprecate) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("废弃")
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionContainer(content: @Composable () -> Unit) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        content()
    }
}
