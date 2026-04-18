package com.smartreminder.ai

import com.smartreminder.domain.model.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自然语言解析器
 * 使用 LocalNlpParser 进行本地解析（无API依赖）
 */
@Singleton
class NaturalLanguageParser @Inject constructor() {

    private val localParser = LocalNlpParser()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // MiniMax API配置 - 需要用户配置自己的API Key
    private val baseUrl = "https://api.minimax.chat/v1"
    // private val apiKey = "YOUR_API_KEY" // TODO: 从配置获取

    /**
     * 解析用户输入的自然语言
     * @param input 用户输入，如"每天早上8点提醒我拿早餐"
     * @return 解析结果
     */
    suspend fun parse(input: String): ParseResult {
        return try {
            // 使用本地解析器
            val localResult = localParser.parseByRules(input)
            if (localResult != null) {
                val triggerCondition = localParser.toTriggerCondition(localResult)

                // 判断提醒方式
                val reminderMethod = if (input.contains("强提醒")) {
                    ReminderMethod.STRONG_REMINDER
                } else {
                    ReminderMethod.NOTIFICATION
                }

                // 生成标题和内容
                val title = localResult.event.take(10).let {
                    if (localResult.event.length > 10) "$it..." else it
                }
                val content = localResult.event

                return ParseResult.Success(
                    triggerCondition = triggerCondition,
                    reminderMethod = reminderMethod,
                    title = title,
                    content = content
                )
            }

            // 如果本地解析失败，尝试使用简单规则解析
            parseWithRules(input)
        } catch (e: Exception) {
            ParseResult.Error("解析失败: ${e.message}")
        }
    }

    /**
     * 使用简单规则进行解析（备用方案）
     */
    private fun parseWithRules(input: String): ParseResult {
        val text = input.trim()

        // 解析时间
        val timeResult = parseTime(text)
        val hour = timeResult.first
        val minute = timeResult.second

        // 解析重复类型
        val repeatType = parseRepeatType(text)

        // 解析动作类型
        val actionType = if (text.contains("强提醒") || text.contains("强提醒")) {
            ActionType.STRONG_REMINDER
        } else {
            ActionType.NOTIFICATION
        }

        // 解析内容
        val (title, content) = parseContent(text)

        // 构建触发条件
        val triggerCondition = when (repeatType) {
            RepeatType.DAILY -> TriggerCondition.Daily(hour, minute)
            RepeatType.WEEKLY -> TriggerCondition.Weekly(parseDayOfWeek(text), hour, minute)
            RepeatType.MONTHLY -> TriggerCondition.Monthly(parseDayOfMonth(text), hour, minute)
            RepeatType.YEARLY -> TriggerCondition.Yearly(parseMonth(text), parseDayOfMonth(text), hour, minute)
            RepeatType.ONCE -> {
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (calendar.timeInMillis <= System.currentTimeMillis()) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
                TriggerCondition.Once(calendar.timeInMillis)
            }
            RepeatType.INTERVAL -> TriggerCondition.Interval(parseInterval(text), parseIntervalUnit(text))
        }

        return ParseResult.Success(
            triggerCondition = triggerCondition,
            reminderMethod = if (actionType == ActionType.STRONG_REMINDER) {
                ReminderMethod.STRONG_REMINDER
            } else {
                ReminderMethod.NOTIFICATION
            },
            title = title,
            content = content
        )
    }

    private fun parseTime(text: String): Pair<Int, Int> {
        var hour = 8
        var minute = 0

        val timePatterns = listOf(
            Regex("(\\d{1,2}):(\\d{2})"),
            Regex("(\\d{1,2})点(\\d{1,2})分"),
            Regex("(?:早上|上午|早晨|下午|晚上|午后)?(\\d{1,2})点(?:(\\d{1,2})分)?")
        )

        for (pattern in timePatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val groups = match.destructured
                hour = groups.component1().toIntOrNull() ?: hour
                minute = if (groups.component2().isNotEmpty()) {
                    groups.component2().toIntOrNull() ?: 0
                } else {
                    0
                }
                break
            }
        }

        if (text.contains("下午") || text.contains("晚上") || text.contains("午后")) {
            if (hour < 12) hour += 12
        }

        return Pair(hour, minute)
    }

    private fun parseRepeatType(text: String): RepeatType {
        return when {
            text.contains("每天") || text.contains("每日") -> RepeatType.DAILY
            text.contains("每周") || text.contains("每周") -> RepeatType.WEEKLY
            text.contains("每月") || text.contains("每月") -> RepeatType.MONTHLY
            text.contains("每年") || text.contains("每年") -> RepeatType.YEARLY
            text.contains("工作日") -> RepeatType.WEEKLY
            text.contains("每间隔") || text.contains("每隔") -> RepeatType.INTERVAL
            else -> RepeatType.DAILY
        }
    }

    private fun parseDayOfWeek(text: String): Int {
        val dayMap = mapOf(
            "周一" to 1, "星期一" to 1,
            "周二" to 2, "星期二" to 2,
            "周三" to 3, "星期三" to 3,
            "周四" to 4, "星期四" to 4,
            "周五" to 5, "星期五" to 5,
            "周六" to 6, "星期六" to 6,
            "周日" to 7, "星期天" to 7, "星期日" to 7
        )

        for ((key, value) in dayMap) {
            if (text.contains(key)) return value
        }

        return 1
    }

    private fun parseDayOfMonth(text: String): Int {
        val match = Regex("(\\d{1,2})日|(\\d{1,2})号").find(text)
        return match?.destructured?.component1()?.toIntOrNull()
            ?: match?.destructured?.component2()?.toIntOrNull()
            ?: 1
    }

    private fun parseMonth(text: String): Int {
        val match = Regex("(\\d{1,2})月").find(text)
        return match?.destructured?.component1()?.toIntOrNull() ?: 1
    }

    private fun parseInterval(text: String): Long {
        val match = Regex("每(\\d+)(?:分钟|小时|天|周)").find(text)
        return match?.destructured?.component1()?.toLongOrNull() ?: 1
    }

    private fun parseIntervalUnit(text: String): IntervalUnit {
        return when {
            text.contains("分钟") -> IntervalUnit.MINUTES
            text.contains("小时") -> IntervalUnit.HOURS
            text.contains("天") -> IntervalUnit.DAYS
            text.contains("周") -> IntervalUnit.DAYS
            else -> IntervalUnit.DAYS
        }
    }

    private fun parseContent(text: String): Pair<String, String> {
        val match = Regex("(?:提醒我|提醒|告诉我说|跟我说)(.+)").find(text)
        val content = match?.destructured?.component1()?.trim() ?: text

        val title = content.take(10).let {
            if (content.length > 10) "$it..." else it
        }

        return Pair(title, content)
    }

    /**
     * 调用MiniMax API进行解析（需要API Key）
     */
    private suspend fun parseWithApi(input: String, apiKey: String): ParseResult {
        val prompt = """
            你是一个智能提醒解析器。请从用户的自然语言描述中提取信息。

用户的描述：$input

请以JSON格式返回结果：
{
    "triggerType": "daily|weekly|monthly|yearly|once|interval",
    "hour": 8,
    "minute": 0,
    "dayOfWeek": 1,
    "dayOfMonth": 26,
    "month": 6,
    "interval": 30,
    "intervalUnit": "minutes|hours|days",
    "title": "提醒标题",
    "content": "提醒内容"
}
        """.trimIndent()

        val json = JSONObject().apply {
            put("prompt", prompt)
            put("role", "user")
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/text/chatcompletion_v2")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val jsonResponse = JSONObject(responseBody)
                val choices = jsonResponse.getJSONArray("choices")
                if (choices.length() > 0) {
                    val message = choices.getJSONObject(0).getJSONObject("message")
                    val content = message.getString("content")
                    parseApiResponse(content)
                } else {
                    ParseResult.Error("API返回为空")
                }
            } else {
                ParseResult.Error("API错误: ${response.code}")
            }
        } catch (e: Exception) {
            ParseResult.Error("网络错误: ${e.message}")
        }
    }

    private fun parseApiResponse(content: String): ParseResult {
        return try {
            val json = JSONObject(content)
            val triggerType = json.getString("triggerType")

            val hour = json.optInt("hour", 8)
            val minute = json.optInt("minute", 0)

            val triggerCondition: TriggerCondition = when (triggerType) {
                "daily" -> TriggerCondition.Daily(hour, minute)
                "weekly" -> TriggerCondition.Weekly(json.optInt("dayOfWeek", 1), hour, minute)
                "monthly" -> TriggerCondition.Monthly(json.optInt("dayOfMonth", 1), hour, minute)
                "yearly" -> TriggerCondition.Yearly(
                    json.optInt("month", 1),
                    json.optInt("dayOfMonth", 1),
                    hour, minute
                )
                "interval" -> TriggerCondition.Interval(
                    json.optLong("interval", 1),
                    IntervalUnit.valueOf(json.optString("intervalUnit", "DAYS").uppercase())
                )
                else -> TriggerCondition.Once(System.currentTimeMillis() + 86400000)
            }

            ParseResult.Success(
                triggerCondition = triggerCondition,
                reminderMethod = ReminderMethod.NOTIFICATION,
                title = json.optString("title", "提醒"),
                content = json.optString("content", "")
            )
        } catch (e: Exception) {
            ParseResult.Error("解析API响应失败: ${e.message}")
        }
    }

    // 内部类型
    enum class RepeatType { DAILY, WEEKLY, MONTHLY, YEARLY, ONCE, INTERVAL }
    enum class ActionType { NOTIFICATION, STRONG_REMINDER }

    sealed class ParseResult {
        data class Success(
            val triggerCondition: TriggerCondition,
            val reminderMethod: ReminderMethod,
            val title: String,
            val content: String
        ) : ParseResult()

        data class Error(val message: String) : ParseResult()
    }
}
