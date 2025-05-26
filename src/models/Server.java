package models;

import config.Config;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import utils.Logger;
import utils.PasswordValidator;

/**
 * 服务器类
 * 管理设备连接和用户界面
 */
public class Server {
    private final Device[] devices;
    private final String username;
    private final String password;
    private final DefaultTableModel tableModel;
    private ServerSocket serverSocket;
    private volatile boolean serverRunning = false;

    public Server(Device[] devices, String username, String password, DefaultTableModel tableModel) {
        this.devices = devices;
        this.username = username;
        this.password = password;
        this.tableModel = tableModel;
        Logger.info("服务器实例创建完成，设备数量: " + devices.length);
    }

    /**
     * 验证登录凭据
     */
    public boolean login(String username, String password) {
        boolean usernameMatch = this.username.equals(username);
        boolean passwordMatch = this.password.equals(password);
        boolean passwordValid = PasswordValidator.isValid(password);
        
        if (usernameMatch && passwordMatch && passwordValid) {
            Logger.info("用户 " + username + " 登录成功");
            return true;
        } else {
            Logger.warning("用户 " + username + " 登录失败");
            return false;
        }
    }

    /**
     * 显示设备监控界面
     */
    public void displayDevices() {
        SwingUtilities.invokeLater(() -> {
            createMainWindow();
        });
    }

    /**
     * 创建主窗口
     */
    private void createMainWindow() {
        JFrame frame = new JFrame("游泳池安全监控系统 v2.0");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(Config.MAIN_WINDOW_WIDTH, Config.MAIN_WINDOW_HEIGHT);
        frame.setLocationRelativeTo(null);
        
        // 窗口关闭事件处理
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                int option = JOptionPane.showConfirmDialog(
                    frame, 
                    "确定要退出系统吗？", 
                    "确认退出", 
                    JOptionPane.YES_NO_OPTION
                );
                if (option == JOptionPane.YES_OPTION) {
                    shutdownSystem();
                    System.exit(0);
                }
            }
        });

        // 创建表格
        JTable table = createDeviceTable();
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder("设备状态监控"));
        
        // 创建控制面板
        JPanel controlPanel = createControlPanel(frame);
        
        // 创建状态栏
        JPanel statusPanel = createStatusPanel();
        
        // 布局
        frame.setLayout(new BorderLayout(10, 10));
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(controlPanel, BorderLayout.SOUTH);
        frame.add(statusPanel, BorderLayout.NORTH);

        // 显示登录对话框
        if (showLoginDialog(frame)) {
            frame.setVisible(true);
            startDeviceThreads();
            startServerInBackground();
        } else {
            Logger.info("登录取消，程序退出");
            System.exit(0);
        }
    }

    /**
     * 创建设备表格
     */
    private JTable createDeviceTable() {
        JTable table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(25);
        table.getTableHeader().setReorderingAllowed(false);
        
        // 设置列宽
        table.getColumnModel().getColumn(0).setPreferredWidth(100); // 设备ID
        table.getColumnModel().getColumn(1).setPreferredWidth(80);  // 电量
        table.getColumnModel().getColumn(2).setPreferredWidth(120); // 位置
        table.getColumnModel().getColumn(3).setPreferredWidth(100); // 状态
        
        return table;
    }

    /**
     * 创建控制面板
     */
    private JPanel createControlPanel(JFrame parent) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createTitledBorder("控制操作"));
        
        // 阻塞信号按钮
        JButton blockSignalButton = new JButton("模拟Device0信号丢失");
        blockSignalButton.setToolTipText("点击模拟Device0设备信号丢失，测试溺水警报功能");
        blockSignalButton.addActionListener(_ -> {
            if (devices.length > 0) {
                int option = JOptionPane.showConfirmDialog(
                    parent,
                    "确定要模拟Device0信号丢失吗？这将触发溺水警报。",
                    "确认操作",
                    JOptionPane.YES_NO_OPTION
                );
                if (option == JOptionPane.YES_OPTION) {
                    devices[0].blockSignal();
                }
            } else {
                JOptionPane.showMessageDialog(parent, "没有可用的设备", "错误", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        // 刷新按钮
        JButton refreshButton = new JButton("刷新显示");
        refreshButton.setToolTipText("手动刷新设备状态显示");
        refreshButton.addActionListener(_ -> {
            tableModel.fireTableDataChanged();
            Logger.info("手动刷新设备状态显示");
        });
        
        // 关于按钮
        JButton aboutButton = new JButton("关于系统");
        aboutButton.addActionListener(_ -> showAboutDialog(parent));
        
        panel.add(blockSignalButton);
        panel.add(refreshButton);
        panel.add(aboutButton);
        
        return panel;
    }

    /**
     * 创建状态栏
     */
    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createLoweredBevelBorder());
        
        JLabel statusLabel = new JLabel("系统运行正常 | 端口: " + Config.DEFAULT_PORT + 
                                       " | 设备数量: " + devices.length);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        panel.add(statusLabel, BorderLayout.WEST);
        
        return panel;
    }

    /**
     * 显示登录对话框
     */
    private boolean showLoginDialog(JFrame parent) {
        JDialog loginDialog = new JDialog(parent, "系统登录", true);
        loginDialog.setSize(Config.LOGIN_WINDOW_WIDTH, Config.LOGIN_WINDOW_HEIGHT);
        loginDialog.setLocationRelativeTo(parent);
        loginDialog.setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // 标题
        JLabel titleLabel = new JLabel("游泳池安全监控系统", JLabel.CENTER);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        
        // 输入面板
        JPanel inputPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        JLabel usernameLabel = new JLabel("用户名:");
        JTextField usernameField = new JTextField();
        JLabel passwordLabel = new JLabel("密码:");
        JPasswordField passwordField = new JPasswordField();
        
        inputPanel.add(usernameLabel);
        inputPanel.add(usernameField);
        inputPanel.add(passwordLabel);
        inputPanel.add(passwordField);
        
        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton loginButton = new JButton("登录");
        JButton cancelButton = new JButton("取消");
        
        final boolean[] loginSuccess = {false};
        
        // 登录按钮事件
        loginButton.addActionListener(_ -> {
            String inputUsername = usernameField.getText().trim();
            String inputPassword = new String(passwordField.getPassword());
            
            if (inputUsername.isEmpty() || inputPassword.isEmpty()) {
                JOptionPane.showMessageDialog(loginDialog, 
                    "用户名和密码不能为空", "输入错误", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            if (!PasswordValidator.isValid(inputPassword)) {
                JOptionPane.showMessageDialog(loginDialog, 
                    PasswordValidator.getPasswordRequirements(), 
                    "密码格式错误", JOptionPane.WARNING_MESSAGE);
                passwordField.setText("");
                passwordField.requestFocus();
                return;
            }
            
            if (login(inputUsername, inputPassword)) {
                loginSuccess[0] = true;
                loginDialog.dispose();
            } else {
                JOptionPane.showMessageDialog(loginDialog, 
                    "用户名或密码错误", "登录失败", JOptionPane.ERROR_MESSAGE);
                passwordField.setText("");
                usernameField.requestFocus();
            }
        });
        
        // 取消按钮事件
        cancelButton.addActionListener(_ -> loginDialog.dispose());
        
        // Enter键支持        passwordField.addActionListener(_ -> loginButton.doClick());
        usernameField.addActionListener(_ -> passwordField.requestFocus());
        
        buttonPanel.add(loginButton);
        buttonPanel.add(cancelButton);
        
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        mainPanel.add(inputPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        loginDialog.add(mainPanel);
        loginDialog.setVisible(true);
        
        return loginSuccess[0];
    }

    /**
     * 显示关于对话框
     */
    private void showAboutDialog(JFrame parent) {
        String aboutText = "<html><body style='width: 300px'>" +
            "<h2>游泳池安全监控系统 v2.0</h2>" +
            "<p><b>功能特点：</b></p>" +
            "<ul>" +
            "<li>实时监控多个设备状态</li>" +
            "<li>自动检测信号丢失和溺水情况</li>" +
            "<li>声音警报系统</li>" +
            "<li>电量监控和低电量警告</li>" +
            "<li>TCP/IP客户端-服务器通信</li>" +
            "</ul>" +
            "<p><b>开发信息：</b></p>" +
            "<p>使用Java Swing开发<br>" +
            "支持多线程并发处理<br>" +
            "集成FlatLaf现代化界面</p>" +
            "</body></html>";
        
        JOptionPane.showMessageDialog(parent, aboutText, "关于系统", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 启动设备线程
     */
    private void startDeviceThreads() {
        Logger.info("启动设备监控线程");
        for (Device device : devices) {
            new Thread(device, "Device-" + device.getId()).start();
        }
    }

    /**
     * 后台启动服务器
     */
    private void startServerInBackground() {
        new Thread(this::startServer, "Server-Thread").start();
    }

    /**
     * 启动服务器监听
     */
    private void startServer() {
        try {
            serverSocket = new ServerSocket(Config.DEFAULT_PORT);
            serverRunning = true;
            Logger.info("服务器启动成功，监听端口: " + Config.DEFAULT_PORT);
            
            while (serverRunning && !serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    Logger.info("客户端连接: " + clientSocket.getInetAddress().getHostAddress());
                    new Thread(new DeviceHandler(clientSocket, devices), 
                             "DeviceHandler-" + clientSocket.getInetAddress().getHostAddress()).start();
                } catch (IOException e) {
                    if (serverRunning) {
                        Logger.error("接受客户端连接时发生错误", e);
                    }
                }
            }
        } catch (IOException e) {
            Logger.error("启动服务器失败", e);
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, 
                    "服务器启动失败: " + e.getMessage(), 
                    "服务器错误", JOptionPane.ERROR_MESSAGE);
            });
        }
    }

    /**
     * 关闭系统
     */
    private void shutdownSystem() {
        Logger.info("开始关闭系统...");
        
        // 停止服务器
        serverRunning = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                Logger.info("服务器套接字已关闭");
            } catch (IOException e) {
                Logger.error("关闭服务器套接字时发生错误", e);
            }
        }
        
        // 停止设备线程
        for (Device device : devices) {
            device.stop();
        }
        
        Logger.info("系统关闭完成");
    }

    /**
     * 设备处理器内部类
     */
    private static class DeviceHandler implements Runnable {
        private final Socket clientSocket;
        private final Device[] devices;

        public DeviceHandler(Socket socket, Device[] devices) {
            this.clientSocket = socket;
            this.devices = devices;
        }

        @Override
        public void run() {
            String clientAddress = clientSocket.getInetAddress().getHostAddress();
            Logger.info("开始处理客户端: " + clientAddress);
            
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()))) {
                
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    processMessage(inputLine, clientAddress);
                }
                
            } catch (IOException e) {
                if (!clientSocket.isClosed()) {
                    Logger.error("处理客户端 " + clientAddress + " 时发生错误", e);
                }
            } finally {
                try {
                    if (!clientSocket.isClosed()) {
                        clientSocket.close();
                    }
                } catch (IOException e) {
                    Logger.error("关闭客户端连接时发生错误", e);
                }
                Logger.info("客户端断开连接: " + clientAddress);
            }
        }

        /**
         * 处理客户端消息
         */
        private void processMessage(String message, String clientAddress) {
            Logger.debug("收到客户端 " + clientAddress + " 消息: " + message);
            
            String[] tokens = message.split(" ");
            if (tokens.length < 4) {
                Logger.warning("消息格式错误，期望4个参数: " + message);
                return;
            }

            try {
                String deviceId = tokens[0];
                int battery = Integer.parseInt(tokens[1]);
                int x = Integer.parseInt(tokens[2]);
                int y = Integer.parseInt(tokens[3]);

                // 查找对应设备并更新状态
                boolean deviceFound = false;
                for (Device device : devices) {
                    if (device.getId().equals(deviceId)) {
                        device.updateStateFromClient(battery, x, y);
                        deviceFound = true;
                        break;
                    }
                }

                if (!deviceFound) {
                    Logger.warning("未找到设备: " + deviceId);
                }

            } catch (NumberFormatException e) {
                Logger.error("解析数字参数失败: " + message, e);
            }
        }
    }
}
