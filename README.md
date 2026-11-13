# Điểm danh sinh viên qua mã vạch

Ứng dụng Android Java/XML, package và applicationId `com.example.th9`. Dữ liệu lưu offline bằng Room; CameraX chọn camera sau và ML Kit đóng gói sẵn mô hình đọc mã vạch 1D.

Bản hiện tại có **Admin / Giảng viên**, Room **v5**, thời khóa biểu và đăng nhập cục bộ. Chi tiết phân quyền: [Bàn giao hệ thống 2 vai trò](HE_THONG_2_VAI_TRO.md); lịch học và migration v5: [Xếp lịch học](XEP_LICH_HOC.md).

## Chạy

Mở thư mục này trong Android Studio, đồng bộ Gradle, chọn cấu hình `app` và bấm **Run**. Giữ SDK/Gradle/JVM đã cấu hình trong project: compile/target SDK 37, min SDK 24; Gradle daemon dùng toolchain 25, mã nguồn Java 11. `local.properties` trỏ tới Android SDK trên máy và không đưa vào Git.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
# Kiểm thử emulator theo hướng dẫn trong HE_THONG_2_VAI_TRO.md.
# Dùng adb install -r và am instrument để giữ dữ liệu ứng dụng.
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Luồng sử dụng

**Quản lý giảng viên (Admin):** tìm theo họ tên/tài khoản, hỗ trợ tiếng Việt không dấu. Nút sắp xếp có tên A–Z/Z–A, tài khoản A–Z/Z–A và số lớp tăng/giảm. Mỗi thẻ hiển thị trạng thái, số lớp và nút phân công; menu ba chấm có sửa tên/đặt lại mật khẩu và khóa/mở khóa. Từ khóa và thứ tự được giữ khi xoay màn hình.

**Xóa sinh viên (Admin):** nhấn biểu tượng thùng rác cạnh sinh viên trong **Quản lý → Quản lý sinh viên**, hoặc mở hồ sơ và chọn **Xóa sinh viên**. Hộp thoại hiển thị họ tên/MSSV để xác nhận. Sau khi xóa, hồ sơ được lưu trữ, gỡ khỏi mọi lớp hiện tại và thẻ ngừng dùng để quét; lịch sử, snapshot điểm danh và bộ đếm MSSV được giữ nguyên.

0. Lần đầu sau khi nâng cấp, tự thiết lập tài khoản Admin (không có mật khẩu mặc định). Admin vào thẳng **Quản lý** sau đăng nhập, với hai mục **Quản lý / Lịch sử**; giao diện tạo buổi và điểm danh dành cho Giảng viên. Admin vào **Quản lý → Quản lý giảng viên** để tạo tài khoản và phân công lớp. Giảng viên đăng nhập sẽ có điều hướng **Điểm danh / Lớp được giao / Lịch sử**, chỉ xem dữ liệu lớp được phân công. Menu góc trên có **Đăng xuất**.
1. **Tạo lớp** trên Trang chủ hoặc **Quản lý → Quản lý lớp học**. Mã nhóm và ghi chú không bắt buộc.
2. Vào **Quản lý → Quản lý sinh viên → Thêm sinh viên**, nhập họ tên, ngày sinh, niên khóa, giới tính và chọn ảnh nếu có. Hệ thống tự cấp MSSV theo niên khóa. Có thể tạo sinh viên trực tiếp từ lớp; hồ sơ có sẵn được thêm vào nhiều lớp từ nút Thêm vào lớp đang quản lý trên hồ sơ. CSV cũ vẫn được hỗ trợ tại menu lớp.
3. Sau khi tạo thành công, popup hiển thị **MSSV của bạn là:** và thẻ có ảnh đã chọn. Barcode CODE_128 dùng chính MSSV. Hồ sơ luôn có thẻ tự động; không có thao tác gắn/đổi thẻ. Mã thẻ cũ vẫn nhận diện được để tương thích dữ liệu.
4. **Tạo buổi** lưu trạng thái chưa mở. Options Menu ở chi tiết buổi cho sửa trước khi mở. Khi mở, Room chốt danh sách và thông tin lớp; mỗi lớp và mỗi người phụ trách chỉ có tối đa một buổi đang mở. Các giảng viên khác nhau có thể mở buổi ở các lớp khác nhau.
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
- `app/schemas/…/1.json`: schema Room đầu tiên; `2.json` bổ sung hồ sơ/thẻ điện tử với migration 1→2. Không bật destructive migration; phải bổ sung migration và giữ schema cũ khi nâng phiên bản.

Dependency được chọn và build với project hiện tại: Room 2.8.4, CameraX 1.5.1, ML Kit bundled 17.3.0, Commons CSV 1.10.0. Tham khảo [Room](https://developer.android.com/jetpack/androidx/releases/room), [CameraX](https://developer.android.com/jetpack/androidx/releases/camera), [ML Kit bundled](https://developers.google.com/ml-kit/vision/barcode-scanning/android).

Xem kết quả kiểm tra và các ca cần thẻ thật trong `BAO_CAO.md`.

## Quản lý sinh viên và thẻ điện tử (schema 3)

**Quản lý → Quản lý sinh viên** để tạo/tìm/sửa hồ sơ. MSSV được hệ thống cấp theo niên khóa: `2020–2026` → `202020260001`, `202020260002`… Bộ đếm riêng cho từng niên khóa, lưu trong Room, giữ cả số của sinh viên đã lưu trữ; không cấp lại số đã dùng. Sửa niên khóa không đổi MSSV đã cấp. Mỗi niên khóa có tối đa 9.999 số theo định dạng bốn chữ số.

Form chỉ nhập họ tên, ngày sinh, niên khóa, giới tính và ảnh tùy chọn. Sau khi tạo có popup MSSV kèm thẻ thật. Trong hồ sơ, hai nút đầu là **Xem lớp đang học** và **Thêm vào lớp đang quản lý**; tiếp theo là thông tin, thẻ CODE_128 chứa MSSV và lịch sử điểm danh. Chạm thẻ hoặc Xem, lưu và chia sẻ thẻ để xuất PNG/chia sẻ/quét kiểm tra. Không có chức năng gắn/cấp/đổi mã thẻ thủ công.

Room migration 2→3 thêm giới tính và bảng bộ đếm; giữ đường nâng cấp 1→2→3, các định danh cũ, mã cũ, quan hệ và lịch sử. CSV theo mẫu cũ tiếp tục dùng MSSV đã có trong tệp; không đổi định dạng CSV. Xem [cập nhật MSSV tự động](CAP_NHAT_MSSV_TU_DONG.md). Báo cáo triển khai ban đầu và ghi chú sửa scanner được giữ tại [báo cáo thẻ sinh viên](BAO_CAO_THE_SINH_VIEN.md).

## Xếp lịch học / thời khóa biểu

Admin phân công giảng viên rồi vào **Thời khóa biểu → Thêm lịch học**. Giảng viên chọn một ngày học để mở/tiếp tục buổi điểm danh; buổi đã kết thúc chỉ mở kết quả. Lịch lặp được tính khi xem, không tự tạo điểm vắng. Room nâng từ v4 lên v5 bằng migration giữ dữ liệu.

Xem khảo sát, quy tắc, kiểm thử và hướng dẫn demo tại [XEP_LICH_HOC.md](XEP_LICH_HOC.md).
