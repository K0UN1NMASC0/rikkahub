import re, os

# ============================================================
# Tulpa Patch - clean version, no sed, no shit mountain
# ============================================================

MSG_FILE = "app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessage.kt"
LIST_FILE = "app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatList.kt"

# === 1. App name → Tulpa ===
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

# === 2. ChatMessage.kt - bubble colors + imports + wings ===
with open(MSG_FILE, "r") as f:
    src = f.read()

# 2a. Add imports (only if not already present)
if "import androidx.compose.foundation.BorderStroke" not in src:
    import_block = (
        "import androidx.compose.foundation.BorderStroke\n"
        "import androidx.compose.foundation.Image\n"
        "import androidx.compose.foundation.layout.offset\n"
        "import androidx.compose.ui.layout.ContentScale\n"
        "import androidx.compose.ui.zIndex\n"
    )
    # Insert after the last import line
    last_import = src.rfind("\nimport ")
    next_nl = src.find("\n", last_import + 1)
    src = src[:next_nl + 1] + import_block + src[next_nl + 1:]
    print("[2a] Imports added")

# 2b. User bubble - replace entire Surface call
# Original pattern (onClick version):
#   Surface(
#       modifier = Modifier.animateContentSize(),
#       shape = RoundedCornerShape(16.dp),
#       color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = settings.displaySetting.bubbleOpacity),
#       onClick = { onUserMessageClick?.invoke() },
#   )
user_pattern = re.compile(
    r'(Surface\(\s*)'
    r'modifier\s*=\s*Modifier\.animateContentSize\(\),\s*'
    r'shape\s*=\s*RoundedCornerShape\(16\.dp\),\s*'
    r'color\s*=\s*MaterialTheme\.colorScheme\.primaryContainer\.copy\(alpha\s*=\s*settings\.displaySetting\.bubbleOpacity\),\s*'
    r'onClick\s*=\s*\{\s*onUserMessageClick\?\.invoke\(\)\s*\},\s*'
    r'\)',
    re.DOTALL
)

user_replacement = (
    "Surface(\n"
    "                                onClick = { onUserMessageClick?.invoke() },\n"
    "                                modifier = Modifier.animateContentSize(),\n"
    "                                shape = RoundedCornerShape(19.dp),\n"
    "                                color = Color(0xFFFCE5EB),\n"
    "                                contentColor = Color(0xFFA36779),\n"
    "                                border = BorderStroke(1.dp, Color(0xFFF1C5D4)),\n"
    "                            )"
)

src, n = user_pattern.subn(user_replacement, src)
print(f"[2b] User bubble: {'OK' if n > 0 else 'NOT FOUND'} ({n} replacements)")

# 2c. AI bubble - replace Surface call
# Original pattern (no onClick):
#   Surface(
#       modifier = Modifier.animateContentSize(),
#       shape = RoundedCornerShape(16.dp),
#       color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = settings.displaySetting.bubbleOpacity),
#   )
ai_pattern = re.compile(
    r'Surface\(\s*'
    r'modifier\s*=\s*Modifier\.animateContentSize\(\),\s*'
    r'shape\s*=\s*RoundedCornerShape\(16\.dp\),\s*'
    r'color\s*=\s*MaterialTheme\.colorScheme\.surfaceContainerHigh\.copy\(alpha\s*=\s*settings\.displaySetting\.bubbleOpacity\),\s*'
    r'\)',
    re.DOTALL
)

ai_replacement = (
    "Surface(\n"
    "                                        modifier = Modifier.animateContentSize(),\n"
    "                                        shape = RoundedCornerShape(19.dp),\n"
    "                                        color = Color(0xFFFFFFFF),\n"
    "                                        contentColor = Color(0xFFA36779),\n"
    "                                        border = BorderStroke(1.dp, Color(0xFFF1C5D4)),\n"
    "                                    )"
)

src, n = ai_pattern.subn(ai_replacement, src)
print(f"[2c] AI bubble: {'OK' if n > 0 else 'NOT FOUND'} ({n} replacements)")

# Save intermediate result
with open(MSG_FILE, "w") as f:
    f.write(src)

# === 2d. Wing injection (line-based) ===
with open(MSG_FILE, "r") as f:
    lines = f.readlines()

def find_line(keyword, start=0):
    for i in range(start, len(lines)):
        if keyword in lines[i]:
            return i
    return None

def get_indent(line):
    return line[:len(line) - len(line.lstrip())]

def inject_wing(color_keyword, drawable, align, offset_x):
    color_line = find_line(color_keyword)
    if color_line is None:
        print(f"  Wing ({drawable}): color line not found")
        return
    # Find Surface( before color line
    surface_line = None
    for i in range(color_line, max(color_line - 10, 0), -1):
        if "Surface(" in lines[i]:
            surface_line = i
            break
    if surface_line is None:
        print(f"  Wing ({drawable}): Surface not found")
        return
    indent = get_indent(lines[surface_line])
    wing = (
        f"{indent}Box {{\n"
        f"{indent}    Image(\n"
        f"{indent}        painter = painterResource(R.drawable.{drawable}),\n"
        f"{indent}        contentDescription = null,\n"
        f"{indent}        modifier = Modifier\n"
        f"{indent}            .size(20.dp)\n"
        f"{indent}            .align({align})\n"
        f"{indent}            .offset(x = {offset_x}, y = (-6).dp)\n"
        f"{indent}            .zIndex(10f),\n"
        f"{indent}        contentScale = ContentScale.Fit\n"
        f"{indent}    )\n"
    )
    lines.insert(surface_line, wing)
    print(f"  Wing ({drawable}): injected at line {surface_line}")
    # Find Surface closing brace to close Box
    new_color = find_line(color_keyword)
    new_surface = None
    for i in range(new_color, max(new_color - 10, 0), -1):
        if "Surface(" in lines[i]:
            new_surface = i
            break
    if new_surface:
        brace = 0
        started = False
        for j in range(new_surface, min(new_surface + 40, len(lines))):
            for ch in lines[j]:
                if ch == '{':
                    brace += 1
                    started = True
                elif ch == '}':
                    brace -= 1
            if started and brace == 0:
                lines.insert(j + 1, f"{indent}}}\n")
                print(f"  Wing ({drawable}): Box closed at line {j + 1}")
                return

# User wing
print("[2d] Injecting wings...")
inject_wing("Color(0xFFFCE5EB)", "wing_right", "Alignment.TopEnd", "6.dp")
# AI wing (re-read to get updated positions)
inject_wing("Color(0xFFFFFFFF)", "wing_left", "Alignment.TopStart", "(-6).dp")

with open(MSG_FILE, "w") as f:
    f.writelines(lines)
print("[2d] Wings done")

# === 3. ChatList.kt - reduce spacing ===
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
