from flask import Flask, jsonify
import pandas as pd
import os
import firebase_admin
from firebase_admin import credentials, db
from datetime import datetime

app = Flask(__name__)

@app.route('/weather/predict', methods=['GET'])
def get_prediction():
    try:
        json_path = "D:/tgmt/DAKT/GRU/latest_prediction.json"

        # Kiểm tra nếu file dự báo không tồn tại
        if not os.path.exists(json_path):
            return jsonify({"error": "❌ File dự báo chưa tồn tại"}), 404

        # Đọc dữ liệu từ file JSON
        df = pd.read_json(json_path, orient="records")

        # Đảm bảo thứ tự cột: time, temp, humid, soil, wind, rain
        df = df[["time", "temp", "humid", "soil", "wind", "rain"]]

        # Lấy thời gian hiện tại và tính ngày mai
        now = datetime.now()
        tomorrow = (now + pd.Timedelta(days=1)).strftime("%d/%m/%Y")  # Lấy ngày mai theo định dạng ngày/tháng/năm

        # Lọc dữ liệu dự báo ngày mai
        tomorrow_data = df[df['time'].str.contains(tomorrow)]

        if tomorrow_data.empty:
            return jsonify({"error": "❌ Không có dữ liệu cho ngày mai"}), 404

        # Lưu vào Firebase dưới mục 'forecast/tomorrow'
        forecast_data = tomorrow_data.to_dict(orient="records")

        # Cập nhật vào Firebase (chỉ thay thế dữ liệu của 'tomorrow')
        if not firebase_admin._apps:
            cred = credentials.Certificate(
                "D:/tgmt/DAKT/GRU/smart-farm-6e42d-firebase-adminsdk-fbsvc-9f6b7c2379.json")
            firebase_admin.initialize_app(cred, {
                'databaseURL': 'https://smart-farm-6e42d-default-rtdb.firebaseio.com/'
            })

        # Trước khi ghi, kiểm tra và thay thế dữ liệu mục 'tomorrow'
        ref = db.reference("forecast/tomorrow")
        ref.set(forecast_data)  # Cập nhật trực tiếp

        return jsonify({"tomorrow": forecast_data})

    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    # Cho phép truy cập từ app Android cùng mạng LAN
    app.run(host="0.0.0.0", port=8080)
