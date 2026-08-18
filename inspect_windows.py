import ctypes
from ctypes import wintypes
import psutil

user32 = ctypes.windll.user32

procs = {p.pid: p.name() for p in psutil.process_iter(['pid', 'name'])}

results = []
def callback(hwnd, extra):
    if user32.IsWindowVisible(hwnd):
        rect = wintypes.RECT()
        user32.GetWindowRect(hwnd, ctypes.byref(rect))
        w = rect.right - rect.left
        h = rect.bottom - rect.top
        
        pid = wintypes.DWORD()
        user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
        
        length = user32.GetWindowTextLengthW(hwnd)
        title = ctypes.create_unicode_buffer(length + 1)
        user32.GetWindowTextW(hwnd, title, length + 1)
        
        cls_name = ctypes.create_unicode_buffer(256)
        user32.GetClassNameW(hwnd, cls_name, 256)
        
        ex_style = user32.GetWindowLongW(hwnd, -20)
        is_topmost = bool(ex_style & 0x00000008)
        is_layered = bool(ex_style & 0x00080000)
        is_transparent = bool(ex_style & 0x00000020)
        
        pname = procs.get(pid.value, "Unknown")
        
        if w > 0 and h > 0:
            results.append({
                'hwnd': hex(hwnd),
                'pid': pid.value,
                'pname': pname,
                'title': title.value,
                'class': cls_name.value,
                'rect': (rect.left, rect.top, rect.right, rect.bottom),
                'w': w, 'h': h,
                'topmost': is_topmost,
                'layered': is_layered,
                'transparent': is_transparent
            })
    return 1

cb = ctypes.WINFUNCTYPE(ctypes.c_int, wintypes.HWND, wintypes.LPARAM)(callback)
user32.EnumWindows(cb, 0)

print(f"Total visible windows: {len(results)}")

# Right monitor: X >= 2560, Y >= 60, width=1920, height=1080
print("\n--- ALL WINDOWS OCCUPYING OR OVERLAPPING RIGHT MONITOR (X >= 2560) ---")
for w in results:
    l, t, r, b = w['rect']
    # Check if window is located on the right monitor
    if r > 2560 and b > 60:
        print(f"PID {w['pid']:5} ({w['pname']:20}) | Topmost={str(w['topmost']):5} | Layered={str(w['layered']):5} | Rect=({l:5}, {t:5}, {r:5}, {b:5}) [{w['w']:4}x{w['h']:4}] | Title='{w['title']}' | Class='{w['class']}'")
