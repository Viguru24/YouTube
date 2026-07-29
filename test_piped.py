import urllib.request
import json

url = 'https://pipedapi.astral.cool/search?q=AKSTAR%20ENG&filter=all'
try:
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    res = urllib.request.urlopen(req, timeout=5).read().decode('utf-8')
    data = json.loads(res)
    items = data.get('items', data) if isinstance(data, dict) else data
    print("PIPED API RETURNED", len(items), "ITEMS")
    if items:
        print("SAMPLE ITEM KEYS:", items[0].keys())
        print("SAMPLE ITEM:", json.dumps(items[0], indent=2))
except Exception as e:
    print("PIPED API ERROR:", e)
