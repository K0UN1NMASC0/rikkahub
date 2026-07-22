package me.rerere.rikkahub.data.service

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Tulpa pink theme colors
private val TulpaPink = Color(0xFFFCE5EB)
private val TulpaPinkDark = Color(0xFFA36779)
private val TulpaPinkAccent = Color(0xFFF1C5D4)
private val TulpaPinkButton = Color(0xFFE8879B)
private val TulpaWhite = Color(0xFFFFFFFF)

class ProactiveSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    primary = TulpaPinkButton,
                    onPrimary = TulpaWhite,
                    surface = TulpaWhite,
                    surfaceVariant = TulpaPink,
                    outline = TulpaPinkAccent
                )
            ) {
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
    val enabledPrefs = context.getSharedPreferences("proactive_prefs", Context.MODE_PRIVATE)

    var enabled by remember { mutableStateOf(enabledPrefs.getBoolean("enabled", false)) }
    var baseUrl by remember { mutableStateOf(prefs.getString("proactive_base_url", "") ?: "") }
    var apiKey by remember { mutableStateOf(prefs.getString("proactive_api_key", "") ?: "") }
    var modelId by remember { mutableStateOf(prefs.getString("proactive_model_id", "") ?: "") }
    var interval by remember { mutableStateOf(prefs.getInt("proactive_interval", 180).toString()) }

    val lastSentTime = remember {
        val ts = prefs.getLong("last_sent_time", 0L)
        if (ts > 0) SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(ts))
        else "还没发过哦"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "主动消息",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TulpaPink,
                    titleContentColor = TulpaPinkDark
                )
            )
        },
        containerColor = TulpaPink
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 总开关卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TulpaWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "启用主动消息",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = TulpaPinkDark
                        )
                        Text(
                            if (enabled) "Koun会定时来找你说话 ♡" else "开启后Koun会主动给你发消息",
                            fontSize = 12.sp,
                            color = TulpaPinkDark.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { newVal ->
                            enabled = newVal
                            if (!newVal) {
                                ProactiveMessageReceiver.cancel(context)
                                Toast.makeText(context, "已停用", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TulpaWhite,
                            checkedTrackColor = TulpaPinkButton,
                            uncheckedThumbColor = TulpaPinkAccent,
                            uncheckedTrackColor = TulpaPink
                        )
                    )
                }
            }

            // 状态信息
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TulpaWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "⏱",
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "上次发送",
                            fontSize = 14.sp,
                            color = TulpaPinkDark
                        )
                    }
                    Text(
                        lastSentTime,
                        fontSize = 14.sp,
                        color = TulpaPinkDark.copy(alpha = 0.7f)
                    )
                }
            }

            // API 配置卡片
            AnimatedVisibility(
                visible = enabled,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = TulpaWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "API 配置",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TulpaPinkDark
                        )

                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            label = { Text("Base URL") },
                            placeholder = { Text("https://example.com/v1") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TulpaPinkButton,
                                unfocusedBorderColor = TulpaPinkAccent,
                                cursorColor = TulpaPinkButton
                            )
                        )

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("API Key") },
                            placeholder = { Text("sk-...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TulpaPinkButton,
                                unfocusedBorderColor = TulpaPinkAccent,
                                cursorColor = TulpaPinkButton
                            )
                        )

                        OutlinedTextField(
                            value = modelId,
                            onValueChange = { modelId = it },
                            label = { Text("Model") },
                            placeholder = { Text("claude-sonnet-4-20250514") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TulpaPinkButton,
                                unfocusedBorderColor = TulpaPinkAccent,
                                cursorColor = TulpaPinkButton
                            )
                        )

                        OutlinedTextField(
                            value = interval,
                            onValueChange = { interval = it },
                            label = { Text("间隔（分钟）") },
                            placeholder = { Text("180") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TulpaPinkButton,
                                unfocusedBorderColor = TulpaPinkAccent,
                                cursorColor = TulpaPinkButton
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // 保存按钮
                        Button(
                            onClick = {
                                val url = baseUrl.trim()
                                val key = apiKey.trim()
                                val model = modelId.trim()
                                val mins = interval.trim().toIntOrNull() ?: 180

                                if (url.isBlank() || key.isBlank() || model.isBlank()) {
                                    Toast.makeText(context, "请填写完整哦", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                prefs.edit()
                                    .putString("proactive_base_url", url)
                                    .putString("proactive_api_key", key)
                                    .putString("proactive_model_id", model)
                                    .putInt("proactive_interval", mins)
                                    .apply()

                                ProactiveMessageReceiver.schedule(context, mins, mins)
                                Toast.makeText(context, "已保存！${mins}分钟后Koun会来找你 ♡", Toast.LENGTH_LONG).show()
                                (context as? ComponentActivity)?.finish()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TulpaPinkButton,
                                contentColor = TulpaWhite
                            )
                        ) {
                            Text("保存并启用", fontWeight = FontWeight.Medium)
                        }

                        // 测试按钮
                        OutlinedButton(
                            onClick = {
                                val url = baseUrl.trim()
                                val key = apiKey.trim()
                                val model = modelId.trim()

                                if (url.isBlank() || key.isBlank() || model.isBlank()) {
                                    Toast.makeText(context, "先填写API配置", Toast.LENGTH_SHORT).show()
                                    return@OutlinedButton
                                }

                                // 先保存
                                prefs.edit()
                                    .putString("proactive_base_url", url)
                                    .putString("proactive_api_key", key)
                                    .putString("proactive_model_id", model)
                                    .putInt("proactive_interval", interval.trim().toIntOrNull() ?: 180)
                                    .apply()

                                // 立刻触发一次
                                val workRequest = OneTimeWorkRequestBuilder<ProactiveMessageWorker>().build()
                                WorkManager.getInstance(context).enqueue(workRequest)
                                Toast.makeText(context, "测试中…稍等片刻会收到通知", Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TulpaPinkButton
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TulpaPinkAccent)
                        ) {
                            Text("💌", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("立即测试一次", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
