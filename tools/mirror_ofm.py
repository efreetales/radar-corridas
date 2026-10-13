import json, math, os, re, urllib.request
UA={"User-Agent":"Mozilla/5.0"}
def get(u):
    with urllib.request.urlopen(urllib.request.Request(u,headers=UA),timeout=60) as r: return r.read()
def save(u,data):
    p="m/"+re.sub(r"^https?://","",u).split("?")[0]
    os.makedirs(os.path.dirname(p),exist_ok=True); open(p,"wb").write(data)
for name in ["dark","liberty","positron"]:
    su=f"https://tiles.openfreemap.org/styles/{name}"; st=get(su); save(su,st); st=json.loads(st)
    if name!="dark": continue
    for src in st["sources"].values():
        if "url" in src:
            tj=get(src["url"]); save(src["url"],tj); tj=json.loads(tj); tiles=tj["tiles"][0]
            bb=(-23.66,-23.44,-46.78,-46.55)
            for z in range(0,15):
                n=2**z
                def t(lat,lon): return int((lon+180)/360*n), int((1-math.asinh(math.tan(math.radians(lat)))/math.pi)/2*n)
                a=t(bb[1],bb[2]); b=t(bb[0],bb[3])
                for x in range(a[0],b[0]+1):
                    for y in range(a[1],b[1]+1):
                        u=tiles.replace("{z}",str(z)).replace("{x}",str(x)).replace("{y}",str(y))
                        try: save(u,get(u))
                        except Exception as e: print("tile",u,e)
    sp=st.get("sprite")
    for spr in (sp if isinstance(sp,list) else [{"url":sp}]):
        for suf in [".json",".png","@2x.json","@2x.png"]:
            try: save(spr["url"]+suf,get(spr["url"]+suf))
            except Exception as e: print("sprite",e)
    fonts=set()
    for l in st["layers"]:
        tf=l.get("layout",{}).get("text-font")
        if isinstance(tf,list) and all(isinstance(x,str) for x in tf): fonts.add(",".join(tf))
    print("fonts",fonts)
    for f in fonts:
        for rg in ["0-255","256-511","8192-8447"]:
            u=st["glyphs"].replace("{fontstack}",urllib.request.quote(f)).replace("{range}",rg)
            try: save(u,get(u))
            except Exception as e: print("glyph",u,e)
print("ok")
