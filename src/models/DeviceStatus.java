package models;

/**
 * 设备状态枚举
 */
public enum DeviceStatus {
    NORMAL("正常", "#000000"),      // 正常状态，黑色
    LOW_BATTERY("电量低", "#FFA500"),  // 电量低，橙色
    WARNING("警告", "#FFFF00"),     // 警告状态，黄色
    DROWNING("溺水", "#FF0000");   // 溺水状态，红色
    
    private final String displayName;
    private final String colorCode;
    
    DeviceStatus(String displayName, String colorCode) {
        this.displayName = displayName;
        this.colorCode = colorCode;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getColorCode() {
        return colorCode;
    }
    
    /**
     * 获取HTML格式的显示文本
     */
    public String getHtmlDisplayText() {
        if (this == NORMAL) {
            return displayName;
        }
        return String.format("<html><font color='%s'>%s</font></html>", colorCode, displayName);
    }
}
