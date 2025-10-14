package xin.eason.smartfollow.domain.model.aggregate.observation;

import lombok.Getter;
import lombok.ToString;
import xin.eason.smartfollow.domain.model.vo.project.ProjectKey;
import xin.eason.smartfollow.types.exceptions.AppException;

import java.time.Instant;

import static xin.eason.smartfollow.types.utils.FieldValidateUtils.requireNotNull;

/**
 * 带单项目墓碑
 * <ul>
 *     <li>当项目不可见时打开墓碑</li>
 *     <li>当项目重新可见时关闭墓碑</li>
 *     <li><b>同一个项目在同一时间只能有一个打开的墓碑</b></li>
 * </ul>
 */
@Getter
@ToString
public class TombstoneAggregate {

    /**
     * 项目唯一标识
     */
    private final ProjectKey projectKey;
    /**
     * 开始不可见的时间
     */
    private final Instant fromTs;
    /**
     * 不可见状态结束时间 (为空则表示仍处于不可见)
     */
    private Instant toTs;
    /**
     * 不可见原因代码
     */
    private final String reasonCode;
    /**
     * 不可见原因
     */
    private final String reasonMsg;
    /**
     * 触发来源
     */
    private final String detector;  // 触发来源 (如 OBSERVATION / RANK_GAP / DETAIL_4XX) 

    /**
     * 创建一个新的 <code>Tombstone</code> 实例, 用于标记项目进入不可见状态
     *
     * @param key        项目唯一标识, 不能为空
     * @param fromTs     开始不可见的时间, 不能为空
     * @param reasonCode 不可见原因代码, 如果为空或空白, 则默认为 "UNKNOWN"
     * @param reasonMsg  不可见的原因描述
     * @param detector   触发来源, 如 OBSERVATION / RANK_GAP / DETAIL_4XX, 如果为空, 则默认为 "OBSERVATION"
     */
    private TombstoneAggregate(ProjectKey key, Instant fromTs, String reasonCode, String reasonMsg, String detector) {
        requireNotNull(key, "projectKey 不能为空");
        requireNotNull(fromTs, "fromTs 不能为空");
        this.projectKey = key;
        this.fromTs = fromTs;
        this.reasonCode = (reasonCode == null || reasonCode.isBlank()) ? "UNKNOWN" : reasonCode;
        this.reasonMsg = reasonMsg;
        this.detector = detector == null ? "OBSERVATION" : detector;
    }

    /**
     * 创建一个新的 <code>Tombstone</code> 实例, 用于标记项目进入不可见状态
     *
     * @param key        项目唯一标识, 不能为空
     * @param fromTs     开始不可见的时间, 不能为空
     * @param reasonCode 不可见原因代码, 如果为空或空白, 则默认为 "UNKNOWN"
     * @param reasonMsg  不可见的原因描述
     * @param detector   触发来源, 如 OBSERVATION / RANK_GAP / DETAIL_4XX, 如果为空, 则默认为 "OBSERVATION"
     * @return 新的 <code>Tombstone</code> 对象
     */
    public static TombstoneAggregate open(ProjectKey key, Instant fromTs, String reasonCode, String reasonMsg, String detector) {
        return new TombstoneAggregate(key, fromTs, reasonCode, reasonMsg, detector);
    }

    /**
     * 检查当前墓碑是否处于打开状态, 即项目是否仍然不可见
     *
     * @return 如果项目仍处于不可见状态, 则返回 <code>true</code>; 否则返回 <code>false</code>
     */
    public boolean isOpen() {
        return this.toTs == null;
    }

    /**
     * 结束当前墓碑, 标记项目重新变为可见状态
     *
     * @param toTs 项目重新变得可见的时间点, 必须晚于 {@code fromTs}
     * @throws AppException 如果尝试关闭一个已经关闭的墓碑, 或者提供的 {@code toTs} 不晚于 {@code fromTs}, 将抛出此异常
     */
    public void close(Instant toTs) {
        requireNotNull(toTs, "toTs 不能为空");
        if (!isOpen())
            throw AppException.of("墓碑已经关闭, 不能重复关闭");
        if (!toTs.isAfter(fromTs))
            throw AppException.of("toTs 必须晚于 fromTs");
        this.toTs = toTs;
    }
}
