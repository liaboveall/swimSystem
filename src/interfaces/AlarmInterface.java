package interfaces;

import java.io.File;
import utils.Logger;

/**
 * 警报接口
 * 提供声音警报功能
 */
public interface AlarmInterface {
    
    /**
     * 播放警报声音
     * @param soundFilePath 声音文件路径
     */
    static void playWarning(String soundFilePath) {
        try {
            File soundFile = new File(soundFilePath);
            if (!soundFile.exists() || soundFile.isDirectory()) {
                Logger.error("警报声音文件未找到或为目录: " + soundFilePath);
                return;
            }
            
            Logger.info("播放警报声音: " + soundFilePath);
            
            // 尝试使用 jmp123 库播放音频
            try {
                Class<?> playBackClass = Class.forName("jmp123.PlayBack");
                Class<?> audioClass = Class.forName("jmp123.output.Audio");
                
                Object audio = audioClass.getDeclaredConstructor().newInstance();
                Object playBack = playBackClass.getDeclaredConstructor(audioClass).newInstance(audio);
                
                // 调用 open 和 start 方法
                playBackClass.getMethod("open", String.class, String.class).invoke(playBack, soundFilePath, "");
                playBackClass.getMethod("start", boolean.class).invoke(playBack, true);
                
                Logger.info("使用 jmp123 库成功播放警报声音");
                  } catch (ClassNotFoundException e) {
                Logger.warning("jmp123 库未找到，警报声音功能不可用");
                Logger.warning("请确保 jmp123.jar 在类路径中");
                // 可以在这里添加其他的音频播放方式作为后备
                playAlternativeWarning();
            } catch (ReflectiveOperationException | IllegalArgumentException e) {
                Logger.error("使用 jmp123 播放音频时发生错误", e);
                playAlternativeWarning();
            }
            
        } catch (SecurityException | IllegalArgumentException e) {
            Logger.error("警报播放期间发生错误", e);
        }
    }
    
    /**
     * 备用警报方式（当音频库不可用时）
     */
    static void playAlternativeWarning() {
        try {
            // 使用系统蜂鸣声作为备用
            java.awt.Toolkit.getDefaultToolkit().beep();
            Logger.info("播放系统蜂鸣声作为警报");
        } catch (Exception e) {
            Logger.error("播放备用警报失败", e);
        }
    }
    
    /**
     * 非阻塞方式播放警报
     * @param soundFilePath 声音文件路径
     */
    static void playWarningNonBlocking(String soundFilePath) {
        new Thread(() -> playWarning(soundFilePath)).start();
    }
}
