package org.example;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;

public class WeatherServer {
    private static final String FILE_PATH = "weather_data.json";
    private static final int PORT = 18080;
    private static Connection connection;

    public static Connection getConnection()
    {
        return connection;
    }

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);
            initializeDatabase();
            loadJsonToDatabase();

            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(socket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void initializeDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/weather","postgres","1q2w3e");
            try (Statement stmt = connection.createStatement()) {
                String createTable ="""
                    CREATE TABLE IF NOT EXISTS cities (
                        id SERIAL PRIMARY KEY,
                        city_name TEXT UNIQUE,
                        lat DOUBLE PRECISION,
                        lon DOUBLE PRECISION
                    );

                    CREATE TABLE IF NOT EXISTS forecasts (
                        id SERIAL PRIMARY KEY,
                        city_id INTEGER REFERENCES cities(id) ON DELETE CASCADE,
                        condition TEXT,
                        temperature DOUBLE PRECISION,
                        UNIQUE(city_id, condition, temperature)
                    );
                """;
                stmt.executeUpdate(createTable);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void loadJsonToDatabase() {
        try {
            File file = new File(FILE_PATH);
            if (!file.exists()) {
                return;
            }

            FileReader reader = new FileReader(file);
            StringBuilder jsonContent = new StringBuilder();
            BufferedReader br = new BufferedReader(reader);
            String line;
            while ((line = br.readLine()) != null) {
                jsonContent.append(line);
            }

            JSONObject jsonObject = new JSONObject(jsonContent.toString());

            for (String city : jsonObject.keySet()) {
                if (cityExists(city)) {
                    continue;
                }

                JSONObject cityData = jsonObject.getJSONObject(city);
                JSONArray coords = cityData.getJSONArray("coords");
                double lat = coords.getDouble(0);
                double lon = coords.getDouble(1);

                int cityId = insertCity(city, lat, lon);
                JSONArray forecasts = cityData.getJSONArray("forecasts");

                for (int i = 0; i < forecasts.length(); i++) {
                    JSONObject forecast = forecasts.getJSONObject(i);
                    insertForecast(cityId, forecast.getString("condition"), forecast.getDouble("temperature"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Optional<Integer> getCityIdOptional(String cityName) {
        String query = "SELECT id FROM cities WHERE city_name = ?;";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, cityName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(rs.getInt("id"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    private static boolean cityExists(String cityName) throws SQLException {
        return getCityIdOptional(cityName).isPresent();
    }

    private static int insertCity(String cityName, double lat, double lon) throws SQLException {
        String query = "INSERT INTO cities (city_name, lat, lon) VALUES (?, ?, ?) ON CONFLICT (city_name) DO NOTHING RETURNING id;";
        try (PreparedStatement pstmt = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, cityName);
            pstmt.setDouble(2, lat);
            pstmt.setDouble(3, lon);
            pstmt.executeUpdate();

            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            } else {
                return getCityIdOptional(cityName).orElse(-1);
            }
        }
    }


    private static void insertForecast(int cityId, String condition, double temperature) throws SQLException {
        String query = "INSERT INTO forecasts (city_id, condition, temperature) VALUES (?, ?, ?) ON CONFLICT DO NOTHING;";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setInt(1, cityId);
            pstmt.setString(2, condition);
            pstmt.setDouble(3, temperature);
            pstmt.executeUpdate();
        }
    }


}
