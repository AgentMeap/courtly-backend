# Module xác thực Courtly: hướng dẫn chạy

## 1. Chuẩn bị SQL Server

1. Bật SQL Server và mở TCP port 1433 (SQL Server Configuration Manager > Protocols > TCP/IP > IPAll > TCP Port = 1433).
2. Tạo database:
   ```powershell
   sqlcmd -S localhost -U sa -P "<mat khau>" -C -i docs/sql/create_database.sql
   ```
   Lệnh này tạo `CourtlyDB` (để chạy app) và `CourtlyDB_Test` (cho integration test). **Bảng do Flyway tạo** khi app khởi động (`db/migration/V1__create_auth_tables.sql`).

## 2. Biến môi trường

| Biến | Bắt buộc | Mặc định |
|---|---|---|
| `DB_PASSWORD` | **Có** (thiếu thì app dừng và báo lỗi) | không có |
| `DB_URL` | không | `jdbc:sqlserver://localhost:1433;databaseName=CourtlyDB;encrypt=true;trustServerCertificate=true` |
| `DB_USERNAME` | không | `sa` |
| `JWT_SECRET` | **Có khi `SPRING_PROFILES_ACTIVE=prod`** (>= 32 byte) | secret dev, chỉ dùng cho dev |
| `SPRING_PROFILES_ACTIVE` | không | (trống). Đặt `prod` khi deploy để bắt buộc `JWT_SECRET` và tắt seed |
| `SEED_ENABLED` | không | `true` |
| `SHOW_SQL` | không | `true` (nên đặt `false` khi deploy) |
| `SEED_PASSWORD` | không | `123456` |
| `CORS_ALLOWED_ORIGINS` | không | `http://localhost:*,http://127.0.0.1:*,http://10.0.2.2:*` |
| `TEST_DB_URL` | không | như `DB_URL`, nhưng dùng database `CourtlyDB_Test` |
| `MAIL_USERNAME` | **Có để đăng ký được** (thiếu thì gửi OTP trả 503) | không có |
| `MAIL_PASSWORD` | **Có để đăng ký được**: App Password 16 ký tự của Gmail | không có |
| `MAIL_FROM` | không | bằng `MAIL_USERNAME` |
| `MAIL_HOST` / `MAIL_PORT` | không | `smtp.gmail.com` / `587` |
| `FIREBASE_*` | không | xem `docs/FIREBASE_BACKEND_SETUP.md` |

### Cấu hình Gmail để gửi OTP

Gmail **không cho đăng nhập SMTP bằng mật khẩu thường**. Bạn cần tạo một App Password:

1. Dùng một tài khoản Gmail riêng cho nhóm, ví dụ `courtly.noreply@gmail.com`.
2. Bật **Xác minh 2 bước** tại https://myaccount.google.com/security.
3. Vào https://myaccount.google.com/apppasswords, đặt tên (ví dụ `Courtly backend`) rồi bấm **Create**. Google sẽ hiện một mật khẩu 16 ký tự.
4. Đặt biến môi trường (có thể bỏ khoảng trắng trong mật khẩu):
   ```powershell
   $env:MAIL_USERNAME = "courtly.noreply@gmail.com"
   $env:MAIL_PASSWORD = "abcdefghijklmnop"
   ```

> App Password có toàn quyền gửi mail từ tài khoản đó, nên **không commit vào git**. Gmail cá nhân giới hạn khoảng 500 email/ngày, đủ dùng cho bài lab.

Tạo secret ngẫu nhiên cho prod:

```powershell
[Convert]::ToBase64String((1..48 | % { Get-Random -Max 256 }))
```

## 3. Chạy

```powershell
$env:DB_PASSWORD = "<mat khau sa>"
./mvnw spring-boot:run
```

- Swagger UI: http://localhost:8080/swagger-ui.html (bấm **Authorize** rồi dán access token).
- Khi `SEED_ENABLED=true` (mặc định) và không chạy `prod`, app tự tạo hai tài khoản mẫu (mật khẩu `SEED_PASSWORD`, mặc định `123456`):
  - `admin@courtly.vn` / SĐT `0900000001`, role ADMIN
  - `customer@courtly.vn` / SĐT `0900000002`, role CUSTOMER
- Từ máy ảo Android, gọi backend qua `http://10.0.2.2:8080`.

## 4. Test

```powershell
$env:DB_PASSWORD = "<mat khau sa>"
./mvnw test
```

- Unit test (JWT, hash refresh token, chuẩn hóa SĐT) luôn chạy.
- Integration test chạy trên **SQL Server thật** (`CourtlyDB_Test`) và **tự bỏ qua (skipped)** khi không có `DB_PASSWORD`. Mỗi test xóa sạch dữ liệu các bảng trong `CourtlyDB_Test`. Test **không gửi email thật**: `OtpMailSender` được mock và test đọc mã OTP từ mock.

## 5. API

| Method | Path | Auth | Kết quả |
|---|---|---|---|
| POST | `/api/auth/register/send-otp` | không | 200 `{ message, expiresIn, resendAfter }` (giây). 409 nếu email đã dùng, 429 nếu gửi quá nhanh hoặc quá nhiều, 503 nếu chưa cấu hình mail hoặc gửi lỗi |
| POST | `/api/auth/register` | không | 201 AuthResponse. Body `{ fullName, email, phone?, password, otp }`. 400 nếu OTP sai hoặc hết hạn |
| POST | `/api/auth/login` | không | 200 AuthResponse (`identifier` = email hoặc SĐT) |
| POST | `/api/auth/google` | không | 200 AuthResponse / 503 khi Google đang tắt |
| POST | `/api/auth/refresh` | không | 200 AuthResponse (refresh token cũ bị thu hồi) |
| POST | `/api/auth/logout` | không | 204 (idempotent) |
| GET | `/api/users/me` | Bearer | 200 UserDto |
| GET | `/api/admin/ping` | Bearer, ADMIN | 200 `{ "message": "pong" }` |

Body lỗi: `{ timestamp, status, error, message, path, fieldErrors? }`.

### Đăng ký bằng OTP email

1. Màn hình nhập email gọi `POST /api/auth/register/send-otp` `{ "email": "..." }`. Backend gửi mã 6 số tới email đó.
2. Người dùng nhập mã. App gọi `POST /api/auth/register` với đầy đủ thông tin cùng `"otp": "123456"`. Chỉ khi mã đúng thì tài khoản mới được tạo, và app nhận AuthResponse luôn (không cần đăng nhập lại).

Quy tắc:

- Mã sống **5 phút** (`expiresIn = 300`). Phải chờ **60 giây** giữa hai lần gửi (`resendAfter = 60`), nên dùng giá trị này cho nút "Gửi lại mã". Tối đa **5 lần gửi mỗi giờ** cho mỗi email.
- Gửi mã mới thì mã cũ hết hiệu lực.
- Mỗi mã cho phép nhập sai tối đa **5 lần**. Message sẽ báo "còn N lần thử", hết lượt thì phải gửi mã mới.
- Nếu đăng ký bị lỗi vì trùng SĐT (409) thì mã OTP **vẫn còn dùng được**: người dùng sửa SĐT rồi gửi lại với cùng mã.
- DB chỉ lưu BCrypt của mã (bảng `email_otps`, Flyway V2), không lưu mã thô và không ghi mã ra log.

### Lưu ý cho app Flutter

- `expiresIn` là số giây (1800). Khi access token hết hạn, API trả 401 với message "Phiên đăng nhập đã hết hạn". Lúc đó gọi `/refresh` rồi **lưu refresh token MỚI**, vì token cũ đã bị thu hồi.
- **Đừng gọi `/refresh` song song với cùng một refresh token**. Request thứ hai sẽ bị coi là dùng lại token (dấu hiệu bị đánh cắp), và backend sẽ thu hồi toàn bộ phiên của user. Hãy dùng một lock hoặc một Future dùng chung trong interceptor.
- Số điện thoại nhận dạng `0xxxxxxxxx` hoặc `+84xxxxxxxxx` và được lưu dạng `0xxxxxxxxx`. Email được lưu dạng chữ thường.
