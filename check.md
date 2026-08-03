# Kế Hoạch Sửa Lỗi Theo Nhóm

Dưới đây là danh sách các nhóm lỗi được phân loại theo mức độ ưu tiên, kèm theo các class cụ thể cần can thiệp và hướng
xử lý đề xuất.

---

## 🧩 Nhóm 1 – Lỗi Logic Nghiêm Trọng (Phải sửa ngay)

### ✅ 1. L‑01: Cache không đồng bộ sau `addBot`

**Vấn đề:**  
Trong `FakePlayerManager.addBot()`, sau khi gọi `addAction.execute()` (lưu DB bất đồng bộ), gọi ngay
`database.loadFakePlayer(name)` để update cache nhưng chưa chắc đã lưu xong → cache thiếu bot.

**Class cần sửa:** `FakePlayerManager.java`  
**Hướng xử lý:**

- Chuyển `addAction.execute()` thành đồng bộ (blocking) hoặc sử dụng callback.
- Đề xuất: Sửa `AddBotAction.execute()` để chạy đồng bộ trên main thread (vì thao tác này chỉ xảy ra khi admin dùng
  lệnh, không ảnh hưởng performance).
- Hoặc: Trong `addBot`, sau khi gọi `addAction.execute()`, thay vì load lại DB, tự tạo `FakePlayerData` và update cache
  trực tiếp.

**Sửa cụ thể:**

```java
// FakePlayerManager.addBot
public boolean addBot(String name, Location location) {
    // ...
    addAction.execute(new AddBotAction.Request(name, location));
    // Tạo data giống như AddBotAction đã tạo và update cache ngay
    UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    FakePlayerData data = new FakePlayerData.Builder()
            .name(name)
            .uuid(uuid)
            .world(plugin.getConfigManager().getBotWorldName())
            .location(0, 64, 0, 0, 0)
            .active(false)
            .build();
    updateCache(data);
    return true;
}
```

---

### ✅ 2. L‑02: Join/Quit message mặc định không bị chặn

**Vấn đề:**  
Trong `BotJoinQuitListener`, nếu config `join-leave-message-enable: false`, bot vẫn gửi message mặc định của Minecraft.

**Class cần sửa:** `BotJoinQuitListener.java`  
**Hướng xử lý:**

- Luôn set `event.setJoinMessage(null)` và `event.setQuitMessage(null)` cho bot, bất kể config.
- Nếu config bật, `FakePlayerBroadcaster` sẽ gửi message tùy chỉnh.

**Sửa cụ thể:**

```java
// BotJoinQuitListener.onPlayerJoin
if(isBot(player)){
        event.

setJoinMessage(null); // Luôn chặn message mặc định
    if(plugin.

getConfigManager().

isJoinLeaveMessageEnable()){
        // broadcaster sẽ gửi sau
        }
        // ...
        }

// onBotQuit tương tự
        event.

setQuitMessage(null);
```

---

## 🔒 Nhóm 2 – Bảo Mật

### 🔐 3. S‑01: API key lưu plaintext trong file cấu hình

**Vấn đề:**  
API key được lưu trực tiếp trong `ai-chat-bot.yml` dưới dạng văn bản thuần.

**Class cần sửa:** `AiConfig.java` và `CryptoUtils.java`  
**Hướng xử lý:**

- Sử dụng `CryptoUtils` (hiện tại dùng XOR yếu) để mã hóa API key trước khi lưu.
- Nâng cấp `CryptoUtils` lên AES với key được lấy từ biến môi trường hoặc file riêng (không hardcode).
- Đề xuất: Tạo một lớp `SecureConfig` để quản lý việc mã hóa/giải mã các giá trị nhạy cảm.

**Sửa cụ thể:**

```java
// AiConfig.reload()
String encryptedApiKey = config.getString("ai-settings.api-key", "");
apiKey =SecureConfig.

decrypt(encryptedApiKey); // nếu decrypt thất bại, coi như plaintext
// Khi lưu: SecureConfig.encrypt(apiKey)
```

---

### 🔐 4. S‑02: XOR mã hóa yếu trong `CryptoUtils`

**Vấn đề:**  
XOR với key hardcoded dễ bị giải mã.

**Class cần sửa:** `CryptoUtils.java`  
**Hướng xử lý:**

- Thay bằng AES-256 với key được tạo từ một chuỗi bí mật (có thể đọc từ env hoặc file ngoài).
- Nếu không thể lưu key an toàn, ít nhất nên sử dụng `Cipher` với chế độ phù hợp.

---

## ⚡ Nhóm 3 – Logic Trung Bình & Hiệu Năng

### 🔧 5. L‑03: Tham số `sendProxySyncData` không rõ ràng

**Vấn đề:**  
`IFakePlayerDatabase.sendProxySyncData(String bungeeName, String name, ...)` truyền
`name = configManager.getRawDatabaseName()` (tên bảng) nhưng `ProxySyncQuery` dùng `name` làm tên server node.

**Class cần sửa:** `IFakePlayerDatabase.java`, `ProxySyncQuery.java`, `ProxySyncSQLiteQuery.java`,
`DatabaseManager.java`, `SQLiteDatabaseManager.java`, `ProxySyncTask.java`  
**Hướng xử lý:**

- Đổi tên tham số `name` trong interface thành `serverNodeName` hoặc `bungeeServerName` để rõ nghĩa.
- Trong `ProxySyncTask`, truyền vào `configManager.getBungeeName()` (chính là tên server node).
- Cập nhật tất cả các implementation.

---

### 🌐 6. L‑04: `TranslatorService` phụ thuộc vào API không chính thức

**Vấn đề:**  
Sử dụng endpoint `translate.googleapis.com` không được hỗ trợ chính thức, có thể thay đổi bất cứ lúc nào.

**Class cần sửa:** `TranslatorService.java`  
**Hướng xử lý:**

- Thêm cấu hình để người dùng chọn dịch vụ: `"google"` (miễn phí nhưng không ổn định), `"gcloud"` (Google Cloud
  Translation API – có phí), `"deepl"`, hoặc `"none"` (tắt dịch).
- Nếu vẫn dùng endpoint miễn phí, nên có fallback khi thất bại.

---

### 💬 7. L‑05: `broadcastNMSChat` dùng `broadcastSystemMessage` không mô phỏng chat thực

**Vấn đề:**  
Tin nhắn hiển thị như hệ thống, không đi qua pipeline chat → các plugin chat không format được.

**Class cần sửa:** Tất cả NMSBridge (ví dụ: `NMSBridge_v1_19_4.java`).  
**Hướng xử lý:**

- Sử dụng `server.getPlayerList().broadcastChatMessage(...)` nếu có.
- Hoặc tạo một `AsyncPlayerChatEvent` giả và gọi `Bukkit.getPluginManager().callEvent(event)` để các plugin xử lý, sau
  đó broadcast kết quả.
- Đề xuất: Tạo một class `ChatBridge` chung để xử lý việc broadcast chat, giảm trùng lặp.

---

### 🛑 8. L‑06: Không kiểm tra `bridge` null trước khi dùng

**Vấn đề:**  
Một số nơi gọi `plugin.getBridge()` mà không kiểm tra null.

**Class cần sửa:** Các class sử dụng `bridge`: `SpawnBotAction`, `DespawnBotAction`, `KeepAliveTask`,
`FakePlayerManager` (despawnAllOnShutdown), ...  
**Hướng xử lý:**

- Kiểm tra `if (plugin.getBridge() == null) return` hoặc log lỗi trước khi gọi bất kỳ phương thức nào từ bridge.
- Đảm bảo các phương thức trong bridge không gây NPE nếu được gọi với null.

---

### 🕒 9. L‑07: `ProxySyncTask` không kiểm tra plugin enabled trong `reschedule`

**Vấn đề:**  
`reschedule()` có thể được gọi ngay cả khi plugin đã disabled.

**Class cần sửa:** `ProxySyncTask.java`  
**Hướng xử lý:**

- Thêm kiểm tra `if (!plugin.isEnabled()) return;` ở đầu `reschedule()`.
- Đã có trong `run()` nhưng nên thêm ở `reschedule()` để an toàn.

---

### 🚀 10. P‑01: Duyệt toàn bộ `botExpirationTime` mỗi tick

**Vấn đề:**  
`BotLifecycleManager` chạy mỗi tick và duyệt toàn bộ Map để kiểm tra bot hết hạn.

**Class cần sửa:** `BotLifecycleManager.java`  
**Hướng xử lý:**

- Sử dụng `DelayQueue` hoặc `PriorityQueue` để chỉ lấy bot sắp hết hạn.
- Hoặc: Tăng tần suất kiểm tra lên mỗi 5-10 tick (vẫn đủ chính xác với khoảng thời gian hàng nghìn giây).
- Đề xuất: Đổi sang `ScheduledExecutorService` với độ trễ tính bằng giây.

---

## 🧹 Nhóm 4 – Các Vấn Đề Khác

### 📦 11. M‑01: Trùng lặp code lớn giữa các NMSBridge

**Vấn đề:**  
Các class NMSBridge ở mỗi version gần như giống hệt nhau.

**Class cần sửa:** Tất cả các `NMSBridge_vX_Y_Z.java`  
**Hướng xử lý:**

- Tạo một abstract class `NMSBridgeBase` chứa các phương thức chung (không phụ thuộc version).
- Mỗi version chỉ override các phần khác biệt (ví dụ: tên packet, cách tạo cookie).
- Sử dụng reflection hoặc `CraftServer` để lấy server instance.

---

### 🎲 12. M‑02: Sử dụng `ThreadLocalRandom` không đồng nhất

**Vấn đề:**  
Có nhiều nơi gọi `ThreadLocalRandom.current()` mà không dùng chung một instance.

**Class cần sửa:** Tất cả class sử dụng `ThreadLocalRandom`.  
**Hướng xử lý:**

- Tạo một class `RandomProvider` với một static `ThreadLocalRandom` hoặc `SecureRandom`.
- Có thể dùng dependency injection để dễ test.

---

### 📝 13. M‑04: Tên biến không rõ ràng

**Vấn đề:**  
`name` trong `sendProxySyncData` không rõ nghĩa.

**Class cần sửa:** `IFakePlayerDatabase.java`, các implementation, và `ProxySyncTask.java`.  
**Hướng xử lý:**

- Đổi thành `serverNodeName` hoặc `bungeeServerName`.

---

## 📌 Lịch Trình Sửa Chữa

| Nhóm   | Thời gian dự kiến | Mức độ ưu tiên |
|--------|-------------------|----------------|
| Nhóm 1 | 2 giờ             | 🔴 Cao         |
| Nhóm 2 | 3 giờ             | 🔴 Cao         |
| Nhóm 3 | 4 giờ             | 🟡 Trung bình  |
| Nhóm 4 | 2 giờ             | 🟢 Thấp        |

**Tổng cộng:** ~11 giờ làm việc.

Sau khi sửa xong từng nhóm, cần kiểm thử kỹ lưỡng các chức năng liên quan để đảm bảo không phát sinh lỗi mới.