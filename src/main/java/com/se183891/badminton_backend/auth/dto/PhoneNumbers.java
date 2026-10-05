package com.se183891.badminton_backend.auth.dto;

/**
 * Chuan hoa so dien thoai Viet Nam ve dang 0xxxxxxxxx de luu va tim kiem nhat quan.
 */
public final class PhoneNumbers {

    /**
     * Dang nhap vao: 0xxxxxxxxx hoac +84xxxxxxxxx, cho phep dau cach, cham, gach giua cac so.
     * Chuoi rong duoc chap nhan va coi nhu khong nhap.
     */
    public static final String INPUT_REGEX = "^\\s*$|^\\s*(\\+84|0)([\\s.-]?\\d){9}\\s*$";

    private PhoneNumbers() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[\\s.-]", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (digits.startsWith("+84")) {
            digits = "0" + digits.substring(3);
        }
        return digits;
    }
}
