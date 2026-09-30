/**
 * PrivacyPage - HarmonyOS NEXT Privacy Page Entry
 * Delegates to PrivacyView for unified k-anonymity and trust boundaries
 */

import { PrivacyView } from './PrivacyView';

@Component
export struct PrivacyPage {
  build() {
    PrivacyView()
  }
}
