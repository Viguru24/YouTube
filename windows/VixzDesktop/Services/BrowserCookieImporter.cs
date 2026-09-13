using System;
using System.Collections.Generic;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;
using Microsoft.Data.Sqlite;

namespace VixzDesktop.Services
{
    public enum BrowserChoice { Chrome, Edge }

    public record ImportedCookie(string Name, string Value, string Domain, string Path);

    public static class BrowserCookieImporter
    {
        // ── Browser profile paths ────────────────────────────────────────────────

        private static string GetCookiePath(BrowserChoice browser)
        {
            var localApp = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            return browser switch
            {
                BrowserChoice.Chrome => System.IO.Path.Combine(localApp, "Google", "Chrome", "User Data", "Default", "Network", "Cookies"),
                BrowserChoice.Edge   => System.IO.Path.Combine(localApp, "Microsoft", "Edge", "User Data", "Default", "Network", "Cookies"),
                _ => throw new ArgumentOutOfRangeException(nameof(browser))
            };
        }

        private static string GetLocalStatePath(BrowserChoice browser)
        {
            var localApp = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            return browser switch
            {
                BrowserChoice.Chrome => System.IO.Path.Combine(localApp, "Google", "Chrome", "User Data", "Local State"),
                BrowserChoice.Edge   => System.IO.Path.Combine(localApp, "Microsoft", "Edge", "User Data", "Local State"),
                _ => throw new ArgumentOutOfRangeException(nameof(browser))
            };
        }

        // ── Public entry point ───────────────────────────────────────────────────

        public static async Task<List<ImportedCookie>> ImportYouTubeCookiesAsync(BrowserChoice browser)
        {
            return await Task.Run(() => ImportYouTubeCookiesInternal(browser));
        }

        // ── Internal synchronous implementation (runs on thread pool) ───────────

        private static List<ImportedCookie> ImportYouTubeCookiesInternal(BrowserChoice browser)
        {
            var results = new List<ImportedCookie>();

            var cookiePath = GetCookiePath(browser);
            var localStatePath = GetLocalStatePath(browser);

            if (!File.Exists(cookiePath) || !File.Exists(localStatePath))
                throw new FileNotFoundException($"{browser} profile not found. Is {browser} installed?");

            byte[] masterKey = GetMasterKey(localStatePath);

            var tempDb = System.IO.Path.Combine(System.IO.Path.GetTempPath(), $"vixz_cookies_{Guid.NewGuid():N}.db");
            try
            {
                File.Copy(cookiePath, tempDb, overwrite: true);

                using var conn = new SqliteConnection($"Data Source={tempDb};Mode=ReadOnly;Cache=Shared");
                conn.Open();

                using var cmd = conn.CreateCommand();
                cmd.CommandText = @"
                    SELECT name, encrypted_value, host_key, path
                    FROM cookies
                    WHERE host_key LIKE '%.youtube.com'
                       OR host_key LIKE '%.google.com'
                       OR host_key = 'youtube.com'
                       OR host_key = 'google.com'";

                using var reader = cmd.ExecuteReader();
                while (reader.Read())
                {
                    var name     = reader.GetString(0);
                    var encBytes = (byte[])reader[1];
                    var domain   = reader.GetString(2);
                    var path     = reader.GetString(3);

                    string value;
                    try   { value = DecryptCookieValue(encBytes, masterKey); }
                    catch { continue; }

                    if (!string.IsNullOrEmpty(value))
                        results.Add(new ImportedCookie(name, value, domain, path));
                }
            }
            finally
            {
                try { File.Delete(tempDb); } catch { }
            }

            return results;
        }

        // ── AES-256-GCM decryption ───────────────────────────────────────────────

        private static string DecryptCookieValue(byte[] encryptedValue, byte[] masterKey)
        {
            if (encryptedValue.Length < 3 || Encoding.ASCII.GetString(encryptedValue, 0, 3) != "v10")
            {
                var plain = ProtectedData.Unprotect(encryptedValue, null, DataProtectionScope.CurrentUser);
                return Encoding.UTF8.GetString(plain);
            }

            const int prefixLen = 3;
            const int nonceLen  = 12;
            const int tagLen    = 16;

            var nonce      = encryptedValue[prefixLen..(prefixLen + nonceLen)];
            var cipherData = encryptedValue[(prefixLen + nonceLen)..];

            if (cipherData.Length < tagLen)
                throw new CryptographicException("Cookie value too short to contain auth tag.");

            var tag        = cipherData[^tagLen..];
            var ciphertext = cipherData[..^tagLen];
            var plaintext  = new byte[ciphertext.Length];

            using var aes = new AesGcm(masterKey, tagSizeInBytes: tagLen);
            aes.Decrypt(nonce, ciphertext, tag, plaintext);

            return Encoding.UTF8.GetString(plaintext);
        }

        // ── DPAPI master key extraction ──────────────────────────────────────────

        private static byte[] GetMasterKey(string localStatePath)
        {
            var json = File.ReadAllText(localStatePath);
            using var doc = JsonDocument.Parse(json);

            var encKeyB64 = doc.RootElement
                               .GetProperty("os_crypt")
                               .GetProperty("encrypted_key")
                               .GetString()
                ?? throw new InvalidOperationException("encrypted_key not found in Local State.");

            var encKeyBytes = Convert.FromBase64String(encKeyB64);

            const string dpapi = "DPAPI";
            if (Encoding.ASCII.GetString(encKeyBytes, 0, dpapi.Length) != dpapi)
                throw new CryptographicException("Unexpected Local State key format.");

            var encKey = encKeyBytes[dpapi.Length..];
            return ProtectedData.Unprotect(encKey, null, DataProtectionScope.CurrentUser);
        }

        // ── Installed-browser discovery ──────────────────────────────────────────

        public static bool IsBrowserInstalled(BrowserChoice browser)
        {
            try { return File.Exists(GetLocalStatePath(browser)); }
            catch { return false; }
        }
    }
}
