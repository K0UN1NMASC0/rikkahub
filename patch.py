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

# --- User wing: find the Surface with Color(0xFFFCE5EB) ---
user_color = find_line(lines, "Color(0xFFFCE5EB)")
print(f"User color line: {user_color}")

if user_color:
    # Find the Surface( line before it
    user_surface = None
    for i in range(user_color, max(user_color - 10, 0), -1):
        if "Surface(" in lines[i]:
            user_surface = i
            break
    print(f"User Surface line: {user_surface}")
    if user_surface:
        indent = get_indent(lines[user_surface])
        box_open = indent + "Box {\n"
        wing_code = (
            indent + "    Image(\n"
            + indent + "        painter = painterResource(R.drawable.wing_right),\n"
            + indent + "        contentDescription = null,\n"
            + indent + "        modifier = Modifier\n"
            + indent + "            .size(20.dp)\n"
            + indent + "            .align(Alignment.TopEnd)\n"
            + indent + "            .offset(x = 4.dp, y = (-4).dp)\n"
            + indent + "            .zIndex(10f),\n"
            + indent + "        contentScale = ContentScale.Fit\n"
            + indent + "    )\n"
        )
        lines.insert(user_surface, box_open + wing_code)
        print("User wing + Box open injected")

        # Now find where user Surface block closes
        # Look for the "} else {" that separates user from assistant
        new_user_color = find_line(lines, "Color(0xFFFCE5EB)")
        for j in range(new_user_color + 5, min(new_user_color + 30, len(lines))):
            if lines[j].strip() == "} else {":
                lines.insert(j, indent + "}\n")
                print(f"User Box closed at line {j}")
                break

# --- AI wing: find the Surface with Color(0xFFFFFFFF) ---
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
        box_open = indent + "Box {\n"
        wing_code = (
            indent + "    Image(\n"
            + indent + "        painter = painterResource(R.drawable.wing_left),\n"
            + indent + "        contentDescription = null,\n"
            + indent + "        modifier = Modifier\n"
            + indent + "            .size(20.dp)\n"
            + indent + "            .align(Alignment.TopStart)\n"
            + indent + "            .offset(x = (-4).dp, y = (-4).dp)\n"
            + indent + "            .zIndex(10f),\n"
            + indent + "        contentScale = ContentScale.Fit\n"
            + indent + "    )\n"
        )
        lines.insert(ai_surface, box_open + wing_code)
        print("AI wing + Box open injected")

        # Find where AI Surface block closes - look for "} else {"
        new_ai_color = find_line(lines, "Color(0xFFFFFFFF)")
        for j in range(new_ai_color + 5, min(new_ai_color + 30, len(lines))):
            if lines[j].strip() == "} else {":
                lines.insert(j, indent + "}\n")
                print(f"AI Box closed at line {j}")
                break

with open(msg_file, "w") as f:
    f.writelines(lines)

print("All patches complete")
