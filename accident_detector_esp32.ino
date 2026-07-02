
#include <Wire.h>
#include "MPU6050_light.h"
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>

const char* WIFI_SSID     = "YourHotspotName";      
const char* WIFI_PASSWORD = "123456789"; 
const char* SERVER_IP     = "192.168.43.1";          
const int   SERVER_PORT   = 8080;
const int   SEND_INTERVAL = 100;                     

String SERVER_URL;

MPU6050 mpu(Wire);

bool     wifiConnected   = false;
uint32_t lastSendTime    = 0;
uint32_t lastWifiCheck   = 0;
uint32_t totalReadings   = 0;
uint32_t failedRequests  = 0;

const int LED_PIN = 2;
void setup() {
  Serial.begin(115200);
  delay(1000);

  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  printBanner();

  initMPU();

  SERVER_URL = "http://" + String(SERVER_IP) + ":" +
               String(SERVER_PORT) + "/data";

  connectWiFi();
}

void loop() {
  uint32_t now = millis();

  if (now - lastWifiCheck >= 5000) {
    lastWifiCheck = now;
    if (WiFi.status() != WL_CONNECTED) {
      Serial.println("[WiFi] Connection lost — reconnecting...");
      wifiConnected = false;
      digitalWrite(LED_PIN, LOW);
      connectWiFi();
    }
  }

  if (now - lastSendTime >= SEND_INTERVAL) {
    lastSendTime = now;
    mpu.update();
    readAndSend();
  }
}


void initMPU() {
  Serial.println("[MPU] Initializing MPU6050...");
  Wire.begin(21, 22); // SDA=GPIO21, SCL=GPIO22
  delay(100);

  byte status = mpu.begin();

  if (status != 0) {
    Serial.print("[MPU] ERROR! Code: ");
    Serial.println(status);
    Serial.println("[MPU] Check wiring:");
    Serial.println("       VCC → 3.3V");
    Serial.println("       GND → GND");
    Serial.println("       SDA → GPIO21");
    Serial.println("       SCL → GPIO22");
    blinkError(); 
    while (true) delay(1000);
  }

  Serial.println("[MPU] MPU6050 connected!");
  Serial.println("[MPU] Calibrating — keep sensor STILL for 3 seconds...");

  for (int i = 3; i > 0; i--) {
    Serial.printf("[MPU] Calibrating... %d\n", i);
    digitalWrite(LED_PIN, HIGH);
    delay(500);
    digitalWrite(LED_PIN, LOW);
    delay(500);
  }

  mpu.calcOffsets(); // auto calibrate offsets
  Serial.println("[MPU] Calibration done!\n");
}


void connectWiFi() {
  Serial.printf("[WiFi] Connecting to: %s\n", WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 20) {
    delay(500);
    Serial.print(".");
    attempts++;
    digitalWrite(LED_PIN, !digitalRead(LED_PIN)); 
  }

  if (WiFi.status() == WL_CONNECTED) {
    wifiConnected = true;
    digitalWrite(LED_PIN, HIGH); 
    Serial.println("\n[WiFi] Connected!");
    Serial.printf("[WiFi] ESP32 IP  : %s\n", WiFi.localIP().toString().c_str());
    Serial.printf("[WiFi] Server URL: %s\n", SERVER_URL.c_str());
    Serial.printf("[WiFi] Signal    : %d dBm\n\n", WiFi.RSSI());
  } else {
    wifiConnected = false;
    digitalWrite(LED_PIN, LOW);
    Serial.println("\n[WiFi] FAILED to connect!");
    Serial.println("[WiFi] Check hotspot name and password in config.");
    Serial.println("[WiFi] Will retry in 5 seconds...\n");
  }
}

void readAndSend() {
  float ax = mpu.getAccX();
  float ay = mpu.getAccY();
  float az = mpu.getAccZ();
  float gx = mpu.getGyroX();
  float gy = mpu.getGyroY();
  float gz = mpu.getGyroZ();

  float magnitude = sqrt(ax*ax + ay*ay + az*az);

  totalReadings++;

  printSerial(ax, ay, az, gx, gy, gz, magnitude);

  if (wifiConnected && WiFi.status() == WL_CONNECTED) {
    sendToApp(ax, ay, az, gx, gy, gz, magnitude);
  }
}

void sendToApp(float ax, float ay, float az,
               float gx, float gy, float gz,
               float magnitude) {

  HTTPClient http;
  http.begin(SERVER_URL);
  http.addHeader("Content-Type", "application/json");
  http.setTimeout(500);

  StaticJsonDocument<256> doc;
  doc["ax"]        = roundTo(ax, 4);
  doc["ay"]        = roundTo(ay, 4);
  doc["az"]        = roundTo(az, 4);
  doc["gx"]        = roundTo(gx, 3);
  doc["gy"]        = roundTo(gy, 3);
  doc["gz"]        = roundTo(gz, 3);
  doc["magnitude"] = roundTo(magnitude, 4);
  doc["uptime"]    = millis() / 1000; 

  String jsonBody;
  serializeJson(doc, jsonBody);

  int responseCode = http.POST(jsonBody);

  if (responseCode == 200) {
   
  } else if (responseCode > 0) {
    Serial.printf("[HTTP] Unexpected response: %d\n", responseCode);
  } else {
    failedRequests++;
    if (failedRequests % 10 == 0) {
      Serial.printf("[HTTP] %lu failed requests — is app running?\n",
                    failedRequests);
    }
  }

  http.end();
}

void printSerial(float ax, float ay, float az,
                 float gx, float gy, float gz,
                 float magnitude) {

  if (totalReadings % 50 == 1) {
    Serial.println("ax\t\tay\t\taz\t\tgx\t\tgy\t\tgz\t\tmagnitude");
    Serial.println("─────────────────────────────────────────────────────────────");
  }

  Serial.printf("%.4f\t\t%.4f\t\t%.4f\t\t%.3f\t\t%.3f\t\t%.3f\t\t%.4f\n",
                ax, ay, az, gx, gy, gz, magnitude);
}

float roundTo(float value, int decimals) {
  float factor = pow(10, decimals);
  return round(value * factor) / factor;
}

void blinkError() {
  for (int i = 0; i < 10; i++) {
    digitalWrite(LED_PIN, HIGH);
    delay(100);
    digitalWrite(LED_PIN, LOW);
    delay(100);
  }
}

void printBanner() {
  Serial.println("============================================");
  Serial.println("  Accident Detection & Rescue System");
  Serial.println("  ESP32 Firmware v1.0.0");
  Serial.println("  Author: Dhiraj Tribhuvan");
  Serial.println("  github.com/Dhiraj316");
  Serial.println("============================================\n");
}
