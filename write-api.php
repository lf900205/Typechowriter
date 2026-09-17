<?php
/**
 * Typecho 极简写作 API
 * 放到网站根目录，访问 /write-api.php
 */

require_once __DIR__ . '/config.inc.php';

header('Content-Type: application/json; charset=utf-8');

define('API_TOKEN', '这里是token');
define('DEFAULT_MID', 1);

// ============================================================
// Token 校验
// ============================================================
$token = $_SERVER['HTTP_X_API_TOKEN'] ?? '';
if (!hash_equals(API_TOKEN, $token)) {
    http_response_code(401);
    echo json_encode(['error' => 'Token 无效']);
    exit;
}

$action = $_GET['action'] ?? '';

try {
    switch ($action) {
        case 'publish':
            echo json_encode(publishPost());
            break;
        case 'polish':
            echo json_encode(polishPost());
            break;
        case 'upload-image':
            echo json_encode(uploadToR2());
            break;
        case 'unsplash-search':
            echo json_encode(unsplashSearch());
            break;
        case 'unsplash-collections':
            echo json_encode(unsplashCollections());
            break;
        case 'unsplash-collection-photos':
            echo json_encode(unsplashCollectionPhotos());
            break;
        case 'debug':
            echo json_encode([
                'AiWriter' => getPluginConfig('AiWriter'),
                'Unsplash' => getPluginConfig('UnsplashForTypecho'),
            ], JSON_UNESCAPED_UNICODE);
            break;
        default:
            throw new Exception('未知操作');
    }
} catch (Throwable $e) {
    http_response_code(400);
    echo json_encode(['error' => $e->getMessage()]);
}

// ============================================================
// 读插件配置
// ============================================================
function getPluginConfig($pluginName) {
    try {
        $db = \Typecho\Db::get();
        $row = $db->fetchRow(
            $db->select()->from('table.options')
               ->where('name = ?', 'plugin:' . $pluginName)
               ->limit(1)
        );
        if (!$row || empty($row['value'])) return [];
        $value = $row['value'];

        $decoded = json_decode($value, true);
        if (is_array($decoded)) return $decoded;

        $decoded = @unserialize($value);
        if (is_array($decoded)) return $decoded;

        return [];
    } catch (Throwable $e) {
        return [];
    }
}

// ============================================================
// 用 DeepSeek 生成英文 slug
// ============================================================
function generateSlugByAI($title) {
    try {
        $cfg = getPluginConfig('AiWriter');
        $apiKey = trim($cfg['deepseekKey'] ?? '');
        if ($apiKey === '') {
            error_log('[slug] AiWriter 未配置 deepseekKey');
            return uniqid('post-', true);
        }

        $prompt = "把下面的中文标题翻译成一个英文 URL slug。"
                . "要求：只输出英文小写字母和连字符，最长 6 个单词，"
                . "不要引号，不要任何解释。\n\n标题：{$title}";

        $payload = json_encode([
            'model' => 'deepseek-chat',
            'messages' => [['role' => 'user', 'content' => $prompt]],
            'temperature' => 0.2,
            'max_tokens'  => 30,
        ], JSON_UNESCAPED_UNICODE);

        $ch = curl_init('https://api.deepseek.com/v1/chat/completions');
        curl_setopt_array($ch, [
            CURLOPT_POST           => 1,
            CURLOPT_POSTFIELDS     => $payload,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_HTTPHEADER     => [
                "Authorization: Bearer {$apiKey}",
                "Content-Type: application/json",
            ],
            CURLOPT_SSL_VERIFYPEER => false,
            CURLOPT_SSL_VERIFYHOST => false,
            CURLOPT_TIMEOUT        => 15,
        ]);
        $raw = curl_exec($ch);
        $err = curl_error($ch);
        $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);

        if ($err) {
            error_log("[slug] curl 错误: {$err}");
            return uniqid('post-', true);
        }
        if ($httpCode !== 200) {
            error_log("[slug] HTTP {$httpCode}: " . substr($raw, 0, 500));
            return uniqid('post-', true);
        }

        $resp = json_decode($raw, true);
        $text = trim($resp['choices'][0]['message']['content'] ?? '');
        if ($text === '') {
            error_log("[slug] AI 返回为空: " . substr($raw, 0, 500));
            return uniqid('post-', true);
        }

        $slug = strtolower($text);
        $slug = preg_replace('/[^a-z0-9\-]/', '-', $slug);
        $slug = preg_replace('/-+/', '-', $slug);
        $slug = trim($slug, '-');
        $slug = substr($slug, 0, 60);

        if ($slug === '') {
            error_log("[slug] 清洗后为空，AI 原文: {$text}");
            return uniqid('post-', true);
        }

        error_log("[slug] 成功: {$title} -> {$slug}");
        return $slug;

    } catch (Throwable $e) {
        error_log("[slug] 异常: " . $e->getMessage());
        return uniqid('post-', true);
    }
}

// ============================================================
// 发布文章
// ============================================================
function publishPost() {
    $title   = trim($_POST['title'] ?? '');
    $content = $_POST['content'] ?? '';
    $status  = $_POST['status'] ?? 'publish';
    $tags    = trim($_POST['tags'] ?? '');
    $cid     = isset($_POST['cid']) ? (int)$_POST['cid'] : 0;
    $slug    = trim($_POST['slug'] ?? '');

    if ($title === '') throw new Exception('标题不能为空');
    if ($slug === '') $slug = generateSlugByAI($title);

    $db = \Typecho\Db::get();
    $prefix = $db->getPrefix();

    $user = $db->fetchRow(
        $db->select()->from('table.users')->where('uid = ?', 1)->limit(1)
    );
    if (!$user) throw new Exception('找不到管理员用户');

    $now = time();

    if ($cid > 0) {
        $db->query($db->update('table.contents')->rows([
            'title' => $title, 'text' => $content, 'modified' => $now,
        ])->where('cid = ?', $cid));
    } else {
        $cid = $db->query($db->insert('table.contents')->rows([
            'title'        => $title,
            'slug'         => $slug,
            'created'      => $now,
            'modified'     => $now,
            'text'         => $content,
            'order'        => 0,
            'authorId'     => $user['uid'],
            'type'         => 'post',
            'status'       => $status,
            'commentsNum'  => 0,
            'allowComment' => 1,
            'allowPing'    => 1,
            'allowFeed'    => 1,
            'parent'       => 0,
        ]));
        $db->query($db->insert('table.relationships')->rows([
            'cid' => $cid, 'mid' => DEFAULT_MID,
        ]));
        $db->query("UPDATE {$prefix}metas SET count = count + 1 WHERE mid = " . (int)DEFAULT_MID);
    }

    if ($tags !== '') {
        $tagArr = array_filter(array_map('trim', preg_split('/[,，]/u', $tags)));
        foreach ($tagArr as $tagName) {
            $mid = $db->fetchRow(
                $db->select('mid')->from('table.metas')
                   ->where('name = ?', $tagName)->where('type = ?', 'tag')->limit(1)
            );
            if ($mid) {
                $tagMid = $mid['mid'];
            } else {
                $tagMid = $db->query($db->insert('table.metas')->rows([
                    'name' => $tagName, 'slug' => uniqid('tag-', true),
                    'type' => 'tag', 'description' => '', 'count' => 1,
                    'order' => 0, 'parent' => 0,
                ]));
            }
            $db->query($db->delete('table.relationships')->where('cid = ?', $cid)->where('mid = ?', $tagMid));
            $db->query($db->insert('table.relationships')->rows(['cid' => $cid, 'mid' => $tagMid]));
        }
    }

    return ['success' => true, 'cid' => (int)$cid, 'slug' => $slug];
}

// ============================================================
// AI 润色
// ============================================================
function polishPost() {
    $content = $_POST['content'] ?? '';
    if (trim($content) === '') throw new Exception('内容不能为空');

    $cfg = getPluginConfig('AiWriter');
    $apiKey = trim($cfg['deepseekKey'] ?? '');
    if ($apiKey === '') throw new Exception('AiWriter 未配置 DeepSeek Key');

    $model   = 'deepseek-chat';
    $timeout = intval($cfg['timeout'] ?? 60) ?: 60;

    $styleOverride = trim($_POST['style'] ?? '');
    $style = $styleOverride !== '' ? $styleOverride : ($cfg['literaryStyle'] ?? 'murakami');

    $styleMap = [
        'murakami' => '村上春树的写作风格：细腻的日常描写、隐喻丰富、语言平实而富有诗意',
        'yu hua'   => '余华的写作风格：简洁有力、冷峻克制、叙事直接',
        'mo yan'   => '莫言的写作风格：魔幻现实主义、乡土气息浓厚、语言富有想象力',
        'none'     => '通用文学润色，保持流畅自然，不刻意模仿特定作家',
    ];
    $styleDesc = $styleMap[$style] ?? $styleMap['murakami'];

    $prompt = <<<PROMPT
你是一个专业的文字编辑和写作助手。请对以下文章内容进行智能处理，按照以下步骤：

1. **判断文章类型**：
   - 生活类（个人随笔、游记、摄影心得、情感叙事、生活感悟等）
   - 技术类（技术教程、软件配置、编程指南等）
   - 难以区分时优先根据内容主体判断

2. **根据类型差异化处理**：
   - 生活类：以 {$styleDesc} 润色，保持原意，适当调整段落
   - 技术类：优化术语，增加 Markdown 标题层级（##、###），步骤用列表
   - 保留原文中的图片占位符（如 [jpg]...[/jpg]）

3. **提取元数据**：
   - 标题（不超过 20 字）
   - 1~3 个中文关键词，逗号分隔
   - 润色后的完整正文

4. **输出严格 JSON**，不要任何额外解释：
{
    "category": "生活类" 或 "技术类",
    "title": "提取的标题",
    "tags": "标签1,标签2,标签3",
    "content": "完整的润色后内容"
}

文章内容如下：
{$content}
PROMPT;

    $payload = json_encode([
        'model'       => $model,
        'messages'    => [['role' => 'user', 'content' => $prompt]],
        'temperature' => 0.7,
        'max_tokens'  => 6000,
    ], JSON_UNESCAPED_UNICODE);

    $ch = curl_init('https://api.deepseek.com/v1/chat/completions');
    curl_setopt_array($ch, [
        CURLOPT_POST           => 1,
        CURLOPT_POSTFIELDS     => $payload,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_HTTPHEADER     => [
            "Authorization: Bearer {$apiKey}",
            "Content-Type: application/json",
        ],
        CURLOPT_SSL_VERIFYPEER => false,
        CURLOPT_SSL_VERIFYHOST => false,
        CURLOPT_TIMEOUT        => $timeout,
    ]);
    $raw = curl_exec($ch);
    $err = curl_error($ch);
    $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);

    if ($err) throw new Exception('DeepSeek 请求失败：' . $err);

    $resp = json_decode($raw, true);
    $aiText = $resp['choices'][0]['message']['content'] ?? '';
    if ($aiText === '') {
        $msg = $resp['error']['message'] ?? ('HTTP ' . $httpCode);
        throw new Exception('DeepSeek 返回异常：' . $msg);
    }

    if (preg_match('/\{.*\}/s', $aiText, $m)) {
        $data = json_decode($m[0], true);
        if ($data && isset($data['title'])) {
            return [
                'success'  => true,
                'category' => $data['category'] ?? '',
                'title'    => $data['title'] ?? '',
                'tags'     => $data['tags'] ?? '',
                'content'  => $data['content'] ?? $aiText,
            ];
        }
    }

    return [
        'success'  => true,
        'category' => '',
        'title'    => '',
        'tags'     => '',
        'content'  => trim($aiText),
    ];
}

// ============================================================
// 上传图片到 R2
// ============================================================
function uploadToR2() {
    if (!isset($_FILES['file']) || $_FILES['file']['error'] !== UPLOAD_ERR_OK) {
        throw new Exception('文件上传失败');
    }
    $file = $_FILES['file'];
    $ext = strtolower(pathinfo($file['name'], PATHINFO_EXTENSION));
    if (!in_array($ext, ['jpg', 'jpeg', 'png', 'gif', 'webp'])) {
        throw new Exception('仅支持 jpg / png / gif / webp');
    }

    $pcfg = getPluginConfig('UnsplashForTypecho');
    $cfg = [
        'accessKey' => trim($pcfg['r2AccessKey'] ?? ''),
        'secretKey' => trim($pcfg['r2SecretKey'] ?? ''),
        'bucket'    => trim($pcfg['r2Bucket'] ?? ''),
        'accountId' => trim($pcfg['r2AccountId'] ?? ''),
        'publicUrl' => rtrim(trim($pcfg['r2PublicUrl'] ?? ''), '/'),
        'folder'    => trim($pcfg['r2Folder'] ?? '', '/') ?: 'uploads',
    ];
    if (!$cfg['accessKey'] || !$cfg['secretKey'] || !$cfg['bucket'] || !$cfg['accountId']) {
        throw new Exception('R2 配置不完整');
    }

    $year = date('Y'); $month = date('m');
    $cleanName = strtolower(preg_replace('/[^\w\-]/', '-', pathinfo($file['name'], PATHINFO_FILENAME)));
    if ($cleanName === '') $cleanName = 'image';
    $fileName = time() . '_' . $cleanName . '.' . $ext;
    $r2Key = $cfg['folder'] . '/' . $year . '/' . $month . '/' . $fileName;

    $body = file_get_contents($file['tmp_name']);
    $fileType = $file['type'] ?: 'application/octet-stream';

    $host      = "{$cfg['bucket']}.{$cfg['accountId']}.r2.cloudflarestorage.com";
    $endpoint  = "https://{$host}";
    $date      = gmdate('Ymd\THis\Z');
    $shortDate = substr($date, 0, 8);
    $scope     = "{$shortDate}/auto/s3/aws4_request";

    $payloadHash = hash('sha256', $body);
    $canonicalHeaders = "host:{$host}\nx-amz-content-sha256:{$payloadHash}\nx-amz-date:{$date}\n";
    $signedHeaders = 'host;x-amz-content-sha256;x-amz-date';
    $canonicalRequest = "PUT\n/{$r2Key}\n\n{$canonicalHeaders}\n{$signedHeaders}\n{$payloadHash}";
    $stringToSign = "AWS4-HMAC-SHA256\n{$date}\n{$scope}\n" . hash('sha256', $canonicalRequest);

    $kDate    = hash_hmac('sha256', $shortDate, 'AWS4' . $cfg['secretKey'], true);
    $kRegion  = hash_hmac('sha256', 'auto', $kDate, true);
    $kService = hash_hmac('sha256', 's3', $kRegion, true);
    $kSigning = hash_hmac('sha256', 'aws4_request', $kService, true);
    $signature = hash_hmac('sha256', $stringToSign, $kSigning);

    $headers = [
        "Content-Type: {$fileType}",
        "Host: {$host}",
        "X-Amz-Date: {$date}",
        "X-Amz-Content-Sha256: {$payloadHash}",
        "Authorization: AWS4-HMAC-SHA256 Credential={$cfg['accessKey']}/{$scope},"
            . "SignedHeaders={$signedHeaders},Signature={$signature}",
    ];

    $ch = curl_init($endpoint . '/' . $r2Key);
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CUSTOMREQUEST  => 'PUT',
        CURLOPT_POSTFIELDS     => $body,
        CURLOPT_HTTPHEADER     => $headers,
        CURLOPT_SSL_VERIFYPEER => true,
        CURLOPT_TIMEOUT        => 60,
    ]);
    curl_exec($ch);
    $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $curlErr  = curl_error($ch);
    curl_close($ch);

    if ($httpCode !== 200) {
        throw new Exception("R2 上传失败 HTTP {$httpCode}" . ($curlErr ? " ({$curlErr})" : ''));
    }

    return [
        'success' => true,
        'url'     => $cfg['publicUrl'] . '/' . $r2Key,
        'key'     => $r2Key,
        'name'    => $fileName,
        'size'    => $file['size'],
    ];
}

// ============================================================
// Unsplash 搜索（支持中文，含翻译层）
// ============================================================
function unsplashSearch() {
    $query = trim($_GET['query'] ?? '');
    $page  = max(1, intval($_GET['page'] ?? 1));
    if ($query === '') throw new Exception('搜索关键词不能为空');

    $pcfg = getPluginConfig('UnsplashForTypecho');
    $accessKey = trim($pcfg['accessKey'] ?? '');
    if ($accessKey === '') throw new Exception('未配置 Unsplash Access Key');

    if (preg_match('/[\x{4e00}-\x{9fa5}]/u', $query)) {
        $translated = translateToEnglish($query);
        if ($translated !== '') {
            $query = $translated;
        }
    }

    $url = 'https://api.unsplash.com/search/photos?query=' . urlencode($query)
         . '&page=' . $page . '&per_page=12';

    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_HTTPHEADER     => [
            "Authorization: Client-ID {$accessKey}",
            "Accept-Version: v1",
        ],
        CURLOPT_SSL_VERIFYPEER => true,
        CURLOPT_TIMEOUT        => 15,
    ]);
    $raw      = curl_exec($ch);
    $err      = curl_error($ch);
    $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);

    if ($err) throw new Exception('Unsplash 请求失败：' . $err);
    if ($httpCode !== 200) throw new Exception("Unsplash HTTP {$httpCode}");

    return [
        'success' => true,
        'results' => formatUnsplashPhotos(json_decode($raw, true)['results'] ?? []),
        'query'   => $query,
    ];
}

// ============================================================
// 用 DeepSeek 把中文关键词翻译成英文
// ============================================================
function translateToEnglish($text) {
    try {
        $cfg = getPluginConfig('AiWriter');
        $apiKey = trim($cfg['deepseekKey'] ?? '');
        if ($apiKey === '') return '';

        $prompt = "把下面的中文关键词翻译成最适合搜索 Unsplash 图片的英文单词或短语。"
                . "只输出翻译结果，不要引号、不要句号、不要任何解释。"
                . "如果是多个词，用空格分隔。最多 5 个英文单词。\n\n"
                . "中文：{$text}";

        $payload = json_encode([
            'model' => 'deepseek-chat',
            'messages' => [['role' => 'user', 'content' => $prompt]],
            'temperature' => 0.2,
            'max_tokens'  => 30,
        ], JSON_UNESCAPED_UNICODE);

        $ch = curl_init('https://api.deepseek.com/v1/chat/completions');
        curl_setopt_array($ch, [
            CURLOPT_POST           => 1,
            CURLOPT_POSTFIELDS     => $payload,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_HTTPHEADER     => [
                "Authorization: Bearer {$apiKey}",
                "Content-Type: application/json",
            ],
            CURLOPT_SSL_VERIFYPEER => false,
            CURLOPT_SSL_VERIFYHOST => false,
            CURLOPT_TIMEOUT        => 10,
        ]);
        $raw = curl_exec($ch);
        $err = curl_error($ch);
        curl_close($ch);

        if ($err) return '';

        $resp = json_decode($raw, true);
        $text2 = $resp['choices'][0]['message']['content'] ?? '';
        $text2 = trim($text2, " \t\n\r\"'“”‘’。.");
        return $text2;
    } catch (Throwable $e) {
        return '';
    }
}

// ============================================================
// Unsplash 用户相册列表
// ============================================================
function unsplashCollections() {
    $pcfg = getPluginConfig('UnsplashForTypecho');
    $accessKey = trim($pcfg['accessKey'] ?? '');
    $username  = trim($pcfg['username'] ?? '');

    if ($accessKey === '') throw new Exception('未配置 Unsplash Access Key');
    if ($username === '')  throw new Exception('未配置 Unsplash 用户名，请在插件设置中填写');

    $url = "https://api.unsplash.com/users/{$username}/collections?per_page=20";

    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_HTTPHEADER     => [
            "Authorization: Client-ID {$accessKey}",
            "Accept-Version: v1",
        ],
        CURLOPT_SSL_VERIFYPEER => true,
        CURLOPT_TIMEOUT        => 15,
    ]);
    $raw      = curl_exec($ch);
    $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);

    if ($httpCode !== 200) throw new Exception("Unsplash HTTP {$httpCode}");

    $data = json_decode($raw, true);
    $out = [];
    foreach ($data as $c) {
        $out[] = [
            'id'           => $c['id'] ?? '',
            'title'        => $c['title'] ?? '未命名',
            'total_photos' => $c['total_photos'] ?? 0,
            'cover'        => $c['cover_photo']['urls']['small'] ?? '',
        ];
    }
    return ['success' => true, 'results' => $out];
}

// ============================================================
// Unsplash 相册照片
// ============================================================
function unsplashCollectionPhotos() {
    $collectionId = $_GET['id'] ?? '';
    $page         = max(1, intval($_GET['page'] ?? 1));
    if ($collectionId === '') throw new Exception('缺少相册 ID');

    $pcfg = getPluginConfig('UnsplashForTypecho');
    $accessKey = trim($pcfg['accessKey'] ?? '');
    if ($accessKey === '') throw new Exception('未配置 Unsplash Access Key');

    $url = "https://api.unsplash.com/collections/{$collectionId}/photos?page={$page}&per_page=12";

    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_HTTPHEADER     => [
            "Authorization: Client-ID {$accessKey}",
            "Accept-Version: v1",
        ],
        CURLOPT_SSL_VERIFYPEER => true,
        CURLOPT_TIMEOUT        => 15,
    ]);
    $raw      = curl_exec($ch);
    $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);

    if ($httpCode !== 200) throw new Exception("Unsplash HTTP {$httpCode}");

    return ['success' => true, 'results' => formatUnsplashPhotos(json_decode($raw, true) ?? [])];
}

// ============================================================
// 统一格式化 Unsplash 照片
// ============================================================
function formatUnsplashPhotos($arr) {
    $out = [];
    foreach ($arr as $r) {
        $out[] = [
            'id'         => $r['id'] ?? '',
            'thumb'      => $r['urls']['thumb']   ?? '',
            'regular'    => $r['urls']['regular'] ?? '',
            'small'      => $r['urls']['small']   ?? '',
            'width'      => $r['width']  ?? 0,
            'height'     => $r['height'] ?? 0,
            'alt'        => $r['alt_description'] ?? '',
            'author'     => $r['user']['name'] ?? '',
            'authorLink' => ($r['user']['links']['html'] ?? '')
                          . '?utm_source=TypechoWriter&utm_medium=referral',
        ];
    }
    return $out;
}
