# Bàn giao ngày 06/11/2026

## Khảo sát và thay đổi

Project ban đầu có Trang chủ/Lớp học/Lịch sử trong `MainActivity`; các màn lớp, sinh viên, tạo/chi tiết/kết quả buổi, quét, CSV, thống kê, cài đặt và hai bottom sheet. Nguồn dữ liệu là map/list trong `MockDataManager`, tự nạp dữ liệu mẫu khi mở app. Camera, đèn pin, chọn/nhập/chia sẻ CSV và biểu đồ dùng callback hoặc dữ liệu mô phỏng.

Đã thay bằng Room, giữ Java/XML, AndroidX/Material, package/applicationId và điều hướng. Cài mới trống. Có tạo/sửa lớp, quản lý hồ sơ và thành viên nhiều lớp, xác nhận mã thẻ, buổi DRAFT/OPEN/CLOSED, danh sách chốt, điểm danh thủ công/quét chống trùng, chỉnh kết quả có lý do, lịch sử, thống kê, CSV và cài đặt lưu lâu dài. Options Menu, SubMenu sắp xếp, ContextMenu và Navigation bar được giữ. Màn Tạo buổi vẫn là XML; thẻ tổng kết vẫn tạo bằng Java.

## Build và kiểm thử

Lệnh đầy đủ đã **BUILD SUCCESSFUL**:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug
```

- **6/6 JVM tests**, **16/16 instrumentation tests**, không bỏ qua hoặc thất bại.
- Lint: **0 lỗi, 284 cảnh báo**; chủ yếu chuỗi giao diện hardcode, bản dịch, resource chưa dùng, style và gợi ý cập nhật dependency. Không tạo baseline hoặc tắt kiểm tra lỗi.
- Thiết bị: **Pixel_4 AVD, emulator-5554, Android 17 / API 37, x86_64, system image 16 KB**. Cấu hình rear và front đều Webcam0; app chỉ bind `DEFAULT_BACK_CAMERA`.
- Nhật ký cuối: [final-check.log](verification/final-check.log). Báo cáo [JVM](app/build/reports/tests/testDebugUnitTest/index.html), [instrumentation](app/build/reports/androidTests/connected/debug/index.html), [lint](app/build/reports/lint-results-debug.html).

| Nhóm | Đã xác minh |
|---|---|
| Nghiệp vụ Room | Lớp A 3 SV, lớp B 1 SV; quét giá trị hợp lệ, trùng, sai lớp và chưa đăng ký; thủ công SV thứ hai; đóng ra **2/3 đã điểm danh, 1 vắng**. Quét sau đóng bị từ chối. |
| Ràng buộc | Chặn mở hai lần, hai buổi mở, lớp trống; quét/thủ công đồng thời chỉ ghi một lần; gỡ thành viên/đổi hồ sơ và tên lớp không đổi snapshot. Chặn mã thẻ trùng MSSV hoặc thẻ của hồ sơ khác; phát hiện mã xung đột từ dữ liệu cũ. |
| Giờ và chỉnh kết quả | Trước mốc, trong toàn bộ phút của mốc và phút kế tiếp; tắt tính muộn; lý do bắt buộc; giữ giờ gốc khi đổi có mặt/muộn; từ vắng sang có mặt để trống giờ quét và ghi Chỉnh thủ công. |
| UI thật | Tạo lớp, thêm 3 SV, tạo/mở buổi, chọn SV thủ công, đóng, lọc kết quả, ContextMenu chỉnh có lý do, xem lịch sử, thống kê 66,7%, tạo lại Activity và thay đổi cài đặt. |
| CSV và chia sẻ | BOM, số 0 đầu, tiếng Việt, dấu phẩy/ngoặc kép/xuống dòng, thiếu header, trùng MSSV, xung đột thẻ, kiểm tra lại trước commit. Lưu tệp thật qua Android Documents rồi đọc lại nội dung; mở Sharesheet CSV; chọn tệp CSV thật, xem trước và nhập thành công. |
| Lưu trữ | Đóng/mở lại database trên đĩa vẫn nhận đúng buổi đang mở và MSSV có 0 đầu. Không dùng destructive migration. |

Kiểm tra trực tiếp bằng ADB sau đó cũng đã qua: **force-stop → mở lại app** vẫn hiện đúng buổi `OPEN` với **1/3** đã điểm danh; giao diện sáng, âm thanh tắt và rung tắt đều được khôi phục từ đĩa. Script đã trả cài đặt về giá trị trước kiểm tra. Bằng chứng: [active-cold-start.txt](verification/active-cold-start.txt), [settings-cold-start.txt](verification/settings-cold-start.txt), [script kiểm tra](verification/check-settings.ps1).

AVD hiện giữ các lớp kiểm thử và buổi “Phuc hoi sau khoi dong lai” đang mở để có thể tiếp tục thử. Đây là dữ liệu được tạo chủ động trong kiểm thử; ứng dụng cài mới vẫn không tự nạp dữ liệu mẫu.

## Camera: quan sát được và còn chờ

**Đã quan sát được ảnh webcam thật trong PreviewView**, camera sau streaming sau khi tạo lại và mở lại màn. Ca kiểm tra riêng đã xoay ngang/dọc và đợi Activity mới cùng preview streaming: [camera-rotation-test.log](verification/camera-rotation-test.log). ML Kit bundled đọc đúng `SV:00010001`, định dạng CODE_128 từ bitmap kiểm thử; đây là kiểm tra bộ giải mã, không phải quét thẻ vật lý.

**Chưa có thẻ vật lý trước webcam để xác minh**: đọc/gắn mã thẻ thật, giữ thẻ để kiểm tra callback liên tục rồi quét lại, nhiều thẻ cùng khung, và toàn bộ kịch bản camera A/B/không đăng ký. Cũng chưa quan sát đèn pin, âm thanh và rung trên điện thoại thật; UI chỉ bật đèn khi camera báo có flash. Các trạng thái quyền camera/lỗi/thử lại/cài đặt ứng dụng và điểm danh thủ công dự phòng đã triển khai; thao tác từ chối quyền vĩnh viễn chưa được kiểm thử tự động trên AVD này.

## File triển khai chính

- [AttendanceDatabase.java](app/src/main/java/com/example/th9/data/AttendanceDatabase.java), [schema 1](app/schemas/com.example.th9.data.AttendanceDatabase/1.json).
- [AttendanceRepository.java](app/src/main/java/com/example/th9/data/AttendanceRepository.java), [AttendanceRules.java](app/src/main/java/com/example/th9/data/AttendanceRules.java), [Tasks.java](app/src/main/java/com/example/th9/data/Tasks.java).
- [CameraScanner.java](app/src/main/java/com/example/th9/camera/CameraScanner.java), [ScanActivity.java](app/src/main/java/com/example/th9/ui/ScanActivity.java).
- [CsvTransfer.java](app/src/main/java/com/example/th9/data/CsvTransfer.java), [CsvImportPreviewActivity.java](app/src/main/java/com/example/th9/ui/CsvImportPreviewActivity.java), [ExportPreviewBottomSheetDialog.java](app/src/main/java/com/example/th9/ui/ExportPreviewBottomSheetDialog.java).
- [SessionFormActivity.java](app/src/main/java/com/example/th9/ui/SessionFormActivity.java), [XML Tạo buổi](app/src/main/res/layout/activity_session_form.xml), [SessionResultActivity.java](app/src/main/java/com/example/th9/ui/SessionResultActivity.java).
- [README](README.md) có hướng dẫn Run và ví dụ CSV. [APK debug](app/build/outputs/apk/debug/app-debug.apk).
