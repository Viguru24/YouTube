Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Text;
using System.Collections.Generic;

public class WinAPI {
    [DllImport("user32.dll", EntryPoint="WindowFromPoint")]
    public static extern IntPtr WindowFromPoint(long point);

    public static IntPtr WindowFromPointSafe(int x, int y) {
        long pt = ((long)y << 32) | ((long)x & 0xFFFFFFFFL);
        return WindowFromPoint(pt);
    }

    [DllImport("user32.dll")]
    public static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int count);

    [DllImport("user32.dll")]
    public static extern int GetClassName(IntPtr hWnd, StringBuilder text, int count);

    [DllImport("user32.dll")]
    public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);

    [DllImport("user32.dll")]
    public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);

    [DllImport("user32.dll")]
    public static extern IntPtr GetAncestor(IntPtr hWnd, uint gaFlags);

    [DllImport("user32.dll")]
    public static extern bool EnumWindows(EnumWindowsProc enumProc, IntPtr lParam);
    public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);

    [DllImport("user32.dll")]
    public static extern bool IsWindowVisible(IntPtr hWnd);

    [DllImport("user32.dll")]
    public static extern int GetWindowLong(IntPtr hWnd, int nIndex);

    [StructLayout(LayoutKind.Sequential)]
    public struct RECT {
        public int Left, Top, Right, Bottom;
    }

    public class WindowEntry {
        public IntPtr Hwnd;
        public uint Pid;
        public string Title;
        public string ClassName;
        public RECT Bounds;
        public bool IsVisible;
        public int ExStyle;
    }

    public static List<WindowEntry> GetAllWindows() {
        var list = new List<WindowEntry>();
        EnumWindows((hWnd, lParam) => {
            RECT r;
            GetWindowRect(hWnd, out r);
            int w = r.Right - r.Left;
            int h = r.Bottom - r.Top;
            if (w > 0 && h > 0) {
                uint pid;
                GetWindowThreadProcessId(hWnd, out pid);
                var sbTitle = new StringBuilder(256);
                GetWindowText(hWnd, sbTitle, 256);
                var sbClass = new StringBuilder(256);
                GetClassName(hWnd, sbClass, 256);
                int exStyle = GetWindowLong(hWnd, -20);
                list.Add(new WindowEntry {
                    Hwnd = hWnd,
                    Pid = pid,
                    Title = sbTitle.ToString(),
                    ClassName = sbClass.ToString(),
                    Bounds = r,
                    IsVisible = IsWindowVisible(hWnd),
                    ExStyle = exStyle
                });
            }
            return true;
        }, IntPtr.Zero);
        return list;
    }
}
'@

Add-Type -AssemblyName System.Windows.Forms
foreach ($screen in [System.Windows.Forms.Screen]::AllScreens) {
    Write-Host "Screen: $($screen.DeviceName) Primary=$($screen.Primary) Bounds=$($screen.Bounds)"
}

Write-Host "`n=== TESTING WINDOW FROM POINT (Right Monitor: X=2560..4480, Y=60..1140) ==="
$testPoints = @(
    @{X=2600; Y=100},
    @{X=2700; Y=100},
    @{X=2800; Y=100},
    @{X=3000; Y=100},
    @{X=2600; Y=200},
    @{X=2600; Y=300},
    @{X=2600; Y=700},
    @{X=500;  Y=500}
)

foreach ($pt in $testPoints) {
    $hwnd = [WinAPI]::WindowFromPointSafe($pt.X, $pt.Y)
    $pidOut = 0
    [WinAPI]::GetWindowThreadProcessId($hwnd, [ref]$pidOut)
    $pName = (Get-Process -Id $pidOut -ErrorAction SilentlyContinue).ProcessName
    
    $sbTitle = New-Object System.Text.StringBuilder 256
    [WinAPI]::GetWindowText($hwnd, $sbTitle, 256) | Out-Null

    $sbClass = New-Object System.Text.StringBuilder 256
    [WinAPI]::GetClassName($hwnd, $sbClass, 256) | Out-Null

    $rect = New-Object WinAPI+RECT
    [WinAPI]::GetWindowRect($hwnd, [ref]$rect) | Out-Null

    # Root window
    $rootHwnd = [WinAPI]::GetAncestor($hwnd, 2)
    $rootPid = 0
    [WinAPI]::GetWindowThreadProcessId($rootHwnd, [ref]$rootPid)
    $rootPName = (Get-Process -Id $rootPid -ErrorAction SilentlyContinue).ProcessName
    $rootTitle = New-Object System.Text.StringBuilder 256
    [WinAPI]::GetWindowText($rootHwnd, $rootTitle, 256) | Out-Null

    Write-Host "Point ($($pt.X), $($pt.Y)) -> HWND $hwnd [PID $pidOut : $pName] Title='$($sbTitle.ToString())' Class='$($sbClass.ToString())' Rect=($($rect.Left),$($rect.Top),$($rect.Right),$($rect.Bottom)) | Root: HWND $rootHwnd [$rootPName] '$($rootTitle.ToString())'"
}

Write-Host "`n=== ALL VISIBLE WINDOWS OVERLAPPING RIGHT MONITOR (X >= 2560) ==="
$windows = [WinAPI]::GetAllWindows()
foreach ($w in $windows) {
    if ($w.IsVisible -and $w.Bounds.Right -gt 2560 -and $w.Bounds.Bottom -gt 60) {
        $pName = (Get-Process -Id $w.Pid -ErrorAction SilentlyContinue).ProcessName
        $isTopmost = ($w.ExStyle -band 0x00000008) -ne 0
        Write-Host "PID $($w.Pid) ($pName) HWND $($w.Hwnd) Topmost=$isTopmost Rect=($($w.Bounds.Left), $($w.Bounds.Top), $($w.Bounds.Right), $($w.Bounds.Bottom)) Title='$($w.Title)' Class='$($w.ClassName)'"
    }
}
