# Chuẩn bị PR version/CHANGELOG V102

`python3 scripts/test-version-pr.py` kiểm offline cả plan và GitDB/PR requests bằng client giả.
`version-pr.py --fixture fixture.json --out plan.json` không mạng, không đổi source hay mở PR thật. Fixture gồm repo/head/build/changelog/previous_tag/date/merged_prs, mỗi PR phải có merge_commit_sha đầy đủ và bằng chứng on_main/after_tag; đây chỉ là dữ liệu giả khi chạy test.

Đường thật đọc main đúng SHA, file version/CHANGELOG, tag trùng versionName (peel annotated tag tối đa3), rồi đối chiếu merged PR với ancestry tag→merge→main. Chặn khi range/page thiếu, không có thay đổi, target đã có PR/version header, main đổi hoặc branch bot khác nội dung. Chỉ sửa versionCode +1, versionName patch+1 (hoặc next_version lớn hơn), và CHANGELOG lấy tiêu đề PR + full merge link; nháp cần reviewer rà ý nghĩa người dùng.

Workflow mới chỉ chuẩn bị. PR/push chạy offline. PM có thể dùng workflow_dispatch mặc định dry plan; create_pr=true tạo duy nhất branch bot/version-V-SHA12 và PR **draft**, không tag, release, merge, force hay overwrite. Nếu retry gặp cùng branch/PR hợp lệ thì reuse, cả PR đã đóng cũng không mở lại. GitDB commit có đúng1 parent main và verify tree/parents trước create_ref. Sol chưa kích hoạt/chạy apply.

Workflow GITHUB_TOKEN luôn chỉ đọc; đường tạo PR dùng token riêng của Environment, cần CI độc lập theo đúng head. Vì vậy luôn nháp, chỉ PM xử lý CI độc lập và review Luna3→Ultra exact head trước gộp. Script không tạo token hay nới quyền/repo settings. API cap180, tối đa40 PR trong range/5 trang100, phải đọc tới trang cuối; vượt cap dừng, không tự tuyên bố đã đủ lịch sử. Phiên bản V99 Sonnet đang có sẽ chặn kế hoạch v0.8.3 trùng nếu chạy trên nguồn0.8.2.

Cổng runtime V102: dispatch plan/create chỉ `refs/heads/main`, checkout đúng `github.sha` từ main. Quyền ghi không nằm trong GITHUB_TOKEN ở bất kỳ job nào. Trước dùng token riêng, Environment `version-pr-main` phải có custom deployment branch policy duy nhất `main` (type branch, không wildcard/tag), required reviewers và prevent_self_review=true; script kiểm metadata bằng token chỉ đọc và dừng nếu thiếu/sai. Policy do GitHub scheduler áp ngoài ref dispatch: nhánh khác không nhận được secret này dù sửa YAML/if.

Owner cần cấu hình Environment trên và đặt `VERSION_PR_TOKEN` **chỉ ở Environment**, không đặt secret cùng tên cấp repo/org; quyền token tối thiểu tạo branch/PR cho repo này, không release/signing/settings. Environment/secret hiện [CHƯA KIỂM]; Sol chỉ chuẩn bị source, không tạo/cấu hình Environment, đọc giá trị token hay chạy workflow thật. Nếu chưa cấu hình/đọc metadata được thì BLOCKED. Local --apply chỉ cho operator được phép từ checkout main đã duyệt.
