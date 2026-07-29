import urllib.request
import re
import json

url = 'https://www.youtube.com/results?search_query=technology'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)', 'Accept-Language': 'en-US,en;q=0.9', 'Cookie': 'SOCS=CAI'})
html = urllib.request.urlopen(req).read().decode('utf-8', errors='ignore')

matches = re.findall(r'"publishedTimeText"\s*:\s*\{\s*"simpleText"\s*:\s*"([^"]+)"', html)
print("PUBLISHED MATCHES:", matches[:10])

view_matches = re.findall(r'"viewCountText"\s*:\s*\{\s*"simpleText"\s*:\s*"([^"]+)"', html)
print("VIEW MATCHES:", view_matches[:10])
