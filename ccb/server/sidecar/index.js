/**
 * 摊小二 Sidecar — 独立 Node express 服务
 *
 * 把 yudao-ui-merchant-uniapp/vite.config.js 里 dev-only 的 sidecar 抽出来，
 * 让生产 H5 build 后也能跑视频端卡 / 二维码植入 / 抖音发布等功能。
 *
 * 启动：node index.js（监听 process.env.PORT || 8081）
 * 反代：nginx 把 /oss/upload /tts/* /video/* /vproxy /jimeng /douyin/* 都转到这里
 *
 * 依赖系统 ffmpeg：包用 ffmpeg-static 自带二进制，无需 yum install。
 *
 * 9 个 handler：
 *   POST /oss/upload                 → TOS S3 上传（base64 → key + url）
 *   POST /tts/volc                   → 豆包 openspeech v3 流式 TTS
 *   POST /tts/edge                   → Microsoft Edge TTS（备用）
 *   POST /video/merge                → 多片段视频 concat（ffmpeg）
 *   POST /video/mux                  → 视频 + TTS + ASS 字幕合流
 *   POST /video/endcard              → ★ 端卡生成：商品图 + 居中大二维码 + 店名 + TTS
 *   GET  /vproxy?url=                → 视频反代（加 CORS 给 canvas 抽帧）
 *   GET  /douyin/auth-url            → 抖音 OAuth URL
 *   GET  /douyin/oauth-callback      → 授权回调
 *   POST /douyin/publish             → 抖音视频发布（multipart 上传 + create_video）
 *   POST /jimeng?action=             → @deprecated 即梦签名代理
 */

const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawn } = require('node:child_process');
const express = require('express');
const QRCode = require('qrcode');

// ffmpeg 二进制选择 — CentOS 7 (glibc 2.17) 下 ffmpeg-static 5.x 二进制
// 要求 GLIBC_2.18+，会报"version `GLIBC_2.18' not found"。优先用系统 ffmpeg
// （yum install epel-release && yum install ffmpeg），找不到才回退 ffmpeg-static。
// FFMPEG_PATH 环境变量可强制指定路径。
function resolveFfmpegPath() {
  // 1) 环境变量显式覆盖
  if (process.env.FFMPEG_PATH && fs.existsSync(process.env.FFMPEG_PATH)) {
    console.log('[ffmpeg] 使用 FFMPEG_PATH 指定:', process.env.FFMPEG_PATH);
    return process.env.FFMPEG_PATH;
  }
  // 2) 系统 ffmpeg（CentOS 7 + EPEL / Nux Dextop / RPM Fusion 安装的）
  for (const p of ['/usr/bin/ffmpeg', '/usr/local/bin/ffmpeg', '/opt/ffmpeg/bin/ffmpeg']) {
    if (fs.existsSync(p)) {
      console.log('[ffmpeg] 使用系统 ffmpeg:', p);
      return p;
    }
  }
  // 3) 回退 ffmpeg-static（要求 glibc ≥ 2.18，CentOS 8+/Ubuntu 18+ OK）
  try {
    const staticPath = require('ffmpeg-static');
    if (staticPath && fs.existsSync(staticPath)) {
      console.warn('[ffmpeg] 回退到 ffmpeg-static (glibc < 2.18 时可能 GLIBC 报错):', staticPath);
      return staticPath;
    }
  } catch (e) {
    console.warn('[ffmpeg] ffmpeg-static 不可用:', e.message);
  }
  console.error('[ffmpeg] 系统未装 ffmpeg 且 ffmpeg-static 不可用 — 视频合成功能将报错。'
              + '\n         CentOS 7 修复：yum install -y epel-release && yum install -y ffmpeg');
  return null;
}
const ffmpegPath = resolveFfmpegPath();
// Node 16 没原生 fetch / FormData / Blob，从 undici 拿；Node 18+ 也安全（覆盖 global）
const {
  fetch,
  FormData,
  Blob,
  ProxyAgent,
  setGlobalDispatcher,
} = require('undici');

// ── 加载 .env（独立服务也需要环境变量）─────────────────────────────────────
//   优先 server/sidecar/.env > 同目录上层 .env > process.env
function loadDotEnv() {
  const candidates = [
    path.join(__dirname, '.env'),
    path.join(__dirname, '..', '..', '.env'),
  ];
  for (const f of candidates) {
    if (!fs.existsSync(f)) continue;
    const content = fs.readFileSync(f, 'utf8');
    for (const line of content.split(/\r?\n/)) {
      const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*?)\s*$/i);
      if (!m) continue;
      const [, k, v] = m;
      if (process.env[k] == null) process.env[k] = v.replace(/^["'](.*)["']$/, '$1');
    }
  }
}
loadDotEnv();

// 让 fetch 尊重 HTTP(S)_PROXY（线上一般无代理；本地调试若用 Clash 配上）
const httpProxy = process.env.HTTPS_PROXY || process.env.HTTP_PROXY;
if (httpProxy) {
  setGlobalDispatcher(new ProxyAgent(httpProxy));
  console.log('[sidecar] fetch via proxy', httpProxy);
}

process.on('unhandledRejection', (e) => console.warn('[unhandledRejection]', e?.message || e));
process.on('uncaughtException', (e) => console.warn('[uncaughtException]', e?.message || e));

// ── TOS（火山对象存储，S3 兼容）签名 ──────────────────────────────────────
const TOS_AK = process.env.TOS_AK || process.env.JIMENG_AK || '';
const TOS_SK = process.env.TOS_SK || process.env.JIMENG_SK || '';
const TOS_REGION = process.env.TOS_REGION || 'cn-beijing';
const TOS_BUCKET = process.env.TOS_BUCKET || 'tanxiaoer';
const TOS_HOST = `${TOS_BUCKET}.tos-s3-${TOS_REGION}.volces.com`;
const TOS_BASE = `https://${TOS_HOST}`;

function tosHmac(key, data) {
  return crypto.createHmac('sha256', key).update(data).digest();
}
function tosSha256(data) {
  return crypto.createHash('sha256').update(data).digest('hex');
}
function tosSign({ method, keyPath, buf, contentType, acl }) {
  const now = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  const datetime = `${now.getUTCFullYear()}${pad(now.getUTCMonth() + 1)}${pad(now.getUTCDate())}T${pad(now.getUTCHours())}${pad(now.getUTCMinutes())}${pad(now.getUTCSeconds())}Z`;
  const shortDate = datetime.slice(0, 8);
  const contentLen = String(buf.length);
  const bodyHash = tosSha256(buf);
  const hdrs = {
    'content-type': contentType,
    host: TOS_HOST,
    'x-amz-acl': acl,
    'x-amz-content-sha256': bodyHash,
    'x-amz-date': datetime,
  };
  const sorted = Object.entries(hdrs).sort(([a], [b]) => a.localeCompare(b));
  const canonHeaders = sorted.map(([k, v]) => `${k}:${v}`).join('\n') + '\n';
  const signedHeaders = sorted.map(([k]) => k).join(';');
  const canonReq = [method, `/${keyPath}`, '', canonHeaders, signedHeaders, bodyHash].join('\n');
  const credential = `${shortDate}/${TOS_REGION}/s3/aws4_request`;
  const stringToSign = `AWS4-HMAC-SHA256\n${datetime}\n${credential}\n${tosSha256(canonReq)}`;
  let sigKey = Buffer.from('AWS4' + TOS_SK);
  sigKey = tosHmac(sigKey, shortDate);
  sigKey = tosHmac(sigKey, TOS_REGION);
  sigKey = tosHmac(sigKey, 's3');
  sigKey = tosHmac(sigKey, 'aws4_request');
  const signature = tosHmac(sigKey, stringToSign).toString('hex');
  return {
    datetime,
    contentLen,
    bodyHash,
    authorization: `AWS4-HMAC-SHA256 Credential=${TOS_AK}/${credential}, SignedHeaders=${signedHeaders}, Signature=${signature}`,
  };
}

async function fetchRetry(url, init = {}, tries = 4) {
  let lastErr;
  for (let i = 0; i < tries; i++) {
    try {
      return await fetch(url, init);
    } catch (e) {
      lastErr = e;
      console.warn(`[fetch] ${url} 第 ${i + 1}/${tries} 次失败：${e?.message || e}`);
      await new Promise((r) => setTimeout(r, 300 * (i + 1)));
    }
  }
  throw lastErr;
}

async function uploadToTos(buf, key, contentType, acl = 'public-read') {
  const { datetime, contentLen, bodyHash, authorization } = tosSign({
    method: 'PUT',
    keyPath: key,
    buf,
    contentType,
    acl,
  });
  const r = await fetchRetry(`${TOS_BASE}/${key}`, {
    method: 'PUT',
    headers: {
      Authorization: authorization,
      'X-Amz-Date': datetime,
      'X-Amz-Content-Sha256': bodyHash,
      'X-Amz-Acl': acl,
      'Content-Type': contentType,
      'Content-Length': contentLen,
    },
    body: buf,
  });
  if (!r.ok) {
    const errTxt = await r.text();
    throw new Error(`TOS put ${r.status}: ${errTxt.slice(0, 300)}`);
  }
  return `${TOS_BASE}/${key}`;
}

/**
 * 给 TOS 私有对象签发一个 GET 预签名 URL（SigV4 query 形式，TTL 默认 1h）
 *
 * 用于 KYC 证件、商户银行卡照等敏感数据 — 上传时走 acl=private，
 * 显示时调本接口拿临时 URL。绝不能给敏感数据用 public-read 永久 URL。
 */
function tosPresignGet(key, ttlSec = 3600) {
  const now = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  const datetime = `${now.getUTCFullYear()}${pad(now.getUTCMonth() + 1)}${pad(now.getUTCDate())}T${pad(now.getUTCHours())}${pad(now.getUTCMinutes())}${pad(now.getUTCSeconds())}Z`;
  const shortDate = datetime.slice(0, 8);
  const credential = `${TOS_AK}/${shortDate}/${TOS_REGION}/s3/aws4_request`;
  const signedHeaders = 'host';
  const qs = new URLSearchParams({
    'X-Amz-Algorithm': 'AWS4-HMAC-SHA256',
    'X-Amz-Credential': credential,
    'X-Amz-Date': datetime,
    'X-Amz-Expires': String(ttlSec),
    'X-Amz-SignedHeaders': signedHeaders,
  });
  const canonReq = [
    'GET',
    `/${key}`,
    qs.toString().split('&').sort().join('&'),
    `host:${TOS_HOST}\n`,
    signedHeaders,
    'UNSIGNED-PAYLOAD',
  ].join('\n');
  const credentialScope = `${shortDate}/${TOS_REGION}/s3/aws4_request`;
  const stringToSign = `AWS4-HMAC-SHA256\n${datetime}\n${credentialScope}\n${tosSha256(canonReq)}`;
  let sigKey = Buffer.from('AWS4' + TOS_SK);
  sigKey = tosHmac(sigKey, shortDate);
  sigKey = tosHmac(sigKey, TOS_REGION);
  sigKey = tosHmac(sigKey, 's3');
  sigKey = tosHmac(sigKey, 'aws4_request');
  const signature = tosHmac(sigKey, stringToSign).toString('hex');
  qs.set('X-Amz-Signature', signature);
  return `${TOS_BASE}/${key}?${qs.toString()}`;
}

// ── 火山豆包 openspeech v3 流式 TTS ────────────────────────────────────
async function getTtsBuffer(text, voice) {
  const apiKey = process.env.TTS_ACCESS_TOKEN || process.env.VOLCANO_ACCESS_TOKEN;
  const resourceId = process.env.TTS_RESOURCE_ID || 'volc.service_type.10029';
  if (!apiKey) throw new Error('未配置 TTS_ACCESS_TOKEN / VOLCANO_ACCESS_TOKEN');
  const speaker = voice || process.env.TTS_VOICE || 'zh_male_beijingxiaoye_emo_v2_mars_bigtts';
  const r = await fetch('https://openspeech.bytedance.com/api/v3/tts/unidirectional', {
    method: 'POST',
    headers: {
      'x-api-key': apiKey,
      'X-Api-Resource-Id': resourceId,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      req_params: {
        text,
        speaker,
        additions: JSON.stringify({
          disable_markdown_filter: true,
          enable_language_detector: true,
          enable_latex_tn: true,
          disable_default_bit_rate: true,
          max_length_to_filter_parenthesis: 0,
          cache_config: { text_type: 1, use_cache: true },
        }),
        audio_params: { format: 'mp3', sample_rate: 24000 },
      },
    }),
  });
  if (!r.ok || !r.body) {
    const errTxt = await r.text();
    throw new Error(`volc TTS ${r.status}: ${errTxt.slice(0, 300)}`);
  }
  const chunks = [];
  let leftover = '';
  const reader = r.body.getReader();
  const decoder = new TextDecoder();
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    leftover += decoder.decode(value, { stream: true });
    const lines = leftover.split('\n');
    leftover = lines.pop() || '';
    for (const line of lines) {
      const s = line.trim();
      if (!s) continue;
      try {
        const obj = JSON.parse(s);
        if (obj.code !== 0 && obj.code !== 20000000 && obj.code != null) {
          throw new Error(`volc code ${obj.code}: ${obj.message || ''}`);
        }
        if (obj.data) chunks.push(Buffer.from(obj.data, 'base64'));
      } catch (e) {
        if (e.message?.startsWith('volc code')) throw e;
      }
    }
  }
  if (!chunks.length) throw new Error('volc TTS 无音频数据');
  return Buffer.concat(chunks);
}

// ── ffmpeg 工具：找系统中文字体 + 跑命令 + ASS 字幕 ──────────────────────
function findChineseFont() {
  const candidates = [
    { path: '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc', name: 'Noto Sans CJK SC' },
    { path: '/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc', name: 'Noto Sans CJK SC' },
    { path: '/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc', name: 'WenQuanYi Zen Hei' },
    { path: '/usr/share/fonts/wqy-zenhei/wqy-zenhei.ttc', name: 'WenQuanYi Zen Hei' },
    { path: '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', name: 'DejaVu Sans' },
    { path: '/System/Library/Fonts/PingFang.ttc', name: 'PingFang SC' },
    { path: '/Library/Fonts/Arial Unicode.ttf', name: 'Arial Unicode MS' },
    { path: 'C:\\Windows\\Fonts\\msyh.ttc', name: 'Microsoft YaHei' },
  ];
  for (const f of candidates) {
    try {
      if (fs.existsSync(f.path)) return f;
    } catch {}
  }
  return null;
}

function runFfmpeg(args) {
  return new Promise((resolve, reject) => {
    const proc = spawn(ffmpegPath, args, { stdio: ['ignore', 'ignore', 'pipe'] });
    let errOut = '';
    proc.stderr.on('data', (c) => { errOut += c.toString(); });
    proc.on('close', (code) => {
      if (code === 0) resolve();
      else reject(new Error(`ffmpeg exit ${code}: ${errOut.slice(-600)}`));
    });
    proc.on('error', reject);
  });
}

// 端卡 ASS 字幕：QR 上方"微信扫描二维码在线下单"+ 店名居中
function buildShopAss(shopName, fontName) {
  const safe = String(shopName || '').replace(/[{}\\]/g, '').slice(0, 24);
  const tip = '微信扫描二维码在线下单';
  const brandLine = safe
    ? `\nDialogue: 0,0:00:00.00,0:00:10.00,Brand,,0,0,0,,{\\pos(360,1060)}${safe}`
    : '';
  return `[Script Info]
ScriptType: v4.00+
WrapStyle: 2
PlayResX: 720
PlayResY: 1280
ScaledBorderAndShadow: yes

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Tip,${fontName},44,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,-1,0,0,0,100,100,2,0,1,2,1,5,0,0,0,1
Style: Brand,${fontName},56,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,-1,0,0,0,100,100,2,0,1,2,2,5,0,0,0,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:00.00,0:00:10.00,Tip,,0,0,0,,{\\pos(360,310)}${tip}${brandLine}`;
}

// 主视频字幕：抖音风格大字花字（fad + move）
function buildHypeAss(text, durationSec, fontName) {
  const fmtTime = (s) => {
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const sc = (s % 60).toFixed(2).padStart(5, '0');
    return `${h}:${String(m).padStart(2, '0')}:${sc}`;
  };
  const wrapText = (t) => {
    if (t.length <= 13) return t;
    const mid = Math.ceil(t.length / 2);
    const breakAt = [
      t.indexOf('，', mid - 5),
      t.indexOf('。', mid - 5),
      t.indexOf('、', mid - 5),
      t.indexOf(' ', mid - 5),
      mid,
    ].find((i) => i > 0) ?? mid;
    return t.slice(0, breakAt) + '\\N' + t.slice(breakAt);
  };
  const endSec = Math.max(durationSec - 0.3, durationSec * 0.92);
  const safeText = wrapText(text).replace(/:/g, '：');
  return `[Script Info]
ScriptType: v4.00+
WrapStyle: 2
PlayResX: 720
PlayResY: 1280
ScaledBorderAndShadow: yes

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Hype,${fontName},58,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,-1,0,0,0,100,100,2,0,1,4,3,2,60,60,130,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,${fmtTime(0.15)},${fmtTime(endSec)},Hype,,0,0,0,,{\\fad(350,450)\\move(360,1230,360,1130,0,550)}${safeText}`;
}

// ─────────────────────────── HTTP 路由 ───────────────────────────────────
const app = express();
app.use(express.json({ limit: '50mb' }));

app.get('/healthz', (_req, res) => res.json({ ok: true, ts: Date.now() }));

// ── /oss/upload ────────────────────────────────────────────────
// 入参 acl: 'public-read' (默认，公开图片如店铺封面/视频背景图) | 'private' (敏感数据如 KYC 证件)
//   private 模式：上传后返回的 url 是预签名 1h GET URL；要长期访问得调 /oss/sign 重新签
//   private key 应入库存储，每次显示再签新的临时 URL
const ALLOWED_ACL = new Set(['public-read', 'private']);
app.post('/oss/upload', async (req, res) => {
  try {
    const { base64, ext = 'jpg', prefix = 'tanxiaoer', acl = 'public-read' } = req.body || {};
    if (!base64) throw new Error('base64 为空');
    if (!ALLOWED_ACL.has(acl)) throw new Error(`acl 非法：${acl}（仅支持 public-read / private）`);
    const clean = base64.replace(/^data:image\/[a-z]+;base64,/, '');
    const buf = Buffer.from(clean, 'base64');
    const key = `${prefix}/${Date.now()}-${Math.random().toString(36).slice(2, 8)}.${ext}`;
    const contentType = ext === 'png' ? 'image/png' : ext === 'webp' ? 'image/webp' : 'image/jpeg';
    await uploadToTos(buf, key, contentType, acl);
    // public-read：直接返永久公网 URL
    // private：返 1h 预签名 GET URL（前端可立即展示；入库时只存 key）
    const url = acl === 'private' ? tosPresignGet(key, 3600) : `${TOS_BASE}/${key}`;
    res.json({ ok: true, url, key, acl });
  } catch (e) {
    console.error('[oss/upload]', e);
    res.status(500).json({ ok: false, error: e.message });
  }
});

// ── /oss/sign ─ 给私有对象签发临时 GET URL ──────────────────────
// 用法：GET /oss/sign?key=<TOS key>&ttl=<秒，默认3600，最多86400>
// 鉴权：必须带 X-Internal-Token == MERCHANT_INTERNAL_TOKEN
//   yudao-server 鉴权后转发；浏览器/外部不能直连（nginx 反代 /oss/* 会透传 header
//   而外部不知道 token，所以拒）
const SIDECAR_INTERNAL_TOKEN = process.env.MERCHANT_INTERNAL_TOKEN || '';
function checkInternalToken(req, res) {
  if (!SIDECAR_INTERNAL_TOKEN) {
    res.status(503).json({ ok: false, error: 'sidecar 未配置 MERCHANT_INTERNAL_TOKEN' });
    return false;
  }
  const t = req.headers['x-internal-token'];
  if (t !== SIDECAR_INTERNAL_TOKEN) {
    res.status(401).json({ ok: false, error: 'invalid internal token' });
    return false;
  }
  return true;
}
app.get('/oss/sign', (req, res) => {
  if (!checkInternalToken(req, res)) return;
  try {
    const key = String(req.query.key || '').trim();
    if (!key) throw new Error('key 为空');
    if (key.includes('..') || key.startsWith('/')) throw new Error('key 非法');
    const ttl = Math.min(86400, Math.max(60, Number(req.query.ttl) || 3600));
    const url = tosPresignGet(key, ttl);
    res.json({ ok: true, url, key, ttl });
  } catch (e) {
    res.status(400).json({ ok: false, error: e.message });
  }
});

// ── /tts/volc ─ 真火山 openspeech v3 TTS ──────────────────────────
app.post('/tts/volc', async (req, res) => {
  try {
    const { text, voice } = req.body || {};
    if (!text) throw new Error('text 为空');
    const buf = await getTtsBuffer(text, voice);
    res.setHeader('Content-Type', 'audio/mpeg');
    res.setHeader('X-Tts-Source', 'volc');
    res.end(buf);
  } catch (e) {
    console.warn('[tts/volc]', e.message);
    res.status(500).json({ error: e.message });
  }
});

// ── /tts/edge ─ Microsoft Edge TTS（备用，按需 npm i msedge-tts）──
app.post('/tts/edge', async (req, res) => {
  try {
    const { text, voice = 'zh-CN-XiaoxiaoNeural' } = req.body || {};
    if (!text) throw new Error('text 为空');
    const mod = await import('msedge-tts');
    const MsEdgeTTS = mod.MsEdgeTTS || mod.default?.MsEdgeTTS;
    const OUTPUT_FORMAT = mod.OUTPUT_FORMAT || mod.default?.OUTPUT_FORMAT;
    const tts = new MsEdgeTTS();
    await tts.setMetadata(voice, OUTPUT_FORMAT.AUDIO_24KHZ_48KBITRATE_MONO_MP3);
    const { audioStream } = tts.toStream(text);
    const chunks = [];
    await new Promise((resolve, reject) => {
      audioStream.on('data', (c) => chunks.push(c));
      audioStream.on('end', resolve);
      audioStream.on('error', reject);
    });
    res.setHeader('Content-Type', 'audio/mpeg');
    res.setHeader('X-Tts-Source', 'edge');
    res.end(Buffer.concat(chunks));
  } catch (e) {
    console.error('[tts/edge]', e);
    res.status(500).json({ error: e.message });
  }
});

// ── /vproxy ─ 视频反代加 CORS ─────────────────────────────────────
app.get('/vproxy', async (req, res) => {
  try {
    const target = req.query.url;
    if (!target) throw new Error('missing url');
    const upstream = await fetchRetry(target);
    if (!upstream.ok) throw new Error(`upstream ${upstream.status}`);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Cache-Control', 'public, max-age=600');
    const ct = upstream.headers.get('content-type');
    if (ct) res.setHeader('Content-Type', ct);
    const cl = upstream.headers.get('content-length');
    if (cl) res.setHeader('Content-Length', cl);
    const buf = Buffer.from(await upstream.arrayBuffer());
    res.end(buf);
  } catch (e) {
    console.warn('[vproxy]', e.message);
    res.status(502).end(e.message);
  }
});

// ── /video/merge ─ 多视频片段拼接（concat demuxer，统一 720x1280 25fps）
app.post('/video/merge', async (req, res) => {
  let workDir;
  try {
    const { urls = [], uploadTos = false } = req.body || {};
    if (!Array.isArray(urls) || !urls.length) throw new Error('urls 为空');
    if (!ffmpegPath) throw new Error('ffmpeg-static 未提供二进制路径');

    workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'tanxiaoer-merge-'));
    const localFiles = await Promise.all(
      urls.map(async (url, i) => {
        const r = await fetchRetry(url);
        if (!r.ok) throw new Error(`片段 ${i + 1} 下载失败 ${r.status}`);
        const buf = Buffer.from(await r.arrayBuffer());
        const f = path.join(workDir, `seg-${String(i).padStart(2, '0')}.mp4`);
        fs.writeFileSync(f, buf);
        return f;
      })
    );

    const audioPresence = await Promise.all(
      localFiles.map(
        (f) =>
          new Promise((resolve) => {
            const p = spawn(ffmpegPath, ['-i', f, '-hide_banner'], {
              stdio: ['ignore', 'ignore', 'pipe'],
            });
            let out = '';
            p.stderr.on('data', (c) => { out += c.toString(); });
            p.on('close', () => resolve(/Stream.*?Audio/i.test(out)));
          })
      )
    );
    const hasAnyAudio = audioPresence.some(Boolean);

    const CANON = { w: 720, h: 1280, fps: 25, sar: 1 };
    const normalizeVideo = (i) =>
      `[${i}:v:0]scale=${CANON.w}:${CANON.h}:force_original_aspect_ratio=decrease,` +
      `pad=${CANON.w}:${CANON.h}:(ow-iw)/2:(oh-ih)/2:color=black,` +
      `setsar=${CANON.sar},fps=${CANON.fps},format=yuv420p[v${i}]`;
    const normalizeAudio = (src, i) => `${src}aresample=44100,asetpts=PTS-STARTPTS[a${i}]`;

    const extraLavfiArgs = [];
    const audioSources = audioPresence.map((has, i) => {
      if (has) return `[${i}:a:0]`;
      const idx = localFiles.length + extraLavfiArgs.length / 4;
      extraLavfiArgs.push('-f', 'lavfi', '-i', 'anullsrc=r=44100:cl=stereo');
      return `[${idx}:a]`;
    });

    const normChains = localFiles.map((_, i) => normalizeVideo(i));
    const normAudioChains = hasAnyAudio
      ? localFiles.map((_, i) => normalizeAudio(audioSources[i], i))
      : [];
    const concatInputs = localFiles.map((_, i) => (hasAnyAudio ? `[v${i}][a${i}]` : `[v${i}]`)).join('');
    const filterComplex = hasAnyAudio
      ? `${[...normChains, ...normAudioChains].join(';')};${concatInputs}concat=n=${localFiles.length}:v=1:a=1[v][a]`
      : `${normChains.join(';')};${concatInputs}concat=n=${localFiles.length}:v=1:a=0[v]`;

    const outFile = path.join(workDir, 'merged.mp4');
    const mergeArgs = [
      '-y',
      ...localFiles.flatMap((f) => ['-i', f]),
      ...(hasAnyAudio ? extraLavfiArgs : []),
      '-filter_complex', filterComplex,
      '-map', '[v]',
      ...(hasAnyAudio ? ['-map', '[a]'] : []),
      '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '23',
      ...(hasAnyAudio ? ['-c:a', 'aac'] : []),
      '-movflags', '+faststart',
      outFile,
    ];
    await runFfmpeg(mergeArgs);

    const mp4 = fs.readFileSync(outFile);
    if (uploadTos) {
      const key = `tanxiaoer/merged/${Date.now()}-${Math.random().toString(36).slice(2, 8)}.mp4`;
      const url = await uploadToTos(mp4, key, 'video/mp4');
      res.json({ ok: true, url, size: mp4.length });
      return;
    }
    res.setHeader('Content-Type', 'video/mp4');
    res.setHeader('Content-Disposition', 'attachment; filename="merged.mp4"');
    res.setHeader('Content-Length', String(mp4.length));
    res.end(mp4);
  } catch (e) {
    console.warn('[merge]', e.message);
    res.status(500).json({ error: e.message });
  } finally {
    if (workDir) {
      try { fs.rmSync(workDir, { recursive: true, force: true }); } catch {}
    }
  }
});

// ── BGM 风格库（改造点 ⑤）─────────────────────────────────────────
// LLM 输出 bgmStyle key → 这里随机选一首 sidecar/bgm/<style>_N.mp3
// 候选 key: street_food_yelling / cozy_explore / asmr_macro
//          elegant_tea / trendy_pop / emotional_story
// 文件由运维去 Pixabay/FreePD 等 CC0 曲库下载，每风格 2-3 首避免雷同
const BGM_DIR = path.join(__dirname, 'bgm');
function pickBgm(bgmStyle) {
  // 'none' 显式不加 BGM —— 前端用户在 confirm 页选"不加 BGM"时传这个 sentinel
  // 空串 / undefined 仍走默认 cozy_explore（保留向后兼容）
  if (bgmStyle === 'none') return null;
  const style = (bgmStyle || 'cozy_explore').replace(/[^a-z_]/gi, '');
  if (!fs.existsSync(BGM_DIR)) return null;
  let cand = [];
  try { cand = fs.readdirSync(BGM_DIR).filter((f) => f.startsWith(style + '_') && f.endsWith('.mp3')); } catch {}
  if (!cand.length) {
    try { cand = fs.readdirSync(BGM_DIR).filter((f) => f.endsWith('.mp3')); } catch {}
  }
  if (!cand.length) return null;
  return path.join(BGM_DIR, cand[Math.floor(Math.random() * cand.length)]);
}

// ── /video/mux ─ 主视频 + TTS 主声 + BGM 背景 + 字幕烧录 → 上传 TOS ─
app.post('/video/mux', async (req, res) => {
  let workDir;
  try {
    const { videoUrl, text, voice, duration = 10, bgmStyle = 'cozy_explore' } = req.body || {};
    if (!videoUrl) throw new Error('videoUrl 为空');
    if (!text) throw new Error('text 为空');

    workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'tanxiaoer-mux-'));

    const [audioBuf, vidResp] = await Promise.all([
      getTtsBuffer(text, voice),
      fetchRetry(videoUrl),
    ]);
    if (!vidResp.ok) throw new Error(`视频下载失败 ${vidResp.status}`);
    const vidBuf = Buffer.from(await vidResp.arrayBuffer());

    const vidFile = path.join(workDir, 'input.mp4');
    const audFile = path.join(workDir, 'audio.mp3');
    const assFile = path.join(workDir, 'sub.ass');
    const outFile = path.join(workDir, 'output.mp4');
    fs.writeFileSync(vidFile, vidBuf);
    fs.writeFileSync(audFile, audioBuf);

    const durSec = Math.max(1, Number(duration) || 10);
    const bgmFile = pickBgm(bgmStyle);
    if (bgmFile) {
      console.log(`[mux] 使用 BGM ${path.basename(bgmFile)} (style=${bgmStyle})`);
    } else {
      console.log(`[mux] 无 BGM (sidecar/bgm/ 空或缺 ${bgmStyle}_*.mp3) — 仅 TTS`);
    }

    // 音频混音：TTS 主声 (1.0) + BGM 背景 (0.18 = -15dB)
    const audioFilter = bgmFile
      ? '[1:a]volume=1.0[a1];[2:a]volume=0.18[a2];[a1][a2]amix=inputs=2:duration=longest:dropout_transition=2[a]'
      : '[1:a]volume=1.0[a]';
    const inputArgs = bgmFile
      ? ['-i', vidFile, '-i', audFile, '-stream_loop', '-1', '-i', bgmFile]
      : ['-i', vidFile, '-i', audFile];

    const audioOnlyArgs = [
      '-y', ...inputArgs,
      '-filter_complex', audioFilter,
      '-map', '0:v:0', '-map', '[a]',
      '-c:v', 'copy', '-c:a', 'aac',
      '-t', String(durSec),
      '-movflags', '+faststart',
      outFile,
    ];

    const font = findChineseFont();
    if (font) {
      fs.writeFileSync(assFile, buildHypeAss(text, durSec, font.name), 'utf8');
      const fontDir = path.dirname(font.path).replace(/\\/g, '/').replace(/:/g, '\\:');
      const assPath = assFile.replace(/\\/g, '/').replace(/:/g, '\\:');
      const subtitleArgs = [
        '-y', ...inputArgs,
        '-filter_complex',
          `[0:v]subtitles=${assPath}:fontsdir=${fontDir}[v];${audioFilter}`,
        '-map', '[v]', '-map', '[a]',
        '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '22',
        '-c:a', 'aac',
        '-t', String(durSec),
        '-movflags', '+faststart',
        outFile,
      ];
      try {
        await runFfmpeg(subtitleArgs);
      } catch (subErr) {
        console.warn(`[mux] 字幕渲染失败，降级 TTS+BGM: ${subErr.message.slice(0, 200)}`);
        if (fs.existsSync(outFile)) fs.unlinkSync(outFile);
        await runFfmpeg(audioOnlyArgs);
      }
    } else {
      console.warn('[mux] 未找到中文字体，降级为 TTS+BGM');
      await runFfmpeg(audioOnlyArgs);
    }

    const outBuf = fs.readFileSync(outFile);
    const key = `tanxiaoer/muxed/${Date.now()}-${Math.random().toString(36).slice(2, 8)}.mp4`;
    const url = await uploadToTos(outBuf, key, 'video/mp4');
    console.log(`[mux] 完成 ${(outBuf.length / 1024 / 1024).toFixed(1)} MB → ${key}`);
    res.json({ ok: true, url });
  } catch (e) {
    console.error('[video/mux]', e.message);
    res.status(500).json({ ok: false, error: e.message });
  } finally {
    if (workDir) {
      try { fs.rmSync(workDir, { recursive: true, force: true }); } catch {}
    }
  }
});

// ── ★ /video/endcard ─ 端卡：商品图 + 居中大二维码 + 店名 + TTS ───
app.post('/video/endcard', async (req, res) => {
  let workDir;
  try {
    const { imageUrl, shopName = '', text, voice, duration = 3, qrUrl } = req.body || {};
    if (!imageUrl) throw new Error('imageUrl 为空');
    if (!text) throw new Error('text 为空');

    workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'tanxiaoer-endcard-'));

    const [audioBuf, imgResp] = await Promise.all([
      getTtsBuffer(text, voice),
      fetchRetry(imageUrl),
    ]);
    if (!imgResp.ok) throw new Error(`图片下载失败 ${imgResp.status}`);
    const imgBuf = Buffer.from(await imgResp.arrayBuffer());

    const imgFile = path.join(workDir, 'bg.jpg');
    const audFile = path.join(workDir, 'audio.mp3');
    const qrFile = path.join(workDir, 'qr.png');
    const assFile = path.join(workDir, 'shop.ass');
    const outFile = path.join(workDir, 'endcard.mp4');
    fs.writeFileSync(imgFile, imgBuf);
    fs.writeFileSync(audFile, audioBuf);

    // 商户分享码（front-end 已传入；缺失则用占位）
    const finalQrUrl = qrUrl || `https://www.doupaidoudian.com/m/shop-home`;
    await QRCode.toFile(qrFile, finalQrUrl, {
      errorCorrectionLevel: 'M',
      margin: 2,
      width: 800,
      color: { dark: '#000000', light: '#FFFFFFFF' },
    });

    const durSec = Math.max(1, Number(duration) || 3);
    const font = findChineseFont();

    const bgChain =
      `[0:v]scale=720:1280:force_original_aspect_ratio=increase,` +
      `crop=720:1280,boxblur=10:1,` +
      `drawbox=x=0:y=0:w=iw:h=ih:color=black@0.45:t=fill[bg]`;
    const qrChain =
      `[2:v][bg]scale2ref=w='min(main_w\\,main_h)*0.50':h=ow[qr][vbg];` +
      `[vbg][qr]overlay=(W-w)/2:(H-h)/2-H*0.05[v_qr]`;

    let filterComplex;
    if (font) {
      fs.writeFileSync(assFile, buildShopAss(shopName, font.name), 'utf8');
      const fontDir = path.dirname(font.path).replace(/\\/g, '/').replace(/:/g, '\\:');
      const assPath = assFile.replace(/\\/g, '/').replace(/:/g, '\\:');
      filterComplex = `${bgChain};${qrChain};[v_qr]subtitles=${assPath}:fontsdir=${fontDir}[v]`;
    } else {
      filterComplex = `${bgChain};${qrChain};[v_qr]null[v]`;
    }

    const args = [
      '-y',
      '-loop', '1', '-i', imgFile,
      '-i', audFile,
      '-i', qrFile,
      '-filter_complex', filterComplex,
      '-map', '[v]', '-map', '1:a:0',
      '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '22', '-pix_fmt', 'yuv420p',
      '-c:a', 'aac', '-af', 'apad',
      '-t', String(durSec),
      '-r', '25',
      '-movflags', '+faststart',
      outFile,
    ];
    console.log(`[endcard] shop="${shopName}", qr=${finalQrUrl}, dur=${durSec}s`);
    await runFfmpeg(args);

    const outBuf = fs.readFileSync(outFile);
    const key = `tanxiaoer/endcard/${Date.now()}-${Math.random().toString(36).slice(2, 8)}.mp4`;
    const url = await uploadToTos(outBuf, key, 'video/mp4');
    console.log(`[endcard] 完成 ${(outBuf.length / 1024 / 1024).toFixed(1)} MB → ${key}`);
    res.json({ ok: true, url });
  } catch (e) {
    // 详细日志：stack + 关键入参，方便定位是 ffmpeg / 字体 / TTS / OSS 哪步挂
    console.error('[video/endcard] 失败:', e && e.stack ? e.stack : e);
    console.error('[video/endcard] 入参 imageUrl=%s shopName=%s textLen=%s voice=%s qrUrl=%s',
        (req.body && req.body.imageUrl) || '<none>',
        (req.body && req.body.shopName) || '<none>',
        (req.body && req.body.text) ? String(req.body.text).length : 0,
        (req.body && req.body.voice) || '<none>',
        (req.body && req.body.qrUrl) || '<none>');
    res.status(500).json({ ok: false, error: e.message, stage: e.stage || 'unknown' });
  } finally {
    if (workDir) {
      try { fs.rmSync(workDir, { recursive: true, force: true }); } catch {}
    }
  }
});

// ── /jimeng ─ @deprecated 即梦签名代理（保留向后兼容） ──────────────
const JIMENG_AK = process.env.JIMENG_AK || '';
const JIMENG_SK = process.env.JIMENG_SK || '';
const JIMENG_REGION = 'cn-north-1';
const JIMENG_SERVICE = 'cv';
const JIMENG_VERSION = '2022-08-31';

function jimengSign(action, bodyStr) {
  const now = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  const xDate = `${now.getUTCFullYear()}${pad(now.getUTCMonth() + 1)}${pad(now.getUTCDate())}T${pad(now.getUTCHours())}${pad(now.getUTCMinutes())}${pad(now.getUTCSeconds())}Z`;
  const shortDate = xDate.slice(0, 8);
  const signedHeaders = 'content-type;x-date';
  const credential = `${shortDate}/${JIMENG_REGION}/${JIMENG_SERVICE}/request`;
  const bodyHash = crypto.createHash('sha256').update(bodyStr).digest('hex');
  const canonReq = ['POST', '/', `Action=${action}&Version=${JIMENG_VERSION}`, 'content-type:application/json', `x-date:${xDate}`, '', signedHeaders, bodyHash].join('\n');
  const hashedCanon = crypto.createHash('sha256').update(canonReq).digest('hex');
  const stringToSign = `HMAC-SHA256\n${xDate}\n${credential}\n${hashedCanon}`;
  let key = Buffer.from(JIMENG_SK);
  key = crypto.createHmac('sha256', key).update(shortDate).digest();
  key = crypto.createHmac('sha256', key).update(JIMENG_REGION).digest();
  key = crypto.createHmac('sha256', key).update(JIMENG_SERVICE).digest();
  key = crypto.createHmac('sha256', key).update('request').digest();
  const signature = crypto.createHmac('sha256', key).update(stringToSign).digest('hex');
  return {
    'Content-Type': 'application/json',
    'X-Date': xDate,
    Authorization: `HMAC-SHA256 Credential=${JIMENG_AK}/${credential}, SignedHeaders=${signedHeaders}, Signature=${signature}`,
  };
}

/**
 * 即梦图生图调用（共用 helper）
 * @param {string} action  即梦 API Action 名（CVProcess / Img2ImgInpainting / OutPainting 等）
 * @param {object} payload 请求体
 */
async function callJimeng(action, payload) {
  if (!JIMENG_AK || !JIMENG_SK) throw new Error('JIMENG_AK/SK 未配置');
  const bodyStr = JSON.stringify(payload || {});
  const headers = jimengSign(action, bodyStr);
  const r = await fetchRetry(
    `https://visual.volcengineapi.com?Action=${action}&Version=${JIMENG_VERSION}`,
    { method: 'POST', headers, body: bodyStr },
  );
  const text = await r.text();
  let json;
  try { json = JSON.parse(text); } catch { throw new Error(`即梦返回非 JSON: ${text.slice(0, 200)}`); }
  if (!r.ok || (json.code != null && json.code !== 10000)) {
    throw new Error(`即梦 ${action} 失败: ${json.message || json.msg || r.status}`);
  }
  return json;
}

// ── /jimeng/enhance ─ 改造点 ① 图生图美化预处理 ────────────────────
//
// 老板手机拍的图常光线差/构图随意，先用即梦图生图增强一遍再喂 Seedance：
// - 食物自动提亮、加饱和
// - 浅景深虚化背景
// - 街拍质感（暖色调 + film grain）
// 输入: { imageUrl, businessHint? } — businessHint 帮 LLM 描述更准
// 输出: { ok, url } — 增强后的图片公网 URL（已落 TOS）
app.post('/jimeng/enhance', async (req, res) => {
  try {
    const { imageUrl, businessHint = '美食/产品' } = req.body || {};
    if (!imageUrl) throw new Error('imageUrl 为空');

    const enhancePrompt =
      `专业${businessHint}摄影，自然光，浅景深，色彩饱满，` +
      `街头夜市质感，金黄时刻光线，电影感胶片颗粒，竖屏构图`;

    const json = await callJimeng('CVProcess', {
      req_key: 'high_aes_general_v30l_zt2i',  // 通用图像处理（增强类）
      prompt: enhancePrompt,
      image_urls: [imageUrl],
      scale: 7,
      ddim_steps: 20,
      width: 1080,
      height: 1920,
      return_url: true,
    });

    // 即梦返回 image_urls 数组
    const out = (json.data && json.data.image_urls && json.data.image_urls[0]) || null;
    if (!out) throw new Error(`即梦未返图: ${JSON.stringify(json).slice(0, 200)}`);
    res.json({ ok: true, url: out, enhanced: true });
  } catch (e) {
    console.warn('[jimeng/enhance]', e.message);
    // 失败时返回原图 URL，让上游不阻塞主流程
    res.json({ ok: false, url: req.body?.imageUrl, enhanced: false, error: e.message });
  }
});

// ── /jimeng/poster ─ 改造点 ④ 即梦图生图生成端卡海报 ───────────────
//
// 替代原 ffmpeg drawtext 拼图端卡，用即梦生成专业海报：
// - 店名艺术字 + 二维码视觉位 + 暖色背景
// 输入: { shopName, slogan? } — slogan 默认 "立即扫码下单"
// 输出: { ok, url } — 海报图 URL（之后 sidecar /video/endcard 用这个图当背景）
app.post('/jimeng/poster', async (req, res) => {
  try {
    const { shopName, slogan = '立即扫码下单' } = req.body || {};
    if (!shopName) throw new Error('shopName 为空');

    const posterPrompt =
      `Chinese street food restaurant promotional poster, ` +
      `shop name "${shopName}" centered with bold modern typography, ` +
      `space below for QR code, ` +
      `slogan "${slogan}" at bottom, ` +
      `warm orange gradient background, ` +
      `cinematic high-end commercial photography style, vertical 9:16`;

    const json = await callJimeng('CVProcess', {
      req_key: 'high_aes_general_v30l',
      prompt: posterPrompt,
      scale: 7.5,
      ddim_steps: 25,
      width: 1080,
      height: 1920,
      return_url: true,
    });

    const out = (json.data && json.data.image_urls && json.data.image_urls[0]) || null;
    if (!out) throw new Error(`即梦未返图: ${JSON.stringify(json).slice(0, 200)}`);
    res.json({ ok: true, url: out });
  } catch (e) {
    console.warn('[jimeng/poster]', e.message);
    res.status(500).json({ ok: false, error: e.message });
  }
});

app.post('/jimeng', async (req, res) => {
  try {
    const action = req.query.action;
    if (!action) throw new Error('missing action param');
    const bodyStr = JSON.stringify(req.body || {});
    const headers = jimengSign(action, bodyStr);
    const r = await fetchRetry(`https://visual.volcengineapi.com?Action=${action}&Version=${JIMENG_VERSION}`, {
      method: 'POST',
      headers,
      body: bodyStr,
    });
    const text = await r.text();
    res.status(r.status).type('application/json').end(text);
  } catch (e) {
    console.error('[jimeng]', e.message);
    res.status(500).json({ code: -1, message: e.message });
  }
});

// ── /douyin/* ─ 抖音 OAuth + 发布 ──────────────────────────────────
const DOUYIN_CLIENT_KEY = process.env.DOUYIN_CLIENT_KEY || '';
const DOUYIN_CLIENT_SECRET = process.env.DOUYIN_CLIENT_SECRET || '';
const DOUYIN_OAUTH_CONNECT = 'https://open.douyin.com/platform/oauth/connect/';
const DOUYIN_ACCESS_TOKEN = 'https://open.douyin.com/oauth/access_token/';
const DOUYIN_UPLOAD_VIDEO = 'https://open.douyin.com/api/douyin/v1/video/upload_video/';
const DOUYIN_CREATE_VIDEO = 'https://open.douyin.com/api/douyin/v1/video/create_video/';

// 全局 demo 兜底守卫：DEMO_MODE=true 才允许走 fake 抖音授权链路
const DEMO_MODE = process.env.DEMO_MODE === 'true';

app.get('/douyin/auth-url', (req, res) => {
  try {
    const redirectUri =
      req.query.redirect_uri ||
      `${req.headers['x-forwarded-proto'] || 'http'}://${req.headers.host}/douyin/oauth-callback`;
    const state = crypto.randomBytes(8).toString('hex');

    // Demo 兜底（仅 DEMO_MODE=true 时启用）：未配 DOUYIN_CLIENT_KEY 时返一个本地
    // demo 授权页 URL，用户能看到完整 OAuth 弹窗动画，前端拿到 fake token；
    // 拿到真 KEY 后切回真链路，前端无改动。
    // 生产 (DEMO_MODE=false) 必须有 DOUYIN_CLIENT_KEY，否则直接报错让运维补 KEY。
    if (!DOUYIN_CLIENT_KEY) {
      if (!DEMO_MODE) throw new Error('DOUYIN_CLIENT_KEY 未配置 — 请在 .env 中填抖音开放平台分配的 client_key');
      const fakeUrl = `${req.headers['x-forwarded-proto'] || 'http'}://${req.headers.host}/douyin/demo-auth?state=${state}&redirect_uri=${encodeURIComponent(redirectUri)}`;
      return res.json({ ok: true, url: fakeUrl, state, demo: true });
    }

    const u = new URL(DOUYIN_OAUTH_CONNECT);
    u.searchParams.set('client_key', DOUYIN_CLIENT_KEY);
    u.searchParams.set('response_type', 'code');
    u.searchParams.set('scope', 'user_info,video.create,video.upload');
    u.searchParams.set('redirect_uri', redirectUri);
    u.searchParams.set('state', state);
    res.json({ ok: true, url: u.toString(), state });
  } catch (e) {
    res.status(500).json({ ok: false, error: e.message });
  }
});

// Demo 兜底：本地 fake 授权页（仅 DEMO_MODE=true 时启用，生产 404）
app.get('/douyin/demo-auth', (req, res) => {
  if (!DEMO_MODE) return res.status(404).end('Not Found');
  const { state = '', redirect_uri = '' } = req.query;
  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.end(`<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>抖音授权</title>
<style>
  body { margin:0; font-family: -apple-system, "PingFang SC", sans-serif; background:#000; color:#fff; min-height:100vh; display:flex; flex-direction:column; align-items:center; justify-content:center; padding:32px; }
  .logo { width: 80px; height: 80px; background: linear-gradient(135deg, #fe2c55, #25f4ee); border-radius: 18px; display:flex; align-items:center; justify-content:center; font-size:42px; font-weight:bold; margin-bottom:24px; }
  h1 { font-size: 22px; margin: 8px 0; }
  .sub { color:#999; margin-bottom: 32px; font-size: 14px; }
  .card { background:#1a1a1a; border-radius:12px; padding:20px; width:100%; max-width: 320px; margin-bottom: 16px; }
  .row { display:flex; align-items:center; padding: 8px 0; color:#ddd; font-size:14px; }
  .row::before { content:"✓"; color:#25f4ee; margin-right: 12px; font-weight:bold; }
  .btn { width:100%; max-width:320px; height:48px; border-radius:24px; background:#fe2c55; color:#fff; border:none; font-size:16px; font-weight:600; }
  .tip { color:#666; font-size:12px; margin-top:24px; }
</style></head><body>
<div class="logo">抖</div>
<h1>授权"摊小二"</h1>
<div class="sub">摊小二 SaaS 想要访问你的抖音账号</div>
<div class="card">
  <div class="row">读取你的抖音昵称和头像</div>
  <div class="row">代你上传视频草稿</div>
  <div class="row">代你发布视频到你的主页</div>
</div>
<button class="btn" onclick="agree()">同意授权</button>
<div class="tip">⚠️ 演示模式：抖音应用未上线，授权为本地 mock</div>
<script>
function agree(){
  var qs = new URLSearchParams({code:'DEMO_'+Date.now(), state:${JSON.stringify(state)}}).toString();
  location.href = ${JSON.stringify(redirect_uri)} + (${JSON.stringify(redirect_uri)}.indexOf('?')<0?'?':'&') + qs;
}
</script></body></html>`);
});

app.get('/douyin/oauth-callback', async (req, res) => {
  try {
    const { code, state = '', errcode, error } = req.query;
    const err = errcode || error;
    if (err) throw new Error(`douyin oauth error: ${err} ${req.query.error_description || ''}`);
    if (!code) throw new Error('回调缺少 code');

    let token;
    // Demo 兜底（仅 DEMO_MODE=true）：未配 KEY 或 code 来自本地 demo-auth 页时返 fake token
    if (DEMO_MODE && (!DOUYIN_CLIENT_KEY || !DOUYIN_CLIENT_SECRET || String(code).startsWith('DEMO_'))) {
      token = {
        accessToken: 'DEMO_AT_' + crypto.randomBytes(8).toString('hex'),
        refreshToken: 'DEMO_RT_' + crypto.randomBytes(8).toString('hex'),
        openId: 'DEMO_OPENID_' + crypto.randomBytes(4).toString('hex'),
        expiresIn: 86400,
        grantedAt: Date.now(),
        demo: true,
      };
    } else if (!DOUYIN_CLIENT_KEY || !DOUYIN_CLIENT_SECRET) {
      throw new Error('未配置 DOUYIN_CLIENT_KEY/SECRET');
    } else {
      const body = new URLSearchParams({
        client_key: DOUYIN_CLIENT_KEY,
        client_secret: DOUYIN_CLIENT_SECRET,
        code,
        grant_type: 'authorization_code',
      });
      const r = await fetchRetry(DOUYIN_ACCESS_TOKEN, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: body.toString(),
      });
      const payload = await r.json().catch(() => ({}));
      const d = payload?.data || {};
      if (!r.ok || !d.access_token) throw new Error(`access_token 换取失败：${JSON.stringify(payload).slice(0, 300)}`);
      token = {
        accessToken: d.access_token,
        refreshToken: d.refresh_token || '',
        openId: d.open_id || '',
        expiresIn: d.expires_in || 0,
        grantedAt: Date.now(),
      };
    }
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.end(`<!doctype html><html><head><meta charset="utf-8"><title>抖音授权成功</title></head>
<body style="font-family:sans-serif;padding:48px;text-align:center;">
<h3>✅ 抖音授权成功</h3>
<p style="color:#888">窗口将自动关闭，回到刚才的页面继续发布…</p>
<script>
(function(){
  var data = ${JSON.stringify({ type: 'douyin-oauth', ok: true, state, token })};
  try { if (window.opener) { window.opener.postMessage(data, '*'); } } catch (e) {}
  setTimeout(function(){ window.close(); }, 400);
})();
</script></body></html>`);
  } catch (e) {
    console.error('[douyin/oauth-callback]', e.message);
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.end(`<!doctype html><html><body style="font-family:sans-serif;padding:48px;text-align:center;">
<h3>❌ 抖音授权失败</h3><p style="color:#c33">${String(e.message).replace(/</g, '&lt;')}</p>
<script>
try { if (window.opener) { window.opener.postMessage(${JSON.stringify({ type: 'douyin-oauth', ok: false })}, '*'); } } catch(e){}
</script></body></html>`);
  }
});

app.post('/douyin/publish', async (req, res) => {
  try {
    const { accessToken, openId, videoUrl, text } = req.body || {};
    if (!accessToken || !openId) throw new Error('未授权（缺少 accessToken/openId），请先完成抖音授权');
    if (!videoUrl) throw new Error('videoUrl 为空');
    if (!DOUYIN_CLIENT_KEY) throw new Error('未配置 DOUYIN_CLIENT_KEY');

    const vidResp = await fetchRetry(videoUrl);
    if (!vidResp.ok) throw new Error(`视频下载失败 ${vidResp.status}`);
    const vidBuf = Buffer.from(await vidResp.arrayBuffer());

    const form = new FormData();
    form.append('video', new Blob([vidBuf], { type: 'video/mp4' }), 'video.mp4');
    const upResp = await fetchRetry(`${DOUYIN_UPLOAD_VIDEO}?open_id=${encodeURIComponent(openId)}`, {
      method: 'POST',
      headers: { 'access-token': accessToken },
      body: form,
    });
    const upJson = await upResp.json().catch(() => ({}));
    const videoId = upJson?.data?.video_id;
    if (!upResp.ok || !videoId) throw new Error(`上传失败：${JSON.stringify(upJson).slice(0, 300)}`);

    const cvResp = await fetchRetry(`${DOUYIN_CREATE_VIDEO}?open_id=${encodeURIComponent(openId)}`, {
      method: 'POST',
      headers: { 'access-token': accessToken, 'Content-Type': 'application/json' },
      body: JSON.stringify({ video_id: videoId, text: (text || '').slice(0, 55) }),
    });
    const cvJson = await cvResp.json().catch(() => ({}));
    const itemId = cvJson?.data?.item_id;
    if (!cvResp.ok || !itemId) throw new Error(`发布失败：${JSON.stringify(cvJson).slice(0, 300)}`);

    res.json({ ok: true, videoId, itemId });
  } catch (e) {
    console.error('[douyin/publish]', e.message);
    res.status(500).json({ ok: false, error: e.message });
  }
});

// ── 启动 ───────────────────────────────────────────────────────────
const PORT = Number(process.env.SIDECAR_PORT || process.env.PORT || 8081);
app.listen(PORT, '127.0.0.1', () => {
  console.log(`[sidecar] listening on 127.0.0.1:${PORT}`);
  console.log('[sidecar] 路由：/oss/upload /tts/* /video/* /vproxy /jimeng /douyin/*');
});
