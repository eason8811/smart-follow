package xin.eason.smartfollow.types.enums;

import xin.eason.smartfollow.types.exceptions.AppException;

/**
 * 项目可见性状态:
 * <ul>
 *     <li>VISIBLE: 本次采集可见</li>
 *     <li>MISSING: 短期不可见 (如最近若干版 dataVer 缺席) </li>
 *     <li>HIDDEN : 被隐藏/下线 (详情接口明确或长期缺席确认) </li>
 * </ul>
 */
public enum Visibility {
    VISIBLE, MISSING, HIDDEN;

    /**
     * 获取枚举值的 Name 用于持久化
     *
     * @return 枚举值名称
     */
    public String getName() {
        return this.name();
    }

    /**
     * 判断当前项目是否可见
     *
     * @return 可见性
     */
    public boolean isVisible() {
        return this == VISIBLE;
    }

    /**
     * 将给定字符串解析为 Visibility 枚举值, 非法值抛 AppException
     *
     * @param string 字符串值
     * @return Visibility 枚举值
     */
    public static Visibility parse(String string) {
        if (string == null || string.isBlank())
            throw AppException.of("visibility 不能为空");
        return switch (string.trim().toUpperCase()) {
            case "VISIBLE" -> VISIBLE;
            case "MISSING" -> MISSING;
            case "HIDDEN" -> HIDDEN;
            default -> throw AppException.of("非法的 visibility 值: " + string);
        };
    }
}
