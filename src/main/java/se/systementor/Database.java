
package se.systementor;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Database {
    private final String url = "jdbc:mysql://localhost:3306/Dzmitrysshop";
    private final String user = "root";
    private final String password = "110386Ndv";

    // Get database connection
    protected Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    // Fetch active products from the database
    public List<Product> activeProducts() {
        List<Product> products = new ArrayList<>();

        String query = "SELECT id, name, price, vat FROM product";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                Product product = new Product();
                product.setId(rs.getInt("id"));
                product.setName(rs.getString("name"));
                product.setPrice(rs.getDouble("price"));
                product.setVAT(rs.getDouble("vat"));
                products.add(product);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return products;
    }

    // Create a new order in the database
    public int createOrder(double totalPrice) {
        int orderDetailsID = -1;
        String sql = "INSERT INTO OrderDetails (total_price) VALUES (?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setDouble(1, totalPrice);
            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    orderDetailsID = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return orderDetailsID;
    }

    // Generate sales statistics in XML format
    public String ReceiptStatistic() {
        String xml = "";
        String sql = "SELECT COUNT(*) AS total, " +
                "MIN(OrderDate) AS FirstOrderDateTime, " +
                "MAX(OrderDate) AS LastOrderDateTime, " +
                "SUM(total_price) AS TotalSalesInclVat, " +
                "SUM(total_price * 0.25) AS TotalVat " + // Assuming VAT is 25%
                "FROM OrderDetails";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                int total = rs.getInt("total");
                String firstOrderDateTime = rs.getString("FirstOrderDateTime");
                String lastOrderDateTime = rs.getString("LastOrderDateTime");
                double totalSalesInclVat = rs.getDouble("TotalSalesInclVat");
                double totalVat = rs.getDouble("TotalVat");

                xml = "<xml>\n" +
                        "<SaleStatistics>\n" +
                        "    <FirstOrderDateTime>" + firstOrderDateTime + "</FirstOrderDateTime>\n" +
                        "    <LastOrderDateTime>" + lastOrderDateTime + "</LastOrderDateTime>\n" +
                        "    <TotalSalesInclVat>" + totalSalesInclVat + "</TotalSalesInclVat>\n" +
                        "    <TotalVat>" + totalVat + "</TotalVat>\n" +
                        "    <TotalNumberOfReceipts>" + total + "</TotalNumberOfReceipts>\n" +
                        "</SaleStatistics>\n" +
                        "</xml>\n";
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return xml;
    }
}




