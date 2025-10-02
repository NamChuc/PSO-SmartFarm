import pandas as pd

# Đường dẫn đến file CSV (đảm bảo file đã được chép vào đúng thư mục)
file_path = "C:/users/hieuv/PycharmProjects/GRU/ESP32_WeatherData_Updated.csv"

# Đọc dữ liệu từ file
df = pd.read_csv(file_path)

# Hiển thị 5 dòng đầu tiên
print("Dữ liệu 5 dòng đầu tiên:")
print(df.head())

# Thông tin tổng quan về dữ liệu
print("\nThông tin tổng quát:")
print(df.info())

# Mô tả thống kê
print("\nThống kê mô tả:")
print(df.describe())
