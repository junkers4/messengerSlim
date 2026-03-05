import os, shutil
base='work\\decompiled_nres'
clean='work\\decompiled'
critical=[
 r'smali_classes2\\com\\facebook\\messenger\\neue\\MainActivity.smali',
 r'smali_classes4\\com\\facebook\\base\\activity\\DelegatingFbFragmentFrameworkActivity.smali',
 r'smali_classes3\\com\\facebook\\messenger\\intents\\AbstractIntentHandlerActivity.smali',
 r'smali_classes2\\com\\facebook\\resources\\impl\\WaitingForStringsActivity.smali',
 r'smali_classes8\\com\\facebook\\orca\\threadview\\ThreadViewActivity.smali',
 r'smali_classes8\\com\\facebook\\orca\\threadview\\ThreadViewBubblesActivity.smali',
 r'smali_classes7\\com\\facebook\\checkpoint\\CheckpointActivity.smali',
 r'smali_classes3\\com\\facebook\\voltron\\ui\\impl\\AppModuleDownloadActivity2.smali',
 r'smali_classes5\\com\\facebook\\messaging\\rtc\\incall\\activity\\InCallActivity.smali',
 r'smali_classes10\\com\\facebook\\messaging\\auth\\LaunchScreenActivity.smali',
 r'smali\\com\\facebook\\messenger\\splashscreen\\MessengerSplashScreenActivity.smali'
]
restored=0
for rel in critical:
 n=os.path.join(base,rel); c=os.path.join(clean,rel)
 if not (os.path.exists(n) and os.path.exists(c)):
  print('MISS', rel); continue
 txt=open(n,'r',encoding='utf-8',errors='replace').read()
 if 'Lcom/facebook/base/activity/FbFragmentActivity;->finish()V' in txt:
  shutil.copy2(c,n)
  restored += 1
  print('RESTORED', rel)
 else:
  print('KEEP', rel)
print('restored_count', restored)
