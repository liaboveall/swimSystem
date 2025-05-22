package com.swimsystem;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;
import jmp123.PlayBack;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


// 设备类
enum DeviceStatus {
    NORMAL, // 设备状态正常
    LOW_BATTERY, // 设备电量低
    DROWNING // 设备溺水
}

//警报接口
interface AlarmInterface {
    public static void playWarning() throws IOException {
        PlayBack playBack = new PlayBack(new jmp123.output.Audio());
        playBack.open("C:\\Users\\Mars\\Desktop\\123.mp3", "");
        playBack.start(true); // 播放警报声音
    }
}

// 设备类
class Device implements Runnable ,AlarmInterface {
    String id; // 设备ID
    int battery; // 设备电量
    DeviceStatus status; // 设备状态
    int x; // 设备位置x坐标
    int y; // 设备位置y坐标
    long lastSignalTime; // 上次信号时间
    long signalInterval; // 信号间隔时间
    DefaultTableModel tableModel; // 表格模型
    Random random; // 随机数生成器

    public Device(String id, int battery, int x, int y, DefaultTableModel tableModel) {
        this.id = id;
        this.battery = battery;
        this.x = x;
        this.y = y;
        this.lastSignalTime = System.currentTimeMillis();
        this.signalInterval = 0;
        this.status = DeviceStatus.NORMAL;
        this.tableModel = tableModel;
        this.random = new Random();
    }

    public String getId() {
        return id;
    }

    public int getBattery() {
        return battery;
    }

    public DeviceStatus getStatus() {
        return status;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void run() {
        while (true) {
            updateSignal();
            try {
                Thread.sleep(2000); // 每秒更新一次信号
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void updateSignal() {
        long currentTime = System.currentTimeMillis();
        signalInterval = (currentTime - lastSignalTime) / 1000;
        if (signalInterval >= 20) { // 如果信号间隔时间大于等于20秒
            lastSignalTime = currentTime;
            x = random.nextInt(51); // 生成0到50之间的随机数
            y = random.nextInt(22); // 生成0到21之间的随机数
            battery = battery - 1; // 电量减1
            if (battery < 0) { // 如果电量小于0
                battery = 0; // 电量为0
            }
            updateTable(); // 更新表格
        }

        if (signalInterval > 30) { // 如果信号间隔时间大于30秒
            status = DeviceStatus.DROWNING; // 设备状态为溺水
            // 发出警报
            try {
                Thread.sleep(20000); // 阻塞20秒
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            new Thread(() -> {
                try {
                    AlarmInterface.playWarning();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        } else if (signalInterval > 10) { // 如果信号间隔时间大于10秒
            status = DeviceStatus.NORMAL; // 设备状态为正常
            // 发出警告
            try {
                Thread.sleep(10000); // 阻塞10秒

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
            status = DeviceStatus.NORMAL; // 设备状态为正常
        }
    }

    public boolean isDrown() {
        return status == DeviceStatus.DROWNING;
    }

    private void updateTable() {
        Object[] rowData = new Object[4];
        rowData[0] = id;
        rowData[1] = battery + "%";
        rowData[2] = "(" + x + ", " + y + ")";
        if (isDrown()) {
            rowData[3] = "Drowning";
        } else if (battery < 10) {
            rowData[3] = "Low Battery";
            rowData[1] = "<html><font color='yellow'>" + battery + "%</font></html>"; // 将电量文本设置为黄色
            rowData[3] = "<html><font color='yellow'>" + status + "</font></html>";
        } else {
            rowData[3] = "Normal";
        }
        int rowIndex = Integer.parseInt(id.substring(6)); // 设备ID为"DeviceX"，X为数字
        tableModel.setValueAt(rowData[1], rowIndex, 1); // 更新电量列
        tableModel.setValueAt(rowData[2], rowIndex, 2); // 更新位置列
        tableModel.setValueAt(rowData[3], rowIndex, 3); // 更新状态列
    }


    public void blockSignal() {
        // 阻塞信号
        try {
            Thread.sleep(30000); // 阻塞20秒
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 创建新进程播放警报
        new Thread(() -> {
            try {
                AlarmInterface.playWarning();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        // 改变设备状态
        status = DeviceStatus.DROWNING;
        updateTable();

        // 将第一个设备的状态修改为溺水并在表格显示中标红
        if (id.equals("Device0")) {
            status = DeviceStatus.DROWNING;
            tableModel.setValueAt("<html><font color='red'>" + status + "</font></html>", 0, 3);
        }
    }
}




// 服务器类
class Server {
    private Device[] devices; // 设备数组
    private String username; // 用户名
    private String password; // 密码
    private DefaultTableModel tableModel; // 表格模型

    public Server(Device[] devices, String username, String password, DefaultTableModel tableModel) {
        this.devices = devices;
        this.username = username;
        this.password = password;
        this.tableModel = tableModel;
    }

    public boolean login(String username, String password) {
        if (this.username.equals(username) && this.password.equals(password)) { // 如果用户名和密码匹配
            return true;
        } else {
            return false;
        }
    }

    public void displayDevices() throws IOException {
        JFrame frame = new JFrame("Swimming Pool Safety System"); // 创建窗口
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);

        // 创建表格
        String[] columnNames = {"Device ID", "Battery", "Position", "Status"}; // 表格列名
        Object[][] data = new Object[devices.length][4]; // 表格数据
        for (int i = 0; i < devices.length; i++) {
            data[i][0] = devices[i].getId(); // 设备ID
            data[i][1] = devices[i].getBattery() + "%"; // 设备电量
            data[i][2] = "(" + devices[i].getX() + ", " + devices[i].getY() + ")"; // 设备位置
            if (devices[i].isDrown()) { // 如果设备溺水
                data[i][3] = "Drowning"; // 设备状态为溺水
            } else if (devices[i].getBattery() < 10) { // 如果设备电量低于10%
                data[i][3] = "Low Battery"; // 设备状态为电量低
            } else {
                data[i][3] = "Normal"; // 设备状态为正常
            }
            tableModel.addRow(data[i]); // 添加行到表格
            new Thread(devices[i]).start(); // 创建线程并启动
        }
        JTable table = new JTable(tableModel); // 创建表格
        JScrollPane scrollPane = new JScrollPane(table); // 创建滚动面板
        frame.add(scrollPane, BorderLayout.CENTER); // 将滚动面板添加到窗口中心

        // 创建阻断信号按钮
        JButton blockSignalButton = new JButton("Block Signal");
        blockSignalButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                devices[0].blockSignal(); // 阻断第一个设备的信号
                
            }
        });
        frame.add(blockSignalButton, BorderLayout.SOUTH); // 将按钮添加到窗口底部


        // 创建登录对话框
        JDialog loginDialog = new JDialog(frame, "Login", true); // 创建对话框
        loginDialog.setSize(300, 200);
        loginDialog.setLocationRelativeTo(frame);
        JPanel loginPanel = new JPanel(new GridLayout(3, 2)); // 创建面板
        JLabel usernameLabel = new JLabel("Username:"); // 用户名标签
        JTextField usernameField = new JTextField(); // 用户名输入框
        JLabel passwordLabel = new JLabel("Password:"); // 密码标签
        JPasswordField passwordField = new JPasswordField(); // 密码输入框
        JButton loginButton = new JButton("Login"); // 登录按钮
        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String username = usernameField.getText(); // 获取用户名
                String password = new String(passwordField.getPassword()); // 获取密码
                if (login(username, password)) { // 如果登录成功
                    loginDialog.dispose(); // 关闭登录对话框
                    frame.setVisible(true); // 显示窗口
                } else {
                    JOptionPane.showMessageDialog(loginDialog, "Invalid username or password."); // 显示错误提示
                }
            }
        });
        loginPanel.add(usernameLabel);
        loginPanel.add(usernameField);
        loginPanel.add(passwordLabel);
        loginPanel.add(passwordField);
        loginPanel.add(new JLabel());
        loginPanel.add(loginButton);
        loginDialog.add(loginPanel);

        // 显示登录对话框
        loginDialog.setVisible(true);
    }

    public void startServer() throws IOException {
        ServerSocket serverSocket = new ServerSocket(8888);
        System.out.println("Server started.");
        while (true) {
            Socket socket = serverSocket.accept();
            System.out.println("Client connected: " + socket.getInetAddress().getHostAddress());
            new Thread(new DeviceHandler(socket)).start();
        }
    }

    private class DeviceHandler implements Runnable {
        private Socket socket;

        public DeviceHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    String[] tokens = inputLine.split(" ");
                    String id = tokens[0];
                    int battery = Integer.parseInt(tokens[1]);
                    int x = Integer.parseInt(tokens[2]);
                    int y = Integer.parseInt(tokens[3]);
                    for (Device device : devices) {
                        if (device.getId().equals(id)) {
                            device.updateSignal();
                            device.getBattery();
                            System.out.println("Received signal from device " + id + ": battery=" + battery + ", position=(" + x + ", " + y + ")");
                            break;
                        }
                    }
                }
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

public class Main {
    public static void main(String[] args) throws IOException {
        // 创建5个设备
        Device[] devices = new Device[5];
        DefaultTableModel tableModel = new DefaultTableModel();
        tableModel.addColumn("Device ID");
        tableModel.addColumn("Battery");
        tableModel.addColumn("Position");
        tableModel.addColumn("Status");
        Random random = new Random();
        for (int i = 0; i < 5; i++) {
            devices[i] = new Device("Device" + i, random.nextInt(100), random.nextInt(51), random.nextInt(22), tableModel);
        }

        // 创建服务器
        Server server = new Server(devices, "username", "Password1", tableModel);

        // 显示设备信息
        server.displayDevices();

        // 启动服务器
        server.startServer();
    }
}