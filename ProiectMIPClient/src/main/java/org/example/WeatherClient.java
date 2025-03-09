package org.example;
import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class WeatherClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 18080;
    private static double currentLat = 0;
    private static double currentLon = 0;
    private static boolean isAdmin = false;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner scanner = new Scanner(System.in)) {


            while (true) {
                displayMenu();
                int choice = scanner.nextInt();
                scanner.nextLine();

                switch (choice) {
                    case 1:
                        setLocation(scanner);
                        break;
                    case 2:
                        getWeather();
                        break;
                    case 4:
                        if (isAdmin) {
                            addCity(scanner, out);
                        } else {
                            System.out.println("Access denied. Only admins can add cities.");
                        }
                        break;
                    case 3:
                        adminLogin(scanner, out, in);
                        break;
                    case 5:
                        addForecast(scanner, out);
                        break;
                    case 6:
                        System.out.println("Exiting...");
                        return;
                    default:
                        System.out.println("Invalid choice. Try again.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void displayMenu() {
        System.out.println("--- Weather Client Menu ---");
        System.out.println("1. Set current location");
        System.out.println("2. Get weather for current location");
        System.out.println("3. Admin Login");
        System.out.println("4. Add new city with weather data (Admin Only)");
        System.out.println("5. Add forecast to city (Admin Only)");
        System.out.println("6. Exit");
        System.out.print("Select an option: ");
    }

    private static void addForecast(Scanner scanner, PrintWriter out) {
        if (!isAdmin) {
            System.out.println("Access denied. Only admins can add forecasts.");
            return;
        }

        System.out.print("Enter city name: ");
        String cityName = scanner.nextLine().toLowerCase();
        System.out.print("Enter weather condition: ");
        String condition = scanner.nextLine();
        System.out.print("Enter temperature: ");
        double temperature = scanner.nextDouble();
        scanner.nextLine();

        out.println("add_forecast " + cityName + " " + condition + " " + temperature);
        System.out.println("Forecast data sent to server.");
    }

    private static void adminLogin(Scanner scanner, PrintWriter out, BufferedReader in) throws IOException {
        System.out.print("Enter admin password: ");
        String password = scanner.nextLine();
        out.println("admin_login " + password);

        String response = in.readLine();
        if ("success".equalsIgnoreCase(response)) {
            System.out.println("Admin login successful!");
            isAdmin = true;
        } else {
            System.out.println("Admin login failed.");
        }
    }

    private static void setLocation(Scanner scanner) {
        System.out.print("Enter latitude: ");
        currentLat = scanner.nextDouble();
        System.out.print("Enter longitude: ");
        currentLon = scanner.nextDouble();
        scanner.nextLine();
        System.out.println("Location set to: " + currentLat + ", " + currentLon);
    }

    private static void getWeather() {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            if (currentLat == 0 && currentLon == 0) {
                System.out.println("Location not set.");
                return;
            }

            out.println("coords " + currentLat + " " + currentLon);
            socket.shutdownOutput();

            String response;
            boolean found = false;
            while ((response = in.readLine()) != null) {
                found = true;
                System.out.println(response);
            }
            if (!found) {
                System.out.println("No response from server.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void addCity(Scanner scanner, PrintWriter out) {
        System.out.print("Enter city name: ");
        String cityName = scanner.nextLine().toLowerCase();
        System.out.print("Enter latitude: ");
        double lat = scanner.nextDouble();
        System.out.print("Enter longitude: ");
        double lon = scanner.nextDouble();
        scanner.nextLine();
        System.out.print("Enter weather condition: ");
        String condition = scanner.nextLine();
        System.out.print("Enter temperature: ");
        double temperature = scanner.nextDouble();
        scanner.nextLine();

        out.println("add_city " + cityName + " " + lat + " " + lon + " " + condition + " " + temperature);
        System.out.println("City data sent to server.");
    }
}