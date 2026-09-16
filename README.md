# Typecho Writer for Android

> 一个为 Typecho 打造的安卓写作客户端。  
> **打开就写，写完就发。**

---

## 简介

Typecho Writer 是一个原生 Android 写作客户端，配合一个单文件的 PHP 服务端接口，让你可以在手机上以最少的操作完成「写作 → 润色 → 配图 → 发布」的全流程。

服务端只需要一个 `write-api.php` 放进 Typecho 根目录即可，客户端通过 Token 认证调用该接口，复用 Typecho 已有的 **AiWriter**、**UnsplashForTypecho** 和 **JustifiedGallery** 插件配置，不重复造轮子。

---

## 功能

| 功能 | 说明 |
| --- | --- |
| ✍️ 极简写作界面 | 打开即写，没有多余干扰 |
| 💾 本地草稿自动保存 | 随时随地续写，不怕丢失 |
| ✨ AI 润色 | 支持 村上春树 / 余华 / 莫言 / 通用 风格 |
| 🖼 Unsplash 图片搜索 | 支持中文关键词自动翻译 |
| ☁️ 图片上传 | 上传到 Cloudflare R2 |
| 🧩 瀑布流展示 | 兼容 JustifiedGallery 插件，前端图片瀑布流布局 |
| 📱 原生 Android | Kotlin + Jetpack Compose |

---

## 架构

```text
┌─────────────────────┐        Token 认证        ┌──────────────────────┐
│   Android 客户端     │  ───────────────────▶   │  write-api.php       │
│  Kotlin + Compose   │  ◀───────────────────   │  （Typecho 根目录）   │
└─────────────────────┘        JSON 接口         └──────────┬───────────┘
                                                            │
                                        ┌───────────────────┼───────────────────┐
                                        ▼                   ▼                   ▼
                                  AiWriter 插件      UnsplashForTypecho     Cloudflare R2
                                  （DeepSeek）          插件（搜图和相册）          （图床）

                                        ┌───────────────────┐
                                        │  JustifiedGallery │
                                        │  插件（瀑布流）    │
                                        └───────────────────┘
```

- **服务端**：一个 PHP 文件（`write-api.php`），放进 Typecho 根目录即可。
- **客户端**：Android App，通过 Token 认证调用该接口。
- **依赖**：复用 Typecho 已有的 AiWriter、UnsplashForTypecho、JustifiedGallery 插件配置。

---

## 目录结构

```text
.
├── app/                    # Android 客户端源码（Kotlin + Jetpack Compose）
├── plugins/                # Typecho 插件压缩包（3 个 .gz）
│   ├── AiWriter...
│   ├── UnsplashForTypecho...
│   └── JustifiedGallery...
├── write-api.php           # Typecho 服务端接口，放到博客根目录
└── README.md
```

---

## 快速开始

### 0. 前置条件

- 一个已经装好的 Typecho 博客（能正常访问）
- 已安装并配置好 **AiWriter** 插件（**注意：AI 写作插件只支持 DeepSeek**）
- 已安装并配置好 **UnsplashForTypecho** 插件
- 已安装并配置好 **JustifiedGallery** 瀑布流插件
- 已配置好 Cloudflare R2（用于图片上传）
- 一台 Android 手机

---

### 1. 安装 Typecho 插件

把 `plugins/` 目录下的 **3 个 `.gz` 结尾的插件**安装到 Typecho，并**逐个配置好**：

1. 进入 Typecho 后台 → **控制台 → 插件**
2. 上传 / 解压 `plugins/` 中的插件包到 `usr/plugins/`
3. 启用插件，并填写对应的配置项：
   - **AiWriter**：填入 DeepSeek 的 API Key 等信息
   - **UnsplashForTypecho**：填入 Unsplash Access Key
   - **JustifiedGallery**：用于前端文章图片瀑布流展示，按插件说明启用并配置
   - 其余插件按提示配置

> ⚠️ AiWriter 目前**仅支持 DeepSeek**，请勿使用其他模型的服务地址 / Key，否则润色功能会失败。

---

### 2. 部署服务端接口

1. 把 `write-api.php` 上传到 **Typecho 的根目录**（与 `index.php`、`admin/` 同级）。
2. 用编辑器打开 `write-api.php`，找到里面的 **Token** 配置项，改成你自己的 Token：

   ```php
   // 示例：找到类似这一行，把值改成你自己的随机字符串
   define('WRITE_API_TOKEN', '你的Token');
   ```

   建议使用足够长的随机字符串，例如：

   ```text
   a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6
   ```

3. 保存并上传。
4. 浏览器访问 `https://你的域名/write-api.php` 验证接口是否可访问（具体返回以实际接口为准）。

---

### 3. 安装并配置 App

1. 下载并安装 Android App（APK）。
2. 打开 App，在设置中填写：
   - **博客地址**：你的 Typecho 站点地址
   - **Token**：**必须与 `write-api.php` 中修改的 Token 完全一致**
3. 保存后即可开始写作。

> ⚠️ **Token 必须保持一致：**
> 1. `write-api.php` 里的 Token
> 2. App 里填写的 Token
> 3. （如插件需要）插件配置中的相关密钥
>
> App 与 `write-api.php` 的 Token **必须完全相同**，否则接口会拒绝请求。

---

## 使用流程

1. **打开就写** —— 启动 App 直接进入编辑界面。
2. **自动保存** —— 输入内容会作为本地草稿自动保存。
3. **AI 润色** —— 选择风格：村上春树 / 余华 / 莫言 / 通用，一键润色。
4. **搜索配图** —— 输入中文关键词，自动翻译后在 Unsplash 搜索图片。
5. **上传图片** —— 图片上传至 Cloudflare R2，返回图片链接。
6. **写完就发** —— 一键发布到你的 Typecho 博客。
7. **前端展示** —— 配合 JustifiedGallery 插件，文章图片以瀑布流形式展示。

---

## 常见问题（FAQ）

**Q：AI 润色报错 / 没有反应？**  
A：AiWriter 插件**只支持 DeepSeek**。请确认插件中填写的是 DeepSeek 的 API Key，且账户余额 / 额度正常。

**Q：提示 Token 错误 / 401？**  
A：请检查 App 中填写的 Token 与 `write-api.php` 中的 Token 是否**完全一致**（注意首尾空格、大小写）。

**Q：图片搜索搜不到结果？**  
A：检查 UnsplashForTypecho 插件是否已启用并配置了有效的 Access Key；中文关键词会先自动翻译为英文再搜索。

**Q：图片上传失败？**  
A：检查 Cloudflare R2 的配置（Bucket、Access Key、Secret Key、公开访问域名等）是否正确。

**Q：前端图片没有瀑布流效果？**  
A：请确认 **JustifiedGallery** 插件已启用，并按照插件说明完成配置；同时确保文章内包含可用的图片链接。

**Q：`write-api.php` 放在哪里？**  
A：放在 **Typecho 根目录**，与 `index.php` 同级。

**Q：草稿会不会丢？**  
A：App 会在本地自动保存草稿；但建议不要卸载 App 或清除应用数据。

---

## 从源码构建（可选）

环境要求：

- Android Studio（最新稳定版）
- JDK 17+
- Android SDK

```bash
git clone <本仓库地址>
cd <仓库目录>
./gradlew assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/`。

---

## 技术栈

- **客户端**：Kotlin、Jetpack Compose、Android 原生
- **服务端**：PHP（单文件 `write-api.php`）
- **博客系统**：Typecho
- **AI**：DeepSeek（经 AiWriter 插件）
- **图搜**：Unsplash（经 UnsplashForTypecho 插件）
- **瀑布流**：JustifiedGallery（Typecho 插件）
- **图床**：Cloudflare R2

---

## 注意事项

- AiWriter 插件**仅支持 DeepSeek**。
- `write-api.php` 中的 Token 与 App 中填写的 Token **必须一致**。
- JustifiedGallery 为 Typecho 前端瀑布流插件，与 App 写作发布流程独立，但会影响博客前端的图片展示效果。
- 请妥善保管 Token，不要泄露给他人。
- 建议为接口开启 HTTPS，避免 Token 在传输过程中被窃听。

---

## 致谢

- [Typecho](https://typecho.org/)
- [DeepSeek](https://www.deepseek.com/)
- [Unsplash](https://unsplash.com/)
- [Cloudflare R2](https://www.cloudflare.com/developer-platform/r2/)
- JustifiedGallery

---

## License

请根据实际情况补充许可证信息（如 MIT License）。
