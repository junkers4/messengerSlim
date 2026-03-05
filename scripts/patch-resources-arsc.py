"""
Binary patch resources.arsc for Spravaren branding + Meta AI UI hiding.
Replaces UTF-8 strings in-place (same byte length = no offset changes needed).
"""
import sys
import os
import shutil
import re

def replace_utf8_string(data: bytes, old: str, new: str) -> tuple[bytes, int]:
    """Replace all UTF-8 string occurrences in Android string pool."""
    old_b = old.encode('utf-8')
    new_b = new.encode('utf-8')
    if len(old_b) != len(new_b):
        raise ValueError(f"Length mismatch: {repr(old)} ({len(old_b)}B) vs {repr(new)} ({len(new_b)}B)")
    count = 0
    result = bytearray(data)
    start = 0
    while True:
        idx = result.find(old_b, start)
        if idx < 0:
            break
        result[idx:idx+len(old_b)] = new_b
        count += 1
        start = idx + len(new_b)
    return bytes(result), count


def main():
    arsc_path = sys.argv[1] if len(sys.argv) > 1 else None
    if not arsc_path:
        print("Usage: patch-resources-arsc.py <path/to/resources.arsc>")
        sys.exit(1)

    backup = arsc_path + '.bak'
    if not os.path.exists(backup):
        shutil.copy2(arsc_path, backup)
        print(f"Backup created: {backup}")
    else:
        print(f"Backup already exists: {backup}")

    with open(arsc_path, 'rb') as f:
        data = f.read()

    original_size = len(data)

    # --- Branding: Messenger -> Spravaren (same 9-char length) ---
    data, n = replace_utf8_string(data, 'Messenger', 'Spravaren')
    print(f"  Messenger -> Spravaren: {n} replacements")

    # --- Meta AI strings -> blank spaces (same length, hides UI labels) ---
    # Order: longest first to avoid partial matches
    for old, new in [
        ('Meta AI camera', '              '),   # 14 chars -> 14 spaces
        ('Meta AI voice',  '             '),    # 13 chars -> 13 spaces
        ('Ask Meta AI',    '           '),      # 11 chars -> 11 spaces
        ('Meta AI',        '       '),          # 7 chars  -> 7 spaces
    ]:
        data, n = replace_utf8_string(data, old, new)
        print(f"  {repr(old)} -> spaces: {n} replacements")

    assert len(data) == original_size, "File size changed - this would corrupt arsc!"

    with open(arsc_path, 'wb') as f:
        f.write(data)

    print(f"\nPatched: {arsc_path} ({original_size} bytes, unchanged size)")


if __name__ == '__main__':
    main()
