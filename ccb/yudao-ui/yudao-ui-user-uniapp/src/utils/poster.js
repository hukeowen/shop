/**
 * 推广海报合成（H5 canvas 手绘，导出一张可保存的 PNG）。
 *
 * 设计要点：
 *  - 二维码用本地 qrcode 库生成 base64（不再依赖第三方 api.qrserver.com，
 *    邀请链接不外泄，且导出永远不会 taint canvas）
 *  - 商品图跨域：crossOrigin 加载 OSS 图；若服务器无 CORS 头导致 toDataURL
 *    抛 SecurityError，自动降级为「不含商品图」版本重绘，保证海报一定能导出
 *  - 纯 canvas，不用 html2canvas（规避 backdrop-filter / background-clip 渲染走样）
 *
 * 仅 H5 可用（依赖 document.createElement('canvas') + new Image()）。
 */
// 零运行时依赖的 QR 编码器（拿模块矩阵自己画格子，不引入 dijkstrajs/pngjs，
// 规避 Vite 把子依赖当 external 导致运行时 require 失败）
import qrcode from 'qrcode-generator';

/** 生成 QR 模块矩阵；失败返回 null（调用方兜底） */
function makeQrMatrix(text) {
  try {
    const qr = qrcode(0, 'M'); // typeNumber=0 自动选版本，纠错级 M
    qr.addData(String(text || ''));
    qr.make();
    return qr;
  } catch {
    return null;
  }
}

const W = 750;          // 逻辑宽度
const PAD = 40;         // 左右留白

/** 加载图片（带跨域），失败 / 无 url 时 resolve(null)，不抛错 */
function loadImage(url) {
  return new Promise((resolve) => {
    if (!url) return resolve(null);
    try {
      const img = new Image();
      img.crossOrigin = 'anonymous';
      img.onload = () => resolve(img);
      img.onerror = () => resolve(null);
      img.src = url;
    } catch {
      resolve(null);
    }
  });
}

/** canvas 文字自动换行；返回绘制后下一行的 y */
function wrapText(ctx, text, x, y, maxWidth, lineHeight) {
  const chars = String(text == null ? '' : text).split('');
  let line = '';
  for (const ch of chars) {
    const test = line + ch;
    if (ctx.measureText(test).width > maxWidth && line) {
      ctx.fillText(line, x, y);
      line = ch;
      y += lineHeight;
    } else {
      line = test;
    }
  }
  if (line) ctx.fillText(line, x, y);
  return y + lineHeight;
}

/** 圆角矩形路径 */
function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

/**
 * 生成邀请海报。
 * @param {object} o
 * @param {string} o.shopName
 * @param {string} o.inviteLink
 * @param {string} o.inviter        邀请人显示名
 * @param {string} [o.spuName]
 * @param {string} [o.spuPic]
 * @param {string} [o.priceYuan]    商品价（元，已格式化字符串）
 * @param {number} [o.n]            推 N
 * @param {string} [o.stepPoints]   每位约 X 积分（元值字符串）
 * @param {string} [o.totalPoints]  累计积分（元值字符串）
 * @returns {Promise<string>} dataURL（image/png）
 */
export async function buildInvitePoster(o) {
  // 仅 H5 有 DOM canvas；小程序 / APP 无 document，直接返回空（调用方兜底）
  if (typeof document === 'undefined') return '';

  // 本地生成 QR 模块矩阵（不外泄链接、无运行时依赖）
  const qr = makeQrMatrix(o.inviteLink);
  const spuImg = await loadImage(o.spuPic);

  const dpr = Math.min(3, Math.max(2, (typeof window !== 'undefined' && window.devicePixelRatio) || 2));
  const hasSpu = !!(o.spuName);
  const hasRule = !!(o.n);

  // ── 预估高度（自上而下累加各区块高度）──
  let H = 0;
  H += 130;                       // 顶部品牌 + 店名
  if (hasSpu) H += 230;           // 商品卡
  if (hasRule) H += 250;          // 规则卡
  H += 430;                       // 二维码区
  H += 120;                       // 底部署名 + 免责

  // ── 绘制函数（includeSpuImg=false 时降级不画商品图，规避 taint）──
  function draw(includeSpuImg) {
    const cv = document.createElement('canvas');
    cv.width = W * dpr;
    cv.height = H * dpr;
    const ctx = cv.getContext('2d');
    ctx.scale(dpr, dpr);

    // 背景渐变
    const bg = ctx.createLinearGradient(0, 0, W, H);
    bg.addColorStop(0, '#FFF9F0');
    bg.addColorStop(0.5, '#FFEFE0');
    bg.addColorStop(1, '#FFE4CC');
    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, W, H);

    ctx.textBaseline = 'top';
    let y = 44;

    // 品牌行
    ctx.fillStyle = '#C2410C';
    ctx.font = '700 22px -apple-system, "PingFang SC", sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText('客小二 · 商户营销优惠', W / 2, y);
    y += 36;

    // 店名
    ctx.fillStyle = '#1F1208';
    ctx.font = '900 40px -apple-system, "PingFang SC", sans-serif';
    ctx.fillText(o.shopName || '本店', W / 2, y);
    y += 64;

    ctx.textAlign = 'left';

    // 商品卡
    if (hasSpu) {
      const cardX = PAD, cardW = W - PAD * 2, cardH = 200;
      ctx.fillStyle = 'rgba(255,255,255,.9)';
      roundRect(ctx, cardX, y, cardW, cardH, 24);
      ctx.fill();

      const picX = cardX + 20, picY = y + 20, picS = 160;
      // 商品图（成功且允许时画，否则橙底 + 名称首字）
      if (includeSpuImg && spuImg) {
        ctx.save();
        roundRect(ctx, picX, picY, picS, picS, 18);
        ctx.clip();
        // aspectFill：按短边缩放铺满
        const r = Math.max(picS / spuImg.width, picS / spuImg.height);
        const dw = spuImg.width * r, dh = spuImg.height * r;
        ctx.drawImage(spuImg, picX + (picS - dw) / 2, picY + (picS - dh) / 2, dw, dh);
        ctx.restore();
      } else {
        const pg = ctx.createLinearGradient(picX, picY, picX + picS, picY + picS);
        pg.addColorStop(0, '#FFE0D1');
        pg.addColorStop(1, '#FF9A4A');
        ctx.fillStyle = pg;
        roundRect(ctx, picX, picY, picS, picS, 18);
        ctx.fill();
        ctx.fillStyle = '#fff';
        ctx.font = '900 64px -apple-system, "PingFang SC", sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText((o.spuName || '商').slice(0, 1), picX + picS / 2, picY + picS / 2 - 38);
        ctx.textAlign = 'left';
      }

      // 商品名 + 价 + 徽标
      const tx = picX + picS + 24;
      const tw = cardX + cardW - tx - 20;
      ctx.fillStyle = '#1F1208';
      ctx.font = '800 30px -apple-system, "PingFang SC", sans-serif';
      let ty = picY + 4;
      const nameChars = String(o.spuName || '').split('');
      // 商品名最多两行
      let line = '', lines = 0;
      for (const ch of nameChars) {
        if (ctx.measureText(line + ch).width > tw && line) {
          ctx.fillText(line, tx, ty); ty += 38; lines++;
          line = ch;
          if (lines >= 2) { line = ''; break; }
        } else line += ch;
      }
      if (line && lines < 2) { ctx.fillText(line, tx, ty); ty += 38; }

      if (o.priceYuan != null) {
        ctx.fillStyle = '#FF6B35';
        ctx.font = '900 38px -apple-system, sans-serif';
        ctx.fillText('¥' + o.priceYuan, tx, ty + 6);
      }
      if (hasRule) {
        const badge = '推 ' + o.n + ' 反 1';
        ctx.font = '800 22px -apple-system, "PingFang SC", sans-serif';
        const bw = ctx.measureText(badge).width + 28;
        const by = picY + picS - 40;
        const bgGrad = ctx.createLinearGradient(tx, by, tx + bw, by);
        bgGrad.addColorStop(0, '#FF6B35');
        bgGrad.addColorStop(1, '#C2410C');
        ctx.fillStyle = bgGrad;
        roundRect(ctx, tx, by, bw, 36, 18);
        ctx.fill();
        ctx.fillStyle = '#fff';
        ctx.fillText(badge, tx + 14, by + 7);
      }
      y += cardH + 30;
    }

    // 规则卡
    if (hasRule) {
      const cardX = PAD, cardW = W - PAD * 2, cardH = 220;
      ctx.fillStyle = 'rgba(255,255,255,.9)';
      roundRect(ctx, cardX, y, cardW, cardH, 24);
      ctx.fill();
      let ry = y + 24;
      const inX = cardX + 28, inW = cardW - 56;
      ctx.fillStyle = '#1F1208';
      ctx.font = '800 26px -apple-system, "PingFang SC", sans-serif';
      ctx.fillText('推 ' + o.n + ' 反 1 活动规则', inX, ry);
      ry += 44;
      ctx.fillStyle = '#5A4A3A';
      ctx.font = '400 24px -apple-system, "PingFang SC", sans-serif';
      ry = wrapText(ctx, `推荐 1 位朋友本店首单，你获约 ${o.stepPoints || ''} 积分`, inX, ry, inW, 36);
      ry = wrapText(ctx, `累计推 ${o.n} 位 → 积分可买本店所有商品 / 找商家兑换现金`, inX, ry, inW, 36);
      ry = wrapText(ctx, '商户承诺独立兑付 · 平台仅技术服务 · 不构成担保', inX, ry, inW, 36);
      y += cardH + 30;
    }

    // 二维码区
    {
      const qS = 300;
      const qX = (W - qS) / 2, qY = y + 10;
      // 白底圆角卡
      ctx.fillStyle = '#fff';
      roundRect(ctx, qX - 24, qY - 24, qS + 48, qS + 96, 24);
      ctx.fill();
      // 画 QR 模块矩阵（黑格）
      if (qr) {
        const count = qr.getModuleCount();
        const cell = qS / count;
        ctx.fillStyle = '#18130E';
        for (let r = 0; r < count; r++) {
          for (let c = 0; c < count; c++) {
            if (qr.isDark(r, c)) {
              // +1 像素消除格子间发丝缝隙
              ctx.fillRect(qX + c * cell, qY + r * cell, Math.ceil(cell) + 0.5, Math.ceil(cell) + 0.5);
            }
          }
        }
      }
      ctx.fillStyle = '#5A4A3A';
      ctx.font = '700 24px -apple-system, "PingFang SC", sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('长按 / 扫码进店参与', W / 2, qY + qS + 16);
      ctx.textAlign = 'left';
      y += qS + 96 + 20;
    }

    // 底部署名 + 免责
    ctx.textAlign = 'center';
    ctx.fillStyle = '#5A4A3A';
    ctx.font = '700 22px -apple-system, "PingFang SC", sans-serif';
    ctx.fillText('邀请人：' + (o.inviter || '客小二用户'), W / 2, y);
    y += 34;
    ctx.fillStyle = '#A0917F';
    ctx.font = '400 19px -apple-system, "PingFang SC", sans-serif';
    ctx.fillText('营销规则与兑付由商户独立负责 · 平台不担保', W / 2, y);
    ctx.textAlign = 'left';

    // 导出（taint 时抛 SecurityError → 由调用方 catch 降级）
    return cv.toDataURL('image/png');
  }

  // 先尝试含商品图；taint 则降级重绘
  try {
    return draw(true);
  } catch {
    try { return draw(false); } catch { return ''; }
  }
}

/**
 * H5 下载 dataURL 为图片文件。
 * @param {string} dataUrl
 * @param {string} [filename]
 */
export function downloadDataUrl(dataUrl, filename = 'invite-poster.png') {
  if (typeof document === 'undefined' || !dataUrl) return false;
  try {
    const a = document.createElement('a');
    a.href = dataUrl;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    return true;
  } catch {
    return false;
  }
}
