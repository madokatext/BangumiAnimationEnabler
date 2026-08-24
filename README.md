# Bangumi Animation Enabler

一个只作用于 [`com.czy0729.bangumi`](https://github.com/czy0729/Bangumi) 的 LSPosed 模块。

当 Android 开发者选项中的动画缩放被设为 `0` 时，本模块让 Bangumi 进程继续看到正常的 `1x` 动画环境，从而恢复 React Native、Reanimated 和 Android `ValueAnimator` 动画。

## 原理

模块在 Bangumi 进程启动的最早阶段处理以下入口：

- 将 `ValueAnimator.setDurationScale(0)` 改为 `ValueAnimator.setDurationScale(1)`；
- 让 `ValueAnimator.getDurationScale()` 返回 `1`；
- 让 `ValueAnimator.areAnimatorsEnabled()` 返回 `true`；
- 仅对三个动画缩放键，让 `Settings.Global` 的字符串、浮点数和整数读取返回 `1`：
  - `window_animation_scale`
  - `transition_animation_scale`
  - `animator_duration_scale`

其中 `transition_animation_scale` 的字符串读取是 React Native 0.81.4 与 Reanimated 4.1.2 判断“减少动态效果”的关键入口。

模块面向 Android 15（API 35）构建，并针对 LSPosed 1.11.0 提供的 libxposed API 100 ABI。静态作用域中只有 Bangumi，不会改变其他普通应用进程看到的动画设置，也不会修改系统设置数据库。

构建工具采用 Android 官方为 API 35 给出的兼容组合：AGP 8.7.3、Gradle 8.9、JDK 17。

GitHub Actions 会从 libxposed 官方仓库检出固定提交并先把 API 100 发布到任务内的 Maven Local，然后再构建模块；不会错误解析到构造器和 Hook ABI 均不兼容的 API 101/102。

## GitHub Actions 构建

1. 新建一个 GitHub 仓库并上传本目录下的全部文件。
2. 打开仓库的 **Actions** 页面。
3. 选择 **Build APK**，点击 **Run workflow**。
4. 构建完成后，从该次任务的 **Artifacts** 下载 `BangumiAnimationEnabler-release`。
5. 解压 artifact，安装其中的 `app-release.apk`。

工作流生成的 release APK 使用 Android 默认调试证书签名，便于直接安装。若将来需要发布稳定版本，应改用自己的持久化签名。

## 使用

1. 安装构建出的 APK。
2. 在 LSPosed 中启用模块。静态作用域会限定为 **Bangumi**。
3. 强制停止 Bangumi，随后重新打开；若仍未生效，重启设备一次。

模块没有配置界面。停用模块后，再次强制停止并打开 Bangumi，即可恢复跟随系统动画缩放。

## 边界

Bangumi 的页面切换主要发生在单个 React Native Activity 内，因此上述处理覆盖其实际使用的应用内动画路径。由 Android `system_server` 绘制的跨应用窗口/任务切换动画仍属于系统全局窗口动画；本模块不会为了改变它而注入系统框架进程。

另外，Android 也可能因省电策略把进程内 Animator 比例降为 `0`。本模块会同样将 Bangumi 进程中的该比例固定为 `1x`。
