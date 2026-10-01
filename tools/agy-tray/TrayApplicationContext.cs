using System.Diagnostics;
using System.Drawing;
using System.Windows.Forms;

namespace Health2609AgyTray;

public class TrayApplicationContext : ApplicationContext
{
    private readonly NotifyIcon _notifyIcon;
    private readonly ContextMenuStrip _contextMenu;
    private readonly BridgeProcessManager _manager;
    private LogViewerForm? _logForm;
    private readonly System.Windows.Forms.Timer _pollTimer;
    private BridgeState _lastState = BridgeState.Stopped;

    private readonly ToolStripMenuItem _statusMenuItem;
    private readonly ToolStripMenuItem _startMenuItem;
    private readonly ToolStripMenuItem _stopMenuItem;
    private readonly ToolStripMenuItem _restartMenuItem;
    private readonly ToolStripMenuItem _autoStartMenuItem;

    public TrayApplicationContext()
    {
        _manager = new BridgeProcessManager();
        _manager.StateChanged += OnStateChanged;

        _contextMenu = new ContextMenuStrip();

        var headerItem = new ToolStripMenuItem("一餐一动 AGY 视觉网桥")
        {
            Enabled = false,
            Font = new Font("Segoe UI", 9f, FontStyle.Bold)
        };
        _contextMenu.Items.Add(headerItem);

        _statusMenuItem = new ToolStripMenuItem("状态: 初始化中...") { Enabled = false };
        _contextMenu.Items.Add(_statusMenuItem);

        _contextMenu.Items.Add(new ToolStripSeparator());

        var showLogsItem = new ToolStripMenuItem("实时运行日志(&L)...", null, (s, e) => ShowLogForm());
        _contextMenu.Items.Add(showLogsItem);

        var openHealthzItem = new ToolStripMenuItem("打开健康检查页面(&H)", null, (s, e) =>
        {
            try
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = $"http://127.0.0.1:{_manager.Port}/healthz",
                    UseShellExecute = true
                });
            }
            catch { }
        });
        _contextMenu.Items.Add(openHealthzItem);

        _contextMenu.Items.Add(new ToolStripSeparator());

        _startMenuItem = new ToolStripMenuItem("启动服务(&S)", null, (s, e) => _manager.Start());
        _stopMenuItem = new ToolStripMenuItem("停止服务(&T)", null, (s, e) => _manager.Stop());
        _restartMenuItem = new ToolStripMenuItem("重启服务(&R)", null, (s, e) => _manager.Restart());

        _contextMenu.Items.Add(_startMenuItem);
        _contextMenu.Items.Add(_stopMenuItem);
        _contextMenu.Items.Add(_restartMenuItem);

        _contextMenu.Items.Add(new ToolStripSeparator());

        _autoStartMenuItem = new ToolStripMenuItem("开机自动启动")
        {
            CheckOnClick = true,
            Checked = AutoStartHelper.IsAutoStartEnabled()
        };
        _autoStartMenuItem.Click += (s, e) =>
        {
            AutoStartHelper.SetAutoStart(_autoStartMenuItem.Checked);
        };
        _contextMenu.Items.Add(_autoStartMenuItem);

        var openLogDirItem = new ToolStripMenuItem("打开日志目录(&D)", null, (s, e) =>
        {
            try
            {
                string dir = Path.GetDirectoryName(_manager.LogFilePath)!;
                Process.Start(new ProcessStartInfo
                {
                    FileName = "explorer.exe",
                    Arguments = $"\"{dir}\"",
                    UseShellExecute = true
                });
            }
            catch { }
        });
        _contextMenu.Items.Add(openLogDirItem);

        _contextMenu.Items.Add(new ToolStripSeparator());

        var exitItem = new ToolStripMenuItem("退出(&X)", null, (s, e) => Exit());
        _contextMenu.Items.Add(exitItem);

        _notifyIcon = new NotifyIcon
        {
            Icon = TrayIconHelper.GetStatusIcon(BridgeState.Starting),
            Text = "一餐一动 AGY 视觉网桥",
            ContextMenuStrip = _contextMenu,
            Visible = true
        };

        _notifyIcon.DoubleClick += (s, e) => ShowLogForm();

        // Start background bridge process
        _manager.Start();

        _pollTimer = new System.Windows.Forms.Timer { Interval = 3000 };
        _pollTimer.Tick += async (s, e) =>
        {
            try
            {
                var health = await _manager.CheckHealthAsync();
                UpdateMenuState(health);
            }
            catch { }
        };
        _pollTimer.Start();

        UpdateMenuState();
    }

    private void ShowLogForm()
    {
        if (_logForm == null || _logForm.IsDisposed)
        {
            _logForm = new LogViewerForm(_manager);
        }
        _logForm.Show();
        _logForm.WindowState = FormWindowState.Normal;
        _logForm.BringToFront();
    }

    private void OnStateChanged(BridgeState state)
    {
        UpdateMenuState();
    }

    private void UpdateMenuState(BridgeHealthStatus? health = null)
    {
        var state = _manager.CurrentState;
        if (_lastState != state)
        {
            _notifyIcon.Icon = TrayIconHelper.GetStatusIcon(state);
            _lastState = state;
        }

        string stateText = state switch
        {
            BridgeState.Ready => "运行中 (AGY就绪)",
            BridgeState.Starting => "启动中 / 检查中",
            BridgeState.Stopped => "已停止",
            BridgeState.Error => "异常 / 未就绪",
            _ => "未知"
        };

        _statusMenuItem.Text = $"状态: {stateText} (端口 {_manager.Port})";
        _notifyIcon.Text = $"一餐一动 AGY 网桥: {stateText}";

        _startMenuItem.Enabled = state == BridgeState.Stopped || state == BridgeState.Error;
        _stopMenuItem.Enabled = state == BridgeState.Ready || state == BridgeState.Starting;
        _restartMenuItem.Enabled = true;
    }

    private void Exit()
    {
        _pollTimer.Stop();
        _notifyIcon.Visible = false;
        _notifyIcon.Dispose();
        _logForm?.Dispose();
        _manager.Dispose();
        Application.Exit();
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            _pollTimer.Dispose();
            _notifyIcon.Dispose();
            _manager.Dispose();
        }
        base.Dispose(disposing);
    }
}
