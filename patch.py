import re, os

MSG_FILE = "app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessage.kt"
LIST_FILE = "app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatList.kt"
MANIFEST_FILE = "app/src/main/AndroidManifest.xml"
TIME_TRANSFORMER_FILE = "app/src/main/java/me/rerere/rikkahub/data/ai/transformers/TimeReminderTransformer.kt"

# === 1. App name -> Tulpa ===
res_dir = "app/src/main/res"
name_count = 0
for root, dirs, files in os.walk(res_dir):
    for fname in files:
        if fname == "strings.xml":
            fpath = os.path.join(root, fname)
            with open(fpath, "r", encoding="utf-8") as f:
                c = f.read()
            new_c = re.sub(r'<string name="app_name">[^<]+</string>',
                           '<string name="app_name">Tulpa</string>', c)
            if new_c != c:
                with open(fpath, "w", encoding="utf-8") as f:
                    f.write(new_c)
                name_count += 1
print(f"[1] App name: patched {name_count} files")

# === 2. ChatMessage.kt 气泡样式 ===
with open(MSG_FILE, "r") as f:
    src = f.read()

if "import androidx.compose.foundation.BorderStroke" not in src:
    import_block = "import androidx.compose.foundation.BorderStroke\n"
    last_import = src.rfind("\nimport ")
    next_nl = src.find("\n", last_import + 1)
    src = src[:next_nl + 1] + import_block + src[next_nl + 1:]
    print("[2a] Imports added")

user_pattern = re.compile(
    r'([ \t]*)(Surface\(\s*'
    r'modifier\s*=\s*Modifier\.animateContentSize\(\),\s*'
    r'shape\s*=\s*RoundedCornerShape\(16\.dp\),\s*'
    r'color\s*=\s*MaterialTheme\.colorScheme\.primaryContainer\.copy\(alpha\s*=\s*settings\.displaySetting\.bubbleOpacity\),\s*'
    r'onClick\s*=\s*\{\s*onUserMessageClick\?\.invoke\(\)\s*\},?\s*'
    r'\)\s*\{)',
    re.DOTALL
)

def user_repl(m):
    indent = m.group(1)
    return (
        f"{indent}Surface(\n"
        f"{indent}onClick = {{ onUserMessageClick?.invoke() }},\n"
        f"{indent}    modifier = Modifier.animateContentSize(),\n"
        f"{indent}    shape = RoundedCornerShape(19.dp),\n"
        f"{indent}    color = Color(0xFFFCE5EB),\n"
        f"{indent}    contentColor = Color(0xFFA36779),\n"
        f"{indent}    border = BorderStroke(1.dp, Color(0xFFF1C5D4)),\n"
        f"{indent}) {{"
    )

src, n = user_pattern.subn(user_repl, src)
print(f"[2b] User bubble: {'OK' if n > 0 else 'NOT FOUND'} ({n})")

ai_pattern = re.compile(
    r'([ \t]*)(Surface\(\s*'
    r'modifier\s*=\s*Modifier\.animateContentSize\(\),\s*'
    r'shape\s*=\s*RoundedCornerShape\(16\.dp\),\s*'
    r'color\s*=\s*MaterialTheme\.colorScheme\.surfaceContainerHigh\.copy\(alpha\s*=\s*settings\.displaySetting\.bubbleOpacity\),\s*'
    r'\)\s*\{)',
    re.DOTALL
)

def ai_repl(m):
    indent = m.group(1)
    return (
        f"{indent}Surface(\n"
        f"{indent}    modifier = Modifier.animateContentSize(),\n"
        f"{indent}    shape = RoundedCornerShape(19.dp),\n"
        f"{indent}    color = Color(0xFFFFFFFF),\n"
        f"{indent}    contentColor = Color(0xFFA36779),\n"
        f"{indent}    border = BorderStroke(1.dp, Color(0xFFF1C5D4)),\n"
        f"{indent}) {{"
    )

src, n = ai_pattern.subn(ai_repl, src)
print(f"[2c] AI bubble: {'OK' if n > 0 else 'NOT FOUND'} ({n})")

with open(MSG_FILE, "w") as f:
    f.write(src)
print("[2] Bubble patches saved")

# === 3. ChatList spacing ===
with open(LIST_FILE, "r") as f:
    list_src = f.read()
list_src = list_src.replace(
    "verticalArrangement = Arrangement.spacedBy(12.dp),",
    "verticalArrangement = Arrangement.spacedBy(4.dp),"
)
with open(LIST_FILE, "w") as f:
    f.write(list_src)
print("[3] Spacing: OK")

# === 4. 时间注入：确保 TimeReminderTransformer 被启用 ===
if os.path.exists(TIME_TRANSFORMER_FILE):
    with open(TIME_TRANSFORMER_FILE, "r") as f:
        t_src = f.read()
    # 检查是否有 enabled = false之类的关闭开关，如果有就打开
    if "enabled = false" in t_src:
        t_src = t_src.replace("enabled = false", "enabled = true")
        with open(TIME_TRANSFORMER_FILE, "w") as f:
            f.write(t_src)
        print("[4] TimeReminderTransformer: enabled")
    else:
        print("[4] TimeReminderTransformer: already enabled or no switch found")
else:
    print("[4] TimeReminderTransformer: file not found, skipping")

# === 5. AndroidManifest.xml 注册主动消息 Service/Receiver ===
with open(MANIFEST_FILE, "r") as f:
    manifest = f.read()

receiver_tag = '''<receiver
            android:name=".data.service.ProactiveMessageReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="me.rerere.rikkahub.PROACTIVE_MESSAGE" />
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>'''

service_tag = '''        <service
            android:name=".data.service.ProactiveMessageTriggerService"
            android:exported="false"
            android:foregroundServiceType="shortService" />'''

changed = False
if "ProactiveMessageReceiver" not in manifest:
    manifest = manifest.replace("</application>", receiver_tag + "\n    </application>")
    changed = True
if "ProactiveMessageTriggerService" not in manifest:
    manifest = manifest.replace("</application>", service_tag + "\n    </application>")
    changed = True

if changed:
    with open(MANIFEST_FILE, "w") as f:
        f.write(manifest)
    print("[5] Manifest: registered Service and Receiver")
else:
    print("[5] Manifest: already registered")

# ===6. 自动启动主动消息 ===
APP_FILE = "app/src/main/java/me/rerere/rikkahub/RikkaHubApp.kt"
with open(APP_FILE, "r") as f:
    app_src = f.read()

schedule_call = "me.rerere.rikkahub.data.service.ProactiveMessageReceiver.schedule(this, 180, 180)\n"
trigger = "this.createNotificationChannel()"

if "ProactiveMessageReceiver.schedule" not in app_src and trigger in app_src:
    app_src = app_src.replace(
        trigger,
        trigger + "\n" + schedule_call
    )
    with open(APP_FILE, "w") as f:
        f.write(app_src)
    print("[6] Auto-start: injected")
else:
    print("[6] Auto-start: already injected or trigger not found")

print("\n=== All patches complete ===")
