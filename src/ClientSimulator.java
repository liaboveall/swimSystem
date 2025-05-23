import java.io.*;
import java.net.*;
import java.util.Random;
import java.util.Scanner;

/**
 * 简单的客户端模拟器
 * 用于测试服务器的数据接收功能
 */
public class ClientSimulator {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8888;
    
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("=== 游泳池设备客户端模拟器 ===");
            System.out.println("1. 发送单条数据");
            System.out.println("2. 连续发送模拟数据");
            System.out.println("3. 退出");
            System.out.print("请选择操作: ");
            
            int choice = scanner.nextInt();
            scanner.nextLine(); // 消费换行符
            
            switch (choice) {
                case 1 -> sendSingleData(scanner);
                case 2 -> sendContinuousData(scanner);
                case 3 -> System.out.println("退出程序");
                default -> System.out.println("无效选择");
            }
        }
        // scanner.close(); // 由 try-with-resources 自动关闭
    }
    
    /**
     * 发送单条数据
     */
    private static void sendSingleData(Scanner scanner) {
        System.out.print("请输入设备ID (例如: Device0): ");
        String deviceId = scanner.nextLine();
        
        System.out.print("请输入电量 (0-100): ");
        int battery = scanner.nextInt();
        
        System.out.print("请输入X坐标 (0-500): ");
        int x = scanner.nextInt();
        
        System.out.print("请输入Y坐标 (0-250): ");
        int y = scanner.nextInt();
        
        String message = deviceId + " " + battery + " " + x + " " + y;
        sendMessage(message);
    }
    
    /**
     * 连续发送模拟数据
     */
    private static void sendContinuousData(Scanner scanner) {
        System.out.print("请输入设备ID (例如: Device0): ");
        String deviceId = scanner.nextLine();
        
        System.out.print("请输入发送次数 (0表示无限): ");
        int count = scanner.nextInt();
        
        Random random = new Random();
        int sent = 0;
        
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            
            System.out.println("开始发送数据到 " + SERVER_HOST + ":" + SERVER_PORT);
            
            while (count == 0 || sent < count) {
                int battery = Math.max(0, 100 - sent * 2); // 模拟电量逐渐下降
                int x = random.nextInt(501); // 0-500
                int y = random.nextInt(251); // 0-250
                
                String message = deviceId + " " + battery + " " + x + " " + y;
                out.println(message);
                
                System.out.println("已发送: " + message);
                sent++;
                
                if (count == 0 || sent < count) {
                    try {
                        // 使用更现代的延迟方式，例如 ScheduledExecutorService，或者根据具体需求调整
                        // 这里暂时注释掉，具体实现应根据项目需求和上下文决定
                        // Introducing a small, fixed delay to prevent tight loop if needed for simulation
                        // For actual timed sending, a proper timer mechanism should be used.
                        if (count != 1) { // Avoid delay if only one message or infinite and first message
                             // Minimal delay to allow other operations, not for precise timing.
                             Thread.sleep(100); // Example minimal delay
                        }
                    } catch (InterruptedException e) { // Changed to InterruptedException
                        System.out.println("发送被中断: " + e.getMessage());
                        Thread.currentThread().interrupt(); // Restore interruption status
                        break;
                    }
                }
            }
            
        } catch (IOException e) {
            System.err.println("连接服务器失败: " + e.getMessage());
        }
        
        System.out.println("数据发送完成，共发送 " + sent + " 条数据");
    }
    
    /**
     * 发送单条消息
     */
    private static void sendMessage(String message) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            
            out.println(message);
            System.out.println("消息发送成功: " + message);
            
        } catch (IOException e) {
            System.err.println("发送消息失败: " + e.getMessage());
        }
    }
}
