package com.example.smartfarm;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.HashMap;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {
    private TextView tempTextView, humiTextView, soilMoistureTextView, rainSpeedText, windSpeedText;
    private TextView tempValue, humiValue, soilValue, distanceValue, waterLevelValue;
    private EditText etTempMin, etTempMax, etHumiMin, etHumiMax, etSoilMin, etSoilMax;
    private Switch relaySwitch1, relaySwitch2, relaySwitch3;
    private ImageView waterLevelImage;
    private DatabaseReference databaseReference, relayReference1, relayReference2, relayReference3, modeReference, plantReference;
    private Spinner modeSpinner, plantSpinner;
    private Button btnSendToFirebase;
    private String previousPlant = "";

    // Định dạng giá trị cảm biến
    private String formatValue(String label, Double value, String unit) {
        return label + (value != null ? String.format("%.2f", value) + unit : "N/A");
    }

    private final String[] plants = {
            "Tự thiết lập", "Sen đá (Succulent)", "Xương rồng", "Trầu bà (Pothos)", "Lưỡi hổ (Snake Plant)",
            "Cây Lan Ý (Peace Lily)", "Cây Kim Tiền", "Cây Ngũ Gia Bì", "Cây Đa Búp Đỏ",
            "Cây Cẩm Nhung", "Cây Dương Xỉ"
    };

    private final int[][] plantValues = {
            {20, 30, 40, 60, 10, 20},  // Sen đá
            {20, 35, 30, 50, 5, 15},   // Xương rồng
            {18, 30, 50, 70, 40, 60},  // Trầu bà
            {15, 30, 40, 60, 20, 40},  // Lưỡi hổ
            {18, 28, 60, 80, 50, 70},  // Cây Lan Ý
            {20, 30, 40, 60, 30, 50},  // Cây Kim Tiền
            {20, 32, 50, 70, 40, 60},  // Cây Ngũ Gia Bì
            {18, 30, 50, 70, 40, 60},  // Cây Đa Búp Đỏ
            {20, 28, 60, 80, 50, 70},  // Cây Cẩm Nhung
            {18, 28, 60, 80, 50, 70}   // Cây Dương Xỉ
    };

    private void setupSpinners() {
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"Auto", "Thủ công"});
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(modeAdapter);

        ArrayAdapter<String> plantAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, plants);
        plantAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        plantSpinner.setAdapter(plantAdapter);
    }

    // Lấy giá trị ngưỡng từ Spinner cây trồng
    private double[] getThresholdFromSpinner() {
        int position = plantSpinner.getSelectedItemPosition();

        if (position == 0) { // Nếu là "Tự thiết lập"
            try {
                double tempMin = Double.parseDouble(etTempMin.getText().toString());
                double tempMax = Double.parseDouble(etTempMax.getText().toString());
                double humiMin = Double.parseDouble(etHumiMin.getText().toString());
                double humiMax = Double.parseDouble(etHumiMax.getText().toString());
                double soilMin = Double.parseDouble(etSoilMin.getText().toString());
                double soilMax = Double.parseDouble(etSoilMax.getText().toString());

                return new double[]{tempMin, tempMax, humiMin, humiMax, soilMin, soilMax};
            } catch (NumberFormatException e) {
                Log.e("Threshold", "Lỗi chuyển đổi ngưỡng: " + e.getMessage());
                Toast.makeText(this, "Vui lòng nhập giá trị hợp lệ!", Toast.LENGTH_SHORT).show();
            }
        }

        // Lấy giá trị từ mảng plantValues
        int[] thresholds = plantValues[position - 1]; // Trừ 1 vì "Tự thiết lập" là vị trí 0
        return new double[]{
                thresholds[0], thresholds[1], // Nhiệt độ Min, Max
                thresholds[2], thresholds[3], // Độ ẩm Min, Max
                thresholds[4], thresholds[5]  // Độ ẩm đất Min, Max
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Ánh xạ Spinner
        modeSpinner = findViewById(R.id.modeSpinner);
        plantSpinner = findViewById(R.id.plantSpinner);
        setupSpinners();

        // Ánh xạ các TextView hiển thị dữ liệu cảm biến
        tempTextView = findViewById(R.id.tempTextView);
        humiTextView = findViewById(R.id.humiTextView);
        soilMoistureTextView = findViewById(R.id.soilMoistureTextView);
        tempValue = findViewById(R.id.tempValue);
        humiValue = findViewById(R.id.humiValue);
        soilValue = findViewById(R.id.soilValue);
        distanceValue = findViewById(R.id.distanceValue);
        waterLevelValue = findViewById(R.id.waterLevelValue);
        etTempMin = findViewById(R.id.etTempMin);
        etTempMax = findViewById(R.id.etTempMax);
        etHumiMin = findViewById(R.id.etHumiMin);
        etHumiMax = findViewById(R.id.etHumiMax);
        etSoilMin = findViewById(R.id.etSoilMin);
        etSoilMax = findViewById(R.id.etSoilMax);
        rainSpeedText = findViewById(R.id.rainSpeedText);
        windSpeedText = findViewById(R.id.windSpeedText);

        // Ánh xạ Switch điều khiển relay
        relaySwitch1 = findViewById(R.id.switchRelay1);
        relaySwitch2 = findViewById(R.id.switchRelay2);
        // Ánh xạ relaySwitch3 (bổ sung mới)
        relaySwitch3 = findViewById(R.id.switchRelay3);
        btnSendToFirebase = findViewById(R.id.btnSendToFirebase);

        // Ánh xạ ImageView hiển thị mức nước
        waterLevelImage = findViewById(R.id.waterLevelImage);

        // Kết nối Firebase Database
        databaseReference = FirebaseDatabase.getInstance("https://smart-farm-6e42d-default-rtdb.firebaseio.com/").getReference();
        relayReference1 = databaseReference.child("RELAY").child("RelayState1");
        relayReference2 = databaseReference.child("RELAY").child("RelayState2");
        // Khai báo DatabaseReference cho relay3
        relayReference3 = databaseReference.child("RELAY").child("RelayState3");
        modeReference = databaseReference.child("Mode");
        plantReference = databaseReference.child("PlantSettings");

        // Dữ liệu cho Spinner (Auto và Thủ công)
        String[] modes = {"Auto", "Thủ công"};
        autoControlRelay();

        // Xử lý chọn chế độ
        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean isManualMode = (position == 1); // Thủ công

                modeReference.setValue(isManualMode ? "Thủ công" : "Auto");
                relaySwitch1.setEnabled(isManualMode);
                relaySwitch2.setEnabled(isManualMode);
                relaySwitch3.setEnabled(isManualMode); // Bật/tắt relay3 theo chế độ thủ công

                if (!isManualMode) {
                    relaySwitch1.setChecked(false);
                    relaySwitch2.setChecked(false);
                    relaySwitch3.setChecked(false);
                    relayReference1.setValue(0);
                    relayReference2.setValue(0);
                    relayReference3.setValue(0);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Xử lý khi chọn item trong plant spinner
        plantSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedPlant = plants[position];

                // Nếu có cây trước đó và cây hiện tại khác cây trước đó thì xóa khỏi Firebase
                if (!previousPlant.isEmpty() && !previousPlant.equals(selectedPlant)) {
                    plantReference.child(previousPlant).removeValue()
                            .addOnSuccessListener(aVoid -> Log.d("Firebase", "Xóa dữ liệu: " + previousPlant))
                            .addOnFailureListener(e -> Log.e("Firebase", "Lỗi xóa dữ liệu", e));
                }

                if (selectedPlant.equals("Tự thiết lập")) {
                    // Xóa dữ liệu min/max và cho phép nhập liệu
                    etTempMin.setText("");
                    etTempMax.setText("");
                    etHumiMin.setText("");
                    etHumiMax.setText("");
                    etSoilMin.setText("");
                    etSoilMax.setText("");

                    etTempMin.setEnabled(true);
                    etTempMax.setEnabled(true);
                    etHumiMin.setEnabled(true);
                    etHumiMax.setEnabled(true);
                    etSoilMin.setEnabled(true);
                    etSoilMax.setEnabled(true);
                } else {
                    // Khóa các trường nhập liệu và gửi dữ liệu mặc định lên Firebase
                    etTempMin.setEnabled(false);
                    etTempMax.setEnabled(false);
                    etHumiMin.setEnabled(false);
                    etHumiMax.setEnabled(false);
                    etSoilMin.setEnabled(false);
                    etSoilMax.setEnabled(false);

                    // Cập nhật và gửi dữ liệu
                    updatePlantValues(plantValues[position]);
                    sendPlantDataToFirebase(selectedPlant, plantValues[position]);
                }

                // Lưu lại cây hiện tại làm cây trước đó
                previousPlant = selectedPlant;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Xử lý sự kiện nhấn nút "Gửi lên Firebase"
        btnSendToFirebase.setOnClickListener(v -> sendPlantDataToFirebase());

        // Lắng nghe dữ liệu cảm biến từ Firebase
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Đọc dữ liệu từ Firebase
                    Double temperature = snapshot.child("DuLieuCamBien").child("NhietDo").getValue(Double.class);
                    Double humidity = snapshot.child("DuLieuCamBien").child("DoAm").getValue(Double.class);
                    Double soilMoisture = snapshot.child("DuLieuCamBien").child("DoAmDat").getValue(Double.class);
                    Integer relayState1 = snapshot.child("RELAY").child("RelayState1").getValue(Integer.class);
                    Integer relayState2 = snapshot.child("RELAY").child("RelayState2").getValue(Integer.class);
                    Integer relayState3 = snapshot.child("RELAY").child("RelayState3").getValue(Integer.class);
                    Double khoangCach = snapshot.child("SRF04").child("KhoangCachHienTai").getValue(Double.class);
                    String currentMode = snapshot.child("Mode").getValue(String.class);
                    Double rain = snapshot.child("DuLieuCamBien").child("Rain").getValue(Double.class);
                    Double windSpeed = snapshot.child("DuLieuCamBien").child("WindSpeed").getValue(Double.class);
                    Double mucNuoc = snapshot.child("SRF04").child("MucNuoc").getValue(Double.class);

                    // Hiển thị dữ liệu lên TextView
                    tempTextView.setText("Nhiệt độ: " + (temperature != null ? temperature : "N/A") + "°C");
                    humiTextView.setText("Độ ẩm: " + (humidity != null ? humidity : "N/A") + "%");
                    soilMoistureTextView.setText("Độ ẩm đất: " + (soilMoisture != null ? soilMoisture : "N/A") + "%");
                    tempValue.setText((temperature != null ? temperature : "N/A") + "°C");
                    humiValue.setText((humidity != null ? humidity : "N/A") + "%");
                    soilValue.setText((soilMoisture != null ? soilMoisture : "N/A") + "%");
                    distanceValue.setText(formatValue("Khoảng cách: ", khoangCach, "cm"));
                    waterLevelValue.setText(formatValue("Mực nước: ", mucNuoc, "cm"));

                    // Cập nhật trạng thái Switch
                    relaySwitch1.setChecked(relayState1 != null && relayState1 == 1);
                    relaySwitch2.setChecked(relayState2 != null && relayState2 == 1);
                    relaySwitch3.setChecked(relayState3 != null && relayState3 == 1);

                    // Cập nhật hình ảnh mức nước
                    if (mucNuoc != null) {
                        updateWaterLevel(mucNuoc.intValue());
                    }
                    // Hiển thị dữ liệu Rain và WindSpeed
                    windSpeedText.setText("Tốc độ gió: " + (windSpeed != null ? windSpeed : "N/A") + " m/s");
                    rainSpeedText.setText("Lượng mưa: " + (rain != null ? rain : "N/A") + " mm");

                    // Cập nhật Spinner về chế độ hiện tại
                    if (currentMode != null) {
                        int spinnerPosition = currentMode.equals("Auto") ? 0 : 1;
                        modeSpinner.setSelection(spinnerPosition);
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Không tìm thấy dữ liệu!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase", "Lỗi tải dữ liệu", error.toException());
                Toast.makeText(MainActivity.this, "Lỗi tải dữ liệu từ Firebase!", Toast.LENGTH_SHORT).show();
            }
        });

        // Điều khiển relay thông qua Switch
        relaySwitch1.setOnCheckedChangeListener((buttonView, isChecked) ->
                relayReference1.setValue(isChecked ? 1 : 0)
        );
        relaySwitch2.setOnCheckedChangeListener((buttonView, isChecked) ->
                relayReference2.setValue(isChecked ? 1 : 0)
        );
        relaySwitch3.setOnCheckedChangeListener((buttonView, isChecked) ->
                relayReference3.setValue(isChecked ? 1 : 0)
        );
    }

    // Hàm cập nhật hình ảnh mức nước
    private void updateWaterLevel(int waterLevel) {
        if (waterLevel <= 0) {
            waterLevelImage.setImageResource(R.drawable.water_level_0);
        } else if (waterLevel < 20) {
            waterLevelImage.setImageResource(R.drawable.water_level_20);
        } else if (waterLevel < 40) {
            waterLevelImage.setImageResource(R.drawable.water_level_40);
        } else if (waterLevel < 60) {
            waterLevelImage.setImageResource(R.drawable.water_level_60);
        } else if (waterLevel < 80) {
            waterLevelImage.setImageResource(R.drawable.water_level_80);
        } else {
            waterLevelImage.setImageResource(R.drawable.water_level_100);
        }
    }

    // Cập nhật các giá trị nhiệt độ, độ ẩm, độ ẩm đất khi chọn cây trồng
    private void updatePlantValues(int[] values) {
        etTempMin.setText(String.valueOf(values[0]));
        etTempMax.setText(String.valueOf(values[1]));
        etHumiMin.setText(String.valueOf(values[2]));
        etHumiMax.setText(String.valueOf(values[3]));
        etSoilMin.setText(String.valueOf(values[4]));
        etSoilMax.setText(String.valueOf(values[5]));
    }

    // 1️⃣ Gửi dữ liệu cây trồng lên Firebase từ các giá trị nhập vào
    private void sendPlantDataToFirebase() {
        // Lấy tên cây trồng từ Spinner
        String plantName = plantSpinner.getSelectedItem().toString();

        try {
            // Lấy giá trị từ các trường nhập liệu (EditText)
            int tempMin = Integer.parseInt(etTempMin.getText().toString());
            int tempMax = Integer.parseInt(etTempMax.getText().toString());
            int humiMin = Integer.parseInt(etHumiMin.getText().toString());
            int humiMax = Integer.parseInt(etHumiMax.getText().toString());
            int soilMin = Integer.parseInt(etSoilMin.getText().toString());
            int soilMax = Integer.parseInt(etSoilMax.getText().toString());

            // Tạo mảng chứa các giá trị
            int[] plantValues = {tempMin, tempMax, humiMin, humiMax, soilMin, soilMax};

            // Gọi phương thức chính để gửi dữ liệu
            sendPlantDataToFirebase(plantName, plantValues);

        } catch (NumberFormatException e) {
            Toast.makeText(MainActivity.this, "Vui lòng nhập đầy đủ và đúng định dạng số!", Toast.LENGTH_SHORT).show();
            Log.e("Firebase", "Lỗi định dạng số: ", e);
        }
    }

    // 2️⃣ Gửi dữ liệu cây trồng dựa trên tên cây trồng và mảng giá trị
    private void sendPlantDataToFirebase(String plantName, int[] plantValues) {
        if (plantValues.length == 6) {
            // Tạo HashMap để lưu trữ dữ liệu
            HashMap<String, Object> plantData = new HashMap<>();
            plantData.put("TempMin", plantValues[0]);
            plantData.put("TempMax", plantValues[1]);
            plantData.put("HumiMin", plantValues[2]);
            plantData.put("HumiMax", plantValues[3]);
            plantData.put("SoilMin", plantValues[4]);
            plantData.put("SoilMax", plantValues[5]);

            // Đẩy dữ liệu lên Firebase
            plantReference.child(plantName).setValue(plantData)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(MainActivity.this, "Dữ liệu cây trồng '" + plantName + "' đã gửi thành công!", Toast.LENGTH_SHORT).show();
                        Log.d("Firebase", "Dữ liệu đã gửi thành công: " + plantName);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(MainActivity.this, "Lỗi khi gửi dữ liệu cây trồng!", Toast.LENGTH_SHORT).show();
                        Log.e("Firebase", "Lỗi khi gửi dữ liệu cây trồng: ", e);
                    });
        } else {
            Toast.makeText(MainActivity.this, "Dữ liệu cây trồng không hợp lệ!", Toast.LENGTH_SHORT).show();
        }
    }

    // 3️⃣ Gửi giá trị tùy chỉnh lên Firebase
    private void sendPlantDataToFirebase(String customValue) {
        // Tạo HashMap để lưu giá trị tùy chỉnh
        HashMap<String, Object> customData = new HashMap<>();
        customData.put("CustomValue", customValue);

        // Gửi giá trị tùy chỉnh lên Firebase
        plantReference.child("CustomPlant").setValue(customData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(MainActivity.this, "Gửi giá trị tùy chỉnh thành công!", Toast.LENGTH_SHORT).show();
                    Log.d("Firebase", "Giá trị tùy chỉnh đã được gửi: " + customValue);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(MainActivity.this, "Lỗi khi gửi giá trị tùy chỉnh!", Toast.LENGTH_SHORT).show();
                    Log.e("Firebase", "Lỗi khi gửi giá trị tùy chỉnh: ", e);
                });
    }

    private void autoControlRelay() {
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference();

        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Kiểm tra chế độ Auto
                    String mode = snapshot.child("Mode").getValue(String.class);
                    if ("Auto".equals(mode)) {
                        try {
                            // Lấy dữ liệu cảm biến
                            double nhietDo = snapshot.child("DuLieuCamBien").child("NhietDo").getValue(Double.class);
                            double doAm = snapshot.child("DuLieuCamBien").child("DoAm").getValue(Double.class);
                            double doAmDat = snapshot.child("DuLieuCamBien").child("DoAmDat").getValue(Double.class);
                            Double mucNuoc = snapshot.child("SRF04").child("MucNuoc").getValue(Double.class);
                            // Lấy giá trị WindSpeed để điều khiển Relay3 (ví dụ: nếu WindSpeed > 10 m/s thì bật relay3)
                            Double windSpeedAuto = snapshot.child("DuLieuCamBien").child("WindSpeed").getValue(Double.class);

                            // Lấy ngưỡng từ PlantSettings > "Tự thiết lập"
                            DataSnapshot plantSettings = snapshot.child("PlantSettings").child("Tự thiết lập");

                            double tempMin = plantSettings.child("TempMin").getValue(Double.class);
                            double tempMax = plantSettings.child("TempMax").getValue(Double.class);
                            double humiMin = plantSettings.child("HumiMin").getValue(Double.class);
                            double humiMax = plantSettings.child("HumiMax").getValue(Double.class);
                            double soilMin = plantSettings.child("SoilMin").getValue(Double.class);
                            double soilMax = plantSettings.child("SoilMax").getValue(Double.class);

                            // So sánh để xác định trạng thái relay
                            boolean relay1State = (nhietDo > tempMax) || (doAm < humiMin);
                            boolean relay2State = (doAmDat < soilMin);
                            // Ví dụ: nếu windSpeed > 10 m/s thì bật relay3
                            boolean relay3State = (mucNuoc != null && mucNuoc < 20);


                            // Cập nhật trạng thái Relay lên Firebase
                            updateRelayState(relay1State, relay2State, relay3State);

                            Log.d("AutoControl", "Relay1: " + relay1State + ", Relay2: " + relay2State + ", Relay3: " + relay3State);
                        } catch (Exception e) {
                            Log.e("AutoControl", "Lỗi khi đọc dữ liệu: " + e.getMessage());
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase", "Lỗi: " + error.getMessage());
            }
        });
    }

    // Cập nhật trạng thái Relay, bổ sung relay3
    private void updateRelayState(boolean relay1, boolean relay2, boolean relay3) {
        DatabaseReference relayRef = FirebaseDatabase.getInstance().getReference().child("RELAY");

        relayRef.child("RelayState1").setValue(relay1 ? 1 : 0);
        relayRef.child("RelayState2").setValue(relay2 ? 1 : 0);
        relayRef.child("RelayState3").setValue(relay3 ? 1 : 0);

        Log.d("RelayUpdate", "Relay1: " + (relay1 ? 1 : 0)
                + ", Relay2: " + (relay2 ? 1 : 0)
                + ", Relay3: " + (relay3 ? 1 : 0));
    }
}
