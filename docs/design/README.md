# Bộ sơ đồ thiết kế Đặt Sân

Bộ sơ đồ được đối chiếu với mã nguồn trong repository ngày 25/11/2026. Kiến trúc dùng React, Spring Boot và MySQL. ERD dựa trên entity JPA và các migration có trong repo. Việc đối chiếu chưa bao gồm kiểm tra schema của một cơ sở dữ liệu đang chạy.

## Các tệp dùng để chèn vào Word

Trong thư mục `png` có 7 ảnh độ phân giải cao, nền trắng. Thư mục `svg` có bản vector tương ứng. Thư mục `sources` chứa mã PlantUML có thể chỉnh sửa.

- `01-resource-model.png` thể hiện resource nghiệp vụ, định danh và quan hệ từ góc nhìn API consumer.
- `02-ERD-core.png` thể hiện các bảng nghiệp vụ cốt lõi và bảng phiên đăng nhập.
- `03-ERD-services.png` thể hiện phần dịch vụ bổ sung, thuê dụng cụ và ảnh bàn giao.
- `04-system-architecture.png` thể hiện các thành phần frontend, backend và lưu trữ.
- `05-UML-use-case.png` thể hiện các chức năng chính theo ba vai trò.
- `06-UML-booking-sequence.png` thể hiện luồng kiểm tra lịch, báo giá và tạo booking tại backend.
- `07-UML-booking-states.png` thể hiện trạng thái và điều kiện chuyển trạng thái của booking.

Chèn ảnh bằng Insert → Pictures trong Word và giữ nguyên tỷ lệ. Với hai ERD có nhiều trường dữ liệu, nên dùng trang riêng và kiểm tra cỡ chữ sau khi chèn. SVG có thể phóng lớn mà vẫn giữ độ nét trên các phiên bản Word hỗ trợ định dạng này.

## Chú thích và thuyết minh để đưa vào báo cáo

### Hình 1. Resource model của hệ thống Đặt Sân

Hệ thống tổ chức các resource chính gồm người dùng, cơ sở thể thao, môn thể thao, sân, lịch đặt, khoản thanh toán và đánh giá. Một cơ sở có thể chứa nhiều sân vật lý. Mỗi sân có lịch và giá riêng, vì vậy yêu cầu đặt sân phải xác định một sân cụ thể. Một người dùng có thể tạo nhiều lịch đặt. Mỗi lịch đặt có thể liên kết nhiều bản ghi thanh toán và tối đa một đánh giá.

Resource được nhận diện qua URI như `/venues/{venueId}`, `/courts/{courtId}` và `/bookings/{bookingId}`. Các API availability và quote tính toán thông tin từ sân, thời gian và lịch đã tồn tại. Các thao tác confirm, cancel và complete thực hiện chuyển trạng thái nghiệp vụ của booking. Với người dùng hiện tại, API sử dụng `/users/me`.

### Hình 2. ERD dữ liệu cốt lõi và phiên đăng nhập

Dữ liệu nghiệp vụ chính được lưu trong các bảng users, venues, sport_types, courts, bookings, payments và reviews. Bảng auth_sessions lưu thông tin phiên xác thực và giá trị băm của token. Các quan hệ được thể hiện bằng khóa ngoại và số lượng liên kết. Ký hiệu 1 biểu thị đúng một bản ghi, 0..* biểu thị từ không đến nhiều bản ghi, 0..1 biểu thị tối đa một bản ghi.

Booking liên kết trực tiếp với người đặt và sân. Venue của booking được xác định thông qua sân. Review có các khóa ngoại riêng đến người dùng, cơ sở và booking. Khóa duy nhất trên booking_id của review giới hạn mỗi booking có tối đa một đánh giá. Booking sử dụng ràng buộc duy nhất theo cặp user_id và request_key để hỗ trợ việc gửi lại yêu cầu có cùng khóa.

Sơ đồ trình bày các thuộc tính chính và ràng buộc phục vụ thuyết minh. Chi tiết độ dài, giá trị mặc định, tính nullable và các chỉ mục còn lại được xác định trong entity và migration.

### Hình 3. ERD dịch vụ bổ sung và thuê dụng cụ

Một cơ sở có thể cung cấp nhiều dịch vụ bổ sung, gồm hàng tiêu hao và dịch vụ cho thuê. Mỗi đơn dịch vụ thuộc một booking và ghi nhận tên hàng, số lượng, giá tại thời điểm đặt cùng các mốc giao hoặc hủy. Với dịch vụ cho thuê, các thiết bị vật lý được quản lý riêng bằng mã và trạng thái.

Bảng equipment_rentals liên kết đơn dịch vụ với thiết bị được thuê. Ảnh bàn giao và hoàn trả thuộc từng lượt thuê, được phân loại theo giai đoạn và góc chụp. Ràng buộc duy nhất theo rental_id, phase và angle bảo đảm mỗi tổ hợp có tối đa một bản ghi ảnh hiện tại. Phần mở rộng đã có mã backend và cần được cân nhắc theo mức ưu tiên của MVP.

### Hình 4. Kiến trúc hệ thống Đặt Sân

Giao diện React gọi các hàm nghiệp vụ qua lớp services và API client. Trong chế độ HTTP, backendAdapter chuyển đổi dữ liệu giữa giao diện và backend. Ở môi trường phát triển, Vite proxy chuyển request có prefix `/api` hoặc `/api/v1` tới backend và loại bỏ prefix này.

Backend là một ứng dụng Spring Boot dùng chung cho các nhóm nghiệp vụ. Spring Security và AccessRules kiểm tra xác thực, vai trò và quyền trên tài nguyên. Controller tiếp nhận request, service thực hiện nghiệp vụ, lớp lưu trữ truy cập MySQL. Mã quản lý ảnh thuê có tích hợp Cloudinary.

Đường nét đứt trong sơ đồ đánh dấu phần xác thực frontend còn cần tích hợp. Adapter hiện vẫn có cơ chế đăng nhập cục bộ, trong khi backend đã cung cấp `/auth/login`, `/auth/refresh` và `/auth/logout`. Sơ đồ thể hiện cấu trúc mã hiện tại. Trạng thái tích hợp thực tế phải được xác nhận bằng kiểm thử runtime.

### Hình 5. UML use case của hệ thống Đặt Sân

Khách hàng tìm sân, xem lịch trống và giá, đăng ký tài khoản, quản lý hồ sơ, đặt hoặc hủy lịch của mình và đánh giá sau khi sử dụng. Chủ sân quản lý cơ sở, sân thuộc quyền sở hữu và xử lý các yêu cầu đặt. Quản trị viên quản lý danh mục môn thể thao.

Các API đọc công khai cho phép xem cơ sở, sân, lịch trống và báo giá. Những thao tác trên dữ liệu riêng yêu cầu xác thực và quyền phù hợp. Đăng ký công khai tạo tài khoản CUSTOMER. Tài khoản OWNER và ADMIN được cấp qua công cụ quản trị backend.

### Hình 6. UML sequence của luồng kiểm tra lịch, báo giá và đặt sân

Consumer gọi API lịch trống, sau đó lấy báo giá cho khoảng thời gian đã chọn. Khi gửi yêu cầu tạo booking, consumer sử dụng access token do backend cấp. Backend lấy người dùng từ phiên xác thực, kiểm tra khoảng thời gian và xử lý khóa idempotency nếu được gửi kèm.

Nếu khóa đã tồn tại và nội dung khớp, backend trả lại booking cũ. Nếu cùng khóa được dùng cho nội dung khác, request nhận lỗi 409. Với yêu cầu tạo mới, service khóa sân trong transaction, kiểm tra lại khả dụng và tính giá từ dữ liệu tin cậy của backend trước khi lưu booking ở trạng thái PENDING.

Quote cung cấp thông tin tại thời điểm hỏi. Việc giữ chỗ được thực hiện khi tạo booking thành công. Luồng trong ảnh được rút gọn để nhấn mạnh các bước chính. Nguồn PlantUML có các thông điệp trả về và nhánh xử lý chi tiết hơn. Luồng này dùng trực tiếp HTTP API backend, với điều kiện có phiên CUSTOMER hợp lệ.

### Hình 7. UML state machine của booking

Booking mới được tạo ở trạng thái PENDING, với hạn giữ chỗ bằng thời điểm sớm hơn giữa thời gian hiện tại cộng 30 phút và giờ bắt đầu sử dụng sân. Chủ sân có thể xác nhận một yêu cầu còn hiệu lực để chuyển sang CONFIRMED. Yêu cầu quá hạn được scheduler chuyển sang EXPIRED.

PENDING và CONFIRMED có thể chuyển sang CANCELLED theo quyền và điều kiện hiện có trong backend. Booking đã check-in phải đi qua luồng check-out. Check-in cập nhật mốc thời gian và giữ trạng thái CONFIRMED. Booking chuyển sang COMPLETED qua thao tác complete hoặc check-out khi đáp ứng điều kiện của từng luồng. Dữ liệu thanh toán được quản lý riêng với trạng thái booking.

## Giải thích resource model và database schema

Resource model mô tả cách API consumer nhận diện và tương tác với các đối tượng nghiệp vụ. Database schema mô tả cách hệ thống lưu dữ liệu, liên kết bảng và bảo đảm ràng buộc. Hai mô hình phục vụ các mục đích khác nhau nên có thể có cấu trúc khác nhau.

Ví dụ, API `/courts/{id}/availability` trả danh sách khung giờ được tính toán từ giờ mở cửa và booking. Trong mô hình dữ liệu hiện tại, thông tin này được suy ra từ các bảng liên quan. Bảng auth_sessions phục vụ quản lý phiên bên trong backend, trong khi consumer thực hiện các thao tác login, refresh và logout qua API xác thực. Trường password_hash được lưu trong bảng users và được cấu hình để tránh xuất ra JSON trả về cho client.

Backend hiện nhận request tạo booking với trường `court: {id}`. Contract frontend có sử dụng `courtId`, và adapter thực hiện chuyển đổi. Bộ sơ đồ dùng luồng backend hiện tại làm căn cứ. Việc thống nhất contract vẫn là đầu việc cần hoàn thiện.

## Nguồn đối chiếu trong repo

- Entity trong backend và entity AuthSession.
- BookingController, BookingServiceImpl, BookingRepository và CourtSchedule.
- BookingHoldExpiry và AddonService.
- SecurityConfig, AccessRules và AuthController.
- Các migration V001, V002 và V003.
- client.js, backendAdapter.js và cấu hình proxy Vite của frontend.

## Chỉnh sửa và tạo lại ảnh

Mỗi tệp `.puml` mở được bằng công cụ hỗ trợ PlantUML. PNG và SVG hiện tại được dựng bằng script để cố định bố cục cho báo cáo. Khi render nguồn PlantUML, vị trí các thành phần có thể thay đổi theo engine. Nguồn PlantUML đã được rà soát cấu trúc nhưng chưa được biên dịch bằng PlantUML trong môi trường này.

Script `generate_diagrams.py` tạo lại toàn bộ ảnh và nguồn, cần Python 3, Pillow và bộ font Arial của Windows. Script chứa dữ liệu sơ đồ và bố cục xuất ảnh. Khi sửa mô hình, cập nhật cả phần dựng hình và phần nguồn PlantUML tương ứng trước khi chạy lại script. Nếu chỉ sửa `.puml`, giữ bản đó riêng trước khi chạy lại script vì các tệp nguồn sẽ được ghi lại.
