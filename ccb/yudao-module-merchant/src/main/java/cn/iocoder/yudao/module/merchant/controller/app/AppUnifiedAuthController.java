package cn.iocoder.yudao.module.merchant.controller.app;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCreateReqDTO;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.yudao.framework.common.enums.TerminalEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.merchant.controller.app.vo.auth.AppApplyMerchantReqVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.auth.AppBindPhoneReqVO;
import cn.iocoder.yudao.module.merchant.controller.app.vo.auth.AppLoginRespVO;
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
