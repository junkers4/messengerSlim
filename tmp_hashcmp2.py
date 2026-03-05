import hashlib
pairs=[
(r'work\\decompiled_nres\\smali\\com\\facebook\\base\\activity\\FbFragmentActivity.smali', r'work\\decompiled_nres_clean\\smali\\com\\facebook\\base\\activity\\FbFragmentActivity.smali'),
(r'work\\decompiled_nres\\smali_classes2\\com\\facebook\\messenger\\neue\\MainActivity.smali', r'work\\decompiled_nres_clean\\smali_classes2\\com\\facebook\\messenger\\neue\\MainActivity.smali')
]
for a,b in pairs:
    h=lambda p: hashlib.md5(open(p,'rb').read()).hexdigest()
    print(a.split('\\')[-1], 'same=', h(a)==h(b))
