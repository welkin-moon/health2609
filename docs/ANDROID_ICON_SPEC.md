# Android Adaptive Icon Specification for 一餐一动 (health2609)

## 1. Canvas & Safe Zone Dimensions
- **Total Canvas Size**: 108dp x 108dp (viewBox 0 0 108 108, Center: 54, 54).
- **Safe Zone (Mask-Safe Area)**: A circle of diameter 66dp centered at (54, 54), radius = 33dp.
- **Active Graphic Bounds**:
  - Horizontal: x in [31, 77] (width: 46dp, centered horizontally at x=54).
  - Vertical: y in [25.5, 77] (height: 51.5dp, optical center at y=51.25, with ~2.7dp upward optical compensation).
  - Maximum distance from center (54, 54): **30.20dp**, strictly within the 33dp safe radius.

## 2. Visual Identity & Meaning
- **餐 (Nutrition / Meal)**: Rounded healthy bowl geometry (y=51.5 to 77) serving as a stable, grounding foundation.
- **生机 (Vitality / Health)**: Upward-growing green leaf / sprout (y=27 to 47), representing vitality, nutrition, and natural growth.
- **动 (Movement / Energy)**: Paired aerodynamic motion curves (left: y=26~43, right: y=26~43), symbolizing active motion, sprint, and energy burn.

## 3. Supported Android Versions & Icon Layers
- **API 26+ (Adaptive Icons)**:
  - Background: @color/ic_launcher_background (#D8F5E8 mint green)
  - Foreground: @drawable/ic_launcher_foreground (@color/ic_launcher_foreground, #0C5F49 forest teal)
- **API 33+ (Monochrome / Themed Icons)**:
  - Monochrome: @drawable/ic_launcher_monochrome (#000000 tinted dynamically by Android Material You theme)
