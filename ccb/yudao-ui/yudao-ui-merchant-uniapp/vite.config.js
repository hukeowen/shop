import { defineConfig, loadEnv } from 'vite';
import uni from '@dcloudio/vite-plugin-uni';
import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { ProxyAgent, setGlobalDispatcher } from 'undici';
import ffmpegPath from 'ffmpeg-static';
import QRCode from 'qrcode';

// 让 Node 的 fetch 尊重 HTTP(S)_PROXY（本机走 Clash，不然 oss/火山都连不上）
const proxy = process.env.HTTPS_PROXY || process.env.HTTP_PROXY;
if (proxy) {
  setGlobalDispatcher(new ProxyAgent(proxy));
  console.log('[proxy] fetch via', proxy);
} else {
  console.warn(
    '[proxy] ⚠️ 未设置 HTTPS_PROXY / HTTP_PROXY。本机若靠 Clash 上网，OSS / 火山等外网调用大概率会 fetch failed。' +
      ' 正确启动示例：HTTPS_PROXY=http://127.0.0.1:7897 pnpm dev'
  );
}

// 兜底：不要因为一次上游 TLS 被 reset 就 crash 整个 vite server
process.on('unhandledRejection', (e) => console.warn('[unhandledRejection]', e?.message || e));
process.on('uncaughtException', (e) => console.warn('[uncaughtException]', e?.message || e));

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');

  // ── 火山 TOS S3 兼容（AWS Sig V4，与即梦AI 同一套 AK/SK）
  const TOS_AK = env.VITE_JIMENG_AK;
  const TOS_SK = env.VITE_JIMENG_SK;
  const TOS_REGION = env.TOS_REGION || 'cn-beijing';
  const TOS_BUCKET = env.TOS_BUCKET || 'tanxiaoer';
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
      'host': TOS_HOST,
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

  // 带重试的 fetch（Clash 代理偶发 ECONNRESET）
  async function fetchRetry(url, init = {}, tries = 4) {
    let lastErr;
    for (let i = 0; i < tries; i++) {
      try {
        return await fetch(url, init);
      } catch (e) {
        lastErr = e;
        const msg = e?.cause?.code || e?.message || String(e);
        console.warn(`[fetch] ${url} 第 ${i + 1}/${tries} 次失败：${msg}`);
        await new Promise((r) => setTimeout(r, 300 * (i + 1)));
      }
    }
    throw lastErr;
  }

  function readJsonBody(req) {
    return new Promise((resolve, reject) => {
      const chunks = [];
      req.on('data', (c) => chunks.push(c));
      req.on('end', () => {
        try {
          const txt = Buffer.concat(chunks).toString('utf8');
          resolve(txt ? JSON.parse(txt) : {});
        } catch (e) {
          reject(e);
        }
      });
      req.on('error', reject);
    });
  }

  function sendJson(res, status, obj) {
    res.statusCode = status;
    res.setHeader('Content-Type', 'application/json');
    res.end(JSON.stringify(obj));
  }

  async function handleOssUpload(req, res) {
    try {
      const { base64, ext = 'jpg', prefix = 'tanxiaoer' } = await readJsonBody(req);
      if (!base64) throw new Error('base64 为空');
      const clean = base64.replace(/^data:image\/[a-z]+;base64,/, '');
      const buf = Buffer.from(clean, 'base64');
      const key = `${prefix}/${Date.now()}-${Math.random().toString(36).slice(2, 8)}.${ext}`;
      const contentType = ext === 'png' ? 'image/png' : 'image/jpeg';
      const { datetime, contentLen, bodyHash, authorization } = tosSign({
        method: 'PUT',
        keyPath: key,
        buf,
        contentType,
        acl: 'public-read',
      });
      const putUrl = `${TOS_BASE}/${key}`;
      const r = await fetchRetry(putUrl, {
        method: 'PUT',
        headers: {
          Authorization: authorization,
          'X-Amz-Date': datetime,
          'X-Amz-Content-Sha256': bodyHash,
          'X-Amz-Acl': 'public-read',
          'Content-Type': contentType,
          'Content-Length': contentLen,
        },
        body: buf,
      });
      if (!r.ok) {
        const errTxt = await r.text();
        throw new Error(`TOS put ${r.status}: ${errTxt.slice(0, 300)}`);
      }
      const url = `${TOS_BASE}/${key}`;
      sendJson(res, 200, { ok: true, url, key });
    } catch (e) {
      console.error('[tos/upload]', e);
      sendJson(res, 500, { ok: false, error: e.message });
    }
  }

  // 火山 openspeech v3 单向流式（豆包语音合成大模型）—— 提取为可复用 buffer helper
  async function getTtsBuffer(text, voice) {
    const apiKey = env.VITE_TTS_ACCESS_TOKEN;
    const resourceId = env.VITE_TTS_RESOURCE_ID || 'volc.service_type.10029';
    if (!apiKey) throw new Error('未配置 VITE_TTS_ACCESS_TOKEN');
    const speaker = voice || env.VITE_TTS_VOICE || 'zh_male_beijingxiaoye_emo_v2_mars_bigtts';
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

  async function handleTtsVolc(req, res) {
    try {
      const { text, voice } = await readJsonBody(req);
      if (!text) throw new Error('text 为空');
      const buf = await getTtsBuffer(text, voice);
      res.setHeader('Content-Type', 'audio/mpeg');
      res.setHeader('X-Tts-Source', 'volc');
      res.end(buf);
    } catch (e) {
      console.warn('[tts/volc]', e.message);
      sendJson(res, 500, { error: e.message });
    }
  }

  async function handleTtsEdge(req, res) {
    try {
      const { text, voice = 'zh-CN-XiaoxiaoNeural' } = await readJsonBody(req);
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
      sendJson(res, 500, { error: e.message });
    }
  }

  // 在线合并视频：拿 N 个 mp4 URL，下载 → ffmpeg concat demuxer → 返回一个单文件 mp4
  async function handleVideoMerge(req, res) {
    let workDir;
    try {
      const { urls = [] } = await readJsonBody(req);
      if (!Array.isArray(urls) || !urls.length) throw new Error('urls 为空');
      if (!ffmpegPath) throw new Error('ffmpeg-static 未提供二进制路径');

      workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'tanxiaoer-merge-'));
      // 并行下载所有片段
      const localFiles = await Promise.all(
        urls.map(async (url, i) => {
          const resp = await fetchRetry(url, { method: 'GET' });
          if (!resp.ok) throw new Error(`片段 ${i + 1} 下载失败 ${resp.status}`);
          const buf = Buffer.from(await resp.arrayBuffer());
          const f = path.join(workDir, `seg-${String(i).padStart(2, '0')}.mp4`);
          fs.writeFileSync(f, buf);
          return f;
        })
      );
      console.log(`[merge] 下载 ${localFiles.length} 个片段，总 ${(localFiles.reduce((s, f) => s + fs.statSync(f).size, 0) / 1024 / 1024).toFixed(1)} MB`);

      // concat demuxer 需要一个列表文件，每行 `file 'xxx.mp4'`
      const listFile = path.join(workDir, 'list.txt');
      fs.writeFileSync(
        listFile,
        localFiles.map((f) => `file '${f.replace(/'/g, `'\\''`)}'`).join('\n')
      );

      const outFile = path.join(workDir, 'merged.mp4');

      // 探测每个片段是否有音轨（ffmpeg -i 读元数据，不处理）
      const audioPresence = await Promise.all(localFiles.map((f) =>
        new Promise((resolve) => {
          const p = spawn(ffmpegPath, ['-i', f, '-hide_banner'], { stdio: ['ignore', 'ignore', 'pipe'] });
          let out = '';
          p.stderr.on('data', (c) => { out += c.toString(); });
          p.on('close', () => resolve(/Stream.*?Audio/i.test(out)));
        })
      ));
      console.log('[merge] 音轨检测:', audioPresence);
      const hasAnyAudio = audioPresence.some(Boolean);

      // 动态构建 filter_complex：
      //   · 有音轨 → [i:v:0][i:a:0]
      //   · 无音轨 → [i:v:0] + 额外的 anullsrc 输入提供静音
      const extraLavfiArgs = [];
      const audioSources = audioPresence.map((has, i) => {
        if (has) return `[${i}:a:0]`;
        const idx = localFiles.length + extraLavfiArgs.length / 4;
        extraLavfiArgs.push('-f', 'lavfi', '-i', 'anullsrc=r=44100:cl=stereo');
        return `[${idx}:a]`;
      });

      // concat filter 要求所有输入的 w/h/SAR/fps/pix_fmt 完全一致。
      // 即梦AI 有时输出 704x1248 / SAR 1920:1919，端卡是 720x1280 / SAR 1:1，
      // 直接 concat 会报「parameters do not match」。统一规范化到 720x1280 / SAR 1:1 / 25fps。
      const CANON = { w: 720, h: 1280, fps: 25, sar: 1 };
      const normalizeVideo = (i) =>
        `[${i}:v:0]scale=${CANON.w}:${CANON.h}:force_original_aspect_ratio=decrease,` +
        `pad=${CANON.w}:${CANON.h}:(ow-iw)/2:(oh-ih)/2:color=black,` +
        `setsar=${CANON.sar},fps=${CANON.fps},format=yuv420p[v${i}]`;
      const normalizeAudio = (src, i) => `${src}aresample=44100,asetpts=PTS-STARTPTS[a${i}]`;

      const normChains = localFiles.map((_, i) => normalizeVideo(i));
      const normAudioChains = hasAnyAudio
        ? localFiles.map((_, i) => normalizeAudio(audioSources[i], i))
        : [];
      const concatInputs = localFiles.map((_, i) => hasAnyAudio ? `[v${i}][a${i}]` : `[v${i}]`).join('');
      const filterComplex = hasAnyAudio
        ? `${[...normChains, ...normAudioChains].join(';')};${concatInputs}concat=n=${localFiles.length}:v=1:a=1[v][a]`
        : `${normChains.join(';')};${concatInputs}concat=n=${localFiles.length}:v=1:a=0[v]`;

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

      await new Promise((resolve, reject) => {
        const proc = spawn(ffmpegPath, mergeArgs, { stdio: ['ignore', 'ignore', 'pipe'] });
        let errOut = '';
        proc.stderr.on('data', (c) => { errOut += c.toString(); });
        proc.on('close', (code) => {
          if (code === 0) resolve();
          else reject(new Error(`ffmpeg exit ${code}: ${errOut.slice(-500)}`));
        });
        proc.on('error', reject);
      });

      const mp4 = fs.readFileSync(outFile);
      console.log(`[merge] 完成，输出 ${(mp4.length / 1024 / 1024).toFixed(1)} MB`);
      res.setHeader('Content-Type', 'video/mp4');
      res.setHeader('Content-Disposition', 'attachment; filename="merged.mp4"');
      res.setHeader('Content-Length', String(mp4.length));
      res.end(mp4);
    } catch (e) {
      console.warn('[merge]', e.message);
      sendJson(res, 500, { error: e.message });
    } finally {
      if (workDir) {
        try { fs.rmSync(workDir, { recursive: true, force: true }); } catch {}
      }
    }
  }

  // 视频反向代理：把火山 tos url 透传出来并加 CORS 头，供浏览器 canvas 抽帧
  async function handleVideoProxy(req, res) {
    try {
      const full = new URL(req.url, 'http://x');
      const target = full.searchParams.get('url');
      if (!target) throw new Error('missing url');
      const upstream = await fetchRetry(target, { method: 'GET' });
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
      res.statusCode = 502;
      res.end(e.message);
    }
  }

  // ── 即梦AI 签名代理（签名在 Node.js 端，避免浏览器 Secure Context 限制）
  const JIMENG_AK = env.VITE_JIMENG_AK;
  const JIMENG_SK = env.VITE_JIMENG_SK;
  const JIMENG_REGION = 'cn-north-1';
  const JIMENG_SERVICE = 'cv';
  const JIMENG_VERSION = '2022-08-31';

  function jimengSign(action, bodyStr) {
    const now = new Date();
    const pad = (n) => String(n).padStart(2, '0');
    const xDate = `${now.getUTCFullYear()}${pad(now.getUTCMonth()+1)}${pad(now.getUTCDate())}T${pad(now.getUTCHours())}${pad(now.getUTCMinutes())}${pad(now.getUTCSeconds())}Z`;
    const shortDate = xDate.slice(0, 8);
    const signedHeaders = 'content-type;x-date';
    const credential = `${shortDate}/${JIMENG_REGION}/${JIMENG_SERVICE}/request`;
    const bodyHash = crypto.createHash('sha256').update(bodyStr).digest('hex');
    const canonReq = ['POST','/',`Action=${action}&Version=${JIMENG_VERSION}`,'content-type:application/json',`x-date:${xDate}`,'',signedHeaders,bodyHash].join('\n');
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

  async function handleJimeng(req, res) {
    try {
      const qs = new URL(req.url, 'http://x').searchParams;
      const action = qs.get('action');
      if (!action) throw new Error('missing action param');
      const body = await readJsonBody(req);
      const bodyStr = JSON.stringify(body);
      const headers = jimengSign(action, bodyStr);
      const targetUrl = `https://visual.volcengineapi.com?Action=${action}&Version=${JIMENG_VERSION}`;
      const r = await fetchRetry(targetUrl, { method: 'POST', headers, body: bodyStr });
      const text = await r.text();
      res.statusCode = r.status;
      res.setHeader('Content-Type', 'application/json');
      res.end(text);
    } catch (e) {
      console.error('[jimeng]', e.message);
      sendJson(res, 500, { code: -1, message: e.message });
    }
  }

  // 查找系统中文字体（libass 需要字体文件路径）
  function findChineseFont() {
    const candidates = [
      { path: '/System/Library/Fonts/PingFang.ttc',              name: 'PingFang SC' },
      { path: '/System/Library/Fonts/STHeiti Light.ttc',         name: 'STHeiti' },
      { path: '/Library/Fonts/Arial Unicode.ttf',                name: 'Arial Unicode MS' },
      { path: '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc', name: 'Noto Sans CJK SC' },
      { path: '/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc',     name: 'Noto Sans CJK SC' },
      { path: '/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc',          name: 'WenQuanYi Zen Hei' },
    ];
    for (const f of candidates) {
      if (fs.existsSync(f.path)) return f;
    }
    return null;
  }

  // 生成 ASS 字幕文件内容（抖音大字花字风格）
  function buildAss(text, durationSec, fontName) {
    // ASS 时间格式 h:mm:ss.cc
    const fmtTime = (s) => {
      const h = Math.floor(s / 3600);
      const m = Math.floor((s % 3600) / 60);
      const sc = (s % 60).toFixed(2).padStart(5, '0');
      return `${h}:${String(m).padStart(2, '0')}:${sc}`;
    };
    // 长文本自动折行（>12字换行，避免单行溢出画面）
    const wrapText = (t) => {
      if (t.length <= 13) return t;
      const mid = Math.ceil(t.length / 2);
      const breakAt = [
        t.indexOf('，', mid - 5),
        t.indexOf('。', mid - 5),
        t.indexOf('、', mid - 5),
        t.indexOf(' ',  mid - 5),
        mid,
      ].find((i) => i > 0) ?? mid;
      return t.slice(0, breakAt) + '\\N' + t.slice(breakAt);
    };
    const endSec = Math.max(durationSec - 0.3, durationSec * 0.92);
    const safeText = wrapText(text).replace(/:/g, '：'); // 避免 ASS 解析器歧义
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

  // 视频+配音+字幕+二维码合流：下载视频 + TTS → 生成 QR → FFmpeg mux + ASS 字幕 + QR overlay → 上传 TOS
  async function handleVideoMux(req, res) {
    let workDir;
    try {
      const { videoUrl, text, voice, duration = 10, qrUrl } = await readJsonBody(req);
      if (!videoUrl) throw new Error('videoUrl 为空');
      if (!text) throw new Error('text 为空');
      if (!ffmpegPath) throw new Error('ffmpeg-static 未提供二进制路径');

      workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'tanxiaoer-mux-'));

      // 并行：TTS 生成 + 视频下载
      const [audioBuf, vidResp] = await Promise.all([
        getTtsBuffer(text, voice),
        fetchRetry(videoUrl),
      ]);
      if (!vidResp.ok) throw new Error(`视频下载失败 ${vidResp.status}`);
      const vidBuf = Buffer.from(await vidResp.arrayBuffer());

      const vidFile = path.join(workDir, 'input.mp4');
      const audFile = path.join(workDir, 'audio.mp3');
      const assFile = path.join(workDir, 'sub.ass');
      const qrFile = path.join(workDir, 'qr.png');
      const outFile = path.join(workDir, 'output.mp4');
      fs.writeFileSync(vidFile, vidBuf);
      fs.writeFileSync(audFile, audioBuf);

      // 二维码：传入 qrUrl 优先；否则生成随机占位 URL（TODO: 后面接真实小程序码）
      const finalQrUrl = qrUrl || `https://tanxiaoer.example/qr/${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`;
      // 白底 + 4 模块 quiet zone，300px 基准大小（后面按视频尺寸缩放到合适比例）
      await QRCode.toFile(qrFile, finalQrUrl, {
        errorCorrectionLevel: 'M',
        margin: 2,
        width: 300,
        color: { dark: '#000000', light: '#FFFFFFFF' },
      });

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

      // 注意：ffmpeg-static 里 `-shortest` 与 `-vf subtitles` 组合会使 aac 编码器收不到样本（Qavg: nan），音频流会被整条丢掉。
      // 用 `-t ${duration}` 显式限长代替 `-shortest`，并补 `-apad` 避免音频短于视频时出现 EOF 抖动。
      const durSec = Math.max(1, Number(duration) || 10);

      // QR 相对视频尺寸缩放（短边 26% 方形）+ 右下角 padding 4% W。
      // scale 滤镜不支持 main_w/main_h，必须用 scale2ref：把 QR 相对视频缩放；overlay 再放到右下角。
      const qrScale2ref = `[2:v][0:v:0]scale2ref=w='min(main_w\\,main_h)*0.26':h=ow[qr][vref]`;
      const overlayPos = `x='W-w-W*0.04':y='H-h-H*0.04'`;

      // 无字幕路径：仅叠加二维码（需要重编码）
      const audioOnlyArgs = [
        '-y', '-i', vidFile, '-i', audFile, '-i', qrFile,
        '-filter_complex', `${qrScale2ref};[vref][qr]overlay=${overlayPos}[v]`,
        '-map', '[v]', '-map', '1:a:0',
        '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '22',
        '-c:a', 'aac', '-af', 'apad',
        '-t', String(durSec),
        '-movflags', '+faststart',
        outFile,
      ];

      // 查字体 → 生成 ASS → 字幕+二维码 一起烧录；失败则降级为"只二维码"
      const font = findChineseFont();
      if (font) {
        fs.writeFileSync(assFile, buildAss(text, durSec, font.name), 'utf8');
        const fontDir = path.dirname(font.path).replace(/\\/g, '/').replace(/:/g, '\\:');
        const assPath = assFile.replace(/\\/g, '/').replace(/:/g, '\\:');
        const subtitleArgs = [
          '-y', '-i', vidFile, '-i', audFile, '-i', qrFile,
          '-filter_complex',
            `${qrScale2ref};` +
            `[vref]subtitles=${assPath}:fontsdir=${fontDir}[vs];` +
            `[vs][qr]overlay=${overlayPos}[v]`,
          '-map', '[v]', '-map', '1:a:0',
          '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '22',
          '-c:a', 'aac', '-af', 'apad',
          '-t', String(durSec),
          '-movflags', '+faststart',
          outFile,
        ];
        console.log(`[mux] 字幕+二维码模式，字体: ${font.name}，qr=${finalQrUrl}`);
        try {
          await runFfmpeg(subtitleArgs);
        } catch (subErr) {
          console.warn(`[mux] 字幕渲染失败，降级为仅二维码: ${subErr.message.slice(0, 200)}`);
          if (fs.existsSync(outFile)) fs.unlinkSync(outFile);
          await runFfmpeg(audioOnlyArgs);
        }
      } else {
        console.warn(`[mux] 未找到中文字体，降级为仅二维码，qr=${finalQrUrl}`);
        await runFfmpeg(audioOnlyArgs);
      }

      const outBuf = fs.readFileSync(outFile);
      const key = `tanxiaoer/muxed/${Date.now()}-${Math.random().toString(36).slice(2, 8)}.mp4`;
      const { datetime, contentLen, bodyHash, authorization } = tosSign({
        method: 'PUT', keyPath: key, buf: outBuf, contentType: 'video/mp4', acl: 'public-read',
      });
      const r = await fetchRetry(`${TOS_BASE}/${key}`, {
        method: 'PUT',
        headers: {
          Authorization: authorization,
          'X-Amz-Date': datetime,
          'X-Amz-Content-Sha256': bodyHash,
          'X-Amz-Acl': 'public-read',
          'Content-Type': 'video/mp4',
          'Content-Length': contentLen,
        },
        body: outBuf,
      });
      if (!r.ok) {
        const errTxt = await r.text();
        throw new Error(`TOS put ${r.status}: ${errTxt.slice(0, 300)}`);
      }
      console.log(`[mux] 完成 ${(outBuf.length / 1024 / 1024).toFixed(1)} MB → ${key}`);
      sendJson(res, 200, { ok: true, url: `${TOS_BASE}/${key}` });
    } catch (e) {
      console.error('[video/mux]', e.message);
      sendJson(res, 500, { ok: false, error: e.message });
    } finally {
      if (workDir) {
        try { fs.rmSync(workDir, { recursive: true, force: true }); } catch {}
      }
    }
  }

  // 端卡字幕：QR 上方一行说明「微信扫描二维码在线下单」+ 店名居中偏下（有名字才写）
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

  // 端卡：用商品图做底 + 半透黑遮罩 + 正中大二维码 + 店名 + TTS → 上传 TOS
  async function handleVideoEndcard(req, res) {
    let workDir;
    try {
      const { imageUrl, shopName = '', text, voice, duration = 3, qrUrl } = await readJsonBody(req);
      if (!imageUrl) throw new Error('imageUrl 为空');
      if (!text) throw new Error('text 为空');
      if (!ffmpegPath) throw new Error('ffmpeg-static 未提供二进制路径');

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

      // 大二维码（白底 + 2 模块 quiet zone，800px 基准）
      const finalQrUrl = qrUrl || `https://tanxiaoer.example/qr/${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`;
      await QRCode.toFile(qrFile, finalQrUrl, {
        errorCorrectionLevel: 'M',
        margin: 2,
        width: 800,
        color: { dark: '#000000', light: '#FFFFFFFF' },
      });

      const durSec = Math.max(1, Number(duration) || 3);
      const font = findChineseFont();

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

      // 背景：商品图填满 720x1280 竖屏，轻微模糊 + 45% 黑色遮罩
      // 前景：QR 居中偏上（短边 50%），店名在 QR 下方
      const bgChain =
        `[0:v]scale=720:1280:force_original_aspect_ratio=increase,` +
        `crop=720:1280,boxblur=10:1,` +
        `drawbox=x=0:y=0:w=iw:h=ih:color=black@0.45:t=fill[bg]`;
      const qrChain =
        `[2:v][bg]scale2ref=w='min(main_w\\,main_h)*0.50':h=ow[qr][vbg];` +
        `[vbg][qr]overlay=(W-w)/2:(H-h)/2-H*0.05[v_qr]`;

      let filterComplex;
      if (font) {
        // 即使没店名也要烧录「微信扫描二维码在线下单」这行指引
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
      const { datetime, contentLen, bodyHash, authorization } = tosSign({
        method: 'PUT', keyPath: key, buf: outBuf, contentType: 'video/mp4', acl: 'public-read',
      });
      const r = await fetchRetry(`${TOS_BASE}/${key}`, {
        method: 'PUT',
        headers: {
          Authorization: authorization,
          'X-Amz-Date': datetime,
          'X-Amz-Content-Sha256': bodyHash,
          'X-Amz-Acl': 'public-read',
          'Content-Type': 'video/mp4',
          'Content-Length': contentLen,
        },
        body: outBuf,
      });
      if (!r.ok) {
        const errTxt = await r.text();
        throw new Error(`TOS put ${r.status}: ${errTxt.slice(0, 300)}`);
      }
      console.log(`[endcard] 完成 ${(outBuf.length / 1024 / 1024).toFixed(1)} MB → ${key}`);
      sendJson(res, 200, { ok: true, url: `${TOS_BASE}/${key}` });
    } catch (e) {
      console.error('[video/endcard]', e.message);
      sendJson(res, 500, { ok: false, error: e.message });
    } finally {
      if (workDir) {
        try { fs.rmSync(workDir, { recursive: true, force: true }); } catch {}
      }
    }
  }

  // Sidecar 插件：OSS 上传 + TTS 代理 + 即梦AI 签名代理 + 视频帧代理 + 视频合流
  const sidecar = {
    name: 'tanxiaoer-sidecar',
    configureServer(server) {
      server.middlewares.use(async (req, res, next) => {
        const url = (req.url || '').split('?')[0];
        if (req.method === 'GET' && url === '/vproxy') return handleVideoProxy(req, res);
        if (req.method !== 'POST') return next();
        if (url === '/oss/upload') return handleOssUpload(req, res);
        if (url === '/tts/volc') return handleTtsVolc(req, res);
        if (url === '/tts/edge') return handleTtsEdge(req, res);
        if (url === '/video/merge') return handleVideoMerge(req, res);
        if (url === '/video/mux') return handleVideoMux(req, res);
        if (url === '/video/endcard') return handleVideoEndcard(req, res);
        if (url === '/jimeng') return handleJimeng(req, res);
        next();
      });
      console.log('[sidecar] /oss/upload /tts/volc /tts/edge /vproxy /video/merge /video/mux /video/endcard /jimeng 已挂载');
    },
  };

  return {
    plugins: [uni(), sidecar],
    server: {
      host: true,
      port: 5180,
      proxy: {
        // 火山方舟（豆包 LLM + Seedance 1.5 Pro，key 由浏览器直接带）
        '/ark': {
          target: 'https://ark.cn-beijing.volces.com',
          changeOrigin: true,
          rewrite: (p) => p.replace(/^\/ark/, ''),
        },
        // 即梦AI 视频生成
        '/visual': {
          target: 'https://visual.volcengineapi.com',
          changeOrigin: true,
          rewrite: (p) => p.replace(/^\/visual/, ''),
        },
        // 业务后端
        '/admin-api': {
          target: 'http://localhost:48080',
          changeOrigin: true,
        },
      },
    },
  };
});
