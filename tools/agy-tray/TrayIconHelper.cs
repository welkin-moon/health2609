using System.Drawing;
using System.Drawing.Drawing2D;
using System.Runtime.InteropServices;

namespace Health2609AgyTray;

public enum BridgeState
{
    Stopped,
    Starting,
    Ready,
    Error
}

public static class TrayIconHelper
{
    [DllImport("user32.dll", CharSet = CharSet.Auto)]
    private static extern bool DestroyIcon(IntPtr handle);

    private static readonly Dictionary<BridgeState, Icon> _iconCache = new();

    static TrayIconHelper()
    {
        _iconCache[BridgeState.Ready] = RenderIcon(Color.FromArgb(34, 197, 94));     // Green
        _iconCache[BridgeState.Starting] = RenderIcon(Color.FromArgb(234, 179, 8));  // Amber
        _iconCache[BridgeState.Stopped] = RenderIcon(Color.FromArgb(100, 116, 139)); // Gray
        _iconCache[BridgeState.Error] = RenderIcon(Color.FromArgb(239, 68, 68));     // Red
    }

    public static Icon GetStatusIcon(BridgeState state)
    {
        if (_iconCache.TryGetValue(state, out var icon))
        {
            return icon;
        }
        return _iconCache[BridgeState.Stopped];
    }

    private static Icon RenderIcon(Color baseColor)
    {
        int size = 32;
        using var bitmap = new Bitmap(size, size);
        using (var g = Graphics.FromImage(bitmap))
        {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.Clear(Color.Transparent);

            // Outer ring
            using var ringBrush = new SolidBrush(Color.FromArgb(40, 255, 255, 255));
            g.FillEllipse(ringBrush, 1, 1, size - 2, size - 2);

            // Inner body
            using var fillBrush = new SolidBrush(baseColor);
            g.FillEllipse(fillBrush, 4, 4, size - 8, size - 8);

            // Highlight dot
            using var highlightBrush = new SolidBrush(Color.FromArgb(180, 255, 255, 255));
            g.FillEllipse(highlightBrush, 8, 8, 7, 7);
        }

        IntPtr hIcon = bitmap.GetHicon();
        var managed = (Icon)Icon.FromHandle(hIcon).Clone();
        DestroyIcon(hIcon);
        return managed;
    }
}
