import re, os

MSG_FILE = "app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessage.kt"
LIST_FILE = "app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatList.kt"

# === Config ===
USER_WING_X = "6.dp"
USER_WING_Y = "(-6).dp"
AI_WING_X = "(-6).dp"
AI_WING_Y = "(-6).dp"

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

# === 2. ChatMessage.kt ===
with open(MSG_FILE, "r") as f:
    src = f.read()

# 2a. Add imports
if "import androidx.compose.foundation.BorderStroke" not in src:
    import_block = (
        "import androidx.compose.foundation.BorderStroke\n"
        "import androidx.compose.foundation.layout.width\n"
        "import androidx.compose.foundation.layout.IntrinsicSize\n"
    )
    last_import = src.rfind("\nimport ")
    next_nl = src.find("\n", last_import + 1)
    src = src[:next_nl + 1] + import_block + src[next_nl + 1:]
    print("[2a] Imports added")

# 2b. User bubble
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
        f"{indent}    onClick = {{ onUserMessageClick?.invoke() }},\n"
        f"{indent}    modifier = Modifier.animateContentSize(),\n"
        f"{indent}    shape = RoundedCornerShape(19.dp),\n"
        f"{indent}    color = Color(0xFFFCE5EB),\n"
        f"{indent}    contentColor = Color(0xFFA36779),\n"
        f"{indent}    border = BorderStroke(1.dp, Color(0xFFF1C5D4)),\n"
        f"{indent}) {{"
    )

src, n = user_pattern.subn(user_repl, src)
print(f"[2b] User bubble: {'OK' if n > 0 else 'NOT FOUND'} ({n} replacements)")

# 2c. AI bubble
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
print(f"[2c] AI bubble: {'OK' if n > 0 else 'NOT FOUND'} ({n} replacements)")

# 2d. Remove wings - already removed, just keeping the replacement logic
print("[2d] Wings: skipped (removed)")

with open(MSG_FILE, "w") as f:
    f.write(src)
print("[2] All bubble patches saved")

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

print("\n=== All patches complete ===")
