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
