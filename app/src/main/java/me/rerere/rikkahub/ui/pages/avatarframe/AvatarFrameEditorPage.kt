package me.rerere.rikkahub.ui.pages.avatarframe

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import me.rerere.rikkahub.data.model.AvatarFrame
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// Tulpa pink theme colors (consistent with ProactiveSettings)
private val TulpaPink = Color(0xFFFCE5EB)
private val TulpaPinkDark = Color(0xFFA36779)
private val TulpaPinkAccent = Color(0xFFF1C5D4)
private val TulpaPinkButton = Color(0xFFE8879B)
private val TulpaWhite = Color(0xFFFFFFFF)

/**
 * 头像框贴纸编辑器
 *
 * 功能：
 * - 选择一个 PNG 贴纸素材（从本地相册）
 * - 在预览区域拖拽、缩放、旋转贴纸
 * - 实时预览头像 + 贴纸的叠加效果
 * - 保存到 DisplaySetting
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarFrameEditorPage(
    target: String = "user",
    vm: me.rerere.rikkahub.ui.pages.setting.SettingVM = org.koin.androidx.compose.koinViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val displaySetting = settings.displaySetting
    val navController = me.rerere.rikkahub.ui.context.LocalNavController.current

    val currentFrame = if (target == "user") displaySetting.userAvatarFrame else displaySetting.assistantAvatarFrame

    fun saveFrame(frame: AvatarFrame) {
        val newDisplay = if (target == "user") {
            displaySetting.copy(userAvatarFrame = frame)
        } else {
            displaySetting.copy(assistantAvatarFrame = frame)
        }
        vm.updateSettings(settings.copy(displaySetting = newDisplay))
    }

    AvatarFrameEditorContent(
        currentFrame = currentFrame,
        onSave = { saveFrame(it) },
        onBack = { navController.popBackStack() },
        title = if (target == "user") "用户头像框" else "AI 头像框",
        target = target,
    )
}

@Composable
private fun AvatarFrameEditorContent(
    currentFrame: AvatarFrame,
    onSave: (AvatarFrame) -> Unit,
    onBack: () -> Unit,
    title: String = "头像框编辑器",
    target: String = "user",
) {
    var stickerUri by remember { mutableStateOf(currentFrame.stickerUrl) }
    var offsetX by remember { mutableFloatStateOf(currentFrame.offsetX) }
    var offsetY by remember { mutableFloatStateOf(currentFrame.offsetY) }
    var scale by remember { mutableFloatStateOf(currentFrame.scale) }
    var rotation by remember { mutableFloatStateOf(currentFrame.rotation) }
    var enabled by remember { mutableStateOf(currentFrame.enabled) }

    val context = LocalContext.current

    var showSaveSuccess by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // 复制图片到内部存储以持久化
            val targetDir = java.io.File(context.filesDir, "avatar_frames").apply { mkdirs() }
            val targetFile = java.io.File(targetDir, "frame_${target}_${System.currentTimeMillis()}.png")
            try {
                context.contentResolver.openInputStream(it)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                stickerUri = targetFile.absolutePath
                enabled = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("头像框编辑器", fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TulpaPink,
                    titleContentColor = TulpaPinkDark
                )
            )
        },
        containerColor = TulpaPink,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TulpaPinkButton),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TulpaPinkAccent)
                ) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        onSave(
                            AvatarFrame(
                                stickerUrl = stickerUri,
                                offsetX = offsetX,
                                offsetY = offsetY,
                                scale = scale,
                                rotation = rotation,
                                enabled = enabled
                            )
                        )
                        showSaveSuccess = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TulpaPinkButton,
                        contentColor = TulpaWhite
                    )
                ) {
                    Text("保存")
                }
            }
        }
    ) { padding ->
        if (showSaveSuccess) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showSaveSuccess = false; onBack() },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showSaveSuccess = false; onBack() }) {
                        Text("好")
                    }
                },
                title = { Text("保存成功") },
                text = { Text("头像框已更新 ✓") },
            )
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 预览区域
            Card(
                modifier = Modifier.size(240.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TulpaWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                // 将像素偏移转换为 -1~1 的比例
                                offsetX = (offsetX + pan.x / size.width * 2).coerceIn(-1f, 1f)
                                offsetY = (offsetY + pan.y / size.height * 2).coerceIn(-1f, 1f)
                                scale = (scale * zoom).coerceIn(0.3f, 3.0f)
                                // rotation is intentionally disabled to prevent accidental rotation
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // 底层：头像预览（使用占位圆形）
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE0E0E0)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("预览", color = Color(0xFF757575), fontSize = 14.sp)
                    }

                    // 上层：贴纸叠加
                    if (stickerUri.isNotBlank()) {
                        AsyncImage(
                            model = stickerUri,
                            contentDescription = "Frame Sticker",
                            modifier = Modifier
                                .size((120.dp * scale))
                                .offset(
                                    x = (60.dp * offsetX),
                                    y = (60.dp * offsetY)
                                ),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            // 提示文字
            Text(
                text = if (stickerUri.isBlank()) "选择一个贴纸素材开始编辑" else "拖拽/捏合来调整位置和大小",
                fontSize = 13.sp,
                color = TulpaPinkDark.copy(alpha = 0.6f)
            )

            // 操作按钮
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TulpaWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 选择贴纸按钮
                    Button(
                        onClick = { imagePickerLauncher.launch("image/png") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TulpaPinkButton,
                            contentColor = TulpaWhite
                        )
                    ) {
                        Text("🖼️ 选择贴纸素材")
                    }

                    // 重置位置
                    OutlinedButton(
                        onClick = {
                            offsetX = 0f
                            offsetY = 0f
                            scale = 1.0f
                            rotation = 0f
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TulpaPinkButton),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TulpaPinkAccent)
                    ) {
                        Text("↺ 重置位置")
                    }

                    // 启用/禁用开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("启用头像框", color = TulpaPinkDark, fontSize = 14.sp)
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TulpaWhite,
                                checkedTrackColor = TulpaPinkButton,
                                uncheckedThumbColor = TulpaPinkAccent,
                                uncheckedTrackColor = TulpaPink
                            )
                        )
                    }

                    // 清除贴纸
                    if (stickerUri.isNotBlank()) {
                        TextButton(
                            onClick = {
                                stickerUri = ""
                                enabled = false
                                offsetX = 0f
                                offsetY = 0f
                                scale = 1.0f
                                rotation = 0f
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🗑️ 清除贴纸", color = TulpaPinkDark.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}
