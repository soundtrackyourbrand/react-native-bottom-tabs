---
'@soundtrackio/react-native-bottom-tabs': patch
---

Declare `@expo/config-plugins`, which the Expo config plugin imports, as an optional peer dependency, so the plugin loads under package managers that do not hoist undeclared dependencies
