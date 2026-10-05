package com.se183891.badminton_backend.common.exception;

/**
 * Cac thong bao loi dung chung (tieng Viet) de client hien thi truc tiep.
 */
public final class ErrorMessages {

    public static final String BAD_CREDENTIALS = "Email/số điện thoại hoặc mật khẩu không đúng";
    public static final String ACCOUNT_DISABLED = "Tài khoản đã bị vô hiệu hóa";
    public static final String EMAIL_TAKEN = "Email đã được sử dụng";
    public static final String PHONE_TAKEN = "Số điện thoại đã được sử dụng";
    public static final String DUPLICATE_DATA = "Dữ liệu đã tồn tại";
    public static final String PASSWORD_TOO_LONG = "Mật khẩu quá dài (tối đa 72 byte)";
    public static final String VALIDATION_FAILED = "Dữ liệu không hợp lệ";
    public static final String MALFORMED_BODY = "Nội dung yêu cầu không đúng định dạng JSON";
    public static final String UNAUTHENTICATED = "Bạn cần đăng nhập để thực hiện thao tác này";
    public static final String TOKEN_EXPIRED = "Phiên đăng nhập đã hết hạn";
    public static final String TOKEN_INVALID = "Token không hợp lệ";
    public static final String REFRESH_TOKEN_INVALID = "Refresh token không hợp lệ";
    public static final String REFRESH_TOKEN_EXPIRED = "Refresh token đã hết hạn, vui lòng đăng nhập lại";
    public static final String REFRESH_TOKEN_REUSED = "Phát hiện refresh token bị dùng lại, vui lòng đăng nhập lại";
    public static final String FORBIDDEN = "Bạn không có quyền truy cập tài nguyên này";
    public static final String NOT_FOUND = "Không tìm thấy tài nguyên";
    public static final String METHOD_NOT_ALLOWED = "Phương thức HTTP không được hỗ trợ";
    public static final String GOOGLE_DISABLED = "Đăng nhập Google chưa được cấu hình";
    public static final String GOOGLE_TOKEN_INVALID = "Google ID token không hợp lệ hoặc đã hết hạn";
    public static final String GOOGLE_EMAIL_UNVERIFIED = "Email Google chưa được xác minh";
    public static final String OTP_SENT = "Đã gửi mã OTP tới %s";
    public static final String OTP_NOT_FOUND = "Mã OTP không hợp lệ, vui lòng yêu cầu mã mới";
    public static final String OTP_EXPIRED = "Mã OTP đã hết hạn, vui lòng yêu cầu mã mới";
    public static final String OTP_WRONG = "Mã OTP không đúng, bạn còn %d lần thử";
    public static final String OTP_TOO_MANY_ATTEMPTS = "Bạn đã nhập sai quá nhiều lần, vui lòng yêu cầu mã mới";
    public static final String OTP_RESEND_WAIT = "Vui lòng đợi %d giây trước khi yêu cầu mã mới";
    public static final String OTP_TOO_MANY_SENDS = "Bạn đã yêu cầu quá nhiều mã OTP, vui lòng thử lại sau 1 giờ";
    public static final String MAIL_DISABLED = "Chức năng gửi email chưa được cấu hình";
    public static final String MAIL_SEND_FAILED = "Không gửi được email, vui lòng thử lại sau";
    public static final String INTERNAL_ERROR = "Đã xảy ra lỗi hệ thống, vui lòng thử lại sau";

    private ErrorMessages() {
    }
}
