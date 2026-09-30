/**
 * Material 3 Expressive (M3E) Design System Token Architecture
 * Unified design specification for HarmonyOS NEXT & OpenHarmony / KaihongOS
 * health2609 ("一餐一动") Campus Health Platform
 */

export class M3EColors {
  // Primary Palette (Vibrant Expressive Deep Teal)
  static readonly primary: string = '#006A6A';
  static readonly onPrimary: string = '#FFFFFF';
  static readonly primaryContainer: string = '#70F7E7';
  static readonly onPrimaryContainer: string = '#002020';

  // Secondary Palette (Adaptive Soft Teal / Mint)
  static readonly secondary: string = '#4A6363';
  static readonly onSecondary: string = '#FFFFFF';
  static readonly secondaryContainer: string = '#CCE8E7';
  static readonly onSecondaryContainer: string = '#051F1F';

  // Tertiary Palette (Warm Blue-Gray Accent)
  static readonly tertiary: string = '#4B607C';
  static readonly onTertiary: string = '#FFFFFF';
  static readonly tertiaryContainer: string = '#D3E4FF';
  static readonly onTertiaryContainer: string = '#041C35';

  // Surface Palette & Elevation Containers
  static readonly surface: string = '#FAFDFB';
  static readonly background: string = '#FAFDFB';
  static readonly onSurface: string = '#191C1C';
  static readonly onSurfaceVariant: string = '#3F4948';
  static readonly surfaceContainerLowest: string = '#FFFFFF';
  static readonly surfaceContainerLow: string = '#F2F5F4';
  static readonly surfaceContainer: string = '#ECEFEF';
  static readonly surfaceContainerHigh: string = '#E7EAEA';
  static readonly surfaceContainerHighest: string = '#E1E4E4';

  // Outlines & Dividers
  static readonly outline: string = '#6F7979';
  static readonly outlineVariant: string = '#BEC9C8';

  // Semantic Status Colors
  static readonly error: string = '#BA1A1A';
  static readonly onError: string = '#FFFFFF';
  static readonly errorContainer: string = '#FFDAD6';
  static readonly onErrorContainer: string = '#410002';

  static readonly success: string = '#006D44';
  static readonly onSuccess: string = '#FFFFFF';
  static readonly successContainer: string = '#98F7BF';
  static readonly onSuccessContainer: string = '#002111';
  static readonly successGreen: string = '#006D44';
  static readonly successGreenContainer: string = '#98F7BF';

  // High-contrast accessibility tokens for Smart Campus Terminals & Student Tablets
  static readonly highContrastText: string = '#000000';
  static readonly highContrastTextSecondary: string = '#212121';
  static readonly highContrastBorder: string = '#004D40';
  static readonly highContrastSurface: string = '#FFFFFF';
  static readonly highContrastContainer: string = '#E0F2F1';
  static readonly accessibleFocusRing: string = '#004D40';
}

export class M3EShapes {
  /** M3E Large Card shape: 32vp */
  static readonly cardRadius: number = 32;

  /** M3E Inner Section / Sub-card shape: 24vp */
  static readonly sectionRadius: number = 24;
  static readonly innerSectionRadius: number = 24;

  /** M3E Floating Capsule Dock shape: 34vp */
  static readonly dockRadius: number = 34;
  static readonly floatingDockRadius: number = 34;

  /** M3E Inner Selected Pill shape: 28vp */
  static readonly pillRadius: number = 28;
  static readonly selectedPillRadius: number = 28;

  /** M3E Filter / Action Chip shape: 18vp */
  static readonly chipRadius: number = 18;

  /** Button & Input Radius: 16vp */
  static readonly buttonRadius: number = 16;

  /** Badge & Metric Small Radius: 12vp */
  static readonly badgeRadius: number = 12;
  static readonly smallRadius: number = 12;

  /** Pill full round: 999vp */
  static readonly fullRound: number = 999;
}

export class M3EAnimation {
  /** Fast spring transition duration */
  static readonly springDurationMs: number = 320;

  /** Standard duration */
  static readonly standardDurationMs: number = 250;

  /** Quick micro-interaction duration */
  static readonly quickDurationMs: number = 180;

  /** Default curve */
  static readonly defaultCurve: Curve = Curve.FastOutSlowIn;

  /** Elastic spring transition curve */
  static readonly springCurve: Curve = Curve.EaseInOut;
}

export class M3ETypography {
  static readonly headlineLarge: number = 28;
  static readonly headlineMedium: number = 24;
  static readonly headlineSmall: number = 20;

  static readonly titleLarge: number = 18;
  static readonly titleMedium: number = 16;
  static readonly titleSmall: number = 14;

  static readonly bodyLarge: number = 16;
  static readonly bodyMedium: number = 14;
  static readonly bodySmall: number = 12;

  static readonly labelLarge: number = 14;
  static readonly labelMedium: number = 12;
  static readonly labelSmall: number = 10;
}

/**
 * Generic Breakpoint Type for ArkUI adaptive layouts
 * Handheld (sm: <600vp), Tablet / Split (md: 600-840vp), Terminal / Desktop (lg: >=840vp)
 */
export class BreakpointType<T> {
  sm: T;
  md: T;
  lg: T;

  constructor(sm: T, md: T, lg: T) {
    this.sm = sm;
    this.md = md;
    this.lg = lg;
  }

  getValue(breakpoint: string): T {
    if (breakpoint === 'lg') {
      return this.lg;
    }
    if (breakpoint === 'md') {
      return this.md;
    }
    return this.sm;
  }
}