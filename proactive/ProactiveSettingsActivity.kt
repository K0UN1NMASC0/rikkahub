package me.rerere.rikkahub.data.service

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

class ProactiveSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ProactiveSettingsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProactiveSettingsScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("proactive_settings", Context.MODE_PRIVATE)

    var baseUrl by remember { mutableStateOf(prefs.getString("proactive_base_url", "") ?: "") }
    var apiKey by remember { mutableStateOf(prefs.getString("proactive_api_key", "") ?: "") }
    var modelId by remember { mutableStateOf(prefs.getString("proactive_model_id", "") ?: "") }
    var interval by remember { mutableStateOf(prefs.getInt("proactive_interval", 180).toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("主动消息设置") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("API Base URL") },
                placeholder = { Text("https://example.com/v1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                placeholder = { Text("sk-...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = modelId,
                onValueChange = { modelId = it },
                label = { Text("Model ID") },
                placeholder = { Text("gpt-4o / claude-sonnet-4...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = interval,
                onValueChange = { interval = it },
                label = { Text("间隔（分钟）") },
                placeholder = { Text("30") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val url = baseUrl.trim()
                    val key = apiKey.trim()
                    val model = modelId.trim()
                    val mins = interval.trim().toIntOrNull() ?: 180

                    if (url.isBlank() || key.isBlank() || model.isBlank()) {
                        Toast.makeText(context, "请填写完整", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    prefs.edit()
                        .putString("proactive_base_url", url)
                        .putString("proactive_api_key", key)
                        .putString("proactive_model_id", model)
                        .putInt("proactive_interval", mins)
                        .apply()

                    ProactiveMessageReceiver.schedule(context, mins, mins)
                    Toast.makeText(context, "已保存！${mins}分钟后发送第一条主动消息", Toast.LENGTH_LONG).show()
                    (context as? ComponentActivity)?.finish()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存并启用")
            }

            OutlinedButton(
                onClick = {
                    ProactiveMessageReceiver.cancel(context)
                    Toast.makeText(context, "已停用", Toast.LENGTH_SHORT).show()
                    (context as? ComponentActivity)?.finish()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("停用主动消息")
            }
        }
    }
}
