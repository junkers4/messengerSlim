import hashlib, os
pairs=[
(r'work\\decompiled_nres\\smali_classes2\\com\\facebook\\messenger\\neue\\MainActivity.smali', r'work\\decompiled\\smali_classes2\\com\\facebook\\messenger\\neue\\MainActivity.smali'),
(r'work\\decompiled_nres\\smali_classes4\\com\\facebook\\base\\activity\\DelegatingFbFragmentFrameworkActivity.smali', r'work\\decompiled\\smali_classes4\\com\\facebook\\base\\activity\\DelegatingFbFragmentFrameworkActivity.smali'),
(r'work\\decompiled_nres\\smali\\com\\facebook\\base\\activity\\FbFragmentActivity.smali', r'work\\decompiled\\smali\\com\\facebook\\base\\activity\\FbFragmentActivity.smali')
]
md=lambda p: hashlib.md5(open(p,'rb').read()).hexdigest()
for a,b in pairs:
 print(os.path.basename(a), 'sameAsDecompiled=', md(a)==md(b))
