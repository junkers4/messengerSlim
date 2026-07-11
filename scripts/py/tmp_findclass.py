import os,re
base='work\\decompiled_nres'
pat=re.compile(r'^\\.class\\s+public\\s+Landroidx/core/app/ComponentActivity;',re.M)
found=[]
for root,_,files in os.walk(base):
  for fn in files:
    if not fn.endswith('.smali'): continue
    p=os.path.join(root,fn)
    try:
      txt=open(p,'r',encoding='utf-8',errors='replace').read(400)
    except: continue
    if 'Landroidx/core/app/ComponentActivity;' in txt and '.class public' in txt:
      if 'Landroidx/core/app/ComponentActivity;' in txt:
        found.append(p)
for p in found: print(p)
print('count',len(found))
