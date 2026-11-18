# JoiPlay's virtual gamepad

The on-screen gamepad of [JoiPlay](https://joiplay.cyou), used by `GameActivity` (see
`app/src/main/java/studio/tsatoru/renpy86/RenPyGamePad.java` for its Ren'Py keys). Each
directory is a copy of a public JoiPlay repository at the commit below, under its own
LICENSE (MIT):

| Directory | Repository | Commit |
|---|---|---|
| `joipad` | <https://github.com/joiplay/joipad> | `0a74ea17e224e6ef256c56d3eb5807de1a3df4c8` |
| `commons` | <https://github.com/joiplay/commons> | `a389ce27960cc665b5fa05a9cf8f864ca38949f3` |
| `sdp` | <https://github.com/joiplay/sdp> | `cf241ad766fcd26fb22ebb8014a6218abadef7b0` |

Changes, all for the Android Gradle Plugin 8 build of this repository:

- `sdp`: the package moved from `AndroidManifest.xml` to `namespace` in `build.gradle`.
- `joipad/.../util/ViewUtils.java`: `sdpToPx` looks the dimensions up in
  `com.intuit.sdp.R` instead of joipad's own `R`, which no longer includes other
  modules' resources (non-transitive R classes).
