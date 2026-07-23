package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingPreferencesBeautifyPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    val navController = LocalNavController.current

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings(settings.copy(displaySetting = setting))
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("美化") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("头像框") },
                ) {
                    item(
                        headlineContent = { Text("用户头像框") },
                        supportingContent = {
                            Text(
                                if (displaySetting.userAvatarFrame.enabled) "已启用 · 点击编辑"
                                else "未设置 · 点击编辑"
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.userAvatarFrame.enabled,
                                onCheckedChange = {
                                    updateDisplaySetting(
                                        displaySetting.copy(
                                            userAvatarFrame = displaySetting.userAvatarFrame.copy(enabled = it)
                                        )
                                    )
                                }
                            )
                        },
                        onClick = { navController.navigate(Screen.AvatarFrameEditor(target = "user")) },
                    )
                    item(
                        headlineContent = { Text("AI 头像框") },
                        supportingContent = {
                            Text(
                                if (displaySetting.assistantAvatarFrame.enabled) "已启用 · 点击编辑"
                                else "未设置 · 点击编辑"
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.assistantAvatarFrame.enabled,
                                onCheckedChange = {
                                    updateDisplaySetting(
                                        displaySetting.copy(
                                            assistantAvatarFrame = displaySetting.assistantAvatarFrame.copy(enabled = it)
                                        )
                                    )
                                }
                            )
                        },
                        onClick = { navController.navigate(Screen.AvatarFrameEditor(target = "assistant")) },
                    )
                }
            }

            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("气泡配色") },
                ) {
                    item(
                        headlineContent = { Text("启用自定义气泡配色") },
                        supportingContent = { Text("关闭则使用主题默认颜色") },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.bubbleColorConfig.enabled,
                                onCheckedChange = {
                                    updateDisplaySetting(
                                        displaySetting.copy(
                                            bubbleColorConfig = displaySetting.bubbleColorConfig.copy(enabled = it)
                                        )
                                    )
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text("配色预设") },
                        supportingContent = {
                            Select(
                                options = me.rerere.rikkahub.data.model.BubbleColorConfig.PRESETS,
                                selectedOption = me.rerere.rikkahub.data.model.BubbleColorConfig.PRESETS.find {
                                    it.id == displaySetting.bubbleColorConfig.presetId
                                } ?: me.rerere.rikkahub.data.model.BubbleColorConfig.PRESETS.first(),
                                onOptionSelected = { preset ->
                                    updateDisplaySetting(
                                        displaySetting.copy(
                                            bubbleColorConfig = me.rerere.rikkahub.data.model.BubbleColorConfig.fromPreset(preset)
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .fillMaxWidth(),
                                optionToString = { it.name },
                            )
                        },
                    )
                }
            }
        }
    }
}
