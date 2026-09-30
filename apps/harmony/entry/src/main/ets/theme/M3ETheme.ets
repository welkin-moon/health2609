/**
 * Material 3 Expressive (M3E) Design System for HarmonyOS NEXT
 * Unified with Android and Web versions of health2609 ("一餐一动")
 */

export class M3EColors {
  // Primary
  static readonly primary: string = '#6750A4';
  static readonly onPrimary: string = '#FFFFFF';
  static readonly primaryContainer: string = '#EADDFF';
  static readonly onPrimaryContainer: string = '#21005D';

  // Secondary
  static readonly secondary: string = '#625B71';
  static readonly onSecondary: string = '#FFFFFF';
  static readonly secondaryContainer: string = '#E8DEF8';
  static readonly onSecondaryContainer: string = '#1D192B';

  // Tertiary
  static readonly tertiary: string = '#7D5260';
  static readonly onTertiary: string = '#FFFFFF';
  static readonly tertiaryContainer: string = '#FFD8E4';
  static readonly onTertiaryContainer: string = '#31111D';

  // Surface & Containers
  static readonly surface: string = '#FEF7FF';
  static readonly surfaceContainerLow: string = '#F7F2FA';
  static readonly surfaceContainer: string = '#F3EDF7';
  static readonly surfaceContainerHigh: string = '#ECE6F0';
  static readonly surfaceContainerHighest: string = '#E6E0E9';
  static readonly onSurface: string = '#1D1B20';
  static readonly onSurfaceVariant: string = '#49454F';

  // Outline
  static readonly outline: string = '#79747E';
  static readonly outlineVariant: string = '#CAC4D0';

  // Error
  static readonly error: string = '#B3261E';
  static readonly onError: string = '#FFFFFF';
  static readonly errorContainer: string = '#F9DEDC';
  static readonly onErrorContainer: string = '#410E0B';

  // Neutrals & Accents
  static readonly background: string = '#FEF7FF';
  static readonly successGreen: string = '#1B8755';
  static readonly successGreenContainer: string = '#D1E7DD';
}

export class M3EShapes {
  /** M3E Large Card shape: 32vp */
  static readonly cardRadius: number = 32;

  /** M3E Inner Section / Sub-card shape: 24vp */
  static readonly sectionRadius: number = 24;

  /** M3E Floating Capsule Dock shape: 34vp */
  static readonly dockRadius: number = 34;

  /** M3E Inner Selected Pill shape: 28vp */
  static readonly pillRadius: number = 28;

  /** M3E Filter / Action Chip shape: 18vp */
  static readonly chipRadius: number = 18;

  /** Small shape: 12vp */
  static readonly smallRadius: number = 12;

  /** Pill full round: 999vp */
  static readonly fullRound: number = 999;
}

export class M3EAnimation {
  /** Fast spring transition duration */
  static readonly springDurationMs: number = 320;

  /** Standard duration */
  static readonly standardDurationMs: number = 250;

  /** Default spring curve */
  static readonly defaultCurve: Curve = Curve.FastOutSlowIn;
}
