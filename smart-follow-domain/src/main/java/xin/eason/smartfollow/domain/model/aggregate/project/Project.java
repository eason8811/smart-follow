package xin.eason.smartfollow.domain.model.aggregate.project;

import lombok.Getter;
import lombok.ToString;
import xin.eason.smartfollow.domain.model.vo.project.ProjectBriefVO;
import xin.eason.smartfollow.domain.model.vo.project.ProjectKey;
import xin.eason.smartfollow.types.enums.Visibility;
import xin.eason.smartfollow.types.exceptions.AppException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * 项目的主档聚合根
 */
@Getter
@ToString
public class Project {

    /**
     * @see ProjectKey
     */
    private final ProjectKey key;

    // 基本信息
    /**
     * 项目名称
     */
    private String name;
    /**
     * 结算币种
     */
    private String baseCurrency;

    // 观察状态
    /**
     * 最后一次可见状态
     */
    private Visibility lastVisibility;
    /**
     * 第一次可见时间
     */
    private Instant firstSeen;
    /**
     * 最后一次课间时间
     */
    private Instant lastSeen;

    // 详情补充
    /**
     * 最小跟单数额
     */
    private BigDecimal minCopyCost; // 可空
    /**
     * 项目运行状态
     */
    private String status;          // 可空
    /**
     * 额外 JSON 字符串
     */
    private String extra;           // 原始 JSON 字符串

    /**
     * 工厂: 从榜单简表创建 首次发现 的项目
     *
     * @param key   项目唯一标识
     * @param brief 项目简单快照
     * @param now   当前时间
     * @return 项目聚合对象
     */
    public static Project newFromBrief(ProjectKey key, ProjectBriefVO brief, Instant now) {
        requireNotNull(key, "key 不能为空");
        requireNotBlank(brief.getExternalId(), "externalId 不能为空");
        String name = (brief.getName() == null || brief.getName().isBlank()) ? key.getExternalId() : brief.getName();
        String ccy = (brief.getBaseCurrency() == null || brief.getBaseCurrency().isBlank())
                ? "USDT" : brief.getBaseCurrency().toUpperCase();

        Project p = new Project(key);
        p.name = name;
        p.baseCurrency = ccy;
        p.lastVisibility = Visibility.VISIBLE;
        p.firstSeen = now;
        p.lastSeen = now;
        p.extra = brief.getRawJson();
        return p;
    }

    /**
     * 吸收榜单变更：更新可见性/最近看到/名称/币种/extra
     */
    public void applyBrief(ProjectBriefVO brief, Instant now) {
        requireNotNull(brief, "brief 不能为空");
        this.lastVisibility = Visibility.VISIBLE;
        if (brief.getName() != null && !brief.getName().isBlank()) this.name = brief.getName();
        if (brief.getBaseCurrency() != null && !brief.getBaseCurrency().isBlank())
            this.baseCurrency = brief.getBaseCurrency().toUpperCase();
        this.lastSeen = ensureMonotonic(this.lastSeen, now);
        if (brief.getRawJson() != null && !brief.getRawJson().isBlank()) this.extra = brief.getRawJson();
    }

    /**
     * 吸收详情补充：最小复制金额 / 业务状态 / 额外 JSON
     */
    public void applyDetail(BigDecimal minCopyCost, String status, String extraJson) {
        if (minCopyCost != null) {
            if (minCopyCost.signum() < 0) throw AppException.of("minCopyCost 不能为负数");
            this.minCopyCost = minCopyCost;
        }
        if (status != null && !status.isBlank()) this.status = status;
        if (extraJson != null && !extraJson.isBlank()) this.extra = extraJson;
    }

    public void markMissing() {
        this.lastVisibility = Visibility.MISSING;
    }

    public void restoreVisible(Instant now) {
        this.lastVisibility = Visibility.VISIBLE;
        this.lastSeen = ensureMonotonic(this.lastSeen, now);
    }

    public void rename(String newName) {
        requireNotBlank(newName, "name 不能为空");
        this.name = newName;
    }

    public void changeBaseCurrency(String newCcy) {
        requireNotBlank(newCcy, "baseCurrency 不能为空");
        this.baseCurrency = newCcy.toUpperCase();
    }

    private Project(ProjectKey key) {
        this.key = key;
    }

    public String projectId() {
        return key.asString();
    }

    // ===== 私有校验/助手 =====
    private static void requireNotBlank(String s, String msg) {
        if (s == null || s.isBlank()) throw AppException.of(msg);
    }

    private static void requireNotNull(Object o, String msg) {
        if (Objects.isNull(o)) throw AppException.of(msg);
    }

    private static Instant ensureMonotonic(Instant oldTs, Instant now) {
        if (oldTs == null) return now;
        return now.isAfter(oldTs) ? now : oldTs;
    }
}
