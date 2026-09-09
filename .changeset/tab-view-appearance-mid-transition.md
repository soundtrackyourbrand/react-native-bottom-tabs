---
'react-native-bottom-tabs': patch
---

Fix missing safe-area insets on iOS when the tab view mounts while its parent screen is still transitioning in, which left the tab bar and the tabs' navigation bars under the status bar and home indicator
