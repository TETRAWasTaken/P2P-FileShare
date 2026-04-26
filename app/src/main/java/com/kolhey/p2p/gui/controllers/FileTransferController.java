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
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import com.kolhey.p2p.gui.utils.P2PServiceManager;
import com.kolhey.p2p.gui.utils.TransferEvent;
import com.kolhey.p2p.gui.utils.UIComponentFactory;
import javax.jmdns.ServiceInfo;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Controller for File Transfer View
 */
public class FileTransferController {
    
    private P2PServiceManager serviceManager;
    private ExecutorService executorService;
    private File selectedFile;
    private ListView<HBox> transferListView;
    private ComboBox<String> recipientCombo;
    private final Map<String, TransferItemView> transferItems = new HashMap<>();
    private Label protocolLabel; // To display current protocol

    public FileTransferController(P2PServiceManager serviceManager, ExecutorService executorService) {
        this.serviceManager = serviceManager;
        this.executorService = executorService;
    }

    public void setSelectedPeer(String peerName) {
        if (recipientCombo != null) {
            recipientCombo.setValue(peerName);
        }
    }
    
    public VBox createTransferView() {
        VBox container = new VBox(15);
        container.setPadding(new Insets(20));
        container.setStyle("-fx-background-color: #F5F5F5;");
        
        // Send File Section
        VBox sendSection = createSendFileSection();
        
        // Active Transfers Section
        VBox transfersSection = createTransfersSection();
        
        VBox.setVgrow(transfersSection, Priority.ALWAYS);
        
        ScrollPane scrollPane = new ScrollPane(new VBox(15, sendSection, transfersSection));
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #F5F5F5;");
        
        container.getChildren().add(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        return container;
    }
    
    private VBox createSendFileSection() {
        VBox card = UIComponentFactory.createCard("Send File", new Label(""));
        card.getChildren().clear();
        
        Label titleLabel = UIComponentFactory.createHeaderLabel("Send File to Peer");
        card.getChildren().add(titleLabel);
        
        // File Selection Area
        VBox fileSelectionArea = new VBox(10);
        fileSelectionArea.setPadding(new Insets(20));
        fileSelectionArea.setStyle(
            "-fx-border-color: #E6D5F5;" +
            "-fx-border-style: dashed;" +
            "-fx-border-radius: 5;" +
            "-fx-background-color: white;" +
            "-fx-background-radius: 5;"
        );
        fileSelectionArea.setAlignment(Pos.CENTER);
        
        Label dragDropLabel = new Label("📁 Drag & Drop or Click to Select File");
        dragDropLabel.setStyle(
            "-fx-font-size: 12pt;" +
            "-fx-text-fill: #999999;"
        );
        
        Label selectedFileLabel = new Label("No file selected");
        selectedFileLabel.setStyle(
            "-fx-font-size: 11pt;" +
            "-fx-text-fill: #C4A0E9;" +
            "-fx-font-weight: bold;"
        );
        
        Button selectFileBtn = UIComponentFactory.createPrimaryButton("📂 Select File");
        selectFileBtn.setOnAction(event -> {
            File file = openFileChooser(new Stage());
            if (file != null) {
                selectedFile = file;
                selectedFileLabel.setText("Selected: " + file.getName() + " (" + formatFileSize(file.length()) + ")");
                dragDropLabel.setText("✓ File ready to send");
                dragDropLabel.setStyle("-fx-text-fill: #4CAF50;");
            }
        });
        
        fileSelectionArea.getChildren().addAll(dragDropLabel, selectedFileLabel, selectFileBtn);
        card.getChildren().add(fileSelectionArea);
        
        // Recipient Selection
        HBox recipientBox = createRecipientSelectionBox();
        card.getChildren().add(recipientBox);
        
        // Send Button
        Button sendBtn = UIComponentFactory.createPrimaryButton("🚀 Send File");
        sendBtn.setPrefWidth(Double.MAX_VALUE);
        sendBtn.setOnAction(event -> handleSendFile());
        
        card.getChildren().add(sendBtn);
        
        return card;
    }
    
    private HBox createRecipientSelectionBox() {
        VBox vBox = new VBox(10);
        vBox.setPadding(new Insets(10, 0, 0, 0));

        // Recipient selection
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);

        Label recipientLabel = new Label("Recipient:");
        recipientLabel.setStyle(
            "-fx-font-size: 11pt;" +
            "-fx-font-weight: bold;"
        );

        recipientCombo = UIComponentFactory.createStyledComboBox();
        recipientCombo.setPrefWidth(250);
        recipientCombo.setPromptText("Select a peer...");

        refreshRecipientList();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = UIComponentFactory.createSecondaryButton("🔄");
        refreshBtn.setOnAction(event -> refreshRecipientList());

        box.getChildren().addAll(recipientLabel, recipientCombo, spacer, refreshBtn);

        // Protocol display
        HBox protocolBox = new HBox(10);
        protocolBox.setAlignment(Pos.CENTER_LEFT);
        protocolBox.setPadding(new Insets(5, 0, 0, 0));

        Label protocolTitleLabel = new Label("Protocol:");
        protocolTitleLabel.setStyle(
            "-fx-font-size: 11pt;" +
            "-fx-font-weight: bold;"
        );

        protocolLabel = new Label(serviceManager.getProtocolPreference());
        protocolLabel.setStyle(
            "-fx-font-size: 11pt;" +
            "-fx-text-fill: #C4A0E9;" +
            "-fx-font-weight: bold;"
        );

        protocolBox.getChildren().addAll(protocolTitleLabel, protocolLabel);

        vBox.getChildren().addAll(box, protocolBox);
        return new HBox(vBox);
    }

    private void refreshRecipientList() {
        Map<String, ServiceInfo> activePeers = serviceManager.getActivePeers();
        String currentSelection = recipientCombo != null ? recipientCombo.getValue() : null;
        String localNodeName = serviceManager.getNodeName();

        List<String> peerNames = new ArrayList<>();
        for (String peerName : activePeers.keySet()) {
            if (localNodeName == null || !localNodeName.equals(peerName)) {
                peerNames.add(peerName);
            }
        }
        peerNames.sort(Comparator.naturalOrder());

        recipientCombo.getItems().setAll(peerNames);
        recipientCombo.setDisable(peerNames.isEmpty());
        recipientCombo.setPromptText(peerNames.isEmpty() ? "No peers available" : "Select a peer...");

        if (currentSelection != null && peerNames.contains(currentSelection)) {
            recipientCombo.setValue(currentSelection);
        } else {
            recipientCombo.setValue(null);
        }
    }
    
    private VBox createTransfersSection() {
        VBox card = UIComponentFactory.createCard("Active Transfers", new Label(""));
        card.getChildren().clear();
        
        Label titleLabel = UIComponentFactory.createHeaderLabel("Transfer Progress");
        card.getChildren().add(titleLabel);
        
        transferListView = new ListView<>();
        transferListView.setStyle(
            "-fx-control-inner-background: white;" +
            "-fx-border-color: #E6D5F5;" +
            "-fx-border-radius: 5;" +
            "-fx-background-radius: 5;"
        );
        transferListView.setPrefHeight(200);
        
        // Empty state
        Label emptyLabel = new Label("No active transfers");
        emptyLabel.setStyle(
            "-fx-text-fill: #999999;" +
            "-fx-font-size: 11pt;"
        );
        StackPane emptyPane = new StackPane(emptyLabel);
        emptyPane.setAlignment(Pos.CENTER);
        transferListView.setPlaceholder(emptyPane);
        
        card.getChildren().add(transferListView);
        VBox.setVgrow(transferListView, Priority.ALWAYS);
        
        return card;
    }
    
    private void handleSendFile() {
        if (selectedFile == null) {
            showAlert(Alert.AlertType.WARNING, "No File Selected", 
                "Please select a file first.");
            return;
        }

        if (recipientCombo == null || recipientCombo.getValue() == null || recipientCombo.getValue().isBlank()) {
            showAlert(Alert.AlertType.WARNING, "No Recipient Selected",
                "Please select a recipient peer before sending.");
            return;
        }

        String recipient = recipientCombo.getValue();
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm File Transfer");
        confirmation.setHeaderText("Send file to selected peer?");
        confirmation.setContentText(
            "Recipient: " + recipient + "\n" +
            "File: " + selectedFile.getName() + "\n" +
            "Size: " + formatFileSize(selectedFile.length())
        );

        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        File fileToSend = selectedFile;
        TransferItemView transferItemView = createTransferItem(fileToSend.getName(), "To: " + recipient);
        transferListView.getItems().add(0, transferItemView.row());
        transferItemView.progressBar().setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        transferItemView.statusLabel().setText("Sending...");
        transferItemView.percentLabel().setText("...");
        
        executorService.execute(() -> {
            try {
                serviceManager.sendFileToPeer(recipient, fileToSend);
                Platform.runLater(() -> {
                    transferItemView.progressBar().setProgress(1.0);
                    transferItemView.statusLabel().setText("Completed");
                    transferItemView.percentLabel().setText("100%");
                    showAlert(Alert.AlertType.INFORMATION, "Transfer Complete",
                        "File sent successfully to " + recipient + ": " + fileToSend.getName());
                    selectedFile = null;
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    transferItemView.progressBar().setProgress(0.0);
                    transferItemView.statusLabel().setText("Failed");
                    transferItemView.percentLabel().setText("0%");
                    showAlert(Alert.AlertType.ERROR, "Transfer Failed",
                        "Could not send file to " + recipient + "\nReason: " + e.getMessage());
                });
            }
        });
    }
    
    private TransferItemView createTransferItem(String fileName, String peerDisplayText) {
        HBox transferItem = new HBox(10);
        transferItem.setPadding(new Insets(12));
        transferItem.setAlignment(Pos.CENTER_LEFT);
        transferItem.setStyle(
            "-fx-background-color: #F5F5F5;" +
            "-fx-border-color: #E6D5F5;" +
            "-fx-border-radius: 5;" +
            "-fx-background-radius: 5;"
        );
        
        VBox detailsBox = new VBox(2);
        Label fileLabel = new Label("📄 " + fileName);
        fileLabel.setStyle(
            "-fx-font-size: 11pt;" +
            "-fx-font-weight: bold;"
        );
        Label recipientLabel = new Label(peerDisplayText);
        recipientLabel.setStyle(
            "-fx-font-size: 10pt;" +
            "-fx-text-fill: #666666;"
        );
        detailsBox.getChildren().addAll(fileLabel, recipientLabel);
        
        ProgressBar progressBar = UIComponentFactory.createStyledProgressBar();
        progressBar.setProgress(0);
        progressBar.setPrefWidth(200);
        
        Label statusLabel = new Label("Queued");
        statusLabel.setStyle("-fx-font-size: 10pt; -fx-text-fill: #666666;");

        Label percentLabel = new Label("0%");
        percentLabel.setStyle("-fx-font-size: 10pt;");
        percentLabel.setPrefWidth(40);
        
        Button cancelBtn = UIComponentFactory.createSmallButton("✕");
        cancelBtn.setStyle(
            "-fx-background-color: #F44336;" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 10pt;" +
            "-fx-padding: 4px 8px;"
        );
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        transferItem.getChildren().addAll(detailsBox, spacer, progressBar, statusLabel, percentLabel, cancelBtn);
        return new TransferItemView(transferItem, progressBar, statusLabel, percentLabel);
    }

    public void handleTransferEvent(TransferEvent event) {
        if (event == null || transferListView == null) {
            return;
        }

        Runnable update = () -> {
            TransferItemView transferItemView = transferItems.get(event.getTransferId());

            if (transferItemView == null) {
                transferItemView = createTransferItem(event.getFileName(), "From: " + event.getPeerLabel());
                transferItems.put(event.getTransferId(), transferItemView);
                transferListView.getItems().add(0, transferItemView.row());
            }

            double progress = event.getProgressFraction();
            String percentText = Math.round(progress * 100) + "%";

            switch (event.getType()) {
                case STARTED -> {
                    transferItemView.progressBar().setProgress(0.0);
                    transferItemView.statusLabel().setText("Receiving...");
                    transferItemView.percentLabel().setText("0%");
                }
                case PROGRESS -> {
                    transferItemView.progressBar().setProgress(progress);
                    transferItemView.statusLabel().setText("Receiving...");
                    transferItemView.percentLabel().setText(percentText);
                }
                case COMPLETED -> {
                    transferItemView.progressBar().setProgress(1.0);
                    transferItemView.statusLabel().setText("Completed");
                    transferItemView.percentLabel().setText("100%");
                }
                case FAILED -> {
                    transferItemView.progressBar().setProgress(0.0);
                    transferItemView.statusLabel().setText("Failed");
                    transferItemView.percentLabel().setText("0%");
                }
            }
        };

        if (Platform.isFxApplicationThread()) {
            update.run();
        } else {
            Platform.runLater(update);
        }
    }

    private record TransferItemView(HBox row, ProgressBar progressBar, Label statusLabel, Label percentLabel) {
    }
    
    public void openFileChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Share");
        
        Stage stage = new Stage();
        File selectedFile = fileChooser.showOpenDialog(stage);
        
        if (selectedFile != null) {
            this.selectedFile = selectedFile;
        }
    }
    
    public File openFileChooser(Stage owner) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Share");
        return fileChooser.showOpenDialog(owner);
    }
    
    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
    
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
