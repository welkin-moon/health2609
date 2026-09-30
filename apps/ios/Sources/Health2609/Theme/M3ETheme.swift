import SwiftUI

#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

// MARK: - Color Hex Initializer

extension Color {
    init(hex: String) {
        let hexClean = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hexClean).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hexClean.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 0, 0, 0)
        }

        self.init(
            .sRGB,
            red: Double(r) / 255.0,
            green: Double(g) / 255.0,
            blue: Double(b) / 255.0,
            opacity: Double(a) / 255.0
        )
    }

    static func dynamic(light: String, dark: String) -> Color {
        #if canImport(UIKit)
        return Color(UIColor { trait in
            trait.userInterfaceStyle == .dark ? UIColor(Color(hex: dark)) : UIColor(Color(hex: light))
        })
        #else
        return Color(hex: light)
        #endif
    }
}

// MARK: - Material 3 Expressive Theme Tokens

public enum M3E {
    // MARK: - Colors
    public enum Colors {
        // Primary
        public static let primary = Color.dynamic(light: "#6750A4", dark: "#D0BCFF")
        public static let onPrimary = Color.dynamic(light: "#FFFFFF", dark: "#381E72")
        public static let primaryContainer = Color.dynamic(light: "#EADDFF", dark: "#4F378B")
        public static let onPrimaryContainer = Color.dynamic(light: "#21005D", dark: "#EADDFF")

        // Secondary
        public static let secondary = Color.dynamic(light: "#625B71", dark: "#CCC2DC")
        public static let onSecondary = Color.dynamic(light: "#FFFFFF", dark: "#332D41")
        public static let secondaryContainer = Color.dynamic(light: "#E8DEF8", dark: "#4A4458")
        public static let onSecondaryContainer = Color.dynamic(light: "#1D192B", dark: "#E8DEF8")

        // Tertiary
        public static let tertiary = Color.dynamic(light: "#7D5260", dark: "#EFB8C8")
        public static let onTertiary = Color.dynamic(light: "#FFFFFF", dark: "#492532")
        public static let tertiaryContainer = Color.dynamic(light: "#FFD8E4", dark: "#633B48")
        public static let onTertiaryContainer = Color.dynamic(light: "#31111D", dark: "#FFD8E4")

        // Surface Hierarchy
        public static let surface = Color.dynamic(light: "#FEF7FF", dark: "#141218")
        public static let surfaceContainerLowest = Color.dynamic(light: "#FFFFFF", dark: "#0F0D13")
        public static let surfaceContainerLow = Color.dynamic(light: "#F7F2FA", dark: "#1D1B20")
        public static let surfaceContainer = Color.dynamic(light: "#F3EDF7", dark: "#211F26")
        public static let surfaceContainerHigh = Color.dynamic(light: "#ECE6F0", dark: "#2B2930")
        public static let surfaceContainerHighest = Color.dynamic(light: "#E6E0E9", dark: "#36343B")

        // On Surface & Outlines
        public static let onSurface = Color.dynamic(light: "#1D1B20", dark: "#E6E0E9")
        public static let onSurfaceVariant = Color.dynamic(light: "#49454F", dark: "#CAC4D0")
        public static let outline = Color.dynamic(light: "#79747E", dark: "#938F99")
        public static let outlineVariant = Color.dynamic(light: "#CAC4D0", dark: "#49454F")

        // Error & Feedback
        public static let error = Color.dynamic(light: "#B3261E", dark: "#F2B8B5")
        public static let onError = Color.dynamic(light: "#FFFFFF", dark: "#601410")
        public static let errorContainer = Color.dynamic(light: "#F9DEDC", dark: "#8C1D18")
        public static let onErrorContainer = Color.dynamic(light: "#410E0B", dark: "#F9DEDC")

        // Accents & Gradients
        public static let gradientStart = Color.dynamic(light: "#EADCFF", dark: "#3B2D54")
        public static let gradientEnd = Color.dynamic(light: "#D8E7FF", dark: "#1E334D")
        public static let success = Color.dynamic(light: "#2E7D32", dark: "#81C784")
        public static let warning = Color.dynamic(light: "#EF6C00", dark: "#FFB74D")
    }

    // MARK: - Shapes
    public enum Shapes {
        /// RoundedRectangle cornerRadius: 32 for primary cards
        public static let card = RoundedRectangle(cornerRadius: 32, style: .continuous)
        /// RoundedRectangle cornerRadius: 24 for internal sections and panels
        public static let section = RoundedRectangle(cornerRadius: 24, style: .continuous)
        /// RoundedRectangle cornerRadius: 34 for floating bottom dock capsule
        public static let dockCapsule = RoundedRectangle(cornerRadius: 34, style: .continuous)
        /// RoundedRectangle cornerRadius: 28 for inner dock selection pill
        public static let dockPill = RoundedRectangle(cornerRadius: 28, style: .continuous)
        /// RoundedRectangle cornerRadius: 18 for badges and status tags
        public static let badge = RoundedRectangle(cornerRadius: 18, style: .continuous)
        /// RoundedRectangle cornerRadius: 16 for interactive chips and inputs
        public static let chip = RoundedRectangle(cornerRadius: 16, style: .continuous)
    }

    // MARK: - Motion Scheme (Expressive Spring Curves)
    public enum Motion {
        /// Default expressive spatial spring for layout changes and card expansions
        public static let expressive = Animation.spring(response: 0.38, dampingFraction: 0.72, blendDuration: 0)
        /// Snappy feedback spring for button presses and micro-interactions
        public static let snappy = Animation.spring(response: 0.22, dampingFraction: 0.86, blendDuration: 0)
        /// Bouncy spring for achievements and success state indicators
        public static let bouncy = Animation.spring(response: 0.46, dampingFraction: 0.62, blendDuration: 0)
        /// Smooth effect transition spring
        public static let effects = Animation.spring(response: 0.28, dampingFraction: 0.82, blendDuration: 0)
    }
}

// MARK: - Custom View Modifiers & Styles

public struct M3CardModifier: ViewModifier {
    var backgroundColor: Color = M3E.Colors.surfaceContainerLow
    var cornerRadius: CGFloat = 32
    var shadowRadius: CGFloat = 6
    var shadowY: CGFloat = 3
    var borderColor: Color = M3E.Colors.outlineVariant.opacity(0.4)

    public func body(content: Content) -> some View {
        content
            .background(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(backgroundColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .strokeBorder(borderColor, lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.04), radius: shadowRadius, x: 0, y: shadowY)
    }
}

public struct M3TonalCardModifier: ViewModifier {
    var backgroundColor: Color = M3E.Colors.surfaceContainerHigh
    var cornerRadius: CGFloat = 24

    public func body(content: Content) -> some View {
        content
            .background(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(backgroundColor)
            )
    }
}

public extension View {
    func m3Card(
        backgroundColor: Color = M3E.Colors.surfaceContainerLow,
        cornerRadius: CGFloat = 32,
        shadowRadius: CGFloat = 6,
        shadowY: CGFloat = 3,
        borderColor: Color = M3E.Colors.outlineVariant.opacity(0.4)
    ) -> some View {
        modifier(M3CardModifier(
            backgroundColor: backgroundColor,
            cornerRadius: cornerRadius,
            shadowRadius: shadowRadius,
            shadowY: shadowY,
            borderColor: borderColor
        ))
    }

    func m3SectionCard(
        backgroundColor: Color = M3E.Colors.surfaceContainerLow
    ) -> some View {
        m3Card(backgroundColor: backgroundColor, cornerRadius: 24, shadowRadius: 4, shadowY: 2)
    }

    func m3TonalCard(
        backgroundColor: Color = M3E.Colors.surfaceContainerHigh,
        cornerRadius: CGFloat = 24
    ) -> some View {
        modifier(M3TonalCardModifier(backgroundColor: backgroundColor, cornerRadius: cornerRadius))
    }
}
