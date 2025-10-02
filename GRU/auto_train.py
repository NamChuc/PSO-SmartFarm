import schedule
import time
import subprocess

def run_train_script():
    print("⏳ Đang kiểm tra và huấn luyện nếu có dữ liệu mới...")
    try:
        subprocess.run(["python", "C:/Users/hieuv/PycharmProjects/GRU/your_train_script.py"], check=True)
        print("✅ Đã chạy xong script huấn luyện.")
    except subprocess.CalledProcessError as e:
        print("❌ Lỗi khi chạy script huấn luyện:", e)

# Đặt lịch chạy mỗi 10 phút
schedule.every(10).minutes.do(run_train_script)

print("🚀 Đang khởi chạy auto-train mỗi 10 phút...")
from datetime import datetime
print("⏳", datetime.now().strftime("%Y-%m-%d %H:%M:%S"), " → Kiểm tra và huấn luyện...")

while True:
    schedule.run_pending()
    time.sleep(1)
