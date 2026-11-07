package com.egoflow.app.domain.model

/**
 * AI教练交互式选项
 *
 * AI 生成一个问题及多个选项，用户可点选或自定义输入
 */
data class CoachOption(
    val id: String,
    val label: String,
    val description: String? = null
)

data class CoachOptionsGroup(
    val question: String,
    val options: List<CoachOption>,
    val allowCustomInput: Boolean = true
)
