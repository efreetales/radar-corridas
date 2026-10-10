import re, urllib.request, os
UA = {"User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36",
      "Accept-Language": "pt-BR,pt;q=0.9"}
log = []
for app in open("apps.txt").read().split():
    url = f"https://play.google.com/store/apps/details?id={app}&hl=pt_BR&gl=BR"
    html = urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30).read().decode("utf-8", "ignore")
    t = re.search(r"<title[^>]*>(.*?)</title>", html, re.S)
    log.append(f"{app}: {t.group(1).strip() if t else '?'}")
    urls = []
    for m in re.finditer(r"<img[^>]+>", html):
        tag = m.group(0)
        low = tag.lower()
        if "captura de tela" in low or "screenshot" in low:
            s = re.search(r'src(?:set)?="(https://play-lh\.googleusercontent\.com/[^"=\s]+)', tag)
            if s and s.group(1) not in urls:
                urls.append(s.group(1))
    os.makedirs(app, exist_ok=True)
    for i, u in enumerate(urls[:16], 1):
        data = urllib.request.urlopen(urllib.request.Request(u + "=w1080-h2400", headers=UA), timeout=30).read()
        open(f"{app}/tela_{i:02d}.png", "wb").write(data)
    log.append(f"  {len(urls)} telas")
    # descrição
    d = re.search(r'<meta name="description" content="([^"]*)"', html)
    if d: open(f"{app}/descricao.txt", "w").write(d.group(1))
open("log.txt", "w").write("\n".join(log) + "\n")
print("\n".join(log))
