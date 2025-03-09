package org.example;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class WeatherClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Connected to Weather Server.");
            System.out.println(in.readLine());

            while (true) {
                System.out.print("Enter location: ");
                String location = scanner.nextLine();
                out.println(location);

                String response = in.readLine();
                System.out.println(response);

                if ("exit".equalsIgnoreCase(location)) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}