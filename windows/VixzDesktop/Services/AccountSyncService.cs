using System;
using System.Diagnostics;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using Microsoft.Web.WebView2.Core;
using Newtonsoft.Json.Linq;
using VixzDesktop.Models;

namespace VixzDesktop.Services
{
    public static class AccountSyncService
    {
        public const string AccountExtractionScript = @"
(function() {
    var result = { name: '', email: '', avatar: '', handle: '', debug: [] };

    function log(msg) {
        try { result.debug.push(String(msg)); } catch(e) {}
    }

    try {
        log('URL: ' + (window.location ? window.location.href : 'none'));
        log('Title: ' + (document.title || ''));

        // 1. Check window.ytInitialData (instantaneous in HTML)
        try {
            if (window.ytInitialData) {
                var str = JSON.stringify(window.ytInitialData);
                log('ytInitialData length: ' + str.length);

                // Look for email pattern anywhere in ytInitialData
                var emailMatches = str.match(/([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,6})/g);
                if (emailMatches && emailMatches.length > 0) {
                    for (var em of emailMatches) {
                        if (!em.includes('google.com') && !em.includes('youtube.com') || em.includes('@gmail.com')) {
                            result.email = em;
                            log('Matched email: ' + em);
                            break;
                        }
                    }
                }

                // Check desktopTopbarRenderer topbarButtons
                var buttons = window.ytInitialData?.topbar?.desktopTopbarRenderer?.topbarButtons;
                if (buttons && Array.isArray(buttons)) {
                    log('Buttons count: ' + buttons.length);
                    for (var i = 0; i < buttons.length; i++) {
                        var b = buttons[i]?.topbarMenuButtonRenderer;
                        if (b) {
                            var lbl = b.accessibility?.accessibilityData?.label || '';
                            log('Button ' + i + ' label: ' + lbl);
                            var m = lbl.match(/Google Account:\s*([^\n\(]+)(?:\s*\(([^)]+)\))?/i) ||
                                    lbl.match(/([^\n\(]+)\s*\(([^)]+@[^)]+)\)/i) ||
                                    lbl.match(/Account:\s*([^\n\(]+)/i);
                            if (m) {
                                if (!result.name && m[1]) result.name = m[1].trim();
                                if (!result.email && m[2]) result.email = m[2].trim();
                            }
                            var thumbs = b.avatar?.thumbnails;
                            if (thumbs && thumbs.length > 0) {
                                result.avatar = thumbs[thumbs.length - 1].url;
                                log('Found avatar in topbarButtons: ' + result.avatar);
                            }
                        }
                    }
                }
            } else {
                log('window.ytInitialData is not defined');
            }
        } catch(eInit) { log('ytInitialData search error: ' + eInit.message); }

        // 2. DOM extraction: Topbar avatar button aria-label & image
        try {
            var avatarBtn = document.querySelector('#avatar-btn, button#avatar-btn, ytd-topbar-menu-button-renderer button, yt-img-shadow#avatar');
            if (avatarBtn) {
                log('Found avatarBtn element in DOM');
                var label = avatarBtn.getAttribute('aria-label') || avatarBtn.closest('ytd-topbar-menu-button-renderer')?.getAttribute('aria-label') || '';
                log('DOM avatarBtn aria-label: ' + label);
                var match = label.match(/Google Account:\s*([^\n\(]+)(?:\s*\(([^)]+)\))?/i) ||
                            label.match(/([^\n\(]+)\s*\(([^)]+@[^)]+)\)/i);
                if (match) {
                    if (!result.name && match[1]) result.name = match[1].trim();
                    if (!result.email && match[2]) result.email = match[2].trim();
                }
                var img = avatarBtn.querySelector('img') || avatarBtn.closest('button')?.querySelector('img') || avatarBtn.closest('ytd-topbar-menu-button-renderer')?.querySelector('img');
                if (img && img.src && !result.avatar && !img.src.includes('data:image')) {
                    result.avatar = img.src;
                    log('Found DOM avatar img: ' + img.src);
                }
            } else {
                log('avatarBtn not found in DOM');
            }
        } catch(eDom) { log('DOM search error: ' + eDom.message); }

        // 3. Scan all images on page for profile avatars (googleusercontent / yt3)
        if (!result.avatar) {
            try {
                var imgs = document.querySelectorAll('img');
                for (var j = 0; j < imgs.length; j++) {
                    var src = imgs[j].src || '';
                    if ((src.includes('googleusercontent.com') || src.includes('yt3.ggpht.com')) && 
                        (src.includes('=s') || src.includes('/photo.jpg') || src.includes('-c-k-c0x'))) {
                        result.avatar = src;
                        log('Found avatar via image scan: ' + src);
                        var alt = imgs[j].alt || imgs[j].getAttribute('aria-label') || '';
                        if (alt && !result.name && !alt.toLowerCase().includes('avatar') && !alt.toLowerCase().includes('picture')) {
                            result.name = alt.trim();
                        }
                        break;
                    }
                }
            } catch(eImg) { log('Image scan error: ' + eImg.message); }
        }

        // 4. Account page DOM selectors (if on /account or myaccount)
        try {
            var nameEl = document.querySelector('#channel-name, #channel-title, yt-formatted-string.ytd-account-item-renderer, [data-email]');
            if (nameEl) {
                var txt = nameEl.textContent ? nameEl.textContent.trim() : '';
                if (txt && !result.name) result.name = txt;
                var attrEmail = nameEl.getAttribute('data-email');
                if (attrEmail && !result.email) result.email = attrEmail;
            }
            var handleEl = document.querySelector('#channel-handle');
            if (handleEl && !result.handle) result.handle = handleEl.textContent ? handleEl.textContent.trim() : '';

            // Google MyAccount page structure
            var welcomeEl = document.querySelector('h1, [data-profile-name], .Wgg1ne, .gb_d');
            if (welcomeEl) {
                var wText = welcomeEl.textContent ? welcomeEl.textContent.trim() : '';
                var wm = wText.match(/Welcome,\s*([^!.]+)/i);
                if (wm && wm[1]) {
                    result.name = wm[1].trim();
                    log('Found name in Welcome header: ' + result.name);
                }
            }

            var gEmail = document.querySelector('.gb_d, [aria-label*=""@""]');
            if (gEmail && !result.email) {
                var gm = (gEmail.textContent || gEmail.getAttribute('aria-label') || '').match(/([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,6})/);
                if (gm) result.email = gm[1];
            }
        } catch(eAcc) { log('Account DOM error: ' + eAcc.message); }

    } catch(err) {
        log('Fatal script error: ' + err.toString());
    }

    return JSON.stringify(result);
})();";

        /// <summary>
        /// Executes profile discovery and updates stored UserAccount if valid credentials exist.
        /// </summary>
        public static async Task<UserAccount?> ExtractAndUpdateAccountAsync(CoreWebView2 core)
        {
            if (core == null) return null;

            try
            {
                var rawResult = await core.ExecuteScriptAsync(AccountExtractionScript);
                
                // Write full script output to log for diagnostic traceability
                try
                {
                    var logFile = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VixzDesktop", "account_sync.log");
                    System.IO.File.AppendAllText(logFile, $"[{DateTime.Now}] Script output: {rawResult}\n");
                }
                catch { }

                if (string.IsNullOrWhiteSpace(rawResult) || rawResult == "null") return null;

                var parsed = JToken.Parse(rawResult);
                var jobj = parsed.Type == JTokenType.String ? JObject.Parse(parsed.ToString()) : (JObject)parsed;

                var name = jobj.Value<string>("name")?.Trim() ?? "";
                var email = jobj.Value<string>("email")?.Trim() ?? "";
                var avatar = jobj.Value<string>("avatar")?.Trim() ?? "";
                var handle = jobj.Value<string>("handle")?.Trim() ?? "";

                // Determine best display name
                string displayName = "";
                if (!string.IsNullOrWhiteSpace(name) && !name.Equals("Google User", StringComparison.OrdinalIgnoreCase))
                {
                    displayName = name;
                }
                else if (!string.IsNullOrWhiteSpace(handle))
                {
                    displayName = handle;
                }
                else if (!string.IsNullOrWhiteSpace(email))
                {
                    var prefix = email.Split('@')[0];
                    var alphaOnly = Regex.Replace(prefix, @"\d+$", "");
                    if (alphaOnly.Length >= 3)
                    {
                        displayName = char.ToUpper(alphaOnly[0]) + alphaOnly.Substring(1);
                    }
                    else
                    {
                        displayName = email;
                    }
                }

                if (string.IsNullOrWhiteSpace(displayName) && string.IsNullOrWhiteSpace(email) && string.IsNullOrWhiteSpace(avatar))
                {
                    return null;
                }

                var existing = StorageService.Settings.UserAccount ?? new UserAccount();
                existing.IsSignedIn = true;
                if (!string.IsNullOrWhiteSpace(displayName)) existing.DisplayName = displayName;
                if (!string.IsNullOrWhiteSpace(email)) existing.Email = email;
                if (!string.IsNullOrWhiteSpace(avatar)) existing.AvatarUrl = avatar;
                existing.LastSyncTime = DateTime.UtcNow;

                StorageService.SetUserAccount(existing);
                return existing;
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"Account sync extraction failed: {ex.Message}");
                return null;
            }
        }
    }
}
