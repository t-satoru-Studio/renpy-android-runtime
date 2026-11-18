# 更新日志

## 8.6.1-r2

只改说明文字和文档，引擎和功能与 8.6.1-r1 相同。

- 说明里的「Tyranor」改为「Tyranor Next」：这个运行时是 Tyranor Next 的模块，不是 Tyranor 的。信息对话框、错误提示、README、PROTOCOL 和更新日志都已更正。
- 模块入口的说法跟上 Tyranor Next 当前版本：「工具 → 引擎模块管理」（旧版是侧栏「妙妙工具」）。
- README 新增「安装与配置」：用真机截图一步步演示授权、启用模块、扫描游戏、单游戏设置，以及游戏里的加载画面、屏幕虚拟按键和手柄。截图在 `docs/images/`。

## 8.6.1-r1

升级到 Ren'Py **8.6.1.26111705**，并包含原定的 8.6.0-r4（没有单独发布）的全部内容。

- Ren'Py 8.6.1 修复了切到后台时卡 8 秒、后台不存档的问题（renpy/renpy#7534），所以删除了本运行时停定时器的绕过。
- 8.6.1 还修复了：游戏在后台被系统结束后，再次打开时回不到原来的位置；手机上 shift+R 重载或 `renpy.utter_restart` 之后黑屏；以及若干与 Android 无关的问题，见 Ren'Py 的更新说明。
- 返回键回滚的绕过保留：8.6.1 的更新说明列了这项修复（renpy/renpy#7533），但官方 RAPT 压缩包里并没有它。
- 新增更新检查：Tyranor Next 只检查模块版本够不够用，不会提示有新版本，所以运行时自己向 GitHub 查询本仓库的发布列表（预发布也算）。从桌面打开本应用时会检查，并在信息对话框里显示结果；游戏启动后每天最多在后台检查一次，有新版本时在画面下方短暂提示，可以打开发布页，点 ✕ 后这个版本不再提示。为此新增了网络权限，这是唯一的网络请求，不发送设备、游戏或玩家信息。
- 新增设置项「检查运行时更新」（`update_check`），默认开启，关闭后游戏中不再检查。

以下为原定的 8.6.0-r4 内容：

- 新增加载画面：从启动到 Ren'Py 画出第一帧，不再是黑屏。画面上显示当前阶段，读取脚本和执行初始化代码时显示进度以及正在处理的文件，已完成的阶段附带用时。进度由 `main.py` 前置代码里的一个后台线程每 0.1 秒采样一次 Ren'Py 的状态得到，不修改引擎，对启动时间没有可测出的影响。
- 新增屏幕虚拟按键：屏幕右侧的 ☰ 按钮展开一组按键，包括菜单、跳过、按住快进、自动、隐藏、历史、回退、前进、快速存档、快速读档。Ren'Py 有默认按键的功能会发送那个按键，所以游戏自己改过的按键设置照样生效；其余的由 Ren'Py 主线程直接执行。按住 ☰ 上下拖动可以移动位置，位置和展开状态会被记住。
- 新增 JoiPlay 虚拟手柄（[joiplay/joipad](https://github.com/joiplay/joipad)，MIT）：十字键、动作键、弹出式键盘、工具栏（关闭、旋转、静音、键盘、设置），从 ☰ 面板的「手柄」「工具栏」打开。默认键位按 Ren'Py 的默认按键配置，可以在手柄设置里修改，保存在 `<游戏目录>/gamepad.json`，与 JoiPlay 通用。
- 新增设置项「屏幕虚拟按键」（`virtual_keys`）和「JoiPlay 虚拟手柄」（`gamepad`），默认都开启。
- 手柄设置和关闭确认等对话框改用系统当前的对话框样式。

## 8.6.0-r3

运行时改为 Tyranor Next 第三方运行时模块。**包名已改为** `com.tyranor.runtime.renpy86`，所以 r3 会作为一个新应用安装，和 r2 并存；装过 r2（`studio.tsatoru.renpy86`）的话请自行卸载。

- 改用 Tyranor Next 的模块启动协议（`com.tyranor.runtime.action.LAUNCH`，以及 `FOLDER_PATH`、`GAME_TITLE`、`SETTINGS`）。删除了 JoiPlay 式的 `studio.tsatoru.renpy86.RUN` 入口。
- 新增模块描述 `res/raw/tyranor_runtime.json`：Tyranor Next 据此识别 Ren'Py 8.6 及以后版本的游戏（同时有 `game/` 和 `renpy/asynctask.py*` 的目录）、显示单游戏设置，并管理 `<游戏目录>/saves`。
- 设置改为在 Tyranor Next 中按游戏单独设置。「硬件视频解码」和「自动存档」可以跟随游戏，也可以强制开或关。
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
