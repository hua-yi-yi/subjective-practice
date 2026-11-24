# 主观题练习

离线 Android 主观题练习 App，支持编辑题干、文字作答、答题卡、本地倒计时和自定义水印。题目中的 Vue 标签与插值语法按纯文本显示。题目和答案保存在设备上。

## 安装

在 [Releases](https://github.com/hua-yi-yi/subjective-practice/releases/latest) 下载 APK，使用 Android 6.0 或更高版本的设备安装。

## 从源码构建

在 Windows 上准备 PowerShell 7.3 或更高版本、JDK 17，以及 Android SDK Platform 35 和 Build-Tools 35.0.0。将 JDK 的命令行工具加入 PATH，随后运行：

~~~powershell
pwsh -File ./build.ps1 -SdkRoot "C:\Android\Sdk"
~~~

如果已设置 ANDROID_HOME，可以省略 SdkRoot 参数。安装包输出到 build/subjective-practice.apk。脚本使用本地开发签名，安装开发包时请使用独立的测试设备或模拟器。
