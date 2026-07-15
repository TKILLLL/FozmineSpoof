package org.phantam.fozminesproofcore.chat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TranslatorService {

    // Regex chuẩn để bóc tách chính xác phần tử chữ đã dịch đầu tiên từ mảng JSON phức hợp của Google Script
    private static final Pattern JSON_TEXT_PATTERN = Pattern.compile("^\\[\\[\\[\"([^\"]+)\"");

    /**
     * Dịch thuật văn bản tự động thông qua Google Translate API tự do (Async Ready).
     * Hàm này được thiết kế để chạy an toàn trên luồng bất đồng bộ (Async Thread).
     *
     * @param text Văn bản gốc (tiếng Anh) cần dịch.
     * @param targetLang Ngôn ngữ đích (Ví dụ: "vi", "ja", "none").
     * @return Văn bản đã dịch hoặc văn bản gốc nếu xảy ra lỗi/gặp cấu hình "none".
     */
    public String translate(String text, String targetLang) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        // Nếu cấu hình ngôn ngữ là "none" hoặc trống, giữ nguyên tiếng Anh gốc của file
        if (targetLang == null || targetLang.trim().equalsIgnoreCase("none")) {
            return text;
        }

        try {
            // VÁ LỖI CẤU TRÚC: Điền đầy đủ Endpoint gtx tự do của Google Translate Script
            String urlStr = "https://googleapis.com"
                    + targetLang
                    + "&dt=t&q="
                    + URLEncoder.encode(text, StandardCharsets.UTF_8);

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            // Đặt thời gian chờ phản hồi ngắn để tránh treo hàng đợi (Stuck Queue) nếu API Google bị bóp băng thông
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);

            // Nếu Google từ chối kết nối (Không trả về mã thành công HTTP 200 OK), kích hoạt Fallback ngay
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return text;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                Matcher matcher = JSON_TEXT_PATTERN.matcher(response.toString());
                if (matcher.find()) {
                    // Trích xuất nhóm khớp số 1 và giải mã các ký tự đặc biệt/Unicode
                    return decodeUnicode(matcher.group(1));
                }
            }
        } catch (Exception e) {
            // Khối phòng vệ Fail-Safe: Tự động giữ nguyên chữ gốc nếu mất mạng hoặc API lỗi để luồng chat không bị đứng
            return text;
        }
        return text;
    }

    /**
     * Giải mã chuỗi chứa mã Unicode và làm sạch các ký tự escaping đặc biệt của JSON.
     * Tối ưu hóa hiệu năng bằng cách nhảy cóc chỉ mục index, giảm tải việc cấp phát vùng nhớ rác cho RAM.
     */
    private String decodeUnicode(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }

        // Sửa lỗi hiển thị: Tự động chuyển các ký tự escape dấu nháy của JSON về dạng ký tự thường
        str = str.replace("\\\"", "\"").replace("\\\\", "\\");

        if (!str.contains("\\u")) {
            return str;
        }

        StringBuilder sb = new StringBuilder(str.length());
        int len = str.length();
        int i = 0;

        while (i < len) {
            char ch = str.charAt(i);

            // Phát hiện mã Unicode hợp lệ
            if (ch == '\\' && i + 1 < len && str.charAt(i + 1) == 'u' && i + 5 < len) {
                try {
                    String unicodeHex = str.substring(i + 2, i + 6);
                    int codePoint = Integer.parseInt(unicodeHex, 16);
                    sb.append((char) codePoint);
                    i += 6; // Nhảy cóc qua 6 ký tự
                    continue;
                } catch (NumberFormatException e) {
                    // Nếu lỗi định dạng hex (Ví dụ chuỗi chứa "" lỗi), giữ nguyên chuỗi thô để xử lý tiếp
                    sb.append(ch);
                }
            } else {
                sb.append(ch);
            }
            i++;
        }
        return sb.toString();
    }
}
