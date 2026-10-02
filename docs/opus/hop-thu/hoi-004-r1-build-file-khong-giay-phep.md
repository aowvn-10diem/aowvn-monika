# Hỏi 004 — R1: build file của bản port không có giấy phép
Bước plan: R1      Hạn cần: không chặn (đã chọn hướng tạm)
Bối cảnh: đã nhận tra-loi-001 (cảm ơn, áp nguyên: sed 3 chỗ + grep kiểm + nm kiểm). Plan R1 nói "không chép Makefile/*.mk của bản port". Viết lại toàn bộ hệ build (Ruby, OpenAL, pixman, SDL*, iconv, openssl…) là rất lớn.
Hướng tạm (đã làm, `.github/workflows/build-rgss.yml`): CI clone đúng commit bản port rồi dựng, KHÔNG chép file build vào repo Monika; vỏ Java do Monika viết lại; ghi nguồn ở `engines/rgss/UPSTREAM.md`.
Câu hỏi: (1) Hướng tạm có thỏa tinh thần "không chép file không giấy phép" không, hay vẫn bắt buộc viết hệ build riêng? (2) Nếu hướng tạm ổn: có cần sếp liên hệ tác giả `thehatkid`/`BookerRues9` xin giấy phép không (ý kiến của PM, việc pháp lý cuối thuộc sếp)?
Đang cân nhắc: A) giữ hướng tạm  B) viết lại build bằng CMake/NDK riêng (nhiều tuần công)  C) hỏi tác giả giấy phép trước
Đã thử và hỏng: chưa.
