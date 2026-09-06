using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.Wpf;
using Newtonsoft.Json.Linq;

namespace VixzDesktop.Services
{
    /// <summary>
    /// Tracks active WebView2 iframes (such as YouTube player embed) to enable host-level script execution.
    /// </summary>
    public class WebViewFrameTracker
    {
        private readonly ConcurrentDictionary<uint, CoreWebView2Frame> _frames = new();

        public void Attach(CoreWebView2? coreWebView)
        {
            if (coreWebView == null) return;
            coreWebView.FrameCreated += (s, e) => TrackFrame(e.Frame);
        }

        private void TrackFrame(CoreWebView2Frame? frame)
        {
            if (frame == null) return;
            try
            {
                _frames[frame.FrameId] = frame;
                frame.Destroyed += (s, e) => _frames.TryRemove(frame.FrameId, out _);
                frame.FrameCreated += (s, e) => TrackFrame(e.Frame);
            }
            catch { }
        }

        public IEnumerable<CoreWebView2Frame> Frames => _frames.Values;
    }

    public static class ScreenshotService
    {
        public static string GetTargetDirectory(string? folderName = null)
        {
            var folder = (folderName ?? StorageService.Settings.ActiveScreenshotFolder).Trim();
            if (string.IsNullOrWhiteSpace(folder)) folder = "Default";

            // 1. If folder is already a fully qualified custom directory path, use it directly
            if (Path.IsPathRooted(folder) && Directory.Exists(folder))
            {
                return folder;
            }

            // 2. Check if the folder name has an explicit custom directory mapped
            if (StorageService.Settings.CustomFolderPaths != null &&
                StorageService.Settings.CustomFolderPaths.TryGetValue(folder, out var customMappedPath) &&
                !string.IsNullOrWhiteSpace(customMappedPath))
            {
                if (!Directory.Exists(customMappedPath))
                {
                    try { Directory.CreateDirectory(customMappedPath); } catch { }
                }
                if (Directory.Exists(customMappedPath)) return customMappedPath;
            }

            // 3. Check if global custom screenshot path is active and valid
            if (!string.IsNullOrWhiteSpace(StorageService.Settings.CustomScreenshotPath) &&
                Directory.Exists(StorageService.Settings.CustomScreenshotPath))
            {
                // Only return global custom path if active folder is currently pointing to it
                var customRootName = Path.GetFileName(StorageService.Settings.CustomScreenshotPath);
                if (folder.Equals(customRootName, StringComparison.OrdinalIgnoreCase) ||
                    folder.Equals(StorageService.Settings.ActiveScreenshotFolder, StringComparison.OrdinalIgnoreCase))
                {
                    return StorageService.Settings.CustomScreenshotPath;
                }
            }

            // 4. Default: User's Pictures/Vixz/(Subfolder)
            var picturesDir = Environment.GetFolderPath(Environment.SpecialFolder.MyPictures);
            var targetDir = (folder.Equals("Default", StringComparison.OrdinalIgnoreCase) || folder.Equals("Screenshots", StringComparison.OrdinalIgnoreCase))
                ? Path.Combine(picturesDir, "Vixz")
                : Path.Combine(picturesDir, "Vixz", folder);

            if (!Directory.Exists(targetDir))
            {
                try { Directory.CreateDirectory(targetDir); } catch { }
            }

            return targetDir;
        }

        public static Task<string?> CaptureAndSaveAsync(
            WebView2? webView,
            FrameworkElement? visualFallback,
            string videoTitle,
            double positionSeconds,
            string? targetFolder = null)
        {
            return CaptureAndSaveAsync(webView, null, visualFallback, videoTitle, positionSeconds, targetFolder);
        }

        public static async Task<string?> CaptureAndSaveAsync(
            WebView2? webView,
            IEnumerable<CoreWebView2Frame>? activeFrames,
            FrameworkElement? visualFallback,
            string videoTitle,
            double positionSeconds,
            string? targetFolder = null)
        {
            try
            {
                var directory = GetTargetDirectory(targetFolder);
                var safeTitle = Regex.Replace(videoTitle, @"[^a-zA-Z0-9_-]", "_");
                if (safeTitle.Length > 30) safeTitle = safeTitle.Substring(0, 30);
                safeTitle = safeTitle.Trim('_');
                if (string.IsNullOrWhiteSpace(safeTitle)) safeTitle = "Video";

                var timeStr = TimeSpan.FromSeconds(positionSeconds);
                var formattedTime = $"{timeStr.Minutes:D2}m{timeStr.Seconds:D2}s";
                var dateStamp = DateTime.Now.ToString("yyyyMMdd_HHmmss");
                var fileName = $"Vixz_{safeTitle}_{formattedTime}_{dateStamp}.jpg";
                var fullPath = Path.Combine(directory, fileName);

                // 1. Hardware-accelerated WebView2 Capture Preview with clean overlay removal & video cropping
                if (webView?.CoreWebView2 != null)
                {
                    double winW = 0, winH = 0;
                    double iframeX = 0, iframeY = 0;
                    bool isLocal = false;
                    double vx = 0, vy = 0, vw = 0, vh = 0;
                    double videoWidth = 0, videoHeight = 0;
                    bool foundVideo = false;

                    // A. Prepare host page: temporarily hide ambient glow, player shadows, controls on local video
                    try
                    {
                        var topInitScript = @"
(() => {
    try {
        var glow = document.getElementById('ambient-glow-container');
        if (glow) glow.style.visibility = 'hidden';
        var wrap = document.getElementById('player-wrapper');
        if (wrap) wrap.style.boxShadow = 'none';
        var localVid = document.getElementById('local-video-player');
        if (localVid) localVid.controls = false;

        var winW = window.innerWidth || document.documentElement.clientWidth || 1;
        var winH = window.innerHeight || document.documentElement.clientHeight || 1;

        if (localVid && localVid.style.display !== 'none') {
            var lr = localVid.getBoundingClientRect();
            return JSON.stringify({
                isLocal: true,
                winW: winW,
                winH: winH,
                x: lr.left,
                y: lr.top,
                width: lr.width,
                height: lr.height,
                videoWidth: localVid.videoWidth || 0,
                videoHeight: localVid.videoHeight || 0
            });
        }

        var iframe = document.getElementById('fallback-yt-frame') || document.querySelector('iframe');
        var ir = iframe ? iframe.getBoundingClientRect() : { left: 0, top: 0, width: winW, height: winH };
        return JSON.stringify({
            isLocal: false,
            winW: winW,
            winH: winH,
            iframeX: ir.left,
            iframeY: ir.top,
            iframeWidth: ir.width,
            iframeHeight: ir.height
        });
    } catch(e) {
        return null;
    }
})()";
                        var topResStr = await webView.CoreWebView2.ExecuteScriptAsync(topInitScript);
                        if (!string.IsNullOrWhiteSpace(topResStr) && topResStr != "null")
                        {
                            var parsed = JToken.Parse(topResStr);
                            var jobj = parsed.Type == JTokenType.String ? JObject.Parse(parsed.ToString()) : (JObject)parsed;
                            isLocal = jobj.Value<bool>("isLocal");
                            winW = jobj.Value<double>("winW");
                            winH = jobj.Value<double>("winH");

                            if (isLocal)
                            {
                                vx = jobj.Value<double>("x");
                                vy = jobj.Value<double>("y");
                                vw = jobj.Value<double>("width");
                                vh = jobj.Value<double>("height");
                                videoWidth = jobj.Value<double>("videoWidth");
                                videoHeight = jobj.Value<double>("videoHeight");
                                foundVideo = vw > 5 && vh > 5;
                            }
                            else
                            {
                                iframeX = jobj.Value<double>("iframeX");
                                iframeY = jobj.Value<double>("iframeY");
                            }
                        }
                    }
                    catch (Exception ex)
                    {
                        Debug.WriteLine($"Error preparing host page for snapshot: {ex.Message}");
                    }

                    var injectedFrames = new List<CoreWebView2Frame>();

                    // B. If not local video, inject clean CSS into YouTube player frame(s) to hide all overlays and locate video element
                    if (!isLocal && activeFrames != null)
                    {
                        var frameCleanScript = @"
(() => {
    try {
        var styleId = 'vixz-clean-snapshot-style';
        var existing = document.getElementById(styleId);
        if (!existing) {
            var s = document.createElement('style');
            s.id = styleId;
            s.textContent = `
                .html5-video-player > *:not(.html5-video-container) { display: none !important; opacity: 0 !important; visibility: hidden !important; }
                .ytp-chrome-top, .ytp-chrome-bottom, .ytp-gradient-top, .ytp-gradient-bottom,
                .ytp-bezel, .ytp-bezel-text, .ytp-bezel-icon, div[class*='bezel'],
                .ytp-pause-overlay, .ytp-pause-overlay-container, .ytp-scroll-min,
                .ytp-ce-element, .ytp-ce-covering-overlay, .ytp-ce-element-show, .ytp-ce-video, .ytp-ce-channel,
                .ytp-cards-teaser, .ytp-cards-button,
                .ytp-watermark, .annotation,
                .ytp-paid-content-overlay, .ytp-suggested-action-badge,
                .ytp-spinner, .ytp-contextmenu, .ytp-tooltip,
                .ytp-cued-thumbnail-overlay, .ytp-upnext, .ytp-iv-video-content, .ytp-iv-drawer,
                .ytp-caption-window-container, .caption-window, .ytp-subtitles-player-content {
                    display: none !important;
                    opacity: 0 !important;
                    visibility: hidden !important;
                }
            `;
            (document.head || document.documentElement).appendChild(s);
        }
        var v = document.querySelector('video');
        if (!v) return null;
        var r = v.getBoundingClientRect();
        return JSON.stringify({
            x: r.left,
            y: r.top,
            width: r.width,
            height: r.height,
            videoWidth: v.videoWidth || 0,
            videoHeight: v.videoHeight || 0
        });
    } catch(e) {
        return null;
    }
})()";

                        foreach (var frame in activeFrames)
                        {
                            try
                            {
                                if (frame.IsDestroyed() != 0) continue;
                                var resStr = await frame.ExecuteScriptAsync(frameCleanScript);
                                injectedFrames.Add(frame);

                                if (!foundVideo && !string.IsNullOrWhiteSpace(resStr) && resStr != "null")
                                {
                                    var parsed = JToken.Parse(resStr);
                                    var jobj = parsed.Type == JTokenType.String ? JObject.Parse(parsed.ToString()) : (JObject)parsed;
                                    var fw = jobj.Value<double>("width");
                                    var fh = jobj.Value<double>("height");
                                    if (fw > 5 && fh > 5)
                                    {
                                        vx = iframeX + jobj.Value<double>("x");
                                        vy = iframeY + jobj.Value<double>("y");
                                        vw = fw;
                                        vh = fh;
                                        videoWidth = jobj.Value<double>("videoWidth");
                                        videoHeight = jobj.Value<double>("videoHeight");
                                        foundVideo = true;
                                    }
                                }
                            }
                            catch { }
                        }
                    }

                    // C. Wait for compositor to flush the cleanly styled frame
                    await Task.Delay(40);

                    // D. Capture preview image to memory stream
                    using var memStream = new MemoryStream();
                    await webView.CoreWebView2.CapturePreviewAsync(CoreWebView2CapturePreviewImageFormat.Jpeg, memStream);

                    // E. Restore all frames and top page immediately so playback UI returns
                    _ = Task.Run(async () =>
                    {
                        try
                        {
                            foreach (var f in injectedFrames)
                            {
                                try
                                {
                                    if (f.IsDestroyed() == 0)
                                    {
                                        await f.ExecuteScriptAsync("try { var s = document.getElementById('vixz-clean-snapshot-style'); if (s) s.remove(); } catch(e) {}");
                                    }
                                }
                                catch { }
                            }

                            await webView.Dispatcher.InvokeAsync(async () =>
                            {
                                try
                                {
                                    await webView.CoreWebView2.ExecuteScriptAsync(@"
(() => {
    try {
        var glow = document.getElementById('ambient-glow-container');
        if (glow) glow.style.visibility = 'visible';
        var wrap = document.getElementById('player-wrapper');
        if (wrap) wrap.style.boxShadow = '';
        var localVid = document.getElementById('local-video-player');
        if (localVid) localVid.controls = true;
    } catch(e) {}
})()");
                                }
                                catch { }
                            });
                        }
                        catch { }
                    });

                    // F. Decode captured preview and crop letterboxing / black bars
                    memStream.Position = 0;
                    var decoder = BitmapDecoder.Create(memStream, BitmapCreateOptions.PreservePixelFormat, BitmapCacheOption.OnLoad);
                    var sourceFrame = decoder.Frames[0];
                    int imgW = sourceFrame.PixelWidth;
                    int imgH = sourceFrame.PixelHeight;

                    BitmapSource finalBitmap = sourceFrame;

                    if (foundVideo && winW > 0 && winH > 0 && vw > 5 && vh > 5)
                    {
                        // Calculate letterbox/pillarbox adjustment if intrinsic resolution is known
                        if (videoWidth > 0 && videoHeight > 0)
                        {
                            double videoAspect = videoWidth / videoHeight;
                            double renderedAspect = vw / vh;
                            if (renderedAspect > videoAspect + 0.01)
                            {
                                // Pillarbox: black bars on left/right
                                double actualW = vh * videoAspect;
                                vx += (vw - actualW) / 2.0;
                                vw = actualW;
                            }
                            else if (renderedAspect < videoAspect - 0.01)
                            {
                                // Letterbox: black bars on top/bottom
                                double actualH = vw / videoAspect;
                                vy += (vh - actualH) / 2.0;
                                vh = actualH;
                            }
                        }

                        double scaleX = (double)imgW / winW;
                        double scaleY = (double)imgH / winH;

                        int cropX = (int)Math.Round(vx * scaleX);
                        int cropY = (int)Math.Round(vy * scaleY);
                        int cropW = (int)Math.Round(vw * scaleX);
                        int cropH = (int)Math.Round(vh * scaleY);

                        cropX = Math.Max(0, Math.Min(cropX, imgW - 1));
                        cropY = Math.Max(0, Math.Min(cropY, imgH - 1));
                        cropW = Math.Max(1, Math.Min(cropW, imgW - cropX));
                        cropH = Math.Max(1, Math.Min(cropH, imgH - cropY));

                        if (cropW > 20 && cropH > 20 && (cropW < imgW || cropH < imgH))
                        {
                            try
                            {
                                finalBitmap = new CroppedBitmap(sourceFrame, new Int32Rect(cropX, cropY, cropW, cropH));
                            }
                            catch (Exception cropEx)
                            {
                                Debug.WriteLine($"CroppedBitmap failed: {cropEx.Message}, saving uncropped");
                            }
                        }
                    }

                    // Save as pristine high-quality JPEG
                    var encoder = new JpegBitmapEncoder { QualityLevel = 98 };
                    encoder.Frames.Add(BitmapFrame.Create(finalBitmap));
                    using (var fs = new FileStream(fullPath, FileMode.Create, FileAccess.Write))
                    {
                        encoder.Save(fs);
                    }

                    return fullPath;
                }

                // 2. Fallback: WPF RenderTargetBitmap Visual capture
                if (visualFallback != null && visualFallback.ActualWidth > 0 && visualFallback.ActualHeight > 0)
                {
                    var rtb = new RenderTargetBitmap(
                        (int)visualFallback.ActualWidth,
                        (int)visualFallback.ActualHeight,
                        96, 96,
                        PixelFormats.Pbgra32
                    );
                    rtb.Render(visualFallback);

                    var encoder = new JpegBitmapEncoder { QualityLevel = 95 };
                    encoder.Frames.Add(BitmapFrame.Create(rtb));

                    using var fs = new FileStream(fullPath, FileMode.Create, FileAccess.Write);
                    encoder.Save(fs);
                    return fullPath;
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"Screenshot capture failed: {ex.Message}");
            }

            return null;
        }

        public static void OpenFolderInExplorer(string? folderName = null)
        {
            try
            {
                var dir = GetTargetDirectory(folderName);
                Process.Start(new ProcessStartInfo
                {
                    FileName = dir,
                    UseShellExecute = true
                });
            }
            catch { }
        }
    }
}
