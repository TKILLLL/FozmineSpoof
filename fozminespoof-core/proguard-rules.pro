# ==================================================================================== #
#                         PROGUARD RULES FOR FOZMINESPOOF                              #
# ==================================================================================== #

# Bỏ qua các cảnh báo không ảnh hưởng từ thư viện ngoài (BẮT BUỘC CHO MINECRAFT PLUGIN)
-ignorewarnings
-dontnote
-dontwarn **

# 1. Cấu hình cơ bản
-dontshrink                   # Không xóa các class chưa dùng
-dontoptimize                 # Tắt optimize bytecode để tránh lỗi JVM Minecraft
-allowaccessmodification      # Cho phép đổi public/protected để làm rối tốt hơn
-useuniqueclassmembernames    # Đảm bảo tên phương thức/biến sau làm rối là duy nhất
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions # Giữ lại Annotations (@EventHandler, @Override)

# ------------------------------------------------------------------------------------ #
# 2. GIỮ LẠI CÁC CLASS TRỌNG YẾU (ABSOLUTE KEEP RULES)                                #
# ------------------------------------------------------------------------------------ #

# A. Class Main chính của Plugin (Được gọi từ plugin.yml)
-keep class org.phantam.fozminespoofcore.FozmineSpoofCore { *; }

# B. Giữ lại toàn bộ Module API (để các plugin khác hook vào)
-keep class org.phantam.fozminespoofapi.** { *; }

# C. GIỮ LẠI TOÀN BỘ NMS BRIDGES (Rất quan trọng! NMSBridgeLoader gọi bằng Reflection)
-keep class org.phantam.fozminespoofv** { *; }
-keep class org.phantam.fozminespoofv**.** { *; }

# D. Giữ lại các Event Listener và Annotation @EventHandler của Bukkit
-keepclassmembers class * implements org.bukkit.event.Listener {
    @org.bukkit.event.EventHandler <methods>;
}

# E. Giữ lại các SubCommand và Command Executors
-keepclassmembers class * implements org.bukkit.command.CommandExecutor { *; }
-keepclassmembers class * implements org.bukkit.command.TabCompleter { *; }
-keep class org.phantam.fozminespoofcore.commands.subcommands.** { *; }

# F. Giữ lại các class Model/Data/Record (Tránh lỗi Jackson/Gson hoặc Database Mapping)
-keepclassmembers class * {
    *** get*();
    *** set*(***);
    *** is*();
}