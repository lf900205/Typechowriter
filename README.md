一个为 Typecho 打造的安卓写作客户端。

打开就写，写完就发。

## 功能

- ✍️ 极简写作界面
- 💾 本地草稿自动保存
- ✨ AI 润色（支持村上春树 / 余华 / 莫言 / 通用风格）
- 🖼 Unsplash 图片搜索（支持中文自动翻译）
- ☁️ 图片上传到 Cloudflare R2
- 📱 原生 Android（Kotlin + Jetpack Compose）

## 架构

- **服务端**：一个 PHP 文件（`write-api.php`），放进 Typecho 根目录即可
- **客户端**：Android App，通过 Token 认证调用该接口
- **依赖**：复用 Typecho 已有的 AiWriter 和 UnsplashForTypecho 插件配置

！ai写字插件只支持deepseek
！使用方法 安装 plugins 目录三个gz结尾的插件，并且配置好插件。
！write-api.php文件放在根目录，并修改文件里面的Token。
！下载APP文件，并且链接Token要与write-api.php里面修改的Token一致。
