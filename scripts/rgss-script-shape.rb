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
load File.expand_path('../app/src/main/assets/rgss/monika-ruby18.rb', __dir__) if ruby18

KEYWORDS = %w[alias and begin break case class def defined? do else elsif end ensure false for if in module next nil not or redo rescue retry return self super then true undef unless until when while yield __method__ lambda proc].freeze
KW = Regexp.union(KEYWORDS.map { |k| /\b#{Regexp.escape(k)}(?![\w?!])/ })

def shape(line)
  # Lexer che cả identifier Unicode, chuỗi có '#' và literal nhiều dòng.
  # Không in raw SyntaxError: Ruby có thể đưa lại tên/chuỗi của game trong thông báo.
  Ripper.lex(line).map do |_, kind, token, _|
    case kind
    when :on_comment then ''
    when :on_sp, :on_ignored_sp then ' '
    when :on_nl, :on_ignored_nl then "\n"
    when :on_kw then KEYWORDS.include?(token) ? token : 'id'
    when :on_int, :on_float, :on_rational, :on_imaginary, :on_CHAR then '0'
    when :on_tstring_beg, :on_tstring_end then '"'
    when :on_tstring_content then 's'
    when :on_op then token.match?(/\A[+\-*\/%=!<>|&^~?:]+\z/) ? token : 'id'
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
data.each_with_index do |entry, i|
  id, title, packed = entry
  abort('Mục Scripts sai dạng') unless entry.is_a?(Array) && id.is_a?(Integer) && title.is_a?(String) && packed.is_a?(String)
  code = SafeInflate.inflate(packed).force_encoding('UTF-8')
  total += code.bytesize
  abort("tổng dữ liệu giải nén vượt #{SafeInflate::MAX_TOTAL_BYTES} byte") if total > SafeInflate::MAX_TOTAL_BYTES
  code = code.encode('UTF-8', 'Shift_JIS', invalid: :replace, undef: :replace) unless code.valid_encoding?
  if ruby18
    trace = ->(line, candidates, next_line) { puts "repair script=#{i} line=#{line} candidates=#{candidates} next=#{next_line.nil? ? 'valid' : next_line}" }
    code = MonikaRuby18.repair(code, [MonikaRuby18::MAX_COMPILES], trace)
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
