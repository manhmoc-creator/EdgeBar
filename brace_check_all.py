import sys, os

def check(path):
    with open(path, 'r', encoding='utf-8', errors='replace') as f:
        text = f.read()
    stack = []
    pairs = {')': '(', ']': '[', '}': '{'}
    openers = set(pairs.values())
    closers = set(pairs.keys())
    i = 0; n = len(text); line = 1; col = 1
    in_line_comment = in_block_comment = in_string = in_char = False
    errors = []
    def advance(ch):
        nonlocal line, col
        if ch == '\n': line += 1; col = 1
        else: col += 1
    while i < n:
        ch = text[i]; nxt = text[i+1] if i+1 < n else ''
        if in_line_comment:
            if ch == '\n': in_line_comment = False
            advance(ch); i += 1; continue
        if in_block_comment:
            if ch == '*' and nxt == '/':
                in_block_comment = False; advance(ch); i += 1; advance('/'); i += 1; continue
            advance(ch); i += 1; continue
        if in_string:
            if ch == '\\':
                advance(ch); i += 1
                if i < n: advance(text[i]); i += 1
                continue
            if ch == '"': in_string = False
            advance(ch); i += 1; continue
        if in_char:
            if ch == '\\':
                advance(ch); i += 1
                if i < n: advance(text[i]); i += 1
                continue
            if ch == "'": in_char = False
            advance(ch); i += 1; continue
        if ch == '/' and nxt == '/': in_line_comment = True; advance(ch); i += 1; continue
        if ch == '/' and nxt == '*': in_block_comment = True; advance(ch); i += 1; advance('*'); i += 1; continue
        if ch == '"': in_string = True; advance(ch); i += 1; continue
        if ch == "'": in_char = True; advance(ch); i += 1; continue
        if ch in openers:
            stack.append((ch, line, col))
        elif ch in closers:
            expected = pairs[ch]
            if not stack:
                errors.append(f"Dư dấu đóng '{ch}' tại dòng {line}, cột {col}")
            else:
                top = stack.pop()
                if top[0] != expected:
                    close_map = {'(':')','[':']','{':'}'}
                    errors.append(f"Lệch: mở '{top[0]}' dòng {top[1]} cột {top[2]}, gặp đóng '{ch}' dòng {line} cột {col} (cần '{close_map[top[0]]}')")
        advance(ch); i += 1
    return errors, stack

def main(root):
    found_any = False
    for dirpath, _, files in os.walk(root):
        for fn in sorted(files):
            if not fn.endswith('.java'): continue
            path = os.path.join(dirpath, fn)
            errors, stack = check(path)
            if errors or stack:
                found_any = True
                print(f"\n== {path} ==")
                for e in errors[:20]:
                    print("  " + e)
                if stack:
                    print(f"  Còn {len(stack)} dấu mở CHƯA ĐÓNG. Gần nhất (khả năng cao là gốc lỗi):")
                    for item in stack[-10:][::-1]:
                        print(f"    '{item[0]}' mở tại dòng {item[1]}, cột {item[2]}")
    if not found_any:
        print("Toàn bộ file .java đều CÂN BẰNG ngoặc — lỗi (nếu còn) không phải do thiếu/dư { } ( ) [ ].")

if __name__ == "__main__":
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    main(root)
