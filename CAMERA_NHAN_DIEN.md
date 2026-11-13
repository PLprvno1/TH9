# Tăng khả năng nhận diện camera

Giao diện cập nhật 11/11/2026: đã bỏ nhãn/nút chữ zoom, dòng hướng dẫn trên camera và thông báo giải mã lặp lại. Phần dưới hiển thị kết quả khi quét; vẫn có zoom bằng hai ngón tay và tự zoom trong pipeline.

Cập nhật trực tiếp pipeline CameraX/ML Kit hiện có; không đổi schema, MSSV, tài khoản hoặc nghiệp vụ điểm danh.

- Chọn ảnh YUV ưu tiên 1920×1080 theo tỷ lệ 16:9, cho phép CameraX chọn kích thước được hỗ trợ và thử lại 1280×720 nếu tổ hợp 1080p không bind được.
- Lấy nét/đo sáng/cân bằng trắng ở tâm khi preview bắt đầu. Nếu liên tục chưa đọc được mã, thử lấy nét lại mỗi 3,5 giây; mỗi yêu cầu tự hủy sau 2 giây để trả quyền lấy nét tự động cho camera. Chạm preview vẫn lấy nét tại điểm chạm khi phần cứng hỗ trợ.
- Bật gợi ý auto-zoom của ML Kit; giới hạn tối đa 3× hoặc mức camera hỗ trợ nếu thấp hơn. Có nút đổi zoom 1× / 1,5× / 2× / tối đa, và hỗ trợ chụm/mở hai ngón tay. Cơ chế ML Kit theo [tài liệu chính thức](https://developers.google.com/ml-kit/vision/barcode-scanning/android).
- Khi ML Kit chưa giải mã được sau vài khung hình, bổ sung ZXing offline làm bộ đọc dự phòng trên độ sáng Y nguyên bản. Đọc đúng rowStride/pixelStride của camera, thử toàn ảnh, hai chiều xoay và đảo sáng/tối. Không chuyển từng frame sang JPEG/Bitmap. Bộ dự phòng tối đa một lần mỗi 250 ms để tránh chạy nặng trên mọi frame.
- Giữ KEEP_ONLY_LATEST, một frame đang xử lý, đóng ImageProxy, hủy kết quả cũ khi pause/đóng màn hình và xử lý quyền camera như trước. Kết quả vẫn đi qua repository để kiểm tra quyền, sinh viên thuộc buổi và chống điểm danh trùng.

## Cập nhật quét toàn ảnh

- Cả ML Kit và ZXing đọc toàn bộ ảnh camera; đã bỏ việc cắt dải giữa ở bộ dự phòng. Khung ngang chỉ hướng dẫn, không giới hạn vị trí mã.
- Gửi ảnh cho ML Kit ngay; chỉ chạy ZXing nếu ML Kit chưa đọc được hoặc trả lỗi. Bỏ điều kiện phải chờ ba frame trống; khoảng cách giữa các lần dự phòng giảm từ 800 ms xuống 250 ms.
- Chạy giải mã dự phòng trên executor của camera, chuyển riêng kết quả sang UI. Giữ frame tới khi xử lý xong và bỏ kết quả đã cũ khi pause/đóng màn hình.
- Khi phát hiện vùng nghi có mã ngoài tâm, hướng đo sáng/lấy nét tới vùng đó. Tự zoom chỉ được tăng nếu toàn bộ vùng mã vẫn nằm trong ảnh sau khi zoom; nút zoom thủ công được giữ.
- Thông báo giao diện ghi rõ quét toàn ảnh, không cần căn vào khung.

## Kiểm chứng hiện tại

- `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest`, `lintDebug`: thành công. 22 unit test đạt; lint 0 lỗi, còn 356 cảnh báo trên toàn project.
- 7 unit test cho bộ đọc dự phòng, gồm mã ở bốn góc ngoài khung (ngang/dọc), đảo màu sát mép và các trường hợp: CODE_128 tương phản thấp, đảo màu, xoay dọc, stride/offset/pixelStride và buffer cuối dòng, từ chối ảnh trắng/buffer thiếu.
- 5 unit test hình học zoom kiểm tra giữ mã trung tâm, từ chối crop ở trái/phải/trên/dưới, kiểm tra toàn bộ mã và trường hợp zoom out/giữ nguyên tỷ lệ.
- 4 kiểm thử emulator đạt: nút zoom theo khả năng phần cứng, kiểm tra gợi ý zoom/pause/resume và preview qua recreation/mở lại; luồng điểm danh Teacher với callback CODE_128; ML Kit giải mã bitmap thật ở giữa và cả bốn góc, với rotation 0°/90°.
- So sánh snapshot riêng ngay trước/sau cập nhật camera: mọi bảng/bản ghi database không đổi, schema vẫn v4; ảnh, thiết lập và phiên đăng nhập được giữ nguyên. [Log dữ liệu](verification/full-frame-data.log).
- Log: [build](verification/full-frame-build.log), [runtime](verification/full-frame-runtime.log).
- Các kiểm thử UI dùng repository in-memory riêng. Cập nhật ứng dụng bằng `adb install -r`, không uninstall/clear data.

Chưa đo tỷ lệ đọc trên thẻ in hoặc camera điện thoại thật. Mã quá nhỏ, mất nét hoặc lóa vẫn cần điều chỉnh khoảng cách/ánh sáng. Khi quét, giữ trọn mã trong ảnh camera ở bất kỳ vị trí nào, chạm để lấy nét; dùng nút zoom nếu mã nhỏ và nút đèn pin khi cần.
