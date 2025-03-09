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
    private static final String ADMIN_PASSWORD = "1q2w3e";
    private static final Set<Socket> loggedInAdmins = Collections.synchronizedSet(new HashSet<>());
    private static Connection connection;

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

    private static class City {
        private final String name;
        private final double latitude;
        private final double longitude;

        public City(String name, double latitude, double longitude) {
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            City city = (City) o;
            return Double.compare(city.latitude, latitude) == 0 &&
                    Double.compare(city.longitude, longitude) == 0 &&
                    Objects.equals(name, city.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, latitude, longitude);
        }
    }

    private static class Forecast {
        private final String condition;
        private final double temperature;

        public Forecast(String condition, double temperature) {
            this.condition = condition;
            this.temperature = temperature;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Forecast forecast = (Forecast) o;
            return Double.compare(forecast.temperature, temperature) == 0 &&
                    Objects.equals(condition, forecast.condition);
        }

        @Override
        public int hashCode() {
            return Objects.hash(condition, temperature);
        }
    }



    private static class ClientHandler extends Thread {
        private final Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                String request;
                while ((request = in.readLine()) != null) {
                    if (request.startsWith("admin_login")) {
                        String[] parts = request.split(" ", 2);
                        String password = parts[1];
                        if (ADMIN_PASSWORD.equals(password)) {
                            loggedInAdmins.add(socket);
                            out.println("success");
                        } else {
                            out.println("failure");
                        }
                    } else if (request.startsWith("add_forecast")) {
                        if (!loggedInAdmins.contains(socket)) {
                            out.println("Access denied. Admin login required.");
                            continue;
                        }

                        String[] parts = request.split(" ", 4);
                        String cityName = parts[1];
                        String condition = parts[2];
                        double temperature = Double.parseDouble(parts[3]);

                        boolean success = addForecastToCityDB(cityName, condition, temperature);
                        if (success) {
                            out.println("Forecast added successfully to city " + cityName + ".");
                        } else {
                            out.println("Failed to add forecast. City " + cityName + " not found.");
                        }
                    } else if (request.startsWith("add_city")) {
                        if (!loggedInAdmins.contains(socket)) {
                            out.println("Access denied. Admin login required.");
                            continue;
                        }

                        String[] parts = request.split(" ");
                        String cityName = parts[1];
                        double lat = Double.parseDouble(parts[2]);
                        double lon = Double.parseDouble(parts[3]);
                        String condition = parts[4];
                        double temperature = Double.parseDouble(parts[5]);

                        boolean success = addCityDB(cityName, lat, lon, condition, temperature);
                        if (success) {
                            out.println("City " + cityName + " added successfully.");
                        } else {
                            out.println("Failed to add city.");
                        }
                    } else if (request.startsWith("coords")) {
                        String[] parts = request.split(" ");
                        double lat = Double.parseDouble(parts[1]);
                        double lon = Double.parseDouble(parts[2]);
                        String response = getWeatherForClosestLocation(lat, lon);
                        for (String line : response.split("\\n")) {
                            out.println(line);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                loggedInAdmins.remove(socket);
            }
        }
    }

    private static void addForecastToCity(String cityName, String condition, double temperature) {
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
            cityName = cityName.toLowerCase();

            if (!jsonObject.has(cityName)) {
                return; // City not found
            }

            JSONObject cityData = jsonObject.getJSONObject(cityName);
            JSONArray forecasts = cityData.getJSONArray("forecasts");

            JSONObject newForecast = new JSONObject();
            newForecast.put("condition", condition);
            newForecast.put("temperature", temperature);
            forecasts.put(newForecast);

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(jsonObject.toString(4));
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean addForecastToCityDB(String cityName, String condition, double temperature) {
        try {
            Optional<Integer> cityIdOpt = getCityIdOptional(cityName);
            if (cityIdOpt.isEmpty()) {
                return false;
            }
            insertForecast(cityIdOpt.get(), condition, temperature);
            addForecastToCity(cityName,condition,temperature);
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }



    private static void addCity(String cityName, double lat, double lon, String condition, double temperature) {
        try {
            File file = new File(FILE_PATH);
            JSONObject jsonObject;

            if (file.exists()) {
                FileReader reader = new FileReader(file);
                StringBuilder jsonContent = new StringBuilder();
                BufferedReader br = new BufferedReader(reader);
                String line;
                while ((line = br.readLine()) != null) {
                    jsonContent.append(line);
                }
                jsonObject = new JSONObject(jsonContent.toString());
            } else {
                jsonObject = new JSONObject();
            }

            cityName=cityName.toLowerCase();

            JSONObject cityData = jsonObject.optJSONObject(cityName);
            if (cityData == null) {
                cityData = new JSONObject();
                JSONArray coords = new JSONArray();
                coords.put(lat);
                coords.put(lon);
                cityData.put("coords", coords);
                cityData.put("forecasts", new JSONArray());
            }

            JSONObject forecast = new JSONObject();
            forecast.put("condition", condition);
            forecast.put("temperature", temperature);

            cityData.getJSONArray("forecasts").put(forecast);
            jsonObject.put(cityName, cityData);

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(jsonObject.toString(4));
            }


        } catch (Exception e) {
            e.printStackTrace();

        }
    }

    private static boolean addCityDB(String cityName, double lat, double lon, String condition, double temperature) {
        try {
            int cityId = insertCity(cityName, lat, lon);
            insertForecast(cityId, condition, temperature);
            addCity(cityName,lat,lon,condition,temperature);
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static String getWeatherForClosestLocation(double lat, double lon) {
        System.out.println("Received coordinates: Latitude = " + lat + ", Longitude = " + lon);
        try {
            String query = """
            SELECT city_name, condition, temperature, 
                   ( (lat - ?) * (lat - ?) + (lon - ?) * (lon - ?) ) AS distance 
            FROM cities 
            JOIN forecasts ON cities.id = forecasts.city_id 
            WHERE ( (lat - ?) * (lat - ?) + (lon - ?) * (lon - ?) ) <= 0.04 
            ORDER BY distance ASC;
        """;

            StringBuilder result = new StringBuilder();
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setDouble(1, lat);
                pstmt.setDouble(2, lat);
                pstmt.setDouble(3, lon);
                pstmt.setDouble(4, lon);
                pstmt.setDouble(5, lat);
                pstmt.setDouble(6, lat);
                pstmt.setDouble(7, lon);
                pstmt.setDouble(8, lon);
                ResultSet rs = pstmt.executeQuery();

                boolean found = false;
                String currentCity = "";
                while (rs.next()) {
                    String cityName = rs.getString("city_name");
                    String condition = rs.getString("condition");
                    double temperature = rs.getDouble("temperature");

                    if (!cityName.equals(currentCity)) {
                        if (!currentCity.isEmpty()) {
                            result.append("\n");
                        }
                        result.append(cityName).append(" forecasts:\n");
                        currentCity = cityName;
                    }

                    result.append(" - ").append(condition)
                            .append(", ").append(temperature).append("°C\n");
                    found = true;
                }

                if (found) {
                    return result.toString().trim();
                } else {
                    return "No nearby location found within 20 km.";
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "Error retrieving weather data.";
        }
    }

}
