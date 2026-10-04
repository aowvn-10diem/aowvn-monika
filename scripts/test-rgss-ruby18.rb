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
kernel_eval = Kernel.instance_method(:eval)
source = <<~'RUBY'
  class CallsiteEvalFixture
    OFFSET = 17
    for name in ['first', 'second']
      eval("def #{name}(arg, *rest); collect (arg, *rest); end")
    end
    def collect(arg, *rest); [arg + OFFSET, rest]; end
    def local_value
      value = 23
      eval('value + OFFSET')
    end
    def explicit_context
      value = 9
      eval("value = add (value, 1); [__FILE__, __LINE__, value]", binding, 'fixture-context', 40)
    end
    def add(a, b); a + b; end
  end
RUBY
entries = [[1, 'synthetic', 'packed', source]]
check(MonikaRuby18.apply(entries) == 1 && entries[0][2] == 'packed', 'bọc đối số eval trong RAM, giữ packed data/source đầu vào')
eval(entries[0][3]) # Chỉ fixture tổng hợp; preparer trong app không eval.
fixture = CallsiteEvalFixture.new
check(fixture.first(2, 3, 4) == [19, [3, 4]] && fixture.second(5) == [22, []], 'eval sinh method giữ lớp/constant/splat của caller')
check(fixture.local_value == 40, 'eval mặc định giữ lexical locals và constant lookup')
check(fixture.explicit_context == ['fixture-context', 40, 10], 'eval explicit Binding/filename/line giữ nguyên')
check(Kernel.instance_method(:eval) == kernel_eval, 'không override Kernel.eval')
check(MonikaRuby18.apply(entries) == 0, 'callsite đã bọc không bị bọc lặp')
source = "đặt = 13; eval('đặt')\n"
fixed = MonikaRuby18.instrument_eval_calls(source, [512])
check(eval(fixed) == 13 && fixed.valid_encoding?, 'range AST theo byte giữ identifier UTF8 và local context')
source = "text = 'eval(\"keep\")' # eval(keep)\nobject.eval(text)\n"
check(MonikaRuby18.instrument_eval_calls(source, [512]).equal?(source), 'không sửa literal/comment/eval của receiver khác')
source = "eval(\"call (1,\n2)\")\n"
check(MonikaRuby18.instrument_eval_calls(source, [512]).equal?(source), 'đối số nhiều dòng/heredoc không bị đoán range')
source = "eval(nil)\n"
fixed = MonikaRuby18.instrument_eval_calls(source, [512])
begin
  eval(fixed)
  abort('FAIL eval(nil) phải giữ TypeError')
rescue TypeError
  check(true, 'eval nonstring giữ TypeError, không che lỗi')
end
source = "eval('1')\n" * (MonikaRuby18::MAX_EVAL_SITES + 1)
check(MonikaRuby18.instrument_eval_calls(source, [512]).equal?(source), 'quá64callsite giữ nguyên trước ghép candidate')
source = "a=1+2+3+4+5+6\n" * 6000 + "eval('1')\n"
check(MonikaRuby18.instrument_eval_calls(source, [512]).equal?(source), 'quá50nghìn ASTnode giữ nguyên trước ghép candidate')
source = "eval('1')\n"
budget = [512, 0]
check(MonikaRuby18.instrument_eval_calls(source, budget).equal?(source) && budget == [512, 0], 'AST parse cũng bị chặn bởi byte budget chung')
source = "eval('1')\n#" + ' ' * MonikaRuby18::MAX_AST_BYTES
budget = [512, MonikaRuby18::MAX_COMPILE_BYTES]
check(MonikaRuby18.instrument_eval_calls(source, budget).equal?(source) && budget[0] == 512, 'AST parse có trần256KiB trước tạo cây, không chỉ đếm node sau parse')
Dir.mktmpdir('eval-fixture') do |dir|
  path = File.join(dir, 'never')
  source = "eval(#{"File.write(#{path.inspect}, 'never')".inspect})\n"
  entries = [[1, 'synthetic', 'packed', source]]
  check(MonikaRuby18.apply(entries) == 1 && !File.exist?(path), 'instrument chỉ parse/compile, không thực thi eval hay ghi file')
end
MonikaRuby18.apply([])
source = "call ('cache', 1)\n"
fixed = MonikaRuby18.eval_source(source)
MonikaRuby18.instance_variable_set(:@eval_budget, [0, 0])
cached = MonikaRuby18.eval_source(source.dup)
check(cached == fixed && !cached.frozen?, 'cache sửa thành công tránh compile lại khi budget đã cạn')
source.replace("call ('changed', 2)\n")
check(MonikaRuby18.eval_source(source).equal?(source), 'cache key không đổi theo String caller mutate, không dùng bản stale')
MonikaRuby18.apply([])
40.times { |i| MonikaRuby18.eval_source("call ('#{i}', 1)\n") }
check(MonikaRuby18.instance_variable_get(:@eval_cache).size == MonikaRuby18::MAX_EVAL_CACHE_ENTRIES && MonikaRuby18.instance_variable_get(:@eval_cache_bytes) <= MonikaRuby18::MAX_EVAL_CACHE_BYTES, 'cache runtime giữ trần32mục/64KiB')
MonikaRuby18.apply([])
source = "call ('a', 1) #" + ' ' * (MonikaRuby18::MAX_EVAL_CACHE_BYTES / 2)
check(MonikaRuby18.eval_source(source) != source && MonikaRuby18.instance_variable_get(:@eval_cache).empty?, 'entry cache quá64KiB vẫn sửa nhưng không giữ RAM')
MonikaRuby18.apply([])
source = <<~'RUBY'
  class EvalOrderFixture
    def source_text; @order << :source; 'value'; end
    def context(target); @order << :binding; target; end
    def check_order
      @order = []
      value = 11
      result = eval(source_text, context(binding))
      [result, @order]
    end
    def add(a, b); a + b; end
    def nested
      eval(eval('"add (1, 2)"'))
    end
  end
RUBY
fixed = MonikaRuby18.instrument_eval_calls(source, [512])
eval(fixed)
check(fixed.include?('::MonikaRuby18.eval_source(source_text)') && EvalOrderFixture.new.check_order == [11, [:source, :binding]], 'đối số được tính đúng một lần/thứ tự, Binding caller giữ nguyên')
check(EvalOrderFixture.new.nested == 3, 'eval lồng nhau giữ dấu ngoặc và ngữ cảnh caller')
source = "eval('def broken(')\n"
begin
  eval(MonikaRuby18.instrument_eval_calls(source, [512]))
  abort('FAIL eval lỗi khác phải ném SyntaxError')
rescue SyntaxError
  check(true, 'lỗi cú pháp runtime ngoài phạm vi vẫn ném nguyên lỗi')
end
# Accessor collection lỗi receiver trong class body; chỉ fixture tổng hợp.
source = <<~'RUBY'
  class AccessorCollectionFixture
    VALUE = 7
    LABEL = 'synthetic'
    for name in self.class.constants
      attr_accessor name.downcase.to_sym
    end
    def initialize
      self.class.constants.each { |name| send("#{name.downcase}=", self.class.const_get(name)) }
    end
  end
RUBY
eval(source)
begin
  AccessorCollectionFixture.new
  abort('FAIL fixture gốc phải thiếu setter')
rescue NoMethodError
  check(true, 'fixture gốc tái hiện setter thiếu do self.class.constants ở class body')
end
Object.send(:remove_const, :AccessorCollectionFixture)
entries = [[1, 'synthetic', 'packed unchanged', source]]
check(MonikaRuby18.apply(entries) == 1 && !Object.const_defined?(:AccessorCollectionFixture, false) && entries[0][3].include?('::MonikaRuby18.accessor_constants(self,self.class.constants)'), 'bọc đúng collection FOR/attr_accessor trong bản RAM')
check(entries[0][2] == 'packed unchanged' && !source.include?('MonikaRuby18'), 'không sửa input/packed data của collection')
eval(entries[0][3])
fixture = AccessorCollectionFixture.new
check(fixture.value == 7 && fixture.label == 'synthetic', 'accessor thật giữ giá trị constant và setter/getter, không stub lỗi')
check(MonikaRuby18.apply(entries) == 0, 'accessor collection đã bọc không sửa lặp')
Object.send(:remove_const, :AccessorCollectionFixture)
unsupported = [
  "class AccessorUnsupported; VALUE=1; for field in self.class.constants; attr_accessor field.downcase.to_sym; puts 'side effect'; end; end",
  "class AccessorUnsupported; VALUE=1; def later; for field in self.class.constants; attr_accessor field.downcase.to_sym; end; end; end",
  "class AccessorUnsupported; VALUE=1; for field in other.class.constants; attr_accessor field.downcase.to_sym; end; end",
  "class AccessorUnsupported; VALUE=1; for field in self.class.constants; attr_accessor other.downcase.to_sym; end; end",
  "module AccessorUnsupported; VALUE=1; for field in self.class.constants; attr_accessor field.downcase.to_sym; end; end",
  "class AccessorUnsupported; VALUE=1; class << self; for field in self.class.constants; attr_accessor field.downcase.to_sym; end; end; end"
]
check(unsupported.all? { |text| MonikaRuby18.instrument_eval_calls(text, [512]).equal?(text) }, 'không sửa method/module/singleton/custom receiver/khác biến/vòng nhiều tác dụng phụ')
original = []
owner = Class.new
owner.const_set(:VALUE, 7)
check(MonikaRuby18.accessor_constants(owner, original) == [:VALUE], 'collection trống ở Class fallback constants của lớp đang khai báo')
check(MonikaRuby18.accessor_constants(owner, [:EXISTING]) == [:EXISTING] && MonikaRuby18.accessor_constants(owner.new, original).equal?(original), 'collection không trống hoặc receiver instance giữ nguyên')
custom = Class.new
custom.const_set(:VALUE, 7)
def custom.class; Class; end
check(MonikaRuby18.accessor_constants(custom, original).equal?(original), 'không thay hành vi class getter do game override')
custom = Class.new
def custom.constants; [:VALUE]; end
check(MonikaRuby18.accessor_constants(custom, original).equal?(original), 'không gọi constants getter do game override')
huge = Class.new
(MonikaRuby18::MAX_ACCESSOR_CONSTANTS + 1).times { |i| huge.const_set("FIELD_#{i}", i) }
check(MonikaRuby18.accessor_constants(huge, original).equal?(original), 'quá256constants không cấp thêm collection accessor')
long = Class.new
long.const_set('A' * 129, 1)
check(MonikaRuby18.accessor_constants(long, original).equal?(original), 'constant name quá128byte giữ nguyên collection')
text = "class AccessorErrorFixture; VALUE=7; for name in self.class.constants; attr_accessor name.downcase.to_sym; end; def fail_unknown; absent_method(); end; end"
eval(MonikaRuby18.instrument_eval_calls(text, [512]))
begin
  AccessorErrorFixture.new.fail_unknown
  abort('FAIL NoMethodError khác phải giữ')
rescue NoMethodError
  check(true, 'NoMethodError khác vẫn ném, không global method_missing/Class.constants patch')
end
Object.send(:remove_const, :AccessorErrorFixture)
text = source
budget = [512, text.bytesize]
check(MonikaRuby18.instrument_eval_calls(text, budget).equal?(text) && budget[1] == 0, 'collection dùng cùng AST/byte budget, cạn trước ghép giữ nguyên source')
# Valid Ruby3 source nhưng ternary bị nuốt vào predicate argument khác Ruby1.8.
# Chỉ dữ liệu tổng hợp; không đọc/chạy script game.
source = "def predicate_fixture(value); value.is_a? (Integer) ? 100..value : value; end\n"
eval(source)
original_range = (100..120)
begin
  predicate_fixture(original_range)
  abort('FAIL fixture spaced phải tái hiện bad Range')
rescue ArgumentError => error
  check(error.message == 'bad value for range', 'fixture valid cú pháp tái hiện Range bị đưa vào predicate argument')
end
fixed = MonikaRuby18.instrument_eval_calls(source, [512])
check(fixed == source.sub('is_a? (', 'is_a?('), 'chỉ xóa spacing của CALL/predicate/CONST/ternary đã nhận bằng AST')
eval(fixed)
check(predicate_fixture(original_range).equal?(original_range) && predicate_fixture(120) == (100..120), 'Range caller giữ object, Integer tạo Range đúng kiểu; không coerce')
check(MonikaRuby18.instrument_eval_calls(fixed, [512]).equal?(fixed), 'predicate normalization idempotent')
source = "value = (100...120); value.kind_of? \t(Integer) ? 100..value : value\n"
fixed = MonikaRuby18.instrument_eval_calls(source, [512])
result = eval(fixed)
check(fixed.include?('kind_of?(Integer)') && result == (100...120) && result.exclude_end?, 'kind_of? và SPACE/TAB giữ Range exclusive')
source = "tên = 120; tên.is_a? (Integer) ? 100..tên : tên # keep (space)\n"
fixed = MonikaRuby18.instrument_eval_calls(source, [512])
check(fixed.include?('tên.is_a?(Integer)') && fixed.end_with?("# keep (space)\n") && eval(fixed) == (100..120), 'byte offsets giữ UTF8/literal/comment của predicate')
source = "value=120; value.is_a? (Integer) ? eval('100..value') : value\n"
fixed = MonikaRuby18.instrument_eval_calls(source, [512])
check(fixed.include?('is_a?(Integer)') && fixed.include?('::MonikaRuby18.eval_source(') && eval(fixed) == (100..120), 'spacing và eval edit dùng chung một AST pass, không overlap')
unsupported = [
  "value.is_a?(Integer) ? 100..value : value",
  "value.is_a? (Integer)",
  "value.other? (Integer) ? 100..value : value",
  "value.is_a? (custom) ? 100..value : value",
  "value.is_a? (true) ? 100..value : value",
  "value.is_a? (::Integer) ? 100..value : value",
  "value.is_a? ((Integer)) ? 100..value : value",
  "value.is_a? (Integer) ?\n 100..value : value",
  "text='value.is_a? (Integer) ? a : b' # value.is_a? (Integer) ? a : b"
]
check(unsupported.all? { |text| MonikaRuby18.instrument_eval_calls(text, [512]).equal?(text) }, 'predicate không sửa method khác/constant phức tạp/multiline/đã đúng/literal/comment')
source = "value.is_a? (Integer) ? 100..value : value\n"
budget = [512, source.bytesize]
check(MonikaRuby18.instrument_eval_calls(source, budget).equal?(source) && budget == [511, 0], 'predicate không refill byte budget và giữ source khi thiếu lượt compile cuối')
many = source * (MonikaRuby18::MAX_EVAL_SITES + 1)
check(MonikaRuby18.instrument_eval_calls(many, [512]).equal?(many), 'quá64predicate sites giữ nguyên toàn script')
source = "def invalid_predicate(; value.is_a? (Integer) ? a : b; end\n"
check(MonikaRuby18.instrument_eval_calls(source, [512]).equal?(source), 'predicate với lỗi parser khác không sửa dở hoặc nuốt SyntaxError')
source = <<~'RUBY'
  class PredicateOrderFixture
    attr_reader :seen
    def is_a?(type); @seen = type; false; end
  end
  calls=0; object=PredicateOrderFixture.new
  take = -> { calls += 1; object }
  result = take.call.is_a? (Integer) ? :wrong : :kept
  [calls, object.seen, result]
RUBY
check(eval(MonikaRuby18.instrument_eval_calls(source, [512])) == [1, Integer, :kept], 'predicate caller/core override giữ đúng đối số/thứ tự/một lần và nhánh false')
source = "value=120; value.is_a? (Integer) ? absent_method() : value\n"
begin
  eval(MonikaRuby18.instrument_eval_calls(source, [512]))
  abort('FAIL lỗi khác phải giữ')
rescue NoMethodError
  check(true, 'predicate normalization không che runtime error khác')
end
# Comparison sau slice! là kết quả, không phải argBoolean (Ruby1.8 vs3.1).
source = "text='abc'; result=text.slice! (/a/) != nil; [result,text]\n"
begin
  eval(source)
  abort('FAIL spaced slice comparison phải tái hiện TypeError')
rescue TypeError => error
  check(error.message == 'no implicit conversion of true into Integer', 'slice fixture tái hiện Boolean bị đưa vào argument')
end
fixed = MonikaRuby18.instrument_eval_calls(source, [512])
check(fixed == source.sub('slice! (', 'slice!(') && eval(fixed) == [true,'bc'], 'slice Regexp so sánh nil đúng kết quả và mutation của caller')
check(MonikaRuby18.instrument_eval_calls(fixed, [512]).equal?(fixed), 'slice comparison idempotent')
source = "text='abc'; result=text.slice! \t(/z/i) == nil; [result,text]\n"
check(eval(MonikaRuby18.instrument_eval_calls(source, [512])) == [true,'abc'], 'slice không match/regexpflags/SPACE-TAB và ==nil giữ text')
source = "text='abc'; result=text.slice! (%r{a}) != nil; [result,text]\n"
check(eval(MonikaRuby18.instrument_eval_calls(source, [512])) == [true,'bc'], 'Regexp literal percent giữ nội dung và delimiter')
source = "tên='abc'; result=tên.slice! (/a/) != nil; [result,tên] # keep (space)\n"
fixed = MonikaRuby18.instrument_eval_calls(source, [512])
check(eval(fixed) == [true,'bc'] && fixed.end_with?("# keep (space)\n"), 'slice byteoffsets giữ Unicode/comment/literal')
unsupported = [
  "text.slice!(/a/) != nil", "text.slice! (/a/)",
  "text.slice! (pattern) != nil", "text.slice! ('a') != nil",
  "text.slice! (/a/) != false", "text.slice! ((/a/)) != nil",
  "text.other! (/a/) != nil", "text.slice! (/a/, 1) != nil",
  "text.slice! (/a/) !=\n nil", "text='slice! (/a/) != nil'"
]
check(unsupported.all? { |text| MonikaRuby18.instrument_eval_calls(text,[512]).equal?(text) }, 'slice không sửa dynamic/multiplearg/methodkhác/khôngnil/multiline/literal/đã đúng')
source = "text.slice! (/a/) != nil\n"
budget=[512,source.bytesize]
check(MonikaRuby18.instrument_eval_calls(source,budget).equal?(source) && budget==[511,0], 'slice dùng chung bytebudget, thiếu compile cuối giữ source')
many=source*65
check(MonikaRuby18.instrument_eval_calls(many,[512]).equal?(many), 'slice quá64sites giữ nguyên')
source="text='abc'; result=text.slice! (/a/) != nil; eval('text')\n"
fixed=MonikaRuby18.instrument_eval_calls(source,[512])
check(eval(fixed)=='bc' && fixed.include?('::MonikaRuby18.eval_source('), 'slice và eval cùng ASTpass giữ thứ tự/tác dụng phụ')
source=<<~'RUBY'
  class SliceOrderFixture
    attr_reader :seen
    def slice!(pattern); @seen=pattern; :ok; end
  end
  count=0; obj=SliceOrderFixture.new; take=->{count+=1;obj}
  result=take.call.slice! (/a/) != nil
  [count,obj.seen,result]
RUBY
result=eval(MonikaRuby18.instrument_eval_calls(source,[512]))
check(result[0]==1 && result[1]==/a/ && result[2]==true, 'slice custommethod nhận Regexp/một lần, không gọi wrappercoerce')
# Class.constants của collection gốc cũng phải là method core chưa bị game thay.
singleton = Class.singleton_class
original_method = Class.method(:constants)
own_method = singleton.instance_methods(false).include?(:constants)
begin
  singleton.send(:define_method, :constants) { [] }
  owner = Class.new
  owner.const_set(:VALUE, 7)
  original = []
  check(MonikaRuby18.accessor_constants(owner, original).equal?(original), 'collection gốc Class.constants custom override giữ nguyên, không fallback')
  isolated = Module.new
  isolated.module_eval(File.read(File.expand_path('../app/src/main/assets/rgss/monika-ruby18.rb', __dir__)))
  check(isolated.const_get(:MonikaRuby18).accessor_constants(owner, original).equal?(original), 'Class.constants override trước preload cũng không được coi là core')
ensure
  singleton.send(:remove_method, :constants)
  singleton.send(:define_method, :constants, original_method) if own_method
end
check(Class.method(:constants) == original_method, 'test khôi phục method Class.constants gốc')
original_method = Module.instance_method(:constants)
begin
  Module.send(:define_method, :constants) { [:CUSTOM] }
  isolated = Module.new
  isolated.module_eval(File.read(File.expand_path('../app/src/main/assets/rgss/monika-ruby18.rb', __dir__)))
  original = []
  check(isolated.const_get(:MonikaRuby18).accessor_constants(Class.new, original).equal?(original), 'Module constants override trước preload giữ collection gốc')
ensure
  Module.send(:define_method, :constants, original_method)
end
check(Module.instance_method(:constants) == original_method, 'test khôi phục Module constants gốc')
# A literal argument followed by width binds to the CALL result in Ruby1.8.
width_fixture = <<~'RUBY'
  class LiteralWidthResultFixture
    def width; 42; end
  end
  class LiteralMeasureFixture
    def measure(text); LiteralWidthResultFixture.new; end
  end
RUBY
source = width_fixture + "LiteralMeasureFixture.new.measure (\" \" ).width\n"
begin
  eval(source)
  abort('FAIL literal width spacing phải tái hiện NoMethodError')
rescue NoMethodError => error
  check(error.name == :width && error.receiver == ' ', 'literal getter fixture tái hiện width gọi trên String argument')
end
fixed = MonikaRuby18.instrument_eval_calls(source,[512])
check(fixed == source.sub('measure (','measure(') && eval(fixed) == 42, 'literal getter xóa chỉ SPACE, lấy width của kết quả CALL không hardcode method')
check(MonikaRuby18.instrument_eval_calls(fixed,[512]).equal?(fixed), 'literal result normalization idempotent')
source = width_fixture + "LiteralMeasureFixture.new.measure \t('literal').width\n"
check(eval(MonikaRuby18.instrument_eval_calls(source,[512])) == 42, 'literal result giữ String đơn và SPACE/TAB')
source = width_fixture + "đo=LiteralMeasureFixture.new; đo.measure (\"escaped\\nvalue\").width # unchanged\n"
fixed = MonikaRuby18.instrument_eval_calls(source,[512])
check(eval(fixed) == 42 && fixed.end_with?("# unchanged\n"), 'literal getter byteoffsets giữ UTF8/escapedliteral/comment')
unsupported = [
  'measure.call(" ").width', 'measure.call (value).width',
  'measure.call ("#{value}").width', 'measure.call ((" ")).width',
  'measure.call (" ",1).width', 'measure.call (" ").height',
  'measure.call (" ").width == 42',
  'measure.call (" ").width(1)', 'measure.call (" ").width()',
  "measure.call (\" \" )\n.width", 'call (" ").width',
  'measure.call (1).width', 'source="measure.call (space).width"'
]
check(unsupported.all? { |text| MonikaRuby18.instrument_eval_calls(text,[512]).equal?(text) }, 'literal getter giữ dynamic/multipleargs/nestedparen/methodargs/getterkhác/multiline/literal/FCALL')
source = "measure.call (\" \" ).width\n"
budget = [512,source.bytesize]
check(MonikaRuby18.instrument_eval_calls(source,budget).equal?(source) && budget == [511,0], 'literal getter không refill budget, thiếu compile cuối giữ source')
many = source*65
check(MonikaRuby18.instrument_eval_calls(many,[512]).equal?(many), 'literal getter quá64sites giữ source')
source = width_fixture + "value=LiteralMeasureFixture.new.measure (\" \" ).width; eval('value')\n"
fixed = MonikaRuby18.instrument_eval_calls(source,[512])
check(eval(fixed) == 42 && fixed.include?('::MonikaRuby18.eval_source('), 'literal getter và eval dùng cùng ASTpass/compile budget')
source = width_fixture + "total=1; total += LiteralMeasureFixture.new.measure (\" \").width; total\n"
check(eval(MonikaRuby18.instrument_eval_calls(source,[512])) == 43, 'literal getter trong compoundassignment đúng thứ tự/giá trị')
source = <<~'RUBY'
  class LiteralOrderResultFixture
    attr_reader :count
    def initialize; @count=0; end
    def width; @count+=1; 17; end
  end
  class LiteralOrderFixture
    attr_reader :seen, :result
    def take(text); @seen=text; @result=LiteralOrderResultFixture.new; end
  end
  calls=0; object=LiteralOrderFixture.new; fetch=->{calls+=1;object}
  value=fetch.call.take ("private synthetic").width
  [calls,object.seen,object.result.count,value]
RUBY
check(eval(MonikaRuby18.instrument_eval_calls(source,[512])) == [1,'private synthetic',1,17], 'literal result giữ receiver/argument/getter order, mỗi lần một call')
source = "object=Object.new; def object.take(text); text; end; object.take (\" \" ).width\n"
begin
  eval(MonikaRuby18.instrument_eval_calls(source,[512]))
  abort('FAIL không được coerce String result')
rescue NoMethodError => error
  check(error.name == :width && error.receiver == ' ', 'literal result không coerce hoặc che lỗi runtime khác')
end
source = "text='abc'; count=(text.split (/a/).size)+1; [count,text]\n"
begin
  eval(source)
  abort('FAIL regexp size spacing phải tái hiện NoMethodError')
rescue NoMethodError => error
  check(error.name == :size && Regexp === error.receiver, 'regexp getter fixture tái hiện size trên Regexp argument')
end
fixed = MonikaRuby18.instrument_eval_calls(source,[512])
check(fixed == source.sub('split (','split(') && eval(fixed) == [3,'abc'], 'regexp getter xóa chỉ SPACE, giữ outerparen/arithmetic/Stringinput')
check(MonikaRuby18.instrument_eval_calls(fixed,[512]).equal?(fixed), 'regexp result normalization idempotent')
source = "text='Abc'; text.split \t(%r{a}i).size\n"
check(eval(MonikaRuby18.instrument_eval_calls(source,[512])) == 2, 'regexp result giữ literal percent/flags/SPACE-TAB')
source = "tên='abc'; (tên.split (/a/).size) + 1 # keep (space)\n"
fixed = MonikaRuby18.instrument_eval_calls(source,[512])
check(eval(fixed) == 3 && fixed.end_with?("# keep (space)\n"), 'regexp getter byteoffsets giữ Unicode/comment/pattern')
unsupported = [
  'object.call(/a/).size', 'object.call (pattern).size',
  'object.call (/a/,1).size', 'object.call ((/a/)).size',
  'object.call (/a/).width', 'object.call ("a").size',
  'object.call (/a/).size(1)', 'object.call (/a/).size()',
  'object.call (/a/).size == 1', 'call (/a/).size',
  "object.call (/a/)\n.size", 'object.call (/#{pattern}/).size'
]
check(unsupported.all? { |text| MonikaRuby18.instrument_eval_calls(text,[512]).equal?(text) }, 'regexp getter giữ unsupported/dynamic/multipleargs/getterargs/getterpairkhác/compare/multiline/FCALL')
source = "object.call (/a/).size\n"
budget = [512,source.bytesize]
check(MonikaRuby18.instrument_eval_calls(source,budget).equal?(source) && budget == [511,0], 'regexp getter không refill work budget khi thiếu compile cuối')
many = source*65
check(MonikaRuby18.instrument_eval_calls(many,[512]).equal?(many), 'regexp getter quá64sites giữ nguyên')
mixed = "object.call (\" \").width\n"*61 + source*4
check(MonikaRuby18.instrument_eval_calls(mixed,[512]).equal?(mixed), 'STRwidth/RegexpSize dùng chung trần64sites không tách/refill')
source = "text='abc'; count=(text.split (/a/).size)+1; eval('count')\n"
fixed = MonikaRuby18.instrument_eval_calls(source,[512])
check(eval(fixed) == 3 && fixed.include?('::MonikaRuby18.eval_source('), 'regexp result và eval giữ context trong một ASTpass')
source = <<~'RUBY'
  class RegexpOrderResultFixture
    attr_reader :count
    def initialize; @count=0; end
    def size; @count+=1; 9; end
  end
  class RegexpOrderFixture
    attr_reader :seen, :result
    def take(pattern); @seen=pattern; @result=RegexpOrderResultFixture.new; end
  end
  count=0; object=RegexpOrderFixture.new; fetch=->{count+=1;object}
  value=fetch.call.take (/a/i).size
  [count,object.seen,object.result.count,value]
RUBY
check(eval(MonikaRuby18.instrument_eval_calls(source,[512])) == [1,/a/i,1,9], 'regexp result giữ receiver/patternflags/getter mỗi lần một call')
source = "object=Object.new; def object.take(pattern); pattern; end; object.take (/a/).size\n"
begin
  eval(MonikaRuby18.instrument_eval_calls(source,[512]))
  abort('FAIL không được coerce hoặc thêm Regexp#size')
rescue NoMethodError => error
  check(error.name == :size && Regexp === error.receiver, 'regexp result không coerce core hoặc che lỗi runtime khác')
end
puts 'ALL OK'
