package interfaces;

import utils.Logger;
import jmp123.PlayBack;
import java.io.File;
import java.io.IOException;

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
            PlayBack playBack = new PlayBack(new jmp123.output.Audio());
            playBack.open(soundFilePath, "");
            playBack.start(true);
            
        } catch (IOException e) {
            Logger.error("播放警报声音失败 (IOException)", e);
        } catch (Exception e) {
            Logger.error("警报播放期间发生意外错误", e);
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
