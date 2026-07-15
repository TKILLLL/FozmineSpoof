package org.phantam.fozminesproofapi.action;

public interface IBotAction<T, R> {
    /**
     * Thực thi một hành động với tham số đầu vào và trả về kết quả
     * @param target Tên của bot hoặc dữ liệu cần xử lý
     * @return Kết quả thực thi hành động (Ví dụ: Boolean thành công/thất bại)
     */
    R execute(T target);
}
