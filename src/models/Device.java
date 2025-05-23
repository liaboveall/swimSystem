package models;

import config.Config;
import interfaces.AlarmInterface;
import java.util.Random;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import utils.Logger;

/**
 * 设备类
 * 模拟游泳池中的可穿戴设备
 */
public class Device implements Runnable, AlarmInterface {
    private final String id;
    private volatile int battery;
    private volatile DeviceStatus status;
    private volatile int x;
    private volatile int y;
    private volatile long lastSignalTime;
    private final DefaultTableModel tableModel;
    private final Random random;
    private final String soundFilePath;
    private volatile boolean running = true;

    public Device(String id, int initialBattery, int initialX, int initialY, 
                  DefaultTableModel tableModel, String soundFilePath) {
        this.id = id;
        this.battery = initialBattery;
        this.x = initialX;
        this.y = initialY;
        this.lastSignalTime = System.currentTimeMillis();
        this.tableModel = tableModel;
        this.random = new Random();
        this.soundFilePath = soundFilePath;
        
        // 初始状态判断
        this.status = determineInitialStatus();
        
        Logger.info("设备创建: " + id + ", 初始电量: " + battery + "%, 初始位置: (" + x + "," + y + ")");
        updateTable();
    }

    // Getters
    public String getId() { return id; }
    public synchronized int getBattery() { return battery; }
    public synchronized DeviceStatus getStatus() { return status; }
    public synchronized int getX() { return x; }
    public synchronized int getY() { return y; }
    public synchronized boolean isDrown() { return status == DeviceStatus.DROWNING; }

    /**
     * 停止设备运行
     */
    public void stop() {
        running = false;
        Logger.info("设备 " + id + " 停止运行");
    }

    @Override
    public void run() {
        Logger.info("设备 " + id + " 开始运行");
        
        while (running) {
            try {
                checkDeviceState();
            } catch (Exception e) { // 更通用的异常捕获
                Logger.error("设备 " + id + " 运行错误", e);
            }
        }
        
        Logger.info("设备 " + id + " 线程结束");
    }

    /**
     * 从客户端更新设备状态
     */
    public synchronized void updateStateFromClient(int newBattery, int newX, int newY) {
        this.lastSignalTime = System.currentTimeMillis();
        this.battery = newBattery;
        this.x = newX;
        this.y = newY;

        // 如果不是溺水状态，重新判断状态
        if (this.status != DeviceStatus.DROWNING) {
            this.status = determineStatusFromBattery();
        }
        
        Logger.debug("设备 " + id + " 由客户端更新: 电量=" + battery + "%, 位置=(" + x + "," + y + "), 状态=" + status);
        updateTable();
    }

    /**
     * 定期检查设备状态
     */
    private synchronized void checkDeviceState() {
        long currentTime = System.currentTimeMillis();
        long signalInterval = (currentTime - lastSignalTime) / 1000;
        DeviceStatus previousStatus = this.status;
        boolean positionChanged = false;

        // 检查信号丢失情况
        if (signalInterval >= Config.DROWNING_TIMEOUT) {
            if (this.status != DeviceStatus.DROWNING) {
                this.status = DeviceStatus.DROWNING;
                Logger.warning("设备 " + id + " 信号丢失 " + signalInterval + "秒，状态变为溺水");
                AlarmInterface.playWarningNonBlocking(this.soundFilePath);
            }
        } else if (signalInterval >= Config.WARNING_TIMEOUT) {
            if (this.status != DeviceStatus.DROWNING && this.status != DeviceStatus.WARNING) {
                if (battery >= Config.LOW_BATTERY_THRESHOLD) {
                    this.status = DeviceStatus.WARNING;
                }
            }
        } else {
            // 信号正常，模拟设备移动
            if (signalInterval >= 5 && signalInterval < Config.DROWNING_TIMEOUT) {
                simulateMovement();
                positionChanged = true;
            }

            // 根据电量更新状态（如果不是溺水状态）
            if (this.status != DeviceStatus.DROWNING) {
                this.status = determineStatusFromBattery();
            }
        }

        // 如果状态或位置发生变化，更新表格
        if (previousStatus != this.status || positionChanged) {
            updateTable();
        }
    }

    /**
     * 模拟设备移动
     */
    private void simulateMovement() {
        this.x = random.nextInt(Config.POOL_WIDTH + 1);
        this.y = random.nextInt(Config.POOL_HEIGHT + 1);
    }

    /**
     * 根据电量确定设备状态
     */
    private DeviceStatus determineStatusFromBattery() {
        return battery < Config.LOW_BATTERY_THRESHOLD ? DeviceStatus.LOW_BATTERY : DeviceStatus.NORMAL;
    }

    /**
     * 确定初始状态
     */
    private DeviceStatus determineInitialStatus() {
        return determineStatusFromBattery();
    }

    /**
     * 更新表格显示
     */
    private void updateTable() {
        final String currentId = this.id;
        final int currentBattery = this.getBattery();
        final int currentX = this.getX();
        final int currentY = this.getY();
        final DeviceStatus currentStatus = this.getStatus();

        SwingUtilities.invokeLater(() -> {
            String batteryText = formatBatteryDisplay(currentBattery, currentStatus);
            String statusText = currentStatus.getHtmlDisplayText();

            int rowIndex = findRowIndex(currentId);
            if (rowIndex != -1) {
                tableModel.setValueAt(batteryText, rowIndex, 1);
                tableModel.setValueAt("(" + currentX + ", " + currentY + ")", rowIndex, 2);
                tableModel.setValueAt(statusText, rowIndex, 3);
            } else {
                Logger.error("在表格中未找到设备 ID: " + currentId);
            }
        });
    }

    /**
     * 格式化电量显示
     */
    private String formatBatteryDisplay(int battery, DeviceStatus status) {
        String batteryText = battery + "%";
        if (status == DeviceStatus.DROWNING || status == DeviceStatus.LOW_BATTERY) {
            return String.format("<html><font color='%s'>%s</font></html>", 
                               status.getColorCode(), batteryText);
        } else if (status == DeviceStatus.WARNING) {
            return String.format("<html><font color='%s'>%s</font></html>", 
                               DeviceStatus.WARNING.getColorCode(), batteryText);
        }
        return batteryText;
    }

    /**
     * 查找设备在表格中的行索引
     */
    private int findRowIndex(String deviceId) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (tableModel.getValueAt(i, 0).equals(deviceId)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 模拟信号阻塞
     */
    public void blockSignal() {
        Logger.info("请求阻塞设备 " + this.id + " 的信号");
        
        new Thread(() -> {
            try {
                Logger.info("设备 " + id + " 正在模拟信号阻塞...");
                Thread.sleep(5000);

                synchronized (this) {
                    this.status = DeviceStatus.DROWNING;
                    this.lastSignalTime = System.currentTimeMillis() - (Config.DROWNING_TIMEOUT + 5) * 1000L;
                    Logger.warning("设备 " + id + " 状态被手动设置为溺水");
                }
                
                AlarmInterface.playWarningNonBlocking(this.soundFilePath);
                updateTable();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Logger.error("设备 " + id + " blockSignal 线程被中断");
            }
        }).start();
    }
}
