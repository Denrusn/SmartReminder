package com.smartreminder.ai

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * LocalNlpParser 单元测试
 *
 * 测试用例参考 Python scheduler_parser.py __main__ 中的示例
 */
class LocalNlpParserTest {

    private val parser = LocalNlpParser()

    @Test
    fun `test 下午3点45分提醒我吃午饭`() {
        val result = parser.parseByRules("下午3点45分提醒我吃午饭")
        assertNotNull(result)

        // 验证时间是下午3:45
        assertEquals(15, result!!.time.hour)
        assertEquals(45, result.time.minute)

        // 验证事件
        assertTrue(result.event.contains("午饭") || result.event.contains("吃午饭"))
    }

    @Test
    fun `test 每天早上8点提醒我拿早餐`() {
        val result = parser.parseByRules("每天早上8点提醒我拿早餐")
        assertNotNull(result)

        // 验证重复
        assertTrue(result!!.repeat.containsKey("days"))
        assertEquals(1, result.repeat["days"])

        // 验证时间是8:00
        assertEquals(8, result.time.hour)
        assertEquals(0, result.time.minute)
    }

    @Test
    fun `test 每隔2小时提醒我喝水`() {
        val result = parser.parseByRules("每隔2小时提醒我喝水")
        assertNotNull(result)

        // 验证重复
        assertTrue(result!!.repeat.containsKey("hours"))
        assertEquals(2, result.repeat["hours"])
    }

    @Test
    fun `test 明天早上9点提醒我开会`() {
        val result = parser.parseByRules("明天早上9点提醒我开会")
        assertNotNull(result)

        // 验证时间是9:00
        assertEquals(9, result!!.time.hour)
        assertEquals(0, result.time.minute)

        // 验证事件
        assertTrue(result.event.contains("开会"))
    }

    @Test
    fun `test 每周一早上9点提醒我开会`() {
        val result = parser.parseByRules("每周一早上9点提醒我开会")
        assertNotNull(result)

        // 验证重复
        assertTrue(result!!.repeat.containsKey("weeks"))

        // 验证时间是9:00
        assertEquals(9, result.time.hour)
        assertEquals(0, result.time.minute)
    }

    @Test
    fun `test 每周日早上9点43提醒我`() {
        val result = parser.parseByRules("每周日早上9:43提醒我")
        assertNotNull(result)

        // 验证重复
        assertTrue(result!!.repeat.containsKey("weeks"))

        // 验证时间是9:43
        assertEquals(9, result.time.hour)
        assertEquals(43, result.time.minute)

        // 验证解析出的 weekday 是周日（7）
        assertEquals(7, result.weekday)

        // 验证 toTriggerCondition 返回 Weekly 类型，且 dayOfWeek=7
        val triggerCondition = parser.toTriggerCondition(result)
        assertTrue(triggerCondition is com.smartreminder.domain.model.TriggerCondition.Weekly)
        val weekly = triggerCondition as com.smartreminder.domain.model.TriggerCondition.Weekly
        assertEquals(7, weekly.dayOfWeek)
    }

    @Test
    fun `test 每周三下午2点30分提醒我开会`() {
        val result = parser.parseByRules("每周三下午2点30分提醒我开会")
        assertNotNull(result)

        // 验证重复
        assertTrue(result!!.repeat.containsKey("weeks"))

        // 验证时间是14:30
        assertEquals(14, result.time.hour)
        assertEquals(30, result.time.minute)
    }

    @Test
    fun `test 每月20号提醒我还信用卡`() {
        val result = parser.parseByRules("每月20号提醒我还信用卡")
        assertNotNull(result)

        // 验证重复
        assertTrue(result!!.repeat.containsKey("months"))

        // 验证日期是20号
        assertEquals(20, result.time.dayOfMonth)
    }

    @Test
    fun `test 半小时后提醒我`() {
        val result = parser.parseByRules("半小时后提醒我")
        assertNotNull(result)

        // 验证时间大约是30分钟后
        val now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
        val expectedTime = now.plusMinutes(30)

        // 允许1分钟误差
        assertTrue(
            kotlin.math.abs(java.time.Duration.between(result!!.time, expectedTime).toMinutes()) <= 1
        )
    }

    @Test
    fun `test 5分钟后提醒我`() {
        val result = parser.parseByRules("5分钟后提醒我")
        assertNotNull(result)

        val now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
        val expectedTime = now.plusMinutes(5)

        // 允许1分钟误差
        assertTrue(
            kotlin.math.abs(java.time.Duration.between(result!!.time, expectedTime).toMinutes()) <= 1
        )
    }

    @Test
    fun `test 今天下午4点提醒我`() {
        val result = parser.parseByRules("今天下午4点提醒我")
        assertNotNull(result)

        // 验证时间是16:00
        assertEquals(16, result!!.time.hour)
        assertEquals(0, result.time.minute)
    }

    @Test
    fun `test 后天下午3点提醒我`() {
        val result = parser.parseByRules("后天下午3点提醒我")
        assertNotNull(result)

        // 验证时间是15:00
        assertEquals(15, result!!.time.hour)
    }

    @Test
    fun `test 下午3点提醒我吃午饭`() {
        val result = parser.parseByRules("下午3点提醒我吃午饭")
        assertNotNull(result)

        assertEquals(15, result!!.time.hour)
    }

    @Test
    fun `test toTriggerCondition for daily`() {
        val result = parser.parseByRules("每天早上8点提醒我")
        assertNotNull(result)

        val triggerCondition = parser.toTriggerCondition(result!!)
        assertTrue(triggerCondition is com.smartreminder.domain.model.TriggerCondition.Daily)
    }

    @Test
    fun `test toTriggerCondition for once`() {
        val result = parser.parseByRules("今天下午3点提醒我")
        assertNotNull(result)

        val triggerCondition = parser.toTriggerCondition(result!!)
        assertTrue(triggerCondition is com.smartreminder.domain.model.TriggerCondition.Once)
    }

    @Test
    fun `test natureTime helper`() {
        val future = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusHours(2)
        val natureTimeMsg = parser.natureTime(future)

        assertTrue(natureTimeMsg.contains("小时"))
    }

    @Test
    fun `test empty input returns null`() {
        val result = parser.parseByRules("")
        assertNull(result)
    }

    @Test
    fun `test 8点30分提醒我`() {
        val result = parser.parseByRules("8点30分提醒我")
        assertNotNull(result)
        assertEquals(8, result!!.time.hour)
        assertEquals(30, result.time.minute)
    }
}
