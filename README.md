# Moonlight Android阿西西修改版
## 此项目实现对官方Moonlight安卓端功能修改<br>
### 如果你特别喜欢某些功能，可以自行提取相关代码，提交到官方库合并请求，无需备注来源于此项目<br>
### 最后切勿将此项目代码用于商业软件中。<br>
### 如果喜欢数码和游戏，可以关注我B站账号(https://space.bilibili.com/16893379) 、Youtube(https://www.youtube.com/@AxixiTV)

此项目版本主要实现如下功能：<br>
1、自定义虚拟按键 支持导入导出<br>
2、自定义分辨率<br>
3、自定义码率<br>
4、多种鼠标模式切换（普通鼠标、多点触控、触控板、禁用触屏操作、本地鼠标模式）<br>
5、虚拟手柄皮肤优化、自由摇杆<br>
6、外接显示器模式<br>
7、适配joycon十字键<br>
8、精简的性能信息显示<br>
9、游戏快捷菜单选项<br>
10、自定义快捷指令<br>
11、方便切换软键盘<br>
12、竖屏模式<br>
13、SBS娱乐模式<br>
14、画面置顶显示 折叠屏有点用<br>
15、虚拟触控板空间及灵敏度调节，游玩右键转视角游戏。例如魔兽<br>
16、强制使用设备本身的震动马达（可能你的手柄震动效果不佳）<br>
17、阿西西手柄调试页面 查看手柄震动及陀螺仪信息、安卓内核版本信息<br>



# Moonlight Android

[![AppVeyor Build Status](https://ci.appveyor.com/api/projects/status/232a8tadrrn8jv0k/branch/master?svg=true)](https://ci.appveyor.com/project/cgutman/moonlight-android/branch/master)
[![Translation Status](https://hosted.weblate.org/widgets/moonlight/-/moonlight-android/svg-badge.svg)](https://hosted.weblate.org/projects/moonlight/moonlight-android/)

[Moonlight for Android](https://moonlight-stream.org) is an open source client for NVIDIA GameStream and [Sunshine](https://github.com/LizardByte/Sunshine).

Moonlight for Android will allow you to stream your full collection of games from your Windows PC to your Android device,
whether in your own home or over the internet.

Moonlight also has a [PC client](https://github.com/moonlight-stream/moonlight-qt) and [iOS/tvOS client](https://github.com/moonlight-stream/moonlight-ios).

You can follow development on our [Discord server](https://moonlight-stream.org/discord) and help translate Moonlight into your language on [Weblate](https://hosted.weblate.org/projects/moonlight/moonlight-android/).

## Downloads
* [Google Play Store](https://play.google.com/store/apps/details?id=com.limelight)
* [Amazon App Store](https://www.amazon.com/gp/product/B00JK4MFN2)
* [F-Droid](https://f-droid.org/packages/com.limelight)
* [APK](https://github.com/moonlight-stream/moonlight-android/releases)

## Building
* Install Android Studio and the Android NDK
* Run ‘git submodule update --init --recursive’ from within moonlight-android/
* In moonlight-android/, create a file called ‘local.properties’. Add an ‘ndk.dir=’ property to the local.properties file and set it equal to your NDK directory.
* Build the APK using Android Studio or gradle

## Authors

* [Cameron Gutman](https://github.com/cgutman)  
* [Diego Waxemberg](https://github.com/dwaxemberg)  
* [Aaron Neyer](https://github.com/Aaronneyer)  
* [Andrew Hennessy](https://github.com/yetanothername)

Moonlight is the work of students at [Case Western](http://case.edu) and was
started as a project at [MHacks](http://mhacks.org).
