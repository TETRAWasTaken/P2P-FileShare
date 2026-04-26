package com.kolhey.p2p.gui.controllers;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import com.kolhey.p2p.gui.utils.P2PServiceManager;
import com.kolhey.p2p.gui.utils.TransferEvent;
import com.kolhey.p2p.gui.utils.UIComponentFactory;
import com.kolhey.p2p.gui.utils.UITheme;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Observable;
import java.util.Observer;

/**
 * Main Window Controller - Orchestrates all GUI components
 */
public class MainWindowController implements Observer {
    
    private P2PServiceManager serviceManager;
    private PeerDiscoveryController peerController;
    private FileTransferController fileTransferController;
    private ExecutorService executorService;
    private Timeline discoveryUpdateTimeline;
    
    // UI Components
    private Label statusLabel;
    private Label nodeNameLabel;
    private Circle statusIndicator;
    private ProgressIndicator loadingIndicator;
    private TabPane tabPane;
    private TextField quicPortField;
    private TextField wsPortField;
    
    // Port configuration
    private int configuredQuicPort;
    private int configuredWsPort;
    
    public MainWindowController() {
        this(9000, 8080);  // Default ports
    }

    public MainWindowController(int quicPort, int wsPort) {
        this.serviceManager = P2PServiceManager.getInstance();
        this.serviceManager.addObserver(this);
        this.executorService = Executors.newFixedThreadPool(2);
        this.configuredQuicPort = quicPort;
        this.configuredWsPort = wsPort;
    }

    public void setControllerReferences() {
        if (peerController != null && tabPane != null && fileTransferController != null) {
            peerController.setTabPaneReference(tabPane);
            peerController.setFileTransferController(fileTransferController);
        }
    }
    
    public VBox createMainLayout() {
        VBox mainLayout = new VBox();
        mainLayout.setStyle("-fx-background-color: #F5F5F5;");
        mainLayout.setFillWidth(true);
        
        // Header
        HBox header = createHeader();
        
        // Tab Pane with different views
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        // Tab 1: Dashboard
        Tab dashboardTab = createDashboardTab();
        
        // Tab 2: Peer Discovery
        peerController = new PeerDiscoveryController(serviceManager);
        Tab peersTab = new Tab("Peers", peerController.createPeerView());
        peersTab.setStyle("-fx-text-fill: #2C2C2C;");
        
        // Tab 3: File Transfer
        fileTransferController = new FileTransferController(serviceManager, executorService);
        Tab transferTab = new Tab("Transfer Files", fileTransferController.createTransferView());
        transferTab.setStyle("-fx-text-fill: #2C2C2C;");
        
        // Tab 4: Settings
        Tab settingsTab = createSettingsTab();
        
        tabPane.getTabs().addAll(dashboardTab, peersTab, transferTab, settingsTab);

        // Set up controller references for tab navigation
        setControllerReferences();

        // Apply tab styling
        tabPane.setStyle(
            "-fx-tab-min-width: 120;" +
            "-fx-tab-max-width: 150;"
        );
        
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        mainLayout.getChildren().addAll(header, new Separator(), tabPane);
        
        // Start auto-refresh of peers
        startDiscoveryUpdater();
        
        return mainLayout;
    }
    
    private HBox createHeader() {
        HBox header = new HBox(20);
        header.setPadding(new Insets(15, 20, 15, 20));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle(
            "-fx-background-color: linear-gradient(to right, #E6D5F5, #F5F5F5);" +
            "-fx-border-color: #C4A0E9;" +
            "-fx-border-width: 0 0 2 0;"
        );
        
        // Title
        Label titleLabel = UIComponentFactory.createTitleLabel("🔐 P2P File Sharing");
        
        // Status Indicator
        HBox statusBox = createStatusBox();
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Start/Stop Button
        Button toggleButton = createToggleButton();
        
        header.getChildren().addAll(titleLabel, statusBox, spacer, toggleButton);
        return header;
    }
    
    private HBox createStatusBox() {
        HBox statusBox = new HBox(10);
        statusBox.setAlignment(Pos.CENTER);
        statusBox.setStyle(
            "-fx-background-color: white;" +
            "-fx-border-color: #E6D5F5;" +
            "-fx-border-radius: 20;" +
            "-fx-background-radius: 20;" +
            "-fx-padding: 8 15 8 15;"
        );
        
        statusIndicator = new Circle(8);
        statusIndicator.setFill(UITheme.ERROR);
        
        VBox textBox = new VBox(2);
        textBox.setAlignment(Pos.CENTER_LEFT);
        
        nodeNameLabel = UIComponentFactory.createInfoLabel("Node: -");
        nodeNameLabel.setStyle("-fx-font-weight: bold;");
        statusLabel = UIComponentFactory.createInfoLabel("Status: Offline");
        
        textBox.getChildren().addAll(nodeNameLabel, statusLabel);
        
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setPrefSize(16, 16);
        loadingIndicator.setProgress(-1);
        loadingIndicator.setVisible(false);
        
        statusBox.getChildren().addAll(statusIndicator, textBox, loadingIndicator);
        return statusBox;
    }
    
    private Button createToggleButton() {
        Button toggleButton = UIComponentFactory.createPrimaryButton("▶ Start Node");
        toggleButton.setPrefWidth(150);
        toggleButton.setOnAction(event -> {
            if (serviceManager.isNodeRunning()) {
                stopNode();
                toggleButton.setText("▶ Start Node");
            } else {
                if (!applySettingsPorts()) {
                    return;
                }
                startNode(toggleButton);
            }
        });
        return toggleButton;
    }
    
    private Tab createDashboardTab() {
        VBox dashboardContent = new VBox(15);
        dashboardContent.setPadding(new Insets(20));
        dashboardContent.setStyle("-fx-background-color: #F5F5F5;");
        
        // Welcome Section
        VBox welcomeCard = UIComponentFactory.createCard("Welcome to P2P File Sharing",
            new Label("Secure, decentralized file sharing over your local network."));
        
        // Quick Stats
        HBox statsBox = createStatsBox();
        
        // Quick Access
        VBox quickAccessCard = createQuickAccessCard();
        
        ScrollPane scrollPane = new ScrollPane(
            new VBox(15,
                welcomeCard,
                statsBox,
                quickAccessCard
            )
        );
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #F5F5F5;");
        
        Tab dashboardTab = new Tab("Dashboard", scrollPane);
        dashboardTab.setStyle("-fx-text-fill: #2C2C2C;");
        return dashboardTab;
    }
    
    private HBox createStatsBox() {
        HBox statsBox = new HBox(15);
        statsBox.setPadding(new Insets(0));
        
        VBox peersCard = createStatCard("📡", "Connected Peers", "0", "#C4A0E9");
        VBox transfersCard = createStatCard("📤", "Active Transfers", "0", "#9575CD");
        VBox securityCard = createStatCard("🔒", "Encrypted", "Yes", "#7E57C2");
        
        statsBox.getChildren().addAll(peersCard, transfersCard, securityCard);
        HBox.setHgrow(peersCard, Priority.ALWAYS);
        HBox.setHgrow(transfersCard, Priority.ALWAYS);
        HBox.setHgrow(securityCard, Priority.ALWAYS);
        
        return statsBox;
    }
    
    private VBox createStatCard(String icon, String title, String value, String color) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(20));
        card.setAlignment(Pos.CENTER);
        card.setStyle(
            "-fx-background-color: " + color + ";" +
            "-fx-border-radius: 10;" +
            "-fx-background-radius: 10;" +
            "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.15), 5, 0.0, 0, 2);"
        );
        
        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 32pt;");
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle(
            "-fx-font-size: 12pt;" +
            "-fx-text-fill: white;" +
            "-fx-opacity: 0.8;"
        );
        
        Label valueLabel = new Label(value);
        valueLabel.setStyle(
            "-fx-font-size: 24pt;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: white;"
        );
        
        card.getChildren().addAll(iconLabel, titleLabel, valueLabel);
        return card;
    }
    
    private VBox createQuickAccessCard() {
        VBox card = UIComponentFactory.createCard("Quick Access", new Label(""));
        card.getChildren().clear();
        
        Label titleLabel = UIComponentFactory.createHeaderLabel("Quick Access");
        card.getChildren().add(titleLabel);
        
        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(10));
        
        Button selectFileBtn = UIComponentFactory.createPrimaryButton("📁 Select File to Send");
        Button browseDownloads = UIComponentFactory.createSecondaryButton("📂 Open Downloads");
        
        selectFileBtn.setOnAction(event -> fileTransferController.openFileChooser());
        
        buttonBox.getChildren().addAll(selectFileBtn, browseDownloads);
        card.getChildren().add(buttonBox);
        
        return card;
    }
    
    private Tab createSettingsTab() {
        VBox settingsContent = new VBox(15);
        settingsContent.setPadding(new Insets(20));
        settingsContent.setStyle("-fx-background-color: #F5F5F5;");
        
        // Node Configuration
        VBox configCard = UIComponentFactory.createCard("Node Configuration", new Label(""));
        configCard.getChildren().clear();
        
        Label configTitle = UIComponentFactory.createHeaderLabel("Node Settings");
        configCard.getChildren().add(configTitle);
        
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setPadding(new Insets(10));
        
        Label quicLabel = new Label("QUIC Port:");
        quicPortField = UIComponentFactory.createStyledTextField("9000");
        quicPortField.setText(String.valueOf(configuredQuicPort));
        
        Label wsLabel = new Label("WebSocket Port:");
        wsPortField = UIComponentFactory.createStyledTextField("8080");
        wsPortField.setText(String.valueOf(configuredWsPort));
        
        grid.add(quicLabel, 0, 0);
        grid.add(quicPortField, 1, 0);
        grid.add(wsLabel, 0, 1);
        grid.add(wsPortField, 1, 1);
        
        configCard.getChildren().add(grid);
        
        // Information Card
        VBox infoCard = UIComponentFactory.createCard("Information", new Label(""));
        infoCard.getChildren().clear();
        
        Label infoTitle = UIComponentFactory.createHeaderLabel("Application Info");
        Label versionLabel = UIComponentFactory.createInfoLabel("Version: 1.0.0");
        Label authorLabel = UIComponentFactory.createInfoLabel("P2P Encrypted File Sharing");
        Label licenseLabel = UIComponentFactory.createInfoLabel("Licensed under MIT");
        
        infoCard.getChildren().addAll(infoTitle, new Separator(), versionLabel, authorLabel, licenseLabel);
        
        ScrollPane scrollPane = new ScrollPane(new VBox(15, configCard, infoCard));
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #F5F5F5;");
        
        Tab settingsTab = new Tab("Settings", scrollPane);
        settingsTab.setStyle("-fx-text-fill: #2C2C2C;");
        return settingsTab;
    }
    
    private void startNode(Button toggleButton) {
        // Show protocol selection dialog
        showProtocolSelectionDialog();

        loadingIndicator.setVisible(true);
        executorService.execute(() -> {
            try {
                String nodeName = "Node-" + UUID.randomUUID().toString().substring(0, 4);
                serviceManager.startNode(nodeName, configuredQuicPort, configuredWsPort);

                Platform.runLater(() -> {
                    statusIndicator.setFill(UITheme.SUCCESS);
                    statusLabel.setText("Status: Online");
                    nodeNameLabel.setText("Node: " + nodeName);
                    loadingIndicator.setVisible(false);
                    if (toggleButton != null) {
                        toggleButton.setText("⏹ Stop Node");
                    }
                });

                System.out.println("[MainWindow] Node started: " + nodeName);
            } catch (Exception e) {
                Platform.runLater(() -> {
                    loadingIndicator.setVisible(false);
                    if (toggleButton != null) {
                        toggleButton.setText("▶ Start Node");
                    }
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error");
                    alert.setHeaderText("Failed to start node");
                    alert.setContentText(e.getMessage());
                    alert.showAndWait();
                });
                e.printStackTrace();
            }
        });
    }

    private void showProtocolSelectionDialog() {
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle("Select Communication Protocol");
        dialog.setHeaderText("Choose the protocol for file transfers");

        VBox content = new VBox(15);
        content.setPadding(new Insets(10));

        Label descLabel = new Label("Select which protocol you prefer for file transfers:");
        descLabel.setWrapText(true);

        ToggleGroup protocolGroup = new ToggleGroup();

        VBox wsOption = new VBox(5);
        RadioButton wsRadio = new RadioButton("WebSocket (WS)");
        wsRadio.setToggleGroup(protocolGroup);
        wsRadio.setSelected(true);
        Label wsDesc = new Label("• TCP-based, more compatible\n• Industry standard\n• Works behind firewalls");
        wsDesc.setStyle("-fx-font-size: 10pt; -fx-text-fill: #666666;");
        wsOption.getChildren().addAll(wsRadio, wsDesc);

        VBox quicOption = new VBox(5);
        RadioButton quicRadio = new RadioButton("QUIC (UDP)");
        quicRadio.setToggleGroup(protocolGroup);
        Label quicDesc = new Label("• UDP-based, lower latency\n• Faster for unstable networks\n• Modern protocol");
        quicDesc.setStyle("-fx-font-size: 10pt; -fx-text-fill: #666666;");
        quicOption.getChildren().addAll(quicRadio, quicDesc);

        content.getChildren().addAll(descLabel, new Separator(), wsOption, quicOption);

        dialog.getDialogPane().setContent(content);
        dialog.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            RadioButton selected = (RadioButton) protocolGroup.getSelectedToggle();
            if (selected != null) {
                String protocol = selected.getText().contains("QUIC") ? "QUIC" : "WS";
                serviceManager.setProtocolPreference(protocol);
                System.out.println("[MainWindow] Protocol preference set to: " + protocol);
            }
        }
    }

    private boolean applySettingsPorts() {
        try {
            int quicPort = parsePort(quicPortField != null ? quicPortField.getText() : String.valueOf(configuredQuicPort), "QUIC");
            int wsPort = parsePort(wsPortField != null ? wsPortField.getText() : String.valueOf(configuredWsPort), "WebSocket");

            configuredQuicPort = quicPort;
            configuredWsPort = wsPort;
            return true;
        } catch (IllegalArgumentException e) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Invalid Port Configuration");
            alert.setHeaderText("Please fix port settings");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
            return false;
        }
    }

    private int parsePort(String rawValue, String label) {
        String value = rawValue != null ? rawValue.trim() : "";
        if (value.isEmpty()) {
            throw new IllegalArgumentException(label + " port cannot be empty.");
        }

        int port;
        try {
            port = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " port must be a number.");
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(label + " port must be between 1 and 65535.");
        }
        return port;
    }
    
    private void stopNode() {
        serviceManager.stopNode();
        statusIndicator.setFill(UITheme.ERROR);
        statusLabel.setText("Status: Offline");
        nodeNameLabel.setText("Node: -");
    }
    
    private void startDiscoveryUpdater() {
        discoveryUpdateTimeline = new Timeline(
            new KeyFrame(Duration.seconds(2), event -> {
                if (peerController != null) {
                    peerController.refreshPeerList();
                }
            })
        );
        discoveryUpdateTimeline.setCycleCount(Animation.INDEFINITE);
        discoveryUpdateTimeline.play();
    }
    
    public void shutdown() {
        if (discoveryUpdateTimeline != null) {
            discoveryUpdateTimeline.stop();
        }
        stopNode();
        serviceManager.deleteObserver(this);
        executorService.shutdownNow();
        try {
            executorService.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Override
    public void update(Observable o, Object arg) {
        Platform.runLater(() -> {
            if (arg instanceof TransferEvent) {
                if (fileTransferController != null) {
                    fileTransferController.handleTransferEvent((TransferEvent) arg);
                }
                return;
            }

            if (arg instanceof com.kolhey.p2p.gui.utils.ConnectionEvent) {
                handleConnectionEvent((com.kolhey.p2p.gui.utils.ConnectionEvent) arg);
                return;
            }

            if ("node_started".equals(arg)) {
                System.out.println("[MainWindow] Node started event received");
            } else if ("node_stopped".equals(arg)) {
                System.out.println("[MainWindow] Node stopped event received");
            }
        });
    }

    private void handleConnectionEvent(com.kolhey.p2p.gui.utils.ConnectionEvent event) {
        System.out.println("[MainWindow] Connection Event: " + event);

        switch (event.getType()) {
            case ATTEMPTING_CONNECTION:
                System.out.println("[MainWindow] Attempting connection to " + event.getPeerName() +
                                   " via " + event.getProtocol());
                break;
            case PEER_CONNECTED:
                System.out.println("[MainWindow] Connected to " + event.getPeerName() +
                                   " (Auth: " + event.getAuthenticationStatus() + ")");
                break;
            case PEER_DISCONNECTED:
                System.out.println("[MainWindow] Disconnected from " + event.getPeerName());
                break;
            case AUTHENTICATION_SUCCESS:
                System.out.println("[MainWindow] Authentication successful with " + event.getPeerName());
                break;
            case AUTHENTICATION_FAILED:
                System.out.println("[MainWindow] Authentication failed with " + event.getPeerName());
                break;
        }
    }
}
