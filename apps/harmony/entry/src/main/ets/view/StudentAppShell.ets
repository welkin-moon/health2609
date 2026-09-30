/**
 * StudentAppShell - HarmonyOS NEXT Shell Entry
 * Delegates to AdaptiveStudentAppShell for multi-screen & tablet responsiveness
 */

import { AdaptiveStudentAppShell } from './AdaptiveStudentAppShell';

@Component
export struct StudentAppShell {
  build() {
    AdaptiveStudentAppShell()
  }
}
