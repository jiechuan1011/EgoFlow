package com.egoflow.app.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * DeepSeek API 封装 —— 用于高频日常对话和日程编排
 */
class DeepSeekService {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer ${AiConfig.deepSeekApiKey}")
                .addHeader("Content-Type", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    data class ChatMessage(val role: String, val content: String)

    /**
     * 发送对话消息到 DeepSeek Chat
     */
    suspend fun sendChatMessage(
        messages: List<ChatMessage>,
        systemPrompt: String = AiConfig.COACH_SYSTEM_PROMPT
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("model", AiConfig.DEEPSEEK_CHAT_MODEL)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    messages.forEach { msg ->
                        put(JSONObject().apply {
                            put("role", msg.role)
                            put("content", msg.content)
                        })
                    }
                })
                put("max_tokens", 4096)
                put("temperature", 0.7)
            }

            val request = Request.Builder()
                .url("${AiConfig.deepSeekBaseUrl}/v1/chat/completions")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val content = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                Result.success(content)
            } else {
                Result.failure(IOException("DeepSeek API error: ${response.code} $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 使用 DeepSeek Reasoner (R1) 进行深度分析和规划
     */
    suspend fun sendReasonerMessage(
        messages: List<ChatMessage>,
        systemPrompt: String = AiConfig.COACH_SYSTEM_PROMPT
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("model", AiConfig.DEEPSEEK_REASONER_MODEL)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    messages.forEach { msg ->
                        put(JSONObject().apply {
                            put("role", msg.role)
                            put("content", msg.content)
                        })
                    }
                })
                put("max_tokens", 8192)
            }

            val request = Request.Builder()
                .url("${AiConfig.deepSeekBaseUrl}/v1/chat/completions")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val content = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                Result.success(content)
            } else {
                Result.failure(IOException("DeepSeek Reasoner API error: ${response.code} $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 从响应中解析 JSON 动作
     */
    fun parseAction(response: String): String? {
        val jsonPattern = Regex("""```json\n([\s\S]*?)\n```""")
        val match = jsonPattern.find(response)
        return match?.groupValues?.getOrNull(1)
    }
}
