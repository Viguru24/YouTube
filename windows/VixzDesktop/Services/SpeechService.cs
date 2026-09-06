using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Speech.Recognition;
using System.Speech.Synthesis;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using System.Windows;

namespace VixzDesktop.Services
{
    /// <summary>
    /// Provides speech synthesis (Text-to-Speech) and microphone recognition (Speech-to-Text)
    /// powered by native Windows desktop speech APIs (System.Speech).
    /// </summary>
    public class SpeechService
    {
        private static readonly SpeechService _instance = new();
        public static SpeechService Instance => _instance;

        private SpeechSynthesizer? _synth;
        private SpeechRecognitionEngine? _recognizer;
        private readonly object _lock = new();

        private Action? _currentSpeechCompleteCallback;
        private Action<string>? _currentHypothesisCallback;
        private Action<string>? _currentFinalResultCallback;
        private Action<string>? _currentErrorCallback;
        private Action? _currentStoppedCallback;

        public bool IsSpeaking { get; private set; }
        public bool IsListening { get; private set; }

        public event EventHandler<bool>? SpeakingStateChanged;
        public event EventHandler<bool>? ListeningStateChanged;

        private SpeechService()
        {
            InitializeSynthesizer();
        }

        #region Text-To-Speech (Speech Synthesis)

        private void InitializeSynthesizer()
        {
            try
            {
                _synth = new SpeechSynthesizer();
                _synth.SetOutputToDefaultAudioDevice();
                _synth.Rate = 0; // Natural conversational rate
                _synth.Volume = 100;

                // Pick high quality natural installed voice if available (Hazel, Zira, David, etc.)
                var voices = _synth.GetInstalledVoices()
                    .Where(v => v.Enabled)
                    .Select(v => v.VoiceInfo)
                    .ToList();

                var preferredVoice = voices.FirstOrDefault(v => v.Name.Contains("Hazel", StringComparison.OrdinalIgnoreCase))
                                  ?? voices.FirstOrDefault(v => v.Name.Contains("Zira", StringComparison.OrdinalIgnoreCase))
                                  ?? voices.FirstOrDefault(v => v.Name.Contains("David", StringComparison.OrdinalIgnoreCase))
                                  ?? voices.FirstOrDefault(v => v.Culture.TwoLetterISOLanguageName.Equals("en", StringComparison.OrdinalIgnoreCase))
                                  ?? voices.FirstOrDefault();

                if (preferredVoice != null)
                {
                    _synth.SelectVoice(preferredVoice.Name);
                }

                _synth.SpeakCompleted += OnSynthSpeakCompleted;
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[SpeechService] TTS Init error: {ex.Message}");
            }
        }

        private void OnSynthSpeakCompleted(object? sender, SpeakCompletedEventArgs e)
        {
            IsSpeaking = false;
            SpeakingStateChanged?.Invoke(this, false);

            var cb = _currentSpeechCompleteCallback;
            _currentSpeechCompleteCallback = null;

            if (cb != null)
            {
                Application.Current?.Dispatcher.BeginInvoke(cb);
            }
        }

        /// <summary>
        /// Reads plain text or rich AI summary out loud asynchronously.
        /// </summary>
        public void SpeakAsync(string text, Action? onStart = null, Action? onComplete = null)
        {
            lock (_lock)
            {
                StopSpeaking();

                if (string.IsNullOrWhiteSpace(text)) return;
                if (_synth == null) InitializeSynthesizer();
                if (_synth == null) return;

                var cleaned = CleanTextForSpeech(text);
                if (string.IsNullOrWhiteSpace(cleaned)) return;

                _currentSpeechCompleteCallback = onComplete;
                IsSpeaking = true;
                SpeakingStateChanged?.Invoke(this, true);

                onStart?.Invoke();

                try
                {
                    _synth.SpeakAsync(cleaned);
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"[SpeechService] SpeakAsync error: {ex.Message}");
                    IsSpeaking = false;
                    SpeakingStateChanged?.Invoke(this, false);
                    _currentSpeechCompleteCallback = null;
                }
            }
        }

        /// <summary>
        /// Halts any active text-to-speech playback immediately.
        /// </summary>
        public void StopSpeaking()
        {
            lock (_lock)
            {
                if (_synth != null && IsSpeaking)
                {
                    try
                    {
                        _synth.SpeakAsyncCancelAll();
                    }
                    catch { }
                }

                IsSpeaking = false;
                SpeakingStateChanged?.Invoke(this, false);

                var cb = _currentSpeechCompleteCallback;
                _currentSpeechCompleteCallback = null;
                if (cb != null)
                {
                    Application.Current?.Dispatcher.BeginInvoke(cb);
                }
            }
        }

        /// <summary>
        /// Strips markdown formatting, links, raw URLs, and special symbols so TTS sounds articulate.
        /// </summary>
        public static string CleanTextForSpeech(string rawText)
        {
            if (string.IsNullOrWhiteSpace(rawText)) return "";

            var text = rawText;

            // Remove markdown bold/italic (**text**, *text*, __text__)
            text = Regex.Replace(text, @"(\*\*|__)(.*?)\1", "$2");
            text = Regex.Replace(text, @"(\*|_)(.*?)\1", "$2");

            // Remove markdown links [title](url) -> title
            text = Regex.Replace(text, @"\[([^\]]+)\]\([^\)]+\)", "$1");

            // Remove raw web URLs
            text = Regex.Replace(text, @"https?:\/\/\S+", "");

            // Remove markdown headers #, ##, ###
            text = Regex.Replace(text, @"^#{1,6}\s*", "", RegexOptions.Multiline);

            // Convert bullet markers to clean conversational pauses
            text = Regex.Replace(text, @"^[\s•\-\*]+\s*", ". ", RegexOptions.Multiline);

            // Remove emojis & high unicode decorative symbols
            text = Regex.Replace(text, @"[\uD83C-\uDBFF\uDC00-\uDFFF]+", "");
            text = Regex.Replace(text, @"[📌🔑⏱️🌐🤖✨▶️🌙⚡📋✔✕⚙️🎙️🎤🔊⏹⏸️]", "");

            // Replace excessive dots or spaces
            text = Regex.Replace(text, @"\s{2,}", " ");
            text = Regex.Replace(text, @"\.{2,}", ".");

            return text.Trim();
        }

        #endregion

        #region Speech Recognition (Microphone Voice Input)

        /// <summary>
        /// Starts microphone speech recognition.
        /// </summary>
        public bool StartListening(
            Action<string> onHypothesis,
            Action<string> onFinalResult,
            Action<string>? onError = null,
            Action? onStopped = null)
        {
            lock (_lock)
            {
                StopListening();

                _currentHypothesisCallback = onHypothesis;
                _currentFinalResultCallback = onFinalResult;
                _currentErrorCallback = onError;
                _currentStoppedCallback = onStopped;

                try
                {
                    if (_recognizer == null)
                    {
                        var recognizers = SpeechRecognitionEngine.InstalledRecognizers();
                        if (recognizers.Count == 0)
                        {
                            onError?.Invoke("No Windows speech recognizers found. Please enable Windows Speech in Settings.");
                            return false;
                        }

                        // Prefer matching current UI culture or English
                        var currentCulture = CultureInfo.CurrentUICulture;
                        var selectedRec = recognizers.FirstOrDefault(r => r.Culture.Equals(currentCulture))
                                       ?? recognizers.FirstOrDefault(r => r.Culture.TwoLetterISOLanguageName.Equals("en", StringComparison.OrdinalIgnoreCase))
                                       ?? recognizers.First();

                        _recognizer = new SpeechRecognitionEngine(selectedRec.Culture);

                        // 1. Dictation grammar for free-form queries
                        var dictation = new DictationGrammar { Name = "DictationGrammar" };
                        _recognizer.LoadGrammar(dictation);

                        // 2. High-priority Choices grammar for common Vixz voice commands
                        try
                        {
                            var commandChoices = new Choices();
                            commandChoices.Add(new string[]
                            {
                                "summarise this video",
                                "summarize this video",
                                "summarise video",
                                "summarize video",
                                "play latest benny johnson video",
                                "play latest news",
                                "find breaking news today",
                                "set sleep timer for 30 minutes",
                                "pause video",
                                "play video",
                                "resume video",
                                "skip 30 seconds",
                                "go back 10 seconds",
                                "skip 10 seconds",
                                "mute",
                                "unmute"
                            });
                            var gb = new GrammarBuilder(commandChoices);
                            var cmdGrammar = new Grammar(gb) { Name = "CommandGrammar", Priority = 1 };
                            _recognizer.LoadGrammar(cmdGrammar);
                        }
                        catch { }

                        _recognizer.SpeechHypothesized += OnSpeechHypothesized;
                        _recognizer.SpeechRecognized += OnSpeechRecognized;
                        _recognizer.RecognizeCompleted += OnRecognizeCompleted;
                    }

                    _recognizer.SetInputToDefaultAudioDevice();
                    _recognizer.RecognizeAsync(RecognizeMode.Single);

                    IsListening = true;
                    ListeningStateChanged?.Invoke(this, true);
                    return true;
                }
                catch (InvalidOperationException ioEx)
                {
                    System.Diagnostics.Debug.WriteLine($"[SpeechService] Audio device error: {ioEx.Message}");
                    onError?.Invoke("Microphone not detected. Please verify your microphone is plugged in and allowed.");
                    CleanupListeningState();
                    return false;
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"[SpeechService] Recognition Start error: {ex.Message}");
                    onError?.Invoke($"Voice input unavailable: {ex.Message}");
                    CleanupListeningState();
                    return false;
                }
            }
        }

        private void OnSpeechHypothesized(object? sender, SpeechHypothesizedEventArgs e)
        {
            if (e.Result != null && !string.IsNullOrWhiteSpace(e.Result.Text))
            {
                var text = e.Result.Text;
                Application.Current?.Dispatcher.BeginInvoke(() =>
                {
                    _currentHypothesisCallback?.Invoke(text);
                });
            }
        }

        private void OnSpeechRecognized(object? sender, SpeechRecognizedEventArgs e)
        {
            if (e.Result != null && !string.IsNullOrWhiteSpace(e.Result.Text))
            {
                var text = e.Result.Text;
                Application.Current?.Dispatcher.BeginInvoke(() =>
                {
                    _currentFinalResultCallback?.Invoke(text);
                });
            }
        }

        private void OnRecognizeCompleted(object? sender, RecognizeCompletedEventArgs e)
        {
            CleanupListeningState();
        }

        /// <summary>
        /// Stops active microphone recognition.
        /// </summary>
        public void StopListening()
        {
            lock (_lock)
            {
                if (_recognizer != null && IsListening)
                {
                    try
                    {
                        _recognizer.RecognizeAsyncCancel();
                    }
                    catch { }
                }

                CleanupListeningState();
            }
        }

        private void CleanupListeningState()
        {
            IsListening = false;
            ListeningStateChanged?.Invoke(this, false);

            var stopped = _currentStoppedCallback;
            _currentHypothesisCallback = null;
            _currentFinalResultCallback = null;
            _currentErrorCallback = null;
            _currentStoppedCallback = null;

            if (stopped != null)
            {
                Application.Current?.Dispatcher.BeginInvoke(stopped);
            }
        }

        #endregion
    }
}
