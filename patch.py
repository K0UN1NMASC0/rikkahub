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

# --- User wing ---
user_color = find_line(lines, "Color(0xFFFCE5EB)")
print(f"User color line: {user_color}")

if user_color:
    user_surface = None
    for i in range(user_color, max(user_color - 10, 0), -1):
        if "Surface(" in lines[i]:
            user_surface = i
            break
    print(f"User Surface line: {user_surface}")
    if user_surface:
        indent = get_indent(lines[user_surface])
        box_and_wing = (
            indent + "Box {\n"
            + indent + "    Image(\n"
            + indent + "        painter = painterResource(R.drawable.wing_right),\n"
            + indent + "        contentDescription = null,\n"
            + indent + "        modifier = Modifier\n"
            + indent + "            .size(20.dp)\n"
            + indent + "            .align(Alignment.TopEnd)\n"
            + indent + "            .offset(x = 6.dp, y = (-6).dp)\n"
            + indent + "            .zIndex(10f),\n"
            + indent + "        contentScale = ContentScale.Fit\n"
            + indent + "    )\n"
        )
        lines.insert(user_surface, box_and_wing)
        print("User wing injected")

        # Find Surface closing brace
        new_user_color = find_line(lines, "Color(0xFFFCE5EB)")
        new_surface = None
        for i in range(new_user_color, max(new_user_color - 10, 0), -1):
            if "Surface(" in lines[i]:
                new_surface = i
                break
        if new_surface:
            brace_count = 0
            found_open = False
            for j in range(new_surface, min(new_surface + 40, len(lines))):
                for ch in lines[j]:
                    if ch == '{':
                        brace_count += 1
                        found_open = True
                    elif ch == '}':
                        brace_count -= 1
                if found_open and brace_count == 0:
                    lines.insert(j + 1, indent + "}\n")
                    print(f"User Box closed at line {j + 1}")
                    break

# --- AI wing ---
ai_color = find_line(lines, "Color(0xFFFFFFFF)")
print(f"AI color line: {ai_color}")

if ai_color:
    ai_surface = None
    for i in range(ai_color, max(ai_color - 10, 0), -1):
        if "Surface(" in lines[i]:
            ai_surface = i
            break
    print(f"AI Surface line: {ai_surface}")
    if ai_surface:
        indent = get_indent(lines[ai_surface])
        box_and_wing = (
            indent + "Box {\n"
            + indent + "    Image(\n"
            + indent + "        painter = painterResource(R.drawable.wing_left),\n"
            + indent + "        contentDescription = null,\n"
            + indent + "        modifier = Modifier\n"
            + indent + "            .size(20.dp)\n"
            + indent + "            .align(Alignment.TopStart)\n"
            + indent + "            .offset(x = (-6).dp, y = (-6).dp)\n"
            + indent + "            .zIndex(10f),\n"
            + indent + "        contentScale = ContentScale.Fit\n"
            + indent + "    )\n"
        )
        lines.insert(ai_surface, box_and_wing)
        print("AI wing injected")

        new_ai_color = find_line(lines, "Color(0xFFFFFFFF)")
        new_surface = None
        for i in range(new_ai_color, max(new_ai_color - 10, 0), -1):
            if "Surface(" in lines[i]:
                new_surface = i
                break
        if new_surface:
            brace_count = 0
            found_open = False
            for j in range(new_surface, min(new_surface + 40, len(lines))):
                for ch in lines[j]:
                    if ch == '{':
                        brace_count += 1
                        found_open = True
                    elif ch == '}':
                        brace_count -= 1
                if found_open and brace_count == 0:
                    lines.insert(j + 1, indent + "}\n")
                    print(f"AI Box closed at line {j + 1}")
                    break

with open(msg_file, "w") as f:
    f.writelines(lines)

print("All patches complete")
