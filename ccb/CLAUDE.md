# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

This is a Yudao (芋道 / ruoyi-vue-pro) SaaS mall/shop fork. It is a **Spring Boot 2.7.18 monolith on JDK 8**, managed as a Maven multi-module build, with a Vue 3 admin frontend under `yudao-ui/`. The Maven revision is `2026.01-jdk8-SNAPSHOT` and is set globally via `${revision}` + the flatten-maven-plugin (don't hard-code version in child poms).

Repo-specific customizations on top of upstream yudao:
- `yudao-module-merchant` — SaaS 商户入驻 (merchant onboarding, tenant subscription, shop info, referral/brokerage, withdrawal)
- `yudao-module-video` — AI 视频生成 + 抖音发布 (Volcano Engine TTS/video + Douyin OAuth/publish)
- Cross-module communication between these two is **Spring application events** (e.g. `AiVideoTaskCreatedEvent` fired from merchant, handled by `AiVideoTaskCreatedListener` in video). Keep new inter-module coupling on events, not direct service calls, so modules stay independently deployable.

## Build, run, test

All Maven commands are run from repo root unless noted. The parent POM already points at Huawei/Aliyun mirrors, no extra `-s settings.xml` needed.

### Backend

```bash
# Full build (skip tests for speed)
mvn clean install -DskipTests

# Build a single module (e.g. after editing merchant)
mvn -pl yudao-module-merchant -am install -DskipTests     # -am: also build upstream deps

# Run the server — entry point is cn.iocoder.yudao.server.YudaoServerApplication
# Active profile is `local` (see yudao-server/src/main/resources/application.yaml)
mvn -pl yudao-server spring-boot:run

# Package the runnable jar (spring-boot-maven-plugin is only configured on yudao-server)
mvn -pl yudao-server -am package -DskipTests
# → yudao-server/target/yudao-server.jar

# Run all tests in a module
mvn -pl yudao-module-system test

# Run a single test class / method (JUnit 5 via maven-surefire-plugin 3.5.3)
mvn -pl yudao-module-system test -Dtest=RoleServiceImplTest
mvn -pl yudao-module-system test -Dtest=RoleServiceImplTest#testCreateRole
```

Server listens on **port 48080**. Admin API is under `/admin-api/**`, user/merchant app API under `/app-api/**`. Swagger: `http://localhost:48080/swagger-ui`, Knife4j: `http://localhost:48080/doc.html`.

### Frontend

Primary admin is `yudao-ui/yudao-ui-admin-vue3` (Vue 3 + Vite 5 + Element Plus + TS). **pnpm is required** (`>=8.6.0`, Node `>=16`).

```bash
cd yudao-ui/yudao-ui-admin-vue3
pnpm install
pnpm dev                 # uses .env.local — points at http://localhost:48080
pnpm dev-server          # uses .env.dev — points at remote dev
pnpm build:local         # local build; other modes: build:dev/test/stage/prod
pnpm ts:check            # vue-tsc type-check only
pnpm lint:eslint         # fix lint
pnpm lint:format         # prettier
```

Other UI projects exist but are not actively maintained here:
`yudao-ui-admin-vben` (Vben 5), `yudao-ui-admin-vue2`, `yudao-ui-admin-uniapp`, `yudao-ui-mall-uniapp` (商城小程序, uni-app, no build script — opened in HBuilderX).

### Required infra for local run

`application-local.yaml` expects: MySQL on `127.0.0.1:3306` (db `ruoyi-vue-pro`, user/pass `root/root`), Redis on `127.0.0.1:16379`. RocketMQ/RabbitMQ/Kafka configs exist but are not required unless you enable the relevant features. **Quartz auto-config is excluded in the `local` profile** — scheduled jobs won't fire locally by default.

### Database bootstrap

Schema lives in `sql/mysql/`. Apply in this order for a fresh DB:
1. `ruoyi-vue-pro.sql` — base yudao schema + seed data
2. `quartz.sql` — only if enabling Quartz
3. `mall.sql`, `mp.sql`, `member_pay.sql` — mall/公众号/会员支付 tables
4. `merchant.sql`, `video.sql` — this repo's custom modules
5. `v2_business_tables.sql` and any `fix_*.sql` — incremental migrations; read the header of each before running

## Architecture

### Module graph

```
yudao-dependencies           (BOM — pins every third-party version)
yudao-framework/
  yudao-common                             (PageResult, CommonResult, ServiceException, enums, utils — everything depends on this)
  yudao-spring-boot-starter-{web,security,mybatis,redis,mq,job,excel,
                             monitor,protection,websocket,test,
                             biz-tenant,biz-data-permission,biz-ip}
yudao-module-infra           (codegen, file, job-log, config, api-log, websocket demo)
yudao-module-system    → infra
yudao-module-member    → system
yudao-module-mp        → system
yudao-module-pay       → system
yudao-module-mall/
  yudao-module-product, yudao-module-promotion, yudao-module-trade,
  yudao-module-trade-api, yudao-module-statistics
yudao-module-merchant  → system, trade, product, member     (this repo)
yudao-module-video     → (listens to merchant events)       (this repo)
yudao-server           (the ONLY @SpringBootApplication — a shell that imports the modules)
```

Modules that exist in source but are **commented out in the root `pom.xml`** (to keep compile fast): `yudao-module-bpm`, `yudao-module-report`, `yudao-module-crm`, `yudao-module-erp`, `yudao-module-iot`, `yudao-module-mes`, `yudao-module-ai`. If you need one, uncomment it in both root `pom.xml` and `yudao-server/pom.xml`.

### Per-module package layout

Every business module follows the same three-layer package convention under `cn.iocoder.yudao.module.<name>`:

```
controller/
  admin/         → /admin-api/**  (management backend)
    vo/          → request/response VOs grouped by sub-feature
  app/           → /app-api/**    (C-end app, merchant mini-program, etc.)
    vo/
dal/
  dataobject/    → MyBatis Plus DO (table row POJO, extends BaseDO or TenantBaseDO)
  mysql/         → Mapper interfaces (+ matching XML in src/main/resources/mapper/)
service/         → FooService (interface) + FooServiceImpl
enums/           → FooStatusEnum, FooErrorCodeConstants
event/           → Spring application events for cross-module comms
job/             → Quartz job classes
framework/       → module-private @Configuration / auto-config glue
```

When adding a new feature, follow this layout exactly — the codegen (`yudao-module-infra` codegen) assumes it, and Swagger/security/tenant interceptors key off these package names.

### Entry point and component scan

`YudaoServerApplication` scans two roots only: `${yudao.info.base-package}.server` and `${yudao.info.base-package}.module` (i.e. `cn.iocoder.yudao.server` + `cn.iocoder.yudao.module`). Framework starters self-register via `spring.factories` / `AutoConfiguration.imports` — don't add them to `scanBasePackages`. `spring.main.allow-circular-references=true` is intentional (controller → service → controller in some audit flows).

### Multi-tenancy

Tenancy is **on by default** (`yudao.tenant.enable=true`). Key pieces:
- DOs extend `TenantBaseDO` → MyBatis Plus auto-appends `tenant_id = ?` on reads/writes
- REST calls must carry the `tenant-id` header (or hit a URL listed in `yudao.tenant.ignore-urls`)
- To bypass tenancy inside code: `TenantUtils.executeIgnore(...)` or `@TenantIgnore`
- Some tables (config, dict, oauth client, sms/mail template, iot device) are cross-tenant — listed in `yudao.tenant.ignore-tables` / `ignore-caches`
- Before adding a new DO, decide: tenanted (extend `TenantBaseDO`) or global (extend `BaseDO`). Getting this wrong silently leaks or hides data across tenants.

### Cross-module integration patterns

This codebase uses **three** patterns, in order of preference:
1. **Spring application events** (`ApplicationEventPublisher` + `@EventListener`) — the merchant ↔ video bridge uses this. Fire-and-forget, keeps modules loosely coupled.
2. **Internal API classes in `yudao-framework/yudao-common/biz/**`** (e.g. `DictDataCommonApi`, `PermissionCommonApi`) — sync calls that every module may need. Add new ones here only if truly cross-cutting.
3. **Direct `@Resource` of another module's `Service`** — allowed but creates a hard Maven dep (see `yudao-module-merchant/pom.xml` depending on `trade`/`product`/`member`). Use sparingly.

Do not add RPC/Feign — this is a monolith.

### Security & API paths

- Admin endpoints: `@PreAuthorize("@ss.hasPermission('module:resource:action')")` — permissions are seeded in `sql/mysql/ruoyi-vue-pro.sql` and editable via the admin UI's 菜单管理.
- App endpoints: JWT via `Authorization: Bearer <token>`; login flows are in `yudao-module-system` `auth` + `yudao-module-member` `auth`.
- `yudao.security.permit-all_urls` in YAML whitelists paths that skip auth (e.g. WeChat callbacks). Don't sprinkle `@PermitAll` annotations — add to YAML.
- API request/response can be AES-encrypted via `yudao.api-encrypt` (enabled by default). If you're debugging 400s, check you're not double-encrypting.

### Video + merchant environment variables

`yudao-module-video/src/main/resources/application-video.yaml` expects these env vars; missing-or-short `MERCHANT_INTERNAL_TOKEN` will **fail app startup** (enforced in `AppMerchantAiVideoController.initInternalToken()`):

- `MERCHANT_INTERNAL_TOKEN` — ≥16 chars, used to auth merchant↔video internal callbacks
- `VOLCANO_APP_ID`, `VOLCANO_ACCESS_TOKEN`, `VOLCANO_AK`, `VOLCANO_SK` — 火山引擎 TTS + smart creation
- `DOUYIN_CLIENT_KEY`, `DOUYIN_CLIENT_SECRET` — 抖音开放平台 OAuth

For local dev you can export dummies, but don't commit real values — the application YAMLs have real API keys for demo AI providers that should be rotated before any public deployment.

### Frontend ↔ backend wiring

- `VITE_BASE_URL` in `.env.local` points to `http://localhost:48080`; `VITE_API_URL=/admin-api` is the fixed prefix.
- Admin API clients are generated under `yudao-ui-admin-vue3/src/api/**`, grouped by module. When you add a controller, add a matching `api/` file so the UI can call it — no auto-generation.
- `VITE_APP_CAPTCHA_ENABLE` must match `yudao.captcha.enable` in `application-local.yaml` (both false locally).