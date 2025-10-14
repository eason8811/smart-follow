package xin.eason.smartfollow.domain.model.aggregate.observation;

import lombok.Getter;
import lombok.ToString;
import lombok.Builder;
import xin.eason.smartfollow.domain.model.vo.project.ProjectKey;
import xin.eason.smartfollow.types.enums.SnapshotSource;
import xin.eason.smartfollow.types.enums.Visibility;
import xin.eason.smartfollow.types.exceptions.IllegalParamException;

import java.math.BigDecimal;
import java.time.Instant;

import static xin.eason.smartfollow.types.utils.FieldValidateUtils.requireNotBlank;
import static xin.eason.smartfollow.types.utils.FieldValidateUtils.requireNotNull;

/**
 * 带单项目时序快照
 */
@Getter
@ToString
public class ProjectSnapshotAggregate {

    private static final long MYSQL_TIMESTAMP_MIN_MS = 1_000L;               // 1970-01-01 00:00:01.000
    private static final long MYSQL_TIMESTAMP_MAX_MS = 2_147_483_647_999L;   // 2038-01-19 03:14:07.999

    // 业务键: 项目唯一标识 + 时间 + 来源 (数据库层面有 uk(project_id, ts, source) 幂等)
    /**
     * 项目唯一标识
     */
    private final ProjectKey projectKey;
    /**
     * 快照时间戳
     */
    private final Instant snapshotTs;
    /**
     * 快照来源
     * @see SnapshotSource
     */
    private final SnapshotSource source;
    /**
     * 数据版本号
     */
    private final String dataVer; // OKX 榜单 14 位版本号, 可空 (其它来源可能没有) 

    /**
     * 可见性状态
     * @see Visibility
     */
    private final Visibility visibility;

    // 常用指标 (可为空, 完整原文放 rawJson)
    /**
     * 项目总带单规模
     */
    private final BigDecimal aumUsd;
    /**
     * 项目跟单者数量
     */
    private final Integer followers;
    /**
     * 项目胜率
     */
    private final BigDecimal winRatio;     // 0.1 = 10%
    /**
     * 项目近90日收益率
     */
    private final BigDecimal pnlRatio90d;  // 近90日收益率
    /**
     * 项目近90日收益 (USDT)
     */
    private final BigDecimal pnl90dUsd;    // 近90日收益 (USDT)

    /**
     * 原始快照 JSON 文本 (按来源保存)
     */
    private final String rawJson;

    /**
     * 项目快照构造方法, 用于创建一个带单项目的时序快照实例
     *
     * @param projectKey   项目唯一标识
     * @param snapshotTs   快照时间戳
     * @param source       快照来源, {@link SnapshotSource}
     * @param dataVer      数据版本号, 对于 OKX 来源, 是一个 14 位的版本号, 其它来源可能为空
     * @param visibility   项目可见性状态 {@link Visibility}
     * @param aumUsd       项目总带单规模, 单位为 USD
     * @param followers    项目跟单者数量
     * @param winRatio     项目胜率, 例如 0.1 表示 10%
     * @param pnlRatio90d  项目近 90 日收益率
     * @param pnl90dUsd    项目近 90 日收益, 单位为 USDT
     * @param rawJson      原始快照 JSON 文本, 按来源保存
     */
    @Builder
    private ProjectSnapshotAggregate(
            ProjectKey projectKey,
            Instant snapshotTs,
            SnapshotSource source,
            String dataVer,
            Visibility visibility,
            BigDecimal aumUsd,
            Integer followers,
            BigDecimal winRatio,
            BigDecimal pnlRatio90d,
            BigDecimal pnl90dUsd,
            String rawJson
    ) {
        requireNotNull(projectKey, "projectKey 不能为空");
        requireNotNull(snapshotTs, "snapshotTs 不能为空");
        requireNotNull(source, "source 不能为空");
        requireNotNull(visibility, "visibility 不能为空");
        requireNotBlank(rawJson, "rawJson 不能为空");

        this.projectKey = projectKey;
        this.snapshotTs = ensureMillis(snapshotTs);
        this.source = source;
        this.dataVer = dataVer;
        this.visibility = visibility;
        this.aumUsd = aumUsd;
        this.followers = followers;
        this.winRatio = winRatio;
        this.pnlRatio90d = pnlRatio90d;
        this.pnl90dUsd = pnl90dUsd;
        this.rawJson = rawJson;
    }

    /**
     * 生成当前项目快照的唯一标识符
     *
     * <p>此方法主要用于日志记录或跨层传递, 通过组合项目键, 快照时间戳和来源生成一个唯一的字符串标识符
     *
     * @return 唯一的项目快照标识符, 格式为 <code>projectKey@timestamp#source</code>, 其中:
     * <ul>
     *     <li><code>projectKey</code> 是 {@link ProjectKey} 的字符串表示形式</li>
     *     <li><code>timestamp</code> 是快照时间戳转换成毫秒数后的字符串表示形式</li>
     *     <li><code>source</code> 是快照来源枚举值的名称, 参见 {@link SnapshotSource}</li>
     * </ul>
     */
    public String snapshotId() {
        // 仅用于日志/跨层传递
        return projectKey.asString() + "@" + snapshotTs.toEpochMilli() + "#" + source.getName();
    }

    /**
     * 确保给定的 <code>Instant</code> 对象的时间精度为毫秒级别
     *
     * @param t 需要处理的 <code>Instant</code> 对象
     * @return 如果输入的 <code>Instant</code> 已经是毫秒级精度, 则直接返回原对象；否则, 返回调整至毫秒级精度后的 <code>Instant</code>
     */
    private static Instant ensureMillis(Instant t) {
        requireNotNull(t, "snapshotTs 不能为空");
        long ms = t.toEpochMilli(); // 统一毫秒
        if (ms < MYSQL_TIMESTAMP_MIN_MS || ms > MYSQL_TIMESTAMP_MAX_MS)
            throw IllegalParamException.of("snapshotTs 超出可存储范围: " + t);
        return Instant.ofEpochMilli(ms);
    }
}
