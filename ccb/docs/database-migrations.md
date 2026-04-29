# 数据库迁移规范

## 文件命名

`sql/mysql/` 目录下三类文件：

| 模式 | 用途 | 自动执行 |
|------|------|---------|
| `*.sql`（无前缀）| **基础建库**：yudao 上游 + 各模块 schema 一次性导入 | ✅ 仅在新建库时跑（按 deploy.sh 顺序） |
| `V001__*.sql` | **增量迁移**：补字段、加索引、seed 数据 | ✅ 每次部署按版本号字典序跑（必须幂等） |
| `_DANGER__*.sql` | **破坏性脚本**（DROP/重置）| ❌ 仅人工定点执行 |

## V 文件命名细则

```
V<3 位数字>__<下划线分隔的英文/拼音描述>.sql
```

- 版本号从 `001` 开始，递增；新加迁移取当前最大版本 + 1
- 描述用蛇形命名，能在文件列表中一眼看出意图（避免起名 `V010__fix.sql` 这种空话）
- 同一逻辑改动只新增一个 V 文件，不要拆碎；同一文件里允许多张表的 ALTER + 多条 SEED

例：

```
V001__online_pay_apply.sql           # 在线支付字段
V002__member_shop_rel.sql            # 用户首次进店关系
V003__member_withdraw_apply.sql      # 用户提现表
V004__promo_settle_mode.sql          # 池结算模式枚举
V005__marketing_seed.sql             # 营销菜单 / Quartz Job seed
V006__demo_pay_seed.sql              # mock 支付渠道 seed
V007__pay_apply_kyc.sql              # KYC 字段（身份证 + 营业执照）
V008__pay_apply_allinpay.sql         # 通联进件 outOrderId
V009__json_to_varchar.sql            # 一些列由 JSON 改 VARCHAR
```

## 幂等性要求（硬性）

每个 V 文件**必须**能反复执行不报错，不破坏已有数据。常见模式：

### ALTER TABLE ADD COLUMN — 用 INFORMATION_SCHEMA 包存储过程

```sql
DROP PROCEDURE IF EXISTS `add_col_xxx`;
DELIMITER $$
CREATE PROCEDURE `add_col_xxx`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'foo'
                      AND COLUMN_NAME = 'bar') THEN
        ALTER TABLE `foo` ADD COLUMN `bar` VARCHAR(64) NULL COMMENT 'bar 描述';
    END IF;
END$$
DELIMITER ;
CALL `add_col_xxx`();
DROP PROCEDURE `add_col_xxx`;
```

### CREATE TABLE — 用 IF NOT EXISTS

```sql
CREATE TABLE IF NOT EXISTS `foo` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='foo 表';
```

### INSERT seed — 用 INSERT ... ON DUPLICATE KEY UPDATE

```sql
INSERT INTO `system_menu` (id, name, ...) VALUES (6100, '推广分销', ...)
ON DUPLICATE KEY UPDATE name = VALUES(name);
```

### MODIFY COLUMN — 检查现状再 ALTER

```sql
-- 通过 INFORMATION_SCHEMA 检查 DATA_TYPE / COLUMN_TYPE 后再 ALTER
-- 实例参考 V009__json_to_varchar.sql 里的 migrate_json_to_varchar 存储过程
```

## 文件头注释（建议）

```sql
-- description: <一句话说清这个 V 文件做了什么>
-- author: <作者>
-- date: YYYY-MM-DD
-- ticket: <可选，关联 issue / Linear / Jira 编号>
```

## deploy.sh 集成

`deploy.sh` 的 `setup_database` 阶段：

1. 先按固定顺序导入 base SQL（`ruoyi-vue-pro.sql` → `mall.sql` → `merchant.sql` → ...）
2. 然后 `V*.sql` 按字典序全部执行（幂等保证可反复跑）
3. `_DANGER__*.sql` 永不自动跑，需要人工 `mysql -uroot < sql/mysql/_DANGER__xxx.sql`

## 增加新迁移

```bash
# 1. 选当前最大版本 + 1
ls sql/mysql/V*.sql | sort | tail -1
# → V008__...
# 下一个就是 V009

# 2. 新建文件，遵守幂等模式
touch sql/mysql/V010__my_change.sql

# 3. 写完先在本机 / dev 库验证：跑两次都不报错
mysql -uroot -p ruoyi-vue-pro < sql/mysql/V010__my_change.sql
mysql -uroot -p ruoyi-vue-pro < sql/mysql/V010__my_change.sql

# 4. commit + push，下次 deploy.sh 会自动捎带
```

## 不引入 Flyway 的原因

- 项目依赖已稳定（2026.01 fork），不动 Maven 树
- yudao 启动期同步跑 Flyway 会加长冷启动；当前手动 `deploy.sh` 显式跑迁移
  对 4G ECS 内存友好
- `INFORMATION_SCHEMA` + 存储过程的幂等模式已经覆盖 90% 场景；剩下复杂迁移
  （重命名表 / 大表 ALTER）真要做时单独走人工 `_DANGER__*.sql`

未来引入 Flyway 时机：团队 ≥3 人 / 多环境（dev/test/staging/prod）/ 数据库分库
之后再考虑。
