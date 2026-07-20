import re, os

MSG_FILE = "app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessage.kt"
LIST_FILE = "app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatList.kt"

# === Config ===
USER_WING_X = "6.dp"
USER_WING_Y = "(-6).dp"
AI_WING_X = "(-6).dp"
AI_WING_Y = "(-6).dp"

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

# === 2. ChatMessage.kt ===
with open(MSG_FILE, "r") as f:
    src = f.read()

# 2a. Add imports
if "import androidx.compose.foundation.BorderStroke" not in src:
        import_block = (
        "import androidx.compose.foundation.BorderStroke\n"
        "import androidx.compose.foundation.Image\n"
        "import androidx.compose.foundation.layout.offset\n"
        "import androidx.compose.foundation.layout.wrapContentSize\n"
        "import androidx.compose.ui.layout.ContentScale\n"
        "import androidx.compose.ui.zIndex\n"
    )
    last_import = src.rfind("\nimport ")
    next_nl = src.find("\n", last_import + 1)
    src = src[:next_nl + 1] + import_block + src[next_nl + 1:]
    print("[2a] Imports added")

# 2b. User bubble - open Box before Surface, change Surface params
user_pattern = re.compile(
    r'([ \t]*)(Surface\(\s*'
    r'modifier\s*=\s*Modifier\.animateContentSize\(\),\s*'
    r'shape\s*=\s*RoundedCornerShape\(16\.dp\),\s*'
    r'color\s*=\s*MaterialTheme\.colorScheme\.primaryContainer\.copy\(alpha\s*=\s*settings\.displaySetting\.bubbleOpacity\),\s*'
    r'onClick\s*=\s*\{\s*onUserMessageClick\?\.invoke\(\)\s*\},\s*'
    r'\)\s*\{)',
    re.DOTALL
)

def user_repl(m):
    indent = m.group(1)
    return (
        f"{indent}Box {{\n"
        f"{indent}    Surface(\n"
        f"{indent}        onClick = {{ onUserMessageClick?.invoke() }},\n"
        f"{indent}        modifier = Modifier.animateContentSize(),\n"
        f"{indent}        shape = RoundedCornerShape(19.dp),\n"
        f"{indent}        color = Color(0xFFFCE5EB),\n"
        f"{indent}        contentColor = Color(0xFFA36779),\n"
        f"{indent}        border = BorderStroke(1.dp, Color(0xFFF1C5D4)),\n"
        f"{indent}    ) {{"
    )

src, n = user_pattern.subn(user_repl, src)
print(f"[2b] User bubble: {'OK' if n > 0 else 'NOT FOUND'} ({n} replacements)")

# 2c. AI bubble - open Box before Surface, change Surface params
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
        f"{indent}Box {{\n"
        f"{indent}    Surface(\n"
        f"{indent}        modifier = Modifier.animateContentSize(),\n"
        f"{indent}        shape = RoundedCornerShape(19.dp),\n"
        f"{indent}        color = Color(0xFFFFFFFF),\n"
        f"{indent}        contentColor = Color(0xFFA36779),\n"
        f"{indent}        border = BorderStroke(1.dp, Color(0xFFF1C5D4)),\n"
        f"{indent}    ) {{"
    )

src, n = ai_pattern.subn(ai_repl, src)
print(f"[2c] AI bubble: {'OK' if n > 0 else 'NOT FOUND'} ({n} replacements)")

# 2d. After each Surface block closes, insert wing Image, then close Box
def insert_wing_after_surface(src, color_marker, drawable, wing_x, wing_y, alignment):
    """Find Surface with color_marker, after its closing }, insert wing Image and Box close."""
    pos = src.find(color_marker)
    if pos == -1:
        print(f"  Wing ({color_marker}): marker not found")
        return src
    surface_start = src.rfind("Surface(", 0, pos)
    # find opening brace of Surface's trailing lambda
    paren_depth = 0
    i = surface_start + len("Surface(")
    # skip to the closing ) of Surface(...)
    while i < len(src):
        if src[i] == '(':
            paren_depth += 1
        elif src[i] == ')':
            if paren_depth == 0:
                break
            paren_depth -= 1
        i += 1
    # now find the { after )
    brace_start = src.find("{", i)
    # count braces to find Surface block end
    depth = 0
    i = brace_start
    while i < len(src):
        if src[i] == '{':
            depth += 1
        elif src[i] == '}':
            depth -= 1
            if depth == 0:
                # i is the position of Surface's closing }
                line_end = src.find('\n', i)
                if line_end == -1:
                    line_end = len(src)
                # determine indent
                line_start = src.rfind('\n', 0, surface_start) + 1
                surface_indent = ""
                for ch in src[line_start:]:
                    if ch in ' \t':
                        surface_indent += ch
                    else:
                        break
                # wing goes after Surface }, before Box }
                # Surface is indented 4 spaces inside Box, so wing also at same level
                wing_code = (
                    f"{surface_indent}Image(\n"
                    f"{surface_indent}    painter = painterResource(R.drawable.{drawable}),\n"
                    f"{surface_indent}    contentDescription = null,\n"
                    f"{surface_indent}    modifier = Modifier\n"
                    f"{surface_indent}        .matchParentSize()\n"
                    f"{surface_indent}        .wrapContentSize(align = {alignment})\n"
                    f"{surface_indent}        .size(20.dp)\n"
                    f"{surface_indent}        .offset(x = {wing_x}, y = {wing_y})\n"
                    f"{surface_indent}        .zIndex(10f),\n"
                    f"{surface_indent}    contentScale = ContentScale.Fit\n"
                    f"{surface_indent})\n"
                )
                # Box closing brace at one indent level less
                box_indent = surface_indent[4:] if len(surface_indent) >= 4 else ""
                insert = wing_code + box_indent + "}\n"
                src = src[:line_end + 1] + insert + src[line_end + 1:]
                print(f"  Wing ({color_marker}): inserted")
                return src
        i += 1
    print(f"  Wing ({color_marker}): could not find closing brace")
    return src

src = insert_wing_after_surface(src, "Color(0xFFFCE5EB)", "wing_right", USER_WING_X, USER_WING_Y, "Alignment.TopEnd")
src = insert_wing_after_surface(src, "Color(0xFFFFFFFF)", "wing_left", AI_WING_X, AI_WING_Y, "Alignment.TopStart")

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
