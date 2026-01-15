import javafx.application.Application;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.sql.*;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;

public class TipCalculatorFx extends Application {

    // ===== wages =====
    private static final double SERVER_WAGE = 3.00;
    private static final double HOST_WAGE   = 11.50;
    private static final double TA_WAGE     = 12.00;

    // ===== DB =====
    private static final String DB_URL = "jdbc:sqlite:tip_calculator.db";

    // ===== UI window drag =====
    private double dragOffsetX;
    private double dragOffsetY;

    // ===== UI state =====
    private final Label statusLabel = new Label("");
    private final ObservableList<ShiftRow> shiftRows = FXCollections.observableArrayList();
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);

    // content area that changes
    private final StackPane content = new StackPane();

    @Override
    public void start(Stage stage) {
        initDatabase();

        // ===== Header =====
        Label title = new Label("Income Tracker");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");

        Button closeBtn = new Button("✕");
        closeBtn.setFocusTraversable(false);
        closeBtn.setStyle("""
            -fx-background-color: transparent;
            -fx-font-size: 14px;
            -fx-padding: 2 8 2 8;
        """);
        closeBtn.setOnAction(e -> stage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(8, title, spacer, closeBtn);
        header.setAlignment(Pos.CENTER_LEFT);

        // ===== Quick action buttons =====
        Button logBtn = new Button("Log Shift");
        Button summaryBtn = new Button("Monthly Summary");
        Button listBtn = new Button("List Shifts");
        Button deleteBtn = new Button("Delete by Date");
        Button helpBtn = new Button("Help");

        for (Button b : new Button[]{logBtn, summaryBtn, listBtn, deleteBtn, helpBtn}) {
            b.setMaxWidth(Double.MAX_VALUE);
            b.setFocusTraversable(false);
            b.setStyle("""
                -fx-background-radius: 10;
                -fx-padding: 8 10 8 10;
            """);
        }

        VBox menu = new VBox(8, logBtn, summaryBtn, listBtn, deleteBtn, helpBtn);
        menu.setPrefWidth(140);

        // ===== Status bar =====
        statusLabel.setStyle("-fx-opacity: 0.85; -fx-font-size: 11px;");

        // ===== Content area (starts on Log Shift) =====
        content.setPadding(new Insets(6));
        showLogShiftView();

        // ===== Layout root =====
        HBox body = new HBox(12, menu, content);
        body.setAlignment(Pos.TOP_LEFT);

        VBox root = new VBox(12, header, body, statusLabel);
        root.setPadding(new Insets(14));
        root.setStyle("""
            -fx-background-color: #FFF6B3;
            -fx-background-radius: 16;
            -fx-border-radius: 16;
            -fx-border-color: rgba(0,0,0,0.12);
            -fx-border-width: 1;
        """);

        Scene scene = new Scene(root, 720, 420);

        // ===== Widget behavior =====
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setAlwaysOnTop(true);
        stage.setScene(scene);

        // Drag window by grabbing anywhere on root
        root.setOnMousePressed(e -> {
            dragOffsetX = e.getSceneX();
            dragOffsetY = e.getSceneY();
        });
        root.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - dragOffsetX);
            stage.setY(e.getScreenY() - dragOffsetY);
        });

        // ===== Wire buttons =====
        logBtn.setOnAction(e -> showLogShiftView());
        summaryBtn.setOnAction(e -> showMonthlySummaryView());
        listBtn.setOnAction(e -> showListShiftsView());
        deleteBtn.setOnAction(e -> showDeleteView());
        helpBtn.setOnAction(e -> showHelpView());

        stage.show();
        setStatus("Ready.");
    }

    // =========================================================
    // VIEWS
    // =========================================================

    private void showLogShiftView() {
        Label h = new Label("Log Shift");
        h.setStyle("-fx-font-size: 16px; -fx-font-weight: 700;");

        DatePicker datePicker = new DatePicker(LocalDate.now());

        ComboBox<String> roleBox = new ComboBox<>();
        roleBox.getItems().addAll("SERVER", "HOST", "TA");
        roleBox.setValue("SERVER");

        TextField hoursField = new TextField();
        hoursField.setPromptText("Hours (e.g. 5.5)");

        TextField tipsField = new TextField();
        tipsField.setPromptText("Tips (e.g. 120)");
        tipsField.setDisable(false);

        Label wageLabel = new Label("Wage: " + currency.format(SERVER_WAGE) + "/hr");
        wageLabel.setStyle("-fx-opacity: 0.9;");

        // tips disabled for TA
        roleBox.valueProperty().addListener((obs, oldV, newV) -> {
            double wage = wageForRole(newV);
            wageLabel.setText("Wage: " + currency.format(wage) + "/hr");

            boolean isTa = "TA".equalsIgnoreCase(newV);
            tipsField.setDisable(isTa);
            if (isTa) tipsField.setText("0");
        });

        Button save = new Button("Save Shift");
        save.setStyle("""
            -fx-background-radius: 10;
            -fx-padding: 8 12 8 12;
            -fx-font-weight: 700;
        """);

        Label result = new Label("");
        result.setStyle("-fx-opacity: 0.85;");

        save.setOnAction(e -> {
            String role = roleBox.getValue();
            LocalDate date = datePicker.getValue();
            if (date == null) {
                setStatus("Pick a date.");
                return;
            }

            Double hours = parseDouble(hoursField.getText());
            if (hours == null || hours < 0.01) {
                setStatus("Hours must be > 0.");
                return;
            }

            double tips;
            if ("TA".equalsIgnoreCase(role)) {
                tips = 0.0;
            } else {
                Double t = parseDouble(tipsField.getText());
                if (t == null || t < 0.0) {
                    setStatus("Tips must be >= 0.");
                    return;
                }
                tips = t;
            }

            double wageRate = wageForRole(role);

            insertShift(date, role, hours, tips, wageRate);

            double total = tips + (hours * wageRate);
            double eph = total / hours;

            result.setText(
                "Saved: " + date + " • " + role +
                " • Total: " + currency.format(total) +
                " • $/hr: " + currency.format(eph)
            );
            setStatus("Shift saved.");

            // clear fields but keep role + date
            hoursField.clear();
            if (!"TA".equalsIgnoreCase(role)) tipsField.clear();
        });

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(new Label("Date:"), 0, 0);
        form.add(datePicker, 1, 0);
        form.add(new Label("Role:"), 0, 1);
        form.add(roleBox, 1, 1);
        form.add(wageLabel, 2, 1);
        form.add(new Label("Hours:"), 0, 2);
        form.add(hoursField, 1, 2);
        form.add(new Label("Tips:"), 0, 3);
        form.add(tipsField, 1, 3);
        form.add(save, 1, 4);

        VBox box = new VBox(10, h, form, result);
        box.setPadding(new Insets(10));
        setContent(box);
    }

    private void showMonthlySummaryView() {
        Label h = new Label("Monthly Summary");
        h.setStyle("-fx-font-size: 16px; -fx-font-weight: 700;");

        DatePicker anyDayInMonth = new DatePicker(LocalDate.now());

        Button load = new Button("Load Summary");
        load.setStyle("-fx-background-radius: 10; -fx-padding: 8 12 8 12; -fx-font-weight: 700;");

        Label out = new Label("");
        out.setStyle("-fx-opacity: 0.9; -fx-font-size: 12px;");

        load.setOnAction(e -> {
            LocalDate d = anyDayInMonth.getValue();
            if (d == null) {
                setStatus("Pick a date in the month you want.");
                return;
            }
            YearMonth ym = YearMonth.from(d);
            MonthlySummary ms = getMonthlySummary(ym);

            String avg = (ms.totalHours > 0)
                ? currency.format(ms.totalEarnings / ms.totalHours)
                : "N/A";

            out.setText(
                "Month: " + ym + "\n" +
                "Shifts: " + ms.shiftCount + "\n" +
                "Hours: " + round2(ms.totalHours) + "\n" +
                "Tips: " + currency.format(ms.totalTips) + "\n" +
                "Earnings: " + currency.format(ms.totalEarnings) + "\n" +
                "Avg $/hr: " + avg
            );
            setStatus("Summary loaded.");
        });

        HBox top = new HBox(10, new Label("Pick any day:"), anyDayInMonth, load);
        top.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(10, h, top, out);
        box.setPadding(new Insets(10));
        setContent(box);
    }

    private void showListShiftsView() {
        Label h = new Label("List Shifts");
        h.setStyle("-fx-font-size: 16px; -fx-font-weight: 700;");

        DatePicker anyDayInMonth = new DatePicker(LocalDate.now());

        Button load = new Button("Load Shifts");
        load.setStyle("-fx-background-radius: 10; -fx-padding: 8 12 8 12; -fx-font-weight: 700;");

        TableView<ShiftRow> table = buildShiftTable();
        table.setItems(shiftRows);

        load.setOnAction(e -> {
            LocalDate d = anyDayInMonth.getValue();
            if (d == null) {
                setStatus("Pick a date in the month you want.");
                return;
            }
            YearMonth ym = YearMonth.from(d);
            shiftRows.setAll(fetchShiftsForMonth(ym));
            setStatus("Loaded " + shiftRows.size() + " shift(s) for " + ym + ".");
        });

        HBox top = new HBox(10, new Label("Pick any day:"), anyDayInMonth, load);
        top.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(10, h, top, table);
        box.setPadding(new Insets(10));
        setContent(box);
    }

    private void showDeleteView() {
        Label h = new Label("Delete Shifts by Date");
        h.setStyle("-fx-font-size: 16px; -fx-font-weight: 700;");

        DatePicker datePicker = new DatePicker(LocalDate.now());

        Button del = new Button("Delete ALL shifts on this date");
        del.setStyle("""
            -fx-background-radius: 10;
            -fx-padding: 8 12 8 12;
            -fx-font-weight: 700;
        """);

        Label out = new Label("");
        out.setStyle("-fx-opacity: 0.9;");

        del.setOnAction(e -> {
            LocalDate d = datePicker.getValue();
            if (d == null) {
                setStatus("Pick a date.");
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm delete");
            confirm.setHeaderText("Delete all shifts on " + d + "?");
            confirm.setContentText("This cannot be undone.");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                setStatus("Delete cancelled.");
                return;
            }

            int rows = deleteShiftsByDate(d);
            out.setText("Deleted " + rows + " shift(s) on " + d + ".");
            setStatus("Delete finished.");
        });

        VBox box = new VBox(10, h, new Label("Date:"), datePicker, del, out);
        box.setPadding(new Insets(10));
        setContent(box);
    }

    private void showHelpView() {
        Label h = new Label("Help");
        h.setStyle("-fx-font-size: 16px; -fx-font-weight: 700;");

        Label text = new Label(
            "Roles:\n" +
            "  SERVER -> $3/hr + tips\n" +
            "  HOST   -> $11.50/hr + tips\n" +
            "  TA     -> $12/hr (no tips)\n\n" +
            "UI:\n" +
            "  Log Shift: saves to SQLite\n" +
            "  Monthly Summary: totals + avg $/hr\n" +
            "  List Shifts: table view\n" +
            "  Delete by Date: deletes ALL shifts on the selected date\n"
        );
        text.setStyle("-fx-opacity: 0.9;");

        VBox box = new VBox(10, h, text);
        box.setPadding(new Insets(10));
        setContent(box);
    }

    private void setContent(Region node) {
        content.getChildren().setAll(node);
        StackPane.setMargin(node, new Insets(0));
    }

    // =========================================================
    // DB + Queries
    // =========================================================

    private static void initDatabase() {
        String sql = """
            CREATE TABLE IF NOT EXISTS shifts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                shift_date TEXT NOT NULL,
                role TEXT NOT NULL,
                hours_worked REAL NOT NULL,
                tips REAL NOT NULL,
                wage_rate REAL NOT NULL
            );
        """;

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.out.println("Failed to init DB: " + e.getMessage());
        }
    }

    private static void insertShift(LocalDate date, String role, double hours, double tips, double wage) {
        String sql = "INSERT INTO shifts VALUES (NULL, ?, ?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, date.toString());
            ps.setString(2, role);
            ps.setDouble(3, hours);
            ps.setDouble(4, tips);
            ps.setDouble(5, wage);
            ps.executeUpdate();

        } catch (SQLException e) {
            System.out.println("Insert failed: " + e.getMessage());
        }
    }

    private static int deleteShiftsByDate(LocalDate date) {
        String sql = "DELETE FROM shifts WHERE shift_date = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, date.toString());
            return ps.executeUpdate();

        } catch (SQLException e) {
            System.out.println("Delete failed: " + e.getMessage());
            return 0;
        }
    }

    private static MonthlySummary getMonthlySummary(YearMonth ym) {
        String sql = """
            SELECT
                COALESCE(COUNT(*), 0),
                COALESCE(SUM(hours_worked), 0),
                COALESCE(SUM(tips), 0),
                COALESCE(SUM(tips + hours_worked * wage_rate), 0)
            FROM shifts
            WHERE shift_date BETWEEN ? AND ?
        """;

        MonthlySummary ms = new MonthlySummary();

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, ym.atDay(1).toString());
            ps.setString(2, ym.atEndOfMonth().toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ms.shiftCount = rs.getInt(1);
                    ms.totalHours = rs.getDouble(2);
                    ms.totalTips = rs.getDouble(3);
                    ms.totalEarnings = rs.getDouble(4);
                }
            }

        } catch (SQLException e) {
            System.out.println("Summary failed: " + e.getMessage());
        }

        return ms;
    }

    private static ObservableList<ShiftRow> fetchShiftsForMonth(YearMonth ym) {
        ObservableList<ShiftRow> rows = FXCollections.observableArrayList();

        String sql = """
            SELECT id, shift_date, role, hours_worked, tips, wage_rate,
                   (tips + hours_worked * wage_rate) AS total_earnings
            FROM shifts
            WHERE shift_date BETWEEN ? AND ?
            ORDER BY shift_date, id
        """;

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, ym.atDay(1).toString());
            ps.setString(2, ym.atEndOfMonth().toString());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String date = rs.getString("shift_date");
                    String role = rs.getString("role");
                    double hours = rs.getDouble("hours_worked");
                    double tips = rs.getDouble("tips");
                    double wage = rs.getDouble("wage_rate");
                    double total = rs.getDouble("total_earnings");

                    rows.add(new ShiftRow(id, date, role, hours, tips, wage, total));
                }
            }

        } catch (SQLException e) {
            System.out.println("List failed: " + e.getMessage());
        }

        return rows;
    }

    // =========================================================
    // Table
    // =========================================================

    private TableView<ShiftRow> buildShiftTable() {
        TableView<ShiftRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<ShiftRow, Integer> cId = new TableColumn<>("ID");
        cId.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<ShiftRow, String> cDate = new TableColumn<>("Date");
        cDate.setCellValueFactory(new PropertyValueFactory<>("date"));

        TableColumn<ShiftRow, String> cRole = new TableColumn<>("Role");
        cRole.setCellValueFactory(new PropertyValueFactory<>("role"));

        TableColumn<ShiftRow, Double> cHours = new TableColumn<>("Hours");
        cHours.setCellValueFactory(new PropertyValueFactory<>("hours"));

        TableColumn<ShiftRow, Double> cTips = new TableColumn<>("Tips");
        cTips.setCellValueFactory(new PropertyValueFactory<>("tips"));

        TableColumn<ShiftRow, Double> cWage = new TableColumn<>("Wage");
        cWage.setCellValueFactory(new PropertyValueFactory<>("wage"));

        TableColumn<ShiftRow, Double> cTotal = new TableColumn<>("Total");
        cTotal.setCellValueFactory(new PropertyValueFactory<>("total"));

        // Format money columns
        cTips.setCellFactory(col -> moneyCell());
        cWage.setCellFactory(col -> moneyCell());
        cTotal.setCellFactory(col -> moneyCell());

        table.getColumns().addAll(cId, cDate, cRole, cHours, cTips, cWage, cTotal);
        table.setPrefHeight(280);
        return table;
    }

    private TableCell<ShiftRow, Double> moneyCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                } else {
                    setText(currency.format(value));
                }
            }
        };
    }

    // =========================================================
    // Helpers
    // =========================================================

    private void setStatus(String msg) {
        statusLabel.setText(msg == null ? "" : msg);
    }

    private static Double parseDouble(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            return Double.parseDouble(t);
        } catch (Exception e) {
            return null;
        }
    }

    private static double wageForRole(String role) {
        if (role == null) return SERVER_WAGE;
        return switch (role.toUpperCase()) {
            case "SERVER" -> SERVER_WAGE;
            case "HOST" -> HOST_WAGE;
            case "TA" -> TA_WAGE;
            default -> SERVER_WAGE;
        };
    }

    private static String round2(double v) {
        return String.format("%.2f", v);
    }

    // =========================================================
    // Data classes
    // =========================================================

    private static class MonthlySummary {
        int shiftCount;
        double totalHours;
        double totalTips;
        double totalEarnings;
    }

    public static class ShiftRow {
        private final SimpleIntegerProperty id;
        private final SimpleStringProperty date;
        private final SimpleStringProperty role;
        private final SimpleDoubleProperty hours;
        private final SimpleDoubleProperty tips;
        private final SimpleDoubleProperty wage;
        private final SimpleDoubleProperty total;

        public ShiftRow(int id, String date, String role, double hours, double tips, double wage, double total) {
            this.id = new SimpleIntegerProperty(id);
            this.date = new SimpleStringProperty(date);
            this.role = new SimpleStringProperty(role);
            this.hours = new SimpleDoubleProperty(hours);
            this.tips = new SimpleDoubleProperty(tips);
            this.wage = new SimpleDoubleProperty(wage);
            this.total = new SimpleDoubleProperty(total);
        }

        public int getId() { return id.get(); }
        public String getDate() { return date.get(); }
        public String getRole() { return role.get(); }
        public double getHours() { return hours.get(); }
        public double getTips() { return tips.get(); }
        public double getWage() { return wage.get(); }
        public double getTotal() { return total.get(); }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
