# V44: diagnostic từ game phải giữ vị trí lỗi nhưng không lộ literal/tiêu đề/tên Unicode.
require 'tmpdir'
require 'open3'
require 'rbconfig'
require 'zlib'

def check(condition, message)
  abort("FAIL: #{message}") unless condition
  puts "ok #{message}"
end

script = File.expand_path('rgss-script-shape.rb', __dir__)
Dir.mktmpdir('shape-fixture') do |dir|
  input = File.join(dir, 'Scripts.rvdata')
  source = "def secret_method(\n  puts \"#hidden_literal\"\n  tên_riêng = 'private_value'\n)\nend\n"
  File.binwrite(input, Marshal.dump([[1, 'PrivateTitle', Zlib::Deflate.deflate(source)]]))
  out, err, status = Open3.capture3(RbConfig.ruby, script, input)
  check(status.success?, 'lỗi cú pháp được chẩn đoán, không chạy script')
  check(out.include?('SyntaxError tại dòng') && out.include?('Tổng số script: 1; lỗi cú pháp: 1'), 'giữ vị trí và tổng số lỗi')
  check(%w[secret_method hidden_literal private_value PrivateTitle tên_riêng].none? { |word| (out + err).include?(word) }, 'không lộ identifier/literal/tiêu đề Unicode')

  File.binwrite(input, Marshal.dump([[2, 'AllowedTitle', Zlib::Deflate.deflate("puts 'not_executed'\n")]]))
  out, _, status = Open3.capture3(RbConfig.ruby, script, input)
  check(status.success? && out.include?('lỗi cú pháp: 0') && !out.include?('not_executed'), 'script hợp lệ chỉ compile, không eval/lộ chuỗi')

  File.open(input, 'wb') { |f| f.truncate(65 * 1024 * 1024) }
  _, _, status = Open3.capture3(RbConfig.ruby, script, input)
  check(!status.success?, 'input quá lớn bị chặn trước đọc')
end
