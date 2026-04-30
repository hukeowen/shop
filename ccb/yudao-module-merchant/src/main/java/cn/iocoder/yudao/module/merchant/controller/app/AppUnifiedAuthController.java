package cn.iocoder.yudao.module.merchant.controller.app;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCreateReqDTO;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.yudao.framework.common.enums.TerminalEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;
import cn.iocoder.yudao.framework.ratelimiter.core.keyresolver.impl.ClientIpRateLimiterKeyResolver;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import javax.servlet.http.HttpServletRequest;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.merchant.controller.app.vo.auth.AppApplyMerchantBySmsReqVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.auth.AppApplyMerchantReqVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.auth.AppBindPhoneReqVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.auth.AppLoginRespVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.auth.AppPasswordLoginReqVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.auth.AppSwitchRoleReqVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.auth.AppWxMiniLoginReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.MerchantInviteCodeDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantMapper;
import cn.iocoder.yudao.module.merchant.service.MerchantInviteCodeService;
import cn.iocoder.yudao.module.merchant.service.MerchantService;
import cn.iocoder.yudao.module.merchant.service.auth.ActiveRoleCache;
import cn.iocoder.yudao.module.merchant.service.wechat.Jscode2SessionResult;
import cn.iocoder.yudao.module.merchant.service.wechat.WeChatMiniAppService;
import cn.iocoder.yudao.module.system.enums.oauth2.OAuth2ClientConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.PASSWORD_INVALID;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.PHONE_REQUIRED;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.ROLE_NOT_GRANTED;
import static cn.iocoder.yudao.module.merchant.enums.MerchantErrorCodeConstants.SESSION_KEY_EXPIRED;

/**
 * 摊小二统一登录 Controller（微信小程序 · 会员 ↔ 商户 双角色）
 *
 * <p>实际 URL 前缀 = {@code /app-api/app/auth}。会员和商户共用一个小程序入口：</p>
 *
 * <ol>
 *   <li>{@code POST /wx-mini-login}（PermitAll）：wx.login → openid → 会员注册/读取 → 发放 JWT</li>
 *   <li>{@code POST /bind-phone}（需登录）：getPhoneNumber → 回写 member.mobile / merchant.contact_phone</li>
 *   <li>{@code POST /apply-merchant}（需登录）：校验邀请码 + 可选手机号 → 开通商户身份</li>
 *   <li>{@code POST /switch-role}（需登录）：切换 activeRole</li>
 *   <li>{@code GET  /me}（需登录）：返回当前登录态摘要</li>
 * </ol>
 *
 * <p>架构说明：starter OAuth2 不支持 extra claims，roles/activeRole 不放 JWT，
 * 而是由当前接口在每次调用时按 memberId 从 DB + Redis 解析。</p>
 */
@Tag(name = "摊小二 App - 统一登录")
@RestController
@RequestMapping("/app/auth")
@Validated
@Slf4j
public class AppUnifiedAuthController {

    @Resource
    private WeChatMiniAppService weChatMiniAppService;
    @Resource
    private MemberUserMapper memberUserMapper;
    @Resource
    private MerchantMapper merchantMapper;
    @Resource
    private MerchantService merchantService;
    @Resource
    private MerchantInviteCodeService inviteCodeService;
    @Resource
    private ActiveRoleCache activeRoleCache;
    @Resource
    private OAuth2TokenCommonApi oauth2TokenApi;
    @Resource
    private PasswordEncoder passwordEncoder;

    // ==================== 1. 微信登录 ====================

    @PostMapping("/wx-mini-login")
    @Operation(summary = "微信小程序登录（code2Session）")
    @PermitAll
    @TenantIgnore // member_user 是全局表（tenant_id=0），会员登录不依赖 tenant-id Header
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<AppLoginRespVO> wxMiniLogin(@Valid @RequestBody AppWxMiniLoginReqVO reqVO) {
        Jscode2SessionResult session = weChatMiniAppService.jscode2session(reqVO.getCode());
        weChatMiniAppService.cacheSessionKey(session.getOpenid(), session.getSessionKey());

        MemberUserDO member = findOrCreateMember(session.getOpenid());
        MerchantDO merchant = merchantService.getMerchantByOpenId(session.getOpenid());
        List<String> roles = buildRoles(merchant);

        String activeRole = decideActiveRole(member.getId(), roles);
        activeRoleCache.set(member.getId(), activeRole);

        OAuth2AccessTokenRespDTO token = issueToken(member.getId());
        return success(buildLoginResp(token, member, merchant, roles, activeRole, session.getOpenid()));
    }

    // ==================== 1B. 演示用 — 手机号+密码登录（不发短信） ====================

    @PostMapping("/password-login")
    @Operation(summary = "手机号+密码登录（H5 / 演示用，首次输入即注册，不验真）")
    @PermitAll
    @TenantIgnore // member_user 是全局表，登录入口不需 tenant-id Header
    @Transactional(rollbackFor = Exception.class)
    // 防刷：每个 IP 60 秒内最多 6 次尝试，超出返 TOO_MANY_REQUESTS
    @RateLimiter(time = 60, count = 6, keyResolver = ClientIpRateLimiterKeyResolver.class,
                 message = "操作过于频繁，请稍后再试")
    public CommonResult<AppLoginRespVO> passwordLogin(@Valid @RequestBody AppPasswordLoginReqVO reqVO) {
        String mobile = reqVO.getMobile();
        String rawPassword = reqVO.getPassword();

        MemberUserDO member = memberUserMapper.selectByMobile(mobile);
        if (member == null) {
            // 自动注册：给 fake openid 满足后续 apply-merchant 的 openid 非空校验
            MemberUserDO newMember = new MemberUserDO();
            newMember.setMobile(mobile);
            newMember.setMiniAppOpenId("pwd:" + mobile);
            newMember.setPassword(passwordEncoder.encode(rawPassword));
            newMember.setStatus(0); // CommonStatusEnum.ENABLE
            newMember.setRegisterTerminal(TerminalEnum.H5.getTerminal());
            memberUserMapper.insert(newMember);
            member = newMember;
            log.info("[passwordLogin] 自动注册 mobile={} userId={}", mobile, newMember.getId());
        } else if (StrUtil.isBlank(member.getPassword())) {
            // 老账号未设密码 → 本次输入作为首次设置
            MemberUserDO update = new MemberUserDO();
            update.setId(member.getId());
            update.setPassword(passwordEncoder.encode(rawPassword));
            // 顺便补上 openid，否则 apply-merchant 流程会失败
            if (StrUtil.isBlank(member.getMiniAppOpenId())) {
                update.setMiniAppOpenId("pwd:" + mobile);
                member.setMiniAppOpenId("pwd:" + mobile);
            }
            memberUserMapper.updateById(update);
            member.setPassword(update.getPassword());
            log.info("[passwordLogin] 老账号首次设置密码 mobile={} userId={}", mobile, member.getId());
        } else if (!passwordEncoder.matches(rawPassword, member.getPassword())) {
            throw exception(PASSWORD_INVALID);
        }

        MerchantDO merchant = StrUtil.isNotBlank(member.getMiniAppOpenId())
                ? merchantService.getMerchantByOpenId(member.getMiniAppOpenId())
                : null;
        List<String> roles = buildRoles(merchant);
        String activeRole = decideActiveRole(member.getId(), roles);
        activeRoleCache.set(member.getId(), activeRole);

        OAuth2AccessTokenRespDTO token = issueToken(member.getId());
        return success(buildLoginResp(token, member, merchant, roles, activeRole, member.getMiniAppOpenId()));
    }

    // ==================== 2. 绑定手机号 ====================

    @PostMapping("/bind-phone")
    @Operation(summary = "绑定手机号（需先登录）")
    @TenantIgnore // member_user / merchant_info 都是全局表，JWT 已鉴权无需 tenant-id Header
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<AppLoginRespVO> bindPhone(@Valid @RequestBody AppBindPhoneReqVO reqVO) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        MemberUserDO member = memberUserMapper.selectById(userId);
        if (member == null || StrUtil.isBlank(member.getMiniAppOpenId())) {
            throw exception(SESSION_KEY_EXPIRED);
        }
        String sessionKey = weChatMiniAppService.getSessionKey(member.getMiniAppOpenId());
        if (StrUtil.isBlank(sessionKey)) {
            throw exception(SESSION_KEY_EXPIRED);
        }
        String phone = weChatMiniAppService.decryptPhoneEncryptedData(
                sessionKey, reqVO.getEncryptedData(), reqVO.getIv());

        MemberUserDO memberUpdate = new MemberUserDO();
        memberUpdate.setId(userId);
        memberUpdate.setMobile(phone);
        memberUserMapper.updateById(memberUpdate);
        member.setMobile(phone);

        MerchantDO merchant = merchantService.getMerchantByOpenId(member.getMiniAppOpenId());
        if (merchant != null && StrUtil.isBlank(merchant.getContactPhone())) {
            MerchantDO mUpdate = new MerchantDO();
            mUpdate.setId(merchant.getId());
            mUpdate.setContactPhone(phone);
            merchantMapper.updateById(mUpdate);
        }

        List<String> roles = buildRoles(merchant);
        String activeRole = decideActiveRole(userId, roles);
        OAuth2AccessTokenRespDTO token = issueToken(userId);
        return success(buildLoginResp(token, member, merchant, roles, activeRole, member.getMiniAppOpenId()));
    }

    // ==================== 3. 开通商户 ====================

    @PostMapping("/apply-merchant")
    @Operation(summary = "开通商户（需登录 + 邀请码）")
    @TenantIgnore // member_user / merchant_info 都是全局表，JWT 已鉴权无需 tenant-id Header
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<AppLoginRespVO> applyMerchant(@Valid @RequestBody AppApplyMerchantReqVO reqVO) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        MemberUserDO member = memberUserMapper.selectById(userId);
        if (member == null || StrUtil.isBlank(member.getMiniAppOpenId())) {
            throw exception(SESSION_KEY_EXPIRED);
        }
        String openid = member.getMiniAppOpenId();

        String phone = member.getMobile();
        if (StrUtil.isBlank(phone)) {
            if (StrUtil.isBlank(reqVO.getEncryptedData()) || StrUtil.isBlank(reqVO.getIv())) {
                throw exception(PHONE_REQUIRED);
            }
            String sessionKey = weChatMiniAppService.getSessionKey(openid);
            if (StrUtil.isBlank(sessionKey)) {
                throw exception(SESSION_KEY_EXPIRED);
            }
            phone = weChatMiniAppService.decryptPhoneEncryptedData(sessionKey,
                    reqVO.getEncryptedData(), reqVO.getIv());
            MemberUserDO memberUpdate = new MemberUserDO();
            memberUpdate.setId(userId);
            memberUpdate.setMobile(phone);
            memberUserMapper.updateById(memberUpdate);
            member.setMobile(phone);
        }

        MerchantInviteCodeDO inviteCode = inviteCodeService.validateAndConsume(reqVO.getInviteCode());

        Long merchantId = merchantService.createMerchantFromMember(
                userId, openid, null, phone, inviteCode.getId());
        MerchantDO merchant = merchantService.getMerchant(merchantId);
        List<String> roles = buildRoles(merchant);
        String activeRole = ActiveRoleCache.ROLE_MERCHANT;
        activeRoleCache.set(userId, activeRole);

        OAuth2AccessTokenRespDTO token = issueToken(userId);
        return success(buildLoginResp(token, member, merchant, roles, activeRole, openid));
    }

    // ==================== 3B. 公网申请商户（不要邀请码、SMS 验证） ====================

    @PostMapping("/apply-merchant-by-sms")
    @Operation(summary = "店铺名+手机号+SMS 一键申请商户（无需登录、无需邀请码）")
    @PermitAll
    @TenantIgnore // member_user / merchant_info 都是全局表，JWT 解析前先 ignore
    @Transactional(rollbackFor = Exception.class)
    @RateLimiter(time = 60, count = 3, keyResolver = ClientIpRateLimiterKeyResolver.class,
                 message = "申请频率过高，请稍后再试")
    public CommonResult<AppLoginRespVO> applyMerchantBySms(@Valid @RequestBody AppApplyMerchantBySmsReqVO reqVO) {
        String mobile = reqVO.getMobile();
        String shopName = reqVO.getShopName().trim();
        String smsCode = reqVO.getSmsCode();

        // 1. 校验验证码（演示模式固定 888888；生产应改为调 SmsCodeService.useSmsCode 真验码）
        // TODO 接入真 SMS：smsCodeService.useSmsCode(SmsCodeUseReqDTO.builder()
        //         .mobile(mobile).code(smsCode).scene(MERCHANT_APPLY).build());
        if (!"888888".equals(smsCode)) {
            throw exception(PASSWORD_INVALID); // 复用密码错误的错误码（演示用）
        }

        // 2. 幂等：找/建会员（password 留空，首次密码登录时再设）
        // 用 selectList 防御历史脏数据（mobile 列没唯一索引时同手机号可能多条）
        // 同手机号多 member 时，复用 id 最小的（最早注册的）；删多余的避免后续 selectOne 报 TooManyResults
        java.util.List<MemberUserDO> existedMembers = memberUserMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MemberUserDO>()
                        .eq(MemberUserDO::getMobile, mobile)
                        .orderByAsc(MemberUserDO::getId));
        MemberUserDO member;
        String fakeOpenId = "h5:" + mobile;
        if (existedMembers.isEmpty()) {
            MemberUserDO newMember = new MemberUserDO();
            newMember.setMobile(mobile);
            newMember.setMiniAppOpenId(fakeOpenId);
            newMember.setNickname(shopName);
            newMember.setStatus(0);
            newMember.setRegisterTerminal(TerminalEnum.H5.getTerminal());
            memberUserMapper.insert(newMember);
            member = newMember;
            // member_user 是商户的【底层账号载体】(yudao 统一账号体系)：
            //   member_user (mobile/password/openid) ← 关联 ─ merchant_info (店铺名/tenantId/通联号)
            // 这条 log 是"建商户账号底盘"，不是"把申请人变成普通用户"
            log.info("[applyMerchantBySms] 创建商户账号底盘 mobile={} userId={} (后续 merchant_info 会关联此 user_id)",
                    mobile, member.getId());
        } else {
            member = existedMembers.get(0);
            // 软删除多余 member 行（保留 id 最小的）
            if (existedMembers.size() > 1) {
                log.warn("[applyMerchantBySms] mobile={} 有 {} 条 member_user 记录，保留 userId={}，软删多余",
                        mobile, existedMembers.size(), member.getId());
                for (int i = 1; i < existedMembers.size(); i++) {
                    memberUserMapper.deleteById(existedMembers.get(i).getId());
                }
            }
            // 老账号 openId 缺失则补上
            if (StrUtil.isBlank(member.getMiniAppOpenId())) {
                MemberUserDO upd = new MemberUserDO();
                upd.setId(member.getId());
                upd.setMiniAppOpenId(fakeOpenId);
                memberUserMapper.updateById(upd);
                member.setMiniAppOpenId(fakeOpenId);
            }
        }

        // 3. 已是商户：直接返登录态（幂等，避免重复点击重复建租户）
        // selectListByOpenId 防御 merchant_info.open_id 重复脏数据
        MerchantDO existed = selectMerchantByOpenIdSafe(member.getMiniAppOpenId());
        if (existed != null) {
            log.info("[applyMerchantBySms] 该手机号 {} 已是商户 (merchantId={})，幂等返回", mobile, existed.getId());
            List<String> roles = buildRoles(existed);
            String activeRole = ActiveRoleCache.ROLE_MERCHANT;
            activeRoleCache.set(member.getId(), activeRole);
            OAuth2AccessTokenRespDTO token = issueToken(member.getId());
            return success(buildLoginResp(token, member, existed, roles, activeRole, member.getMiniAppOpenId()));
        }

        // 4. 创建租户 + 店铺 + 商户（inviteCodeId=null 跳过邀请码逻辑）
        // PermitAll 路径下 SecurityContext 为空，yudao BaseDO 自动填 creator/updater
        // 会拿到 null → SQL 报 "Column 'creator' cannot be null"。这里手工设
        // fakeUser，让 createTenant 等 INSERT 能拿到 member.id 作为 creator。
        LoginUser fakeUser = new LoginUser();
        fakeUser.setId(member.getId());
        fakeUser.setUserType(UserTypeEnum.MEMBER.getValue());
        try {
            HttpServletRequest req = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            SecurityFrameworkUtils.setLoginUser(fakeUser, req);
        } catch (Exception ignored) {
            // 没拿到 request 也继续，BaseDO 拿不到 user 才会报错
        }
        Long merchantId;
        MerchantDO merchant;
        OAuth2AccessTokenRespDTO token;
        // 关键：方法上 @TenantIgnore 让 step 1-3 能跨租户查 member_user / merchant_info
        // （member_user 是全局表，merchant_info 是租户表但要按 openId 全局幂等查）。
        // 但进入 createMerchantFromMember 后 yudao 内部会调 createTenant → createRole，
        // RoleMapper.selectByName('租户管理员') 是 TenantBaseDO 表，必须按新租户的
        // tenant_id 过滤。如果继续保持 ignore=true，会全表扫到 ruoyi-vue-pro.sql
        // seed 的 (id=109,tenant_id=121) + (id=111,tenant_id=122) 两条历史 demo 数据 →
        // selectOne 直接 TooManyResultsException 把整条申请流程崩掉。
        // 做法：手工 setIgnore(false)，让 yudao 内部 TenantUtils.execute(newTenantId)
        // 切到新租户 ctx 后能正确加 tenant_id 过滤；finally 恢复成 true 让 aspect 不
        // 混乱（aspect finally 会基于进入时的 oldIgnore 恢复）。
        // 同时保存 tenantId — 防 createTenant 中途异常时 TenantUtils.execute 的 finally
        // 没机会还原，导致这一请求后续逻辑（buildLoginResp / activeRoleCache 等）被新租户 ctx 污染
        Boolean prevIgnore = TenantContextHolder.isIgnore();
        Long prevTenantId = TenantContextHolder.getTenantId();
        TenantContextHolder.setIgnore(false);
        try {
            merchantId = merchantService.createMerchantFromMember(
                    member.getId(), member.getMiniAppOpenId(), null, mobile, null);
            merchant = merchantService.getMerchant(merchantId);

            // 5. 把"新店<userId>"默认值改成用户填的 shopName
            //    (BaseDO updater 字段也需要 LoginUser，所以放在 try 块内)
            try {
                applyCustomShopName(merchant, shopName);
            } catch (Exception e) {
                log.warn("[applyMerchantBySms] 改店铺名失败 merchantId={} shopName={}", merchantId, shopName, e);
            }

            // 6. 发 token（OAuth2 token 表也是 BaseDO，creator 字段同样需要 LoginUser）
            token = issueToken(member.getId());
        } finally {
            // 清掉 fake context，避免污染后续 filter chain
            SecurityContextHolder.clearContext();
            // 同时还原 ignore + tenantId，防异常分支留下脏 ThreadLocal
            TenantContextHolder.setIgnore(prevIgnore);
            TenantContextHolder.setTenantId(prevTenantId);
        }

        List<String> roles = buildRoles(merchant);
        String activeRole = ActiveRoleCache.ROLE_MERCHANT;
        activeRoleCache.set(member.getId(), activeRole);
        log.info("[applyMerchantBySms] 申请成功 mobile={} merchantId={} tenantId={} shopName={}",
                mobile, merchantId, merchant.getTenantId(), shopName);
        return success(buildLoginResp(token, member, merchant, roles, activeRole, member.getMiniAppOpenId()));
    }

    /**
     * 防御 merchant_info.open_id 历史脏数据（同 openid 多条）— 用 selectList 取最早那条
     * （对比 merchantService.getMerchantByOpenId 内部用 selectOne 会撞 TooManyResults）
     */
    private MerchantDO selectMerchantByOpenIdSafe(String openId) {
        if (StrUtil.isBlank(openId)) return null;
        java.util.List<MerchantDO> list = merchantMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MerchantDO>()
                        .eq(MerchantDO::getOpenId, openId)
                        .orderByAsc(MerchantDO::getId));
        if (list.isEmpty()) return null;
        if (list.size() > 1) {
            log.warn("[selectMerchantByOpenIdSafe] openId={} 有 {} 条 merchant_info，复用 id={}",
                    openId, list.size(), list.get(0).getId());
        }
        return list.get(0);
    }

    /** 把默认 "新店<userId>" 名字改成用户填的 shopName */
    private void applyCustomShopName(MerchantDO merchant, String shopName) {
        if (merchant == null || StrUtil.isBlank(shopName)) return;
        // (a) merchant_info.name
        MerchantDO mUpd = new MerchantDO();
        mUpd.setId(merchant.getId());
        mUpd.setName(shopName);
        merchantMapper.updateById(mUpd);
        // (b) shop_info.shop_name (跨租户写：用 TenantContextHolder 切到该租户)
        cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO si =
                cn.iocoder.yudao.framework.tenant.core.util.TenantUtils.execute(
                        merchant.getTenantId(),
                        () -> {
                            cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper m =
                                    cn.hutool.extra.spring.SpringUtil.getBean(
                                            cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper.class);
                            cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO existed =
                                    m.selectByTenantId(merchant.getTenantId());
                            if (existed != null) {
                                cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO upd =
                                        new cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO();
                                upd.setId(existed.getId());
                                upd.setShopName(shopName);
                                m.updateById(upd);
                            }
                            return existed;
                        });
    }

    // ==================== 4. 切换角色 ====================

    @PostMapping("/switch-role")
    @Operation(summary = "切换当前激活角色")
    @TenantIgnore // member_user / merchant_info 都是全局表，JWT 已鉴权无需 tenant-id Header
    public CommonResult<AppLoginRespVO> switchRole(@Valid @RequestBody AppSwitchRoleReqVO reqVO) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        MemberUserDO member = memberUserMapper.selectById(userId);
        if (member == null) {
            throw exception(SESSION_KEY_EXPIRED);
        }
        MerchantDO merchant = merchantService.getMerchantByOpenId(member.getMiniAppOpenId());
        List<String> roles = buildRoles(merchant);
        if (!roles.contains(reqVO.getRole())) {
            throw exception(ROLE_NOT_GRANTED);
        }
        activeRoleCache.set(userId, reqVO.getRole());
        OAuth2AccessTokenRespDTO token = issueToken(userId);
        return success(buildLoginResp(token, member, merchant, roles, reqVO.getRole(), member.getMiniAppOpenId()));
    }

    // ==================== 5. 获取当前登录态 ====================

    @GetMapping("/me")
    @Operation(summary = "获取当前登录态")
    @TenantIgnore // member_user / merchant_info 都是全局表，JWT 已鉴权无需 tenant-id Header
    public CommonResult<AppLoginRespVO> me() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        MemberUserDO member = memberUserMapper.selectById(userId);
        if (member == null) {
            throw exception(SESSION_KEY_EXPIRED);
        }
        MerchantDO merchant = merchantService.getMerchantByOpenId(member.getMiniAppOpenId());
        List<String> roles = buildRoles(merchant);
        String activeRole = decideActiveRole(userId, roles);
        AppLoginRespVO resp = new AppLoginRespVO();
        resp.setUserId(userId);
        resp.setOpenid(member.getMiniAppOpenId());
        resp.setPhone(member.getMobile());
        resp.setRoles(roles);
        resp.setActiveRole(activeRole);
        resp.setMerchantId(merchant == null ? null : merchant.getId());
        return success(resp);
    }

    // ==================== 内部工具 ====================

    /**
     * 幂等取/建会员。依赖 {@code member_user.mini_app_open_id} 上的 UNIQUE 索引，配合
     * {@code INSERT IGNORE} 兜底并发竞态：两条请求同时拿到 null 双双进入 insert 分支时，
     * 只有一条真正入表，另一条被 UNIQUE 约束静默忽略，最后统一重新查一次即可。
     */
    private MemberUserDO findOrCreateMember(String openid) {
        // 快速路径：已注册直接返回
        MemberUserDO existed = memberUserMapper.selectByMiniAppOpenId(openid);
        if (existed != null) {
            return existed;
        }
        // 幂等插入：冲突时返回 0，不抛异常
        int affected = memberUserMapper.insertIgnoreByMiniAppOpenId(
                openid,
                0, // CommonStatusEnum.ENABLE
                TerminalEnum.WECHAT_MINI_PROGRAM.getTerminal());
        // 回查：无论是本次插入成功还是被并发请求抢先插入，最终必有记录
        MemberUserDO finalMember = memberUserMapper.selectByMiniAppOpenId(openid);
        if (finalMember == null) {
            // 理论不该发生：唯一的可能是 SQL 层 UNIQUE 索引未生效，属配置/迁移问题
            throw exception(SESSION_KEY_EXPIRED);
        }
        log.info("[wxLogin] openid={} insertWon={}", openid, affected == 1);
        return finalMember;
    }

    private OAuth2AccessTokenRespDTO issueToken(Long userId) {
        return oauth2TokenApi.createAccessToken(new OAuth2AccessTokenCreateReqDTO()
                .setUserId(userId)
                .setUserType(UserTypeEnum.MEMBER.getValue())
                .setClientId(OAuth2ClientConstants.CLIENT_ID_DEFAULT));
    }

    private List<String> buildRoles(MerchantDO merchant) {
        List<String> roles = new ArrayList<>();
        roles.add(ActiveRoleCache.ROLE_MEMBER);
        if (merchant != null) {
            roles.add(ActiveRoleCache.ROLE_MERCHANT);
        }
        return Collections.unmodifiableList(roles);
    }

    private String decideActiveRole(Long userId, List<String> roles) {
        String cached = activeRoleCache.get(userId);
        if (ActiveRoleCache.isValidRole(cached) && roles.contains(cached)) {
            return cached;
        }
        return roles.contains(ActiveRoleCache.ROLE_MERCHANT)
                ? ActiveRoleCache.ROLE_MERCHANT : ActiveRoleCache.ROLE_MEMBER;
    }

    private AppLoginRespVO buildLoginResp(OAuth2AccessTokenRespDTO token,
                                          MemberUserDO member,
                                          MerchantDO merchant,
                                          List<String> roles,
                                          String activeRole,
                                          String openid) {
        AppLoginRespVO vo = new AppLoginRespVO();
        vo.setToken(token.getAccessToken());
        vo.setRefreshToken(token.getRefreshToken());
        vo.setExpiresTime(token.getExpiresTime());
        vo.setUserId(member.getId());
        vo.setPhone(member.getMobile());
        vo.setRoles(roles);
        vo.setActiveRole(activeRole);
        vo.setOpenid(openid);
        vo.setMerchantId(merchant == null ? null : merchant.getId());
        return vo;
    }
}
