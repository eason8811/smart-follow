package xin.eason.smartfollow.domain.model.aggregate.project;

import lombok.Getter;
import lombok.ToString;
import xin.eason.smartfollow.domain.model.vo.project.ProjectBriefVO;
import xin.eason.smartfollow.domain.model.vo.project.ProjectKey;
import xin.eason.smartfollow.types.enums.Visibility;
import xin.eason.smartfollow.types.exceptions.AppException;

import java.math.BigDecimal;
import java.time.Instant;

import static xin.eason.smartfollow.types.utils.FieldValidateUtils.requireNotBlank;
import static xin.eason.smartfollow.types.utils.FieldValidateUtils.requireNotNull;

/**
 * 项目的主档聚合根
 */
@Getter
@ToString
public class ProjectAggregate {

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
     * 最后一次可见时间
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
     * 工厂: 从项目简单快照创建 首次发现 的项目
     *
     * @param key   项目唯一标识
     * @param brief 项目简单快照
     * @param now   当前时间
     * @return 项目聚合对象
     */
    public static ProjectAggregate newFromBrief(ProjectKey key, ProjectBriefVO brief, Instant now) {
        requireNotNull(key, "key 不能为空");
        requireNotBlank(brief.getExternalId(), "externalId 不能为空");
        String name = (brief.getName() == null || brief.getName().isBlank()) ? key.externalId() : brief.getName();
        String currency = (brief.getBaseCurrency() == null || brief.getBaseCurrency().isBlank())
                ? "USDT" : brief.getBaseCurrency().toUpperCase();

        ProjectAggregate project = new ProjectAggregate(key);
        project.name = name;
        project.baseCurrency = currency;
        project.lastVisibility = Visibility.VISIBLE;
        project.firstSeen = now;
        project.lastSeen = now;
        project.extra = brief.getRawJson();
        return project;
    }

    /**
     * 从项目快照 {@link ProjectBriefVO} 中添加更详细的信息
     *
     * @param brief 项目简单快照
     * @param now   当前时间
     */
    public void addDetailFromBrief(ProjectBriefVO brief, Instant now) {
        requireNotNull(brief, "brief 不能为空");
        this.lastVisibility = Visibility.VISIBLE;
        if (brief.getName() != null && !brief.getName().isBlank())
            this.name = brief.getName();
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
            if (minCopyCost.signum() < 0)
                throw AppException.of("minCopyCost 不能为负数");
            this.minCopyCost = minCopyCost;
        }
        if (status != null && !status.isBlank())
            this.status = status;
        if (extraJson != null && !extraJson.isBlank())
            this.extra = extraJson;
    }

    /**
     * 标记项目丢失
     */
    public void markMissing() {
        this.lastVisibility = Visibility.MISSING;
    }

    /**
     * 恢复可见性
     *
     * @param now 当前时间
     */
    public void restoreVisible(Instant now) {
        this.lastVisibility = Visibility.VISIBLE;
        this.lastSeen = ensureMonotonic(this.lastSeen, now);
    }

    /**
     * 修改项目名称
     *
     * @param newName 新的项目名称
     */
    public void rename(String newName) {
        requireNotBlank(newName, "name 不能为空");
        this.name = newName;
    }

    /**
     * 修改结算币种
     *
     * @param newBaseCurrency 新的结算币种
     */
    public void changeBaseCurrency(String newBaseCurrency) {
        requireNotBlank(newBaseCurrency, "baseCurrency 不能为空");
        this.baseCurrency = newBaseCurrency.toUpperCase();
    }

    /**
     * 私有构造函数, 禁止外部创建实例, 必须通过工厂方法 {@link #newFromBrief} 创建实例
     *
     * @param key 项目唯一标识对象
     */
    private ProjectAggregate(ProjectKey key) {
        this.key = key;
    }

    /**
     * 获取系统内的项目 ID (格式: {@code 交易所名称:外部 ID})
     *
     * @return 系统内的项目 ID
     * @see ProjectKey#asString()
     */
    public String projectId() {
        return key.asString();
    }

    /**
     * 确保时间戳是单调递增的
     * <p>如果 {@code oldTs} 缺失或 {@code now < oldTs}, 则返回 {@code now}</p>
     *
     * @param oldTs 旧时间戳
     * @param now   当前时间戳
     * @return 单调递增的时间戳
     */
    private static Instant ensureMonotonic(Instant oldTs, Instant now) {
        if (oldTs == null)
            return now;
        return now.isAfter(oldTs) ? now : oldTs;
    }
}
