using System.Diagnostics;
using System.Drawing;
using System.Text;
using System.Windows.Forms;

namespace Health2609AgyTray;

public class LogViewerForm : Form
{
    private readonly BridgeProcessManager _manager;
    private readonly TextBox _logTextBox;
    private readonly CheckBox _autoScrollCheckBox;
    private readonly Label _statusLabel;
    private readonly System.Windows.Forms.Timer _statusTimer;

    public LogViewerForm(BridgeProcessManager manager)
    {
        _manager = manager;

        Text = "一餐一动 AGY 视觉服务日志";
        Width = 880;
        Height = 560;
        StartPosition = FormStartPosition.CenterScreen;
        Icon = TrayIconHelper.GetStatusIcon(manager.CurrentState);

        var topPanel = new Panel
        {
            Dock = DockStyle.Top,
            Height = 44,
            BackColor = Color.FromArgb(243, 244, 246),
            Padding = new Padding(10, 6, 10, 6)
        };

        _statusLabel = new Label
        {
            Text = "状态: 检测中...",
            AutoSize = true,
            Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
            ForeColor = Color.FromArgb(31, 41, 55),
            Location = new Point(12, 12)
        };
        topPanel.Controls.Add(_statusLabel);

        int right = Width - 30;

        var restartBtn = new Button
        {
            Text = "重启服务",
            Size = new Size(80, 28),
            Anchor = AnchorStyles.Top | AnchorStyles.Right,
            Location = new Point(right - 85, 7),
            BackColor = Color.White,
            FlatStyle = FlatStyle.System
        };
        restartBtn.Click += (s, e) => _manager.Restart();
        topPanel.Controls.Add(restartBtn);

        var openLogBtn = new Button
        {
            Text = "日志文件",
            Size = new Size(80, 28),
            Anchor = AnchorStyles.Top | AnchorStyles.Right,
            Location = new Point(right - 175, 7),
            BackColor = Color.White,
            FlatStyle = FlatStyle.System
        };
        openLogBtn.Click += (s, e) =>
        {
            if (File.Exists(_manager.LogFilePath))
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = "notepad.exe",
                    Arguments = $"\"{_manager.LogFilePath}\"",
                    UseShellExecute = true
                });
            }
            else
            {
                string dir = Path.GetDirectoryName(_manager.LogFilePath)!;
                Process.Start("explorer.exe", dir);
            }
        };
        topPanel.Controls.Add(openLogBtn);

        var copyBtn = new Button
        {
            Text = "复制全部",
            Size = new Size(80, 28),
            Anchor = AnchorStyles.Top | AnchorStyles.Right,
            Location = new Point(right - 265, 7),
            BackColor = Color.White,
            FlatStyle = FlatStyle.System
        };
        topPanel.Controls.Add(copyBtn);

        var clearBtn = new Button
        {
            Text = "清空窗口",
            Size = new Size(80, 28),
            Anchor = AnchorStyles.Top | AnchorStyles.Right,
            Location = new Point(right - 355, 7),
            BackColor = Color.White,
            FlatStyle = FlatStyle.System
        };
        topPanel.Controls.Add(clearBtn);

        _autoScrollCheckBox = new CheckBox
        {
            Text = "自动滚动",
            Checked = true,
            AutoSize = true,
            Anchor = AnchorStyles.Top | AnchorStyles.Right,
            Location = new Point(right - 450, 12)
        };
        topPanel.Controls.Add(_autoScrollCheckBox);

        _logTextBox = new TextBox
        {
            Multiline = true,
            ReadOnly = true,
            Dock = DockStyle.Fill,
            BackColor = Color.FromArgb(24, 24, 27),
            ForeColor = Color.FromArgb(228, 228, 231),
            Font = new Font("Consolas", 10f),
            ScrollBars = ScrollBars.Vertical,
            WordWrap = true
        };

        copyBtn.Click += (s, e) =>
        {
            if (!string.IsNullOrEmpty(_logTextBox.Text))
            {
                Clipboard.SetText(_logTextBox.Text);
            }
        };

        clearBtn.Click += (s, e) => _logTextBox.Clear();

        Controls.Add(_logTextBox);
        Controls.Add(topPanel);

        // Load existing logs
        var logs = _manager.GetRecentLogs();
        if (logs.Count > 0)
        {
            _logTextBox.Text = string.Join(Environment.NewLine, logs) + Environment.NewLine;
            _logTextBox.SelectionStart = _logTextBox.Text.Length;
            _logTextBox.ScrollToCaret();
        }

        _manager.LogReceived += OnLogReceived;

        _statusTimer = new System.Windows.Forms.Timer { Interval = 2000 };
        _statusTimer.Tick += async (s, e) => await UpdateStatusAsync();
        _statusTimer.Start();
    }

    private void OnLogReceived(string line)
    {
        if (IsDisposed || !IsHandleCreated) return;
        try
        {
            BeginInvoke(() =>
            {
                if (_logTextBox.TextLength > 200000)
                {
                    _logTextBox.Text = _logTextBox.Text.Substring(50000);
                }
                _logTextBox.AppendText(line + Environment.NewLine);
                if (_autoScrollCheckBox.Checked)
                {
                    _logTextBox.SelectionStart = _logTextBox.Text.Length;
                    _logTextBox.ScrollToCaret();
                }
            });
        }
        catch
        {
        }
    }

    private async Task UpdateStatusAsync()
    {
        var health = await _manager.CheckHealthAsync();
        if (IsDisposed || !IsHandleCreated) return;
        try
        {
            Invoke(() =>
            {
                string stateText = _manager.CurrentState switch
                {
                    BridgeState.Ready => "运行中 (AGY就绪)",
                    BridgeState.Starting => "启动/初始化中",
                    BridgeState.Stopped => "已停止",
                    BridgeState.Error => "异常",
                    _ => "未知"
                };

                _statusLabel.Text = $"状态: {stateText} | 端口: {_manager.Port} | 队列: {health.QueueDepth}";
                Icon = TrayIconHelper.GetStatusIcon(_manager.CurrentState);
            });
        }
        catch
        {
        }
    }

    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        if (e.CloseReason == CloseReason.UserClosing)
        {
            e.Cancel = true;
            Hide();
        }
        else
        {
            _statusTimer.Stop();
            _manager.LogReceived -= OnLogReceived;
            base.OnFormClosing(e);
        }
    }
}
