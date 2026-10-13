"""Agenda de eventos de HOJE em São Paulo, sem IA (roda de graça no GitHub Actions).

Fontes:
  - Ticket360  (lista da cidade + página de cada evento: horário, endereço e coordenadas)
  - Sympla     (lista "hoje" da cidade: horário, endereço e coordenadas)
  - semana.json (eventos dos próximos dias levantados na revisão semanal: Shotgun, Ao Vivo, casas)
Aplica as regras de horário de saída e grava eventos.json (o passo seguinte, geocode.py,
localiza os que vierem sem coordenadas).
"""
import datetime as dt, html, json, re, sys, time, unicodedata, urllib.request

TZ = dt.timezone(dt.timedelta(hours=-3))
AGORA = dt.datetime.now(TZ)
# Antes das 5h ainda é a noite de ontem
HOJE = (AGORA - dt.timedelta(hours=5)).date()
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
      "Accept-Language": "pt-BR,pt;q=0.9"}
LOG = []

def log(*a):
    s = " ".join(str(x) for x in a); LOG.append(s); print(s)

def get(url):
    for t in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=40) as r:
                return r.read().decode("utf-8", "ignore")
        except Exception as ex:
            log("  erro", url, ex); time.sleep(4)
    return ""

def norm(t):
    t = "".join(c for c in unicodedata.normalize("NFD", (t or "").lower()) if unicodedata.category(c) != "Mn")
    return re.sub(r"[^a-z0-9]+", " ", t).strip()

def hm(d): return d.strftime("%H:%M")

# ---------- casas e regras de saída ----------
# "abertura": o horário do ingresso é a abertura da casa; show começa 1–2h depois. Saída ≈ +4h15 (ver aprendizados.md)
# "show":     horário é do show; saída ≈ +1h45
# "teatro":   peça/musical/stand-up; saída ≈ +2h
# "festa":    balada; saída espalhada
CASAS = {k: v for k, v in json.load(open("tools/casas.json", encoding="utf-8")).items() if not k.startswith("_")}

def regra_da_casa(local):
    n = norm(local)
    for chave, regra in CASAS.items():
        if norm(chave) in n:
            return regra
    return None

PALAVRA_FESTA = re.compile(r"\b(festa|baile|rave|open air|after|balada|forro|pagode|funk|techno|house|samba|dj|party|noite)\b")
PALAVRA_TEATRO = re.compile(r"\b(teatro|musical|stand ?up|comedia|espetaculo|peca|opera|ballet|danca|circo|monologo)\b")

def classificar(nome, local, dica=""):
    r = regra_da_casa(local)
    if r: return r
    t = norm(f"{nome} {dica}")
    loc = norm(local)
    if PALAVRA_TEATRO.search(t) or "teatro" in loc: return "teatro"
    if PALAVRA_FESTA.search(t): return "festa"
    return "show"

TIPO = {"abertura": "show", "show": "show", "teatro": "teatro", "festa": "festa", "futebol": "futebol"}

def saida(regra, inicio, fim_informado=None):
    """Devolve (meio da saída, incerteza em minutos)."""
    if fim_informado and inicio < fim_informado <= inicio + dt.timedelta(hours=9):
        if regra == "festa": return fim_informado - dt.timedelta(minutes=30), 120
        return fim_informado, 45
    if regra == "abertura": return inicio + dt.timedelta(minutes=255), 90
    if regra == "teatro": return inicio + dt.timedelta(minutes=120), 30
    if regra == "futebol": return inicio + dt.timedelta(minutes=115), 30
    if regra == "festa":
        fim = inicio + dt.timedelta(hours=6)
        limite = dt.datetime.combine(inicio.date() + dt.timedelta(days=1 if inicio.hour >= 12 else 0), dt.time(4, 30), TZ)
        return min(fim, limite), 120
    return inicio + dt.timedelta(minutes=105), 45

EVENTOS = {}

def add(nome, local, endereco, inicio, regra, fonte, origem, lat=None, lng=None, fim_informado=None, publico=None):
    if not nome or not inicio: return
    if (inicio - dt.timedelta(hours=5)).date() != HOJE: return
    chave = norm(nome)[:40] + "|" + norm(local)[:20]
    if chave in EVENTOS: return
    fim, inc = saida(regra, inicio, fim_informado)
    if lat in (0, 0.0) or lng in (0, 0.0): lat = lng = None
    e = {"nome": re.sub(r"\s+", " ", nome).strip()[:70], "local": local.strip(), "endereco": endereco.strip(),
         "tipo": TIPO.get(regra, "show"), "data": inicio.date().isoformat(), "inicio": hm(inicio),
         "fim_estimado": hm(fim), "incerteza_min": inc, "publico_estimado": publico, "fonte": fonte, "origem": origem,
         "regra": regra}
    if lat is not None and lng is not None: e["lat"], e["lng"] = round(lat, 6), round(lng, 6)
    EVENTOS[chave] = e

# ---------- Ticket360 ----------
MESES = {m: i + 1 for i, m in enumerate(["janeiro", "fevereiro", "marco", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"])}

def ticket360():
    s = get("https://www.ticket360.com.br/cidade/sp/sao-paulo")
    n = 0
    for m in re.finditer(r'<a[^>]+href="(/evento/\d+[^"]*)"[^>]*>(.*?)</a>', s, re.S):
        t = re.sub(r"<[^>]+>", "|", m.group(2)); partes = [p.strip() for p in html.unescape(t).split("|") if p.strip()]
        if not partes: continue
        d = re.search(r"(\d{1,2})\s+([A-Za-zçÇ]+)", norm(partes[0]))
        if not d or norm(d.group(2)) not in MESES: continue
        dia = dt.date(HOJE.year, MESES[norm(d.group(2))], int(d.group(1)))
        if dia != HOJE: continue
        url = "https://www.ticket360.com.br" + m.group(1)
        ev = None
        for j in re.findall(r'<script type="application/ld\+json"[^>]*>(.*?)</script>', get(url), re.S):
            try: data = json.loads(j)
            except Exception: continue
            for o in data if isinstance(data, list) else [data]:
                if isinstance(o, dict) and "startDate" in o: ev = o
        if not ev: continue
        ini = dt.datetime.fromisoformat(ev["startDate"]).astimezone(TZ)
        fim = None
        if ev.get("endDate"):
            f = dt.datetime.fromisoformat(ev["endDate"]).astimezone(TZ)
            if f - ini <= dt.timedelta(hours=6): fim = f
        loc = ev.get("location") or {}
        ad = loc.get("address") or {}; geo = loc.get("geo") or {}
        tipo_ld = ev.get("@type", "")
        dica = "teatro" if tipo_ld in ("TheaterEvent", "ComedyEvent", "DanceEvent") else ""
        regra = classificar(ev.get("name", ""), loc.get("name", ""), dica)
        add(html.unescape(ev.get("name", "")), loc.get("name", ""), f"{ad.get('streetAddress', '')}, São Paulo", ini, regra, url, "ticket360",
            geo.get("latitude"), geo.get("longitude"), fim)
        n += 1  # (a lista da Ticket360 já vem filtrada para hoje)
    log(f"Ticket360: {n} de hoje")

# ---------- Sympla ----------
def sympla_pagina(url):
    s = get(url)
    chunks = re.findall(r'self\.__next_f\.push\(\[1,"(.*?)"\]\)', s, re.S)
    txt = "".join(json.loads('"' + c + '"') for c in chunks)
    i = txt.find('"searchDataResult":')
    if i < 0: return None
    return json.JSONDecoder().raw_decode(txt[i + len('"searchDataResult":'):])[0]

def sympla():
    n, pag, total = 0, 1, 0
    while pag <= 15:
        o = sympla_pagina(f"https://www.sympla.com.br/eventos/sao-paulo-sp/hoje?page={pag}")
        if not o or not o.get("data"): break
        total = o.get("total", 0)
        for e in o["data"]:
            loc = e.get("location") or {}
            if norm(loc.get("city")) != "sao paulo": continue
            ini = dt.datetime.fromisoformat(e["start_date"]).astimezone(TZ)
            fim = dt.datetime.fromisoformat(e["end_date"]).astimezone(TZ) if e.get("end_date") else None
            if e.get("duration_type") == "multiple":
                # temporada (teatro, exposição): a sessão de hoje não vem na lista; só entra se começa hoje
                if (ini - dt.timedelta(hours=5)).date() != HOJE: continue
                fim = None
            if ini.hour < 12 and not (fim and fim.hour >= 18): continue  # matinê/evento de dia: pouco útil à noite
            endereco = ", ".join(x for x in [loc.get("address", "").strip(), (loc.get("neighborhood") or "").strip(), "São Paulo"] if x)
            regra = classificar(e.get("name", ""), loc.get("name", ""))
            antes = len(EVENTOS)
            add(e.get("name", ""), loc.get("name", ""), endereco, ini, regra, e.get("url", ""), "sympla", loc.get("lat"), loc.get("lon"), fim)
            n += len(EVENTOS) - antes
        if pag * o.get("limit", 16) >= total: break
        pag += 1
    log(f"Sympla: {n} de hoje ({pag} páginas, total {total})")

# ---------- revisão semanal ----------
def semana():
    try: d = json.load(open("semana.json", encoding="utf-8"))
    except Exception: log("semana.json: não encontrado"); return
    n = 0
    for e in d.get("eventos", []):
        try:
            ini = dt.datetime.fromisoformat(f"{e['data']}T{e['inicio']}").replace(tzinfo=TZ)
        except Exception: continue
        regra = e.get("regra") or classificar(e.get("nome", ""), e.get("local", ""), e.get("tipo", ""))
        fim = None
        if e.get("fim"):
            try:
                fim = dt.datetime.fromisoformat(f"{e['data']}T{e['fim']}").replace(tzinfo=TZ)
                if fim <= ini: fim += dt.timedelta(days=1)
            except Exception: fim = None
        antes = len(EVENTOS)
        add(e.get("nome", ""), e.get("local", ""), e.get("endereco", ""), ini, regra, e.get("fonte", ""), "semana", e.get("lat"), e.get("lng"), fim, e.get("publico_estimado"))
        n += len(EVENTOS) - antes
    log(f"semana.json: {n} de hoje")

def main():
    log(f"Agenda {HOJE} (rodou {AGORA:%Y-%m-%d %H:%M})")
    for f in (ticket360, sympla, semana):
        try: f()
        except Exception as ex: log(f"{f.__name__}: FALHOU {ex}")
    evs = sorted(EVENTOS.values(), key=lambda e: (e["data"], e["inicio"]))
    if not evs:
        log("Nenhum evento encontrado; mantendo o arquivo anterior")
        open("agenda_log.txt", "w", encoding="utf-8").write("\n".join(LOG) + "\n"); sys.exit(0)
    out = {"gerado_em": AGORA.isoformat(timespec="seconds"), "data": HOJE.isoformat(),
           "observacao": "Gerado automaticamente (Ticket360, Sympla e revisão semanal). Saída estimada pelas regras de tools/casas.json.",
           "eventos": evs}
    json.dump(out, open("eventos.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    por = {}
    for e in evs: por[e["origem"]] = por.get(e["origem"], 0) + 1
    log(f"Total: {len(evs)} {por}")
    open("agenda_log.txt", "w", encoding="utf-8").write("\n".join(LOG) + "\n")

if __name__ == "__main__":
    main()
