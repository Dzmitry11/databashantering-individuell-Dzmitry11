package se.systementor;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class StatisticsGenerator {
    private static final String BUCKET_NAME = "dagsstatistik"; //  My S3-bucket
    private static final String S3_FOLDER = "statistics/"; // My S3-folder
    private static final String SAVE_DIRECTORY = "/Users/dzmitrynikalayeu/Documents/Dagsstatistik"; // My local folder
    private Database database = new Database(); //

    public void generateAndUploadStatistics() {
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String firstOrderDateTime = "";
        String lastOrderDateTime = "";
        double totalSalesInclVat = 0;
        double totalVat = 0;
        int totalNumberOfReceipts = 0;

        // Get data from database
        try (Connection conn = database.getConnection()) { // 🟢 Убедитесь, что getConnection() в Database — public
            String sql = "SELECT o.OrderDate, o.TotalAmount, p.VAT FROM Orders o " +
                    "JOIN OrderItems oi ON o.OrderID = oi.OrderID " +
                    "JOIN Products p ON oi.ProductID = p.ProductID " +
                    "WHERE o.OrderDate LIKE ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, today + "%");

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String orderDate = rs.getString("OrderDate");
                        double totalAmount = rs.getDouble("TotalAmount");
                        double vatRate = rs.getDouble("VAT");

                        if (firstOrderDateTime.isEmpty()) firstOrderDateTime = orderDate;
                        lastOrderDateTime = orderDate;

                        totalSalesInclVat += totalAmount;
                        totalVat += totalAmount * vatRate;
                        totalNumberOfReceipts++;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            return;
        }

        if (firstOrderDateTime.isEmpty()) {
            System.out.println("No sales data for today.");
            return;
        }

        // ✅ Create XML
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

        // Saving file
        try {
            Path directoryPath = Paths.get(SAVE_DIRECTORY);
            if (!Files.exists(directoryPath)) Files.createDirectories(directoryPath);

            String fileName = "Dagsstatistik_" + today.replace("-", "") + ".xml";
            Path filePath = directoryPath.resolve(fileName);

            Files.write(filePath, xmlContent.getBytes());
            System.out.println(" XML saved: " + filePath);

            // Download to S3
            uploadToS3(filePath.toString());
        } catch (IOException e) {
            System.err.println("File save error: " + e.getMessage());
        }
    }

    private void uploadToS3(String filePath) {
        try (S3Client s3 = S3Client.builder()
                .region(Region.EU_NORTH_1)
                .credentialsProvider(ProfileCredentialsProvider.create())
                .build()) {

            String fileName = Paths.get(filePath).getFileName().toString();

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(S3_FOLDER + fileName)
                    .build();

            s3.putObject(putRequest, Paths.get(filePath));
            System.out.println(" Uploaded to S3: " + S3_FOLDER + fileName);
        } catch (Exception e) {
            System.err.println("S3 upload error: " + e.getMessage());
        }
    }
}






