# 更新日志

## 8.6.0-r3

运行时改为 Tyranor 第三方运行时模块。**包名已改为** `com.tyranor.runtime.renpy86`，所以 r3 会作为一个新应用安装，和 r2 并存；装过 r2（`studio.tsatoru.renpy86`）的话请自行卸载。

- 改用 Tyranor 的模块启动协议（`com.tyranor.runtime.action.LAUNCH`，以及 `FOLDER_PATH`、`GAME_TITLE`、`SETTINGS`）。删除了 JoiPlay 式的 `studio.tsatoru.renpy86.RUN` 入口。
- 新增模块描述 `res/raw/tyranor_runtime.json`：Tyranor 据此识别 Ren'Py 8.6 及以后版本的游戏（同时有 `game/` 和 `renpy/asynctask.py*` 的目录）、显示单游戏设置，并管理 `<游戏目录>/saves`。
- 设置改为在 Tyranor 中按游戏单独设置。「硬件视频解码」和「自动存档」可以跟随游戏，也可以强制开或关。
- 不再自行设置 `SDL_ANDROID_BLOCK_ON_PAUSE`：Ren'Py 的 `librenpython` 在 SDL 启动前已经设好了。r2 说明里写的「Ren'Py 设置得太晚」是错的。

## 8.6.0-r2

安装包已撤下，请使用 r3。

- 修复：游戏切到后台时主线程会卡 8 秒，而且 Ren'Py 要等游戏回到前台才存档。现在 SDL 提示在 SDL 启动前设置（`SDL_ANDROID_BLOCK_ON_PAUSE=0`，当时以为 Ren'Py 设置得太晚，见 r3），并在 SDL 暂停时停掉 Ren'Py 的定时器，让 SDL 能送达进入后台的事件。`onStop` 现在约 1 秒结束，包括 Ren'Py 存档的时间。
- `SDL_ANDROID_TRAP_BACK_BUTTON` 现在真正能被 SDL 读到了（之前是在 SDL 复制环境变量之后才设置的）。

## 8.6.0-r1

第一个版本。安装包已撤下，请使用 r3。

- 使用官方 RAPT 和 SDK 压缩包中的 Ren'Py 8.6.0.26111403（SDL3、Python 3.12），arm64-v8a。
- 启动协议支持兼容 JoiPlay 的参数（`game`、`settings`）和普通参数（`folder`、`savedir` 等），见 PROTOCOL.md。
- 存档位于 `<游戏目录>/saves`（或 `savedir` 指定的目录）；日志位于 `Android/data/<包名>/files/logs/<id>/`。
- 设置项：减少内存占用、减少画面刷新、垂直同步、手机界面变体、重新编译、硬件视频解码、自动存档。
- 每个游戏在独立的 `:game` 进程中运行，游戏退出时进程也随之结束；启动另一个游戏会先结束前一个，再次启动同一个游戏会切回原来的进程。
- 返回手势和返回键会像在 Ren'Py 中一样执行回滚，而不是关闭游戏（Android 16 只通过返回分发器传递返回操作，否则 SDL3 会直接关闭 Activity）。
