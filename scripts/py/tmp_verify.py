import os, hashlib

def md5(path):
    with open(path,'rb') as f:
        return hashlib.md5(f.read()).hexdigest()
clean = r'work\\decompiled'
nres = r'work\\decompiled_nres'
checks = [
 r'smali\\com\\facebook\\sosource\\bsod\\BSODActivity.smali',
 r'smali_classes7\\com\\facebook\\messaging\\bubbles\\inbox\\activity\\BubblesInboxActivity.smali',
 r'smali\\X\\000.smali']
for rel in checks:
 c=os.path.join(clean,rel); n=os.path.join(nres,rel)
 same=os.path.exists(c) and os.path.exists(n) and md5(c)==md5(n)
 print(f'{rel} same={same}')
