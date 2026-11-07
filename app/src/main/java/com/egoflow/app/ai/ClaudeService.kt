package com.egoflow.app.ai

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Claude API 封装 —— 用于周/月度宏观反思和代码演进
 */
class ClaudeService {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("x-api-key", AiConfig.claudeApiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    data class Message(val role: String, val content: String)

    /**
     * 发送反思分析到 Claude
     */
    suspend fun sendReflection(
        systemPrompt: String,
        messages: List<Message>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("model", AiConfig.CLAUDE_MODEL)
                put("max_tokens", 8192)
                put("system", systemPrompt)
                put("messages", JSONArray().apply {
                    messages.forEach { msg ->
                        put(JSONObject().apply {
                            put("role", msg.role)
                            put("content", msg.content)
                        })
                    }
                })
            }

            val request = Request.Builder()
                .url("${AiConfig.claudeBaseUrl}/v1/messages")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val content = json.getJSONArray("content")
                    .getJSONObject(0)
                    .getString("text")
                Result.success(content)
            } else {
                Result.failure(IOException("Claude API error: ${response.code} $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 解析进化配置字典（Configuration Overrides）
     */
    fun parseEvolutionConfig(response: String): String? {
        val jsonPattern = Regex("""\{[\s\S]*?"evolution_action"[\s\S]*?\}""")
        return jsonPattern.find(response)?.value
    }
}
