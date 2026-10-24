using System;
using System.Collections.Concurrent;
using System.Collections.ObjectModel;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using Newtonsoft.Json;
using VixzDesktop.Models;

namespace VixzDesktop.Services
{
    public class DownloadQueueService
    {
        private static readonly Lazy<DownloadQueueService> _instance = new(() => new DownloadQueueService());
        public static DownloadQueueService Instance => _instance.Value;

        private readonly string _queueFilePath;
        private readonly SemaphoreSlim _concurrencySemaphore = new(2, 2);
        private readonly ConcurrentDictionary<string, CancellationTokenSource> _activeTokens = new();
        private bool _isProcessing = false;

        public ObservableCollection<DownloadTask> Tasks { get; } = new();

        public event Action? QueueChanged;
        public event Action<DownloadTask>? TaskCompleted;

        private DownloadQueueService()
        {
            var appData = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VixzDesktop");
            Directory.CreateDirectory(appData);
            _queueFilePath = Path.Combine(appData, "download_queue.json");
            LoadQueue();
        }

        public void Enqueue(VideoItem video, bool isVideo, string? cookiePath = null)
        {
            if (video == null || string.IsNullOrWhiteSpace(video.Id)) return;

            // Check if already in queue (queued or downloading)
            var existing = Tasks.FirstOrDefault(t => t.VideoId == video.Id && (t.Status == DownloadStatus.Queued || t.Status == DownloadStatus.Downloading));
            if (existing != null) return;

            var task = new DownloadTask
            {
                VideoId = video.Id,
                Title = video.Title,
                ChannelTitle = video.ChannelTitle,
                ThumbnailUrl = video.ThumbnailUrl,
                IsVideo = isVideo,
                Status = DownloadStatus.Queued,
                EnqueuedAt = DateTime.UtcNow
            };

            Application.Current.Dispatcher.Invoke(() =>
            {
                Tasks.Insert(0, task);
            });

            SaveQueue();
            QueueChanged?.Invoke();
            _ = ProcessQueueAsync(cookiePath);
        }

        public void Pause(string taskId)
        {
            var task = Tasks.FirstOrDefault(t => t.Id == taskId);
            if (task == null) return;

            if (task.Status == DownloadStatus.Downloading && _activeTokens.TryRemove(taskId, out var cts))
            {
                cts.Cancel();
            }
            task.Status = DownloadStatus.Paused;
            SaveQueue();
            QueueChanged?.Invoke();
        }

        public void Resume(string taskId, string? cookiePath = null)
        {
            var task = Tasks.FirstOrDefault(t => t.Id == taskId);
            if (task == null) return;

            if (task.Status == DownloadStatus.Paused || task.Status == DownloadStatus.Failed)
            {
                task.Status = DownloadStatus.Queued;
                task.ErrorMessage = null;
                SaveQueue();
                QueueChanged?.Invoke();
                _ = ProcessQueueAsync(cookiePath);
            }
        }

        public void Cancel(string taskId)
        {
            var task = Tasks.FirstOrDefault(t => t.Id == taskId);
            if (task == null) return;

            if (_activeTokens.TryRemove(taskId, out var cts))
            {
                cts.Cancel();
            }

            Application.Current.Dispatcher.Invoke(() =>
            {
                Tasks.Remove(task);
            });

            SaveQueue();
            QueueChanged?.Invoke();
        }

        public void Retry(string taskId, string? cookiePath = null)
        {
            Resume(taskId, cookiePath);
        }

        public void ClearCompleted()
        {
            Application.Current.Dispatcher.Invoke(() =>
            {
                var toRemove = Tasks.Where(t => t.Status == DownloadStatus.Completed || t.Status == DownloadStatus.Failed).ToList();
                foreach (var t in toRemove)
                {
                    Tasks.Remove(t);
                }
            });
            SaveQueue();
            QueueChanged?.Invoke();
        }

        public async Task ProcessQueueAsync(string? cookiePath = null)
        {
            if (_isProcessing) return;
            _isProcessing = true;

            try
            {
                while (true)
                {
                    var nextTask = Tasks.FirstOrDefault(t => t.Status == DownloadStatus.Queued);
                    if (nextTask == null) break;

                    await _concurrencySemaphore.WaitAsync();

                    _ = Task.Run(async () =>
                    {
                        var cts = new CancellationTokenSource();
                        _activeTokens[nextTask.Id] = cts;

                        try
                        {
                            nextTask.Status = DownloadStatus.Downloading;
                            QueueChanged?.Invoke();

                            var progressHandler = new Progress<double>(p =>
                            {
                                if (cts.IsCancellationRequested) return;
                                Application.Current.Dispatcher.Invoke(() =>
                                {
                                    nextTask.Progress = p;
                                });
                            });

                            string filePath;
                            if (nextTask.IsVideo)
                            {
                                filePath = await DownloadService.DownloadVideoAsync(nextTask.VideoId, nextTask.Title, progressHandler, cookiePath);
                            }
                            else
                            {
                                filePath = await DownloadService.DownloadAudioAsync(nextTask.VideoId, nextTask.Title, progressHandler, cookiePath);
                            }

                            if (cts.IsCancellationRequested)
                            {
                                nextTask.Status = DownloadStatus.Paused;
                            }
                            else
                            {
                                nextTask.FilePath = filePath;
                                nextTask.Progress = 1.0;
                                nextTask.Status = DownloadStatus.Completed;
                                nextTask.CompletedAt = DateTime.UtcNow;

                                // Register with offline storage
                                var vi = new VideoItem
                                {
                                    Id = nextTask.VideoId,
                                    Title = nextTask.Title,
                                    ChannelTitle = nextTask.ChannelTitle,
                                    ThumbnailUrl = nextTask.ThumbnailUrl,
                                    LocalFilePath = filePath,
                                    IsDownloaded = true
                                };
                                StorageService.AddDownload(vi);

                                TaskCompleted?.Invoke(nextTask);
                            }
                        }
                        catch (OperationCanceledException)
                        {
                            nextTask.Status = DownloadStatus.Paused;
                        }
                        catch (Exception ex)
                        {
                            nextTask.Status = DownloadStatus.Failed;
                            nextTask.ErrorMessage = ex.Message.Contains("DRM_PROTECTED_CONTENT")
                                ? "DRM protected movie"
                                : ex.Message.Length > 80 ? ex.Message.Substring(0, 80) : ex.Message;
                        }
                        finally
                        {
                            _activeTokens.TryRemove(nextTask.Id, out _);
                            _concurrencySemaphore.Release();
                            SaveQueue();
                            QueueChanged?.Invoke();
                        }
                    });
                }
            }
            finally
            {
                _isProcessing = false;
            }
        }

        private void SaveQueue()
        {
            try
            {
                var json = JsonConvert.SerializeObject(Tasks.Take(100).ToList(), Formatting.Indented);
                File.WriteAllText(_queueFilePath, json);
            }
            catch { }
        }

        private void LoadQueue()
        {
            try
            {
                if (File.Exists(_queueFilePath))
                {
                    var json = File.ReadAllText(_queueFilePath);
                    var items = JsonConvert.DeserializeObject<ObservableCollection<DownloadTask>>(json);
                    if (items != null)
                    {
                        foreach (var item in items)
                        {
                            // Reset downloading to queued on app relaunch
                            if (item.Status == DownloadStatus.Downloading)
                            {
                                item.Status = DownloadStatus.Queued;
                            }
                            Tasks.Add(item);
                        }
                    }
                }
            }
            catch { }
        }
    }
}
