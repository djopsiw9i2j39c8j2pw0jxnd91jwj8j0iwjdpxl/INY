# INY
Ứng dụng Android độc lập để xem video công khai. Tìm kiếm, gợi ý từ khóa và tab Shorts của kênh dùng [NewPipe Extractor v0.27.1](https://github.com/TeamNewPipe/NewPipeExtractor/releases/tag/v0.27.1). Shorts không phải bảng đề xuất cá nhân của YouTube. Nút cài đặt của trình phát gốc có chất lượng (nếu nguồn cung cấp) và tốc độ; trình phát nhúng tự điều khiển khi được dùng dự phòng.

## Build

Dùng JDK 17 trở lên và Android SDK 35. Chạy `./gradlew assembleDebug lintDebug`. Trong GitHub Actions, dùng keystore cố định cho bản release để chữ ký và SHA-1 không thay đổi mỗi lần phát hành. Không commit keystore hoặc thông tin xác thực.

## Đăng nhập với Google

1. Trên APK đã cài, mở **Bạn → Cài đặt & cập nhật** và sao chép SHA-1.
2. Trong Google Cloud, bật **YouTube Data API v3**, cấu hình màn hình đồng ý OAuth và tạo **Android OAuth client** với package `io.github.iny.app` và đúng SHA-1 vừa sao chép.
3. Nếu màn hình đồng ý đặt ở chế độ **External / Testing**, thêm tài khoản Google vào **Test users**. Quay lại app, mở ảnh đại diện và chọn **Đăng nhập với Google**.

Mỗi khóa ký (debug, release, Play App Signing) có thể có SHA-1 riêng. Xem video công khai không cần đăng nhập; đăng nhập dùng cho thích/không thích, đăng ký kênh, bình luận và playlist. Tải lên xin quyền bổ sung khi bắt đầu thao tác. [Hướng dẫn cấu hình OAuth Android](https://developer.android.com/identity/authorization).
