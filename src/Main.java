import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;
import jmp123.PlayBack; // 确保 jmp123.jar 在类路径中
import jmp123.NoPlayerException; // 导入用于异常处理
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

// 设备状态枚举
enum DeviceStatus {
    NORMAL, // 设备状态正常
    LOW_BATTERY, // 设备电量低
    DROWNING // 设备溺水
}

//警报接口
interface AlarmInterface {
    public static void playWarning(String soundFilePath) {
        try {
            File soundFile = new File(soundFilePath);
            if (!soundFile.exists() || soundFile.isDirectory()) {
                System.err.println("警告: 警报声音文件未找到或为目录: " + soundFilePath);
                return;
            }
            PlayBack playBack = new PlayBack(new jmp123.output.Audio());
            playBack.open(soundFilePath, ""); // 使用提供的路径
            playBack.start(true); // 播放警报声音
        } catch (NoPlayerException e) {
            System.err.println("错误：初始化音频播放失败 (NoPlayerException): " + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("错误：播放警报声音失败 (IOException): " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("在警报播放期间发生意外错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

// 设备类
class Device implements Runnable, AlarmInterface {
    private final String id; // 设备ID
    private volatile int battery; // 设备电量
    private volatile DeviceStatus status; // 设备状态
    private volatile int x; // 设备位置x坐标
    private volatile int y; // 设备位置y坐标
    private volatile long lastSignalTime; // 上次信号时间
    // signalInterval 现在在 checkDeviceState 中动态计算
    private final DefaultTableModel tableModel; // 表格模型
    private final Random random; // 随机数生成器
    private final String soundFilePath; // 警报声音文件的路径

    public Device(String id, int initialBattery, int initialX, int initialY, DefaultTableModel tableModel, String soundFilePath) {
        this.id = id;
        this.battery = initialBattery;
        this.x = initialX;
        this.y = initialY;
        this.lastSignalTime = System.currentTimeMillis();
        this.status = DeviceStatus.NORMAL; // 初始状态
        if (this.battery < 10) {
            this.status = DeviceStatus.LOW_BATTERY;
        }
        this.tableModel = tableModel;
        this.random = new Random();
        this.soundFilePath = soundFilePath;
        updateTable(); // 在表格中显示初始状态
    }

    public String getId() {
        return id;
    }

    public synchronized int getBattery() {
        return battery;
    }

    public synchronized DeviceStatus getStatus() {
        return status;
    }

    public synchronized int getX() {
        return x;
    }

    public synchronized int getY() {
        return y;
    }

    public synchronized boolean isDrown() {
        return status == DeviceStatus.DROWNING;
    }

    @Override
    public void run() {
        while (true) {
            checkDeviceState();
            try {
                Thread.sleep(2000); // 每2秒检查一次设备状态
            } catch (InterruptedException e) {
                System.err.println("设备 " + id + " 线程被中断。");
                Thread.currentThread().interrupt(); // 保留中断状态
                break; // 退出循环
            }
        }
    }

    // 当从客户端接收到信号时由 DeviceHandler 调用
    public synchronized void updateStateFromClient(int newBattery, int newX, int newY) {
        this.lastSignalTime = System.currentTimeMillis();
        this.battery = newBattery;
        this.x = newX;
        this.y = newY;

        // 根据新数据确定状态，但如果服务器逻辑已设置 DROWNING，则不覆盖
        if (this.status != DeviceStatus.DROWNING) {
            if (this.battery < 10) {
                this.status = DeviceStatus.LOW_BATTERY;
            } else {
                this.status = DeviceStatus.NORMAL;
            }
        }
        System.out.println("设备 " + id + " 由客户端更新: 电量=" + battery + "%, 位置=(" + x + "," + y + "), 状态=" + status);
        updateTable();
    }
    
    // 定期检查设备的状态（信号丢失、电池）并模拟行为
    private synchronized void checkDeviceState() {
        long currentTime = System.currentTimeMillis();
        long currentSignalInterval = (currentTime - lastSignalTime) / 1000; // 单位：秒
        DeviceStatus previousStatus = this.status;
        boolean positionChanged = false;

        if (currentSignalInterval >= 30) { // 信号丢失
            if (this.status != DeviceStatus.DROWNING) {
                this.status = DeviceStatus.DROWNING;
                System.out.println("设备 " + id + " 状态因信号丢失 ("+ currentSignalInterval +"s) 变为 DROWNING。正在播放警报。");
                playDrowningAlarmNonBlocking();
            }
        } else { // 信号尚未被视为溺水丢失
            // 如果没有最近的客户端更新（例如5-29秒），则模拟随机移动
            if (currentSignalInterval >= 5 && currentSignalInterval < 30) {
                 this.x = random.nextInt(501); // 假设泳池宽度 0-500
                 this.y = random.nextInt(251); // 假设泳池长度 0-250
                 positionChanged = true;
                 // System.out.println("设备 " + id + " 模拟移动到 (" + x + "," + y + ")");
            }

            // 如果未溺水，则根据电池更新状态
            if (this.status != DeviceStatus.DROWNING) {
                if (this.battery < 10) {
                    this.status = DeviceStatus.LOW_BATTERY;
                } else {
                    // 如果之前是 LOW_BATTERY 但现在电池 >=10 (例如客户端更新修复了它)
                    this.status = DeviceStatus.NORMAL;
                }
            }
        }

        if (previousStatus != this.status || positionChanged) {
            // System.out.println("设备 " + id + " 状态改变。旧: " + previousStatus + ", 新: " + this.status + ". 更新表格。");
            updateTable();
        }
    }

    private void playDrowningAlarmNonBlocking() {
        new Thread(() -> AlarmInterface.playWarning(this.soundFilePath)).start();
    }

    private void updateTable() {
        // 捕获当前状态以进行UI更新，以避免在invokeLater调度和执行之间状态发生变化导致的问题
        final String currentId = this.id;
        final int currentBatteryVal = this.getBattery(); // 使用 getter 进行同步访问
        final int currentXVal = this.getX();
        final int currentYVal = this.getY();
        final DeviceStatus currentStatusVal = this.getStatus();

        SwingUtilities.invokeLater(() -> {
            String displayStatusText;
            String batteryText = currentBatteryVal + "%"; // 默认电池文本

            if (currentStatusVal == DeviceStatus.DROWNING) {
                displayStatusText = "<html><font color=\\'red\\'>DROWNING</font></html>";
                batteryText = "<html><font color=\\'red\\'>" + currentBatteryVal + "%</font></html>";
            } else if (currentStatusVal == DeviceStatus.LOW_BATTERY) { // 简化条件
                displayStatusText = "<html><font color=\\'yellow\\'>LOW BATTERY</font></html>";
                batteryText = "<html><font color=\\'yellow\\'>" + currentBatteryVal + "%</font></html>";
            } else { // NORMAL
                displayStatusText = "NORMAL";
            }

            int rowIndex = -1;
            // 通过迭代查找具有匹配ID的行
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                if (tableModel.getValueAt(i, 0).equals(currentId)) {
                    rowIndex = i;
                    break;
                }
            }

            if (rowIndex != -1) {
                tableModel.setValueAt(batteryText, rowIndex, 1);
                tableModel.setValueAt("(" + currentXVal + ", " + currentYVal + ")", rowIndex, 2);
                tableModel.setValueAt(displayStatusText, rowIndex, 3);
            } else {
                System.err.println("在表格模型中未找到设备 ID: " + currentId + "。无法更新其行。");
            }
        });
    }

    // 模拟阻塞信号，导致 DROWNING 状态的方法
    public void blockSignal() {
        System.out.println("请求阻塞设备 " + this.id + " 的信号");
        new Thread(() -> {
            try {
                // 模拟信号被视为丢失/设备进入问题状态的时间
                System.out.println("设备 " + id + " 正在模拟信号阻塞...");
                Thread.sleep(5000); // 从30000缩短以便更快地获得用户反馈

                synchronized (this) { // 同步访问设备状态
                    this.status = DeviceStatus.DROWNING;
                    // 将 lastSignalTime 设置为很久以前，以确保 checkDeviceState 确认 DROWNING
                    this.lastSignalTime = System.currentTimeMillis() - 35000; // 35秒前
                    System.out.println("设备 " + id + " 状态被 blockSignal 手动设置为 DROWNING。正在播放警报。");
                }
                playDrowningAlarmNonBlocking(); // 播放警报
                updateTable(); // 根据新状态更新表格

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("设备 " + id + " blockSignal 线程被中断。");
            }
        }).start();
    }
}

// 服务器类
class Server {
    public static final int DEFAULT_PORT = 8888; // 服务器默认端口
    private final Device[] devices; // 设备数组
    private final String username; // 用户名
    private final String password; // 密码
    private final DefaultTableModel tableModel; // 表格模型 (与设备共享)

    public Server(Device[] devices, String username, String password, DefaultTableModel tableModel) {
        this.devices = devices;
        this.username = username;
        this.password = password;
        this.tableModel = tableModel; // tableModel 已由 Device 实例管理以进行更新
    }

    public boolean login(String username, String password) {
        return this.username.equals(username) && this.password.equals(password);
    }

    public void displayDevices() { // 不再直接抛出 IOException
        JFrame frame = new JFrame("游泳池安全系统");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null); // 居中显示窗口

        // 表格列已在 Main 中添加。此处无需重新定义。
        // 初始数据在 Main 中添加到 tableModel，设备会自行更新。
        JTable table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        JScrollPane scrollPane = new JScrollPane(table);
        frame.add(scrollPane, BorderLayout.CENTER);

        // 为第一个设备创建阻断信号按钮
        JButton blockSignalButton = new JButton("阻断Device0信号");
        blockSignalButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (devices.length > 0) {
                    devices[0].blockSignal(); // 非阻塞调用
                } else {
                    JOptionPane.showMessageDialog(frame, "没有可阻断的设备。", "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        JPanel bottomPanel = new JPanel();
        bottomPanel.add(blockSignalButton);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        // 创建登录对话框
        JDialog loginDialog = new JDialog(frame, "登录", true);
        loginDialog.setSize(300, 150); // 调整了大小
        loginDialog.setLocationRelativeTo(frame);
        loginDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE); // 确保对话框正确关闭

        JPanel loginPanel = new JPanel(new GridLayout(3, 2, 5, 5)); // 添加了间隙
        loginPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // 添加了内边距

        JLabel usernameLabel = new JLabel("用户名:");
        JTextField usernameField = new JTextField();
        JLabel passwordLabel = new JLabel("密码:");
        JPasswordField passwordField = new JPasswordField();
        JButton loginButton = new JButton("登录");

        // 允许在密码字段上按 Enter 键登录
        passwordField.addActionListener(e -> loginButton.doClick());
        usernameField.addActionListener(ignored -> passwordField.requestFocusInWindow());


        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String inputUsername = usernameField.getText();
                String inputPassword = new String(passwordField.getPassword());
                if (login(inputUsername, inputPassword)) {
                    loginDialog.dispose();
                    frame.setVisible(true); // 显示主应用程序窗口
                    // 在UI可能可见并准备就绪后启动设备线程
                    for (Device device : devices) {
                        new Thread(device).start();
                    }
                } else {
                    JOptionPane.showMessageDialog(loginDialog, "用户名或密码无效。", "登录失败", JOptionPane.ERROR_MESSAGE);
                    passwordField.setText(""); // 清空密码字段
                    usernameField.requestFocusInWindow();
                }
            }
        });
        loginPanel.add(usernameLabel);
        loginPanel.add(usernameField);
        loginPanel.add(passwordLabel);
        loginPanel.add(passwordField);
        loginPanel.add(new JLabel()); // 占位符
        loginPanel.add(loginButton);
        loginDialog.add(loginPanel);

        // 首先显示登录对话框
        loginDialog.setVisible(true);

        // 如果登录成功，则调用了 frame.setVisible(true)。
        // 如果在未成功登录的情况下关闭了登录对话框，应用程序可能会在此处退出或挂起。
        // 考虑在未登录的情况下通过“X”按钮关闭 loginDialog 时会发生什么。
        // 主线程将在 loginDialog.setVisible(true) 返回后（当对话框关闭时）继续。
        // 如果此时 frame 不可见（登录失败/取消），应用程序可能看起来什么也没做。
        if (!frame.isVisible()) {
            System.out.println("登录未成功或已取消。正在退出应用程序。");
            System.exit(0); // 或进行适当处理
        }
    }

    public void startServer() throws IOException {
        ServerSocket serverSocket = new ServerSocket(DEFAULT_PORT); // 使用常量
        System.out.println("服务器已在端口 " + DEFAULT_PORT + " 启动。等待客户端连接..."); // 使用常量
        try {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("客户端已连接: " + clientSocket.getInetAddress().getHostAddress());
                // 创建一个新线程来处理客户端通信
                new Thread(new DeviceHandler(clientSocket, devices)).start();
            }
        } finally {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                System.out.println("服务器套接字已关闭。");
            }
        }
    }

    // 用于处理与单个客户端设备通信的内部类
    private static class DeviceHandler implements Runnable {
        private final Socket clientSocket;
        private final Device[] devices; // 对服务器设备数组的引用

        public DeviceHandler(Socket socket, Device[] devices) {
            this.clientSocket = socket;
            this.devices = devices;
        }

        @Override
        public void run() {
            try (
                InputStreamReader isr = new InputStreamReader(clientSocket.getInputStream());
                BufferedReader in = new BufferedReader(isr);
                // PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true); // 如果需要回传数据
            ) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    System.out.println("从客户端 " + clientSocket.getInetAddress().getHostAddress() + " 收到: " + inputLine);
                    String[] tokens = inputLine.split(" ");
                    if (tokens.length < 4) { // 期望格式: DeviceID Battery X Y
                        System.err.println("来自客户端 " + clientSocket.getInetAddress().getHostAddress() + " 的输入格式错误: " + inputLine + ". 期望4个令牌。");
                        continue;
                    }
                    String deviceId = tokens[0];
                    try {
                        int battery = Integer.parseInt(tokens[1]);
                        int x = Integer.parseInt(tokens[2]);
                        int y = Integer.parseInt(tokens[3]);

                        boolean deviceFound = false;
                        for (Device device : devices) {
                            if (device.getId().equals(deviceId)) {
                                device.updateStateFromClient(battery, x, y);
                                deviceFound = true;
                                break;
                            }
                        }
                        if (!deviceFound) {
                            System.err.println("来自客户端 " + clientSocket.getInetAddress().getHostAddress() + " 的设备ID '" + deviceId + "' 在服务器上未找到。");
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("从客户端输入 '" + inputLine + "' 解析数字时出错: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                if (!clientSocket.isClosed()) { // 如果套接字由客户端正常关闭，则避免错误消息
                     System.err.println("DeviceHandler 中 " + clientSocket.getInetAddress().getHostAddress() + " 的 IOException: " + e.getMessage());
                }
            } finally {
                try {
                    if (clientSocket != null && !clientSocket.isClosed()) {
                        clientSocket.close();
                    }
                } catch (IOException e) {
                    System.err.println("关闭客户端套接字 " + clientSocket.getInetAddress().getHostAddress() + " 时出错: " + e.getMessage());
                }
                System.out.println("客户端已断开连接: " + clientSocket.getInetAddress().getHostAddress());
            }
        }
    }
}

public class Main {
    private static final String ALARM_SOUND_FILE_PATH = "src/sounds/alert.mp3"; // 更新路径以反映项目结构

    public static void main(String[] args) {
        // 创建一个 DefaultTableModel (将被共享)
        DefaultTableModel tableModel = new DefaultTableModel();
        tableModel.addColumn("设备 ID");
        tableModel.addColumn("电量");
        tableModel.addColumn("位置 (X,Y)");
        tableModel.addColumn("状态");

        // 创建5个设备
        final int NUM_DEVICES = 5;
        Device[] devices = new Device[NUM_DEVICES];
        Random random = new Random();
        for (int i = 0; i < NUM_DEVICES; i++) {
            // 表格模型行的初始数据
            String deviceId = "Device" + i;
            int initialBattery = random.nextInt(101); // 0-100
            int initialX = random.nextInt(501); // 0-500 (示例泳池尺寸)
            int initialY = random.nextInt(251); // 0-250
            
            // 为此设备的初始状态向表格模型添加一行
            // Device 构造函数将调用 updateTable，该方法使用 setValueAt，因此初始行必须存在。
            Object[] rowData = {
                deviceId,
                initialBattery + "%",
                "(" + initialX + ", " + initialY + ")",
                (initialBattery < 10 ? "电量低" : "正常") // 简化的初始状态字符串
            };
            tableModel.addRow(rowData);
            
            // 现在创建设备实例，它将通过 updateTable() 更新其行
            devices[i] = new Device(deviceId, initialBattery, initialX, initialY, tableModel, ALARM_SOUND_FILE_PATH);
        }

        // 创建服务器实例
        Server server = new Server(devices, "admin", "password", tableModel); // 简单凭据

        // 显示登录窗口，然后显示设备信息窗口
        // 设备线程在 server.displayDevices() 中成功登录后启动
        server.displayDevices();


        // 启动服务器套接字以侦听客户端连接（如果登录成功且UI已启动）
        // 如果 displayDevices() 在登录前阻塞，则理想情况下应在与 EDT 不同的线程上运行。
        // 但是，displayDevices() 显示一个模态登录对话框，因此主线程会等待。
        // 如果登录成功，则继续。
        
        // 检查主框架是否可见，这意味着登录成功
        // 此检查有点间接；如果登录失败，displayDevices 会处理 System.exit。
        // 因此，如果我们到达这里，登录可能已成功。
        
        new Thread(() -> {
            try {
                server.startServer();
            } catch (IOException e) {
                System.err.println("启动服务器失败: " + e.getMessage());
                e.printStackTrace();
                // 可选地，向用户显示错误对话框
                JOptionPane.showMessageDialog(null, "启动服务器失败: " + e.getMessage(), "服务器错误", JOptionPane.ERROR_MESSAGE);
                System.exit(1); // 如果服务器无法启动则退出
            }
        }).start();

        System.out.println("主应用程序设置完成。服务器线程已启动。");
    }
}