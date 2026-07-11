import hashlib
p1=r'work\\decompiled_nres\\smali_classes4\\com\\facebook\\base\\activity\\DelegatingFbFragmentFrameworkActivity.smali'
p2=r'work\\decompiled_nres_clean\\smali_classes4\\com\\facebook\\base\\activity\\DelegatingFbFragmentFrameworkActivity.smali'
md=lambda p: hashlib.md5(open(p,'rb').read()).hexdigest()
print('same=', md(p1)==md(p2))
