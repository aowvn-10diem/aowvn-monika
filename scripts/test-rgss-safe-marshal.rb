# Kiểm bộ đọc Marshal an toàn (scripts/rgss_safe_marshal.rb). Chạy: ruby scripts/test-rgss-safe-marshal.rb
require 'zlib'
require_relative 'rgss_safe_marshal'

def check(cond, msg); abort("FAIL: #{msg}") unless cond; puts "ok  #{msg}"; end
def rejects(bytes, msg)
  SafeMarshal.new(bytes).read
  abort("FAIL: phải từ chối: #{msg}")
rescue RuntimeError, ArgumentError, NoMemoryError, SystemStackError => e
  puts "ok  từ chối #{msg} (#{e.class})"
end

# Hợp lệ: đúng dạng Scripts.rvdata (id, tiêu đề UTF-8/Shift_JIS/ASCII-8BIT, mã nén).
mk = -> { Zlib::Deflate.deflate("puts 1\n") } # mỗi script một chuỗi riêng (Marshal tạo liên kết '@' nếu dùng chung một đối tượng)
code = mk.call
good = [[1, 'Main', mk.call], [2, "Th\u1EED".encode('UTF-8'), mk.call], [300, 'x'.encode('Shift_JIS'), mk.call], [70000, 'z'.b, mk.call]]
back = SafeMarshal.new(Marshal.dump(good)).read
check back.size == 4 && back[0][0] == 1 && back[0][1] == 'Main' && back[2][0] == 300 && back[3][0] == 70000 && back[3][2] == code.b, 'đọc đúng mảng id/tiêu đề/mã (cả mã hóa lạ, số lớn)'

# Từ chối: đối tượng tùy ý, Hash, Symbol ở vị trí giá trị, Float, kiểu 'o'.
rejects Marshal.dump([[1, Object.new, 'x']]), 'đối tượng tùy ý'
rejects Marshal.dump({ a: 1 }), 'Hash'
rejects Marshal.dump([:sym]), 'Symbol'
rejects Marshal.dump(1.5), 'Float'
rejects "\x04\x08".b, 'cụt'
rejects "\x03\x00[".b, 'sai phiên bản'

# Tệp hỏng/độc: mảng khai báo cực lớn, lồng quá sâu, quá nhiều phần tử.
rejects "\x04\x08[\x04\xff\xff\xff\x7f".b, 'mảng khai báo 2 tỉ phần tử'
deep = "\x04\x08".b + ("[\x06".b * 20) + '0'.b
rejects deep, 'lồng 20 cấp'
same = 'a'; rejects Marshal.dump([same, same]), "liên kết đối tượng '@' (không có trong Scripts.*data thật)"
big = Marshal.dump(Array.new(250_000) { 0 })
rejects big, '250.000 phần tử'
puts 'ALL OK'
