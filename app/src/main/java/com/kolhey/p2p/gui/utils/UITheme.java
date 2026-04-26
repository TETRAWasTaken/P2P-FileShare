package com.kolhey.p2p.gui.utils;

import javafx.scene.paint.Color;

/**
 * UI Theme configuration for the P2P File Sharing application.
 * Color scheme: Lilac and Off-White
 */
public class UITheme {
    
    // Primary Colors
    public static final Color PRIMARY_LILAC = Color.web("#C4A0E9");
    public static final Color LIGHT_LILAC = Color.web("#E6D5F5");
    public static final Color DARK_LILAC = Color.web("#9575CD");
    public static final Color OFF_WHITE = Color.web("#F5F5F5");
    public static final Color LIGHT_GRAY = Color.web("#E8E8E8");
    public static final Color DARK_GRAY = Color.web("#424242");
    public static final Color TEXT_DARK = Color.web("#2C2C2C");
    
    // Semantic Colors
    public static final Color SUCCESS = Color.web("#4CAF50");
    public static final Color WARNING = Color.web("#FF9800");
    public static final Color ERROR = Color.web("#F44336");
    public static final Color INFO = Color.web("#2196F3");

    // Connection State Colors
    public static final Color CONNECTING = Color.web("#FFC107");
    public static final Color AUTHENTICATED = Color.web("#4CAF50");
    public static final Color AUTHENTICATION_FAILED = Color.web("#F44336");
    public static final Color NEW_PEER = Color.web("#2196F3");
    
    // CSS Stylesheet
    public static final String STYLESHEET = 
        "/* Root Styling */\n" +
        ".root {\n" +
        "    -fx-font-family: 'Segoe UI', 'Arial', sans-serif;\n" +
        "    -fx-font-size: 11pt;\n" +
        "}\n" +
        "\n" +
        "/* Main Container */\n" +
        ".main-container {\n" +
        "    -fx-background-color: #F5F5F5;\n" +
        "}\n" +
        "\n" +
        "/* Buttons */\n" +
        ".btn-primary {\n" +
        "    -fx-background-color: #C4A0E9;\n" +
        "    -fx-text-fill: white;\n" +
        "    -fx-font-size: 12pt;\n" +
        "    -fx-padding: 10px 20px;\n" +
        "    -fx-border-radius: 5;\n" +
        "    -fx-background-radius: 5;\n" +
        "    -fx-cursor: hand;\n" +
        "}\n" +
        "\n" +
        ".btn-primary:hover {\n" +
        "    -fx-background-color: #9575CD;\n" +
        "}\n" +
        "\n" +
        ".btn-primary:pressed {\n" +
        "    -fx-background-color: #7E57C2;\n" +
        "}\n" +
        "\n" +
        ".btn-secondary {\n" +
        "    -fx-background-color: #E8E8E8;\n" +
        "    -fx-text-fill: #2C2C2C;\n" +
        "    -fx-font-size: 11pt;\n" +
        "    -fx-padding: 8px 16px;\n" +
        "    -fx-border-radius: 5;\n" +
        "    -fx-background-radius: 5;\n" +
        "    -fx-cursor: hand;\n" +
        "}\n" +
        "\n" +
        ".btn-secondary:hover {\n" +
        "    -fx-background-color: #D8D8D8;\n" +
        "}\n" +
        "\n" +
        "/* Text Fields */\n" +
        ".text-field {\n" +
        "    -fx-font-size: 11pt;\n" +
        "    -fx-padding: 8px;\n" +
        "    -fx-border-color: #E6D5F5;\n" +
        "    -fx-border-radius: 5;\n" +
        "    -fx-background-radius: 5;\n" +
        "}\n" +
        "\n" +
        ".text-field:focused {\n" +
        "    -fx-border-color: #C4A0E9;\n" +
        "    -fx-border-width: 2;\n" +
        "}\n" +
        "\n" +
        "/* Labels */\n" +
        ".label-title {\n" +
        "    -fx-font-size: 18pt;\n" +
        "    -fx-font-weight: bold;\n" +
        "    -fx-text-fill: #C4A0E9;\n" +
        "}\n" +
        "\n" +
        ".label-header {\n" +
        "    -fx-font-size: 14pt;\n" +
        "    -fx-font-weight: bold;\n" +
        "    -fx-text-fill: #2C2C2C;\n" +
        "}\n" +
        "\n" +
        ".label-info {\n" +
        "    -fx-font-size: 11pt;\n" +
        "    -fx-text-fill: #424242;\n" +
        "}\n" +
        "\n" +
        "/* Cards/Panels */\n" +
        ".card {\n" +
        "    -fx-background-color: white;\n" +
        "    -fx-border-color: #E6D5F5;\n" +
        "    -fx-border-radius: 8;\n" +
        "    -fx-background-radius: 8;\n" +
        "    -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.1), 4, 0.0, 0, 2);\n" +
        "    -fx-padding: 15;\n" +
        "}\n" +
        "\n" +
        "/* Tabs */\n" +
        ".tab-pane {\n" +
        "    -fx-tab-min-width: 100;\n" +
        "}\n" +
        "\n" +
        ".tab {\n" +
        "    -fx-background-color: #F5F5F5;\n" +
        "    -fx-text-fill: #424242;\n" +
        "}\n" +
        "\n" +
        ".tab:selected {\n" +
        "    -fx-background-color: #C4A0E9;\n" +
        "    -fx-text-fill: white;\n" +
        "}\n" +
        "\n" +
        "/* Progress Bar */\n" +
        ".progress-bar {\n" +
        "    -fx-accent: #C4A0E9;\n" +
        "}\n" +
        "\n" +
        "/* Scroll Pane */\n" +
        ".scroll-pane {\n" +
        "    -fx-background-color: #F5F5F5;\n" +
        "}\n" +
        "\n" +
        "/* ListView */\n" +
        ".list-view {\n" +
        "    -fx-background-color: white;\n" +
        "    -fx-border-color: #E6D5F5;\n" +
        "    -fx-border-radius: 5;\n" +
        "}\n" +
        "\n" +
        ".list-cell {\n" +
        "    -fx-padding: 8px;\n" +
        "    -fx-border-color: transparent;\n" +
        "}\n" +
        "\n" +
        ".list-cell:filled:selected {\n" +
        "    -fx-background-color: #E6D5F5;\n" +
        "}\n" +
        "\n" +
        ".list-cell:filled:hover {\n" +
        "    -fx-background-color: #F0E8F8;\n" +
        "}\n" +
        "\n" +
        "/* Separators */\n" +
        ".separator {\n" +
        "    -fx-padding: 10;\n" +
        "}\n" +
        "\n" +
        "/* VBox and HBox */\n" +
        ".vbox, .hbox {\n" +
        "    -fx-spacing: 10;\n" +
        "    -fx-padding: 10;\n" +
        "}\n" +
        "\n" +
        "/* Status Indicator */\n" +
        ".status-online {\n" +
        "    -fx-fill: #4CAF50;\n" +
        "}\n" +
        "\n" +
        ".status-offline {\n" +
        "    -fx-fill: #F44336;\n" +
        "}\n" +
        "\n" +
        ".status-connecting {\n" +
        "    -fx-fill: #FF9800;\n" +
        "}\n";
    
    /**
     * Get the color as a hex string for CSS
     */
    public static String toHex(Color color) {
        return String.format("#%02X%02X%02X", 
            (int)(color.getRed() * 255),
            (int)(color.getGreen() * 255),
            (int)(color.getBlue() * 255));
    }
}
