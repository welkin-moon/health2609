/**
 * TodayPage - HarmonyOS NEXT Today Page Entry
 * Delegates to TodayView for unified responsive multi-screen layout
 */

import { TodayView } from './TodayView';

@Component
export struct TodayPage {
  build() {
    TodayView()
  }
}
