# Courtly Backend

Backend REST API cho **Courtly**, ứng dụng Flutter đặt sân cầu lông (môn PRM393). Repo hiện có **module xác thực**: đăng ký có xác minh email bằng mã OTP, **quên mật khẩu bằng OTP email**, đăng nhập bằng email hoặc số điện thoại, JWT kèm refresh token xoay vòng, phân quyền ADMIN/CUSTOMER. Đăng nhập Google qua Firebase mặc định tắt; khi dev có thể bật **Google mock** để demo mà chưa cần Firebase.

## Công nghệ

| | |
|---|---|
| Ngôn ngữ | Java 21 |
| Framework | Spring Boot 4.1 (Web MVC, Security, Data JPA, Validation, Mail) |
| Cơ sở dữ liệu | Microsoft SQL Server, schema quản lý bằng **Flyway** |
| Xác thực | JWT HS256 (jjwt 0.12), BCrypt, Firebase Admin SDK (tùy chọn) |
| Tài liệu API | SpringDoc OpenAPI / Swagger UI |
| Build | Maven Wrapper (`./mvnw`) |

## Bắt đầu nhanh

### 1. Yêu cầu
- JDK 21 trở lên
- SQL Server 2019 trở lên, bật TCP/IP ở port 1433
- Một tài khoản Gmail có **App Password** để gửi OTP (xem [hướng dẫn](docs/AUTH_BACKEND.md#cấu-hình-gmail-để-gửi-otp))

### 2. Tạo database
```powershell
sqlcmd -S localhost -U sa -P "<mat khau>" -C -i docs/sql/create_database.sql
```
Lệnh này tạo `CourtlyDB` (để chạy app) và `CourtlyDB_Test` (cho integration test). Các bảng sẽ do Flyway tự tạo khi app khởi động.

### 3. Cấu hình bí mật
```powershell
copy .env.example .env
```
Mở `.env` rồi điền `DB_PASSWORD`, `MAIL_USERNAME` và `MAIL_PASSWORD`. File `.env` đã nằm trong `.gitignore`, **không bao giờ commit file này**. Nếu cùng một biến được đặt cả trong `.env` lẫn trong biến môi trường thật (IntelliJ, PowerShell) thì app dùng giá trị của biến môi trường thật.

### 4. Chạy
```powershell
./mvnw spring-boot:run
```
- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html. Muốn gọi API cần đăng nhập thì bấm **Authorize** và dán access token.
- Từ máy ảo Android, gọi API qua `http://10.0.2.2:8080`.

Lần chạy đầu, app tự tạo hai tài khoản mẫu, mật khẩu đều là `123456`:

| Email | SĐT | Role |
|---|---|---|
| `admin@courtly.vn` | `0900000001` | ADMIN |
| `customer@courtly.vn` | `0900000002` | CUSTOMER |

## API

Mọi đường dẫn bắt đầu bằng `/api`, dữ liệu JSON dùng camelCase.

| Method | Path | Cần đăng nhập | Mô tả |
|---|---|---|---|
| POST | `/api/auth/register/send-otp` | không | Gửi mã OTP 6 số tới email |
| POST | `/api/auth/register` | không | Đăng ký (cần `otp`), trả 201 + token |
| POST | `/api/auth/login` | không | Đăng nhập bằng email **hoặc** SĐT |
| POST | `/api/auth/password/forgot` | không | Quên mật khẩu: gửi OTP nếu email đã đăng ký (luôn trả 200) |
| POST | `/api/auth/password/reset` | không | Đặt mật khẩu mới bằng OTP, đăng xuất mọi thiết bị |
| POST | `/api/auth/google` | không | Đăng nhập Google (trả 503 khi tính năng đang tắt) |
| POST | `/api/auth/refresh` | không | Đổi refresh token lấy cặp token mới |
| POST | `/api/auth/logout` | không | Thu hồi refresh token (trả 204) |
| GET | `/api/users/me` | Bearer | Thông tin user hiện tại |
| GET | `/api/admin/ping` | Bearer + ADMIN | Kiểm tra quyền admin |

Luồng đăng ký:

```
App                         Backend                       Gmail
 |-- POST send-otp {email} -->|-- gửi mã 6 số ------------->|
 |<-- 200 {expiresIn,resendAfter}                           |
 |   (người dùng nhập mã)     |                             |
 |-- POST register {...,otp} ->|
 |<-- 201 {accessToken, refreshToken, user}
```

Luồng quên mật khẩu:

```
App                                   Backend                         Gmail
 |-- POST password/forgot {email} ---->|-- (chỉ khi email có thật) ---->|
 |<-- 200 {message chung, expiresIn, resendAfter}                     |
 |-- POST password/reset {email, otp, newPassword} -->|
 |<-- 200 {message}   (mọi refresh token cũ bị thu hồi, phải đăng nhập lại)
```

Mọi lỗi trả về cùng một dạng JSON, thông báo bằng tiếng Việt:
```json
{ "timestamp": "...", "status": 400, "error": "Bad Request",
  "message": "Dữ liệu không hợp lệ", "path": "/api/auth/register",
  "fieldErrors": { "password": "Mật khẩu phải có ít nhất 1 chữ hoa, 1 chữ số ..." } }
```

Chi tiết từng request/response và các quy tắc OTP (thời hạn, giới hạn gửi lại, số lần nhập sai) có trong [docs/AUTH_BACKEND.md](docs/AUTH_BACKEND.md).

## Bảo mật

- Mật khẩu được băm bằng BCrypt. Mã OTP cũng chỉ lưu dưới dạng BCrypt.
- Access token JWT sống 30 phút. Refresh token sống 7 ngày, DB chỉ lưu SHA-256 của token.
- Mỗi lần refresh, token cũ bị thu hồi. Nếu một token đã thu hồi bị dùng lại, mọi phiên của user đó bị thu hồi.
- Lỗi đăng nhập không cho biết email hay SĐT có tồn tại hay không. Quên mật khẩu cũng vậy: `forgot` luôn trả cùng một phản hồi, `reset` trả cùng một lỗi cho mọi trường hợp mã sai, hết hạn hoặc email không tồn tại.
- Đặt lại mật khẩu thành công sẽ thu hồi toàn bộ refresh token của user (đăng xuất mọi thiết bị).
- Google mock chỉ dành cho dev: app **không khởi động** nếu bật mock cùng profile `prod` hoặc cùng Firebase, và mock không bao giờ đăng nhập được vào tài khoản có sẵn (LOCAL, ADMIN, Google thật).
- API không giữ session (stateless) và tắt CSRF. Lỗi 401/403 trả về dạng JSON.
- Mọi bí mật lấy từ biến môi trường hoặc `.env`, không có mật khẩu nào trong code.

## Biến môi trường chính

| Biến | Mục đích |
|---|---|
| `DB_PASSWORD` | Mật khẩu SQL Server (**bắt buộc**; thiếu thì app dừng ngay khi khởi động) |
| `DB_URL`, `DB_USERNAME` | Chuỗi kết nối và tài khoản DB (mặc định: localhost:1433, `sa`) |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Gmail và App Password dùng để gửi OTP (đăng ký và quên mật khẩu) |
| `MAIL_FROM` | Địa chỉ người gửi (mặc định bằng `MAIL_USERNAME`) |
| `JWT_SECRET` | Khóa ký JWT, ≥ 32 byte (**bắt buộc** khi `SPRING_PROFILES_ACTIVE=prod`) |
| `SPRING_PROFILES_ACTIVE` | Đặt `prod` khi deploy: bắt buộc `JWT_SECRET` và tắt tạo tài khoản mẫu |
| `SEED_ENABLED`, `SHOW_SQL` | Bật/tắt tài khoản mẫu và log SQL (mặc định `true`) |
| `FIREBASE_ENABLED`, `FIREBASE_*` | Bật đăng nhập Google, xem [FIREBASE_BACKEND_SETUP.md](docs/FIREBASE_BACKEND_SETUP.md) |
| `GOOGLE_MOCK_ENABLED` | `true` = Google mock **chỉ cho dev** (mặc định `false`), xem [Google mock](docs/AUTH_BACKEND.md#google-mock-cho-dev) |

Danh sách đầy đủ có trong [docs/AUTH_BACKEND.md](docs/AUTH_BACKEND.md#2-biến-môi-trường).

## Chạy bằng Docker

```powershell
copy .env.example .env      # điền MSSQL_SA_PASSWORD, JWT_SECRET, MAIL_USERNAME, MAIL_PASSWORD
docker compose up -d --build
```
Compose chạy SQL Server (cổng máy `14330`), tạo database rồi chạy app ở http://localhost:8080. Chỉ các biến được liệt kê trong `docker-compose.yml` mới được truyền vào container.

**Bật Google thật (Firebase) trong Docker**
1. Tải file service account của project Firebase (xem [FIREBASE_BACKEND_SETUP.md](docs/FIREBASE_BACKEND_SETUP.md)), đặt tại `./secrets/service-account.json`. Thư mục `secrets/` đã nằm trong `.gitignore` và `.dockerignore`: **không commit và không build file khóa vào image**. Compose mount thư mục này chỉ đọc vào `/run/secrets/firebase`.
2. Trong `.env`:
   ```
   FIREBASE_ENABLED=true
   FIREBASE_PROJECT_ID=caulong-9b67e
   FIREBASE_SERVICE_ACCOUNT_PATH=/run/secrets/firebase/service-account.json
   GOOGLE_MOCK_ENABLED=false
   ```
   `caulong-9b67e` là project của app Flutter hiện tại; project ID phải **trùng với `google-services.json` của app**, nếu không mọi token đều bị từ chối (401).
3. `docker compose up -d --build app`, log phải có dòng `Firebase Admin SDK da khoi tao cho project caulong-9b67e`.

**Bật Google mock (chỉ dev/demo)**: đặt `GOOGLE_MOCK_ENABLED=true` (và `FIREBASE_ENABLED=false`) rồi `docker compose up -d app`. Log sẽ có cảnh báo `MOCK GOOGLE LOGIN ĐANG BẬT - CHỈ DÙNG CHO DEV`.

> ⚠️ **Không bao giờ bật `GOOGLE_MOCK_ENABLED` ở production.** Với mock, ai cũng tự tạo được tài khoản cho bất kỳ email nào chưa đăng ký. App tự dừng nếu bật mock cùng `SPRING_PROFILES_ACTIVE=prod`.

## Test

```powershell
$env:DB_PASSWORD = "<mat khau sa>"
./mvnw test
```
- Unit test (JWT, refresh token, chuẩn hóa SĐT, dựng email, Google mock, kiểm tra cấu hình khi khởi động) luôn chạy.
- Integration test (đăng ký, đăng nhập, quên mật khẩu, Google mock) chạy trên **SQL Server thật** (`CourtlyDB_Test`), không dùng H2 hay SQLite. Nếu dùng SQL Server trong Docker thì đặt thêm `$env:TEST_DB_URL = "jdbc:sqlserver://localhost:14330;databaseName=CourtlyDB_Test;encrypt=true;trustServerCertificate=true"`. Chúng tự bỏ qua nếu biến môi trường `DB_PASSWORD` chưa được đặt (giá trị trong `.env` không được tính). Test không gửi email thật.

## Cấu trúc thư mục

```
src/main/java/com/se183891/badminton_backend/
├── auth/       # Đăng ký, đăng nhập, quên mật khẩu, OTP, refresh token, Google (Firebase / mock)
├── user/       # Entity User, repository, /api/users/me
├── admin/      # Endpoint chỉ dành cho ADMIN
├── security/   # JWT, filter, xử lý 401/403
├── config/     # Security, CORS, OpenAPI, Firebase, tài khoản mẫu
└── common/     # Exception handler, định dạng lỗi chung
src/main/resources/
├── application.properties
└── db/migration/   # Script Flyway (V1 auth, V2 OTP, V3 OTP đặt lại mật khẩu)
docs/               # Hướng dẫn chi tiết và script SQL
```

## Tài liệu thêm
- [docs/AUTH_BACKEND.md](docs/AUTH_BACKEND.md): cấu hình, API, quy tắc OTP, quên mật khẩu, Google mock, lưu ý cho app Flutter
- [docs/FIREBASE_BACKEND_SETUP.md](docs/FIREBASE_BACKEND_SETUP.md): bật đăng nhập Google
- [docs/sql/create_database.sql](docs/sql/create_database.sql): tạo database
