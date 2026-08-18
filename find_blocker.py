import ctypes
from ctypes import wintypes
import subprocess
import json

user32 = ctypes.windll.user32

class POINT(ctypes.Structure):
    _fields_ = [("x", ctypes.c_long), ("y", ctypes.c_long)]

user32.WindowFromPoint.argtypes = [POINT]
user32.WindowFromPoint.restype = wintypes.HWND

ps = subprocess.check_output(['powershell', '-Command', 'Get-Process | Select-Object Id, ProcessName | ConvertTo-Json']).decode('utf-8', errors='ignore')
try:
    proc_list = json.loads(ps)
    proc_map = {p['Id']: p['ProcessName'] for p in proc_list}
except:
    proc_map = {}

# Test points on right monitor
for x in [2600, 2700, 2800, 3000, 3500]:
    for y in [100, 200, 300, 500]:
        pt = POINT(x, y)
        hwnd = user32.WindowFromPoint(pt)
        if hwnd:
            pid = wintypes.DWORD()
            user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
            
            length = user32.GetWindowTextLengthW(hwnd)
            title = ctypes.create_unicode_buffer(length + 1)
            user32.GetWindowTextW(hwnd, title, length + 1)
            
            cls_name = ctypes.create_unicode_buffer(256)
            user32.GetClassNameW(hwnd, cls_name, 256)
            
            rect = wintypes.RECT()
            user32.GetWindowRect(hwnd, ctypes.byref(rect))
            
            proc_name = proc_map.get(pid.value, 'Unknown')
            print(f"Point ({x}, {y}) -> HWND {hex(hwnd)} (PID {pid.value} - {proc_name}) | Class: '{cls_name.value}' | Title: '{title.value}' | Rect: ({rect.left}, {rect.top}, {rect.right}, {rect.bottom})")
        else:
            print(f"Point ({x}, {y}) -> NULL")
