require 'tmpdir'
load File.expand_path('../app/src/main/assets/rgss/monika-ruby18.rb', __dir__)
def check(condition, message)
  abort("FAIL #{message}") unless condition
  puts "ok #{message}"
end
# Hình dạng tối thiểu của ba lỗi thật, không tên/hội thoại/script game.
["self.call ('text'.upcase, helper(1))\n", "result = call ('text', helper(1))\n", "thing.call (-1, 2)\n"].each do |source|
  fixed = MonikaRuby18.repair(source)
  check(fixed != source && MonikaRuby18.error_line(fixed).nil?, 'cú pháp gọi hàm cũ compile được')
  check(source.include?(' ('), 'source đầu vào không bị mutate')
end
valid = "puts 'keep (space)' # comment (space)\n"
check(MonikaRuby18.repair(valid).equal?(valid), 'script hợp lệ giữ nguyên object/bytes')
source = "call ('literal (space)', helper(1)) # comment (space)\nnext_call ('second', helper(2))\n"
fixed = MonikaRuby18.repair(source)
check(fixed == "call('literal (space)', helper(1)) # comment (space)\nnext_call('second', helper(2))\n", 'nhiều lỗi: giữ literal/comment và số dòng')
unknown = "def broken(\n"
check(MonikaRuby18.repair(unknown).equal?(unknown), 'lỗi khác không bị sửa hoặc che')
partial = "call ('text', 1)\ndef broken(\n"
check(MonikaRuby18.repair(partial).equal?(partial), 'chỉ nhận khi toàn script compile được, không nhận bản sửa dở')
big = ' ' * (MonikaRuby18::MAX_BYTES + 1)
check(MonikaRuby18.repair(big).equal?(big), 'input quá lớn giữ nguyên trước compile')
Dir.mktmpdir('ruby18-fixture') do |dir|
  path = File.join(dir, 'marker')
  source = "File.write(#{path.inspect}, 'never')\ncall ('a', 1)\n"
  entries = [[1, 'synthetic', 'packed unchanged', source]]
  check(MonikaRuby18.apply(entries) == 1 && !File.exist?(path), 'chỉ compile, không eval hoặc ghi file')
  check(entries[0][2] == 'packed unchanged' && source.include?('call ('), 'không sửa packed data và source gốc')
  check(MonikaRuby18.apply(entries) == 0, 'nạp lại idempotent')
end
check(MonikaRuby18.apply([[1, 'x', 'x', 'call (1, 2)'.freeze].freeze]) == 0, 'mảng frozen giữ nguyên')
budget = [0]
source = 'call (1, 2)'
check(MonikaRuby18.repair(source, budget).equal?(source), 'budget hết giữ nguyên, không compile thêm')
invalid = "\xFF".force_encoding('UTF-8')
check(MonikaRuby18.repair(invalid).equal?(invalid), 'encoding hỏng giữ nguyên, không gây lỗi preload')
puts 'ALL OK'
