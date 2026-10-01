using System.Diagnostics;
using System.Net.Http;
using System.Text;
using System.Text.Json;

namespace Health2609AgyTray;

public class BridgeHealthStatus
{
    public bool IsOnline { get; set; }
    public bool AgyReady { get; set; }
    public int QueueDepth { get; set; }
    public string Message { get; set; } = string.Empty;
}

public class BridgeProcessManager : IDisposable
{
    private Process? _process;
    private readonly object _lock = new();
    private readonly List<string> _recentLogs = new();
    private const int MaxLogLines = 1500;
    private bool _manualStop = false;
    private bool _isDisposed = false;
    private readonly HttpClient _httpClient = new() { Timeout = TimeSpan.FromSeconds(3) };

    public event Action<string>? LogReceived;
    public event Action<BridgeState>? StateChanged;

    public BridgeState CurrentState { get; private set; } = BridgeState.Stopped;
    public string LogFilePath { get; }
    public int Port { get; } = 18788;
    public string Token { get; } = "00e037966ab94d718109e054e1922133e1aded4063ea4ec7a5dba7c5cf80ab35";

    public BridgeProcessManager()
    {
        string appData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        string logDir = Path.Combine(appData, "health2609", "logs");
        Directory.CreateDirectory(logDir);
        LogFilePath = Path.Combine(logDir, "agy-bridge.log");
    }

    private void SetState(BridgeState state)
    {
        if (CurrentState != state)
        {
            CurrentState = state;
            StateChanged?.Invoke(state);
        }
    }

    public IReadOnlyList<string> GetRecentLogs()
    {
        lock (_lock)
        {
            return _recentLogs.ToList();
        }
    }

    public void AppendLog(string message)
    {
        string timestamped = $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}] {message}";
        lock (_lock)
        {
            _recentLogs.Add(timestamped);
            if (_recentLogs.Count > MaxLogLines)
            {
                _recentLogs.RemoveRange(0, _recentLogs.Count - MaxLogLines);
            }
        }

        try
        {
            File.AppendAllText(LogFilePath, timestamped + Environment.NewLine, Encoding.UTF8);
        }
        catch
        {
            // Ignore file logging failures
        }

        LogReceived?.Invoke(timestamped);
    }

    public void Start()
    {
        lock (_lock)
        {
            if (_process != null && !_process.HasExited)
            {
                return;
            }

            _manualStop = false;
            SetState(BridgeState.Starting);
            AppendLog("Starting AGY Bridge service supervisor...");

            string? nodePath = FindNodeExecutable();
            if (string.IsNullOrEmpty(nodePath) || !File.Exists(nodePath))
            {
                AppendLog("ERROR: node.exe not found. Please install Node.js.");
                SetState(BridgeState.Error);
                return;
            }

            string? scriptPath = FindBridgeScript();
            if (string.IsNullOrEmpty(scriptPath) || !File.Exists(scriptPath))
            {
                AppendLog($"ERROR: server.mjs not found at candidate paths.");
                SetState(BridgeState.Error);
                return;
            }

            string repoRoot = Path.GetFullPath(Path.Combine(Path.GetDirectoryName(scriptPath)!, "..", ".."));

            var psi = new ProcessStartInfo
            {
                FileName = nodePath,
                Arguments = $"\"{scriptPath}\"",
                WorkingDirectory = repoRoot,
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                StandardOutputEncoding = Encoding.UTF8,
                StandardErrorEncoding = Encoding.UTF8
            };

            psi.EnvironmentVariables["HEALTH2609_AGY_PORT"] = Port.ToString();
            psi.EnvironmentVariables["HEALTH2609_AGY_TOKEN"] = Token;
            psi.EnvironmentVariables["HEALTH2609_REPO_ROOT"] = repoRoot;

            string currentPath = Environment.GetEnvironmentVariable("PATH") ?? "";
            string extraPaths = @"C:\Users\meteo\AppData\Local\MixProcessProxy\bin;C:\Program Files\nodejs;";
            psi.EnvironmentVariables["PATH"] = extraPaths + currentPath;

            try
            {
                _process = new Process { StartInfo = psi, EnableRaisingEvents = true };

                _process.OutputDataReceived += (s, e) =>
                {
                    if (!string.IsNullOrEmpty(e.Data))
                    {
                        AppendLog(e.Data);
                        if (e.Data.Contains("resident AGY ready"))
                        {
                            SetState(BridgeState.Ready);
                        }
                    }
                };

                _process.ErrorDataReceived += (s, e) =>
                {
                    if (!string.IsNullOrEmpty(e.Data))
                    {
                        AppendLog($"[ERR] {e.Data}");
                    }
                };

                _process.Exited += (s, e) =>
                {
                    int exitCode = -1;
                    try { exitCode = _process?.ExitCode ?? -1; } catch { }
                    AppendLog($"Bridge process terminated with exit code: {exitCode}");

                    if (!_manualStop && !_isDisposed)
                    {
                        SetState(BridgeState.Error);
                        AppendLog("Process exited unexpectedly. Restarting in 3 seconds...");
                        Task.Delay(3000).ContinueWith(_ =>
                        {
                            if (!_manualStop && !_isDisposed)
                            {
                                Start();
                            }
                        });
                    }
                    else
                    {
                        SetState(BridgeState.Stopped);
                    }
                };

                _process.Start();
                _process.BeginOutputReadLine();
                _process.BeginErrorReadLine();
                AppendLog($"Bridge process spawned successfully (PID: {_process.Id}).");
            }
            catch (Exception ex)
            {
                AppendLog($"Failed to spawn node process: {ex.Message}");
                SetState(BridgeState.Error);
            }
        }
    }

    public void Stop()
    {
        lock (_lock)
        {
            _manualStop = true;
            if (_process != null && !_process.HasExited)
            {
                AppendLog($"Stopping Bridge process (PID: {_process.Id})...");
                try
                {
                    KillProcessTree(_process.Id);
                }
                catch (Exception ex)
                {
                    AppendLog($"Error while killing process: {ex.Message}");
                }
                _process = null;
            }
            SetState(BridgeState.Stopped);
            AppendLog("Bridge service stopped.");
        }
    }

    public void Restart()
    {
        AppendLog("Restarting Bridge service...");
        Stop();
        Thread.Sleep(800);
        Start();
    }

    public async Task<BridgeHealthStatus> CheckHealthAsync()
    {
        var result = new BridgeHealthStatus();
        try
        {
            var req = new HttpRequestMessage(HttpMethod.Get, $"http://127.0.0.1:{Port}/healthz");
            req.Headers.Add("Authorization", $"Bearer {Token}");
            var resp = await _httpClient.SendAsync(req);
            if (resp.IsSuccessStatusCode)
            {
                string json = await resp.Content.ReadAsStringAsync();
                result.IsOnline = true;
                using var doc = JsonDocument.Parse(json);
                if (doc.RootElement.TryGetProperty("agyReady", out var readyProp))
                {
                    result.AgyReady = readyProp.GetBoolean();
                }
                if (doc.RootElement.TryGetProperty("queueDepth", out var queueProp))
                {
                    result.QueueDepth = queueProp.GetInt32();
                }
                result.Message = result.AgyReady ? "AGY就绪" : "启动中/等待就绪";

                if (result.AgyReady)
                {
                    SetState(BridgeState.Ready);
                }
                else
                {
                    SetState(BridgeState.Starting);
                }
            }
            else
            {
                result.IsOnline = false;
                result.Message = $"HTTP {resp.StatusCode}";
                SetState(BridgeState.Error);
            }
        }
        catch (Exception ex)
        {
            result.IsOnline = false;
            result.Message = "未响应: " + ex.Message;
            if (_process == null || _process.HasExited)
            {
                SetState(BridgeState.Stopped);
            }
            else
            {
                SetState(BridgeState.Starting);
            }
        }
        return result;
    }

    private static void KillProcessTree(int pid)
    {
        try
        {
            using var proc = Process.Start(new ProcessStartInfo
            {
                FileName = "taskkill",
                Arguments = $"/pid {pid} /T /F",
                CreateNoWindow = true,
                UseShellExecute = false
            });
            proc?.WaitForExit(2000);
        }
        catch
        {
        }
    }

    private static string? FindNodeExecutable()
    {
        string[] candidates = new[]
        {
            @"C:\Program Files\nodejs\node.exe",
            @"C:\Program Files (x86)\nodejs\node.exe",
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "AppData", "Local", "MixProcessProxy", "bin", "node.exe")
        };

        foreach (var c in candidates)
        {
            if (File.Exists(c)) return c;
        }

        string? pathEnv = Environment.GetEnvironmentVariable("PATH");
        if (pathEnv != null)
        {
            foreach (var segment in pathEnv.Split(';'))
            {
                string candidate = Path.Combine(segment.Trim(), "node.exe");
                if (File.Exists(candidate)) return candidate;
            }
        }

        return "node";
    }

    private static string? FindBridgeScript()
    {
        string appDir = AppContext.BaseDirectory;
        string[] candidates = new[]
        {
            Path.Combine(appDir, "..", "..", "..", "..", "tools", "agy-bridge", "server.mjs"),
            Path.Combine(appDir, "..", "..", "tools", "agy-bridge", "server.mjs"),
            Path.Combine(appDir, "tools", "agy-bridge", "server.mjs"),
            Path.Combine(appDir, "server.mjs"),
            @"C:\Users\meteo\Documents\health2609\tools\agy-bridge\server.mjs"
        };

        foreach (var c in candidates)
        {
            string full = Path.GetFullPath(c);
            if (File.Exists(full)) return full;
        }

        return null;
    }

    public void Dispose()
    {
        if (_isDisposed) return;
        _isDisposed = true;
        Stop();
        _httpClient.Dispose();
    }
}
