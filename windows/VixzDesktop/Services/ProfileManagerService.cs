using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Threading.Tasks;
using Microsoft.Web.WebView2.Core;
using Newtonsoft.Json;
using VixzDesktop.Models;

namespace VixzDesktop.Services
{
    public class ProfileManagerService
    {
        private static readonly Lazy<ProfileManagerService> _instance = new(() => new ProfileManagerService());
        public static ProfileManagerService Instance => _instance.Value;

        private readonly string _profilesFilePath;
        public List<UserProfileSession> Profiles { get; private set; } = new();
        public string ActiveProfileId { get; private set; } = "";

        public event Action? ProfilesChanged;

        private ProfileManagerService()
        {
            var appData = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VixzDesktop");
            Directory.CreateDirectory(appData);
            _profilesFilePath = Path.Combine(appData, "user_profiles.json");
            LoadProfiles();
        }

        public UserProfileSession GetActiveProfile()
        {
            var p = Profiles.FirstOrDefault(x => x.Id == ActiveProfileId);
            if (p == null)
            {
                p = Profiles.FirstOrDefault();
                if (p == null)
                {
                    p = new UserProfileSession { Name = "Personal" };
                    Profiles.Add(p);
                }
                ActiveProfileId = p.Id;
            }
            p.IsActive = true;
            return p;
        }

        public List<UserProfileSession> GetProfilesList()
        {
            foreach (var pr in Profiles)
            {
                pr.IsActive = (pr.Id == ActiveProfileId);
            }
            return Profiles.OrderByDescending(x => x.IsActive).ThenByDescending(x => x.LastUsed).ToList();
        }

        public async Task SaveCurrentProfileSnapshotAsync(CoreWebView2? webView)
        {
            var active = GetActiveProfile();
            active.SubscribedChannels = new List<string>(StorageService.Settings.SubscribedChannels);
            active.Favorites = new List<VideoItem>(StorageService.Settings.Favorites);
            active.WatchLater = new List<VideoItem>(StorageService.Settings.WatchLater);
            active.WatchHistory = new List<VideoItem>(StorageService.Settings.WatchHistory);
            active.Email = StorageService.Settings.UserAccount?.Email ?? "";
            active.AvatarUrl = StorageService.Settings.UserAccount?.AvatarUrl ?? "";
            active.LastUsed = DateTime.UtcNow;

            if (webView != null)
            {
                try
                {
                    var cookies = await webView.CookieManager.GetCookiesAsync("https://www.youtube.com");
                    if (cookies != null && cookies.Count > 0)
                    {
                        var serializable = cookies.Select(c => new
                        {
                            c.Name,
                            c.Value,
                            c.Domain,
                            c.Path,
                            c.IsSecure,
                            c.IsHttpOnly,
                            c.Expires
                        }).ToList();

                        var json = JsonConvert.SerializeObject(serializable);
                        active.EncryptedCookies = EncryptString(json);
                    }
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"[ProfileManager] Cookie snapshot failed: {ex.Message}");
                }
            }

            SaveProfiles();
        }

        public async Task<bool> SwitchToProfileAsync(string profileId, CoreWebView2? webView)
        {
            var target = Profiles.FirstOrDefault(p => p.Id == profileId);
            if (target == null) return false;

            // 1. Save current profile first
            await SaveCurrentProfileSnapshotAsync(webView);

            // 2. Set new active profile
            ActiveProfileId = target.Id;
            target.LastUsed = DateTime.UtcNow;

            // 3. Restore subscriptions & library data
            StorageService.Settings.SubscribedChannels = new List<string>(target.SubscribedChannels);
            StorageService.Settings.Favorites = new List<VideoItem>(target.Favorites);
            StorageService.Settings.WatchLater = new List<VideoItem>(target.WatchLater);
            StorageService.Settings.WatchHistory = new List<VideoItem>(target.WatchHistory);
            StorageService.Settings.UserAccount = new UserAccount
            {
                IsSignedIn = !string.IsNullOrWhiteSpace(target.Email),
                DisplayName = target.Name,
                Email = target.Email,
                AvatarUrl = target.AvatarUrl
            };
            StorageService.Save();

            // 4. Inject target profile cookies into WebView2
            if (webView != null && !string.IsNullOrWhiteSpace(target.EncryptedCookies))
            {
                try
                {
                    var json = DecryptString(target.EncryptedCookies);
                    if (!string.IsNullOrWhiteSpace(json))
                    {
                        var cookieDtos = JsonConvert.DeserializeObject<List<dynamic>>(json);
                        if (cookieDtos != null)
                        {
                            // Clear current cookies
                            webView.CookieManager.DeleteAllCookies();

                            foreach (var cd in cookieDtos)
                            {
                                try
                                {
                                    string name = cd.Name;
                                    string value = cd.Value;
                                    string domain = cd.Domain ?? ".youtube.com";
                                    string path = cd.Path ?? "/";
                                    var wvCookie = webView.CookieManager.CreateCookie(name, value, domain, path);
                                    if (cd.IsSecure == true) wvCookie.IsSecure = true;
                                    if (cd.IsHttpOnly == true) wvCookie.IsHttpOnly = true;
                                    webView.CookieManager.AddOrUpdateCookie(wvCookie);
                                }
                                catch { }
                            }
                        }
                    }
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"[ProfileManager] Cookie restore failed: {ex.Message}");
                }
            }

            SaveProfiles();
            ProfilesChanged?.Invoke();
            return true;
        }

        public UserProfileSession CreateNewProfile(string name)
        {
            var p = new UserProfileSession
            {
                Name = string.IsNullOrWhiteSpace(name) ? "New Profile" : name.Trim(),
                LastUsed = DateTime.UtcNow
            };
            Profiles.Add(p);
            SaveProfiles();
            ProfilesChanged?.Invoke();
            return p;
        }

        public void DeleteProfile(string profileId)
        {
            if (Profiles.Count <= 1) return; // Keep at least one profile
            var p = Profiles.FirstOrDefault(x => x.Id == profileId);
            if (p != null)
            {
                Profiles.Remove(p);
                if (ActiveProfileId == profileId)
                {
                    ActiveProfileId = Profiles.First().Id;
                }
                SaveProfiles();
                ProfilesChanged?.Invoke();
            }
        }

        private void SaveProfiles()
        {
            try
            {
                var payload = new
                {
                    ActiveProfileId,
                    Profiles
                };
                var json = JsonConvert.SerializeObject(payload, Formatting.Indented);
                File.WriteAllText(_profilesFilePath, json);
            }
            catch { }
        }

        private void LoadProfiles()
        {
            try
            {
                if (File.Exists(_profilesFilePath))
                {
                    var json = File.ReadAllText(_profilesFilePath);
                    var payload = JsonConvert.DeserializeAnonymousType(json, new
                    {
                        ActiveProfileId = "",
                        Profiles = new List<UserProfileSession>()
                    });

                    if (payload?.Profiles != null && payload.Profiles.Count > 0)
                    {
                        Profiles = payload.Profiles;
                        ActiveProfileId = !string.IsNullOrWhiteSpace(payload.ActiveProfileId) ? payload.ActiveProfileId : Profiles.First().Id;
                        return;
                    }
                }
            }
            catch { }

            // Default fallback
            var def = new UserProfileSession { Name = "Personal" };
            Profiles.Add(def);
            ActiveProfileId = def.Id;
        }

        private static string EncryptString(string plainText)
        {
            if (string.IsNullOrEmpty(plainText)) return "";
            try
            {
                var bytes = Encoding.UTF8.GetBytes(plainText);
                var enc = ProtectedData.Protect(bytes, null, DataProtectionScope.CurrentUser);
                return Convert.ToBase64String(enc);
            }
            catch
            {
                return Convert.ToBase64String(Encoding.UTF8.GetBytes(plainText));
            }
        }

        private static string DecryptString(string cipherText)
        {
            if (string.IsNullOrEmpty(cipherText)) return "";
            try
            {
                var bytes = Convert.FromBase64String(cipherText);
                var dec = ProtectedData.Unprotect(bytes, null, DataProtectionScope.CurrentUser);
                return Encoding.UTF8.GetString(dec);
            }
            catch
            {
                try
                {
                    return Encoding.UTF8.GetString(Convert.FromBase64String(cipherText));
                }
                catch { return ""; }
            }
        }
    }
}
