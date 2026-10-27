# 原版调研与本次实现

调研日期：2026-10-22。

## 主要来源

- [NoFeed 官方 App Store](https://apps.apple.com/us/app/nofeed/id6745481301)：功能介绍、主界面、历史、别名、小组件截图。
- [Apple 官方元数据接口](https://itunes.apple.com/lookup?id=6745481301&country=cn)：获取四张公开宣传截图作本地参考，未纳入源码许可或发行素材。
- [开发者访谈](https://quaily.com/apptisan/p/nofeed)：主动搜索、避免信息流分心，以及原版坚持文字输入应用名的设计动机。
- [Android 官方 Intent 文档](https://developer.android.com/guide/components/intents-filters)：使用 ACTION_VIEW、显式包限定和异常处理。
- [Android 官方构建工具](https://developer.android.com/tools)：aapt2、D8、zipalign、apksigner。

原版定位是跳过平台首页，输入应用名称及关键词直接进入结果。官方介绍还包括线性历史、名称联想、自定义别名、桌面小组件、离线保存、无登录和无广告。公开页面仍可访问，没有找到足以确认“停服”的官方声明。

## 用户最终指定的首页

用户最终要求更改原版首页：单个关键词输入框；下方用图标选择软件；最右侧放“更多”。本次实现以这条最新要求为准，保留纸白底色、克制的文字、本地历史和主动搜索的目标。图标由文字和几何背景独立绘制，未使用原版图标、截图或第三方官方 Logo 文件。

首批为豆瓣、淘宝、闲鱼；0.3.0 增加微信。“更多”列出四个已实现的应用，不提供别名或自定义搜索链接。应用改名为“一搜”，启动图标采用黑白放大镜。

## 跳转适配

| 目标 | 包名 | 默认搜索模板 | 网页备用 |
| --- | --- | --- | --- |
| 豆瓣 | com.douban.frodo | douban://douban.com/search/result?q={query} | https://www.douban.com/search?q={query} |
| 淘宝 | com.taobao.taobao | tbopen://m.taobao.com/tbopen/index.html（h5Url 为编码后的搜索 URL） | https://s.taobao.com/search?q={query} |
| 闲鱼 | com.taobao.idlefish | fleamarket://idle_search?flutter=true&flutter_animated=false&initSubPageTag=searchResult&keyword={query} | https://www.goofish.com/search?q={query} |
| 微信 | com.tencent.mm | 用户开启后的本地无障碍搜索辅助 | 无 |

这些 URI 并非平台公开承诺稳定的 API。0.2.0 对豆瓣、闲鱼完成了同版本模拟器结果页验证，用户后续在 HarmonyOS 4.2.0 确认豆瓣、淘宝、闲鱼搜索均正常。闲鱼另有内置的 `fleamarket://x_search_items?keyword={query}` 备用入口。不能自动判断第三方 App 实际打开的是首页还是结果页，因此不以“startActivity 成功”宣称端到端兼容。

所有模板由代码维护，使用固定包名发送。网页同样可能要求登录、受到风控或改版。

## 微信 0.3.0 的历史实现

从[微信官方站点](https://weixin.qq.com/)链接的 Android 8.0.79 原包提取 Manifest 检查，本轮没有找到可用于任意关键词搜索的标准 SEARCH、WEB_SEARCH 或 PROCESS_TEXT 入口；FTSMainUI 与 MMFTSWebViewUI 没有对外导出。没有把网上未经验证的 searchresult scheme 当成可用功能。

用户明确要求在微信客户端自动搜索，允许自动点击。故采用应用内置的固定搜索流程，不依赖额外自动化 App、不使用网页或剪贴板：一次开启系统辅助权限，之后识别搜索控件、填入关键词、点击“搜一搜/搜索”，必要时调用编辑框的输入动作。参考 [Android AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService) 和 [ACTION_IME_ENTER](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo.AccessibilityAction#ACTION_IME_ENTER)。后者要求目标编辑框正确提供输入动作，调用成功不能证明平台已显示结果。

服务只订阅窗口切换事件，空闲时立即忽略；在任务期间检查窗口所属包名以识别离开微信，并允许系统当前输入法窗口。读取控件前检查微信包名。每次任务由用户点击发起，限时运行，离开微信后取消。测试夹具用于验证流程和停止边界，不能代替已登录微信的真机验收。


## 0.3.1：按用户截图回放坐标

用户真机微信为 8.0.78，反馈旧版只打开应用。用户随后明确要求不再用无障碍控件识别，并提供微信首页和搜索输入页的截图。本版删除控件识别与 ACTION_SET_TEXT/ACTION_IME_ENTER 路径，将 canRetrieveWindowContent 关闭，仅使用系统手势能力模拟点击与长按。

提供的两张 1084 × 2412 竖屏截图可确定搜索图标、输入框和键盘搜索键的相对位置，分别约为 (0.830, 0.073)、(0.420, 0.084)、(0.892, 0.934)。这些预设仅匹配 8.0.78 及相近竖屏比例，粘贴菜单位置没有出现在截图中，不猜测：第一次由用户在校准覆盖层点选，保存后自动使用。其他布局通过四点校准记录全部位置。

应用在前台把本次关键词写入系统剪贴板，之后打开微信、点击搜索和输入框、长按呼出粘贴菜单、点击已保存的粘贴位置、点击键盘搜索键。保存的屏幕与应用配置用于失效检查；任务只检查窗口所属应用以判断是否离开，不获取内容树、不做图像识别。用户的微信截图没有复制入项目，也不进入开源包。

系统设置跳转改用公开的 ACTION_ACCESSIBILITY_SETTINGS，失败再尝试 ACTION_SETTINGS，均捕获启动异常，最后显示应用内指引。没有用户手机的崩溃日志，不能将旧详情设置入口认定为已确认的崩溃原因。


## 2026-10-27：新增三个原版平台

应用宝当天渠道版本（不宣称所有渠道同时最新），下载 MD5 与商店元数据匹配、apksigner 验证成功；原版 APK 只放忽略的本地调研缓存，不纳入仓库、源码包或一搜 APK。

| 平台/版本 | 包名 | 客户端入口 | 运行边界 |
| --- | --- | --- | --- |
| 哔哩哔哩 9.16.1 | tv.danmaku.bili | bilibili://search?keyword={query} | 原版 IntentHandlerActivity 公开接收；模拟器首次同意后无响应，未确认结果 |
| 拼多多 8.28.0 | com.xunmeng.pinduoduo | pinduoduo://com.xunmeng.pinduoduo/search_result.html?search_key={query} | 原版 MainFrameActivity 接收，实际出现关键词搜索头后要求登录 |
| 小红书 9.52.0 | com.xingin.xhs | xhsdiscover://search/result?keyword={query} | RouterPageActivity 公开接收；原版普通启动及路由在模拟器退出，未确认结果 |

关键词均一次 UTF-8 percent encoding，并由 Intent 限定包名。原有淘宝嵌套 URL 的两层编码保持不变。没有尝试启动私有结果 Activity。

来源：[哔哩哔哩商店](https://sj.qq.com/appdetail/tv.danmaku.bili)、[拼多多商店](https://sj.qq.com/appdetail/com.xunmeng.pinduoduo)、[小红书商店](https://sj.qq.com/appdetail/com.xingin.xhs)。小红书的[官方协议](https://pages.xiaohongshu.com/activity/deeplink)明确列出 /search/result 与必需 keyword。

有界静态核对：BiliMainSearchActivity.O8 从 URI 读取 keyword，非空时进入搜索结果分支；rx0.e.f 匹配 bilibili scheme 和 search/search3 host。小红书 GlobalSearchActivity 注册 /search/result，Mapping.parseExtras 将 query 写入 Bundle，Routers.doOpen 转入目标 Intent，搜索参数 j80.c.n 读取 keyword Extra。未穷举小红书新组件路由的每个分支，不能据此宣称运行时一定命中旧路由。REA Android 工具缺少独立 JADX MCP JAR，因此这些证据来自现有 AAPT2、DEX 定位和 JADX CLI 1.5.0；未修改 REA 或客户端配置。

原版 APK SHA-256：

- 哔哩哔哩：ad073a3e01e26c336b7e65181f6acb40e920698bb40fb15ee35e03ff6fb02799
- 拼多多：25bb13c6fad8336febf3c43e78c07b352d85b3f7fe86da6b8e82ab5360cef5ce
- 小红书：510ee7bc44620144d91bd0f8f52642b12d4e4b57dc9c8d3e1e71385856357226

原版自身在模拟器的异常不等同于一搜路由失败，也不证明手机可以成功，最终仍需已安装、已完成首次提示及必要登录的真实手机复测。未登录任何账号、未购买或执行社交操作。
