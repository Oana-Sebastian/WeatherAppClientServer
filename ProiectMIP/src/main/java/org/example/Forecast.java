package org.example;

class Forecast {
    String day;
    String condition;
    double temperature;

    @Override
    public String toString() {
        return day + ": " + condition + " (" + temperature + "°C)";
    }
}