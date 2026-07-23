package me.rerere.rikkahub.data.model

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/**
 * 气泡配色预设
 * 独立于主题系统，允许单独切换气泡颜色
 */
@Serializable
data class BubbleColorConfig(
    /** 预设ID，"custom"表示自定义 */
    val presetId: String = "tulpa_pink",
    /** 自定义时的用户气泡背景色 (ARGB hex) */
    val userBubbleColor: Long = 0xFFFCE5EB,
    /** 自定义时的AI气泡背景色 (ARGB hex) */
    val aiBubbleColor: Long = 0xFFFFFFFF,
    /** 自定义时的用户气泡文字色 (ARGB hex) */
    val userTextColor: Long = 0xFFA36779,
    /** 自定义时的AI气泡文字色 (ARGB hex) */
    val aiTextColor: Long = 0xFF1A1112,
    /** 气泡边框色 (ARGB hex)，0表示无边框 */
    val borderColor: Long = 0xFFF1C5D4,
    /** 气泡圆角大小 (dp)，4=偏方形，16=默认圆角 */
    val cornerRadius: Int = 16,
    /** 是否启用自定义气泡（false则使用主题默认） */
    val enabled: Boolean = true,
) {
    companion object {
        val DEFAULT = BubbleColorConfig()

        /** 内置预设列表 */
        val PRESETS = listOf(
            BubblePreset(
                id = "tulpa_pink",
                name = "Tulpa Pink",
                userBubble = 0xFFFCE5EB,
                aiBubble = 0xFFFFFFFF,
                userText = 0xFFA36779,
                aiText = 0xFF1A1112,
                border = 0xFFF1C5D4,
            ),
            BubblePreset(
                id = "tulpa_kounloka",
                name = "Koun × 洛卡",
                userBubble = 0xFFF2C4CE,
                aiBubble = 0xFFFAF0C8,
                userText = 0xFF5C2D3A,
                aiText = 0xFF3D3520,
                border = 0x00000000,
                cornerRadius = 6,
            ),
            BubblePreset(
                id = "dark_minimal",
                name = "Dark Minimal",
                userBubble = 0xFF2D2D2D,
                aiBubble = 0xFF1A1A1A,
                userText = 0xFFE0E0E0,
                aiText = 0xFFCCCCCC,
                border = 0x00000000,
            ),
            BubblePreset(
                id = "ocean_blue",
                name = "Ocean Blue",
                userBubble = 0xFFDCEEFB,
                aiBubble = 0xFFF5F9FC,
                userText = 0xFF2C5F7C,
                aiText = 0xFF1A3A4A,
                border = 0xFFB3D4E8,
            ),
            BubblePreset(
                id = "lavender",
                name = "Lavender",
                userBubble = 0xFFE8DEFF,
                aiBubble = 0xFFF8F5FF,
                userText = 0xFF5B4A8A,
                aiText = 0xFF2D2341,
                border = 0xFFD4C4F0,
            ),
            BubblePreset(
                id = "mint",
                name = "Mint",
                userBubble = 0xFFD4F5E9,
                aiBubble = 0xFFF2FDF8,
                userText = 0xFF2A6B52,
                aiText = 0xFF1A3D31,
                border = 0xFFB0E5D0,
            ),
        )

        fun fromPreset(preset: BubblePreset): BubbleColorConfig {
            return BubbleColorConfig(
                presetId = preset.id,
                userBubbleColor = preset.userBubble,
                aiBubbleColor = preset.aiBubble,
                userTextColor = preset.userText,
                aiTextColor = preset.aiText,
                borderColor = preset.border,
                cornerRadius = preset.cornerRadius,
                enabled = true,
            )
        }
    }
}

data class BubblePreset(
    val id: String,
    val name: String,
    val userBubble: Long,
    val aiBubble: Long,
    val userText: Long,
    val aiText: Long,
    val border: Long,
    val cornerRadius: Int = 16,
)
