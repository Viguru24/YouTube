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

    function cleanLine(val) {
        if (!val) return '';
        var first = String(val).split(/[\r\n]+/)[0].replace(/\s+/g, ' ').trim();
        if (!first) return '';
        if (/^(account menu|google account|google user|sign in|youtube|avatar|profile picture)$/i.test(first)) return '';
        if (first.indexOf('\u2022') !== -1 || first.indexOf('\u00B7') !== -1 || first.indexOf('\uFFFD') !== -1) return '';
        return first;
    }

    function findAccountRenderer(obj, depth, isAccountPage) {
        if (!obj || typeof obj !== 'object' || depth > 12) return;
        if (obj.activeAccountHeaderRenderer) {
            var hdr = obj.activeAccountHeaderRenderer;
            var nm = hdr.accountName?.simpleText || (hdr.accountName?.runs && hdr.accountName.runs[0]?.text) || '';
            var em = hdr.email?.simpleText || (hdr.email?.runs && hdr.email.runs[0]?.text) || '';
            var hd = hdr.channelHandle?.simpleText || (hdr.channelHandle?.runs && hdr.channelHandle.runs[0]?.text) || '';
            var th = hdr.accountPhoto?.thumbnails;
            if (!result.name && cleanLine(nm)) { result.name = cleanLine(nm); log('Found name in activeAccountHeaderRenderer: ' + result.name); }
            if (!result.email && em && em.includes('@')) { result.email = em.trim(); log('Found email in activeAccountHeaderRenderer: ' + result.email); }
            if (!result.handle && cleanLine(hd)) { result.handle = cleanLine(hd); }
            if (!result.avatar && th && th.length > 0) { result.avatar = th[th.length - 1].url; }
        }
        if (obj.accountItemRenderer) {
            var item = obj.accountItemRenderer;
            if (item.isSelected || !result.name) {
                var inm = item.accountName?.simpleText || (item.accountName?.runs && item.accountName.runs[0]?.text) || '';
                var ihd = item.channelHandle?.simpleText || (item.channelHandle?.runs && item.channelHandle.runs[0]?.text) || '';
                var ith = item.accountPhoto?.thumbnails;
                if (cleanLine(inm)) { result.name = cleanLine(inm); log('Found name in accountItemRenderer: ' + result.name); }
                if (!result.handle && cleanLine(ihd)) { result.handle = cleanLine(ihd); }
                if (!result.avatar && ith && ith.length > 0) { result.avatar = ith[ith.length - 1].url; }
            }
        }
        if (obj.accountChannelRenderer) {
            var ch = obj.accountChannelRenderer;
            var cnm = ch.title?.simpleText || (ch.title?.runs && ch.title.runs[0]?.text) || '';
            var cth = ch.thumbnail?.thumbnails;
            if (!result.name && cleanLine(cnm)) { result.name = cleanLine(cnm); log('Found name in accountChannelRenderer: ' + result.name); }
            if (!result.avatar && cth && cth.length > 0) { result.avatar = cth[cth.length - 1].url; }
        }
        if (Array.isArray(obj)) {
            for (var i = 0; i < obj.length; i++) findAccountRenderer(obj[i], depth + 1, isAccountPage);
        } else {
            var keys = Object.keys(obj);
            for (var k = 0; k < keys.length; k++) {
                var key = keys[k];
                // Skip video feed/watch contents so we never inspect video cards or descriptions
                if (key === 'richGridRenderer' || key === 'twoColumnWatchNextResults' || (key === 'twoColumnBrowseResultsRenderer' && !isAccountPage)) continue;
                findAccountRenderer(obj[key], depth + 1, isAccountPage);
            }
        }
    }

    try {
        var href = window.location ? window.location.href : '';
        var isYoutubeAccountPage = /youtube\.com\/account(\b|\/|\?|$)/i.test(href);
        var isGoogleAccountPage = /(myaccount|accounts)\.google\.com/i.test(href);

        log('URL: ' + (href || 'none'));
        log('Title: ' + (document.title || ''));

        // 1. Check window.ytInitialData (structured account/topbar fields only)
        try {
            if (window.ytInitialData) {
                findAccountRenderer(window.ytInitialData, 0, isYoutubeAccountPage);

                // Only scan raw ytInitialData for email on dedicated /account page (never on feed/watch pages where creator emails appear)
                if (isYoutubeAccountPage && !result.email) {
                    var str = JSON.stringify(window.ytInitialData);
                    log('ytInitialData (/account) length: ' + str.length);
                    var emailMatches = str.match(/([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,6})/g);
                    if (emailMatches && emailMatches.length > 0) {
                        for (var em of emailMatches) {
                            if ((!em.includes('google.com') && !em.includes('youtube.com')) || em.includes('@gmail.com')) {
                                result.email = em;
                                log('Matched email on /account: ' + em);
                                break;
                            }
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
                                    lbl.match(/^Account:\s*([^\n\(]+)/i);
                            if (m) {
                                if (!result.name && cleanLine(m[1])) result.name = cleanLine(m[1]);
                                if (!result.email && m[2] && m[2].includes('@')) result.email = m[2].trim();
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

        // 2. DOM extraction: Strictly scoped to Topbar masthead avatar button
        try {
            var avatarBtn = document.querySelector('#masthead-container #avatar-btn, ytd-masthead button#avatar-btn, ytd-masthead ytd-topbar-menu-button-renderer button');
            if (avatarBtn) {
                log('Found masthead avatarBtn element in DOM');
                var label = avatarBtn.getAttribute('aria-label') || avatarBtn.closest('ytd-topbar-menu-button-renderer')?.getAttribute('aria-label') || '';
                log('DOM avatarBtn aria-label: ' + label);
                var match = label.match(/Google Account:\s*([^\n\(]+)(?:\s*\(([^)]+)\))?/i) ||
                            label.match(/([^\n\(]+)\s*\(([^)]+@[^)]+)\)/i);
                if (match) {
                    if (!result.name && cleanLine(match[1])) result.name = cleanLine(match[1]);
                    if (!result.email && match[2] && match[2].includes('@')) result.email = match[2].trim();
                }
                var img = avatarBtn.querySelector('img') || avatarBtn.closest('button')?.querySelector('img') || avatarBtn.closest('ytd-topbar-menu-button-renderer')?.querySelector('img');
                if (img && img.src && !result.avatar && !img.src.includes('data:image')) {
                    result.avatar = img.src;
                    log('Found DOM avatar img: ' + img.src);
                }
            } else {
                log('masthead avatarBtn not found in DOM');
            }
        } catch(eDom) { log('DOM search error: ' + eDom.message); }

        // 3. Fallback avatar scan: Strictly scoped to masthead or dedicated account pages (never video feed cards)
        if (!result.avatar) {
            try {
                var imgScope = (isYoutubeAccountPage || isGoogleAccountPage)
                    ? document.querySelectorAll('img')
                    : document.querySelectorAll('#masthead-container img, ytd-masthead img, header img');
                for (var j = 0; j < imgScope.length; j++) {
                    var src = imgScope[j].src || '';
                    if ((src.includes('googleusercontent.com') || src.includes('yt3.ggpht.com')) &&
                        (src.includes('=s') || src.includes('/photo.jpg') || src.includes('-c-k-c0x'))) {
                        result.avatar = src;
                        log('Found avatar via scoped image scan: ' + src);
                        break;
                    }
                }
            } catch(eImg) { log('Image scan error: ' + eImg.message); }
        }

        // 4. Account page DOM selectors — ONLY run on youtube.com/account or Google Account pages!
        //    (On youtube.com Home/Watch pages, #channel-name belongs to video/movie cards!)
        if (isYoutubeAccountPage) {
            try {
                var nameEl = document.querySelector('ytd-account-item-renderer #channel-title, ytd-account-settings #name, yt-formatted-string.ytd-account-item-renderer, [data-email]');
                if (nameEl) {
                    var txt = cleanLine(nameEl.textContent);
                    if (txt && !result.name) result.name = txt;
                    var attrEmail = nameEl.getAttribute('data-email');
                    if (attrEmail && !result.email) result.email = attrEmail.trim();
                }
                var handleEl = document.querySelector('ytd-account-item-renderer #channel-handle, #channel-handle');
                if (handleEl && !result.handle) result.handle = cleanLine(handleEl.textContent);
            } catch(eAcc) { log('YouTube /account DOM error: ' + eAcc.message); }
        }

        if (isGoogleAccountPage) {
            try {
                var welcomeEl = document.querySelector('h1, [data-profile-name], .Wgg1ne, .gb_d');
                if (welcomeEl) {
                    var wText = welcomeEl.textContent ? welcomeEl.textContent.trim() : '';
                    var wm = wText.match(/Welcome,\s*([^!.\r\n]+)/i);
                    if (wm && cleanLine(wm[1])) {
                        result.name = cleanLine(wm[1]);
                        log('Found name in Welcome header: ' + result.name);
                    }
                }

                var gEmail = document.querySelector('[data-email], .gb_d, [aria-label*=""@""]');
                if (gEmail && !result.email) {
                    var gm = (gEmail.getAttribute('data-email') || gEmail.textContent || gEmail.getAttribute('aria-label') || '')
                        .match(/([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,6})/);
                    if (gm) result.email = gm[1];
                }
            } catch(eGAcc) { log('Google Account DOM error: ' + eGAcc.message); }
        }

    } catch(err) {
        log('Fatal script error: ' + err.toString());
    }

    return JSON.stringify(result);
})();";

        /// <summary>
        /// Sanitizes a scraped or stored display name, rejecting multi-line or feed metadata strings
        /// (such as "Action &amp; adventure • 1984") and falling back cleanly when invalid.
        /// </summary>
        public static string SanitizeDisplayName(string? rawName)
        {
            if (string.IsNullOrWhiteSpace(rawName)) return "";

            var firstLine = rawName.Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries)[0];
            firstLine = Regex.Replace(firstLine, @"\s+", " ").Trim();

            if (string.IsNullOrWhiteSpace(firstLine) || firstLine.Length > 60)
                return "";

            // Reject YouTube UI chrome labels and movie/video metadata badges (e.g., "Action & adventure • 1984")
            if (firstLine.Equals("Google User", StringComparison.OrdinalIgnoreCase) ||
                firstLine.Equals("Account menu", StringComparison.OrdinalIgnoreCase) ||
                firstLine.Equals("Sign in", StringComparison.OrdinalIgnoreCase) ||
                firstLine.Equals("YouTube", StringComparison.OrdinalIgnoreCase) ||
                firstLine.Contains('•') ||
                firstLine.Contains('·') ||
                firstLine.Contains('\uFFFD') ||
                Regex.IsMatch(firstLine, @"\b(Action & adventure|Comedy|Drama|Thriller|Horror|Sci-fi|Documentary)\b", RegexOptions.IgnoreCase))
            {
                return "";
            }

            return firstLine;
        }

        public static string DeriveDisplayNameFromEmail(string? email)
        {
            if (string.IsNullOrWhiteSpace(email) || !email.Contains('@')) return "";
            var prefix = email.Split('@')[0];
            var alphaOnly = Regex.Replace(prefix, @"\d+$", "");
            if (alphaOnly.Length >= 3)
            {
                return char.ToUpper(alphaOnly[0]) + alphaOnly.Substring(1);
            }
            return email.Trim();
        }

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

                var name = SanitizeDisplayName(jobj.Value<string>("name"));
                var email = jobj.Value<string>("email")?.Trim() ?? "";
                var avatar = jobj.Value<string>("avatar")?.Trim() ?? "";
                var handle = SanitizeDisplayName(jobj.Value<string>("handle"));

                var existing = StorageService.Settings.UserAccount ?? new UserAccount();
                var effectiveEmail = !string.IsNullOrWhiteSpace(email) ? email : existing.Email;
                var existingCleanName = SanitizeDisplayName(existing.DisplayName);

                // Determine best display name without overwriting a valid stored name with empty fallback
                string displayName = "";
                if (!string.IsNullOrWhiteSpace(name))
                {
                    displayName = name;
                }
                else if (!string.IsNullOrWhiteSpace(handle))
                {
                    displayName = handle;
                }
                else if (!string.IsNullOrWhiteSpace(existingCleanName))
                {
                    displayName = existingCleanName;
                }
                else if (!string.IsNullOrWhiteSpace(effectiveEmail))
                {
                    displayName = DeriveDisplayNameFromEmail(effectiveEmail);
                }

                if (string.IsNullOrWhiteSpace(displayName) && string.IsNullOrWhiteSpace(email) && string.IsNullOrWhiteSpace(avatar))
                {
                    return null;
                }

                existing.IsSignedIn = true;
                existing.DisplayName = !string.IsNullOrWhiteSpace(displayName) ? displayName : "Google Account";
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
