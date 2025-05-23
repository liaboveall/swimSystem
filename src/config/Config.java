package config;

/**
 * 系统配置类
 * 包含所有系统配置常量
 */
public class Config {
    // 服务器配置
    public static final int DEFAULT_PORT = 8888;
    public static final int SIGNAL_CHECK_INTERVAL = 2000; // 2秒
    public static final int WARNING_TIMEOUT = 10; // 10秒警告
    public static final int DROWNING_TIMEOUT = 30; // 30秒溺水
    
    // 设备配置
    public static final int NUM_DEVICES = 5;
    public static final int LOW_BATTERY_THRESHOLD = 10; // 10%
    public static final int POOL_WIDTH = 500;
    public static final int POOL_HEIGHT = 250;
    
    // 文件路径
    public static final String ALARM_SOUND_FILE_PATH = "src/sounds/alert.mp3";
    
    // 默认登录凭据
    public static final String DEFAULT_USERNAME = "admin";
    public static final String DEFAULT_PASSWORD = "password"; // 将密码更新为 "password"
    
    // UI配置
    public static final int MAIN_WINDOW_WIDTH = 900;
    public static final int MAIN_WINDOW_HEIGHT = 700;
    public static final int LOGIN_WINDOW_WIDTH = 350;
    public static final int LOGIN_WINDOW_HEIGHT = 200;
}
