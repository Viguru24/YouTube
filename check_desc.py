import urllib.request
import json
import re

videoId = 'SBHG6xjui_o'
url = f'https://www.youtube.com/watch?v={videoId}&hl=en&gl=US'
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Accept-Language': 'en-US,en;q=0.9',
    'Cookie': 'PREF=hl=en&gl=US; SOCS=CAI'
}
req = urllib.request.Request(url, headers=headers)
with urllib.request.urlopen(req) as resp:
    html = resp.read().decode('utf-8')
    m_player = re.search(r'ytInitialPlayerResponse\s*=\s*({.*?});', html)
    if m_player:
        data = json.loads(m_player.group(1))
        details = data.get('videoDetails', {})
        print('Title:', details.get('title'))
        print('Channel:', details.get('author'))
        with open('desc.txt', 'w', encoding='utf-8') as f:
            f.write(details.get('shortDescription', ''))
        print('Saved description to desc.txt')
