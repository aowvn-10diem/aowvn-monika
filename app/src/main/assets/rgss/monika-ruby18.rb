# Aow Monika V44: sửa khoảng trắng của cú pháp Ruby1.8 chỉ trên bản đã giải nén trong RAM.
# binding-mri.cpp@b668e08 đặt $RGSS_SCRIPTS[i][3] rồi mới nạp preload, trước eval.
# Không đọc/ghi Scripts.*data; không eval, không in source hoặc tiêu đề game.
module MonikaRuby18
  MAX_BYTES = 8 * 1024 * 1024
  MAX_TOTAL = 128 * 1024 * 1024
  MAX_REPAIRS = 64
  MAX_CANDIDATES = 8
  MAX_COMPILES = 512
  KEYWORDS = %w[def class module if elsif unless while until for case when begin end return yield super rescue ensure not and or].freeze

  def self.error_line(source, budget = nil)
    if budget
      return 0 if budget[0] <= 0
      budget[0] -= 1
    end
    RubyVM::InstructionSequence.compile(source, 'monika-vx')
    nil
  rescue SyntaxError => error
    error.message[/monika-vx:(\d+):/, 1]&.to_i || 0
  end

  def self.repair(source, budget = [MAX_COMPILES], trace = nil)
    return source unless source.is_a?(String) && source.bytesize <= MAX_BYTES && source.valid_encoding?
    return source unless defined?(RubyVM::InstructionSequence)
    line = error_line(source, budget)
    return source if line.nil? || line == 0 # Source hợp lệ giữ nguyên từng byte.
    original = source
    MAX_REPAIRS.times do
      lines = source.lines
      text = lines[line - 1]
      return original unless text
      # Chỉ thử bỏ SPACE/TAB giữa tên hàm ASCII và '(' ở dòng lỗi hiện tại.
      # Xóa trong literal/comment không thể làm lỗi chuyển sang dòng sau, nên bị loại.
      matches = []
      text.to_enum(:scan, /\b([A-Za-z_][A-Za-z_0-9]*[!?]?)([ \t]+)(?=\()/).each do
        match = Regexp.last_match
        next if KEYWORDS.include?(match[1])
        matches << [match.begin(2), match.end(2)]
      end
      trace.call(line, matches.size, nil) if trace && (matches.empty? || matches.size > MAX_CANDIDATES)
      return original if matches.empty? || matches.size > MAX_CANDIDATES
      candidates = matches.filter_map do |start, finish|
        changed = lines.dup
        changed[line - 1] = text[0...start] + text[finish..-1]
        candidate = changed.join
        next_line = error_line(candidate, budget)
        trace.call(line, matches.size, next_line) if trace
        [candidate, next_line] if next_line.nil? || next_line > line
      end
      return original unless candidates.size == 1 # Mơ hồ/không tiến triển: không sửa.
      source, line = candidates.first
      return source if line.nil? # Chỉ nhận khi TOÀN script compile được.
    end
    original
  end

  def self.applied_count; @applied_count.to_i; end

  def self.apply(scripts)
    return 0 unless scripts.is_a?(Array) && scripts.size <= 10_000
    total = scripts.sum { |entry| entry.is_a?(Array) && entry[3].is_a?(String) ? entry[3].bytesize : 0 }
    return 0 if total > MAX_TOTAL
    count = 0
    budget = [MAX_COMPILES]
    scripts.each do |entry|
      next unless entry.is_a?(Array) && !entry.frozen? && entry[3].is_a?(String)
      fixed = repair(entry[3], budget)
      next if fixed.equal?(entry[3])
      entry[3] = fixed
      count += 1
    end
    @applied_count = count
  end
end

if defined?($RGSS_SCRIPTS) && !defined?(RGSS_VERSION) # Ace định nghĩa 3.0.1: không áp dụng.
  count = MonikaRuby18.apply($RGSS_SCRIPTS)
  puts "MonikaRuby18 normalized scripts=#{count}" if count > 0
end
