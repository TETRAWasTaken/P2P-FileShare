package com.kolhey.p2p.gui.controllers;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import com.kolhey.p2p.gui.utils.P2PServiceManager;
import com.kolhey.p2p.gui.utils.UIComponentFactory;
import com.kolhey.p2p.gui.utils.UITheme;
import javax.jmdns.ServiceInfo;
import java.util.Map;

/**
 * Controller for Peer Discovery View
 */
public class PeerDiscoveryController {
    
    private P2PServiceManager serviceManager;
    private ListView<HBox> peerListView;
    private Label peerCountLabel;
    private Map<String, ServiceInfo> lastPeers = Map.of();
    private TabPane tabPane; // Reference to main tab pane for navigation
    private FileTransferController fileTransferController; // Reference for pre-selecting peer

    public PeerDiscoveryController(P2PServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

    public void setTabPaneReference(TabPane tabPane) {
        this.tabPane = tabPane;
    }

    public void setFileTransferController(FileTransferController controller) {
        this.fileTransferController = controller;
    }
    
    public VBox createPeerView() {
        VBox container = new VBox(15);
        container.setPadding(new Insets(20));
        container.setStyle("-fx-background-color: #F5F5F5;");
        
        // Header
        HBox headerBox = createHeaderBox();
        
        // Peer List
        VBox peerListCard = createPeerListCard();
        
        VBox.setVgrow(peerListCard, Priority.ALWAYS);
        container.getChildren().addAll(headerBox, peerListCard);
        
        return container;
    }
    
    private HBox createHeaderBox() {
        HBox headerBox = new HBox(15);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = UIComponentFactory.createHeaderLabel("Available Peers");
        
        peerCountLabel = UIComponentFactory.createInfoLabel("Peers: 0");
        peerCountLabel.setStyle(
            "-fx-font-size: 12pt;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #C4A0E9;"
        );
        
        Button refreshBtn = UIComponentFactory.createSecondaryButton("🔄 Refresh");
        refreshBtn.setOnAction(event -> refreshPeerList());
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        headerBox.getChildren().addAll(titleLabel, spacer, peerCountLabel, refreshBtn);
        return headerBox;
    }
    
    private VBox createPeerListCard() {
        VBox card = UIComponentFactory.createCard("Connected Peers", new Label(""));
        card.getChildren().clear();
        
        peerListView = new ListView<>();
        peerListView.setStyle(
            "-fx-control-inner-background: white;" +
            "-fx-border-color: #E6D5F5;" +
            "-fx-border-radius: 5;" +
            "-fx-background-radius: 5;" +
            "-fx-padding: 5;"
        );
        peerListView.setPrefHeight(400);
        
        // Empty state
        Label emptyLabel = new Label("No peers discovered yet");
        emptyLabel.setStyle(
            "-fx-text-fill: #999999;" +
            "-fx-font-size: 12pt;" +
            "-fx-padding: 40;"
        );
        StackPane emptyPane = new StackPane(emptyLabel);
        emptyPane.setAlignment(Pos.CENTER);
        peerListView.setPlaceholder(emptyPane);
        
        card.getChildren().add(peerListView);
        return card;
    }
    
    public void refreshPeerList() {
        Platform.runLater(() -> {
            try {
                Map<String, ServiceInfo> activePeers = serviceManager.getActivePeers();
                
                if (activePeers.size() != lastPeers.size()) {
                    peerCountLabel.setText("Peers: " + activePeers.size());
                    peerListView.getItems().clear();
                    
                    for (Map.Entry<String, ServiceInfo> entry : activePeers.entrySet()) {
                        HBox peerItem = createPeerItem(entry.getKey(), entry.getValue());
                        peerListView.getItems().add(peerItem);
                    }
                    
                    lastPeers = activePeers;
                }
            } catch (Exception e) {
                System.err.println("Error refreshing peer list: " + e.getMessage());
            }
        });
    }
    
    private HBox createPeerItem(String peerName, ServiceInfo serviceInfo) {
        HBox peerItem = new HBox(15);
        peerItem.setPadding(new Insets(12));
        peerItem.setAlignment(Pos.CENTER_LEFT);
        peerItem.setStyle(
            "-fx-background-color: #F5F5F5;" +
            "-fx-border-color: #E6D5F5;" +
            "-fx-border-radius: 5;" +
            "-fx-background-radius: 5;" +
            "-fx-border-width: 0 0 1 0;"
        );
        
        // Status indicator
        Circle statusIndicator = new Circle(6);
        statusIndicator.setFill(UITheme.SUCCESS);
        
        // Peer Info
        VBox infoBox = new VBox(3);
        
        Label peerLabel = new Label(peerName);
        peerLabel.setStyle(
            "-fx-font-size: 12pt;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #2C2C2C;"
        );
        
        String[] addresses = serviceInfo.getHostAddresses();
        String ipAddress = (addresses != null && addresses.length > 0) ? addresses[0] : "Unknown";
        Label ipLabel = new Label("IP: " + ipAddress);
        ipLabel.setStyle(
            "-fx-font-size: 10pt;" +
            "-fx-text-fill: #666666;"
        );
        
        String quicPort = serviceInfo.getPropertyString("quic_port");
        String wsPort = serviceInfo.getPropertyString("ws_port");
        Label portsLabel = new Label(
            "Ports: QUIC=" + (quicPort != null ? quicPort : "N/A") +
            ", WS=" + (wsPort != null ? wsPort : "N/A")
        );
        portsLabel.setStyle(
            "-fx-font-size: 10pt;" +
            "-fx-text-fill: #666666;"
        );
        
        infoBox.getChildren().addAll(peerLabel, ipLabel, portsLabel);
        
        // Action buttons
        HBox actionBox = new HBox(8);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button connectBtn = UIComponentFactory.createSmallButton("Connect");
        Button sendFileBtn = UIComponentFactory.createSmallButton("Send File");
        
        connectBtn.setOnAction(event -> handleConnect(peerName, ipAddress));
        sendFileBtn.setOnAction(event -> handleSendFile(peerName, ipAddress));
        
        actionBox.getChildren().addAll(connectBtn, sendFileBtn);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        peerItem.getChildren().addAll(statusIndicator, infoBox, spacer, actionBox);
        return peerItem;
    }
    
    private void handleConnect(String peerName, String ipAddress) {
        // Get service info for protocol display
        Map<String, ServiceInfo> activePeers = serviceManager.getActivePeers();
        ServiceInfo serviceInfo = activePeers.get(peerName);

        String quicPort = serviceInfo != null ? serviceInfo.getPropertyString("quic_port") : "N/A";
        String wsPort = serviceInfo != null ? serviceInfo.getPropertyString("ws_port") : "N/A";

        // Create detailed connection dialog
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Peer Connection Details");
        alert.setHeaderText("Connecting to: " + peerName);

        String content = String.format(
            "Peer Name: %s\n" +
            "IP Address: %s\n" +
            "\n" +
            "Available Protocols:\n" +
            "  • QUIC Port: %s\n" +
            "  • WebSocket Port: %s\n" +
            "\n" +
            "Authentication Status: Trusted (TOFU)\n" +
            "\n" +
            "Click OK to proceed with connection.",
            peerName, ipAddress, quicPort, wsPort
        );

        alert.setContentText(content);
        alert.showAndWait();
    }

    private void handleSendFile(String peerName, String ipAddress) {
        // Store the peer info for the transfer controller
        System.out.println("[PeerDiscovery] Send file to: " + peerName + " at " + ipAddress);

        if (fileTransferController != null) {
            fileTransferController.setSelectedPeer(peerName);
        }

        if (tabPane != null) {
            // Switch to Transfer Files tab (index 2)
            tabPane.getSelectionModel().select(2);
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Transfer Files Ready");
        alert.setHeaderText("Send file to: " + peerName);
        alert.setContentText(
            "You are now in the Transfer Files tab.\n" +
            "Select a file and confirm the transfer.\n\n" +
            "Peer: " + peerName + " (" + ipAddress + ")"
        );
        alert.showAndWait();
    }
}
