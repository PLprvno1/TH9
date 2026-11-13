# Cập nhật quản lý sinh viên — MSSV và thẻ tự động

## Luồng sử dụng

Quản lý → Quản lý sinh viên → Thêm sinh viên. Nhập họ tên, ngày sinh, niên khóa (ví dụ 2020–2026), giới tính Nam/Nữ/Khác; ảnh không bắt buộc. Không nhập MSSV, mã thẻ hoặc chọn lớp ở form này. Tạo từ màn lớp sẽ thêm vào lớp đó trong cùng transaction.

Sau khi lưu, popup “MSSV của bạn là:” hiển thị mã vừa cấp và ảnh toàn bộ thẻ có avatar nếu đã chọn. Nút Xem hồ sơ mở hồ sơ vừa tạo; Đóng quay về danh sách. Popup và tiến trình lưu/nhập ảnh được giữ qua việc tạo lại Activity bằng DialogFragment/ViewModel. UUID tạo hồ sơ được giữ để thử lại không tạo thêm hồ sơ khi giao diện bị tạo lại.

Hồ sơ có hai nút đầu trang: Xem lớp đang học và Thêm vào lớp đang quản lý. Chọn lớp đang học để mở chi tiết lớp; thêm lớp không thay đổi quan hệ với các lớp khác. Khi thêm vào lớp lần đầu, lớp đó trở thành lớp hiển thị trên thẻ. Phần tiếp theo hiển thị họ tên, MSSV, ngày sinh, niên khóa, giới tính, trường và lớp; bên dưới là thẻ sinh viên thật. Giữ khu vực lịch sử điểm danh cũ. Không còn hình barcode minh họa, nút/ContextMenu/chế độ scanner gắn thẻ hoặc thao tác cấp mã thủ công.

## Quy tắc MSSV

- Prefix: năm bắt đầu và năm kết thúc niên khóa ghép lại, ví dụ 2020–2026 → 20202026.
- Mỗi niên khóa có bộ đếm riêng: 0001, 0002…9999; ví dụ MSSV đầu tiên là **202020260001**.
- Bảng StudentNumberSequence lưu số cuối đã cấp trong Room. Bộ đếm, hồ sơ và thành viên lớp (nếu tạo từ lớp) được ghi trong cùng transaction. Ghi lỗi thì rollback toàn bộ.
- Khi khởi tạo/đọc bộ đếm, xét cả MSSV/mã cũ và sinh viên lưu trữ để không dùng lại định danh đã tồn tại. Không dùng COUNT(*) để cấp số; xóa sinh viên không làm số giảm.
- Không đổi MSSV đã cấp khi sửa tên, niên khóa, giới tính, ảnh hoặc lớp. Hết 9.999 số trong một niên khóa sẽ thông báo lỗi rõ ràng, không cấp trùng hoặc tự đổi định dạng.
- Thẻ mới vẽ barcode **CODE_128 chứa đúng MSSV**. Ảnh thẻ dùng cùng hàm vẽ cho popup, hồ sơ, màn xem và PNG. Avatar được đọc từ tệp riêng của app, không giữ URI ảnh tạm.

## Dữ liệu và tương thích

Room **version 3**, MIGRATION_2_3 thêm Student.gender (nullable) và StudentNumberSequence. MIGRATION_1_2 vẫn được đăng ký để giữ đường nâng cấp 1→2→3. Có schema 3.json được Room processor xuất khi biên dịch.

Không đổi MSSV/ID/mã thẻ của hồ sơ cũ, không đổi snapshot và không xóa attendance. Mã điện tử TDMU1- và mã vật lý cũ vẫn được scanner tra cứu; giao diện thẻ hiện vẽ MSSV nên không cần cấp hay gắn mã. Hồ sơ cũ thiếu giới tính hiển thị Chưa cập nhật. CSV cũ vẫn nhập/xuất theo định dạng hiện có, không tự đổi MSSV từ CSV thành mã khác.

Các API dữ liệu cũ được giữ cho tương thích; giao diện tạo mới sử dụng registerStudent, không dùng API nhập MSSV thủ công. Scanner chỉ còn điểm danh và quét kiểm tra. Giữ bản sửa lifecycle camera ở đợt trước.

## Kiểm tra trong lần cập nhật này

- **BUILD PASS:** assembleDebug và assembleDebugAndroidTest. [Log biên dịch](verification/student-registration-build.log).
- **NOT RUN:** unit/instrumentation/UI/camera và migration thực thi trên emulator, theo yêu cầu người dùng tự kiểm tra. Biên dịch test APK không phải chạy kiểm thử.
- Không cài APK, mở camera, xóa dữ liệu hoặc thay đổi database emulator. Các kết quả PASS trong báo cáo cũ thuộc phiên bản trước, không dùng để kết luận bản này đã được kiểm thử thực tế.

## File chính

AttendanceDatabase/AttendanceRepository (migration, bộ đếm, transaction), Student/ProfileRules, StudentFormActivity/StudentSaveViewModel, StudentCreatedDialog, StudentProfileActivity, StudentCardView/StudentCardActivity, ScanActivity, StudentAdapter và ClassDetailActivity. XML form/hồ sơ/popup/scanner được cập nhật tương ứng.
