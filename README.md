# Điểm danh sinh viên qua mã vạch

Ứng dụng Android Java/XML, package và applicationId `com.example.th9`. Dữ liệu lưu offline bằng Room; CameraX chọn camera sau và ML Kit đóng gói sẵn mô hình đọc mã vạch 1D.

## Chạy

Mở thư mục này trong Android Studio, đồng bộ Gradle, chọn cấu hình `app` và bấm **Run**. Giữ SDK/Gradle/JVM đã cấu hình trong project: compile/target SDK 37, min SDK 24; Gradle daemon dùng toolchain 25, mã nguồn Java 11. `local.properties` trỏ tới Android SDK trên máy và không đưa vào Git.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Luồng sử dụng

1. **Tạo lớp** trên Trang chủ hoặc tab Lớp học. Mã nhóm và ghi chú không bắt buộc.
2. Mở lớp, **Thêm sinh viên** hoặc vào Options Menu → Nhập CSV. MSSV giữ nguyên số 0 đầu. Hồ sơ đã tồn tại có thể được thêm vào nhiều lớp.
3. Từ hồ sơ, **Gắn/Đổi mã thẻ** mở camera. Kiểm tra chuỗi và định dạng, xác nhận trên form rồi bấm Lưu. Mã chưa biết cấu trúc được giữ nguyên, không tự cắt `SV:`.
4. **Tạo buổi** lưu trạng thái chưa mở. Options Menu ở chi tiết buổi cho sửa trước khi mở. Khi mở, Room chốt danh sách và thông tin lớp; toàn app chỉ có một buổi đang mở.
5. Quét thẻ hoặc chọn sinh viên trong danh sách điểm danh thủ công. Có mặt/Đi muộn được tính tự động theo phút. Nếu không có quyền camera, vẫn dùng điểm danh thủ công.
6. **Kết thúc buổi** chuyển người chưa điểm danh thành Vắng. Kết quả có thẻ tổng kết tạo bằng Java, tìm kiếm, chip lọc; nhấn giữ sinh viên để chỉnh kết quả với lý do bắt buộc.
7. Nút xuất ở Kết quả mở bản xem trước đầy đủ, lưu CSV qua Android Documents hoặc chia sẻ CSV/văn bản qua Sharesheet. Tab Lịch sử dùng lớp thật; Options Menu có bộ lọc thời gian/trạng thái. Thống kê chỉ tính các buổi đã kết thúc.

Cài mới không tự tạo dữ liệu mẫu. Các bài kiểm thử UI tạo lớp tên `Kiem thu UI …` và `Kiem thu CSV …` trong môi trường kiểm thử. Không dùng bộ kiểm thử UI trên dữ liệu quan trọng: Gradle có thể gỡ ứng dụng kiểm thử và ứng dụng đích sau khi chạy.

## CSV nhập

CSV UTF-8, có hoặc không có BOM. Header phân biệt chính xác tên cột:

```csv
mssv,ho_ten,ma_the
00010001,"Nguyễn, Văn An",SV:00010001
00010002,Trần Thị Bình,
```

`ma_the` là tùy chọn. Trường có dấu phẩy, dấu ngoặc kép hoặc xuống dòng được đọc/ghi theo RFC 4180 bằng Apache Commons CSV. Bản xem trước không ghi dữ liệu. Tất cả dòng phải hợp lệ, sau đó kiểm tra lại và nhập trong một transaction; hồ sơ hiện tại không bị đổi tên/thẻ bởi CSV. Không có thao tác nhập một phần tệp lỗi.

## Cấu trúc

- `data/AttendanceDatabase.java`: năm bảng, khóa ngoại, khóa ghép, index và DAO.
- `data/AttendanceRepository.java`: transaction, quy tắc chuyển trạng thái, nhận diện và chốt lịch sử; UI đọc các model tách rời từ snapshot Room.
- `data/Tasks.java`, `ui/DataActivity.java`: đọc/ghi ngoài main thread và khởi tạo sau process recreation.
- `data/AttendanceRules.java`, `data/CsvTransfer.java`: tính đi muộn, CSV nhập/xuất.
- `camera/CameraScanner.java`: CameraX/ML Kit, đóng ImageProxy trên mọi nhánh và quản lý lifecycle.
- `ui/…`: các Activity/dialog đã có, tích hợp với nguồn dữ liệu thật.
- `app/schemas/…/1.json`: schema Room đầu tiên. Không bật destructive migration; phải bổ sung migration và giữ schema cũ khi nâng phiên bản.

Dependency được chọn và build với project hiện tại: Room 2.8.4, CameraX 1.5.1, ML Kit bundled 17.3.0, Commons CSV 1.10.0. Tham khảo [Room](https://developer.android.com/jetpack/androidx/releases/room), [CameraX](https://developer.android.com/jetpack/androidx/releases/camera), [ML Kit bundled](https://developers.google.com/ml-kit/vision/barcode-scanning/android).

Xem kết quả kiểm tra và các ca cần thẻ thật trong `BAO_CAO.md`.
