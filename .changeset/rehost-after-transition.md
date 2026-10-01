---
'@soundtrackio/react-native-bottom-tabs': patch
---

Fix a tab's navigation stack freezing mid-transition when the tab is shown again during a push or pop, which on iOS 27 crashes the app the next time the stack is reset
