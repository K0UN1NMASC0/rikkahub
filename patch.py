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

# --- User wing: put inside Surface's Column, as overlay ---
user_color = find_line(lines, "Color(0xFFFCE5EB)")
print(f"User color line: {user_color}")

if user_color:
    # Find "Column(modifier = Modifier.padding(8.dp))" after user Surface
    user_column = None
    for i in range(user_color, min(user_color + 8, len(lines))):
        if "Column(modifier = Modifier.padding" in lines[i]:
            user_column = i
            break
    print(f"User Column line: {user_column}")
    if user_column:
        # Replace Column with Box containing wing overlay + Column
        indent = get_indent(lines[user_column])
        # Replace the Column line with Box + wing + Column
        old_line = lines[user_column]
        new_block = (
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
            + old_line
        )
        lines[user_column] = new_block
        print("User wing injected inside Surface")

        # Find the closing "}" of Column and add Box closing "}"
        # Count braces from user_column to find matching close
        brace_count = 0
        found_open = False
        for j in range(user_column, min(user_column + 30, len(lines))):
            for ch in lines[j]:
                if ch == '{':
                    brace_count += 1
                    found_open = True
                elif ch == '}':
                    brace_count -= 1
            if found_open and brace_count == 0:
                # This is where Column closes, add Box close after
                lines.insert(j + 1, indent + "}\n")
                print(f"User Box closed at line {j + 1}")
                break

# --- AI wing: same approach ---
ai_color = find_line(lines, "Color(0xFFFFFFFF)")
print(f"AI color line: {ai_color}")

if ai_color:
    ai_column = None
    for i in range(ai_color, min(ai_color + 8, len(lines))):
        if "Column(modifier = Modifier.padding" in lines[i]:
            ai_column = i
            break
    print(f"AI Column line: {ai_column}")
    if ai_column:
        indent = get_indent(lines[ai_column])
        old_line = lines[ai_column]
        new_block = (
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
            + old_line
        )
        lines[ai_column] = new_block
        print("AI wing injected inside Surface")

        brace_count = 0
        found_open = False
        for j in range(ai_column, min(ai_column + 30, len(lines))):
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
