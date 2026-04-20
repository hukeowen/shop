import { mockDelay } from './request.js';

/**
 * status:
 *  1 文案生成中
 *  2 等待确认
 *  3 视频合成中
 *  4 完成
 *  5 失败
 */
const store = {
  nextId: 1005,
  quota: { total: 10, used: 3 },
  tasks: [
    {
      id: 1001,
      status: 4,
      imageUrls: [
        'https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=400',
        'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=400',
        'https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=400',
      ],
      userDescription: '手工烤地瓜，现烤现卖，软糯香甜',
      aiCopywriting: [
        '街角那抹香气，是童年的味道',
        '铁皮桶里，火候到了',
        '掰开流心，糖浆挂壁',
        '5 块钱一个，温暖一整天',
      ],
      finalCopywriting: [
        '街角那抹香气，是童年的味道',
        '铁皮桶里，火候到了',
        '掰开流心，糖浆挂壁',
        '5 块钱一个，温暖一整天',
      ],
      videoUrl: 'https://media.w3.org/2010/05/sintel/trailer.mp4',
      coverUrl: 'https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=400',
      createdAt: '2026-04-19 20:15',
      publishedToDouyin: true,
    },
    {
      id: 1002,
      status: 2,
      imageUrls: [
        'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=400',
        'https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=400',
      ],
      userDescription: '春季限定甜玉米',
      aiCopywriting: [
        '春天第一口甜',
        '清晨摘下的嫩玉米',
        '咬下去爆浆',
        '现在下单，10 分钟送达',
      ],
      finalCopywriting: null,
      videoUrl: null,
      coverUrl: null,
      createdAt: '2026-04-20 11:30',
      publishedToDouyin: false,
    },
    {
      id: 1003,
      status: 5,
      imageUrls: ['https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=400'],
      userDescription: '夜市收摊前大甩卖',
      aiCopywriting: null,
      finalCopywriting: null,
      videoUrl: null,
      coverUrl: null,
      failReason: 'Seedance 生成失败：图片审核未通过',
      createdAt: '2026-04-18 22:01',
      publishedToDouyin: false,
    },
  ],
};

// 创建任务
export function createTask({ imageUrls, userDescription }) {
  const id = store.nextId++;
  const task = {
    id,
    status: 1,
    imageUrls,
    userDescription,
    aiCopywriting: null,
    finalCopywriting: null,
    videoUrl: null,
    coverUrl: null,
    createdAt: new Date().toLocaleString('zh-CN'),
    publishedToDouyin: false,
  };
  store.tasks.unshift(task);
  // 模拟 3 秒后 LLM 返回文案
  setTimeout(() => {
    task.status = 2;
    task.aiCopywriting = [
      '烟火气，藏在街角',
      '现做现卖，热乎得烫手',
      '一口咬下去，香到跺脚',
      '扫码下单，5 块钱安排',
    ];
  }, 3000);
  return mockDelay(id);
}

// 查询任务
export function getTask(id) {
  const t = store.tasks.find((x) => x.id === id);
  return mockDelay(t ? { ...t } : null);
}

// 确认文案
export function confirmTask({ taskId, finalCopywriting, bgmId }) {
  const t = store.tasks.find((x) => x.id === taskId);
  if (!t) return mockDelay(false);
  t.finalCopywriting = finalCopywriting || t.aiCopywriting;
  t.bgmId = bgmId;
  t.status = 3;
  // 模拟 6 秒后 Seedance 完成
  setTimeout(() => {
    t.status = 4;
    t.videoUrl = 'https://media.w3.org/2010/05/sintel/trailer.mp4';
    t.coverUrl = t.imageUrls[0];
    store.quota.used += 1;
  }, 6000);
  return mockDelay(true);
}

// 历史列表
export function getTaskPage() {
  return mockDelay({ total: store.tasks.length, list: [...store.tasks] });
}

// 配额
export function getQuota() {
  return mockDelay({ ...store.quota });
}

// 购买配额（占位 — 后端尚未接支付）
export function buyQuota(count) {
  return mockDelay({ ok: false, msg: '配额购买功能正在对接支付系统，敬请期待' });
}

// 抖音授权 URL
export function getDouyinAuthUrl() {
  return mockDelay({ url: 'https://open.douyin.com/platform/oauth/connect?mock=1' });
}

// 发布到抖音
export function publishToDouyin(taskId) {
  const t = store.tasks.find((x) => x.id === taskId);
  if (t) t.publishedToDouyin = true;
  return mockDelay(true);
}
