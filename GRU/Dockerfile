# Sử dụng Python 3.9 image từ Docker Hub
FROM python:3.9-slim

# Cài đặt các thư viện cần thiết
WORKDIR /app
COPY . /app
RUN pip install --no-cache-dir -r requirements.txt

# Mở cổng 8080
EXPOSE 8080

# Lệnh để chạy Flask app
CMD ["gunicorn", "-b", "0.0.0.0:$PORT", "app:app"]

