# 摊小二 AI 视频 BGM 库

sidecar `/video/mux` 自动从此目录挑选 BGM 与 TTS 混音。

## 命名规则

`<bgmStyle>_<n>.mp3`，其中 `bgmStyle` 是 LLM 在富脚本里输出的 6 个 key 之一：

| Style key | 用途 | 推荐风格关键词 |
|---|---|---|
| `street_food_yelling` | 烧烤/夜市/路边摊（叫卖型） | upbeat drums, street market, hyped |
| `cozy_explore` | 咖啡馆/网红店/餐厅（探店温馨） | acoustic guitar, light pop, vlog |
| `asmr_macro` | 茶叶/水果切片/烘焙（ASMR 静谧） | ambient, soft, no drums |
| `elegant_tea` | 茶馆/酒/古典中式 | guzheng, traditional, calm |
| `trendy_pop` | 奶茶/甜品/时尚单品（网红跟风） | electronic pop, energetic |
| `emotional_story` | 老板访谈/手艺传承（情感故事） | piano, emotional, cinematic |

每个 key 准备 **2-3 首** mp3 避免不同视频用同一段（抖音算法会判重）。

## 文件示例

```
sidecar/bgm/
├── street_food_yelling_1.mp3
├── street_food_yelling_2.mp3
├── street_food_yelling_3.mp3
├── cozy_explore_1.mp3
├── cozy_explore_2.mp3
├── asmr_macro_1.mp3
├── asmr_macro_2.mp3
├── elegant_tea_1.mp3
├── elegant_tea_2.mp3
├── trendy_pop_1.mp3
├── trendy_pop_2.mp3
└── emotional_story_1.mp3
```

## 素材来源（CC0 / 商用免费）

1. **Pixabay Music** <https://pixabay.com/music/> — 关键词搜「market」「cooking」「ASMR」等
2. **FreePD** <https://freepd.com/>
3. **YouTube Audio Library** — 选「无版权要求」的曲目
4. **FMA (Free Music Archive)** <https://freemusicarchive.org/>
5. **海螺/Suno 自建** — 用 LLM prompt 生成原创 30 秒纯音乐

## 处理建议

- 时长截 **30-45 秒纯音乐**（视频是 10-15 秒，能覆盖 + 留余量给 BGM 渐弱）
- 比特率 **128kbps mp3** 即可（文件小）
- **首尾各 2 秒淡入淡出**：`ffmpeg -i in.mp3 -af "afade=t=in:d=2,afade=t=out:st=28:d=2" -b:a 128k out.mp3`

## 当前状态

✅ sidecar 代码已支持自动 pick + amix 混音
⏳ 此目录暂为空，运维需补充素材；空时降级为仅 TTS 音轨（不影响主流程）
