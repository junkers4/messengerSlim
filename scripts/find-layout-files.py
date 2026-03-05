"""
Parse resources.arsc to find file paths for specific resource IDs.
Usage: python find-layout-files.py <resources.arsc>
"""
import struct, sys

def u16(d,o): return struct.unpack_from('<H',d,o)[0]
def u32(d,o): return struct.unpack_from('<I',d,o)[0]

def read_str8(data, base, off):
    p = base + off
    c = data[p]; p += 1
    if c & 0x80: c = ((c & 0x7f) << 8) | data[p]; p += 1
    b = data[p]; p += 1
    if b & 0x80: b = ((b & 0x7f) << 8) | data[p]; p += 1
    return data[p:p+b].decode('utf-8', 'replace')

arsc_path = sys.argv[1]
data = open(arsc_path, 'rb').read()

# Global string pool at offset 12
sp_start = 12
sp_size = u32(data, sp_start + 4)
str_count = u32(data, sp_start + 8)
str_offsets_base = sp_start + 28
strings_data_base = sp_start + u32(data, sp_start + 16)

def get_str(idx):
    if idx < 0 or idx >= str_count: return f'<invalid:{idx}>'
    off = u32(data, str_offsets_base + idx * 4)
    return read_str8(data, strings_data_base, off)

# Package follows string pool
pkg_off = sp_start + sp_size
pkg_type = u16(data, pkg_off)
pkg_size = u32(data, pkg_off + 4)
print(f'Package type={hex(pkg_type)} size={hex(pkg_size)} at {hex(pkg_off)}')

TARGET_TYPE = 0x1f
TARGET_ENTRIES = {0x413, 0x414, 0x415, 0x416, 0x417}

# Walk sub-chunks inside package
# Package header = 8(chunk_header) + 4(id) + 256(name) + 20(5 uint32) = 288 bytes
# But we can scan from pkg_off+8 for safety
pos = pkg_off + 288  # skip fixed-size package header
pkg_end = pkg_off + pkg_size

found_type = False
chunk_count = 0

while pos < pkg_end:
    if pos + 8 > pkg_end:
        break
    chunk_type = u16(data, pos)
    chunk_header_size = u16(data, pos + 2)
    chunk_size = u32(data, pos + 4)
    
    if chunk_size == 0:
        print(f'Zero-size chunk at {hex(pos)}, stopping')
        break
    
    chunk_count += 1
    
    if chunk_type == 0x0201:  # RES_TABLE_TYPE_TYPE
        type_id = data[pos + 8]
        entry_count = u32(data, pos + 12)
        entries_start_off = u32(data, pos + 16)  # offset from chunk start to first entry (FIXED: was pos+20)
        
        if type_id == TARGET_TYPE:
            found_type = True
            
            # Offset array is right after the chunk header
            off_array_base = pos + chunk_header_size
            
            for i in range(entry_count):
                if i not in TARGET_ENTRIES:
                    continue
                entry_rel = u32(data, off_array_base + i * 4)
                if entry_rel == 0xFFFFFFFF:
                    continue  # null entry in this config, may be in another
                
                # Entry absolute position
                abs_entry = pos + entries_start_off + entry_rel
                entry_size = u16(data, abs_entry)
                entry_flags = u16(data, abs_entry + 2)
                
                # ResTable_value follows the entry
                value_off = abs_entry + entry_size
                value_data_type = data[value_off + 3]
                value_data = u32(data, value_off + 4)
                
                if value_data_type == 0x03:  # TYPE_STRING = file reference
                    fname = get_str(value_data)
                    print(f'  ID 0x7f1f{i:04x} -> {repr(fname)}')
                    TARGET_ENTRIES.discard(i)  # found it, no need to search more configs
    
    pos += chunk_size

print(f'Total chunks scanned: {chunk_count}, found target type: {found_type}')
