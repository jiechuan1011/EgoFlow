package com.egoflow.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private val TAB_LABELS = listOf("DeepSeek", "Claude", "OpenAI", "Gemini", "自定义")
private val TAB_ICONS = listOf(Icons.Default.Code, Icons.Default.Psychology, Icons.Default.Lightbulb, Icons.Default.Star, Icons.Default.AddBox)
private val PROVIDER_NAMES = listOf("DeepSeek", "Claude", "OpenAI", "Gemini", "自定义")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory())
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showKey by remember { mutableStateOf(false) }
    var showBaseUrl by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) { kotlinx.coroutines.delay(2000); viewModel.clearSavedFlag() }
    }

    val currentConfig = when (uiState.selectedTab) {
        0 -> uiState.deepSeek; 1 -> uiState.claude; 2 -> uiState.openAi
        3 -> uiState.gemini; else -> uiState.custom
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = uiState.selectedTab) {
                TAB_LABELS.forEachIndexed { i, label ->
                    Tab(
                        selected = uiState.selectedTab == i,
                        onClick = { viewModel.selectTab(i) },
                        text = { Text(label, maxLines = 1) },
                        icon = { Icon(TAB_ICONS[i], contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                // 自定义供应商名称 + 模型名（仅第5个tab）
                if (uiState.selectedTab == 4) {
                    OutlinedTextField(
                        value = uiState.custom.providerName,
                        onValueChange = { viewModel.updateCustomName(it) },
                        label = { Text("供应商名称") },
                        placeholder = { Text("如：硅基流动、Groq...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.custom.modelName,
                        onValueChange = { viewModel.updateCustomModel(it) },
                        label = { Text("模型名称") },
                        placeholder = { Text("如：gpt-4o-mini、qwen-turbo...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // API Key
                OutlinedTextField(
                    value = currentConfig.apiKey,
                    onValueChange = { viewModel.updateApiKey(it) },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                        }
                    }
                )

                // Base URL（折叠）
                Surface(
                    onClick = { showBaseUrl = !showBaseUrl },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("自定义 Base URL", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Icon(if (showBaseUrl) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                    }
                }
                AnimatedVisibility(visible = showBaseUrl) {
                    OutlinedTextField(
                        value = currentConfig.baseUrl,
                        onValueChange = { viewModel.updateBaseUrl(it) },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                HorizontalDivider()
                Spacer(Modifier.height(4.dp))

                // 用途选择
                Text("选择用途", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("日常对话", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PROVIDER_NAMES.forEachIndexed { i, name ->
                        FilterChip(
                            selected = uiState.chatProvider == i,
                            onClick = { viewModel.setChatProvider(i) },
                            label = { Text(name) }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("月度蓝图", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PROVIDER_NAMES.forEachIndexed { i, name ->
                        FilterChip(
                            selected = uiState.blueprintProvider == i,
                            onClick = { viewModel.setBlueprintProvider(i) },
                            label = { Text(name) }
                        )
                    }
                }

                // 保存提示
                if (uiState.saved) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("已保存", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
