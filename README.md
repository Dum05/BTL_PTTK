# QLST điện máy đề 07

Hai luồng chạy được gồm đăng kí thành viên và nhập hàng từ nhà cung cấp. Trang nhập hàng có tìm hoặc thêm danh mục, lập phiếu nhiều dòng, cập nhật tồn kho và in hóa đơn từ trình duyệt.

## Chạy thử

Cần Java 17 trở lên và Maven 3.6.3 trở lên.

```sh
mvn clean package
java -jar target/qlst-dien-may-1.0.0.jar
```

Mở `http://127.0.0.1:8080`. H2 tự tạo tệp dữ liệu trong thư mục `data` tại thư mục chạy. Các tài khoản dưới đây dùng dữ liệu mô phỏng và cùng mật khẩu `Demo1234`.

| Tài khoản | Quyền |
| --- | --- |
| khachhang | Đăng kí một hồ sơ thành viên |
| khachhang2 | Đăng kí một hồ sơ khác để thử kiểm tra trùng |
| nhanvienkho | Lập phiếu nhập và xem hóa đơn do mình lập |

Tìm nhà cung cấp **An Phát**, mặt hàng **Samsung** hoặc **Tủ lạnh** để dùng dữ liệu mẫu. Để thử thêm mới, tìm theo tên chưa có, mở phần thêm danh mục, nhập và lưu. Danh mục mới có hiệu lực ngay. Chỉ thao tác lưu phiếu mới ghi phiếu, chi tiết và tăng tồn. Muốn thử lại từ đầu, dừng ứng dụng và chạy ở thư mục làm việc mới để ứng dụng khởi tạo một CSDL riêng.

## Cấu trúc

- `web` nhận yêu cầu HTTP, kiểm tra phiên và chọn trang.
- `service` kiểm tra nghiệp vụ và điều phối giao dịch nhập hàng.
- `dao` thực thi truy vấn JDBC tham số hóa.
- `model` chứa các record dùng trong hai module.
- `src/main/resources/templates` chứa trang Thymeleaf, `static/assets` chứa CSS và JavaScript.
- `src/main/resources/schema.sql` khởi tạo CSDL. Hai bảng bán hàng dùng cho lược đồ mở rộng theo phạm vi đề, không có giao diện thực thi.

## Bản dùng trong báo cáo 

Tên lớp Java dùng hậu tố `4`, gồm `ShopController4`, `MemberService4`, `ReceiptService4`, `ShopDao4` và các record trong `Models4`. Mô hình đọc hóa đơn là `GoodsReceiptView4`. Các lớp biên trong thiết kế được hiện thực bằng HTML và JavaScript. Tám bảng dùng tên `tblAccount4`, `tblMemberProfile4`, `tblSupplier4`, `tblProduct4`, `tblGoodsReceipt4`, `tblReceiptLine4`, `tblSalesOrder4`, `tblSalesLine4`.

Khi dùng CSDL cũ, các câu `ALTER TABLE IF EXISTS ... RENAME TO ...` ở đầu `schema.sql` đổi tên tại chỗ, giữ bản ghi, định danh và khóa. Dừng ứng dụng và sao lưu tệp `data/qlst.mv.db` trước khi nâng cấp dữ liệu đang dùng. Đã kiểm thử trên bản sao CSDL v2 và khởi động lặp lại hai lần. Nếu một CSDL đã có cả bảng cũ lẫn bảng mới, cần đối chiếu dữ liệu trước khi hợp nhất; ứng dụng sẽ báo lỗi thay vì tự xóa bảng.

Trong lúc lưu phiếu, toàn bộ vùng chỉnh sửa, các nút Bỏ, thêm dòng, chọn NCC, Hủy và liên kết đầu trang đều bị khóa bằng `inert`. Các bộ xử lí cũng kiểm tra trạng thái lưu. Lỗi yêu cầu mở khóa và giữ nháp. Nút Lưu tạm khóa khi yêu cầu tra/thêm danh mục chưa hoàn tất. Lỗi mạng, HTTP HTML và JSON hỏng có thông báo tiếng Việt.

`ReceiptService4.receive()` dùng `@Transactional`: proxy Spring bắt đầu giao dịch trước thân phương thức, commit sau khi thân hàm trả thành công và rollback khi có ngoại lệ. Service không gọi begin/commit/rollback thủ công.


