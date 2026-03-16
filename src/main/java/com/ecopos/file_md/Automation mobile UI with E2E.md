Thực hiện giúp tôi task dựa trên prompt sau:

Vừa rồi chúng ta đã triển khai testing API cho luồng đơn hàng từ lúc tạo đơn đến lúc hoàn thành đơn hàng E2E. Bây giờ chúng ta sẽ triển khai testing mobile UI cho luồng đơn hàng.

- Nhiệm vụ: (sử dụng app mobile của ecopos)
1. Login vào app mobile (UI login trên app) -> chọn chi nhánh (.env) -> xác nhận
2. CLick tab bar -> đơn online (đơn sàn xuất phát từ đối tác, khách hàng đặt đơn trên App đối tác)
3. Tạo đơn online bằng API (với testcase API push đơn đã tạo)
4. Sau khi push đơn hàng bằng API, full refresh trên app -> verify thông tin đơn online vừa push trên UI (sử dụng phương thức automation mobile lấy dom, xpath... để verify)
Các thông tin verify: 
 - Mã đơn đặt 
 - Mã tham chiếu
 - Mã đơn bán
 - Khách hàng (tên KH)
 - Địa chỉ
 - Ngày tạo
 - Tổng tiền
 - Trạng thái
 - Hành đồng
 Khi click vào đơn hàng đó,bên phải màn hình view ra tab bên phải thông ti chi tiết đơn online
 - Trạng thái
 - Đơn đặt hàng (Mã đơn đặt)
 - Thông tin khách hàng (Họ tên - SĐT (SĐT Mã hóa **** bốn số cuối sđt))
 - Phương thức thanh toán (đang mặc định là chuyển khoản)
 - Thông tin các SP đặt đơn từ API
  + Các tên SP, đơn giá, số lượng, thành tiền)
  + Tổng tiền

* Push đơn bằng API -> lấy request nhận được so sánh với request push đơn từ API, lấy data từ api đã push đơn để so sánh với UI)
5. LAm trước giúp tôi luồng đầu tiên là các test case push đơn hàng
 - Khi push đơn, trên UI hiển thị trạng thái là chờ xác nhận, ở cột hành động có 2 button xác nhận và từ chối
 -  Các thông tin data khác lấy từ data api đã push

