import java.text.NumberFormat;
import java.util.Scanner;

public class tipCalculator {
    //constants for wage (server or host)
    private static final double SERVER_WAGE = 3.00;
    private static final double HOST_WAGE = 11.50;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int choice = 0;
        System.out.println("========== Welcome to the Tip Calculator! ==========");
        System.out.println("Are you a server or a host?");

        //gives the user 3 options if they are host / server / exit
        while (choice < 1 || choice > 3){
            System.out.println("1. Server");
            System.out.println("2. Host");
            System.out.println("3. Exit");
            System.out.print("Please enter your choice (1,2, or 3): ");

            //gives the user a error if they enter outside of range
            try{
                choice = Integer.parseInt(scanner.nextLine());
                if (choice < 1 || choice > 3) {
                    System.out.println("Invalid choice. Please enter 1, 2, or 3.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number (1, 2, or 3).");
                continue;
            }
        }

        //switch case for 3 options
        switch(choice){
            case 1:
                System.out.println("you are server tonight! (wage: $3/hour)");
                break;
            case 2:
                System.out.println("you are host tonight! (wage: $11.50/hour)");
                break;
            case 3:
                System.out.println("goodbye!");
                return;
        }
        //get tips and hours worked
        System.out.print("======================================================");
        System.out.print("\nTips made tonight: $");
        double tips = scanner.nextDouble();
        System.out.print("Enter hours worked tonight: ");
        double hoursWorked = scanner.nextDouble();
        scanner.nextLine();

        //determine role and wage 
        String role = (choice == 1) ? "Server" : "Host";
        double wage = (choice == 1) ? SERVER_WAGE : HOST_WAGE;

        //calculate current shift earnings
        double totalWageEarning = wage * hoursWorked;
        double tiotalEarnings = totalWageEarning + tips;
        double earningPerHour = tiotalEarnings / hoursWorked;

        //display results 
        NumberFormat currency = NumberFormat.getCurrencyInstance();
        System.out.println("=================== Earning Summary ====================");
        System.out.println("Role: " + role);
        System.out.println("Wage: $" + String.format("%.2f", wage) + "/hour");
        System.out.println("Tips: " + currency.format(tips));
        System.out.println("Hours Worked: " + hoursWorked);
        System.out.println("Total Wage Earnings: " + currency.format(totalWageEarning));
        System.out.println("Total Earnings: " + currency.format(tiotalEarnings));
        System.out.println("Earnings Per Hour: " + currency.format(earningPerHour));
        scanner.close();
    }
}