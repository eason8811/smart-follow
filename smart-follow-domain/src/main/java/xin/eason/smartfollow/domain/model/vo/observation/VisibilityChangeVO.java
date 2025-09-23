package xin.eason.smartfollow.domain.model.vo.observation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import xin.eason.smartfollow.types.enums.Visibility;

import java.time.Instant;

/**
 * 可见性变化事件
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VisibilityChangeVO {
    /**
     * 从某个状态开始变化
     *
     * @see Visibility
     */
    private Visibility from;
    /**
     * 变化到某个状态
     *
     * @see Visibility
     */
    private Visibility to;
    /**
     * 发生时间
     */
    private Instant atTs;
    /**
     * 原因代码
     */
    private String reasonCode;
    /**
     * 变化原因
     */
    private String reasonMsg;
}
