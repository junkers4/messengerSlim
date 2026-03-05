import os, hashlib

def md5(path):
    with open(path,'rb') as f: return hashlib.md5(f.read()).hexdigest()
checks=[r'smali\\X\\000.smali', r'smali_classes7\\com\\facebook\\messaging\\bubbles\\inbox\\activity\\BubblesInboxActivity.smali']
for rel in checks:
 a=os.path.join('work\\decompiled_nres_clean', rel)
 b=os.path.join('work\\decompiled_nres', rel)
 print(rel, md5(a)==md5(b))
