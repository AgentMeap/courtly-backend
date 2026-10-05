# Courtly Backend

Backend REST API cho **Courtly**, ứng dụng Flutter đặt sân cầu lông (môn PRM393). Repo hiện có **module xác thực**: đăng ký có xác minh email bằng mã OTP, đăng nhập bằng email hoặc số điện thoại, JWT kèm refresh token xoay vòng, phân quyền ADMIN/CUSTOMER. Đăng nhập Google qua Firebase mới có khung và đang tắt.

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
- Lỗi đăng nhập không cho biết email hay SĐT có tồn tại hay không.
- API không giữ session (stateless) và tắt CSRF. Lỗi 401/403 trả về dạng JSON.
- Mọi bí mật lấy từ biến môi trường hoặc `.env`, không có mật khẩu nào trong code.

## Biến môi trường chính

| Biến | Mục đích |
|---|---|
| `DB_PASSWORD` | Mật khẩu SQL Server (**bắt buộc**; thiếu thì app dừng ngay khi khởi động) |
| `DB_URL`, `DB_USERNAME` | Chuỗi kết nối và tài khoản DB (mặc định: localhost:1433, `sa`) |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Gmail và App Password dùng để gửi OTP |
| `JWT_SECRET` | Khóa ký JWT, ≥ 32 byte (**bắt buộc** khi `SPRING_PROFILES_ACTIVE=prod`) |
| `SPRING_PROFILES_ACTIVE` | Đặt `prod` khi deploy: bắt buộc `JWT_SECRET` và tắt tạo tài khoản mẫu |
| `SEED_ENABLED`, `SHOW_SQL` | Bật/tắt tài khoản mẫu và log SQL (mặc định `true`) |
| `FIREBASE_ENABLED`, `FIREBASE_*` | Bật đăng nhập Google, xem [FIREBASE_BACKEND_SETUP.md](docs/FIREBASE_BACKEND_SETUP.md) |

Danh sách đầy đủ có trong [docs/AUTH_BACKEND.md](docs/AUTH_BACKEND.md#2-biến-môi-trường).

## Test

```powershell
$env:DB_PASSWORD = "<mat khau sa>"
./mvnw test
```
- Unit test (JWT, refresh token, chuẩn hóa SĐT, dựng email) luôn chạy.
- Integration test chạy trên **SQL Server thật** (`CourtlyDB_Test`), không dùng H2 hay SQLite. Chúng tự bỏ qua nếu biến môi trường `DB_PASSWORD` chưa được đặt (giá trị trong `.env` không được tính). Test không gửi email thật.

## Cấu trúc thư mục

```
src/main/java/com/se183891/badminton_backend/
├── auth/       # Đăng ký, đăng nhập, OTP, refresh token, Google
├── user/       # Entity User, repository, /api/users/me
├── admin/      # Endpoint chỉ dành cho ADMIN
├── security/   # JWT, filter, xử lý 401/403
├── config/     # Security, CORS, OpenAPI, Firebase, tài khoản mẫu
└── common/     # Exception handler, định dạng lỗi chung
src/main/resources/
├── application.properties
└── db/migration/   # Script Flyway (V1 auth, V2 OTP)
docs/               # Hướng dẫn chi tiết và script SQL
```

## Tài liệu thêm
- [docs/AUTH_BACKEND.md](docs/AUTH_BACKEND.md): cấu hình, API, quy tắc OTP, lưu ý cho app Flutter
- [docs/FIREBASE_BACKEND_SETUP.md](docs/FIREBASE_BACKEND_SETUP.md): bật đăng nhập Google
- [docs/sql/create_database.sql](docs/sql/create_database.sql): tạo database
