import gspread
import pandas as pd
import numpy as np
from oauth2client.service_account import ServiceAccountCredentials
from sklearn.preprocessing import MinMaxScaler
from tensorflow.keras.models import load_model

# ======= KẾT NỐI GOOGLE SHEETS =========
scope = ["https://spreadsheets.google.com/feeds", "https://www.googleapis.com/auth/drive"]
creds = ServiceAccountCredentials.from_json_keyfile_name("C:/Users/hieuv/PycharmProjects/GRU/focal-grin-455408-m0-c0013e6015d9.json", scope)
client = gspread.authorize(creds)

# Đọc dữ liệu từ sheet
sheet_url = "https://docs.google.com/spreadsheets/d/19qBwHPrIes6PeGAyIzMORPVB-7utQpaZG7RHrdRfoNI/edit#gid=0"
sheet = client.open_by_url(sheet_url)
worksheet = sheet.worksheet("DATA")
data = pd.DataFrame(worksheet.get_all_records())

# ======= XỬ LÝ DỮ LIỆU =========
data['timestamp'] = pd.to_datetime(data['NGÀY'] + ' ' + data['GIỜ'], format='%d/%m/%Y %H:%M:%S')
data = data.sort_values('timestamp')
data.rename(columns={
    'temperature': 'temp',
    'humidity': 'humid',
    'soil_moisture': 'soil',
    'wind': 'wind',
    'rain': 'rain'
}, inplace=True)

features = ['temp', 'humid', 'soil', 'wind', 'rain']
dataset = data[features].copy()

# ======= CHUẨN HÓA & TẠO SCALER =========
scaler = MinMaxScaler()
scaled_data = scaler.fit_transform(dataset)

# ======= LẤY CHUỖI CUỐI CÙNG ĐỂ DỰ BÁO =========
window_size = 6
x_input = scaled_data[-window_size:]  # Lấy 6 dòng gần nhất
x_input = x_input.reshape(1, window_size, len(features))  # (1, 6, 5)

# ======= LOAD MÔ HÌNH ĐÃ HUẤN LUYỆN =========
model = load_model("C:/Users/hieuv/PycharmProjects/GRU/gru_weather_model.h5")

# ======= DỰ BÁO =========
y_pred = model.predict(x_input)

# ======= CHUYỂN DỰ BÁO VỀ GIÁ TRỊ GỐC =========
y_pred_original = scaler.inverse_transform(y_pred)

# ======= HIỂN THỊ KẾT QUẢ =========
columns = ['temperature', 'humidity', 'soil_moisture', 'wind', 'rain']
pred_df = pd.DataFrame(y_pred_original, columns=columns)

print("\n📈 KẾT QUẢ DỰ BÁO THỜI TIẾT TIẾP THEO:")
print(pred_df.round(2))
