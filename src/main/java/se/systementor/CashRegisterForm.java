
package se.systementor;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import java.nio.file.Path;

public class CashRegisterForm {
    private JPanel panel1;
    private JPanel panelRight;
    private JPanel panelLeft;
    private JTextArea receiptArea;
    private JPanel buttonsPanel;
    private JTextField textField1;
    private JTextField textField2; // Поле для количества
    private JButton addButton;
    private JButton payButton;
    private JButton clearButton;
    private JButton dagsstatistikButton;
    private Database database = new Database();
    private Product lastClickedProduct = null;
    private Map<Product, Integer> cart = new HashMap<>(); // Хранение товаров и их количества
    private static int receiptNumber = 1; // Счетчик чеков
    private static final String BUCKET_NAME = "your-bucket-name"; //  Укажи своё название
    private static final String S3_FOLDER = "statistics/"; //  Папка в бакете


    public CashRegisterForm() {
        for (Product product : database.activeProducts()) {
            JButton button = new JButton(product.getName());
            buttonsPanel.add(button);
            button.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    lastClickedProduct = product;
                    textField1.setText(product.getName());
                    textField2.setText("1"); // Начальное количество 1
                }
            });
        }

        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (lastClickedProduct != null) {
                    int quantity = 1;
                    try {
                        quantity = Integer.parseInt(textField2.getText()); // Читаем количество
                    } catch (NumberFormatException ignored) {}

                    addToCart(lastClickedProduct, quantity);
                    textField2.setText(String.valueOf(cart.get(lastClickedProduct))); // Обновляем поле Antal

                    if (receiptArea.getText().isEmpty()) {
                        receiptArea.append("                     Security SHOP\n");
                        receiptArea.append("----------------------------------------------------\n");
                        receiptArea.append("\n");
                        receiptArea.append("Kvittonummer: " + receiptNumber + "   Datum: " + getCurrentTime() + "\n");
                    }

                    receiptArea.append(String.format("%s x%d  %.2f\n", lastClickedProduct.getName(), quantity, lastClickedProduct.getPrice() * quantity));
                }
            }
        });

        payButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                printReceipt();
                saveOrderToDatabase();
                clearCart();
                receiptNumber++; // Увеличиваем номер чека после оплаты
            }
        });


        clearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                receiptArea.setText("");
                clearCart();
                textField1.setText("");
                textField2.setText("");
            }
        });
    }

    private void generateDailyStatistics() {

        dagsstatistikButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                generateDailyStatistics();
            }
        });


        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        String firstOrderDateTime = "";
        String lastOrderDateTime = "";
        double totalSalesInclVat = 0;
        double totalVat = 0;
        int totalNumberOfReceipts = 0;

        try (Connection conn = database.getConnection()) {
            // Запрос всех заказов за сегодня
            String sql = "SELECT o.OrderDate, o.TotalAmount, p.VAT " +
                    "FROM Orders o " +
                    "JOIN OrderItems oi ON o.OrderID = oi.OrderID " +
                    "JOIN Products p ON oi.ProductID = p.ProductID " +
                    "WHERE o.OrderDate LIKE ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, today + "%");

                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String orderDate = rs.getString("OrderDate");
                        double totalAmount = rs.getDouble("TotalAmount");
                        double vatRate = rs.getDouble("VAT"); // Используем существующее поле VAT

                        // first order
                        if (firstOrderDateTime.isEmpty()) {
                            firstOrderDateTime = orderDate;
                        }

                        // last order
                        lastOrderDateTime = orderDate;

                        // Amount
                        totalSalesInclVat += totalAmount;
                        totalVat += totalAmount * vatRate; // Используем реальную ставку VAT
                        totalNumberOfReceipts++;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Statistic fale: " + e.getMessage());
            return;
        }

        if (firstOrderDateTime.isEmpty()) {
            System.out.println("No data today");
            return;
        }

        // Creating XML
        String xmlContent = String.format("""
                <xml>
                <SaleStatistics>
                <FirstOrderDateTime>%s</FirstOrderDateTime>
                <LastOrderDateTime>%s</LastOrderDateTime>
                <TotalSalesInclVat>%.2f</TotalSalesInclVat>
                <TotalVat>%.2f</TotalVat>
                <TotalNumberOfReceipts>%d</TotalNumberOfReceipts>
                </SaleStatistics>
                </xml>
                """, firstOrderDateTime, lastOrderDateTime, totalSalesInclVat, totalVat, totalNumberOfReceipts);

        // XML
//        try {
//            String fileName = "Dagsstatistik_" + today.replace("-", "") + ".xml";
//            String saveDirectory = "/Users/dzmitrynikalayeu/Documents/Dagsstatistik";
//
//            Files.write(Paths.get(fileName), xmlContent.getBytes());
//            System.out.println(" XML-файл создан: " + fileName);
//
//            String reportContent = new String(Files.readAllBytes(Paths.get(fileName)));
//            receiptArea.setText(reportContent);
//
//            uploadToS3(fileName);
//        } catch (IOException e) {
//            System.err.println("Ошибка при сохранении XML-файла: " + e.getMessage());
//        }

        try {
            //String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String fileName = "Dagsstatistik_" + today.replace("-", "") + ".xml";
            String saveDirectory = "/Users/dzmitrynikalayeu/Documents/Dagsstatistik"; // Папка для отчетов
            String filePath = saveDirectory + "/" + fileName; // Полный путь к файлу


            Path directoryPath = Paths.get(saveDirectory);
            if (!Files.exists(directoryPath)) {
                Files.createDirectories(directoryPath);
            }


            Files.write(Paths.get(filePath), xmlContent.getBytes());
            System.out.println(" XML-файл создан: " + filePath);


            String reportContent = new String(Files.readAllBytes(Paths.get(filePath)));
            receiptArea.setText(reportContent);


            uploadToS3(filePath);

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("fale with XML-file saving: " + e.getMessage());
        }

    }

        private void uploadToS3(String filePath) {
            try (S3Client s3 = S3Client.builder()
                    .region(Region.EU_NORTH_1) //
                    .credentialsProvider(ProfileCredentialsProvider.create()) //  AWS CLI
                    .build()) {

                String fileName = Path.of(filePath).getFileName().toString();

                PutObjectRequest putRequest = PutObjectRequest.builder()
                        .bucket(BUCKET_NAME)
                        .key(S3_FOLDER + fileName)
                        .build();

                s3.putObject(putRequest, Path.of(filePath));
                System.out.println("File was downloaded in S3: " + S3_FOLDER + fileName);
            } catch (Exception e) {
                System.err.println("Downloading error S3: " + e.getMessage());

        }

    }
    private void addToCart(Product product, int quantity) {
        cart.put(product, cart.getOrDefault(product, 0) + quantity);
    }

    private void clearCart() {
        cart.clear();
    }

    private String getCurrentTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.now().format(formatter);
    }

    private void printReceipt() {
        if (cart.isEmpty()) {
            System.out.println("Cart is empty. Receipt can't be printed");
            return;
        }

        Map<Double, Double> vatSummary = new HashMap<>();
        double totalNetto = 0;
        double totalBrutto = 0;
        String currentTime = getCurrentTime();


        receiptArea.append("\n=================================\n");
        receiptArea.append("         KASSAKVITTO\n");
        receiptArea.append("=================================\n");
        receiptArea.append("Kvittonummer: " + receiptNumber + "\n");
        receiptArea.append("Datum: " + currentTime + "\n");
        receiptArea.append("---------------------------------\n");

        for (Map.Entry<Product, Integer> entry : cart.entrySet()) {
            Product product = entry.getKey();
            int quantity = entry.getValue();
            double brutto = product.getPrice() * quantity;
            double netto = Math.round((brutto / (1 + product.getVAT() / 100)) * 100) / 100.0;
            double moms = Math.round((brutto - netto) * 100) / 100.0;

            totalNetto += netto;
            totalBrutto += brutto;

            vatSummary.put(product.getVAT(), vatSummary.getOrDefault(product.getVAT(), 0.0) + moms);

            receiptArea.append(String.format("%-20s %2d x %7.2f = %7.2f\n", product.getName(), quantity, product.getPrice(), brutto));
        }

        receiptArea.append("\nMoms%      Moms     Netto     Brutto\n");

        for (Map.Entry<Double, Double> entry : vatSummary.entrySet()) {
            double vatRate = entry.getKey();
            double momsAmount = Math.round(entry.getValue() * 100) / 100.0;
            double nettoAmount = Math.round((momsAmount / (vatRate / 100)) * 100) / 100.0;
            double bruttoAmount = Math.round((nettoAmount + momsAmount) * 100) / 100.0;

            receiptArea.append(String.format("%-10.2f %-8.2f %-8.2f %-8.2f\n", vatRate, momsAmount, nettoAmount, bruttoAmount));
        }

        receiptArea.append("\n---------------------------------\n");
        receiptArea.append(String.format("Total:               %.2f\n", totalBrutto));
        receiptArea.append("=================================\n");
        receiptArea.append("TACK FÖR DITT KÖP!\n");

    }

    public void run() {
        JFrame frame = new JFrame("Cash Register");
        frame.setContentPane(new CashRegisterForm().panel1);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setSize(1000, 800);
        frame.setVisible(true);
    }

    private void saveOrderToDatabase() {
        if (cart.isEmpty()) {
            System.out.println("Cart is empty. Order can't be saved");
            return;
        }

        String orderDate = getCurrentTime();
        double totalAmount = cart.entrySet().stream()
                .mapToDouble(entry -> entry.getKey().getPrice() * entry.getValue())
                .sum();

        String customerID = getExistingCustomerID();
        if (customerID == null) {
            System.out.println("Error: no existing customer ID!");
            return;
        }

        try (Connection conn = database.getConnection()) {
            conn.setAutoCommit(false);

            String insertOrderSQL = "INSERT INTO Orders (CustomerID, OrderDate, TotalAmount) VALUES (?, ?, ?)";
            try (PreparedStatement orderStmt = conn.prepareStatement(insertOrderSQL, PreparedStatement.RETURN_GENERATED_KEYS)) {
                orderStmt.setString(1, customerID);
                orderStmt.setString(2, orderDate);
                orderStmt.setDouble(3, totalAmount);
                orderStmt.executeUpdate();
            }

            conn.commit();
            System.out.println(" Receipt №" + receiptNumber + " successfully saved.");
        } catch (SQLException e) {
            System.err.println("Fale with order saving: " + e.getMessage());
        }

    }

    private String getExistingCustomerID() {
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT CustomerID FROM Customers LIMIT 1");
             var rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getString("CustomerID");
            }
        } catch (SQLException e) {
            System.err.println("Fale with getting CustomerID: " + e.getMessage());
        }
        return null;
    }
}


