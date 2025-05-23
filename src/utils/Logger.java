package utils;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 简单的日志工具类
 */
public class Logger {
    private static final String LOG_FILE = "system.log";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public enum Level {
        INFO, WARNING, ERROR, DEBUG
    }
    
    /**
     * 记录信息
     */
    public static void info(String message) {
        log(Level.INFO, message);
    }
    
    /**
     * 记录警告
     */
    public static void warning(String message) {
        log(Level.WARNING, message);
    }
    
    /**
     * 记录错误
     */
    public static void error(String message) {
        log(Level.ERROR, message);
    }
    
    /**
     * 记录错误（带异常）
     */
    public static void error(String message, Throwable throwable) {
        log(Level.ERROR, message + " - " + throwable.getMessage());
    }
    
    /**
     * 记录调试信息
     */
    public static void debug(String message) {
        log(Level.DEBUG, message);
    }
    
    /**
     * 通用日志记录方法
     */
    private static void log(Level level, String message) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String logMessage = String.format("[%s] %s: %s", timestamp, level, message);
        
        // 控制台输出
        System.out.println(logMessage);
        
        // 文件输出
        try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            writer.println(logMessage);
        } catch (IOException e) {
            System.err.println("写入日志文件失败: " + e.getMessage());
        }
    }
}
