package me.rerere.rikkahub.data.service

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ProactiveSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("proactive_settings", Context.MODE_PRIVATE)

        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "主动消息设置"
            textSize = 22f
            setPadding(0, 0, 0, 32)
        }
        layout.addView(title)

        val labelUrl = TextView(this).apply { text = "API Base URL"; setPadding(0, 16, 0, 4) }
        layout.addView(labelUrl)
        val editUrl = EditText(this).apply {
            hint = "https://example.com/v1"
            setText(prefs.getString("proactive_base_url", ""))
            isSingleLine = true
        }
        layout.addView(editUrl)

        val labelKey = TextView(this).apply { text = "API Key"; setPadding(0, 16, 0, 4) }
        layout.addView(labelKey)
        val editKey = EditText(this).apply {
            hint = "sk-..."
            setText(prefs.getString("proactive_api_key", ""))
            isSingleLine = true
        }
        layout.addView(editKey)

        val labelModel = TextView(this).apply { text = "Model ID"; setPadding(0, 16, 0, 4) }
        layout.addView(labelModel)
        val editModel = EditText(this).apply {
            hint = "gpt-4o / claude-sonnet-4..."
            setText(prefs.getString("proactive_model_id", ""))
            isSingleLine = true
        }
        layout.addView(editModel)

        val labelInterval = TextView(this).apply { text = "间隔（分钟）"; setPadding(0, 16, 0, 4) }
        layout.addView(labelInterval)
        val editInterval = EditText(this).apply {
            hint = "30"
            setText(prefs.getInt("proactive_interval", 180).toString())
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        layout.addView(editInterval)

        val btnSave = Button(this).apply {
            text = "保存并启用"
            setPadding(0, 32, 0, 0)
        }
        btnSave.setOnClickListener {
            val url = editUrl.text.toString().trim()
            val key = editKey.text.toString().trim()
            val model = editModel.text.toString().trim()
            val interval = editInterval.text.toString().trim().toIntOrNull() ?: 180

            if (url.isBlank() || key.isBlank() || model.isBlank()) {
                Toast.makeText(this, "请填写完整", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putString("proactive_base_url", url)
                .putString("proactive_api_key", key)
                .putString("proactive_model_id", model)
                .putInt("proactive_interval", interval)
                .apply()

            // Schedule with the new interval
            ProactiveMessageReceiver.schedule(this, interval, interval)

            Toast.makeText(this, "已保存！${interval}分钟后发送第一条主动消息", Toast.LENGTH_LONG).show()
            finish()
        }
        layout.addView(btnSave)

        val btnDisable = Button(this).apply {
            text = "停用主动消息"
        }
        btnDisable.setOnClickListener {
            ProactiveMessageReceiver.cancel(this@ProactiveSettingsActivity)
            Toast.makeText(this, "已停用", Toast.LENGTH_SHORT).show()
            finish()
        }
        layout.addView(btnDisable)

        scrollView.addView(layout)
        setContentView(scrollView)
    }
}
