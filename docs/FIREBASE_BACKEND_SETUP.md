# Bật đăng nhập Google (Firebase) cho backend Courtly

Mặc định tính năng này **TẮT** (`firebase.enabled=false`) và `POST /api/auth/google` trả **503** với thông báo "Đăng nhập Google chưa được cấu hình". Mọi chức năng khác vẫn chạy bình thường. Các chỗ trong code cần chú ý được đánh dấu `// TODO(FIREBASE):`.

## Luồng hoạt động

1. App Flutter đăng nhập Google bằng `firebase_auth` + `google_sign_in`.
2. App lấy **Firebase ID token** (`await FirebaseAuth.instance.currentUser!.getIdToken()`) và gửi `POST /api/auth/google` với body `{ "idToken": "..." }`.
3. Backend xác minh token bằng Firebase Admin SDK (`FirebaseAuth.verifyIdToken`), rồi:
   - tìm user theo `firebase_uid`, nếu không có thì tìm theo email;
   - nếu email trùng một tài khoản LOCAL thì **liên kết** tài khoản đó (ghi `firebase_uid`, không tạo bản ghi mới);
   - nếu chưa có thì tạo user mới (`auth_provider = GOOGLE`, `role = CUSTOMER`, `password_hash = NULL`).
4. Backend trả `AuthResponse` gồm access token và refresh token **của Courtly**, giống hệt `/api/auth/login`.

> Backend chỉ chấp nhận token có email **đã xác minh** (`email_verified = true`), để không ai chiếm được tài khoản LOCAL bằng một email chưa xác minh.

## Bước 1: Tạo project Firebase (nếu chưa có)

1. Vào https://console.firebase.google.com, chọn **Add project**, rồi đặt tên (ví dụ `courtly`).
2. Vào **Build > Authentication > Sign-in method** và bật **Google**.
3. Thêm app Android/iOS/Web cho Flutter (`flutterfire configure`). App Flutter và backend **phải dùng cùng một project**.
4. Ghi lại **Project ID** (trong *Project settings > General*), ví dụ `courtly-ab12c`.

## Bước 2: Tạo service account key

1. Vào **Project settings > Service accounts**.
2. Chọn **Firebase Admin SDK**, rồi bấm **Generate new private key**, sau đó **Generate key**.
3. Bạn sẽ tải về một file JSON, ví dụ `courtly-ab12c-firebase-adminsdk-xxxxx.json`.
4. Lưu file **ngoài thư mục dự án**, ví dụ `C:\secrets\courtly-firebase.json`.

> ⚠️ File này là khóa quản trị toàn quyền của project. **KHÔNG commit lên git, không gửi qua chat nhóm.** `.gitignore` đã chặn các mẫu `*firebase-adminsdk*.json` và `secrets/`, nhưng tốt nhất vẫn nên để file ngoài repo. Nếu lỡ lộ key, hãy xóa ngay ở cùng màn hình Service accounts.

## Bước 3: Đặt biến môi trường

PowerShell (chỉ có hiệu lực trong cửa sổ đang mở):

```powershell
$env:FIREBASE_ENABLED = "true"
$env:FIREBASE_PROJECT_ID = "courtly-ab12c"
$env:FIREBASE_SERVICE_ACCOUNT_PATH = "C:\secrets\courtly-firebase.json"
```

IntelliJ: mở **Run > Edit Configurations > Environment variables** và thêm ba biến trên.

| Biến | Ý nghĩa |
|---|---|
| `FIREBASE_ENABLED` | `true` để bật (mặc định `false`) |
| `FIREBASE_PROJECT_ID` | Project ID của Firebase |
| `FIREBASE_SERVICE_ACCOUNT_PATH` | Đường dẫn tuyệt đối tới file JSON ở Bước 2 |

## Bước 4: Khởi động và kiểm tra

1. Chạy backend. Log sẽ có dòng `Firebase Admin SDK da khoi tao cho project courtly-ab12c`.
   - Nếu bật mà thiếu đường dẫn hoặc file không đọc được, app **dừng ngay** và báo lỗi rõ ràng.
2. Lấy một ID token thật từ app Flutter (in ra log ở chế độ debug), rồi gọi:

```bash
curl -X POST http://localhost:8080/api/auth/google \
  -H "Content-Type: application/json" \
  -d '{"idToken":"<firebase id token>"}'
```

| Kết quả | Ý nghĩa |
|---|---|
| 200 + AuthResponse | Thành công |
| 401 "Google ID token không hợp lệ hoặc đã hết hạn" | Token sai, đã hết hạn (ID token sống khoảng 1 giờ), hoặc thuộc **project Firebase khác** |
| 401 "Email Google chưa được xác minh" | Tài khoản Google chưa xác minh email |
| 503 | Vẫn đang tắt: kiểm tra `FIREBASE_ENABLED` |

## Ghi chú

- Phải gửi **Firebase ID token**, không phải Google OAuth `idToken`/`accessToken` từ `google_sign_in`. Hãy đăng nhập vào Firebase trước rồi mới gọi `getIdToken()`.
- Có thể chuyển sang `verifyIdToken(idToken, true)` để kiểm tra cả token đã bị thu hồi (xem TODO trong `GoogleAuthService`).
- Khi deploy, đặt các biến trên ở server (`SPRING_PROFILES_ACTIVE=prod`) và không đóng gói file JSON vào jar.
