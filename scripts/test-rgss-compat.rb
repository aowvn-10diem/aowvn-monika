# Kiểm bản giả Win32API (app/src/main/assets/rgss/monika-win32api.rb) bằng Ruby thường. Chạy: ruby scripts/test-rgss-compat.rb
module Graphics; def self.width; 544; end; def self.height; 416; end; end
load File.expand_path('../app/src/main/assets/rgss/monika-win32api.rb', __dir__)

def check(cond, msg); abort("FAIL: #{msg}") unless cond; puts "ok  #{msg}"; end

check Win32API.new('user32', 'GetAsyncKeyState', 'i', 'i').call(0x01) == 0, 'GetAsyncKeyState = 0 (không có MiniFFI)'
check Win32API.new('User32.dll', 'FindWindow', 'pp', 'l').call('RGSS Player', 'x') == 1, 'FindWindow (user32.dll) = 1'
buf = [0, 0, 0, 0].pack('l4')
Win32API.new('user32', 'GetWindowRect', 'lp', 'i').call(1, buf)
check buf.unpack('l4') == [0, 0, 544, 416], 'GetWindowRect điền cỡ cửa sổ'
pt = [9, 9].pack('l2')
Win32API.new('user32', 'GetCursorPos', 'p', 'i').call(pt)
check pt.unpack('l2') == [0, 0], 'GetCursorPos điền (0,0)'
check Win32API.new('user32', 'GetSystemMetrics', 'i', 'i').call(1) == 480, 'GetSystemMetrics(SM_CYSCREEN)'
check Win32API.new('kernel32', 'GetPrivateProfileString', 'pppplp', 'l').call('a', 'b', '', '', 0, 'x') == 0, 'hàm lạ = 0'
check Win32API.new('winmm', 'timeGetTime', '', 'l').call.is_a?(Integer), 'timeGetTime là số nguyên'
# DLL ngoài danh sách Windows mà MiniFFI không có/không mở được: ném lỗi như cũ (không che lỗi thật).
begin
  Win32API.new('mylib', 'Foo', 'i', 'i'); check false, 'DLL lạ phải ném lỗi'
rescue LoadError
  check true, 'DLL lạ không có MiniFFI ném lại lỗi gốc'
end
# MiniFFI chạy được → dùng hàng thật, không giả.
class MiniFFI; def initialize(*); end; def call(*a); 42; end; end
check Win32API.new('user32', 'GetAsyncKeyState', 'i', 'i').call(1) == 42, 'MiniFFI chạy được thì dùng hàm thật'
class MiniFFI; def initialize(d, *); raise 'dlopen failed' if d == 'user32'; end; end
check Win32API.new('user32', 'GetAsyncKeyState', 'i', 'i').call(1) == 0, 'MiniFFI lỗi + DLL Windows → bản giả'
puts 'ALL OK'
