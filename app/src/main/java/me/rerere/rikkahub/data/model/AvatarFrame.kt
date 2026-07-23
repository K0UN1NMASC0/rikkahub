package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable

/**
 * 头像框贴纸数据模型
 * 支持在头像外叠加一个装饰性 PNG 贴纸
 */
@Serializable
data class AvatarFrame(
    /** 贴纸图片 URI（本地文件或网络 URL） */
    val stickerUrl: String = "",
    /** 水平偏移比例 (-1.0 ~ 1.0)，相对于头像中心 */
    val offsetX: Float = 0f,
    /** 垂直偏移比例 (-1.0 ~ 1.0)，相对于头像中心 */
    val offsetY: Float = 0f,
    /** 缩放比例 (0.5 ~ 3.0) */
    val scale: Float = 1.0f,
    /** 旋转角度 (0 ~ 360) */
    val rotation: Float = 0f,
    /** 是否启用 */
    val enabled: Boolean = false,
) {
    companion object {
        val EMPTY = AvatarFrame()
    }
}
