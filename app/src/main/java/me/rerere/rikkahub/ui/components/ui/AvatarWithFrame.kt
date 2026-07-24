package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.AvatarFrame

/**
 * 带头像框的头像组件
 * 在原有 UIAvatar 外叠加一个可配置位置/大小的 PNG 贴纸
 */
@Composable
fun AvatarWithFrame(
    name: String,
    value: Avatar,
    frame: AvatarFrame,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 28.dp,
    loading: Boolean = false,
    onUpdate: ((Avatar) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    // 贴纸需要更大的容器来容纳溢出部分
    // Scale container dynamically based on frame scale to prevent clipping
    val effectiveScale = if (frame.enabled) frame.scale.coerceAtLeast(1.0f) else 1.0f
    val containerSize = avatarSize * (1.0f + effectiveScale * 0.8f)

    Box(
        modifier = modifier.size(containerSize),
        contentAlignment = Alignment.Center
    ) {
        // 底层：原始头像
        UIAvatar(
            name = name,
            value = value,
            modifier = Modifier.size(avatarSize),
            loading = loading,
            onUpdate = onUpdate,
            onClick = onClick
        )

        // 上层：贴纸叠加
        if (frame.enabled && frame.stickerUrl.isNotBlank()) {
            val stickerSize = avatarSize * frame.scale
            val offsetXDp = (avatarSize / 2) * frame.offsetX
            val offsetYDp = (avatarSize / 2) * frame.offsetY

            AsyncImage(
                model = frame.stickerUrl,
                contentDescription = "Avatar Frame",
                modifier = Modifier
                    .size(stickerSize)
                    .offset(x = offsetXDp, y = offsetYDp)
                    .rotate(frame.rotation),
                contentScale = ContentScale.Fit
            )
        }
    }
}
