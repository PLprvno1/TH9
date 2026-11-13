# Xếp lịch học và điểm danh theo lịch

## Khảo sát trước triển khai

- Ứng dụng Java/XML, AndroidX/Material, offline; `AttendanceRepository` là nguồn nghiệp vụ, Room chạy ngoài main thread qua `Tasks.IO`.
- Phiên bản trước thay đổi: **Room v4**, schema đã xuất tại `app/schemas/com.example.th9.data.AttendanceDatabase/4.json`. Có migration 1→2→3→4, không dùng destructive fallback.
- Các bảng có sẵn: Classroom, Student, ClassMembership, AttendanceSession, AttendanceRecord, StudentNumberSequence, UserAccount, TeacherClass, LocalLogin.
- Đã có Admin/Giảng viên, phân công lớp, chặn thu hồi phân công/khóa tài khoản khi giảng viên có buổi OPEN; giảng viên chỉ xem lớp được giao và chỉ thao tác buổi mình phụ trách.
- Luồng điểm danh có sẵn: DRAFT→OPEN→CLOSED; snapshot danh sách sinh viên và thông tin lớp/giảng viên khi mở; mỗi lớp và mỗi giảng viên tối đa một OPEN; CameraX/ML Kit CODE_128, điểm danh thủ công, chống quét trùng, mốc đi muộn và đánh dấu vắng khi kết thúc.
- Tận dụng các màn chi tiết lớp, hồ sơ sinh viên, chi tiết/kết quả buổi, CSV, thống kê và navigation hiện tại. Giữ các thay đổi đã tồn tại trong workspace.

## Phần triển khai

- **Cá nhân** ở thanh điều hướng dưới cho cả Admin/Giảng viên, hiển thị họ tên, tên đăng nhập và vai trò; có nút Lịch sử điểm danh, Cài đặt, Đăng xuất. Lịch sử không còn là mục riêng ở thanh dưới, dùng lại giao diện/bộ lọc/phân quyền cũ; quay lại trở về Cá nhân. Trạng thái Cá nhân/lịch sử và bộ lọc được giữ qua xoay màn hình; đường mở lịch sử bằng intent cũ vẫn hoạt động. Đăng xuất có xác nhận, xóa phiên đăng nhập và ngăn quay lại màn tài khoản; giữ nguyên dữ liệu và buổi OPEN. [Ảnh trang Cá nhân](verification/personal-page.png).

- **Thời khóa biểu** trên thanh điều hướng cho cả hai vai trò; xem tuần trước/sau, về tuần hiện tại, trạng thái hôm nay/sắp tới/đã qua, trạng thái buổi đã mở và thông báo khi trống. Các ngày học được tính lúc xem, không tạo sẵn buổi hay điểm vắng.
- Admin có chế độ **Quản lý lịch**, xem cả các quy tắc ngoài tuần đang chọn, lọc lớp và giảng viên; có thể truy cập lịch từ chi tiết lớp.
- **Chi tiết lớp của Admin**: nút Quản lý lịch học mở thẳng form thêm/sửa lịch, cố định lớp đang xem. Ngay bên dưới là Phân công giảng viên: popup bo góc, tìm theo tên/tài khoản (hỗ trợ tìm không dấu), giữ các lựa chọn khi lọc và xoay màn hình, xác nhận Lưu hoặc Hủy. Lưu phân công theo lớp trong một transaction, giữ phân công ở các lớp khác, kiểm tra lịch trùng, tài khoản khóa và buổi OPEN. Nút Thêm sinh viên cạnh quản lý lịch đã ẩn; thao tác thêm vẫn có ở trạng thái lớp trống và menu.
- **Thêm sinh viên từ lớp**: mở trang chọn hồ sơ sinh viên hiện có trong hệ thống, có checkbox, tìm tên/MSSV và số lượng đã chọn. Lựa chọn giữ qua tìm kiếm/xoay màn hình; người đã có trong lớp được đánh dấu sẵn và không thêm trùng. Có thể chọn sinh viên đang thuộc lớp khác; giữ nguyên MSSV/hồ sơ/lớp chính đã có. Thêm hàng loạt trong một transaction, không sửa snapshot các buổi OPEN. Quay lại khi chưa bấm Thêm không ghi dữ liệu.
- **Nhập CSV sinh viên** chỉ có lối vào ở Quản lý sinh viên, nhập hồ sơ vào hệ thống và chưa tự gán lớp. Sau đó dùng trang chọn sinh viên để đưa vào lớp. Đã gỡ nút/menu nhập CSV khỏi chi tiết lớp. Giữ định dạng CSV, xem trước, xác nhận và kiểm tra trùng/xung đột; API nhập theo lớp cũ vẫn giữ để tương thích, luồng giao diện dùng nhập toàn hệ thống.
- **Đề xuất trên trang chủ giảng viên**: hiển thị lớp được phân công có lịch đúng ngày hiện tại và đang trong khoảng `[giờ bắt đầu, giờ kết thúc)`, kèm ngày/ca/giờ và sĩ số. Nút **Tiến hành điểm danh** yêu cầu xác nhận rồi mở buổi theo lịch; buổi OPEN có nút tiếp tục, buổi CLOSED không được đề xuất lại. Nếu đang mở một buổi khác thì chặn bắt đầu buổi mới và giải thích. Khi có đề xuất, thay thẻ trống bằng các lớp đang đến giờ, vẫn giữ lối tạo buổi ngoài lịch. Làm mới ở ranh giới mỗi phút khi màn hình hoạt động và ngay khi quay lại, không tạo dữ liệu chỉ vì thời gian đã đến. [Ảnh UI với dữ liệu test](verification/home-suggestions.png).
- **Thêm/sửa lịch**: lớp, ca, DatePicker, TimePicker, chọn nhiều thứ bằng chip; giữ lựa chọn khi xoay màn hình; lỗi hiển thị trên form. Mỗi lớp chỉ có **một lịch đang hoạt động**. Form thêm chỉ liệt kê lớp chưa có lịch. Nếu lớp đã có lịch khi lưu, phải xác nhận ghi đè; hệ thống cập nhật ID lịch cũ thay vì tạo lịch thứ hai. Khi cập nhật lớp có nhiều lịch từ phiên bản cũ, các lịch còn lại được lưu trữ sau xác nhận, giữ nguyên buổi và lịch sử liên quan.
- **Chi tiết lịch**: khoảng hiệu lực, ngày học được chọn, ca/giờ, sĩ số, liên kết xem lớp và danh sách sinh viên. Admin sửa/lưu trữ; giảng viên mở/tiếp tục điểm danh hoặc xem kết quả CLOSED.
- **ClassSchedule** lưu một quy tắc với bitmask thứ 2→CN, ngày ISO `yyyy-MM-dd`, giờ tính bằng phút và cờ lưu trữ. Ca Sáng kết thúc không quá 12:00; ca Chiều bắt đầu từ 12:00. Không hỗ trợ lịch qua đêm.
- Kiểm tra ít nhất một ngày học thực tế trong khoảng hiệu lực, thứ hợp lệ, ngày/giờ đúng thứ tự, giờ phù hợp ca. Hai lịch xung đột khi cùng có một ngày học thực tế và khoảng giờ giao nhau; giờ kết thúc bằng giờ bắt đầu được chấp nhận.
- Chặn trùng lịch cùng lớp hoặc cùng bất kỳ giảng viên được phân công. Cả hai API phân công đơn lẻ/thay toàn bộ đều kiểm tra lịch trong transaction trước khi lưu, lỗi làm rollback toàn bộ thay đổi.
- **Room v5**, migration 4→5 tạo bảng lịch và thêm `scheduleId`, `plannedDate`, `plannedShift`, `plannedStartMinute`, `plannedEndMinute` vào AttendanceSession. Các buổi cũ/ngoài lịch có liên kết NULL. Schema v5 được xuất cùng v1–v4.
- FK lịch→lớp, buổi→lịch; unique index `(scheduleId, plannedDate)` bảo vệ chống trùng. Mở buổi gồm kiểm quyền, tìm buổi hiện có, kiểm giới hạn OPEN và snapshot trong **cùng một transaction**. Thất bại không để lại DRAFT/record thừa.
- Snapshot ngày/ca/giờ dự kiến và giảng viên thực hiện không thay đổi khi sửa/lưu trữ lịch. Xóa lịch dùng lưu trữ, vẫn xem buổi cũ trong Lịch sử. Không tự tạo buổi thay thế khi CLOSED.
- Buổi mới từ lịch: mốc đi muộn sau giờ dự kiến 15 phút, tối đa đến giờ kết thúc. Khi mở sớm hoặc sau khung giờ có thông báo và xác nhận; không chặn theo đồng hồ. Nếu mở sau mốc, lượt điểm danh có thể là đi muộn ngay.
- Buổi ngoài lịch: form hiện có thêm xác nhận rõ trước khi tạo; liên kết lịch NULL và nhãn phân biệt hiển thị ở danh sách, chi tiết, kết quả. Các định dạng CSV hiện có được giữ nguyên.
- Khi bị thu hồi phân công, giảng viên không còn xem/mở/điểm danh lớp đó; nếu đang OPEN thì phải kết thúc trước khi thu hồi, nhất quán với nghiệp vụ cũ.

## Kiểm thử

Cập nhật Cá nhân và điều hướng lịch sử: **build thành công, 34 unit test và 4/4 kiểm thử emulator đạt**. Đã kiểm tra thông tin tài khoản của cả hai vai trò; lịch sử đúng phạm vi lớp, bộ lọc và khôi phục khi xoay màn hình; đường mở lịch sử cũ; mở/quay lại Cài đặt; Hủy/đồng ý đăng xuất, xóa back stack nhưng giữ OPEN/snapshot; hồi quy trang quản trị và đề xuất điểm danh. [Log build](verification/personal-build.log), [log emulator](verification/personal-tests.log).

Cập nhật chọn sinh viên có sẵn và chuyển CSV về Quản lý sinh viên: **build thành công, 34 unit test và 6/6 kiểm thử emulator đạt**. Đã kiểm tra chọn nhiều sinh viên, tìm tên/MSSV, giữ lựa chọn khi xoay màn hình, quay lại không ghi, đánh dấu người đã thuộc lớp, giữ hồ sơ/lớp chính, thêm nguyên tử/chống trùng, snapshot OPEN không đổi, nhập CSV toàn hệ thống không tạo membership, lỗi CSV không ghi một phần và phân quyền. [Log build](verification/student-selection-build.log), [log emulator](verification/student-selection-tests.log), [ảnh trang chọn](verification/class-student-selection.png).

Cập nhật thao tác trong Chi tiết lớp: **build thành công, 34 unit test và 5/5 kiểm thử emulator đạt**. Bao gồm form thêm/sửa mở trực tiếp và khóa đúng lớp; popup tìm không dấu, giữ lựa chọn qua tìm kiếm/xoay màn hình, lưu/Hủy; phân công theo lớp cập nhật nguyên tử, chặn lịch trùng/tài khoản khóa/thu hồi khi OPEN; hồi quy phân quyền và form thêm lịch chung. [Log build](verification/class-actions-build.log), [log emulator](verification/class-actions-tests.log), [ảnh popup bằng dữ liệu test](verification/class-teachers-popup.png).

Cập nhật đề xuất trang chủ giảng viên: **build thành công, 34/34 unit test và 4/4 kiểm thử emulator đạt**. Đã kiểm tra biên giờ bắt đầu/kết thúc, đúng ngày/thứ, lớp được phân công, không tự tạo dữ liệu, loại buổi CLOSED, xác nhận/Hủy mở buổi, tiếp tục không tạo trùng và hồi quy điều hướng Admin/Giảng viên. Dùng database test riêng, không đổi đồng hồ hay dữ liệu thiết bị. [Log build](verification/home-suggestions-build.log), [log emulator](verification/home-suggestions-tests.log).

Cập nhật quy tắc mỗi lớp một lịch: build và 33 unit test đạt. Sáu ca emulator liên quan đã đạt: chặn lịch thứ hai dù khác giờ; ghi đè giữ ID/lịch sử; lưu trữ lịch trùng cũ và rollback khi xung đột; hồi quy snapshot; thêm/sửa/lưu trữ bằng UI; lọc lớp chưa có lịch, Hủy/xác nhận ghi đè khi form cũ và thêm lại sau lưu trữ. Năm ca đạt trong [lần chạy đầu](verification/single-schedule-tests.log); ca UI cuối đạt sau khi sửa thao tác test để cuộn nút Lưu vào vùng nhìn thấy: [chạy lại](verification/single-schedule-ui-recheck.log). [Log build](verification/single-schedule-build.log). Không đổi schema hay xóa lịch sử; lịch trùng đã tồn tại chỉ được lưu trữ khi Admin xác nhận cập nhật.

Đã chạy trên emulator **Pixel_4, API 37**, ngày 12/11/2026:

- Build debug và build bộ kiểm thử thành công; **33/33 unit test đạt**.
- Bộ lịch học mới: **12/12 kiểm thử emulator đạt** (10 nghiệp vụ/migration, 2 giao diện).
- Hồi quy: **19/19 đạt** (11 repository/CSV/snapshot, 6 phân quyền/migration/MSSV, 2 điều hướng/camera).
- Kiểm tra lại trên bản cuối: **1/1 ca UI đạt**, xác nhận sau khi đổi giờ lịch, buổi CLOSED vẫn hiển thị giờ học và mốc đi muộn gốc: [log](verification/schedule-final-ui.log).
- Lint thành công, 0 lỗi; vẫn có cảnh báo của dự án về chuỗi hiển thị, API deprecated, layout và các đề xuất cải thiện.
- Log: [build/test/Lint](verification/schedule-build.log), [12 ca lịch học](verification/schedule-instrumentation.log), [19 ca hồi quy](verification/schedule-regression.log).
- Ảnh demo dùng dữ liệu test riêng: [thời khóa biểu](verification/schedule-week.png), [chi tiết quản lý lịch](verification/schedule-admin-detail.png).

- `ScheduleRulesTest`: lịch lặp qua năm mới, Chủ nhật/năm nhuận, không có ngày thực tế, ngày sai, thứ/ca/giờ không hợp lệ, giao ngày thực tế và khoảng giờ chỉ chạm nhau.
- `ScheduleInstrumentedTest`: CRUD/lưu trữ, trùng lớp/giảng viên, rollback phân công, phân quyền đọc/ghi, tạo/tiếp tục/CLOSED, snapshot sau sửa/xóa, giới hạn OPEN, ngoài lịch, hai luồng mở đồng thời, migration 4→5 và 1→5 trên database riêng.
- `ScheduleUiTest`: điều hướng, DatePicker/TimePicker, form sau xoay màn hình, thêm/sửa/lưu trữ bằng UI, thời khóa biểu giảng viên, mở buổi/xem kết quả và chặn form quản trị.
- **Database thực trên emulator v4→v5 đã đối chiếu đạt**: 2 lớp, 10 sinh viên, 2 membership, 5 buổi, 3 record, 2 sequence, 2 tài khoản, 1 phân công, 1 phiên đăng nhập; mọi giá trị ở các cột cũ giữ nguyên. `integrity_check` và `foreign_key_check` đều đạt. Log: [bảo toàn dữ liệu](verification/schedule-data-preservation.log). Script tái kiểm tra: `verification/check-schedule-data.py`. Các bản sao chứa dữ liệu cá nhân nằm trong thư mục đã gitignore, không đưa vào bàn giao.

Phạm vi chưa kiểm thử: máy Android thật và việc đưa thẻ giấy/thẻ trên thiết bị khác trước camera thực tế; các API Android khác ngoài API 37, giao diện đêm và cấu hình chữ rất lớn. CameraX preview, tạo lại màn hình scanner, callback CODE_128 và luồng điểm danh đã được kiểm thử trên emulator. Không chạy lại toàn bộ các bộ UI cũ không liên quan tới thay đổi này.

## Hiển thị giảng viên đã phân công

Chi tiết lớp của Admin hiển thị tên và tài khoản giảng viên đã chọn ngay dưới nút Phân công giảng viên. Tên cập nhật ngay sau khi lưu, giữ nguyên sau khi mở lại màn hình; khi không còn phân công sẽ hiện “Chưa phân công giảng viên”.

Lần kiểm tra hiển thị tên trước khi giới hạn một giảng viên/lớp: build thành công và kiểm thử giao diện trên emulator đạt **1/1**. [Log build](verification/assigned-teacher-names-build.log), [log kiểm thử](verification/assigned-teacher-names-tests.log).

Quy tắc hiện tại: mỗi lớp tối đa một giảng viên; một giảng viên vẫn có thể phụ trách nhiều lớp không trùng lịch. Popup dùng lựa chọn đơn, giữ lựa chọn qua tìm kiếm/xoay màn hình, chọn người khác để thay thế và có nút Bỏ phân công. Repository chặn danh sách nhiều giảng viên và chặn thêm người thứ hai từ các đường phân công khác. Trang quản lý giảng viên chỉ cho chọn lớp chưa phân công hoặc lớp người đó đang phụ trách. Việc đổi người phụ trách vẫn kiểm tra lịch trùng và không cho thu hồi người đang có buổi OPEN. Với dữ liệu cũ có nhiều người, popup yêu cầu chọn lại một người; không tự ý xóa phân công khi mở màn hình.

Kiểm chứng giới hạn một giảng viên/lớp: build thành công, **34/34 unit test đạt**. Bộ emulator chạy 27 ca, 26 ca đạt ngay; một ca lỗi do dữ liệu kiểm thử đã bỏ phân công trước khi sửa lịch. Đã sửa dữ liệu mẫu và chạy lại thành công **2/2 ca** về xung đột lịch/phân công, thay thế nguyên tử và bảo vệ buổi OPEN. Giao diện lựa chọn đơn, cập nhật tên, Hủy, bỏ phân công, xoay màn hình, quản lý giảng viên và hồi quy phân quyền đều đạt. [Log build](verification/single-teacher-build.log), [log emulator](verification/single-teacher-tests.log), [build lại bộ test](verification/single-teacher-test-build.log), [log chạy lại](verification/single-teacher-recheck.log), [ảnh popup](verification/class-teachers-popup.png).

## Demo

Đã xác minh lại luồng tạo lớp: trong mã hiện tại, lưu form tạo lớp mở `ClassDetailActivity` với ID lớp vừa tạo rồi đóng form. Không tự mở màn hình xếp lịch; Admin bấm Quản lý lịch học trong chi tiết lớp để mở form lịch. Đã build/cài lại APK trên emulator và mở rộng ca giao diện sẵn có để kiểm tra tạo lớp qua form → đúng tên lớp trong chi tiết → form tạo đã đóng → chưa phát sinh lịch; sau đó kiểm tra nút quản lý lịch mở form đúng lớp. **1/1 ca đạt**, dùng database in-memory. [Log build](verification/class-create-navigation-build.log), [log kiểm thử](verification/class-create-navigation-tests.log).

1. Đăng nhập Admin, tạo lớp và sinh viên bằng chức năng hiện có; vào Quản lý → Giảng viên → Phân công lớp.
2. Vào Thời khóa biểu → Thêm lịch học (hoặc Chi tiết lớp → Quản lý lịch học). Chọn lớp, ca Sáng, khoảng ngày chứa tuần demo, T2/T4/T6 và 07:00–10:00; lưu.
3. Xem theo tuần, thử lọc lớp/giảng viên; chuyển Quản lý lịch để sửa một quy tắc bất kể tuần hiện tại. Thử tạo lịch chồng giờ để xem lỗi.
4. Đăng xuất rồi đăng nhập giảng viên đã phân công. Vào Thời khóa biểu, chọn ngày học → Bắt đầu điểm danh → xác nhận. Nếu chọn ngày quá khứ/tương lai vẫn có thể mở và nhận thông báo thời điểm tương ứng.
5. Từ chi tiết buổi, quét thẻ CODE_128 chứa MSSV hoặc điểm danh thủ công, thử quét trùng; kết thúc buổi và xem kết quả/CSV.
6. Quay lại lịch cùng ngày để xem kết quả; không phát sinh buổi mới. Đổi giờ hoặc lưu trữ lịch bằng Admin rồi xem Lịch sử: thông tin buổi và snapshot vẫn giữ nguyên.
7. Nếu cần học bù/demo ngoài lịch: vào lớp với vai trò giảng viên → Tạo buổi ngoài lịch → xác nhận → mở buổi bằng luồng hiện có.

## Lưu ý môi trường build

Máy có JRE của extension VS Code trùng tiêu chí Java 25 nhưng thiếu `jlink`. Lệnh kiểm tra dùng JDK đầy đủ sẵn có và không sửa cấu hình JDK của dự án:

```powershell
$env:JAVA_HOME='C:\Users\holon\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --no-daemon --no-configuration-cache --console=plain
```
