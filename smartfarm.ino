#include <Wire.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include "DHT.h"
#include "FirebaseESP32.h"
#include "SimpleKalmanFilter.h"
#include <HCSR04.h>

// -----------------------
// CHUỖI HẰNG & PROGMEM
// -----------------------
const char wifiSSID[]        PROGMEM = "--";
const char wifiPassword[]    PROGMEM = "--";
const char firebaseHostStr[] PROGMEM = "--";
const char firebaseAuthStr[] PROGMEM = "--";
const char Web_App_URL[]     PROGMEM = "--";  // Thay YOUR_SCRIPT_ID bằng ID của bạn

// Bộ đệm để copy chuỗi từ PROGMEM
char ssidBuffer[32];
char passwordBuffer[32];
char firebaseHostBuffer[128];
char firebaseAuthBuffer[64];
char webAppBuffer[128];

// -----------------------
// KHỞI TẠO CÁC CẢM BIẾN
// -----------------------

// Cảm biến DHT11
#define DHT11_PIN 26
#define DHTTYPE DHT11
DHT dht(DHT11_PIN, DHTTYPE);

// Cảm biến độ ẩm đất (analog)
#define SOIL_MOISTURE_PIN 39

// Các chân điều khiển relay
#define RELAY1_PIN 25
#define RELAY2_PIN 4
#define RELAY3_PIN 5

// Cảm biến khoảng cách SRF04
#define SRF04_TRIG_PIN 19
#define SRF04_ECHO_PIN 18
HCSR04 ultrasonicSensor(SRF04_TRIG_PIN, SRF04_ECHO_PIN, 0, 400);
SimpleKalmanFilter srf04Filter(2, 2, 0.1);

// Cảm biến gió
#define WIND_SENSOR_PIN 15
#define PI 3.14
volatile int windCountPulse = 0;
uint32_t windMillis = 0, windCalcTime = 0;
float D_Anemometer = 0.205;          // Đường kính 205mm
float C_Anemometer = PI * 0.205;
float windSpeed = 0, windSpeedMax = 0;
volatile bool windTrig = false;

// Cảm biến mưa
#define RAIN_SENSOR_PIN 13
#define RAIN_AMOUNT_PER_PULSE 6     // ml mỗi xung
#define RAIN_FUNNEL_AREA 9498       // mm²
#define RAIN_SAMPLE_TIME 1          // phút
volatile int rainCountPulse = 0;
float onePulseValue = ((1000000.0 / RAIN_FUNNEL_AREA) * RAIN_AMOUNT_PER_PULSE) / 1000.0;
uint32_t rainMillis = 0, rainCalcTime = 0;
float rainValue = 0, rainValueMax = 0;
volatile bool rainTrig = false;

// Các biến cho cảm biến khoảng cách
int srf04Distance = 0, waterLevel = 0;
bool sensorOutOfRange = false, waterWarning = false;
const int installHeight = 100;      // chiều cao lắp đặt sensor (cm)
const int warningThreshold = 20;    // ngưỡng cảnh báo (cm)
int tempInstallHeight = 0, tempWarningThreshold = 0;

// -----------------------
// CẤU HÌNH FIREBASE
// -----------------------
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

// Thời gian cập nhật Firebase (500ms)
unsigned long lastFirebaseUpdate = 0;
const unsigned long firebaseUpdateInterval = 500;  // ms

// Thời gian gửi dữ liệu lên Google Sheet (mỗi 5 phút)
unsigned long lastSheetUpdate = 0;
const unsigned long googleSheetUpdateInterval = 60000;  // 300,000 ms = 5 phút

// -----------------------
// BIẾN TÍNH TRUNG BÌNH (ACCUMULATOR)
// -----------------------
float tempSum = 0, humiSum = 0, soilSum = 0, windSum = 0, rainSum = 0;
unsigned long sampleCount = 0;

// -----------------------
// BIẾN DỮ LIỆU HIỆN TẠI
// -----------------------
float temperature = 0, humidity = 0;
int soilMoisturePercent = 0;

// -----------------------
// NGẮN NGỮ CHO ISR
// -----------------------
void IRAM_ATTR onWindSensorInterrupt() {
  windTrig = true;
}

void IRAM_ATTR onRainSensorInterrupt() {
  rainTrig = true;
}

// -----------------------
// KHỞI TẠO CẢM BIẾN Gió & Mưa
// -----------------------
void initWindSensor() {
  pinMode(WIND_SENSOR_PIN, INPUT_PULLUP);
  attachInterrupt(digitalPinToInterrupt(WIND_SENSOR_PIN), onWindSensorInterrupt, FALLING);
  // Tính chu vi vòng tròn của anemometer
  C_Anemometer = PI * D_Anemometer;
}

void initRainSensor() {
  pinMode(RAIN_SENSOR_PIN, INPUT_PULLUP);
  attachInterrupt(digitalPinToInterrupt(RAIN_SENSOR_PIN), onRainSensorInterrupt, FALLING);
}

// -----------------------
// GỬI DỮ LIỆU LÊN GOOGLE SPREADSHEET (TRUNG BÌNH)
// -----------------------
void sendDataToGoogleSheet(float avgTemp, float avgHumi, float avgSoil, float avgWind, float avgRain) {
  String url = String(webAppBuffer) + "?sts=write";
  url += "&temp=" + String(avgTemp);
  url += "&humi=" + String(avgHumi);
  url += "&soil=" + String(avgSoil);
  url += "&wind=" + String(avgWind);
  url += "&rain=" + String(avgRain);
  
  Serial.println();
  Serial.println("-------------");
  Serial.println("Gửi dữ liệu trung bình lên Google Spreadsheet...");
  Serial.print("URL: ");
  Serial.println(url);
  
  HTTPClient http;
  http.begin(url.c_str());
  http.setFollowRedirects(HTTPC_STRICT_FOLLOW_REDIRECTS);
  
  int httpCode = http.GET();
  Serial.print("HTTP Status Code: ");
  Serial.println(httpCode);
  
  if (httpCode > 0) {
    String payload = http.getString();
    Serial.println("Payload: " + payload);
  }
  
  http.end();
}

// -----------------------
// GỬI DỮ LIỆU MƯA LÊN FIREBASE
// -----------------------
void updateRainOnFirebase(float rValue) {
  Firebase.setFloat(fbdo, "/DuLieuCamBien/Rain", rValue);
}

// -----------------------
// TASK: XỬ LÝ CẢM BIẾN Gió (FreeRTOS Task)
// -----------------------
void TaskWindSensor(void *pvParameters) {
  for (;;) {
    if (windTrig) {
      windTrig = false;
      windCountPulse++;
    }
    
    if (millis() - windMillis > 1000) {
      windCalcTime++;
      windMillis = millis();
    }
    
    if (windCalcTime >= 5) {
      // Tính vận tốc gió: khoảng cách di chuyển của gió trên 5 giây
      windSpeed = (windCountPulse * C_Anemometer) / 5.0;
      windCountPulse = 0;
      windCalcTime = 0;
      
      if (windSpeed > windSpeedMax)
        windSpeedMax = windSpeed;
    }
    
    vTaskDelay(pdMS_TO_TICKS(100));
  }
}

// -----------------------
// TASK: XỬ LÝ CẢM BIẾN Mưa (FreeRTOS Task)
// -----------------------
void TaskRainSensor(void *pvParameters) {
  for (;;) {
    if (rainTrig) {
      // Giảm nhiễu bằng delay ngắn
      vTaskDelay(pdMS_TO_TICKS(150));
      rainTrig = false;
      rainCountPulse++;
    }
    
    if (millis() - rainMillis > 1000) {
      rainCalcTime++;
      rainMillis = millis();
    }
    
    if (rainCalcTime >= RAIN_SAMPLE_TIME * 60) {
      // Tính giá trị mưa theo đơn vị ml/giờ
      rainValue = (rainCountPulse * onePulseValue) * (3600.0 / (RAIN_SAMPLE_TIME * 60));
      rainCountPulse = 0;
      rainCalcTime = 0;
      
      if (rainValue > rainValueMax)
        rainValueMax = rainValue;
      
      updateRainOnFirebase(rainValue);
    }
    
    vTaskDelay(pdMS_TO_TICKS(100));
  }
}

// -----------------------
// TASK: XỬ LÝ CẢM BIẾN SRF04 (Khoảng cách & Mức nước)
// -----------------------
void TaskSRF04Sensor(void *pvParameters) {
  for (;;) {
    int d = ultrasonicSensor.getMedianFilterDistance();
    d = srf04Filter.updateEstimate(d);
    if (d > 0 && d < 400) {
      sensorOutOfRange = false;
      srf04Distance = d;
      // Tính mức nước: chiều cao lắp đặt trừ khoảng cách đo được
      waterLevel = ((tempInstallHeight > 0) ? tempInstallHeight : installHeight) - srf04Distance;
      if (waterLevel < 0)
        waterLevel = 0;
      waterWarning = (waterLevel >= ((tempWarningThreshold > 0) ? tempWarningThreshold : warningThreshold));
    } else {
      sensorOutOfRange = true;
      waterWarning = true;
      waterLevel = installHeight;
    }
    
    vTaskDelay(pdMS_TO_TICKS(200));
  }
}

// -----------------------
// SETUP: KHỞI TẠO, KẾT NỐI WIFI, FIREBASE & CÁC CẢM BIẾN
// -----------------------
void setup() {
  Serial.begin(115200);
  
  // Copy chuỗi từ PROGMEM sang bộ đệm
  strcpy_P(ssidBuffer, wifiSSID);
  strcpy_P(passwordBuffer, wifiPassword);
  strcpy_P(firebaseHostBuffer, firebaseHostStr);
  strcpy_P(firebaseAuthBuffer, firebaseAuthStr);
  strcpy_P(webAppBuffer, Web_App_URL);
  
  // Kết nối WiFi
  WiFi.begin(ssidBuffer, passwordBuffer);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
  }
  Serial.println("WiFi connected!");
  
  // Cấu hình Firebase
  config.host = firebaseHostBuffer;
  config.signer.tokens.legacy_token = firebaseAuthBuffer;
  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);
  
  // Khởi tạo cảm biến DHT11 và cảm biến đất
  dht.begin();
  pinMode(SOIL_MOISTURE_PIN, INPUT);
  
  // Cấu hình chân Relay
  pinMode(RELAY1_PIN, OUTPUT);
  pinMode(RELAY2_PIN, OUTPUT);
  pinMode(RELAY3_PIN, OUTPUT);
  
  // Khởi tạo cảm biến khoảng cách SRF04
  ultrasonicSensor.begin();
  
  // Khởi tạo cảm biến gió và mưa
  initWindSensor();
  initRainSensor();
  
  // Tạo các task xử lý cảm biến sử dụng FreeRTOS
  xTaskCreatePinnedToCore(TaskSRF04Sensor, "SRF04", 4096, NULL, 10, NULL, 1);
  xTaskCreatePinnedToCore(TaskWindSensor, "Wind", 4096, NULL, 10, NULL, 1);
  xTaskCreate(TaskRainSensor, "Rain", 4096, NULL, 1, NULL);
}

// -----------------------
// LOOP: CẬP NHẬT DỮ LIỆU, TÍNH TRUNG BÌNH & ĐIỀU KHIỂN RELAY
// -----------------------
void loop() {
  // Cập nhật dữ liệu lên Firebase sau mỗi khoảng thời gian định sẵn (500 ms)
  if (millis() - lastFirebaseUpdate > firebaseUpdateInterval) {
    lastFirebaseUpdate = millis();
    
    // Đọc nhiệt độ và độ ẩm từ DHT11
    temperature = dht.readTemperature();
    humidity = dht.readHumidity();
    
    // Đọc giá trị analog từ cảm biến độ ẩm đất và chuyển sang phần trăm
    int soilValue = analogRead(SOIL_MOISTURE_PIN);
    soilMoisturePercent = map(soilValue, 0, 4095, 0, 100);
    
    // Cập nhật dữ liệu lên Firebase
    Firebase.setFloat(fbdo, "/DuLieuCamBien/NhietDo", temperature);
    Firebase.setFloat(fbdo, "/DuLieuCamBien/DoAm", humidity);
    Firebase.setInt(fbdo, "/DuLieuCamBien/DoAmDat", soilMoisturePercent);
    Firebase.setInt(fbdo, "/SRF04/KhoangCachHienTai", srf04Distance);
    Firebase.setInt(fbdo, "/SRF04/MucNuoc", waterLevel);
    Firebase.setBool(fbdo, "/SRF04/OutOfRange", sensorOutOfRange);
    Firebase.setBool(fbdo, "/SRF04/WaterWarning", waterWarning);
    Firebase.setFloat(fbdo, "/DuLieuCamBien/WindSpeed", windSpeed);
    Firebase.setFloat(fbdo, "/DuLieuCamBien/Rain", rainValue);
    
    // Tích lũy dữ liệu để tính trung bình cho 5 phút
    tempSum += temperature;
    humiSum += humidity;
    soilSum += soilMoisturePercent;
    windSum += windSpeed;
    rainSum += rainValue;
    sampleCount++;
  }
  
  // Sau 5 phút, tính giá trị trung bình và gửi lên Google Sheet
  if (millis() - lastSheetUpdate > googleSheetUpdateInterval) {
    lastSheetUpdate = millis();
    
    // Nếu có mẫu dữ liệu được tích lũy, tính trung bình
    if(sampleCount > 0) {
      float avgTemp  = tempSum / sampleCount;
      float avgHumi  = humiSum / sampleCount;
      float avgSoil  = soilSum / sampleCount;
      float avgWind  = windSum / sampleCount;
      float avgRain  = rainSum / sampleCount;
      
      // Gửi dữ liệu trung bình lên Google Sheet
      sendDataToGoogleSheet(avgTemp, avgHumi, avgSoil, avgWind, avgRain);
      
      // Reset biến tích lũy và đếm mẫu
      tempSum = humiSum = soilSum = windSum = rainSum = 0;
      sampleCount = 0;
    }
  }
  
  // Điều khiển relay theo trạng thái được lấy từ Firebase
  if (Firebase.getInt(fbdo, "/RELAY/RelayState1")) {
    digitalWrite(RELAY1_PIN, fbdo.to<int>());
  }
  if (Firebase.getInt(fbdo, "/RELAY/RelayState2")) {
    digitalWrite(RELAY2_PIN, fbdo.to<int>());
  }
  if (Firebase.getInt(fbdo, "/RELAY/RelayState3")) {
    digitalWrite(RELAY3_PIN, fbdo.to<int>());
  }
  
  // Delay để giảm tải vòng lặp chính
  vTaskDelay(pdMS_TO_TICKS(1000));
  delay(100);
}
