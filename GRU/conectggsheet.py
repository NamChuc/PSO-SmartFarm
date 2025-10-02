import gspread
import pandas as pd
import numpy as np
from oauth2client.service_account import ServiceAccountCredentials
from sklearn.preprocessing import MinMaxScaler

# ======= KẾT NỐI GOOGLE SHEETS =========
scope = ["https://spreadsheets.google.com/feeds", "https://www.googleapis.com/auth/drive"]
creds = ServiceAccountCredentials.from_json_keyfile_name("D:/tgmt/DAKT/GRU/focal-grin-455408-m0-c0013e6015d9.json", scope)
client = gspread.authorize(creds)

# Đọc dữ liệu từ sheet
sheet_url = "https://docs.google.com/spreadsheets/d/19qBwHPrIes6PeGAyIzMORPVB-7utQpaZG7RHrdRfoNI/edit#gid=0"
sheet = client.open_by_url(sheet_url)
worksheet = sheet.worksheet("DATA")
data = pd.DataFrame(worksheet.get_all_records())

# ======= XỬ LÝ CỘT THỜI GIAN =========
data['timestamp'] = pd.to_datetime(data['NGÀY'] + ' ' + data['GIỜ'], format='%d/%m/%Y %H:%M:%S')
data = data.sort_values('timestamp')

# Đổi tên cột để dễ gọi
data.rename(columns={
    'temperature': 'temp',
    'humidity': 'humid',
    'soil_moisture': 'soil',
    'wind': 'wind',
    'rain': 'rain'
}, inplace=True)

# ======= CHUẨN HÓA DỮ LIỆU =========
features = ['temp', 'humid', 'soil', 'wind', 'rain']
dataset = data[features].copy()

scaler = MinMaxScaler()
scaled_data = scaler.fit_transform(dataset)

print("✅ Dữ liệu đã chuẩn hóa:")
print(pd.DataFrame(scaled_data, columns=features).head())

# ======= TẠO CHUỖI THỜI GIAN =========
window_size = 6
X, y = [], []

for i in range(len(scaled_data) - window_size):
    X.append(scaled_data[i:i + window_size])
    y.append(scaled_data[i + window_size])

X = np.array(X)
y = np.array(y)

print("\n✅ Chuỗi thời gian đã tạo:")
print(f"X shape: {X.shape}")
print(f"y shape: {y.shape}")
