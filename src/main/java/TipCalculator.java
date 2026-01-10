import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Scanner;

public class TipCalculator {

    // constants for wages
    private static final double SERVER_WAGE = 3.00;
    private static final double HOST_WAGE   = 11.50;
    private static final double TA_WAGE     = 12.00;

    // SQLite database file
    private static final String DB_URL = "jdbc:sqlite:tip_calculator.db";

    public static void main(String[] args) {
        initDatabase();

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                printMenu();

                System.out.print("Choose an option (1-5): ");
                String input = scanner.nextLine().trim().toLowerCase();

                switch (input) {
                    case "1", "log" -> logShift(scanner);
                    case "2", "summary" -> monthlySummary(scanner);
                    case "3", "list" -> listShifts(scanner);
                    case "4", "delete", "del" -> deleteShiftByDateFlow(scanner);
                    case "5", "exit", "quit", "q" -> {
                        System.out.println("Goodbye!");
                        return;
                    }
                    case "help", "h", "?" -> printHelp();
                    default -> System.out.println(
                        "Invalid option. Please enter 1-5 or type: log, summary, list, delete, help, exit."
                    );
                }
            }
        }
    }

    // ================= MENU =================

    private static void printMenu() {
        System.out.println("\n=================== Tip Calculator ===================");
        System.out.println("1. Log a shift (save to database)");
        System.out.println("2. Monthly summary (avg $/hr)");
        System.out.println("3. List shifts for a month");
        System.out.println("4. Delete a shift (by date)");
        System.out.println("5. Exit");
        System.out.println("Commands: log, summary, list, delete, help, exit");
    }

    private static void printHelp() {
        System.out.println("\n=================== Help ===================");
        System.out.println("Roles:");
        System.out.println("  Server -> $3/hr + tips");
        System.out.println("  Host   -> $11.50/hr + tips");
        System.out.println("  TA     -> $12/hr (no tips)");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  log       -> Log a shift");
        System.out.println("  summary   -> Monthly summary");
        System.out.println("  list      -> List shifts");
        System.out.println("  delete    -> Delete shifts by date");
        System.out.println("  exit      -> Quit");
    }

    // ================= OPTION 1: LOG SHIFT =================

    private static void logShift(Scanner scanner) {
        int roleChoice = readIntInRange(scanner,
            "\nChoose your role:\n1. Server\n2. Host\n3. TA\nChoose (1-3): ",
            1, 3
        );

        String role;
        double wageRate;

        switch (roleChoice) {
            case 1 -> {
                role = "SERVER";
                wageRate = SERVER_WAGE;
            }
            case 2 -> {
                role = "HOST";
                wageRate = HOST_WAGE;
            }
            case 3 -> {
                role = "TA";
                wageRate = TA_WAGE;
            }
            default -> throw new IllegalStateException("Unexpected role");
        }

        LocalDate date = readDate(scanner, "Enter date (YYYY-MM-DD) or press Enter for today: ");

        double tips;
        if (role.equals("TA")) {
            tips = 0.0;
        } else {
            tips = readDoubleMin(scanner, "Tips made tonight ($): ", 0.0);
        }

        double hoursWorked = readDoubleMin(scanner, "Hours worked tonight: ", 0.01);

        double wageEarnings = wageRate * hoursWorked;
        double totalEarnings = wageEarnings + tips;
        double earningsPerHour = totalEarnings / hoursWorked;

        insertShift(date, role, hoursWorked, tips, wageRate);

        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);
        System.out.println("\n=================== Shift Saved ===================");
        System.out.println("Date: " + date);
        System.out.println("Role: " + role);
        System.out.println("Wage Rate: " + currency.format(wageRate) + "/hour");
        System.out.println("Tips: " + currency.format(tips));
        System.out.println("Hours Worked: " + round2(hoursWorked));
        System.out.println("Total Earnings: " + currency.format(totalEarnings));
        System.out.println("Earnings Per Hour: " + currency.format(earningsPerHour));
    }

    // ================= OPTION 2: MONTHLY SUMMARY =================

    private static void monthlySummary(Scanner scanner) {
        YearMonth ym = readYearMonth(scanner, "Enter month (YYYY-MM): ");
        MonthlySummary ms = getMonthlySummary(ym);

        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);

        System.out.println("\n=================== Monthly Summary ===================");
        System.out.println("Month: " + ym);
        System.out.println("Shifts Logged: " + ms.shiftCount);
        System.out.println("Total Hours: " + round2(ms.totalHours));
        System.out.println("Total Tips: " + currency.format(ms.totalTips));
        System.out.println("Total Earnings: " + currency.format(ms.totalEarnings));

        if (ms.totalHours > 0) {
            System.out.println("Average $/hr: " + currency.format(ms.totalEarnings / ms.totalHours));
        } else {
            System.out.println("Average $/hr: N/A");
        }
    }

    // ================= OPTION 3: LIST SHIFTS =================

    private static void listShifts(Scanner scanner) {
        YearMonth ym = readYearMonth(scanner, "Enter month (YYYY-MM): ");
        listShiftsForMonth(ym);
    }

    // ================= OPTION 4: DELETE =================

    private static void deleteShiftByDateFlow(Scanner scanner) {
        LocalDate date = readDate(scanner, "Enter shift date to delete (YYYY-MM-DD): ");
        int rowsDeleted = deleteShiftsByDate(date);

        if (rowsDeleted > 0) {
            System.out.println("Deleted " + rowsDeleted + " shift(s) on " + date + ".");
        } else {
            System.out.println("No shifts found on " + date + ".");
        }
    }

    // ================= DATABASE =================

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

    // ================= QUERIES =================

    private static MonthlySummary getMonthlySummary(YearMonth ym) {
        String sql = """
            SELECT COUNT(*), SUM(hours_worked), SUM(tips),
                   SUM(tips + hours_worked * wage_rate)
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

    private static void listShiftsForMonth(YearMonth ym) {
        String sql = """
            SELECT shift_date, role, hours_worked, tips, wage_rate
            FROM shifts
            WHERE shift_date BETWEEN ? AND ?
            ORDER BY shift_date, id
        """;

        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, ym.atDay(1).toString());
            ps.setString(2, ym.atEndOfMonth().toString());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println(
                        "[" + rs.getString(1) + "] " + rs.getString(2)
                        + " | Hours: " + round2(rs.getDouble(3))
                        + " | Tips: " + currency.format(rs.getDouble(4))
                        + " | Wage: " + currency.format(rs.getDouble(5))
                    );
                }
            }

        } catch (SQLException e) {
            System.out.println("List failed: " + e.getMessage());
        }
    }

    // ================= HELPERS =================

    private static int readIntInRange(Scanner s, String p, int min, int max) {
        while (true) {
            System.out.print(p);
            try {
                int v = Integer.parseInt(s.nextLine().trim());
                if (v >= min && v <= max) return v;
            } catch (Exception ignored) {}
            System.out.println("Invalid input.");
        }
    }

    private static double readDoubleMin(Scanner s, String p, double min) {
        while (true) {
            System.out.print(p);
            try {
                double v = Double.parseDouble(s.nextLine().trim());
                if (v >= min) return v;
            } catch (Exception ignored) {}
            System.out.println("Invalid input.");
        }
    }

    private static LocalDate readDate(Scanner s, String p) {
        while (true) {
            System.out.print(p);
            String v = s.nextLine().trim();
            if (v.isEmpty()) return LocalDate.now();
            try {
                return LocalDate.parse(v);
            } catch (Exception e) {
                System.out.println("Invalid date.");
            }
        }
    }

    private static YearMonth readYearMonth(Scanner s, String p) {
        while (true) {
            System.out.print(p);
            try {
                return YearMonth.parse(s.nextLine().trim());
            } catch (Exception e) {
                System.out.println("Invalid month.");
            }
        }
    }

    private static String round2(double v) {
        return String.format("%.2f", v);
    }

    private static class MonthlySummary {
        int shiftCount;
        double totalHours;
        double totalTips;
        double totalEarnings;
    }
}
