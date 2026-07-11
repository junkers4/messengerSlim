import os, hashlib

def md5(path):
    with open(path,'rb') as f:
        return hashlib.md5(f.read()).hexdigest()
base1='work\\decompiled_nres_clean'
base2='work\\decompiled_nres'
checks=[r'smali\\X\\000.smali', r'smali_classes7\\com\\facebook\\messaging\\bubbles\\inbox\\activity\\BubblesInboxActivity.smali']
for rel in checks:
 p1=os.path.join(base1,rel); p2=os.path.join(base2,rel)
 same=os.path.exists(p1) and os.path.exists(p2) and md5(p1)==md5(p2)
 print(rel, 'same_cleanNres=', same)
