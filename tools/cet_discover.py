"""Descobre a estrutura do painel Power BI público da CET (fiscalização eletrônica)."""
import json, os, urllib.request

KEY = "d8b9566c-25e1-4f66-a54b-ae3b02bda6e7"
TENANT = "b0826786-6f69-4ecd-bfa2-622daa2b19ad"
os.makedirs("out", exist_ok=True)

def get(url, headers=None):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0", **(headers or {})})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()

def post(url, body, headers=None):
    req = urllib.request.Request(url, data=json.dumps(body).encode(), method="POST",
                                 headers={"User-Agent": "Mozilla/5.0", "Content-Type": "application/json", **(headers or {})})
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()

route = json.loads(get(f"https://api.powerbi.com/public/routing/cluster/{TENANT}"))
print("route", route)
cluster = route["FixedClusterUri"].rstrip("/")
api = cluster.replace("://", "://").replace("-redirect", "").replace(".analysis.windows.net", "-api.analysis.windows.net") if "-api." not in cluster else cluster
print("api", api)
h = {"X-PowerBI-ResourceKey": KEY}
expl = get(f"{api}/public/reports/{KEY}/modelsAndExploration?preferReadOnlySession=true", h)
open("out/exploration.json", "wb").write(expl)
e = json.loads(expl)
model_id = e["models"][0]["id"]
print("model", model_id)
try:
    schema = post(f"{api}/public/reports/conceptualschema", {"modelIds": [model_id]}, h)
    open("out/schema.json", "wb").write(schema)
except Exception as ex:
    print("schema falhou", ex)
open("out/meta.json", "w").write(json.dumps({"api": api, "model": model_id, "dbName": e["models"][0].get("dbName")}))
