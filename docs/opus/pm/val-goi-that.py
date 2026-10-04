import zipfile,os,sys,json,hashlib,tempfile,shutil
SYS={"libc.so","libm.so","libdl.so","liblog.so","libandroid.so","libz.so","libEGL.so","libGLESv1_CM.so","libGLESv2.so","libGLESv3.so","libOpenSLES.so","libjnigraphics.so","libvulkan.so","libaaudio.so","libmediandk.so","libnativewindow.so","libcamera2ndk.so","libstdc++.so","libsync.so","libneuralnetworks.so","libOpenMAXAL.so","libamidi.so","libbinder_ndk.so"}
def run(zp,main,abi,flatten=False):
    d=tempfile.mkdtemp(); root=os.path.realpath(d)
    try:
        written=set()
        with zipfile.ZipFile(zp) as z:
            for e in z.infolist():
                orig=os.path.realpath(os.path.join(root,e.filename))
                if not orig.startswith(root+os.sep): return f"FAIL zip vượt thư mục: {e.filename!r}"
                if e.is_dir(): continue
                dest=os.path.realpath(os.path.join(root,os.path.basename(e.filename))) if flatten else orig
                if dest in written: return f"FAIL trùng tên {e.filename}"
                written.add(dest); os.makedirs(os.path.dirname(dest),exist_ok=True)
                open(dest,'wb').write(z.read(e))
        m=os.path.join(root,main)
        if not os.path.isfile(m) or os.path.getsize(m)==0: return f"FAIL không có {main}"
        mf=os.path.join(root,'manifest.json'); md=os.path.dirname(m)
        if os.path.isfile(mf):
            meta=json.load(open(mf))
            if 'abi' in meta and meta['abi']!=abi: return f"FAIL manifest abi {meta['abi']}"
            for n in meta.get('loadOrder',[]):
                p=os.path.join(md,n)
                if not os.path.isfile(p) or os.path.getsize(p)==0: return f"FAIL loadOrder thiếu {n}"
            for n,info in (meta.get('files') or {}).items():
                p=os.path.join(md,n)
                if not os.path.isfile(p) or os.path.getsize(p)==0: return f"FAIL files thiếu {n}"
                if 'size' in info and os.path.getsize(p)!=info['size']: return f"FAIL size {n}"
                if 'sha256' in info and hashlib.sha256(open(p,'rb').read()).hexdigest()!=info['sha256'].lower(): return f"FAIL sha {n}"
        nd=os.path.join(root,'needed.txt')
        if os.path.isfile(nd):
            for l in open(nd).read().splitlines():
                l=l.strip()
                if l and l not in SYS:
                    p=os.path.join(root,l)
                    if not os.path.isfile(p) or os.path.getsize(p)==0: return f"FAIL needed thiếu {l}"
        exp={"arm64-v8a":183,"armeabi-v7a":40}[abi]; cls=2 if abi=="arm64-v8a" else 1
        n=0
        for dp,_,fs in os.walk(root):
            for f in fs:
                if f.endswith('.so'):
                    h=open(os.path.join(dp,f),'rb').read(20); n+=1
                    mach=h[18]|(h[19]<<8)
                    if len(h)!=20 or h[:4]!=b'\x7fELF' or h[4]!=cls or h[5]!=1 or mach!=exp: return f"FAIL ELF {f} mach={mach}"
        return f"OK ({len(written)} file, {n} .so, manifest={'có' if os.path.isfile(mf) else 'không'}, needed={'có' if os.path.isfile(nd) else 'không'})"
    finally: shutil.rmtree(d)
P=os.path.dirname(os.path.abspath(__file__))
for zp,main,abi,fl in [("sevenzip-arm64-v8a.zip",sys.argv[1],"arm64-v8a",False),("sevenzip-armeabi-v7a.zip",sys.argv[1],"armeabi-v7a",False),("kirikiri-arm64.zip",sys.argv[2],"arm64-v8a",False),("onsyuri-web.zip","onsyuri.wasm","arm64-v8a",False),("azahar-android-arm64.zip","libcitra-android.so","arm64-v8a",True)]:
    print(zp, run(os.path.join(P,zp),main,abi,fl))
