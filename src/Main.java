import config.Config;
import java.util.Random;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import models.Device;
import models.DeviceStatus;
import models.Server;
import utils.Logger;

/**
 * 游泳池安全监控系统主类
 * 系统入口点，负责初始化和启动整个系统
 * 
 * @version 2.0
 * @author System
 * @since 2025-05-23
 */
public class Main {
    
    public static void main(String[] args) {
        Logger.info("=== 游泳池安全监控系统启动 ===");
        Logger.info("系统版本: 2.0");
        Logger.info("启动时间: " + java.time.LocalDateTime.now());
        
        // 设置系统外观
        setupLookAndFeel();
        
        // 创建表格模型
        DefaultTableModel tableModel = createTableModel();
        
        // 创建设备实例
        Device[] devices = createDevices(tableModel);
        
        // 创建并启动服务器
        createAndStartServer(devices, tableModel);
        
        Logger.info("=== 系统初始化完成 ===");
    }      /**
     * 设置系统外观
     */
    private static void setupLookAndFeel() {
        try {
            // 尝试使用 FlatLaf 现代外观
            trySetFlatLaf();
        } catch (Exception e) {
            Logger.warning("FlatLaf 外观设置失败，尝试使用系统默认外观: " + e.getMessage());
            // 回退到系统默认外观
            trySetSystemLookAndFeel();
        }
    }
    
    /**
     * 尝试设置 FlatLaf 外观
     */
    private static void trySetFlatLaf() throws Exception {
        try {
            Class<?> flatLightLafClass = Class.forName("com.formdev.flatlaf.FlatLightLaf");
            UIManager.setLookAndFeel((javax.swing.LookAndFeel) flatLightLafClass.getDeclaredConstructor().newInstance());
            Logger.info("FlatLaf 现代外观设置成功");
            
            // 设置一些额外的现代UI属性
            UIManager.put("Button.arc", 8);
            UIManager.put("Component.arc", 8);
            UIManager.put("ProgressBar.arc", 8);
            UIManager.put("TextComponent.arc", 8);
            UIManager.put("TabbedPane.showTabSeparators", true);
            UIManager.put("Table.showHorizontalLines", true);
            UIManager.put("Table.showVerticalLines", false);
            
        } catch (ClassNotFoundException e) {
            throw new Exception("FlatLaf 库未找到，请确保 flatla-3.6.jar 在类路径中", e);
        }
    }
    
    /**
     * 尝试设置系统默认外观
     */
    private static void trySetSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            Logger.info("系统默认外观设置成功");
            
            // 设置一些基本UI属性
            UIManager.put("Button.arc", 4);
            UIManager.put("Component.arc", 4);
            
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            Logger.error("系统外观设置失败，使用 Java 默认外观", e);
        }
    }
    
    /**
     * 创建表格数据模型
     */
    private static DefaultTableModel createTableModel() {
        DefaultTableModel tableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // 表格只读
            }
        };
        
        // 添加列
        tableModel.addColumn("设备ID");
        tableModel.addColumn("电量");
        tableModel.addColumn("位置 (X,Y)");
        tableModel.addColumn("状态");
        
        Logger.info("表格数据模型创建完成");
        return tableModel;
    }
    
    /**
     * 创建设备实例
     */
    private static Device[] createDevices(DefaultTableModel tableModel) {
        Logger.info("开始创建设备实例，数量: " + Config.NUM_DEVICES);
        
        Device[] devices = new Device[Config.NUM_DEVICES];
        Random random = new Random();
        
        for (int i = 0; i < Config.NUM_DEVICES; i++) {
            String deviceId = "Device" + i;
            int initialBattery = random.nextInt(101); // 0-100
            int initialX = random.nextInt(Config.POOL_WIDTH + 1);
            int initialY = random.nextInt(Config.POOL_HEIGHT + 1);
            
            // 为表格添加初始行数据
            Object[] rowData = createInitialRowData(deviceId, initialBattery, initialX, initialY);
            tableModel.addRow(rowData);
            
            // 创建设备实例
            devices[i] = new Device(
                deviceId, 
                initialBattery, 
                initialX, 
                initialY, 
                tableModel, 
                Config.ALARM_SOUND_FILE_PATH
            );
            
            Logger.debug("设备创建: " + deviceId + 
                        ", 电量: " + initialBattery + "%" + 
                        ", 位置: (" + initialX + "," + initialY + ")");
        }
        
        Logger.info("所有设备创建完成");
        return devices;
    }
    
    /**
     * 创建表格初始行数据
     */
    private static Object[] createInitialRowData(String deviceId, int battery, int x, int y) {
        String batteryDisplay = battery + "%";
        String positionDisplay = "(" + x + ", " + y + ")";
        String statusDisplay = battery < Config.LOW_BATTERY_THRESHOLD ? 
            DeviceStatus.LOW_BATTERY.getDisplayName() : DeviceStatus.NORMAL.getDisplayName();
            
        return new Object[]{deviceId, batteryDisplay, positionDisplay, statusDisplay};
    }
    
    /**
     * 创建并启动服务器
     */
    private static void createAndStartServer(Device[] devices, DefaultTableModel tableModel) {
        try {
            // 验证音频文件
            validateAudioFile();
            
            // 创建服务器实例
            Server server = new Server(
                devices, 
                Config.DEFAULT_USERNAME, 
                Config.DEFAULT_PASSWORD, 
                tableModel
            );
            
            Logger.info("服务器实例创建完成");
            Logger.info("默认用户名: " + Config.DEFAULT_USERNAME);
            Logger.info("监听端口: " + Config.DEFAULT_PORT);
            
            // 显示主界面（包含登录）
            server.displayDevices();
            
        } catch (Exception e) {
            Logger.error("创建服务器时发生错误", e);
            showErrorDialog("系统启动失败", "无法创建服务器实例: " + e.getMessage());
            System.exit(1);
        }
    }
    
    /**
     * 验证音频文件是否存在
     */
    private static void validateAudioFile() {
        java.io.File audioFile = new java.io.File(Config.ALARM_SOUND_FILE_PATH);
        if (!audioFile.exists()) {
            Logger.warning("警报音频文件不存在: " + Config.ALARM_SOUND_FILE_PATH);
            Logger.warning("警报功能可能无法正常工作");
        } else {
            Logger.info("警报音频文件验证通过: " + Config.ALARM_SOUND_FILE_PATH);
        }
    }
    
    /**
     * 显示错误对话框
     */
    private static void showErrorDialog(String title, String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(
                null, 
                message, 
                title, 
                JOptionPane.ERROR_MESSAGE
            );
        });
    }
    
    /**
     * 系统关闭钩子
     */
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("=== 系统正在关闭 ===");
            Logger.info("感谢使用游泳池安全监控系统");
        }));
    }
}
