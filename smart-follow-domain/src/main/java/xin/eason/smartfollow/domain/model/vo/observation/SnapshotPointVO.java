package xin.eason.smartfollow.domain.model.vo.observation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import xin.eason.smartfollow.types.enums.Visibility;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 时序点, 用于曲线渲染或构造查询结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotPointVO {
    /**
     * 时间戳
     */
    private Instant ts;
    /**
     * 带单项目规模
     */
    private BigDecimal aumUsd;
    /**
     * 带单项目跟随者
     */
    private Integer followers;
    /**
     * 带单项目胜率
     */
    private BigDecimal winRatio;
    /**
     * 带单项目收益率 (90天)
     */
    private BigDecimal pnlRatio90d;
    /**
     * 带单项目收益 (90天)
     */
    private BigDecimal pnl90dUsd;
    /**
     * 带单项目可见性
     *
     * @see Visibility
     */
    private Visibility visibility;
    /**
     * 数据来源
     */
    private String source;
}
