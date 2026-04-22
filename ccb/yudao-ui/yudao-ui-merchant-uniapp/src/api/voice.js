/**
 * 配音 · 三级降级：
 *   1. VITE_TTS_PROVIDER=ark    → 火山方舟 /api/v3/audio/speech（豆包-seed-tts）
 *   2. 失败/未配置 → Edge TTS （微软免费，侧车代理）
 *   3. 还失败 → 浏览器 Web Speech API
 *
 * 音色映射：前端给"业务名"（灿灿/爽快思思/...），自动映射到两边的音色 ID。
 */

const PROVIDER = import.meta.env.VITE_TTS_PROVIDER || 'volc';
const DEFAULT_VOICE = import.meta.env.VITE_TTS_VOICE || 'zh_female_cancan_mars_bigtts';

export const VOICES = [
  {
    key: 'cancan',
    name: '灿灿',
    desc: '年轻活力女 · 带货感最强',
    ark: 'zh_female_cancan_mars_bigtts',
    edge: 'zh-CN-XiaoxiaoNeural',
  },
  {
    key: 'shuangkuaisisi',
    name: '爽快思思',
    desc: '利落大姐 · 东北味',
    ark: 'zh_female_shuangkuaisisi_moon_bigtts',
    edge: 'zh-CN-liaoning-XiaobeiNeural',
  },
  {
    key: 'yangguangqingnian',
    name: '阳光青年',
    desc: '阳光男声 · 活力',
    ark: 'zh_male_yangguangqingnian_mars_bigtts',
    edge: 'zh-CN-YunxiNeural',
  },
  {
    key: 'wanwanxiaohe',
    name: '湾湾小何',
    desc: '温柔女声 · 治愈',
    ark: 'zh_female_wanwanxiaohe_moon_bigtts',
    edge: 'zh-CN-XiaoyiNeural',
  },
  {
    key: 'beijingxiaoye',
    name: '北京小爷',
    desc: '京腔痞帅男声',
    ark: 'zh_male_beijingxiaoye_emo_v2_mars_bigtts',
    edge: 'zh-CN-YunjianNeural',
  },
  {
    key: 'shenyeboke',
    name: '深夜播客',
    desc: '浑厚男中音 · 广告大片',
    ark: 'zh_male_shenyeboke_moon_bigtts',
    edge: 'zh-CN-YunjianNeural',
  },
];

export function findVoice(key) {
  return VOICES.find((v) => v.key === key) || VOICES[0];
}

/**
 * 为一段文案生成 mp3（blob URL）
 * 按 provider 优先级尝试，全失败抛异常
 */
export async function synthMp3(text, voiceKey) {
  const v = findVoice(voiceKey);
  const errors = [];

  // 1. 火山 openspeech（豆包语音合成大模型）
  if (PROVIDER === 'volc') {
    try {
      return await fetchMp3('/tts/volc', { text, voice: v.ark });
    } catch (e) {
      errors.push('volc: ' + e.message);
      console.warn('[voice] 火山 TTS 失败，降级 edge:', e.message);
    }
  }

  // 2. Edge TTS 兜底
  try {
    return await fetchMp3('/tts/edge', { text, voice: v.edge });
  } catch (e) {
    errors.push('edge: ' + e.message);
  }

  throw new Error('TTS 全部失败：' + errors.join(' | '));
}

async function fetchMp3(endpoint, body) {
  const res = await fetch(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    let msg = `HTTP ${res.status}`;
    try {
      const j = await res.json();
      msg = j.error || msg;
    } catch {
      // binary but not ok
    }
    throw new Error(msg);
  }
  const buf = await res.arrayBuffer();
  const blob = new Blob([buf], { type: 'audio/mpeg' });
  return {
    url: URL.createObjectURL(blob),
    source: res.headers.get('X-Tts-Source') || 'unknown',
  };
}

/** 浏览器朗读兜底（仅 H5） */
export function createBrowserSpeaker(text) {
  // #ifdef H5
  if (typeof window === 'undefined' || !('speechSynthesis' in window)) return null;
  const synth = window.speechSynthesis;
  let utter = null;
  function build() {
    utter = new SpeechSynthesisUtterance(text);
    utter.lang = 'zh-CN';
    utter.rate = 1.0;
    const voices = synth.getVoices();
    const zh = voices.find((v) => /zh/i.test(v.lang));
    if (zh) utter.voice = zh;
  }
  return {
    play() {
      if (synth.paused && utter) synth.resume();
      else {
        synth.cancel();
        build();
        synth.speak(utter);
      }
    },
    pause() {
      synth.pause();
    },
    stop() {
      synth.cancel();
      utter = null;
    },
  };
  // #endif
  // #ifndef H5
  return null;
  // #endif
}
