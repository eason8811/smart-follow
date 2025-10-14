package xin.eason.smartfollow.domain.model.vo.project;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 项目简单快照, 领航员榜单的最小子集 (不做业务约束, 仅做承载与传递)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectBriefVO {
    /**
     * 交易员唯一标识码 (来源于 OKX API)
     */
    private String externalId;        // OKX: ranks[].uniqueCode
    /**
     * 项目名称
     */
    private String name;              // ranks[].nickName
    /**
     * 结算币种
     */
    private String baseCurrency;      // ranks[].ccy, 缺省用 USDT
    /**
     * 项目总带单规模
     */
    private BigDecimal aum;           // ranks[].aum (可空)
    /**
     * 项目跟随者数量
     */
    private Integer followers;        // ranks[].copyTraderNum (可空)
    /**
     * 项目数据版本
     */
    private String dataVer;           // data[].dataVer
    /**
     * 项目原始 JSON 数据
     */
    private String rawJson;           // 原始 ranks[] JSON (字符串), 用于落到 Project.extra
}
