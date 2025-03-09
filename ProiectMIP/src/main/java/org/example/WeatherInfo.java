package org.example;

import java.util.List;

class WeatherInfo {
    String condition;
    double temperature;
    List<Forecast> forecast;

    @Override
    public String toString() {
        return "Condition: " + condition + ", Temp: " + temperature + "°C, Forecast: " + forecast;
    }
}
