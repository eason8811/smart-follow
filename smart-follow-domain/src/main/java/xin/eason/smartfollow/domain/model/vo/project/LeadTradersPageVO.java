package xin.eason.smartfollow.domain.model.vo.project;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 交易员榜单分页数据响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadTradersPageVO {
    /**
     * <b>固定分页版本; 第一页拿到后续必须回传, 以避免翻页期间版本漂移</b>
     * <p>排名数据的版本, 14 位数字, 如: <code>20231010182400</code>, 主要在分页时使用</p>
     * <p>每10分钟生成一版, 仅保留最新的5个版本</p>
     * <p>默认使用最近的版本; 不存在时不会报错, 会使用最近的版本</p>
     */
    private String dataVer;                 // 10min 一版
    /**
     * 总页数
     */
    private int totalPage;                  // 总页数
    /**
     * 项目简单快照列表 <code>List&lt;</code>{@link ProjectBriefVO}<code>&gt;</code>
     */
    private List<ProjectBriefVO> ranks;     // 当前页的项目简表
}
