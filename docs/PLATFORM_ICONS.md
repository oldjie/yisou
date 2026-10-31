# 内置平台图标来源

0.6.0 起内置七个平台的 256×256 图标，供安装与未安装状态一致展示。由维护者从以下原版 APK 的图标资源使用 Android 系统渲染生成，不运行原版应用代码。不随手机主题或应用版本自动改变。平台图标及商标属于各自权利人，不因项目代码采用 AGPL 而被重新授权。

| 图标 | 原版包名 / 版本 | PNG SHA-256 |
| --- | --- | --- |
| platform_douban.png | com.douban.frodo 7.136.0 | `873fedebaf8cdc832f4eb70d3f500d397fa5b6236ac20679dc39a936b9b77fc0` |
| platform_taobao.png | com.taobao.taobao 10.68.0 | `e8bab27669864624f6714ebd2a9c406f6c6cddb350b85845292794cdd6549f61` |
| platform_xianyu.png | com.taobao.idlefish 7.28.70 | `c736e422845c495406d33375a4248c5bb12d91cffac90cf5a5eeed5c3c7e6ecd` |
| platform_wechat.png | com.tencent.mm 8.0.79 | `63b3e7dd50d8e08308e1b19257ca527ebeeb97f3fce67f86549acd2d7966914a` |
| platform_bilibili.png | tv.danmaku.bili 9.16.1 | `d210b88eccad05224e09022a6d9f3eb5b67f65789f35e3e92d4d610ed2b86dc4` |
| platform_pinduoduo.png | com.xunmeng.pinduoduo 8.28.0 | `125020950c137239022a4e369ce11cc859898ffaceef559656acd8b766ff191a` |
| platform_xiaohongshu.png | com.xingin.xhs 9.52.0 | `45a48b9c69de013fa1bad5e3bf36a55593061dbf57c9c030094435df2ca5bbdb` |

来源：豆瓣、淘宝、闲鱼、哔哩哔哩、拼多多、小红书为各自应用宝渠道原版（https://sj.qq.com/）；微信为微信官方 Android 下载原版。原版 APK 不纳入本仓库或一搜安装包。

维护者可用 `tests/widget_fixture/IconExportActivity.java` 从本地原版 APK 资源导出；该自建工具属于一次性测试宿主，不随一搜发布。

原版 APK SHA-256：
- com.douban.frodo：`1afe9e805b54031d3c2c984694ba4944e691a409ae660f7a8fb644ab2aa517bb`
- com.taobao.taobao：`9ab7733d49302c973cd49765b13fc836fbee24fd58f6d77c998fd4f5a5d2bea4`
- com.taobao.idlefish：`905718aff71859527c53b80c88a971fc47c7aa5e8c1a6d3d661b9e073b837530`
- com.tencent.mm：`f2a725bc04302ad218a563533ae79356e66b64aa711b275bc919a8d432f7e15d`
- tv.danmaku.bili：`ad073a3e01e26c336b7e65181f6acb40e920698bb40fb15ee35e03ff6fb02799`
- com.xunmeng.pinduoduo：`25bb13c6fad8336febf3c43e78c07b352d85b3f7fe86da6b8e82ab5360cef5ce`
- com.xingin.xhs：`510ee7bc44620144d91bd0f8f52642b12d4e4b57dc9c8d3e1e71385856357226`

## 0.8.0 新增平台（2026-11-01）

在原有七个平台图标之外新增四个；原七个文件不变。来源均为当日应用宝原版，包名、商店校验值与签名核对后，用同一导图工具读取图标资源；不执行原版代码，原APK不发布。淘宝闪购使用独立客户端 me.ele，与现有淘宝 com.taobao.taobao 分开。

| 图标 | 原版包名 / 版本 | PNG SHA-256 |
| --- | --- | --- |
| platform_meituan.png | com.sankuai.meituan 12.68.202 | `e05f32ce6cc2e9bbc57ede166400a9ce179a6cfaa379e1465673f5ad4b030f3c` |
| platform_ctrip.png | ctrip.android.view 8.97.0 | `b16267dce5daf9cf034faa8a9531d7f1a61e638eb316d3254fc6c947f396a904` |
| platform_amap.png | com.autonavi.minimap 17.02.0.2010 | `fff3f061b8746e3bcb538d5fe451458718dbebabb55e497b730d263dff02e89c` |
| platform_taobao_shangou.png | me.ele 12.10.38 | `8d2bbcba156cbde41e8179cfd5cd7ba7cff457793a3b6a07187f0fd0dbc85eb7` |

原版来源与 APK SHA-256：
- com.sankuai.meituan：https://sj.qq.com/appdetail/com.sankuai.meituan；`a16d5ab55d37cf1b6bb869e71a8fc7251ff8d98eb0b14bfb3adf57006d7f417a`
- ctrip.android.view：https://sj.qq.com/appdetail/ctrip.android.view；`43785d822086ce3fb66bcc3373938568e16d05817d6e478450ab564434ef14b6`
- com.autonavi.minimap：https://sj.qq.com/appdetail/com.autonavi.minimap；`3ecc20305f523a346d4d374ba4f0ffd3ef66d5c48b98526e08d7b613e9a7df19`
- me.ele：https://sj.qq.com/appdetail/me.ele；`dd51a8e753d9786d18c9580c1185e683a1d17874a85bed633e4ec1bffedec283`
