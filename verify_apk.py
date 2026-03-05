import zipfile
apk = "output/messenger-550.0.0.45.63-nres-spravaren-metaai-nuclear-full-v1-signed.apk"
z = zipfile.ZipFile(apk)
arsc = z.read("resources.arsc")
print(f"Spravaren: {arsc.count(b'Spravaren')}")
print(f"Messenger: {arsc.count(b'Messenger')}")
meta = [n for n in z.namelist() if n in ["res/d2o.xml","res/d2p.xml","res/d2q.xml","res/d2r.xml","res/d2s.xml"]]
print(f"Meta AI layouts: {len(meta)} files")
for n in meta:
    print(f"  {n}: {z.getinfo(n).file_size} bytes")
