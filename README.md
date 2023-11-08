# AndroidAirPlayServer
一个基于安卓原生的 为了跑在x2上面做了兼容的 基于其他开源项目的airplay投屏应用

感谢 https://github.com/serezhka 的投屏基础库

1. 在雷鸟上面使用adb命令安装debug app或clone本项目后直接run app到设备上 打开后会自动运行
2. 在ios设备上下拉控制台选择屏幕镜像 选择visionPro即可（目前设置的这个名称 这个名称可以自行在MainActivity自行修改 SERVER_NAME ）
3. 注意目前只完成了画面镜像功能 音频目前有问题 每次投屏结束后需要杀掉app重新进入开启投屏 同时投屏需要x2和iPhone接入同一个wifi局域网内