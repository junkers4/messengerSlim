import os, re, hashlib, shutil
base = r'work\\decompiled_nres'
clean = r'work\\decompiled_nres_clean'

def read(path):
    with open(path,'r',encoding='utf-8',errors='replace') as f: return f.read()

def write(path,s):
    with open(path,'w',encoding='utf-8') as f: f.write(s)

# map class descriptor -> file
class_map = {}
for root,_,files in os.walk(base):
    for fn in files:
        if not fn.endswith('.smali'): continue
        p=os.path.join(root,fn)
        try:
            head=''.join(open(p,'r',encoding='utf-8',errors='replace').readlines()[:8])
        except: continue
        m=re.search(r'\\.class[^\n]* (L[^;]+;)', head)
        if m: class_map[m.group(1)] = p

def get_super(desc, depth=0):
    p = class_map.get(desc)
    if not p: return None
    try:
        head=''.join(open(p,'r',encoding='utf-8',errors='replace').readlines()[:20])
    except: return None
    m=re.search(r'\\.super\\s+(L[^;]+;)', head)
    return m.group(1) if m else None

def is_subclass_of(desc, target, depth=0):
    if depth>20 or not desc: return False
    if desc==target: return True
    sup=get_super(desc)
    if not sup: return False
    return is_subclass_of(sup,target,depth+1)

bad_replace=0
bad_restore=0
checked=0
for desc,p in list(class_map.items()):
    txt=read(p)
    if 'Lcom/facebook/base/activity/FbFragmentActivity;->finish()V' not in txt:
        continue
    checked += 1
    is_fb = is_subclass_of(desc, 'Lcom/facebook/base/activity/FbFragmentActivity;')
    if is_fb:
        continue
    is_activity = is_subclass_of(desc, 'Landroid/app/Activity;')
    if is_activity:
        new = txt.replace('Lcom/facebook/base/activity/FbFragmentActivity;->finish()V','Landroid/app/Activity;->finish()V')
        if new!=txt:
            write(p,new)
            bad_replace += 1
    else:
        rel = os.path.relpath(p, base)
        src = os.path.join(clean, rel)
        if os.path.exists(src):
            shutil.copy2(src,p)
            bad_restore += 1

print(f'checked_with_fbfinish={checked}')
print(f'fixed_activity_callsite={bad_replace}')
print(f'restored_nonactivity_files={bad_restore}')
