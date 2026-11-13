# Hệ thống offline Admin / Giảng viên — bàn giao 10/11/2026

## Khảo sát trước khi sửa

Source hiện có Java/XML, Room v3, `MIGRATION_1_2` và `MIGRATION_2_3`, repository giao dịch, snapshot danh sách khi mở buổi, CameraX + ML Kit bundled, CSV, lịch sử và thống kê. Chưa có tài khoản, phiên đăng nhập hay phân công; kiểm tra OPEN trước đây áp dụng toàn ứng dụng. Cơ chế `StudentNumberSequence`, popup MSSV, ảnh, CODE_128 và xuất PNG đã có và được giữ lại.

Trước khi cập nhật emulator, đã dừng app và sao lưu database, thiết lập và file riêng vào `verification/roles-before/app-data.tar` (được gitignore vì có thể chứa dữ liệu cá nhân). Baseline v3 có 1 lớp, 9 sinh viên, 1 membership, 1 buổi, 1 bản ghi điểm danh và 1 sequence. Không dùng uninstall, clear data hoặc migration phá hủy.

## Sử dụng

1. Mở app sau nâng cấp: thiết lập tài khoản Admin đầu tiên bằng họ tên, tên đăng nhập và mật khẩu từ 8 đến 128 ký tự. Không có tài khoản/mật khẩu mặc định. Dữ liệu cũ được giữ nguyên sau bước này.
2. Admin vào thẳng Quản lý và có thanh điều hướng Quản lý / Lịch sử; không còn tab điểm danh. Quản lý gồm Sinh viên, Lớp học và Giảng viên. Tạo tài khoản giảng viên, chọn nhiều lớp ở **Phân công lớp** rồi lưu. Một lớp có thể được chọn cho nhiều giảng viên.
3. Menu góc trên có Đăng xuất. Buổi OPEN và dữ liệu vẫn được giữ để tiếp tục sau khi đăng nhập lại.
4. Giảng viên có Điểm danh / Lớp được giao / Lịch sử; chỉ thấy lớp được giao, sinh viên và kết quả thuộc các lớp đó. Không có nút tạo/sửa sinh viên, quản lý thành viên/lớp hay tài khoản. Màn quản trị mở bằng Intent cũng bị chặn.
5. Giảng viên tạo, mở, quét/thủ công, kết thúc buổi của mình. Giảng viên khác cùng lớp được xem kết quả nhưng không điều khiển buổi của người khác. Admin chỉ xem lịch sử/kết quả/thống kê trên giao diện; các nút tạo, mở, quét, thủ công, sửa kết quả và kết thúc buổi được dành cho Giảng viên.
6. Mỗi lớp và mỗi người phụ trách chỉ có tối đa một OPEN. Hai giảng viên có thể mở đồng thời ở hai lớp khác nhau. Muốn hủy phân công hoặc khóa tài khoản có buổi OPEN, cần kết thúc buổi trước; Giảng viên phụ trách cần kết thúc buổi.
7. Tài khoản giảng viên có thể sửa tên, đặt lại mật khẩu, khóa/mở khóa. Giữ tài khoản và liên kết lịch sử thay vì xóa vật lý.

## Dữ liệu và quyền

Room v4 thêm `UserAccount`, `TeacherClass` (khóa ghép nhiều-nhiều), `LocalLogin`, các cột người phụ trách và snapshot tên/username trên `AttendanceSession`. Giữ đường nâng cấp 1→2→3→4 và schema JSON của từng phiên bản.

Các buổi lịch sử trước phân quyền không bị gán một giảng viên giả: owner null, giao diện ghi **Dữ liệu trước phân quyền**. Admin vẫn xem các buổi cũ. Draft cũ được gán người phụ trách khi mở. Snapshot tên người phụ trách được chốt lúc mở; đổi tên tài khoản hoặc phân công sau đó không viết lại lịch sử.

Repository kiểm tra phiên, vai trò, phân công và chủ buổi trong transaction trước mọi thao tác ghi. Các danh sách/lịch sử/biểu đồ/CSV lấy cùng nguồn dữ liệu đã lọc theo vai trò. Teacher không thể vượt quyền chỉ bằng gọi phương thức repository hoặc mở Activity trực tiếp.

Mật khẩu dùng PBKDF2-HMAC-SHA256 600.000 vòng, salt ngẫu nhiên riêng từng tài khoản, so sánh constant-time. API 24–25 thiếu SHA256 PBKDF2 sẽ dùng PBKDF2-HMAC-SHA1 1.300.000 vòng. Không ghi mật khẩu plaintext vào DB, SharedPreferences hoặc log. Phiên cục bộ có token ngẫu nhiên, DB chỉ giữ hash và hạn 7 ngày; token gốc ở SharedPreferences riêng của app. Sai 5 lần tạm khóa đăng nhập 60 giây; Admin có thể đặt lại mật khẩu giảng viên. Tác vụ băm chạy ngoài main thread, giữ trạng thái qua xoay màn hình bằng ViewModel.

Tham khảo kỹ thuật: [OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html), [Android crypto support](https://android.googlesource.com/platform/libcore/+/master/tools/docs/crypto/data/crypto_support.json).

## Cơ chế sinh viên được bảo toàn

- Tạo mới bằng họ tên, ngày sinh, niên khóa, giới tính và ảnh tùy chọn; bộ đếm riêng từng niên khóa cấp MSSV trong transaction. Ví dụ 2020–2026 bắt đầu `202020260001`.
- Tạo từ lớp đồng thời tạo membership trong cùng transaction. Lưu lỗi không tiêu thụ số. Không dùng lại số của hồ sơ lưu trữ.
- Sửa hồ sơ/niên khóa không đổi MSSV đã cấp. Thẻ CODE_128 chứa chính MSSV, popup thành công có số và hình thẻ; hồ sơ có ảnh, thông tin, thẻ và lịch sử.
- Admin giữ **Xem lớp đang học / Thêm vào lớp đang quản lý**. Teacher chỉ có chức năng xem phù hợp quyền. Xuất PNG và mã thẻ vật lý cũ vẫn hoạt động.
- Không đưa UI gắn mã thẻ/cấp mã thủ công trở lại. API nhập CSV và tương thích dữ liệu cũ vẫn giữ theo ứng dụng trước đó, chỉ Admin được sử dụng.
- Camera vẫn dùng CameraX/ML Kit offline. Đã sửa trạng thái resume khiến analyzer có thể bị pause dù preview đang chạy; permission, đèn pin, chống quét trùng và nhập thủ công được giữ.

## Kiểm chứng

Kết quả cuối:

- Build debug, APK kiểm thử, unit test và lint: **BUILD SUCCESSFUL**. **10 unit test đạt**. Lint **0 lỗi, 354 cảnh báo** (chủ yếu chuỗi giao diện, tài nguyên chưa dùng và gợi ý cải tiến); chưa xử lý hết các cảnh báo này.
- AVD Pixel 4 API 37: **28/28 instrumentation test đạt** trong lần chạy cuối, gồm 3 luồng UI, 6 kiểm thử vai trò/migration/phiên/MSSV, 11 kiểm thử repository, 7 kiểm thử thẻ/ảnh/migration và 1 decoder ML Kit. Log: [roles-acceptance.log](verification/roles-acceptance.log).
- UI đã kiểm tra: thiết lập Admin qua tái tạo Activity, tạo tài khoản giảng viên, phân công lớp, đăng nhập sai/đúng, navigation Teacher, ẩn nút quản trị và từ chối mở StudentForm bằng Intent; tạo sinh viên từ lớp, popup thẻ qua xoay màn hình; tạo/mở/kết thúc buổi và CameraX streaming qua recreation.
- Nghiệp vụ đã kiểm tra: Teacher không thể gọi API quản trị; không thấy/điểm danh/xuất CSV lớp ngoài phân công; không điều khiển buổi của người khác; hai giảng viên mở ở hai lớp đồng thời; chặn OPEN trùng lớp hoặc cùng người phụ trách; snapshot lớp/sinh viên/giảng viên giữ nguyên; hủy phân công không sửa lịch sử; hash mật khẩu, khóa đăng nhập, reset/khóa tài khoản, lưu phiên và dữ liệu qua đóng/mở database.
- Migration kiểm thử cả **v3→v4** và **v1→v2→v3→v4**, xác nhận dữ liệu cũ, thẻ cũ, buổi OPEN và bộ đếm vẫn dùng được.
- Đã cập nhật APK trên emulator bằng **install -r**, mở app thật để nâng database v3 lên v4 rồi đối chiếu baseline: toàn bộ bản ghi và các cột cũ không đổi; số lượng vẫn 1 lớp, 9 sinh viên, 1 membership, 1 buổi, 1 record, 1 sequence. `foreign_key_check` rỗng, `integrity_check=ok`. **8 ảnh và 2 file thiết lập cũ giống từng byte**. AndroidX cập nhật marker nội bộ `files/profileInstalled` khi thay APK; file này không phải dữ liệu sinh viên và được loại khỏi phép so sánh. Log: [roles-data-preservation.log](verification/roles-data-preservation.log).
- Không có tài khoản/phân công/phiên thử trong database thật. Emulator hiện mở màn **Thiết lập quản trị viên** để người dùng tự chọn tài khoản. Ảnh giao diện: [roles-login.png](verification/roles-login.png).

Log build: [roles-build.log](verification/roles-build.log). Script đối chiếu dữ liệu chỉ đọc: [check-roles-data.py](verification/check-roles-data.py); cần bản sao baseline riêng đã tạo trong phiên làm việc này.

Các kiểm thử nghiệp vụ dùng Room in-memory hoặc database thử có tên ngẫu nhiên. `RoleUiTest` thay singleton repository bằng Room in-memory trong thời gian kiểm thử, kết thúc Activity rồi khôi phục singleton. Tài khoản thử không được tạo trong database thật của emulator.

Chạy build:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
```

Để giữ dữ liệu khi chạy emulator, dùng adb `install -r` cho APK debug và APK instrumentation, sau đó `am instrument -w -e class ... com.example.th9.test/androidx.test.runner.AndroidJUnitRunner`. Không dùng uninstall/clear data. Các lớp kiểm thử mới: `RolesInstrumentedTest` và `RoleUiTest`. Kiểm thử hồi quy database/thẻ/decoder: `RepositoryInstrumentedTest`, `StudentCardInstrumentedTest`, `BundledBarcodeTest`.

Giới hạn kiểm chứng: AVD Pixel 4 API 37; chưa chạy thiết bị vật lý hoặc API 24–25. Preview CameraX được kiểm tra thực tế trên AVD; barcode được giải mã từ PNG thật bằng ML Kit. Callback scan trong luồng UI được đưa dữ liệu CODE_128 đã biết để kiểm tra quyền và ghi nhận; điều này không chứng minh khả năng lấy nét/đọc thẻ in dưới mọi ánh sáng của camera thiết bị thật. Các thao tác Documents/ShareSheet có sẵn được giữ, không coi build thành công là runtime pass.
