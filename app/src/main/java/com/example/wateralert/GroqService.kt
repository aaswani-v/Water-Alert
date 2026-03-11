package com.example.wateralert

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Represents the Groq API Request Body
data class GroqRequest(
    val model: String,  
    val response_format: ResponseFormat? = null,
    val messages: List<Message>
)

data class ResponseFormat(
    val type: String = "json_object"
)

data class Message(
    val role: String,
    val content: String
)

// Represents the Groq API Response
data class GroqResponse(
    val choices: List<Choice>?
)

data class Choice(
    val message: Message?
)

interface GroqApi {
    @POST("v1/chat/completions")
    suspend fun generateChatCompletion(
        @Header("Authorization") authHeader: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: GroqRequest
    ): GroqResponse
}

class GroqService {

    private val api: GroqApi

    init {
        val logging = HttpLoggingInterceptor()
        logging.setLevel(HttpLoggingInterceptor.Level.BODY)
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.groq.com/openai/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        api = retrofit.create(GroqApi::class.java)
    }

    private fun getCurrentLocalTime(): String {
        return SimpleDateFormat("h:mm a, EEEE, dd MMM yyyy", Locale.ENGLISH).format(Date())
    }

    /**
     * Sends batch water messages to Groq and expects a STRICT JSON response containing the summary and 24h alarm time.
     */
    suspend fun summarizeWaterMessage(batchMessages: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val promptContent = """
                    You are an assistant for a Hindi local. The current local time is: ${getCurrentLocalTime()}.
                    Your job is to read these batched WhatsApp group messages containing water supply timings and give a clear summary of exactly WHEN the water will come.
                    
                    Rules:
                    1. Output MUST be strictly valid JSON.
                    2. "spoken_summary": simple conversational Hindi (Devanagari script) ONLY. Even if the input messages are in Marathi, English, or Hinglish, you MUST translate and reply in proper Hindi. Do not use Marathi words.
                    3. "water_time_hour_24h": integer (0-23) representing the absolute hour the water comes. Calculate this relative to the current local time based on what the messages say. Put 0 if unknown.
                    4. "water_time_minute": integer (0-59) for the minute. Put 0 if unknown.
                    
                    Please reply with JSON.
                    
                    Messages to summarize: "$batchMessages"
                """.trimIndent()

                val requestBody = GroqRequest(
                    model = "llama-3.3-70b-versatile",
                    response_format = ResponseFormat(type = "json_object"),
                    messages = listOf(Message(role = "user", content = promptContent))
                )

                val response = api.generateChatCompletion(
                    authHeader = "Bearer ${BuildConfig.GROQ_API_KEY}",
                    request = requestBody
                )

                response.choices?.firstOrNull()?.message?.content
            } catch (e: Exception) {
                android.util.Log.e("WaterAlert_Groq", "Water API Failed", e)
                "ERROR: ${e.message}"
            }
        }
    }

    /**
     * Sends batch general messages to Groq for a fun society gossip summary.
     */
    suspend fun summarizeGossip(batchMessages: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val promptContent = """
                    You are an assistant for a Hindi local. The current local time is: ${getCurrentLocalTime()}.
                    Summarize these batched WhatsApp group messages. Give a friendly, conversational 2-sentence summary of what's going on in the society.
                    
                    CRITICAL RULE: The input messages might be in English, Marathi, or Hinglish, but your output summary MUST be entirely in pure Hindi (Devanagari script) ONLY. Do not use Marathi words. Give it a slight "gossip" tone to make it fun.
                    
                    Messages: "$batchMessages"
                """.trimIndent()

                val requestBody = GroqRequest(
                    model = "llama-3.3-70b-versatile",
                    messages = listOf(Message(role = "user", content = promptContent))
                )

                val response = api.generateChatCompletion(
                    authHeader = "Bearer ${BuildConfig.GROQ_API_KEY}",
                    request = requestBody
                )

                val reply = response.choices?.firstOrNull()?.message?.content
                reply?.trim()
            } catch (e: Exception) {
                android.util.Log.e("WaterAlert_Groq", "Gossip API Failed", e)
                "ERROR: ${e.message}"
            }
        }
    }
}
