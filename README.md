# Typecho Writer

一个为 Typecho 博客打造的安卓写作客户端。

打开就写，写完就发。本地草稿自动保存、AI 润色、Unsplash 配图、图片上传 R2、文章列表 / 编辑 / 删除，全部内置。

## ✨ 功能

| 功能 | 说明 |
| --- | --- |
| 极简写作界面 | 白纸风格，无边框输入框，专注写作 |
| 本地草稿自动保存 | 800ms 无操作自动保存，App 被杀不丢内容 |
| 分类选择 | 发布时可选 Typecho 后台的所有分类 |
| AI 润色 | 4 种风格：村上春树 / 余华 / 莫言 / 通用 |
| Unsplash 配图 | 支持中文搜索（自动翻译成英文），可浏览自己的相册 |
| 多选插图 | 本地相册多选 + Unsplash 多选，按顺序自动编号 |
| 图片上传 R2 | 上传到 Cloudflare R2，自动插入 Markdown |
| 文章管理 | 查看已发布文章列表，支持编辑、删除 |
| AI 生成 slug | 发布时自动生成英文 URL slug（如 walk-in-park-flowers-bloom） |
| Unsplash 合规 | 完整署名 + 链接 + UTM 参数 + Download Tracking |

## 🏗️ 架构

```
┌─────────────────────┐        HTTP + Token        ┌──────────────────────┐
│   Android App       │ ─────────────────────────► │   write-api.php      │
│  (Kotlin + Compose) │                            │   (Typecho 根目录)    │
└─────────────────────┘                            └──────────────────────┘
                                                              │
                                                    ┌─────────┼─────────┐
                                                    ▼         ▼         ▼
                                              Typecho DB  DeepSeek  Unsplash
                                                                    + R2
```

- 服务端：单个 PHP 文件 `write-api.php`，放在 Typecho 根目录
- 客户端：Android 原生，Kotlin + Jetpack Compose
- 依赖插件：AiWriter、UnsplashForTypecho、JustifiedGallery（服务端已启用）

## 📦 部署

### 一、服务端

#### 1. 前置条件

Typecho 后台必须已启用以下插件（App 从它们的配置里读取 Key）：

| 插件 | 用途 | 必需配置 |
| --- | --- | --- |
| AiWriter | DeepSeek Key（润色 / 翻译 / slug） | deepseekKey |
| UnsplashForTypecho | Unsplash Access Key + R2 配置 | accessKey、username、r2AccessKey、r2SecretKey、r2Bucket、r2AccountId、r2PublicUrl、r2Folder |
| JustifiedGallery | 前台渲染 [jpg] 瀑布流 | 保持启用即可 |

#### 2. 部署 write-api.php

把 `write-api.php` 放到 Typecho 网站根目录（和 `config.inc.php` 同级）：

```
/www/wwwroot/你的域名/write-api.php
```

#### 3. 修改 write-api.php 顶部配置

```php
// 改成一个你自己的长随机字符串（32 位以上）
define('API_TOKEN', 'CHANGE_ME_TO_A_LONG_RANDOM_STRING');

// 默认分类 ID（可在 Typecho 后台 → 分类 里看 URL 的 mid 参数）
define('DEFAULT_MID', 1);
```

生成随机 Token 的快捷方法（PowerShell）：

```powershell
-join ((48..57) + (65..90) + (97..122) | Get-Random -Count 32 | % {[char]$_})
```

#### 4. 验证接口

```powershell
curl.exe "https://你的域名/write-api.php?action=publish" -H "X-API-Token: 你的Token" -d "title=测试&content=内容&status=publish"
```

预期返回：

```json
{"success":true,"cid":123,"slug":"test","category":1}
```

### 二、客户端编译

#### 1. 环境要求

- Android Studio Hedgehog（2023.1.1）或更新
- JDK 17
- Android SDK 34
- minSdk 26（Android 8.0）

#### 2. 打开项目 → Sync → Build → 装到手机

首次编译会下载依赖，约 1-3 分钟。

#### 3. 首次启动配置

打开 App → 填：

| 字段 | 内容 |
| --- | --- |
| 博客地址 | https://你的域名（不要带 write-api.php，不要带尾部斜杠） |
| API Token | 和 write-api.php 里的 API_TOKEN 完全一致 |

保存后进入写日志界面。

## 📖 使用说明

### 写文章

1. 点标题输入框写标题
2. 点正文输入框写内容（支持 Markdown）
3. 顶栏右侧选分类
4. 底栏三个按钮：
   - **润色**：AI 润色（弹风格选择）
   - **插图**：插入本地图片 / Unsplash 图片
   - **发布**：发布到博客

### 插图

点"插图"→ 三个 Tab：

| Tab | 说明 |
| --- | --- |
| 本地图片 | 从手机相册多选，上传到 R2，自动编号插入 |
| 搜索图片 | Unsplash 搜索（支持中文），多选后插入 |
| 我的相册 | 浏览你 Unsplash 账号下的相册，多选插入 |

选中图片会在右上角显示序号（1、2、3…），底部出现"插入"按钮。

### 编辑 / 删除文章

顶栏点"文章" → 弹出文章列表 → 每行右侧有"编辑"和"删除"按钮。

- 编辑 → 内容回填到写日志界面，按钮变"更新"
- 删除 → 弹确认框，确认后从数据库删除

### 草稿

- 写文章时自动保存，顶栏显示 `草稿 MM-dd HH:mm`
- App 被杀 / 关闭后重新打开会自动恢复
- 发布成功后自动清空

## ⚠️ 注意事项

### 1. Token 不要弄错

三处必须一致：

| 位置 | 说明 |
| --- | --- |
| write-api.php 里 `define('API_TOKEN', '...')` | 服务端校验用 |
| App 设置页"API Token" | 客户端发送用 |
| 调试用 curl 的 `-H "X-API-Token: ..."` | 测试用 |

不要从文章内容或聊天记录里复制 Token，只复制 32 位随机串本身。粘贴后手动检查首尾没有空格 / 换行。

如果 Token 出错，App 会全线报"Token 无效"。

### 2. 博客地址格式

- 正确：`https://你的域名`
- 错误：`https://你的域名/`、`https://你的域名/write-api.php`、`https://你的域名/`（尾部有空格）

### 3. R2 配置

在 UnsplashForTypecho 插件设置页里必须填全：

- r2AccessKey、r2SecretKey
- r2Bucket、r2AccountId
- r2PublicUrl（公开访问域名，如 `https://img.你的域名`）
- r2Folder（如 `uploads`）

R2 的公开域名要在 Cloudflare 后台绑定好，否则图片上传成功但访问 404。

### 4. Unsplash 合规要求

App 已实现完整合规：

- ✅ 图片下方有 `Photo by 摄影师 on Unsplash`（可点击链接）
- ✅ 悬停图片显示纯文本署名（桌面端）
- ✅ 链接带 `utm_source=TypechoWriter&utm_medium=referral`
- ✅ 每次插图自动上报 Download Tracking

不要删除署名行，否则会违反 Unsplash API 条款，可能导致生产环境权限被撤销。

### 5. 图片显示问题

App 插入的图片格式：

```markdown
[jpg]
![图1 · Photo by 摄影师 on Unsplash](https://images.unsplash.com/...)
[/jpg]

<p style="font-size:13px;color:#666;margin-top:12px;">
Photo by <a href="...">摄影师</a> on <a href="...">Unsplash</a>
</p>
```

`[jpg]...[/jpg]` 是 JustifiedGallery 插件识别的语法，必须保留。删掉后前台不会渲染成瀑布流。

### 6. 分类 ID

`write-api.php` 里的 `DEFAULT_MID` 是分类 ID，默认 1 通常是"默认分类"。如果你的博客没有分类 ID 为 1 的分类，发布时分类可能显示异常。

解决：在 Typecho 后台建至少一个分类，把它的 mid 填到 `DEFAULT_MID`。

### 7. Markdown 编辑器提示

在博客后台编辑 App 发的文章时，Typecho 可能弹"这篇文章不是由 Markdown 语法创建"。

这是正常的，点"是"即可。原因：App 直接写数据库，`markdown` 字段未设。不影响前台渲染。

### 8. 网络问题

- 服务器必须能访问外网（DeepSeek、Unsplash、R2 都在国外）
- 如果服务器在国内，检查防火墙、DNS、SSL 是否正常
- curl 测试通过但 App 失败 → 通常是手机网络问题（VPN、DNS）

### 9. 数据库兼容性

`write-api.php` 兼容 MySQL 和 SQLite。如果你用的是 MySQL，`GREATEST()` 等函数正常工作；SQLite 下已用 `CASE WHEN` 替代。

### 10. 数据备份

- 服务端：`write-api.php` 单独备份一份
- 客户端源码：建议用 Git 管理
- 关键信息：Token、R2 密钥、Unsplash Key、DeepSeek Key 存到密码管理器

## ❓ 常见问题

**Q：发布时转圈很久？**

A：AI 生成 slug 需要 1-2 秒。如果想跳过，可以关掉——把 `write-api.php` 里 `generateSlugByAI` 的调用改成 `uniqid`。

**Q：Unsplash 搜索返回英文结果？**

A：Unsplash API 本身只支持英文，App 会自动把中文翻译成英文再搜。

**Q：图片上传到 R2 后打不开？**

A：检查 `r2PublicUrl` 配置，以及 Cloudflare R2 后台的公开访问设置。

**Q：润色失败 / 超时？**

A：DeepSeek 服务不稳定或 Key 余额不足。去 DeepSeek 控制台检查余额和用量。

**Q：文章编辑后显示乱码？**

A：可能是编码问题。确认 `write-api.php` 文件本身是 UTF-8 无 BOM。

**Q：Downloads 一直是 0？**

A：Unsplash 后台数据有几分钟到几小时延迟。另外确认 App 是最新版（含 download tracking 调用）。

## 📄 License

MIT

## 🙏 致谢

- [Typecho](https://typecho.org/)
- [Unsplash API](https://unsplash.com/developers)
- [DeepSeek](https://www.deepseek.com/)
- [Cloudflare R2](https://www.cloudflare.com/developer-platform/r2/)
- [JustifiedGallery](https://github.com/monkeymonk/JustifiedGallery)
