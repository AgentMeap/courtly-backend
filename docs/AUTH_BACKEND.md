# Module xác thực Courtly: hướng dẫn chạy

## 1. Chuẩn bị SQL Server

1. Bật SQL Server và mở TCP port 1433 (SQL Server Configuration Manager > Protocols > TCP/IP > IPAll > TCP Port = 1433).
2. Tạo database:
   ```powershell
   sqlcmd -S localhost -U sa -P "<mat khau>" -C -i docs/sql/create_database.sql
   ```
   Lệnh này tạo `CourtlyDB` (để chạy app) và `CourtlyDB_Test` (cho integration test). **Bảng do Flyway tạo** khi app khởi động (`db/migration/V1__create_auth_tables.sql`, `V2__create_email_otps.sql`, `V3__allow_reset_password_otp.sql`).

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
| `MAIL_USERNAME` | **Có để đăng ký / quên mật khẩu được** (thiếu thì gửi OTP trả 503) | không có |
| `MAIL_PASSWORD` | **Có để đăng ký được**: App Password 16 ký tự của Gmail | không có |
| `MAIL_FROM` | không | bằng `MAIL_USERNAME` |
| `MAIL_HOST` / `MAIL_PORT` | không | `smtp.gmail.com` / `587` |
| `FIREBASE_*` | không | xem `docs/FIREBASE_BACKEND_SETUP.md` |
| `GOOGLE_MOCK_ENABLED` | không. **Chỉ dev**, xem [Google mock cho dev](#google-mock-cho-dev) | `false` |

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

- Unit test (JWT, hash refresh token, chuẩn hóa SĐT, nội dung email, Google mock, kiểm tra cấu hình lúc khởi động) luôn chạy.
- Integration test chạy trên **SQL Server thật** (`CourtlyDB_Test`) và **tự bỏ qua (skipped)** khi không có `DB_PASSWORD`. Mỗi test xóa sạch dữ liệu các bảng trong `CourtlyDB_Test`. Test **không gửi email thật**: `OtpMailSender` được mock và test đọc mã OTP từ mock.
- Integration test gồm `AuthFlowIntegrationTest` (đăng ký, đăng nhập, refresh), `PasswordResetIntegrationTest` (quên mật khẩu) và `GoogleMockLoginIntegrationTest` (Google mock).
- Nếu SQL Server chạy bằng Docker (`docker compose up -d sqlserver db-init`, cổng máy `14330`), đặt thêm `$env:TEST_DB_URL = "jdbc:sqlserver://localhost:14330;databaseName=CourtlyDB_Test;encrypt=true;trustServerCertificate=true"`.

## 5. API

| Method | Path | Auth | Kết quả |
|---|---|---|---|
| POST | `/api/auth/register/send-otp` | không | 200 `{ message, expiresIn, resendAfter }` (giây). 409 nếu email đã dùng, 429 nếu gửi quá nhanh hoặc quá nhiều, 503 nếu chưa cấu hình mail hoặc gửi lỗi |
| POST | `/api/auth/register` | không | 201 AuthResponse. Body `{ fullName, email, phone?, password, otp }`. 400 nếu OTP sai hoặc hết hạn |
| POST | `/api/auth/login` | không | 200 AuthResponse (`identifier` = email hoặc SĐT) |
| POST | `/api/auth/password/forgot` | không | 200 `{ message, expiresIn, resendAfter }` **luôn giống nhau** dù email có tồn tại hay không. Body `{ email }`. 429 nếu gửi quá nhanh hoặc quá nhiều, 503 nếu chưa cấu hình mail |
| POST | `/api/auth/password/reset` | không | 200 `{ "message": "Đặt lại mật khẩu thành công" }`. Body `{ email, otp, newPassword }`. 400 với một thông báo chung nếu mã sai, hết hạn hoặc email không hợp lệ; 400 + `fieldErrors.newPassword` nếu mật khẩu yếu |
| POST | `/api/auth/google` | không | 200 AuthResponse / 401 token sai / 409 (mock) email thuộc tài khoản khác / 503 khi Google đang tắt |
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

### Quên mật khẩu bằng OTP

1. Màn hình "Quên mật khẩu" gọi `POST /api/auth/password/forgot` `{ "email": "..." }`. Phản hồi **luôn là 200** với cùng message *"Nếu email đã đăng ký, mã OTP đặt lại mật khẩu đã được gửi tới email đó"* cùng `expiresIn` và `resendAfter`, để kẻ xấu không dò được email nào đã đăng ký.
2. Người dùng nhập mã và mật khẩu mới. App gọi `POST /api/auth/password/reset` `{ "email": "...", "otp": "123456", "newPassword": "..." }`.
3. Thành công trả 200 `{ "message": "Đặt lại mật khẩu thành công" }` và **không trả token**: app chuyển về màn hình đăng nhập.

Quy tắc:

- Mã chỉ thực sự được tạo và gửi khi email thuộc tài khoản **LOCAL, đang hoạt động và có mật khẩu**. Email không tồn tại hoặc tài khoản chỉ dùng Google thì không gửi gì và không tạo bản ghi, nhưng phản hồi vẫn y hệt.
- Thời hạn mã, thời gian chờ gửi lại, số lần gửi mỗi giờ và giới hạn **5 lần nhập sai** giống hệt OTP đăng ký (bảng `email_otps`, `purpose = RESET_PASSWORD`). Mã đăng ký không dùng được để đặt lại mật khẩu và ngược lại.
- Mật khẩu mới theo **đúng quy tắc đăng ký**: 8 đến 72 ký tự, có ít nhất 1 chữ hoa và 1 chữ số, không có khoảng trắng đầu/cuối, tối đa 72 byte (BCrypt). Nếu mật khẩu không hợp lệ thì mã OTP **không bị tính lượt**.
- Mọi lỗi về mã (sai, hết hạn, đã dùng, hết lượt) và email không hợp lệ đều trả **cùng một lỗi** 400 *"Mã OTP không đúng hoặc đã hết hạn"*. Vì vậy app không hiển thị được số lượt còn lại; sau vài lần sai hãy gợi ý người dùng gửi lại mã.
- Khi đặt lại thành công, trong **một transaction**: mã bị đánh dấu đã dùng, `password_hash` và `updated_at` được cập nhật, và **toàn bộ refresh token** của user bị thu hồi. Mọi thiết bị đang đăng nhập sẽ nhận 401 ở lần refresh tiếp theo và phải đăng nhập lại.
- Email đặt lại mật khẩu có tiêu đề riêng *"Mã đặt lại mật khẩu Courtly"* và nhắc *"nếu không phải bạn yêu cầu, hãy bỏ qua email này"*. Mã OTP không bao giờ được ghi ra log.

> Đánh đổi đã biết: lỗi 429 (gửi quá nhanh) chỉ xảy ra với email có thật, vì giới hạn gửi được tính theo bản ghi OTP. Đây là lựa chọn có chủ ý, để chống spam hộp thư của người dùng thật.

Ví dụ:

```bash
curl -X POST http://localhost:8080/api/auth/password/forgot \
  -H "Content-Type: application/json" -d '{"email":"customer@courtly.vn"}'

curl -X POST http://localhost:8080/api/auth/password/reset \
  -H "Content-Type: application/json" \
  -d '{"email":"customer@courtly.vn","otp":"123456","newPassword":"MatKhauMoi1"}'
```

### Google mock cho dev

Khi chưa có Firebase, app Flutter ở chế độ mock Google có thể gửi **token giả** lên `POST /api/auth/google` để demo luồng đăng nhập Google.

Bật bằng `GOOGLE_MOCK_ENABLED=true` (property `app.google.mock-enabled`, mặc định `false`). Khi bật, log khởi động có cảnh báo `MOCK GOOGLE LOGIN ĐANG BẬT - CHỈ DÙNG CHO DEV`.

Định dạng token: `mock:<email>:<tên hiển thị>`

- Token được tách tối đa 3 phần theo dấu `:`, nên tên hiển thị được phép chứa dấu hai chấm. Tên có thể bỏ trống (`mock:<email>`), khi đó dùng phần trước `@` của email.
- Token sai định dạng (không bắt đầu bằng `mock:`, email không hợp lệ) trả **401**.
- Ví dụ:
  ```bash
  curl -X POST http://localhost:8080/api/auth/google \
    -H "Content-Type: application/json" \
    -d '{"idToken":"mock:demo.user@example.com:Người Dùng Demo"}'
  ```

Chọn bộ kiểm tra token:

| `FIREBASE_ENABLED` | `GOOGLE_MOCK_ENABLED` | Kết quả |
|---|---|---|
| `false` | `false` | `/api/auth/google` trả 503 (mặc định) |
| `true` | `false` | Firebase thật |
| `false` | `true` | Google mock |
| `true` | `true` | **App không khởi động** (cấu hình mơ hồ) |

Quy tắc an toàn:

- ⚠️ **Không bao giờ bật mock ở production.** App **từ chối khởi động** nếu `GOOGLE_MOCK_ENABLED=true` cùng `SPRING_PROFILES_ACTIVE=prod`.
- Danh tính mock có `firebase_uid = mock:<email>`. Mock chỉ tạo hoặc đăng nhập tài khoản **CUSTOMER, auth_provider GOOGLE** do chính mock tạo.
- Mock **không bao giờ** đăng nhập vào hay liên kết với tài khoản có sẵn khác: nếu email đã thuộc tài khoản LOCAL, tài khoản ADMIN hoặc tài khoản Google thật thì trả **409** *"Email đã được dùng bởi tài khoản khác"*.
- Luồng Firebase thật giữ nguyên hành vi, kể cả việc liên kết theo email với tài khoản LOCAL (vì Firebase đã xác minh email).

### Lưu ý cho app Flutter

- `expiresIn` là số giây (1800). Khi access token hết hạn, API trả 401 với message "Phiên đăng nhập đã hết hạn". Lúc đó gọi `/refresh` rồi **lưu refresh token MỚI**, vì token cũ đã bị thu hồi.
- **Đừng gọi `/refresh` song song với cùng một refresh token**. Request thứ hai sẽ bị coi là dùng lại token (dấu hiệu bị đánh cắp), và backend sẽ thu hồi toàn bộ phiên của user. Hãy dùng một lock hoặc một Future dùng chung trong interceptor.
- Số điện thoại nhận dạng `0xxxxxxxxx` hoặc `+84xxxxxxxxx` và được lưu dạng `0xxxxxxxxx`. Email được lưu dạng chữ thường.
- Sau khi đặt lại mật khẩu, refresh token đang lưu trong app không còn dùng được (401): hãy xóa token và chuyển về màn hình đăng nhập.
- Màn hình quên mật khẩu nên dùng `resendAfter` cho nút "Gửi lại mã", giống màn hình đăng ký.
