using System;
using System.IO;
using System.Windows;

namespace VixzDesktop
{
    public partial class App : Application
    {
        protected override void OnStartup(StartupEventArgs e)
        {
            try
            {
                var currentPid = System.Diagnostics.Process.GetCurrentProcess().Id;
                var existingProcesses = System.Diagnostics.Process.GetProcessesByName("VixzDesktop");
                foreach (var p in existingProcesses)
                {
                    if (p.Id != currentPid)
                    {
                        try
                        {
                            p.Kill();
                            p.WaitForExit(800);
                        }
                        catch { }
                    }
                }
            }
            catch { }

            base.OnStartup(e);

            AppDomain.CurrentDomain.UnhandledException += (s, args) =>
            {
                LogException("AppDomain.UnhandledException", args.ExceptionObject as Exception);
            };

            DispatcherUnhandledException += (s, args) =>
            {
                LogException("DispatcherUnhandledException", args.Exception);
                args.Handled = true;
            };
        }

        private static void LogException(string source, Exception? ex)
        {
            try
            {
                var folder = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VixzDesktop");
                Directory.CreateDirectory(folder);
                var logFile = Path.Combine(folder, "error.log");
                var text = $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}] [{source}]\n{ex?.ToString()}\n\n";
                File.AppendAllText(logFile, text);
            }
            catch { }
        }
    }
}
