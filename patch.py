import re, os

# === App name → Tulpa ===
res_dir = "app/src/main/res"
count = 0
for root, dirs, files in os.walk(res_dir):
    for fname in files:
        if fname == "strings.xml":
            fpath = os.path.join(root, fname)
            with open(fpath, "r", encoding="utf-8") as f:
                content = f.read()
            new_content = re.sub(
                r'<string name="app_name">[^<]+</string>',
                '<string name="app_name">Tulpa</string>',
                content
            )
            if new_content != content:
                with open(fpath, "w", encoding="utf-8") as f:
                    f.write(new_content)
                print(f"App name patched: {fpath}")
                count += 1
print(f"App name patched in {count} files")

# === Wing patch ===
msg_file = "app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessage.kt"

with open(msg_file, "r") as f:
    lines = f.readlines()

def find_line(lines, keyword, start=0, end=None):
    end = end or len(lines)
    for i in range(start, end):
        if keyword in lines[i]:
            return i
    return None

def get_indent(line):
    return line[:len(line) - len(line.lstrip())]

def make_wing(indent, side):
    drawable = "wing_right" if side == "right" else "wing_left"
    align = "Alignment.TopEnd" if side == "right" else "Alignment.TopStart"
    offset_x = "8.dp" if side == "right" else "(-8).dp"
    return (
        indent + "    Box {\n"
        + indent + "        Image(\n"
        + indent + "            painter = painterResource(R.drawable." + drawable + "),\n"
        + indent + "            contentDescription = null,\n"
        + indent + "            modifier = Modifier\n"
        + indent + "                .size(16.dp)\n"
        + indent + "                .align(" + align + ")\n"
        + indent + "                .offset(x = " + offset_x + ", y = (-5).dp)\n"
        + indent + "                .zIndex(10f),\n"
        + indent + "            contentScale = ContentScale.Fit\n"
        + indent + "        )\n")

# --- User wing ---
user_color = find_line(lines, "Color(0xFFFCE5EB)")
print(f"User color line: {user_color}")
if user_color:
    user_role = None
    for i in range(user_color, max(user_color - 25, 0), -1):
        if "MessageRole.USER" in lines[i] and "role ==" in lines[i]:
            user_role = i
            break
    print(f"User role line: {user_role}")
    if user_role:
        indent = get_indent(lines[user_role])
        wing = make_wing(indent, "right")
        lines.insert(user_role + 1, wing)
        print(f"User wing injected")
        for j in range(user_color + 10, min(user_color + 50, len(lines))):
            if lines[j].strip() == "} else {":
                lines.insert(j, indent + "    }\n")
                print(f"User Box closed at line {j}")
                break

# --- AI wing ---
ai_color = find_line(lines, "Color(0xFFFFFFFF)")
print(f"AI color line: {ai_color}")
if ai_color:
    ai_bubble = None
    for i in range(ai_color, max(ai_color - 25, 0), -1):
        if "showAssistantBubble" in lines[i]:
            ai_bubble = i
            break
    print(f"AI bubble line: {ai_bubble}")
    if ai_bubble:
        indent = get_indent(lines[ai_bubble])
        wing = make_wing(indent, "left")
        lines.insert(ai_bubble + 1, wing)
        print(f"AI wing injected")
        for j in range(ai_color + 10, min(ai_color + 50, len(lines))):
            if lines[j].strip() == "} else {" and "showAssistantBubble" not in lines[j - 1]:
                lines.insert(j, indent + "    }\n")
                print(f"AI Box closed at line {j}")
                break

with open(msg_file, "w") as f:
    f.writelines(lines)

print("Wing patch complete")
