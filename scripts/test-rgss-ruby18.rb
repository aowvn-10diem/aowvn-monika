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
  check(MonikaRuby18.applied_count == 1, 'nativefixture đọc được số script sửa, không dựa stdout')
  check(entries[0][2] == 'packed unchanged' && source.include?('call ('), 'không sửa packed data và source gốc')
  check(MonikaRuby18.apply(entries) == 0, 'nạp lại idempotent')
end
check(MonikaRuby18.apply([[1, 'x', 'x', 'call (1, 2)'.freeze].freeze]) == 0, 'mảng frozen giữ nguyên')
budget = [0]
source = 'call (1, 2)'
check(MonikaRuby18.repair(source, budget).equal?(source), 'budget hết giữ nguyên, không compile thêm')
invalid = "\xFF".force_encoding('UTF-8')
check(MonikaRuby18.repair(invalid).equal?(invalid), 'encoding hỏng giữ nguyên, không gây lỗi preload')
many = ("call ('a', 1)\n" * 24)
fixed = MonikaRuby18.repair(many)
check(fixed != many && MonikaRuby18.error_line(fixed).nil?, '24lỗi cần hơn16vòng nhưng vẫn trong budget')
lines = "\n" * (MonikaRuby18::MAX_LINES + 1) + 'call (1, 2)'
check(MonikaRuby18.repair(lines).equal?(lines), 'quá20nghìndòng bị chặn trước tách lines')
source = "result = first ('a', 1) + second ('b', 2)\n"
check(MonikaRuby18.repair(source) == "result = first('a', 1) + second('b', 2)\n", 'hai lời gọi lỗi cùng dòng cần sửa cùng nhau')
source = "result = first ('a', 1) + second ('b', 2) + third ('c', 3) # keep (space)\n"
check(MonikaRuby18.repair(source) == "result = first('a', 1) + second('b', 2) + third('c', 3) # keep (space)\n", 'tập nhỏ nhất ba lời gọi giữ khoảng trắng trong comment')
budget = [3]
check(MonikaRuby18.repair(source, budget).equal?(source) && budget[0] == 0, 'hết budget giữa tìm tổ hợp trả nguyên source')
source = "result = first ('literal (space)', 1) + second ('b', 2)\ndef broken(\n"
check(MonikaRuby18.repair(source).equal?(source), 'tổ hợp tiến triển nhưng lỗi khác vẫn trả nguyên toàn script')
source = "result = first ('a', 1) + second ('b', 2)\n"
budget = [512, source.bytesize]
trace = []
check(MonikaRuby18.repair(source, budget, ->(*args) { trace << args }).equal?(source) && budget == [511, 0] && trace.empty?, 'cạn byte ngay sau compile gốc chặn candidate trước cấp phát')
budget = [512, source.bytesize * 3]
check(MonikaRuby18.repair(source, budget).equal?(source) && budget[1] >= 0 && budget[0] == 509, 'cạn byte giữa tổ hợp giữ source, dù còn lượt compile')
source = "call ('a', 1)\n"
fixed_size = source.bytesize - 1
budget = [512, source.bytesize + fixed_size]
fixed = MonikaRuby18.repair(source, budget)
check(fixed == "call('a', 1)\n" && budget[1] == 0 && MonikaRuby18.repair(source, budget).equal?(source), 'byte budget dùng chung qua nhiều script, không reset sau sửa')
check(MonikaRuby18.error_line('a' * (MonikaRuby18::MAX_COMPILE_BYTES + 1), [512]) == 0, 'compile chặn input vượt trần byte trước RubyVM')
source = "class ParentFixture\n def forward(arg, *rest); [arg, rest]; end\nend\nclass ChildFixture < ParentFixture\n def forward(arg, *rest)\n  super (arg, *rest)\n end\nend\n"
fixed = MonikaRuby18.repair(source)
check(fixed.include?('super(arg, *rest)') && MonikaRuby18.error_line(fixed).nil?, 'super với danh sách/splat Ruby1.8 compile được')
# Chỉ fixture tổng hợp trong test được thực thi; repair/app vẫn chỉ compile.
eval(fixed)
check(ChildFixture.new.forward(1, 2, 3) == [1, [2, 3]], 'super giữ giá trị/thứ tự/splat khi chuyển tiếp đối số')
source = "class ChildFixture < ParentFixture\n def forward(arg, *rest); super(arg, *rest); end\nend\n"
check(MonikaRuby18.repair(source).equal?(source), 'super hợp lệ không bị normalize')
source = "call ('a', 1)\n" * 80
check(MonikaRuby18.error_line(MonikaRuby18.repair(source)).nil?, '80lời gọi lỗi vượt trần64 cũ vẫn dùng budget chung')
source = "call ('a', 1)\n" * (MonikaRuby18::MAX_REPAIRS + 1)
trace = []
check(MonikaRuby18.repair(source, [512], ->(*args) { trace << args }).equal?(source) && trace.last[1] == -1, 'vượt128vòng giữ nguyên toàn source và ghi đúng lý do giới hạn')
puts 'ALL OK'
