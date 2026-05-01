# 摊小二 — 一键部署指南

**目标**：从一台空白阿里云 ECS（CentOS 7.9 x86_64）开始，一条命令跑完，
得到可访问的官网 + 商户/用户端 H5 + PC 管理后台 + AI 视频后处理服务。

---

## 一、环境要求

| 项目 | 推荐 | 最低 |
|---|---|---|
| OS | 阿里云 CentOS 7.9 x86_64 | RHEL/CentOS 7+ |
| CPU | 4 vCPU | 2 vCPU |
| 内存 | 8 GB | 4 GB（脚本会自动加 2-4 GB swap） |
| 磁盘 | 80 GB SSD | 40 GB |
| 公网带宽 | 5 Mbps | 1 Mbps |
| 安全组 | 80 / 443 / 22 入方向放行 | 同 |

> CentOS 7 glibc 2.17 限制 Node ≤ 16，脚本已锁版本，无需手动指定。

---

## 二、三步部署（首次）

> **国内阿里云 ECS 务必先配 GitHub 反代**，否则 `git clone` / `curl raw.githubusercontent.com`
> 容易超时。任选其一（已实测可用）：`https://gh-proxy.com/` / `https://ghfast.top/` /
> `https://gh.llkk.cc/`，下面的 `${GH}` 是反代前缀，**不需要可留空**。

```bash
# 0) 国内 ECS 加速（可选）
GH=https://gh-proxy.com/        # 国内 ECS：填反代；境外/国内大厂 BGP：留空 GH=

# 1) 拉脚本
curl -fsSL ${GH}https://raw.githubusercontent.com/hukeowen/shop/main/ccb/deploy.sh -o /tmp/deploy.sh

# 2) 准备配置（在脚本所在目录执行）
mkdir -p /opt/tanxiaer && cd /opt/tanxiaer
curl -fsSL ${GH}https://raw.githubusercontent.com/hukeowen/shop/main/ccb/.env.example -o .env.example
cp .env.example .env
vi .env                # 把 CHANGE_ME_xxx 全部改成真实强密码
                       # 国内 ECS 还要在 .env 里写一行 GITHUB_PROXY=${GH}（让脚本 git clone 用反代）

# 3) 一键跑完（约 25-40 分钟，取决于网络）
sudo ENV_FILE=/opt/tanxiaer/.env bash /tmp/deploy.sh
```

完成后控制台会输出：

```
🎉 摊小二部署完成！
官网首页:    http://<ECS 公网 IP>/
商户/用户端: http://<ECS 公网 IP>/m/
管理后台:    http://<ECS 公网 IP>/admin/
```

---

## 三、`.env` 关键字段

| 字段 | 必填 | 说明 |
|---|---|---|
| `MYSQL_ROOT_PASS` | ✅ | MySQL root 密码（脚本会创建） |
| `DB_PASS` | ✅ | 业务库 `tanxiaer` 用户密码 |
| `REDIS_PASS` | ✅ | Redis 密码 |
| `MERCHANT_INTERNAL_TOKEN` | ✅ | 商户↔视频内部鉴权 token，**≥16 字符随机串** |
| `ARK_API_KEY` | ⚠ | 火山方舟 API Key，缺则 AI 视频文案/Seedance 不可用 |
| `VOLCANO_APP_ID` + `VOLCANO_ACCESS_TOKEN` | ⚠ | 豆包 TTS 配音 |
| `JIMENG_AK` + `JIMENG_SK` | ⚠ | 即梦图生视频 + CV 美化 + 端卡海报（商户主链路） |
| `DOUYIN_CLIENT_KEY` + `DOUYIN_CLIENT_SECRET` | ⚠ | 抖音视频发布 |
| `YUDAO_SMS_DEMO_MODE` | ⚠ | `true` 演示模式固定码 `888888`；生产改 `false` 接真短信网关 |

✅ = 必填（不填脚本启动失败）；⚠ = 留空则对应功能降级，主链路（商户开通 / 商品上架 / 推广引擎 / 用户下单）不受影响。

---

## 四、再次部署 / 升级 / 重置

```bash
# A) 拉最新代码 + 重新打包重启（最常用）
sudo bash /tmp/deploy.sh --skip-install

# B) 仅替换已编译产物（CI/CD 远程打包后发上来）
sudo bash /tmp/deploy.sh --skip-install --skip-build

# C) ⚠️ 重置数据：DROP DATABASE + Redis FLUSHALL（演示后清场用）
sudo bash /tmp/deploy.sh --skip-install --reset
```

---

## 五、生产上线 checklist

部署完成 ≠ 上线就绪。客户首次面向真实用户前请确认：

- [ ] 阿里云 ECS 安全组放行 **80/443**（firewalld 在脚本里已开，但安全组是另一层）
- [ ] DNS 解析域名到 ECS 公网 IP（修改 `.env` 的 `SERVER_NAME=你的域名` 重跑）
- [ ] HTTPS：`yum install -y certbot python2-certbot-nginx && certbot --nginx`
- [ ] `.env` 中 `YUDAO_SMS_DEMO_MODE=false`，PC 后台「系统管理 → 短信渠道」配置短信网关 + 「短信模板」绑 `user-sms-login`
- [ ] **PC 后台「商品 → 商品分类」建立至少 1 条真分类**（前端 product/edit 优先拉后端，
      为空时降级 fallback 8 类，但生产建议显式配置）
- [ ] 填入真实 `ARK_API_KEY` / `JIMENG_AK/SK`（如不用 AI 视频可留空）
- [ ] 填入真实 `DOUYIN_CLIENT_KEY/SECRET`（如不用抖音发布可留空）
- [ ] 演示邀请码 `DEMO20260428` **禁用**（PC 后台 → merchant_invite_code → 改 status=2）
- [ ] PC 后台默认密码改强：`admin / admin123` → 强密码
- [ ] 火山方舟控制台设单日额度上限（防 AI 调用刷量）
- [ ] 备份策略：MySQL `mysqldump` 定时上传 OSS / 异地

---

## 六、运行期排查

| 现象 | 命令 |
|---|---|
| 后端起不来 | `journalctl -u tanxiaer -n 100 --no-pager` |
| Sidecar 起不来 | `journalctl -u tanxiaer-sidecar -n 100 --no-pager` |
| Nginx 502 | `tail -200 /var/log/nginx/error.log` |
| MySQL 连不上 | `systemctl status mysqld && tail /var/log/mysqld.log` |
| Redis 鉴权错 | `bash /opt/tanxiaer/repo/ccb/scripts/fix-redis-auth.sh` |
| 静态文件 404 | `bash /opt/tanxiaer/repo/ccb/scripts/fix-selinux-static.sh` |
| 部署后冒烟自检 | `bash /opt/tanxiaer/repo/ccb/scripts/post-deploy-verify.sh --base-url http://localhost --mysql-pass <root_pass> --project-dir /opt/tanxiaer/repo/ccb` |

服务管理：
```bash
systemctl restart tanxiaer            # 后端
systemctl restart tanxiaer-sidecar    # AI 视频后处理
systemctl restart nginx               # 反代
```

日志位置：
- 后端：`/opt/tanxiaer/logs/stdout.log` / `stderr.log`
- Sidecar：`journalctl -u tanxiaer-sidecar`
- Nginx：`/var/log/nginx/access.log` + `error.log`

---

## 七、目录结构

```
/opt/tanxiaer/
├─ .env                  # 客户填的密码（mode 600）
├─ repo/ccb/            # 仓库（脚本自动 git pull）
├─ app/yudao-server.jar # 后端可运行 jar
├─ app/runtime.env      # systemd 注入的 ENV（mode 600，含 ARK key 等）
├─ admin-dist/          # PC 管理后台静态产物（nginx /admin/）
├─ m/                    # 商户/用户端 H5 静态产物（nginx /m/）
├─ sidecar/             # Node sidecar（systemd: tanxiaer-sidecar）
├─ logs/                # 后端日志
└─ .token-secret        # JWT 签名密钥（首次自动生成，mode 600）
```

---

## 八、数据库迁移规范

`sql/mysql/V*.sql` 按字典序自动执行（`V001__xxx.sql ... V0NN__xxx.sql`），
**每个迁移必须幂等**（`CREATE TABLE IF NOT EXISTS` / `INFORMATION_SCHEMA` 检查）。

新增字段 / 表：
1. 在 `sql/mysql/` 加 `V0NN__feature.sql`（NN = 现有最大值 + 1）
2. 重跑 `sudo bash deploy.sh --skip-install` 自动应用
3. 不要修改已发布的 V 文件（要回滚加 V0NN+1__revert_xxx.sql）

破坏性脚本（如 `_DANGER__tenant_reset.sql`）**永不自动跑**，需手动 `mysql -u root -p < _DANGER__xxx.sql`。

---

## 九、Q&A

**Q：脚本中途失败，可以重跑吗？**
A：可以。脚本所有步骤设计成幂等（已存在跳过），重跑同一命令会从失败点继续。

**Q：演示当天网络不好，能不能本地打好包传上去？**
A：本地 `mvn clean package -DskipTests` 拿 `yudao-server.jar`，
本地 `cd yudao-ui/yudao-ui-admin-vue3 && pnpm build` 拿 `dist/`，
本地 `cd yudao-ui/yudao-ui-merchant-uniapp && pnpm build:h5` 拿 `dist/build/h5/`，
上传到 ECS 对应目录后跑 `--skip-build` 即可。

**Q：需要 GPU 吗？**
A：不需要。所有 AI 调用走火山方舟云端 API，ECS 只跑业务 / sidecar。

**Q：能在 Ubuntu / Debian 跑吗？**
A：脚本只针对 CentOS 7.9 验证过。Ubuntu 需要把 `yum` 替换成 `apt`、`firewalld` 替换 `ufw`、`policycoreutils` 部分跳过等。如有需要可以给我们提 issue。
