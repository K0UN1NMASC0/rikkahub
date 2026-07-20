import re, os

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

# 2b. User bubble - wrap in Box with wing, onClick first
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
        f"{indent}Box(\n"
        f"{indent}    modifier = Modifier.wrapContentSize()\n"
        f"{indent}) {{\n"
        f"{indent}    Image(\n"
        f"{indent}        painter = painterResource(R.drawable.wing_right),\n"
        f"{indent}        contentDescription = null,\n"
        f"{indent}        modifier = Modifier\n"
        f"{indent}            .size(20.dp)\n"
        f"{indent}            .align(Alignment.TopEnd)\n"
        f"{indent}            .offset(x = (-6).dp, y = (-4).dp)\n"
        f"{indent}            .zIndex(10f),\n"
        f"{indent}        contentScale = ContentScale.Fit\n"
        f"{indent}    )\n"
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

# 2c. AI bubble - wrap in Box with wing
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
        f"{indent}Box(\n"
        f"{indent}    modifier = Modifier.wrapContentSize()\n"
        f"{indent}) {{\n"
        f"{indent}    Image(\n"
        f"{indent}        painter = painterResource(R.drawable.wing_left),\n"
        f"{indent}        contentDescription = null,\n"
        f"{indent}        modifier = Modifier\n"
        f"{indent}            .size(20.dp)\n"
        f"{indent}            .align(Alignment.TopStart)\n"
        f"{indent}            .offset(x = (6).dp, y = (-4).dp)\n"
        f"{indent}            .zIndex(10f),\n"
        f"{indent}        contentScale = ContentScale.Fit\n"
        f"{indent}    )\n"
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

# 2d. Close Box after each Surface block
# Strategy: find the closing } of Surface's trailing lambda content,
# then add a } to close Box after it.
# The Surface block structure is: Surface(...) { Column(...) { ... } }
# So we need to find TWO closing braces after "Color(0xFFFCE5EB)" (Column close + Surface close)
# then add Box close.

def add_box_close_after_surface(src, color_marker):
    """Find the Surface block containing color_marker, then add } after it closes."""
    pos = src.find(color_marker)
    if pos == -1:
        print(f"  Box close ({color_marker}): marker not found")
        return src
    # Find the ") {" that opens the Surface trailing lambda (search backward from marker)
    # Actually, find forward from marker to the Column { ... } } pattern
    # The Surface content is: { Column(...) { MarkdownBlock(...) } }
    # We need to count from the "Surface(...) {" opening brace
    # Find "Surface(" before the marker
    surface_start = src.rfind("Surface(", 0, pos)
    # Find the "{ " that opens the trailing lambda after Surface's )
    brace_start = src.find("{", src.find(")", surface_start))
    # Now count braces from brace_start to find where Surface block ends
    depth = 0
    i = brace_start
    while i < len(src):
        if src[i] == '{':
            depth += 1
        elif src[i] == '}':
            depth -= 1
            if depth == 0:
                # This is where Surface block closes
                # Find the end of this line
                line_end = src.find('\n', i)
                if line_end == -1:
                    line_end = len(src)
                # Get indent of the Box (which is the Surface indent minus 4 spaces)
                # Find the line containing Surface(
                line_start = src.rfind('\n', 0, surface_start) + 1
                surface_indent = ""
                for ch in src[line_start:]:
                    if ch in ' \t':
                        surface_indent += ch
                    else:
                        break
                # Box indent is same level as Surface was (Surface is now indented inside Box)
                # Actually Box indent = Surface indent - 4 spaces
                box_indent = surface_indent[4:] if len(surface_indent) >= 4 else ""
                # Insert Box closing brace
                src = src[:line_end + 1] + box_indent + "}\n" + src[line_end + 1:]
                print(f"  Box close ({color_marker}): inserted after position {line_end}")
                return src
        i += 1
    print(f"  Box close ({color_marker}): could not find closing brace")
    return src

src = add_box_close_after_surface(src, "Color(0xFFFCE5EB)")
src = add_box_close_after_surface(src, "Color(0xFFFFFFFF)")

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
