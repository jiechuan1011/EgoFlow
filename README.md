<div align="center">
  <h1>🧠 EgoFlow · 自我流</h1>
  <p><strong>AI 精力调度与系统自我进化引擎</strong></p>
  <p>全方位接管、保护并优化高认知个体有限意志力的"外置大脑"执行系统</p>

  <p>
    <img src="https://img.shields.io/badge/Kotlin-2.0-blueviolet?logo=kotlin" alt="Kotlin">
    <img src="https://img.shields.io/badge/Compose-Material3-green?logo=jetpackcompose" alt="Compose">
    <img src="https://img.shields.io/badge/Room-SQLite-orange" alt="Room">
    <img src="https://img.shields.io/badge/MinSdk-26-brightgreen" alt="MinSdk">
    <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License">
  </p>
</div>

---

## 📋 项目定位

EgoFlow 绝非传统的 Todo 待办列表应用，而是专门为**高智商、技术型创作者**设计的全方位执行与进化系统。它深刻理解多任务认知过载导致的"合理化逃避"心理——当面对高压的主线任务时，人们往往会倾向于钻研更具挑战性的技术支线。

### 核心哲学

1. **决策层与执行层隔离**：AI 代理负责统筹排班，界面只暴露当前唯一该执行的卡片
2. **主线优先，支线奖励**：严厉拦截技术执念，将技术钻研转化为高效执行后的正向激励机制
3. **纯本地安全闭环**：全部数据扎根 Android 本地，极致响应速度与全物理隔离

---

## 🏗 系统架构

```
                    ┌─────────────────────────────┐
                    │    用户界面 (Jetpack Compose)  │
                    │  FocusFirewall | ChatCoach    │
                    │  EvolutionCenter | Timeline   │
                    └──────────────┬──────────────┘
                                   │
              ┌────────────────────┼────────────────────┐
              │                    │                    │
         ┌────▼────┐        ┌─────▼─────┐       ┌─────▼─────┐
         │ ViewModel │        │ UseCases  │       │ Repository │
         └────┬────┘        └─────┬─────┘       └─────┬─────┘
              │                    │                    │
         ┌────▼────────────────────▼────────────────────▼─────┐
         │              弹性调度引擎 (ElasticSchedulingEngine)│
         │       硬墙碰撞检测 · 主线优先 · 支线奖励解锁       │
         └──────────────────────┬────────────────────────────┘
                                │
         ┌──────────────────────┼──────────────────────┐
         │                      │                      │
    ┌────▼────┐          ┌─────▼─────┐          ┌─────▼─────┐
    │Room DB │          │DeepSeekAPI │          │ ClaudeAPI │
    │本地SQL  │          │ 高频对话   │          │ 宏观反思  │
    └────────┘          └───────────┘          └───────────┘
```

### 双模型 AI 路由

| 场景 | 模型 | 职责 |
|------|------|------|
| 日常对话与日程编排 | **DeepSeek Chat / R1** | 碎碎念录入、弹性排班、进化需求捕获 |
| 周/月度宏观反思 | **Claude 3.5 Sonnet** | 心理诊断、系统代码演进、配置字典生成 |

---

## ✨ 核心功能

### 🔥 认知防火墙
- 主界面只显示**一个当前精力块**
- 大字番茄钟倒计时
- 全景任务池隐藏于二级菜单
- 彻底卸载"未做事情总体积"带来的焦虑

### 💬 AI 教练对话
- 仿 IM 聊天界面
- 主支线强制隔离（MAIN_LINE / SUB_LINE）
- 单一焦点逼问协议
- 进化需求自动拦截与入库

### 🔄 弹性日程调度
- **硬墙机制**：课程表等刚性时段绝对不可侵犯
- **主线优先**：高耗刷题任务抢占 08:00-12:00 黄金时段
- **支线奖励**：主线满 3 小时解锁 20:00 后的技术钻研时段
- **同等级互换**：相同 Drain Level 的任务可拖拽对调

### 🧬 系统自我进化
- **双通道采集**：用户主动吐槽 + AI 自主诊断
- **进化蓄水池**：所有灵感与建议结构化存储
- **月度蓝图**：一键导出 Markdown 升级说明书
- **配置字典**：Claude 生成的动态参数覆盖

---

## 📱 页面结构

| 页面 | 路径 | 说明 |
|------|------|------|
| FocusFirewall | `ui/focus/` | 主屏幕：单卡聚焦 + 番茄钟 |
| ChatCoach | `ui/coach/` | AI 教练对话 |
| EvolutionCenter | `ui/evolution/` | 进化中心：配置面板 + 蓝图导出 |
| ScheduleTimeline | `ui/timeline/` | 日程时间线：弹性块编排 |

---

## 🛠 开发指南

### 环境要求

- Android Studio Hedgehog (2023.1.1+) 或更高版本
- JDK 17
- Android SDK 34
- Gradle 8.5

### 本地构建

```bash
# 克隆项目
git clone https://github.com/你的用户名/egoflow.git
cd egoflow

# 配置 API Key（编辑 app/.../ai/AiConfig.kt）
# deepSeekApiKey = "your-deepseek-key"
# claudeApiKey = "your-claude-key"

# 使用 Android Studio 打开项目，或命令行构建
./gradlew assembleDebug    # Debug 构建
./gradlew assembleRelease  # Release 构建（需要签名配置）
```

### 签名配置

**本地开发**：项目已包含 `egoflow_keystore.jks`（密码：`egoflow_release`），
`keystore.properties` 自动读取该文件。**发布前请更换为自己的证书！**

**CI 自动构建**：在 GitHub 仓库的 Secrets 中配置：

| Secret | 说明 |
|--------|------|
| `KEYSTORE_BASE64` | keystore 文件的 base64 编码 |
| `KEYSTORE_PASSWORD` | 证书存储密码 |
| `KEY_ALIAS` | 证书别名 |
| `KEY_PASSWORD` | 证书密钥密码 |

### 生成发布 APK

```bash
# 方式一：手动构建
./gradlew assembleRelease

# 方式二：通过 CI（推送到 main 分支或打 v* tag）
git tag v1.0.0
git push origin v1.0.0
# GitHub Actions 自动构建并发布 Release
```

---

## 📦 数据库结构

| 表 | 说明 |
|----|------|
| `tasks` | 核心任务动态表（主支线、能耗等级、状态） |
| `hard_blocks` | 刚性拦截课表（绝对不可侵犯时段） |
| `evolution_backlog` | 进化需求蓄水池 |
| `daily_metrics` | 每日精力与生理特征 |

---

## 🧪 测试用例

### 硬墙碰撞测试
用户导入 14:00-15:30 课程，尝试排入 120 分钟高耗刷题任务
→ 弹性调度引擎触发碰撞拦截 → 任务自动平移至 15:30 后

### 进化通道测试
用户说"帮我加一个计时结束后的长震动提醒"
→ 对话模型识别为 EVOLITION → 任务池无变化 → `evolution_backlog` 新增一条记录

---

## 📄 许可证

```
MIT License

Copyright (c) 2026 EgoFlow

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files...
```

---

<div align="center">
  <sub>Built with ❤️ for high-cognition creators who deserve better than a todo list.</sub>
</div>
