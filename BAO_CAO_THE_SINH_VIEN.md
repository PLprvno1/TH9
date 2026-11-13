# Bàn giao quản lý sinh viên và thẻ điện tử — 10/11/2026

Bản cập nhật mới nhất (MSSV tự động, giới tính, popup và hồ sơ mới) nằm trong [CAP_NHAT_MSSV_TU_DONG.md](CAP_NHAT_MSSV_TU_DONG.md). Các mục bên dưới lưu diễn tiến triển khai các phiên bản trước.

## A. Khảo sát ban đầu

Đã đọc README, BAO_CAO, schema v1, entity/DAO/Repository, scanner, các Activity/menu và kiểm thử hiện hữu trước khi sửa. Project thực tế dùng Java/XML, Room 2.8.4, CameraX 1.5.1, ML Kit bundled 17.3.0, min SDK 24, compile/target SDK 37.

Có sẵn MainActivity (Trang chủ/Lớp học/Lịch sử), quản lý lớp, form/hồ sơ sinh viên, buổi DRAFT/OPEN/CLOSED, kết quả, thống kê, CSV, cài đặt và điểm danh thủ công. Năm bảng: Classroom, Student, ClassMembership, AttendanceSession, AttendanceRecord. Student.id là UUID nội bộ; MSSV và cardCode có unique index riêng. cardCode cũ là chuỗi nguyên gốc được gắn từ camera; không tự cắt tiền tố hoặc bỏ số 0 đầu. Thành viên lớp là quan hệ nhiều-nhiều. Khi mở buổi, thông tin sinh viên/lớp được chốt vào snapshot; khóa ghép sessionId/studentId và cập nhật có điều kiện chống ghi trùng. Repository giữ một buổi OPEN trên toàn app trong transaction.

Tái sử dụng toàn bộ nguồn dữ liệu và pipeline CameraX/ML Kit, Activity cơ sở, Tasks, FileProvider, menu và điều hướng. Không tạo hệ thống sinh viên song song, backend hay dữ liệu mẫu tự động. Giữ thay đổi cấu hình có sẵn của người dùng trong `.idea/misc.xml` và `gradle.properties`.

## B. Đã triển khai

- **Quản lý sinh viên:** Tab Quản lý → Quản lý sinh viên. Danh sách toàn bộ hồ sơ hiện hành, tìm tên/MSSV, ảnh hoặc placeholder, lớp chính, khóa học, trạng thái thẻ điện tử, thêm/xem/sửa và ContextMenu xem thẻ/xóa. Xóa có xác nhận, lưu trữ hồ sơ và gỡ thành viên hiện tại; không xóa bản ghi lịch sử, không thay đổi snapshot hoặc hủy buổi đang mở. MSSV của hồ sơ lưu trữ được giữ để tránh dùng lại định danh cũ.
- **Hồ sơ:** họ tên, MSSV, DatePicker không cho ngày tương lai, khóa học dạng yyyy–yyyy hoặc yyyy-yyyy với năm kết thúc sau năm bắt đầu; không ép bốn năm. Trường cố định Trường Đại học Thủ Dầu Một. Chọn lớp chính từ lớp đã quản lý, lưu riêng nhãn lớp trên thẻ; thao tác này không thêm/gỡ quan hệ thành viên. Nút Thêm vào lớp ở hồ sơ thực hiện việc thêm thành viên rõ ràng.
- **Ảnh:** ACTION_OPEN_DOCUMENT chỉ nhận ảnh, đọc giảm kích thước, chỉnh hướng EXIF rồi sao chép JPEG vào `files/student_photos/`. Không giữ URI tạm trong DB, không xin quyền đọc/ghi toàn bộ bộ nhớ. Không có ảnh hoặc tệp lỗi/mất được thay bằng placeholder. Dữ liệu ảnh đang sửa được giữ qua việc tạo lại Activity.
- **Mã thẻ:** digitalCardCode lưu riêng với cardCode vật lý cũ. SecureRandom sinh `TDMU1-` + 12 ký tự từ bảng Base32 `23456789ABCDEFGHJKLMNPQRSTUVWXYZ`. Kiểm tra va chạm, tối đa 32 lần thử, unique index ở Room. Cấp tự động trong transaction khi tạo hồ sơ bằng form hoặc CSV. Hồ sơ cũ chưa có mã được cấp một lần từ màn thẻ; mở màn hình và sửa hồ sơ không đổi mã. Không cung cấp thao tác cấp lại mã điện tử. Đây là định danh ngẫu nhiên, không phải thuật toán mã hóa bảo mật.
- **Thẻ:** StudentCardActivity với XML cho bố cục/nút và StudentCardView vẽ nội dung động bằng Java. Bố cục ngang bo góc, header xanh, trường/tựa thẻ, ảnh không bóp méo, tên dài tự xuống dòng/giảm cỡ chữ, MSSV/ngày sinh/khóa/lớp, CODE_128 và chuỗi mã, chú thích demo. Không dùng logo/chứng nhận chính thức.
- **PNG/chia sẻ:** cùng một hàm vẽ cho preview và PNG 1600×1050; xuất toàn bộ thẻ, không chụp thanh công cụ/nút. ZXing Core 3.5.3 tạo barcode đen trắng với quiet zone, không làm mịn bitmap barcode. Lưu bằng Android CreateDocument, chia sẻ qua Sharesheet với FileProvider và quyền URI tạm. Xử lý kết quả lưu sau khi Activity được tạo lại.
- **Scanner:** nhận diện mã điện tử bằng Room, giữ chuỗi chính xác. Vẫn hỗ trợ thẻ/MSSV cũ và các định dạng 1D sẵn có, kể cả mã vật lý cũ có tiền tố TDMU1-. Chỉ cho tạo mã mới trong namespace TDMU1- bằng bộ cấp mã. Barcode điện tử phải có định dạng CODE_128 khi nhận từ camera. Mã không đăng ký không tạo sinh viên.
- **Điểm danh:** mã mới đi qua cùng transaction kiểm tra OPEN, snapshot, có mặt/muộn và chống trùng như mã cũ/thủ công; cập nhật tiến độ cũ. Sinh viên ngoài snapshot và buổi đã đóng bị từ chối.
- **Quét kiểm tra:** MODE_VERIFY_CARD dùng camera hiện hữu, không cần buổi; hiển thị hồ sơ trùng khớp hoặc lỗi. Repository tra cứu chỉ đọc; nhánh này không gọi scan/checkIn. Không tạo AttendanceRecord.
- **Camera/webcam:** giữ pipeline 1280×720, KEEP_ONLY_LATEST, xử lý một frame, đóng ImageProxy trên các nhánh, epoch chặn kết quả cũ, debounce kết quả. Có hướng dẫn ánh sáng/khoảng cách; chạm lấy nét/đo sáng khi phần cứng báo hỗ trợ. Flash chỉ bật được khi camera có flash. Điểm danh thủ công vẫn có trong chế độ điểm danh.
- **Menu/nghiệp vụ:** giữ Navigation bar, Options Menu, SubMenu, ContextMenu cũ, CSV và thống kê. Explicit Intent giữa các màn; implicit Intent khi chia sẻ thẻ. Animation nhẹ khi hiển thị thẻ/kết quả.

### Room migration

Nâng schema **1 → 2** bằng MIGRATION_1_2, đăng ký tại Room.databaseBuilder. Bổ sung các cột nullable digitalCardCode, birthDate, course, primaryClassId, photoPath; archived NOT NULL DEFAULT 0 và unique index digitalCardCode. Không đổi khóa ID, không đổi năm bảng/các khóa ngoại và khóa ghép, không xóa dữ liệu, không bật destructive migration. Giữ schema 1.json và xuất 2.json. Mã điện tử cũ để NULL sau nâng cấp; không tự ghi đè mã vật lý.

CSV vẫn dùng header cũ `mssv,ho_ten,ma_the`; ma_the tiếp tục là mã vật lý. Không sửa tên/mã của hồ sơ hiện hữu khi nhập. Hồ sơ mới nhận thêm mã điện tử riêng. Các trường hồ sơ mở rộng chưa có trong CSV; CSV hiện tại phục vụ danh sách lớp/kết quả, không phải bản sao lưu đầy đủ hồ sơ/ảnh.

### File chính

- `data/AttendanceDatabase.java`, `data/AttendanceRepository.java`, `app/schemas/.../2.json`.
- `data/CardCodes.java`, `data/ProfileRules.java`, `data/StudentPhotos.java`, `model/Student.java`.
- `ui/StudentsActivity.java`, `ui/StudentFormActivity.java`, `ui/StudentProfileActivity.java`, `ui/StudentCardActivity.java`.
- `view/StudentCardView.java`, `ui/ScanActivity.java`, `camera/CameraScanner.java`, `adapter/StudentAdapter.java`, `MainActivity.java`.
- XML `activity_students`, `activity_student_form`, `activity_student_card`; AndroidManifest và dependency ZXing Core chuyển từ androidTest sang implementation.
- `StudentCardRulesTest`, `StudentCardInstrumentedTest`, `StudentCardUiTest`.

## C. Kết quả kiểm thử

**Lưu ý:** Các kết quả PASS trong mục này thuộc đợt triển khai ban đầu, trước lần sửa scanner theo phản hồi người dùng ở mục F. Bản sửa scanner mới chưa được build hoặc kiểm thử theo yêu cầu người dùng.

Thiết bị: **Pixel_4, emulator-5554, Android API 37, x86_64**, camera rear/front cấu hình Webcam0. Đã sao lưu dữ liệu ứng dụng trước cài bằng `adb install -r`; không dùng `pm clear`, không gỡ cài đặt app và không xóa database người dùng. Kiểm thử repository/migration dùng database in-memory hoặc tệp kiểm thử riêng.

| Hạng mục | Kết quả và bằng chứng |
|---|---|
| Build | **PASS** — assembleDebug và assembleDebugAndroidTest; chạy trực tiếp từ Android Studio. [Log build](verification/student-card-build.log). |
| JVM | **PASS 10/10** — 6 bài cũ và 4 bài mới: 10.000 mã không trùng trong mẫu thử, định dạng chính xác/không trim, ngày sinh/khóa học hợp lệ và không hợp lệ. [Báo cáo JVM](app/build/reports/tests/testDebugUnitTest/index.html). Tính duy nhất thực tế còn được bảo vệ bằng unique index, không chỉ dựa vào phép thử ngẫu nhiên. |
| Instrumentation | **PASS 21/21**, không skip/fail trong lần tổng hợp cuối: StudentCardInstrumentedTest 7, RepositoryInstrumentedTest 11, BundledBarcodeTest 1, CameraLifecycleTest 1, StudentCardUiTest 1. [Log cuối](verification/student-card-instrumentation.log). |
| Room/migration | **PASS** — dựng DB từ schema 1.json, nâng lên v2 qua migration thật và Room kiểm tra schema; giữ lớp/sinh viên/thành viên/buổi OPEN/snapshot/thẻ vật lý. Unique index từ chối mã trùng; cấp một lần, sửa hồ sơ không đổi mã; đổi lớp chính không thay thành viên; lưu trữ sinh viên giữ snapshot và cho phép xử lý thủ công danh sách đã chốt. |
| Ảnh/lưu bền | **PASS** — ảnh 2400×3200 được giảm cỡ, sửa EXIF xoay 90°, sao chép nội bộ; xóa ảnh nguồn vẫn đọc được ảnh trong app. Đóng/mở database trên đĩa giữ mã/đường dẫn; tệp ảnh mất trả placeholder. UI chọn ảnh thật bằng Android Documents và giữ ảnh qua xoay form. |
| Barcode | **PASS** — ML Kit bundled giải mã đúng chuỗi từ PNG **toàn bộ thẻ** 1600×1050 xuất bằng hàm production; test cũ vẫn giải mã đúng mã vật lý CODE_128. [PNG mẫu có tên dài](verification/student-card.png). Đây là bitmap test, không phải quét vật lý. |
| Điểm danh/hồi quy | **PASS** — mã điện tử đúng lớp, sai lớp, không tồn tại/sai định dạng, mã cũ, trùng giữa quét/thủ công, sau đóng buổi; đúng giờ/muộn, một OPEN toàn app, race chỉ ghi một lần, CSV nhập/xuất và snapshot không đổi. Mã vật lý cũ có tiền tố TDMU1- vẫn nhận diện được. |
| UI hồ sơ/thẻ | **PASS** — tạo/tìm/xem/sửa, chọn ảnh qua provider hệ thống, DatePicker, giữ trường qua xoay ngang/dọc, mở thẻ, lưu PNG có dữ liệu qua Android Documents, Sharesheet, mở scanner kiểm tra, tạo lại/xoay màn thẻ. Có kiểm tra tạo lại màn thẻ khi hộp thoại lưu đang mở. [Ảnh UI](verification/student-card-ui.png). |
| Quét kiểm tra | **PASS** — lookup repository không ghi dữ liệu; UI gọi nhánh callback scanner bằng mã đã lưu, hiển thị tên/MSSV trùng khớp và tổng số AttendanceRecord không thay đổi. Việc đưa chuỗi vào callback là kiểm thử tích hợp, không phải ảnh camera nhận dạng thật. |
| Camera | **PASS** — PreviewView STREAMING sau tạo lại/mở lại và xoay ngang/dọc, quan sát ảnh webcam thật. [Ảnh camera](verification/student-card-camera.png). Torch/AF chỉ gọi khi phần cứng báo hỗ trợ. |
| Dữ liệu emulator cũ | **PASS** — đối chiếu từng cột cũ của toàn bộ bản ghi trước/sau: 1 lớp, 1 sinh viên, 1 thành viên, 1 buổi, 1 record giữ nguyên. Schema=2, foreign_key_check không có lỗi; âm thanh/rung/theme giữ nguyên. Hiện không có buổi OPEN, đúng trạng thái gốc. Có thêm **6 hồ sơ UI kiểm thử** do các lần chạy tạo ra; không thêm buổi/attendance mới vào dữ liệu người dùng. [Log đối chiếu](verification/student-card-data-preservation.log), [script](verification/check-student-card-data.py). Bản sao lưu/tệp database cục bộ được loại khỏi Git. |
| Khởi động lại | **PASS** — force-stop rồi khởi động MainActivity thành công với LaunchState COLD; database thật đọc được sau migration. |
| Lint | **PASS — 0 lỗi, 316 cảnh báo**; không suppress/baseline lỗi. [Báo cáo lint](app/build/reports/lint-results-debug.html). |

Lệnh build cuối:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
```

Instrumentation được chạy bằng `adb shell am instrument -w -e class ... com.example.th9.test/androidx.test.runner.AndroidJUnitRunner` trên năm test class nêu trên, sau khi cài đè app/test APK bằng `adb install -r`. Bài UI tạm tắt animation hệ thống để ổn định thao tác Espresso và phục hồi đúng các giá trị trước kiểm thử trong @After.

Các lỗi phát hiện đã được sửa: thẻ ở chiều ngang co theo chiều cao viewport để không bị cắt khi xem; kết quả CreateDocument được chờ đến khi thẻ sẵn sàng sau tái tạo Activity. Không còn lỗi build/test/lint trong phạm vi đã chạy.

## D. Quy trình kiểm thử thủ công

1. Mở project trong Android Studio, Gradle Sync, chọn app/Pixel_4 rồi bấm **Run**. Không cần xuất APK riêng. **Quản lý → Quản lý sinh viên**.
2. Nhấn Thêm sinh viên, nhập tên/MSSV, chọn ảnh từ thiết bị, ngày sinh và khóa học; chọn lớp chính nếu đã có lớp. Nhấn Lưu. Mã điện tử được cấp tự động; không cần gắn mã vật lý.
3. Chạm sinh viên → Xem / Cấp thẻ điện tử. Với hồ sơ cũ chưa có mã, nhấn phần thông báo cấp mã. Kiểm tra tên/ảnh/thông tin/barcode.
4. Nhấn Lưu thẻ PNG, chọn thư mục trong Android Documents và xác nhận Lưu. Chia sẻ thẻ mở Sharesheet. PNG phải chỉ chứa thẻ.
5. Trong Device Manager → Edit Pixel_4 → Camera, đặt Back = Webcam0. Emulator hiện đã có rear/front = webcam0. Cho app quyền Camera. Nếu webcam đang được ứng dụng khác sử dụng, đóng ứng dụng đó rồi Thử lại.
6. Nhấn Quét kiểm tra. Hiển thị PNG trên thiết bị/màn hình khác hoặc in thẻ; đưa barcode nằm ngang trước webcam, đủ lớn và tránh phản sáng. Kiểm tra tên/MSSV trùng hồ sơ; chế độ này không thay đổi số người điểm danh.
7. Từ hồ sơ chọn Thêm vào lớp đang quản lý (hoặc từ lớp thêm MSSV hiện có). Chọn lớp chính trên thẻ không thay cho việc thêm thành viên. Tạo buổi; chỉ mở khi có thành viên. Nếu đã có buổi OPEN, tiếp tục/kết thúc buổi đó theo nhu cầu trước khi mở buổi khác.
8. Mở buổi → Quét thẻ. Kiểm tra đúng tên, thời gian, có mặt/muộn và tiến độ tăng một lần. Quét lại cùng thẻ phải báo đã điểm danh, không tăng số lượng. Thử thẻ ngoài lớp, mã lạ và quét sau đóng buổi. Thử điểm danh thủ công để kiểm tra luồng dự phòng.
9. Sửa tên/ảnh/lớp chính rồi quay lại thẻ: mã giữ nguyên. Xoay ngang/dọc; force-stop và mở lại: hồ sơ/ảnh/mã còn nguyên. Không dùng Xóa dữ liệu hoặc gỡ cài đặt để kiểm tra khởi động lại.

## E. Giới hạn và phần cần thiết bị thực tế

- **NOT TESTED — quét vật lý end-to-end qua webcam:** chưa có thẻ/ảnh được đưa trước webcam để xác minh toàn chuỗi hình ảnh thật → ML Kit → Room → điểm danh. Test ML Kit trên PNG và callback scanner không thay cho ca này.
- **NOT TESTED — điện thoại thật:** chất lượng đọc dưới ánh sáng yếu/nhòe/lóa, autofocus/torch theo phần cứng, âm thanh/rung, máy Android API 24 và các nhà cung cấp tệp khác. Camera/ảnh/SAF đã được kiểm tra trên Pixel_4 API 37.
- Các bài UI kiểm thử cũ UiFlowTest/DocumentsUiTest **NOT RUN** trong đợt này để hạn chế thay đổi dữ liệu/cài đặt người dùng; chúng chủ động tạo lớp và mở buổi mới. Đã chạy lại RepositoryInstrumentedTest để hồi quy nghiệp vụ, BundledBarcodeTest và CameraLifecycleTest; có bài UI mới riêng cho hồ sơ/thẻ. Không dùng connectedDebugAndroidTest trên dữ liệu quan trọng vì quá trình quản lý APK của Gradle có thể gỡ app.
- Bài UI tạo hồ sơ có MSSV bắt đầu `00` kèm timestamp và tên kiểm thử. Các hồ sơ kiểm thử được tạo có chủ đích; cài mới vẫn trống. Ảnh trong bằng chứng UI là ảnh màu tổng hợp để kiểm thử chọn/lưu ảnh.
- Có cảnh báo lint, chủ yếu chuỗi giao diện hardcode/style và cảnh báo tồn tại từ trước; không thêm baseline, không tắt lỗi lint.

## F. Sửa scanner theo phản hồi không nhận mã qua camera

Qua đọc source, phát hiện `DataActivity.onResume()` gọi `ScanActivity.onDataResume()` khi AndroidX có thể chưa chuyển registry sang RESUMED. `pauseScanner()` dựa vào registry tại thời điểm đó nên đặt scanner ở trạng thái paused; sau khi lifecycle chuyển sang RESUMED, không có callback nào bật lại analyzer. Vì thế PreviewView vẫn có thể streaming nhưng mọi frame đều bị bỏ qua, chưa đến ML Kit. Trình tự dispatch ON_RESUME được đối chiếu với [source AndroidX ReportFragment](https://android.googlesource.com/platform/frameworks/support/+/e04a6b04c4826c20696eb774b338867276db75fa/lifecycle/lifecycle-runtime/src/main/java/androidx/lifecycle/ReportFragment.kt).

Đã sửa:

- `ScanActivity`: dùng trạng thái foreground riêng, bật lại analyzer trong `onPostResume()` và dừng trong `onPause()`. Vẫn dừng khi đang xác nhận thẻ, thao tác thủ công hoặc xử lý dữ liệu; các callback đó dùng cùng hàm cập nhật trạng thái. Khi khởi động camera lại, xóa tham chiếu camera và trạng thái đèn cũ.
- `CameraScanner.setPaused()`: chỉ đổi epoch khi trạng thái thực sự thay đổi, tránh loại bỏ kết quả đang giải mã vì các lệnh cập nhật trạng thái trùng nhau.
- CameraX ưu tiên ImageAnalysis 1920×1080, YUV_420_888, đặt rotation theo display và đồng bộ tỷ lệ preview 16:9; CameraX thương lượng độ phân giải thực tế theo thiết bị. Đây là cấu hình ưu tiên, không khẳng định webcam luôn cung cấp Full HD. Lựa chọn độ phân giải dựa trên [hướng dẫn ảnh đầu vào của ML Kit](https://developers.google.com/ml-kit/vision/barcode-scanning/android#input-image-guidelines).
- Preview dùng fitCenter để hiển thị toàn khung hình, tránh cắt hai đầu barcode do fillCenter.
- Gom xử lý kết quả ML Kit vào callback hoàn tất, đóng frame và giải phóng cờ analyzing trong `finally`, kể cả khi task thất bại/hủy hoặc bỏ kết quả cũ. Lỗi đồng bộ khi gửi frame được ghi log và hiển thị thông báo thay vì bị bỏ qua im lặng.
- Logcat tag `TH9CameraScanner` ghi trạng thái paused/resumed, kích thước/rotation frame thực tế đầu tiên, định dạng khi đọc được mã và lỗi phân tích. Không log nội dung mã thẻ. Thông báo “Đang phân tích ảnh” chỉ được phát sau khi một frame thực sự đã qua ML Kit.

**Trạng thái: CHƯA BUILD / NOT TESTED**, theo yêu cầu người dùng chỉ sửa source và tự kiểm tra thực tế. Không chạy Gradle, kiểm thử, mở camera, cài app hoặc thay đổi dữ liệu emulator trong lần sửa này. Cần bấm Run lại trong Android Studio để sử dụng source đã sửa.
