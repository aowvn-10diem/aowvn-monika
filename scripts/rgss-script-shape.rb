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

KEYWORDS = %w[alias and begin break case class def defined? do else elsif end ensure false for if in module next nil not or redo rescue retry return self super then true undef unless until when while yield __method__ lambda proc].freeze
KW = Regexp.union(KEYWORDS.map { |k| /\b#{Regexp.escape(k)}(?![\w?!])/ })

def shape(line)
  s = line.sub(/#.*\z/, '')
  s = s.gsub(/"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'/, '"s"')
  s = s.gsub(/\b\d+(?:\.\d+)?\b/, '0')
  # che mọi từ không phải từ khóa
  s.gsub(/[A-Za-z_][\w]*[?!]?/) { |w| KEYWORDS.include?(w) ? w : 'id' }
end

# Bộ đọc Marshal 4.8 tối thiểu cho Scripts.*data = [[id, tiêu đề, mã nén], …].
class SafeMarshal
  def initialize(bytes); @b = bytes.b; @i = 0; @syms = []; end
  def read; raise 'không phải Marshal 4.8' unless @b[0, 2].bytes == [4, 8]; @i = 2; value; end

  private

  def byte; v = @b.getbyte(@i) or raise('hết dữ liệu'); @i += 1; v; end

  def long
    c = byte; c -= 256 if c > 127
    return 0 if c == 0
    return c - 5 if c > 4
    return c + 5 if c < -4
    n = c.abs; x = 0
    n.times { |k| x |= byte << (8 * k) }
    c < 0 ? x - (1 << (8 * n)) : x
  end

  def bytes_n; n = long; raise 'độ dài lạ' if n < 0 || n > 64 * 1024 * 1024; s = @b[@i, n] or raise('hết dữ liệu'); @i += n; s; end

  def symbol
    case (t = byte.chr)
    when ':' then (@syms << bytes_n.force_encoding('UTF-8')).last
    when ';' then @syms[long] or raise('symlink sai')
    else raise "mong đợi symbol, gặp #{t.inspect}"
    end
  end

  def value
    t = byte.chr
    case t
    when '0' then nil
    when 'T' then true
    when 'F' then false
    when 'i' then long
    when '[' then Array.new(long) { value }
    when '"' then bytes_n
    when 'I' # chuỗi kèm biến thể (mã hóa): đọc chuỗi rồi bỏ qua các ivar chỉ gồm symbol → nil/true/false/chuỗi ngắn
      v = value
      long.times { symbol; value }
      v
    else raise "kiểu Marshal không cho phép: #{t.inspect}"
    end
  end
end

path = ARGV[0] or abort('thiếu đường dẫn Scripts.*data')
ctx = (ARGV[1] || 3).to_i
data = SafeMarshal.new(File.binread(path)).read
bad = 0
data.each_with_index do |entry, i|
  id, title, packed = entry
  code = Zlib::Inflate.inflate(packed).force_encoding('UTF-8')
  code = code.encode('UTF-8', 'Shift_JIS', invalid: :replace, undef: :replace) unless code.valid_encoding?
  begin
    RubyVM::InstructionSequence.compile(code, title.to_s)
  rescue SyntaxError => e
    bad += 1
    msg = e.message.lines.first.to_s
    ln = msg[/:(\d+):/, 1].to_i
    t = title.to_s.dup.force_encoding('UTF-8').scrub('?')
    puts "== Script ##{i} tiêu đề[#{t.length} ký tự, hình: #{shape(t)}] (#{code.lines.size} dòng): #{msg.sub(/\A.*?:\d+:\s*/, '').gsub(/"[^"]*"/, '"s"').strip}"
    lines = code.lines
    ((ln - ctx)..(ln + ctx)).each do |n|
      next if n < 1 || n > lines.size
      puts format('%s%5d | %s', n == ln ? '>' : ' ', n, shape(lines[n - 1]).rstrip)
    end
  end
end
puts "Tổng số script: #{data.size}; lỗi cú pháp: #{bad}; Ruby #{RUBY_VERSION}"
