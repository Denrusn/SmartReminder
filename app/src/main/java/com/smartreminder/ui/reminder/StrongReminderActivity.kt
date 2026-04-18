package com.smartreminder.ui.reminder

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.smartreminder.databinding.ActivityStrongReminderBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StrongReminderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStrongReminderBinding

    private var pressStartTime = 0L
    private var isLongPress = false
    private val longPressDuration = 2000L // 2秒

    private var vibrator: Vibrator? = null
    private var toneGenerator: ToneGenerator? = null

    // 用于取消长按检查的Runnable
    private val longPressChecker = Runnable {
        isLongPress = true
        binding.progressIndicator.visibility = View.GONE
        binding.tvConfirmHint.text = "✓ 确认成功"
        binding.btnConfirm.setBackgroundColor(Color.parseColor("#4CAF50"))
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 设置为全屏、锁屏可见
        setupFullScreen()

        binding = ActivityStrongReminderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 获取传递的数据
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "提醒"
        val content = intent.getStringExtra(EXTRA_CONTENT) ?: ""
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1)

        binding.tvTitle.text = title
        binding.tvContent.text = content

        // 启动震动和声音
        startAlert()

        // 设置按钮事件
        setupButton()

        // 稍后提醒按钮
        binding.btnSnooze.setOnClickListener {
            snoozeReminder(reminderId, 5)
            finish()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupButton() {
        binding.btnConfirm.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    pressStartTime = System.currentTimeMillis()
                    isLongPress = false
                    binding.tvConfirmHint.text = "长按2秒确认"
                    binding.btnConfirm.setBackgroundColor(Color.parseColor("#FFFFFF"))
                    binding.progressIndicator.visibility = View.VISIBLE
                    binding.progressIndicator.progress = 0
                    // 开始长按倒计时
                    Handler(Looper.getMainLooper()).postDelayed(longPressChecker, longPressDuration)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val pressDuration = System.currentTimeMillis() - pressStartTime
                    // 取消长按检查的回调
                    Handler(Looper.getMainLooper()).removeCallbacks(longPressChecker)

                    if (pressDuration >= longPressDuration && isLongPress) {
                        // 长按确认成功
                        stopAlert()
                        finish()
                    } else {
                        // 按压时间不够，重置
                        resetProgress()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    Handler(Looper.getMainLooper()).removeCallbacks(longPressChecker)
                    resetProgress()
                    true
                }
                else -> false
            }
        }
    }

    private fun resetProgress() {
        isLongPress = false
        binding.progressIndicator.progress = 0
        binding.progressIndicator.visibility = View.VISIBLE
        binding.tvConfirmHint.text = "长按2秒确认"
        binding.btnConfirm.setBackgroundColor(Color.parseColor("#FFFFFF"))
    }

    private fun startAlert() {
        // 震动
        val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibrator = vibratorManager.defaultVibrator

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
            val vibrationEffect = VibrationEffect.createWaveform(pattern, -1)
            vibrator?.vibrate(vibrationEffect)
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 500, 200, 500, 200, 500), -1)
        }

        // 播放提示音
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 循环播放提示音
        Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
            override fun run() {
                if (!isFinishing) {
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)
                    Handler(Looper.getMainLooper()).postDelayed(this, 3000)
                }
            }
        }, 2000)
    }

    private fun stopAlert() {
        vibrator?.cancel()
        toneGenerator?.release()
        toneGenerator = null
    }

    private fun snoozeReminder(reminderId: Long, minutes: Int) {
        // TODO: 实现稍后提醒功能
    }

    @Suppress("DEPRECATION")
    private fun setupFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }

        // 锁屏可见
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlert()
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_CONTENT = "extra_content"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
    }
}
