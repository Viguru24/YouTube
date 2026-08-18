using System;
using System.Runtime.InteropServices;
using System.Text;
using System.Diagnostics;
using System.Collections.Generic;

public class Program {
    [DllImport("user32.dll", SetLastError=true)]
    public static extern IntPtr OpenWindowStation(string lpszWinStation, bool fInherit, uint dwDesiredAccess);

    [DllImport("user32.dll", SetLastError=true)]
    public static extern bool SetProcessWindowStation(IntPtr hWinStation);

    [DllImport("user32.dll", SetLastError=true)]
    public static extern IntPtr OpenDesktop(string lpszDesktop, uint dwFlags, bool fInherit, uint dwDesiredAccess);

    [DllImport("user32.dll", SetLastError=true)]
    public static extern bool SetThreadDesktop(IntPtr hDesktop);

    [DllImport("user32.dll")]
    public static extern bool EnumDesktopWindows(IntPtr hDesktop, EnumWindowsProc enumProc, IntPtr lParam);
    public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);

    [DllImport("user32.dll")]
    public static extern bool IsWindowVisible(IntPtr hWnd);

    [DllImport("user32.dll")]
    public static extern int GetWindowText(IntPtr hWnd, StringBuilder lpString, int nMaxCount);

    [DllImport("user32.dll")]
    public static extern int GetClassName(IntPtr hWnd, StringBuilder lpClassName, int nMaxCount);

    [DllImport("user32.dll")]
    public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);

    [DllImport("user32.dll")]
    public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);

    [DllImport("user32.dll")]
    public static extern int GetWindowLong(IntPtr hWnd, int nIndex);

    [StructLayout(LayoutKind.Sequential)]
    public struct RECT {
        public int Left, Top, Right, Bottom;
    }

    public static void Main() {
        IntPtr hWinsta = OpenWindowStation("winsta0", false, 0x0000037F);
        if (hWinsta != IntPtr.Zero) SetProcessWindowStation(hWinsta);
        IntPtr hDesk = OpenDesktop("Default", 0, false, 0x000001FF);
        if (hDesk != IntPtr.Zero) SetThreadDesktop(hDesk);

        Console.WriteLine("=== DETAILED INSPECTION OF SUSPECT PROCESSES ===");
        var targetProcs = new HashSet<string>(StringComparer.OrdinalIgnoreCase) {
            "FloatingMenu", "DesktopParts", "cosmo-orb-net", "CosmoWhisperNative2", "msedgewebview2", "NVIDIA Overlay"
        };

        EnumDesktopWindows(hDesk, (hWnd, lParam) => {
            uint pid;
            GetWindowThreadProcessId(hWnd, out pid);
            string pName = "";
            try { pName = Process.GetProcessById((int)pid).ProcessName; } catch {}

            if (targetProcs.Contains(pName)) {
                RECT r;
                GetWindowRect(hWnd, out r);
                int w = r.Right - r.Left;
                int h = r.Bottom - r.Top;

                var sbTitle = new StringBuilder(256);
                GetWindowText(hWnd, sbTitle, 256);

                var sbClass = new StringBuilder(256);
                GetClassName(hWnd, sbClass, 256);

                int exStyle = GetWindowLong(hWnd, -20);
                int style = GetWindowLong(hWnd, -16);
                bool isTopmost = (exStyle & 0x00000008) != 0;
                bool isLayered = (exStyle & 0x00080000) != 0;
                bool isTransparent = (exStyle & 0x00000020) != 0;
                bool isVisible = IsWindowVisible(hWnd);

                Console.WriteLine(string.Format("PID {0,5} ({1,-20}) | HWND 0x{2:X8} | Vis={3,-5} Topmost={4,-5} Layered={5,-5} Trans={6,-5} | Rect: ({7,5},{8,5}) to ({9,5},{10,5}) [{11}x{12}] | Title: '{13}' | Class: '{14}'",
                    pid, pName, (long)hWnd, isVisible, isTopmost, isLayered, isTransparent, r.Left, r.Top, r.Right, r.Bottom, w, h, sbTitle.ToString(), sbClass.ToString()));
            }
            return true;
        }, IntPtr.Zero);
    }
}
