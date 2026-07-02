# Accident-Detection-Rescue-AI-System
An IoT-based safety system that uses an ESP32 and MPU6050 sensor to stream real-time motion data to a custom Android app, where an embedded AI model detects accidents and automatically dispatches emergency alerts.
An end-to-end IoT and Machine Learning safety system that detects vehicular accidents in real-time and automatically dispatches SOS alerts. This project bridges hardware sensors, edge AI, and an Android application to provide a rapid emergency response pipeline.

## Project Overview
This system integrates an ESP32 microcontroller paired with an MPU6050 6-axis sensor to continuously monitor vehicular motion. This data is transmitted wirelessly via Wi-Fi to a companion Android application. The app processes the incoming data stream using a custom-trained, on-device machine learning model to accurately identify crash events. Upon detecting an accident, the application instantly triggers an emergency protocol, sending automated SOS alerts and GPS coordinates to predefined contacts to facilitate a rapid rescue.

## System Architecture
1. **Hardware Edge Node (ESP32 + MPU6050):** Samples 6-axis IMU data (accelerometer and gyroscope) at 10Hz and transmits it via Wi-Fi (HTTP POST).
2. **On-Device ML Inference:** A custom-trained Random Forest model (converted to TensorFlow Lite) runs locally on the Android device. It uses a magnitude pre-filter to ignore standard road bumps.
3. **Android Rescue App:** A Kotlin-based application that receives hardware data, runs the ML prediction, and executes automated emergency protocols.

## 🛠️ Tech Stack
* **Hardware:** ESP32 NodeMCU, MPU6050, I2C, Arduino C++
* **Machine Learning:** Python, Scikit-Learn, TensorFlow Lite, Google Colab
* **Android App:** Kotlin, Android Studio, Room Database
* **Communication:** Wi-Fi Hotspot, JSON payload over HTTP

## 🔌 Hardware Wiring

| MPU6050 Pin | ESP32 Pin | Connection Note |
| :--- | :--- | :--- |
| VCC | 3V3 | Power (Do not use 5V) |
| GND | GND | Ground |
| SCL | GPIO22 | I2C Clock |
| SDA | GPIO21 | I2C Data |

##  Repository Structure
* `/Hardware` - ESP32 Arduino code for sensor reading and Wi-Fi transmission.
* `/ML_Pipeline` - Google Colab notebooks for training the Random Forest model and dataset.
* `/Android_App` - Complete Kotlin Android Studio project source code.

## ⚙️ How to Run the Project
1. **Hardware:** Flash the code in `/Hardware` to your ESP32. Update the Wi-Fi credentials in the code to match your phone's mobile hotspot.
2. **Android App:** provided all the necessary code files to run the app manage the files and install the app to android device. 
3. **Connect:** Turn on your phone's mobile hotspot. The ESP32 will connect automatically and begin transmitting data to the app's local NanoHTTPD server.

## 👨‍💻 Author
**Dhiraj Tribhuvan**
