import os, re, shutil
base=r'work\\decompiled_nres'
clean=r'work\\decompiled_nres_clean'
pat='Lcom/facebook/base/activity/FbFragmentActivity;->finish()V'
restored=[]
for root,_,files in os.walk(base):
    for fn in files:
        if not fn.endswith('.smali'): continue
        p=os.path.join(root,fn)
        rel=os.path.relpath(p,base)
        rel_norm=rel.replace('\\','/')
        if not (rel_norm.startswith('smali/androidx/') or rel_norm.startswith('smali/android/') or '/androidx/' in rel_norm or '/android/' in rel_norm):
            continue
        try:
            txt=open(p,'r',encoding='utf-8',errors='replace').read()
        except: continue
        if pat in txt:
            src=os.path.join(clean,rel)
            if os.path.exists(src):
                shutil.copy2(src,p)
                restored.append(rel_norm)
print('restored_count',len(restored))
for r in restored[:50]:
    print(r)
