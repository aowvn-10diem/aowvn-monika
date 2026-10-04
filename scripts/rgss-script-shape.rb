#!/usr/bin/env ruby
# Chẩn đoán lỗi cú pháp Ruby của script RPG Maker mà KHÔNG lộ nội dung game (V44).
# Dùng: ruby scripts/rgss-script-shape.rb <Data/Scripts.rvdata|.rxdata|.rvdata2> [số dòng ngữ cảnh=3]
# Với mỗi script trong tệp: nén zlib + Marshal → thử biên dịch bằng Ruby của máy chạy (CI nên dùng Ruby 3.1 như mkxp-z).
# Chỉ in: số thứ tự, tiêu đề script ĐÃ CHE (độ dài + hình dạng), câu báo lỗi cú pháp (đã che chuỗi) và "hình dạng" vài dòng quanh dòng lỗi:
# từ khóa Ruby giữ nguyên, tên → id, chuỗi → "s", số → 0, chú thích bỏ. Không in tên biến/chuỗi/hội thoại/tiêu đề thô của game.
#
# AN TOÀN: dữ liệu game là đầu vào KHÔNG tin cậy. KHÔNG dùng Marshal.load (có thể tạo đối tượng tùy ý). Dùng bộ đọc Marshal tối thiểu bên dưới,
# chỉ chấp nhận Array / String / số nguyên nhỏ / nil / true / false; gặp kiểu khác → dừng. Vẫn nên chạy trong job KHÔNG có secret/quyền ghi.
require 'zlib'
require 'ripper'
ruby18 = ARGV.delete('--ruby18')
inspections = ARGV.select { |arg| arg.start_with?('--inspect=') }.map do |arg|
  ARGV.delete(arg)
  value = arg.delete_prefix('--inspect=')
  abort('inspect cần index:dòng') unless value.match?(/\A\d{1,5}:\d{1,5}\z/)
  index, line = value.split(':').map(&:to_i)
  abort('inspect vượt giới hạn') unless index < 10_000 && line.between?(1, 20_000)
  [index, line]
end
abort('quá nhiều điểm inspect') if inspections.size > 4
load File.expand_path('../app/src/main/assets/rgss/monika-ruby18.rb', __dir__) if ruby18

KEYWORDS = %w[alias and begin break case class def defined? do else elsif end ensure false for if in module next nil not or redo rescue retry return self super then true undef unless until when while yield __method__ lambda proc].freeze
KW = Regexp.union(KEYWORDS.map { |k| /\b#{Regexp.escape(k)}(?![\w?!])/ })

def shape(line, names = nil)
  anonymous = ->(token) { names ? "id#{names[token] ||= names.size + 1}" : 'id' }
  # Lexer che cả identifier Unicode, chuỗi có '#' và literal nhiều dòng.
  # Không in raw SyntaxError: Ruby có thể đưa lại tên/chuỗi của game trong thông báo.
  Ripper.lex(line).map do |_, kind, token, _|
    case kind
    when :on_comment then ''
    when :on_sp, :on_ignored_sp then ' '
    when :on_nl, :on_ignored_nl then "\n"
    when :on_kw then KEYWORDS.include?(token) ? token : 'id'
    when :on_ident then %w[eval binding class_eval module_eval instance_eval attr_accessor instance_variables instance_variable_get instance_variable_set instance_methods public_instance_methods private_instance_methods protected_instance_methods include? keys each send to_s to_sym constants const_get class_variables class_variable_get sort downcase class superclass singleton_class is_a? kind_of? first last begin end min max rand size exclude_end?].include?(token) ? token : anonymous.call(token)
    when :on_ivar then '@' + anonymous.call(token)
    when :on_cvar then '@@' + anonymous.call(token)
    when :on_gvar then '$' + anonymous.call(token)
    when :on_label then anonymous.call(token.delete_suffix(':')) + ':'
    when :on_const then %w[Range Array Hash String Integer Float Numeric].include?(token) ? token : anonymous.call(token)
    when :on_int, :on_float, :on_rational, :on_imaginary, :on_CHAR then '0'
    when :on_tstring_beg, :on_tstring_end then '"'
    when :on_tstring_content then 's'
    when :on_op then (token == '..' || token == '...' || token.match?(/\A[+\-*\/%=!<>|&^~?:]+\z/)) ? token : 'id'
    else token.match?(/\A[(){}\[\],.;:]\z/) ? token : 'id'
    end
  end.join
end

require_relative 'rgss_safe_marshal'

path = ARGV[0] or abort('thiếu đường dẫn Scripts.*data')
ctx = Integer(ARGV[1] || 3).clamp(0, 10)
MAX_FILE = 64 * 1024 * 1024 # Scripts.*data thực tế < 5 MB
abort("tệp quá lớn (#{File.size(path)} byte, tối đa #{MAX_FILE})") if File.size(path) > MAX_FILE
data = SafeMarshal.new(File.binread(path)).read
abort('Scripts phải là mảng') unless data.is_a?(Array)
total = 0
bad = 0
ruby18_budget = [MonikaRuby18::MAX_COMPILES, MonikaRuby18::MAX_COMPILE_BYTES] if ruby18
data.each_with_index do |entry, i|
  id, title, packed = entry
  abort('Mục Scripts sai dạng') unless entry.is_a?(Array) && id.is_a?(Integer) && title.is_a?(String) && packed.is_a?(String)
  code = SafeInflate.inflate(packed).force_encoding('UTF-8')
  total += code.bytesize
  abort("tổng dữ liệu giải nén vượt #{SafeInflate::MAX_TOTAL_BYTES} byte") if total > SafeInflate::MAX_TOTAL_BYTES
  code = code.encode('UTF-8', 'Shift_JIS', invalid: :replace, undef: :replace) unless code.valid_encoding?
  if ruby18
    shown = {}
    trace = ->(line, candidates, next_line) do
      puts "repair script=#{i} line=#{line} candidates=#{candidates} next=#{next_line.nil? ? 'valid' : next_line}"
      if next_line == 0 && !shown[line]
        shown[line] = true
        code.lines.each_with_index do |text, index|
          puts format('blocked-shape %5d | %s', index + 1, shape(text).rstrip) if (index + 1 - line).abs <= ctx
        end
      end
    end
    code = MonikaRuby18.repair(code, ruby18_budget, trace)
    prepared = MonikaRuby18.instrument_eval_calls(code, ruby18_budget)
    puts "eval-callsite script=#{i} wrapped=#{prepared.scan('::MonikaRuby18.eval_source(').size}" unless prepared.equal?(code)
    puts "accessor-callsite script=#{i} wrapped=#{prepared.scan('::MonikaRuby18.accessor_constants(').size}" if prepared.include?('::MonikaRuby18.accessor_constants(')
    code = prepared
  end
  inspections.select { |index, _| index == i }.each do |_, line|
    puts "inspect script=#{i} line=#{line}"
    names = {} # Chỉ số ẩn danh trong cửa sổ; không hash/in tên game.
    code.each_line.with_index(1) do |text, index|
      puts format('inspect-shape %5d | %s', index, shape(text, names).rstrip) if (index - line).abs <= ctx
    end
  end
  begin
    RubyVM::InstructionSequence.compile(code, "script-#{i}")
  rescue SyntaxError => e
    bad += 1
    msg = e.message.lines.first.to_s
    ln = msg[/:(\d+):/, 1].to_i
    t = title.to_s.dup.force_encoding('UTF-8').scrub('?')
    puts "== Script ##{i} tiêu đề[#{t.length} ký tự] (#{code.lines.size} dòng): SyntaxError tại dòng #{ln}"
    lines = code.lines
    ((ln - ctx)..(ln + ctx)).each do |n|
      next if n < 1 || n > lines.size
      puts format('%s%5d | %s', n == ln ? '>' : ' ', n, shape(lines[n - 1]).rstrip)
    end
  end
end
puts "Tổng số script: #{data.size}; lỗi cú pháp: #{bad}; Ruby #{RUBY_VERSION}"
puts "Ruby18 budget remaining_compiles=#{ruby18_budget[0]} remaining_bytes=#{ruby18_budget[1]}" if ruby18
