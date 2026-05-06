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
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
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
import cn.iocoder.yudao.module.merchant.dal.dataobject.ShopInfoDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.MerchantMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.ShopInfoMapper;
import cn.iocoder.yudao.module.merchant.service.MerchantInviteCodeService;
import cn.iocoder.yudao.module.merchant.service.MerchantService;
import cn.iocoder.yudao.module.merchant.service.auth.ActiveRoleCache;
import cn.iocoder.yudao.module.merchant.service.wechat.Jscode2SessionResult;
import cn.iocoder.yudao.module.merchant.service.wechat.WeChatMiniAppService;
import cn.iocoder.yudao.module.system.api.sms.SmsCodeApi;
import cn.iocoder.yudao.module.system.api.sms.dto.code.SmsCodeSendReqDTO;
import cn.iocoder.yudao.module.system.api.sms.dto.code.SmsCodeUseReqDTO;
import cn.iocoder.yudao.module.system.enums.oauth2.OAuth2ClientConstants;
import cn.iocoder.yudao.module.system.enums.sms.SmsSceneEnum;
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
    private ShopInfoMapper shopInfoMapper;
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
    @Resource
    private SmsCodeApi smsCodeApi;

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

        // 商户登录必须在 merchant.tenantId 上下文内签 token，否则 oauth2_access_token.tenant_id
        // 落空导致后续所有 /app-api/** 跨租户。详见 issueTokenForMerchant Javadoc。
        OAuth2AccessTokenRespDTO token = issueTokenForMerchant(
                member.getId(), merchant == null ? null : merchant.getTenantId());
        return success(buildLoginResp(token, member, merchant, roles, activeRole, session.getOpenid()));
    }

    // ==================== 1A. C 端被分享落地：邀请人公开信息（无需登录） ====================

    /**
     * 通过 inviter userId 拉取邀请人的公开展示信息（昵称 + 头像）。
     *
     * <p>用于 C 端用户从分享链接 ({@code /m/shop-home?tenantId=X&inviter=Y})
     * 进入登录页时，登录页能展示"小李 邀请你来 王师傅烤地瓜"的引导。仅返展示用字段，
     * 不暴露手机号 / openid 等敏感信息。</p>
     *
     * <p>幂等 + 容错：找不到 user / mobile 不存在时返 null（不抛错，前端兜底）。</p>
     */
    @GetMapping("/inviter-info")
    @Operation(summary = "C 端被分享落地：拉邀请人公开展示信息（昵称+头像）")
    @PermitAll
    @TenantIgnore
    public CommonResult<java.util.Map<String, Object>> getInviterInfo(
            @org.springframework.web.bind.annotation.RequestParam("inviterUserId") Long inviterUserId) {
        if (inviterUserId == null || inviterUserId <= 0) {
            return success(null);
        }
        try {
            MemberUserDO inviter = memberUserMapper.selectById(inviterUserId);
            if (inviter == null) return success(null);
            java.util.Map<String, Object> resp = new java.util.LinkedHashMap<>();
            resp.put("userId", inviter.getId());
            resp.put("nickname", StrUtil.isBlank(inviter.getNickname()) ? "好友" : inviter.getNickname());
            resp.put("avatarUrl", inviter.getAvatar());
            return success(resp);
        } catch (Exception e) {
            log.warn("[getInviterInfo] failed inviterUserId={}", inviterUserId, e);
            return success(null);
        }
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

        // 商户登录必须在 merchant.tenantId 上下文内签 token，否则 oauth2_access_token.tenant_id
        // 落空导致后续所有 /app-api/** 跨租户。详见 issueTokenForMerchant Javadoc。
        OAuth2AccessTokenRespDTO token = issueTokenForMerchant(
                member.getId(), merchant == null ? null : merchant.getTenantId());
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
        // 商户登录必须在 merchant.tenantId 上下文内签 token，详见 issueTokenForMerchant Javadoc。
        OAuth2AccessTokenRespDTO token = issueTokenForMerchant(
                userId, merchant == null ? null : merchant.getTenantId());
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
        // createMerchantFromMember 内部 tenant_id 是 TenantBaseDO 自动注入的，
        // 但 controller 这层是 @TenantIgnore 的，要 ignore 跨租户取回 merchant 拿真 tenantId。
        final Long mid2 = merchantId;
        MerchantDO merchant = TenantUtils.executeIgnore(() -> merchantService.getMerchant(mid2));
        List<String> roles = buildRoles(merchant);
        String activeRole = ActiveRoleCache.ROLE_MERCHANT;
        activeRoleCache.set(userId, activeRole);

        // 商户登录必须在 merchant.tenantId 上下文内签 token，详见 issueTokenForMerchant Javadoc。
        OAuth2AccessTokenRespDTO token = issueTokenForMerchant(
                userId, merchant == null ? null : merchant.getTenantId());
        return success(buildLoginResp(token, member, merchant, roles, activeRole, openid));
    }

    // ==================== 3B. 公网申请商户（不要邀请码、SMS 验证） ====================

    /**
     * 发送商户申请 / 登录用 SMS 验证码。
     *
     * <p><b>当前实现：固定验证码 888888（不真发短信）</b></p>
     *
     * <p>简化处理：用户点"发送验证码" → 后端直接返成功，验证码默认 888888。
     * 接口仍保留 IP 限流（每分钟 5 次）和参数校验，前端流程完整。</p>
     *
     * <p>TODO 接入真 SMS 服务时把下面 5 行恢复成 SmsCodeApi.sendSmsCode 调用：</p>
     * <pre>
     *   SmsCodeSendReqDTO dto = new SmsCodeSendReqDTO();
     *   dto.setMobile(reqVO.getMobile());
     *   dto.setScene(SmsSceneEnum.MEMBER_LOGIN.getScene());
     *   dto.setCreateIp(resolveClientIp());
     *   smsCodeApi.sendSmsCode(dto);
     * </pre>
     */
    @PostMapping("/send-sms-code")
    @Operation(summary = "发送商户申请验证码（当前固定 888888，不真发短信）")
    @PermitAll
    @TenantIgnore
    @RateLimiter(time = 60, count = 5, keyResolver = ClientIpRateLimiterKeyResolver.class,
                 message = "发送频率过高，请稍后再试")
    public CommonResult<Boolean> sendSmsCodeForMerchantApply(
            @Valid @RequestBody cn.iocoder.yudao.module.merchant.controller.app.vo.auth.AppSmsCodeSendReqVO reqVO) {
        // 简化处理：不真发短信，验证码固定 888888（applyMerchantBySms 那侧硬比对）
        log.info("[sendSmsCode] mobile={} 固定码模式（待接入真 SMS）", reqVO.getMobile());
        return success(true);
    }

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

        // 1. 校验验证码：当前固定 888888（不依赖短信网关）
        //    与 /send-sms-code 简化处理对应；接入真 SMS 服务时改回 SmsCodeApi.useSmsCode：
        //    SmsCodeUseReqDTO useDTO = new SmsCodeUseReqDTO();
        //    useDTO.setMobile(mobile); useDTO.setCode(smsCode);
        //    useDTO.setScene(SmsSceneEnum.MEMBER_LOGIN.getScene());
        //    useDTO.setUsedIp(resolveClientIp());
        //    smsCodeApi.useSmsCode(useDTO);
        if (!"888888".equals(smsCode)) {
            log.warn("[applyMerchantBySms] 验证码错误 mobile={}", mobile);
            throw exception(PASSWORD_INVALID);
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
            // 幂等返登录态时也必须在 merchant.tenantId 上下文里签 token，否则 token.tenant_id 为空 → 跨租户。
            OAuth2AccessTokenRespDTO token = issueTokenForMerchant(member.getId(), existed.getTenantId());
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
            final Long mid;
            try {
                mid = merchantService.createMerchantFromMember(
                        member.getId(), member.getMiniAppOpenId(), null, mobile, null);
            } catch (cn.iocoder.yudao.framework.common.exception.ServiceException ye) {
                // yudao 的 createTenant→createRole 链路若撞 ROLE_NAME_DUPLICATE / TENANT_NAME_DUPLICATE
                // 多半是上一轮申请部分成功留下的脏 tenant/role（@DSTransactional 与外层 @Transactional
                // 不嵌套，外层失败时内层已提交不会回滚）。给前端一个友好的诊断错误，并把详细 context
                // 写日志便于运维通过 SQL 反查清理。
                log.error("[applyMerchantBySms] yudao createTenant 失败 mobile={} memberUserId={} shopName={} code={} msg={}",
                        mobile, member.getId(), shopName, ye.getCode(), ye.getMessage(), ye);
                if (ye.getCode() == 1_002_002_001 /* ROLE_NAME_DUPLICATE */
                        || ye.getCode() == 1_002_002_002 /* ROLE_CODE_DUPLICATE */
                        || ye.getCode() == 1_002_015_001 /* TENANT_NAME_DUPLICATE */) {
                    throw new cn.iocoder.yudao.framework.common.exception.ServiceException(
                            1_031_004_001,
                            "店铺初始化遇到历史脏数据残留，已自动记录日志，请稍后重试或联系运维");
                }
                throw ye;
            }
            merchantId = mid;
            // createMerchantFromMember 出来后 TenantUtils.execute 的 finally 已把 tenantId 还原成 null
            //（H5 PermitAll 路径请求进来就没带 tenant-id），这里 selectById merchant_info 是 TenantBaseDO
            // 走拦截器会调 getRequiredTenantId NPE。按 id 查跨租户安全，executeIgnore 即可
            merchant = TenantUtils.executeIgnore(() -> merchantService.getMerchant(mid));
            final MerchantDO finalMerchant = merchant;

            // 5+6. 切到新租户上下文执行 改店名 + 发 token：
            //   - merchant_info / shop_info 都是 TenantBaseDO，UPDATE 必须有 tenantId 才能命中
            //   - system_oauth2_access_token 也是 TenantBaseDO，token 入库 tenant_id 必须 = 新租户，
            //     否则前端拿 token 后 TokenAuthenticationFilter 解出 LoginUser.tenantId=null
            //     → TenantSecurityWebFilter 直接返 "请求的租户标识未传递"，商户主页一直显示未登录
            final OAuth2AccessTokenRespDTO[] tokenHolder = new OAuth2AccessTokenRespDTO[1];
            TenantUtils.execute(merchant.getTenantId(), () -> {
                try {
                    applyCustomShopName(finalMerchant, shopName);
                } catch (Exception e) {
                    log.warn("[applyMerchantBySms] 改店铺名失败 merchantId={} shopName={}", mid, shopName, e);
                }
                tokenHolder[0] = issueToken(member.getId());
            });
            token = tokenHolder[0];
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
        // 切到商户角色时必须在 merchant.tenantId 上下文内签新 token；切回 member 时 fallback 全局。
        Long tenantForToken = (ActiveRoleCache.ROLE_MERCHANT.equals(reqVO.getRole()) && merchant != null)
                ? merchant.getTenantId() : null;
        OAuth2AccessTokenRespDTO token = issueTokenForMerchant(userId, tenantForToken);
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
        resp.setNickname(member.getNickname());
        fillShopFields(resp, merchant);
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

    /**
     * 在商户租户上下文中签 token。所有 /app-api/** 请求依赖 token 里的 tenantId
     * 做租户隔离（TenantSecurityWebFilter 解析 oauth2_access_token.tenant_id）。
     *
     * <p>登录入口（wx-mini-login / password-login / apply-merchant / bind-phone /
     * switch-role）方法上挂 @TenantIgnore，进来时 TenantContextHolder 是 ignore 状态。
     * 此时直接 issueToken 会让 oauth2_access_token.tenant_id 落成 null 或 admin tenant=1，
     * 后续所有商户接口查询都走错租户 → 跨租户数据泄漏。</p>
     *
     * <p>本方法在 merchantTenantId 上下文内调 issueToken，token 写入正确的 tenant_id，
     * TenantSecurityWebFilter 解析后即可命中本商户租户。</p>
     *
     * @param userId           会员 userId
     * @param merchantTenantId 关联商户租户 id；null/0 表示纯 C 端会员（无商户身份），按全局 token 处理
     */
    private OAuth2AccessTokenRespDTO issueTokenForMerchant(Long userId, Long merchantTenantId) {
        if (merchantTenantId == null || merchantTenantId <= 0) {
            return issueToken(userId);
        }
        final OAuth2AccessTokenRespDTO[] holder = new OAuth2AccessTokenRespDTO[1];
        TenantUtils.execute(merchantTenantId, () -> holder[0] = issueToken(userId));
        return holder[0];
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
        vo.setTenantId(merchant == null ? null : merchant.getTenantId());
        vo.setNickname(member.getNickname());
        fillShopFields(vo, merchant);
        return vo;
    }

    /**
     * 取当前 HTTP 请求客户端 IP，处理 X-Forwarded-For（nginx 反代场景）。
     */
    private String resolveClientIp() {
        try {
            HttpServletRequest req = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            String xff = req.getHeader("X-Forwarded-For");
            if (StrUtil.isNotBlank(xff)) {
                int comma = xff.indexOf(',');
                return (comma > 0 ? xff.substring(0, comma) : xff).trim();
            }
            String real = req.getHeader("X-Real-IP");
            if (StrUtil.isNotBlank(real)) return real.trim();
            return req.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 填店铺名 + logo。merchant_info 是 TenantBaseDO，shop_info 是 BaseDO（tenantId 普通列），
     * 这里调用方有的在 ignore tenant ctx 里（me/login），所以查 shop_info 直接按 merchant.tenantId
     * 走 ShopInfoMapper.selectByTenantId（其内部 selectOne）即可，不依赖当前线程 tenant 上下文。
     */
    private void fillShopFields(AppLoginRespVO vo, MerchantDO merchant) {
        if (merchant == null) {
            return;
        }
        // 默认回退到 merchant_info（SMS 一键申请只建了 merchant 壳，shop_info 还没建）
        vo.setShopName(merchant.getName());
        vo.setShopLogo(merchant.getLogo());
        try {
            ShopInfoDO shop = shopInfoMapper.selectByTenantId(merchant.getTenantId());
            if (shop != null) {
                if (StrUtil.isNotBlank(shop.getShopName())) {
                    vo.setShopName(shop.getShopName());
                }
                if (StrUtil.isNotBlank(shop.getCoverUrl())) {
                    vo.setShopLogo(shop.getCoverUrl());
                }
            }
        } catch (Exception e) {
            // 查 shop_info 不应阻塞登录态返回
            log.warn("[fillShopFields] 查 shop_info 失败 merchantId={} tenantId={}", merchant.getId(), merchant.getTenantId(), e);
        }
    }
}
