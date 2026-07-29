import urllib.request
import re

url = 'https://www.youtube.com/results?search_query=AKSTAR+ENG'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)', 'Accept-Language': 'en-US,en;q=0.9', 'Cookie': 'SOCS=CAI'})
html = urllib.request.urlopen(req).read().decode('utf-8', errors='ignore')

blocks = html.split('"videoRenderer":{')
print(f"Total videoRenderers for AKSTAR ENG: {len(blocks)-1}")

for idx, block in enumerate(blocks[1:6], 1):
    id_m = re.search(r'"videoId":"([a-zA-Z0-9_-]{11})"', block)
    # Check simpleText OR runs text
    pub_simple = re.search(r'"publishedTimeText":\s*\{\s*"simpleText":\s*"([^"]+)"', block)
    pub_runs = re.search(r'"publishedTimeText":\s*\{\s*"runs":\s*\[\s*\{\s*"text":\s*"([^"]+)"', block)
    
    view_simple = re.search(r'"viewCountText":\s*\{\s*"simpleText":\s*"([^"]+)"', block)
    view_runs = re.search(r'"viewCountText":\s*\{\s*"runs":\s*\[\s*\{\s*"text":\s*"([^"]+)"', block)
    
    vid = id_m.group(1) if id_m else "N/A"
    pub = (pub_simple.group(1) if pub_simple else (pub_runs.group(1) if pub_runs else "N/A"))
    view = (view_simple.group(1) if view_simple else (view_runs.group(1) if view_runs else "N/A"))
    
    print(f"[{idx}] ID: {vid} | Time: {pub} | Views: {view}")
