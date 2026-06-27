# RealRandomMusic

<p align="center"><i>🤖 Code by DeepSeek V4 Pro · Idea by <a href="https://github.com/XPYEX">XPYEX</a></i></p>

真正的随机播放 —— 自动检测并跳过重复歌曲，让音乐播放器真正做到随机<br>
反正我的App的随机播放老是重复，，，<br>
我就是要0重复！<br>

## 功能

- **自动跳过重复歌曲** — 通过监听系统媒体会话，检测到重复播放时自动切歌
- **兼容所有音乐应用** — 基于 Android MediaSession，无需适配具体播放器
- **白名单选择** — 自由选择需要监听的应用，只对你关心的音乐 App 生效
- **车载蓝牙兼容** — 车载模式修复蓝牙音频元数据反转问题
- **灵活配置** — 可设置每首歌最多播放次数、重复间隔时长、歌单总曲数等

## 工作原理

```
音乐 App 播放歌曲
    → 系统发出媒体通知
    → RealRandomMusic 通过 MediaSessionManager 捕获
    → 记录歌曲历史
    → 检测到重复 → 自动执行"下一首"
```
其实就是雇佣我帮你看看歌放过没有。。。

## 安全
本App代码已全部开源，且除检测更新外，无联网部分<br>
(你关掉联网权限也没问题，只是会收不到更新提示)


## 构建

```bash
./gradlew assembleDebug
```

最低要求 Android 12 (API 31)。
