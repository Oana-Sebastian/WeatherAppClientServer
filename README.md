# 🌦️ **Weather Server-Client Application**
A **client-server** application for retrieving weather forecasts based on user-provided coordinates. The server stores weather data in a **database**, while the client requests weather information for the nearest city within a **20 km radius**.

# ⚡ **Features**

✅ **Client-Server Communication**: Clients send requests to the server, which processes them and returns weather data.

✅ **Database-Backed Forecasts**: Cities and forecasts are stored in a relational database.

✅ **Admin Controls**: Admins can add cities and forecasts via authentication.

✅ **Geographical Search**: The server finds the closest city to given coordinates and returns all forecasts for that city.

# 🛠️ **How It Works**

  1. 🔐 **Admin Login** – Secure authentication for modifying the weather database.
  2. 🏙️ **Add City** – Admins can add new cities with latitude, longitude, and initial weather data.
  3. 🌤️ **Add Forecast** – Admins can append multiple forecasts for a city.
  4. 📡 **Get Weather** – Clients send coordinates, and the server finds the nearest city and returns **all available forecasts**.
     
# 🏗️ **Technologies Used**
  * ☕ **Java** (Client & Server)
  * 🗄️ **JDBC & SQL** (Database Management)
  * 🔗 **Sockets** (Networking for communication)
  * 🌀 **Multi-threading** (Handling multiple clients)
# 🚀 **Setup & Usage**
  1. 🖥️ **Run the Server** – Starts listening for client connections.
  2. 📲 **Run the Client** – Sends requests to fetch weather data.
  3. ⚙️ **Admin Commands** – Use special commands to add cities and forecasts.
