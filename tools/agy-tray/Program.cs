namespace Health2609AgyTray;

static class Program
{
    private const string MutexName = @"Local\Health2609AgyTray_SingleInstance_Mutex";

    [STAThread]
    static void Main()
    {
        string appData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        string crashLog = Path.Combine(appData, "health2609", "logs", "tray-crash.log");

        AppDomain.CurrentDomain.UnhandledException += (s, e) =>
        {
            try
            {
                Directory.CreateDirectory(Path.GetDirectoryName(crashLog)!);
                File.AppendAllText(crashLog, $"[{DateTime.Now}] UnhandledException: {e.ExceptionObject}\n");
            }
            catch { }
        };

        Application.SetUnhandledExceptionMode(UnhandledExceptionMode.CatchException);
        Application.ThreadException += (s, e) =>
        {
            try
            {
                Directory.CreateDirectory(Path.GetDirectoryName(crashLog)!);
                File.AppendAllText(crashLog, $"[{DateTime.Now}] ThreadException: {e.Exception}\n");
            }
            catch { }
        };

        bool createdNew;
        Mutex? mutex = null;
        try
        {
            mutex = new Mutex(true, MutexName, out createdNew);
        }
        catch (Exception ex)
        {
            try
            {
                Directory.CreateDirectory(Path.GetDirectoryName(crashLog)!);
                File.AppendAllText(crashLog, $"[{DateTime.Now}] Mutex error: {ex}\n");
            }
            catch { }
            createdNew = true;
        }

        if (!createdNew)
        {
            MessageBox.Show("一餐一动 AGY 托盘管理服务已经在运行中。\n请检查右下角系统托盘图标。", "提示", MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }

        try
        {
            ApplicationConfiguration.Initialize();
            Application.Run(new TrayApplicationContext());
        }
        catch (Exception ex)
        {
            try
            {
                Directory.CreateDirectory(Path.GetDirectoryName(crashLog)!);
                File.AppendAllText(crashLog, $"[{DateTime.Now}] Application.Run error: {ex}\n");
            }
            catch { }
        }
        finally
        {
            try { mutex?.ReleaseMutex(); } catch { }
            mutex?.Dispose();
        }
    }
}