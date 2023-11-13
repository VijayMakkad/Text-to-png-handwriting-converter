package com.example.finalt2h;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.imageio.ImageIO;

public class TextToHandwritingConverter extends JFrame {

    private String text;
    private Font handwritingFont;
    private int fontSize;
    private int pageWidth;
    private int pageHeight;
    private int currentY;
    private int currentPageIndex;
    private BufferedImage currentPage;
    private Graphics2D currentGraphics;
    private JComboBox<String> handwritingStyleComboBox; // Added JComboBox for handwriting styles


    public TextToHandwritingConverter() {
        // Create a JFrame with a title
        super("Text to Handwriting Converter");

        // Initialize page size (A4: 210mm x 297mm at 72 DPI)
        pageWidth = (int) (210 * 72 / 25.4);
        pageHeight = (int) (297 * 72 / 25.4);

        // Initialize font and font size
        fontSize = 20;  // Start with a default font size

        // Create a JPanel to hold the text and handwritingStyleComboBox
        JPanel panel = new JPanel();

        // Initialize the handwritingStyleComboBox
        handwritingStyleComboBox = new JComboBox<>();
        loadHandwritingStyles(); // Load styles into the combo box
        handwritingStyleComboBox.addItem("Style 1");
        handwritingStyleComboBox.addItem("Style 2");

        // Create a JTextArea for text input
        JTextArea textArea = new JTextArea(10, 30);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(textArea);

        // Create a button to update the text and save as an image
        JButton convertButton = new JButton("Convert and Save");
        convertButton.addActionListener(e -> {
            text = textArea.getText();
            currentPageIndex = 1;

            String selectedStyle = (String) handwritingStyleComboBox.getSelectedItem();

            try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost/txt", "root", "1234")) {
                // String selectedStyle = (String) handwritingStyleComboBox.getSelectedItem(); // This line is not needed here
                loadHandwritingFont(selectedStyle);

                saveTextToDatabase(text, selectedStyle, connection);

                currentPage = new BufferedImage(pageWidth, pageHeight, BufferedImage.TYPE_INT_ARGB);
                currentGraphics = currentPage.createGraphics();
                currentGraphics.setFont(handwritingFont);
                currentGraphics.setColor(Color.BLACK);

                currentGraphics.setColor(Color.WHITE);
                currentGraphics.fillRect(0, 0, pageWidth, pageHeight);
                currentGraphics.setColor(Color.BLACK);

                writeTextToPage(text, currentGraphics, fontSize);

                saveCurrentPage();

                textArea.setText(text);

                JOptionPane.showMessageDialog(this, "Images saved as page" + currentPageIndex + ".png and text saved to database.");
            } catch (SQLException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error connecting to the database: " + ex.getMessage());
            }
        });

        panel.add(scrollPane);
        panel.add(convertButton);

        // Add the panel to the frame
        add(panel);

        // Set frame properties
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void loadHandwritingStyles() {
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost/txt", "root", "1234")) {
            String query = "SELECT DISTINCT style FROM handwriting_styles";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    handwritingStyleComboBox.addItem(resultSet.getString("style"));
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading handwriting styles: " + ex.getMessage());
        }
    }

    private void loadHandwritingFont(String selectedStyle) {
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost/txt", "root", "1234")) {
            String query = "SELECT font_path FROM handwriting_styles WHERE style = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, selectedStyle);
                ResultSet resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    String fontPath = resultSet.getString("font_path");
                    try {
                        handwritingFont = Font.createFont(Font.TRUETYPE_FONT, new File(fontPath));
                        handwritingFont = handwritingFont.deriveFont(Font.PLAIN, fontSize);
                    } catch (FontFormatException | IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading handwriting font: " + ex.getMessage());
        }
    }

    private void saveTextToDatabase(String text, String selectedStyle, Connection connection) throws SQLException {
        String query = "INSERT INTO handwritten_pages (text, style) VALUES (?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, text);
            statement.setString(2, selectedStyle);
            statement.executeUpdate();
        }
    }

    private void writeTextToPage(String text, Graphics2D g2d, int fontSize) {
        FontMetrics fm = g2d.getFontMetrics();
        int lineHeight = fm.getHeight();
        int maxLines = pageHeight / lineHeight;
        currentY = lineHeight;

        for (String line : text.split("\n")) {
            String[] words = line.split(" ");
            StringBuilder currentLine = new StringBuilder();

            for (String word : words) {
                String lineWithWord = currentLine + (currentLine.length() > 0 ? " " : "") + word;
                int lineWidth = fm.stringWidth(lineWithWord);

                if (lineWidth > pageWidth - 20) {
                    if (currentY + lineHeight < maxLines * lineHeight) {
                        g2d.drawString(currentLine.toString(), 10, currentY);
                        currentY += lineHeight;
                        currentLine = new StringBuilder(word);
                    } else {
                        saveCurrentPage();
                        currentPageIndex++;
                        currentPage = new BufferedImage(pageWidth, pageHeight, BufferedImage.TYPE_INT_ARGB);
                        currentGraphics = currentPage.createGraphics();
                        currentGraphics.setFont(handwritingFont);
                        currentGraphics.setColor(Color.BLACK);

                        currentGraphics.setColor(Color.WHITE);
                        currentGraphics.fillRect(0, 0, pageWidth, pageHeight);
                        currentGraphics.setColor(Color.BLACK);

                        currentY = lineHeight;
                        g2d = currentGraphics;
                        g2d.drawString(currentLine.toString(), 10, currentY);
                        currentY += lineHeight;
                        currentLine = new StringBuilder(word);
                    }
                } else {
                    currentLine = new StringBuilder(lineWithWord);
                }
            }

            if (currentY + lineHeight < maxLines * lineHeight) {
                g2d.drawString(currentLine.toString(), 10, currentY);
                currentY += lineHeight;
            } else {
                saveCurrentPage();
                currentPageIndex++;
                currentPage = new BufferedImage(pageWidth, pageHeight, BufferedImage.TYPE_INT_ARGB);
                currentGraphics = currentPage.createGraphics();
                currentGraphics.setFont(handwritingFont);
                currentGraphics.setColor(Color.BLACK);

                currentGraphics.setColor(Color.WHITE);
                currentGraphics.fillRect(0, 0, pageWidth, pageHeight);
                currentGraphics.setColor(Color.BLACK);

                currentY = lineHeight;
                g2d = currentGraphics;
                g2d.drawString(currentLine.toString(), 10, currentY);
                currentY += lineHeight;
            }
        }
    }

    private void saveCurrentPage() {
        try {
            String desktopPath = System.getProperty("user.home") + File.separator + "Desktop";
            String folderName = "t2h";
            File folder = new File(desktopPath, folderName);
            if (!folder.exists()) {
                folder.mkdir();
            }

            File outputFile = new File(folder, "page" + currentPageIndex + ".png");
            ImageIO.write(currentPage, "png", outputFile);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TextToHandwritingConverter());
    }
}
