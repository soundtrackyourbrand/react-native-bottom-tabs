---
'react-native-bottom-tabs': patch
---

Fix an iOS crash (`UIViewControllerHierarchyInconsistency`) when a tab is hidden and shown again while its content is managed by a child view controller, e.g. a react-native-screens native stack
