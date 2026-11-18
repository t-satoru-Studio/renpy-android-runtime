# 启动协议

本运行时是一个 Tyranor 第三方运行时模块（协议 1）。协议本身见 Tyranor 的模块开发指南；本页列出本运行时声明了什么，以及每个值是怎么读取的。

## 身份

| | |
|---|---|
| 包名 | `com.tyranor.runtime.renpy86`（debug 构建：`com.tyranor.runtime.renpy86.debug`） |
| Action | `com.tyranor.runtime.action.LAUNCH`，category 为 `DEFAULT` |
| Activity | `studio.tsatoru.renpy86.LaunchActivity`（exported） |
| 模块描述 | 该 Activity 上的 `<meta-data android:name="com.tyranor.runtime.MANIFEST" android:resource="@raw/tyranor_runtime">` |
| Ren'Py 构建 | `<meta-data android:name="studio.tsatoru.renpy86.RENPY_BUILD">`，例如 `8.6.1.26111705` |
| versionCode | `MMmmppRR`：Ren'Py 主版本、次版本、补丁版本和运行时修订号（8.6.1 r1 = `8060101`） |

8.6.0-r2 及之前的包名是 `studio.tsatoru.renpy86`，用 action `studio.tsatoru.renpy86.RUN` 和 JoiPlay 式参数启动。这个入口已经删除；新旧两个包可以同时安装。

## 模块描述

[app/src/main/res/raw/tyranor_runtime.json](app/src/main/res/raw/tyranor_runtime.json)：

- **扫描**：`match: all`，标记为 `game` 和 `renpy/asynctask.py*`。`renpy/asynctask.py` 从 Ren'Py 8.6.0 开始才有，所以用 8.5 及更早版本制作的游戏不会被认领。末尾的 `*` 也能匹配只带 `.pyc` 文件的发行版。
- **设置**（版本 1），见下文。
- **存档**：目录 `saves`，即 Ren'Py 写入存档的位置（`<游戏目录>/saves`）。

## 启动参数

| 参数 | 用途 |
|---|---|
| `com.tyranor.runtime.extra.FOLDER_PATH` | 游戏的绝对路径，必填。可以是游戏根目录（包含 `game/` 的那一层），也可以是 `game/` 本身。 |
| `com.tyranor.runtime.extra.GAME_TITLE` | 可选，默认取文件夹名。 |
| `com.tyranor.runtime.extra.PROTOCOL_VERSION` | 只写入日志。 |
| `com.tyranor.runtime.extra.SETTINGS` | `{"version": 1, "values": {...}}`。不是 JSON 或版本不对的载荷整份忽略；已知键的值类型不对时使用默认值；未知键忽略。 |

## 设置

Ren'Py 相关的设置项，默认值不改变任何行为：使用默认值时，一切由 Ren'Py 和游戏自己决定。`virtual_keys` 和 `gamepad` 只控制运行时自己画在游戏上面的按键。

| 键 | 类型、默认值 | 效果 |
|---|---|---|
| `hw_video` | `game` / `on` / `off`，默认 `game` | 在游戏的初始化代码之后设置 `config.hw_video` |
| `autosave` | `game` / `on` / `off`，默认 `game` | 在游戏的初始化代码之后设置 `config.has_autosave` 和 `config.autosave_on_choice` |
| `phone_small_variant` | 布尔，默认 `false` | 为 `true` 时强制使用 `small phone touch android mobile` 界面变体 |
| `vsync` | 布尔，默认 `true` | 为 `false` 时设置 `RENPY_GL_VSYNC=0` |
| `less_memory` | 布尔，默认 `false` | 为 `true` 时设置 `RENPY_LESS_MEMORY` |
| `less_updates` | 布尔，默认 `false` | 为 `true` 时设置 `RENPY_LESS_UPDATES` |
| `recompile` | 布尔，默认 `false` | 为 `true` 时以 `--compile` 运行 Ren'Py（把 `.rpy` 重新编译为 `.rpyc`） |
| `virtual_keys` | 布尔，默认 `true` | 为 `false` 时不显示屏幕右侧的 ☰ 虚拟按键面板 |
| `gamepad` | 布尔，默认 `true` | 为 `false` 时不显示 JoiPlay 虚拟手柄。键位保存在 `<游戏目录>/gamepad.json`，与 JoiPlay 通用 |

## 文件

- 存档：`<游戏目录>/saves`。Ren'Py 8.6 修复权限时，可能会在旁边短暂创建 `saves.renpy-repair` 或 `saves.renpy-replaced`。
- 日志：`Android/data/<包名>/files/logs/<id>/log.txt` 和 `traceback.txt`，其中 `<id>` 是文件夹路径的 Java `hashCode()` 的十六进制形式。
- 如果游戏要编译脚本或写缓存，游戏目录需要可写。

## 存储权限

运行时从共享存储读取游戏，所以需要「所有文件访问」权限（Android 11 及以上）或存储权限（Android 6–10）；Tyranor 自己的权限不会传递过来。缺少权限时，`LaunchActivity` 会说明原因并打开系统设置页，用户返回后继续启动。

## 进程

一个进程里只能启动一次 Python。游戏运行在 `:game` 进程中；启动另一个游戏会先结束前一个，再次启动同一个游戏则切回原来的进程。游戏退出时，进程也随之结束。

## 示例

```sh
adb shell am start -a com.tyranor.runtime.action.LAUNCH \
  -n com.tyranor.runtime.renpy86/studio.tsatoru.renpy86.LaunchActivity \
  --es com.tyranor.runtime.extra.FOLDER_PATH /sdcard/Games/MyGame \
  --es com.tyranor.runtime.extra.SETTINGS '{"version":1,"values":{"less_memory":true}}'
```
