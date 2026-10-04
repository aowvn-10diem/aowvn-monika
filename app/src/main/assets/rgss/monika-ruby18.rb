# Aow Monika V44: sửa khoảng trắng của cú pháp Ruby1.8 chỉ trên bản đã giải nén trong RAM.
# binding-mri.cpp@b668e08 đặt $RGSS_SCRIPTS[i][3] rồi mới nạp preload, trước eval.
# Không đọc/ghi Scripts.*data; không eval, không in source hoặc tiêu đề game.
module MonikaRuby18
  MAX_BYTES = 8 * 1024 * 1024
  MAX_LINES = 20_000
  MAX_TOTAL = 128 * 1024 * 1024
  MAX_REPAIRS = 128
  MAX_CANDIDATES = 8
  MAX_COMPILES = 512
  MAX_COMPILE_BYTES = 64 * 1024 * 1024
  MAX_AST_NODES = 50_000
  MAX_AST_BYTES = 256 * 1024
  MAX_EVAL_SITES = 64
  MAX_EVAL_CACHE_ENTRIES = 32
  MAX_EVAL_CACHE_BYTES = 64 * 1024
  # super là lời gọi với danh sách đối số; Ruby1.8 cho phép SPACE trước
  # '(' + splat, Ruby3 cần super(...). Các từ khóa điều khiển vẫn bị loại.
  KEYWORDS = %w[def class module if elsif unless while until for case when begin end return yield rescue ensure not and or].freeze

  def self.error_line(source, budget = nil)
    if budget
      budget[1] ||= MAX_COMPILE_BYTES
      return 0 if budget[0] <= 0 || source.bytesize > budget[1]
      budget[0] -= 1
      budget[1] -= source.bytesize
    end
    RubyVM::InstructionSequence.compile(source, 'monika-vx')
    nil
  rescue SyntaxError => error
    error.message[/monika-vx:(\d+):/, 1]&.to_i || 0
  end

  def self.repair(source, budget = [MAX_COMPILES], trace = nil)
    return source unless source.is_a?(String) && source.bytesize <= MAX_BYTES && source.valid_encoding?
    return source if source.count("\n") > MAX_LINES
    return source unless defined?(RubyVM::InstructionSequence)
    line = error_line(source, budget)
    return source if line.nil? || line == 0 # Source hợp lệ giữ nguyên từng byte.
    original = source
    MAX_REPAIRS.times do
      return original if budget[0] <= 0 || budget[1] <= 0
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
        break if matches.size > MAX_CANDIDATES
      end
      trace.call(line, matches.size, 0) if trace && (matches.empty? || matches.size > MAX_CANDIDATES)
      return original if matches.empty? || matches.size > MAX_CANDIDATES
      # Một dòng có thể chứa nhiều lời gọi cũ: thử tập nhỏ nhất làm parser
      # tiến triển, chỉ nhận khi tập đó duy nhất. Tối đa 2^8-1 lần thử và
      # vẫn dùng chung budget compile của cả phiên; không chọn khi hết budget.
      candidates = []
      1.upto(matches.size) do |size|
        matches.combination(size) do |edits|
          # Kiểm trước dup/join: cả nguồn compile lẫn candidate mới dùng
          # chung trần byte, không chỉ giới hạn số lần compile.
          candidate_bytes = source.bytesize - edits.sum { |start, finish| finish - start }
          return original if budget[0] <= 0 || candidate_bytes > budget[1]
          changed = lines.dup
          changed_text = text.dup
          edits.reverse_each { |start, finish| changed_text[start...finish] = '' }
          changed[line - 1] = changed_text
          candidate = changed.join
          next_line = error_line(candidate, budget)
          trace.call(line, matches.size, next_line) if trace
          candidates << [candidate, next_line] if next_line.nil? || next_line > line
          return original if candidates.size > 1
        end
        break unless candidates.empty?
      end
      return original unless candidates.size == 1 # Mơ hồ/không tiến triển: không sửa.
      source, line = candidates.first
      return source if line.nil? # Chỉ nhận khi TOÀN script compile được.
    end
    trace.call(line, -1, 0) if trace # -1: hết trần vòng sửa, không phải thiếu candidate.
    original
  end

  def self.applied_count; @applied_count.to_i; end

  # Giữ eval ở callsite: chỉ bọc đối số đầu, không override Kernel#eval
  # (override sẽ làm mất local variables/cref mặc định của người gọi).
  def self.instrument_eval_calls(source, budget)
    return source unless source.is_a?(String) && source.bytesize <= MAX_AST_BYTES && source.valid_encoding?
    return source if source.count("\n") > MAX_LINES || !source.include?('eval')
    return source unless defined?(RubyVM::AbstractSyntaxTree)
    budget[1] ||= MAX_COMPILE_BYTES
    return source if budget[0] <= 0 || source.bytesize > budget[1]
    budget[0] -= 1
    budget[1] -= source.bytesize # AST parse cũng dùng cùng work budget.
    root = RubyVM::AbstractSyntaxTree.parse(source)
    stack = [root]
    sites = []
    visited = 0
    while (node = stack.pop)
      visited += 1
      return source if visited > MAX_AST_NODES
      children = node.children
      children.each { |child| stack << child if child.is_a?(RubyVM::AbstractSyntaxTree::Node) }
      next unless node.type == :FCALL && children[0] == :eval
      args = children[1]
      next unless args && args.type == :LIST
      arg = args.children[0]
      next unless arg.is_a?(RubyVM::AbstractSyntaxTree::Node)
      next unless arg.first_lineno == arg.last_lineno # Heredoc/multiline: không đoán range.
      if arg.type == :CALL
        receiver, method = arg.children
        next if method == :eval_source && receiver&.type == :COLON3 && receiver.children == [:MonikaRuby18]
      end
      sites << [arg.first_lineno, arg.first_column, arg.last_column]
      return source if sites.size > MAX_EVAL_SITES
    end
    return source if sites.empty?
    offsets = [0]
    source.each_line { |line| offsets << offsets.last + line.bytesize }
    inserts = Hash.new { |hash, key| hash[key] = '' }
    prefix = '::MonikaRuby18.eval_source('
    sites.each do |line, first, last|
      start = offsets[line - 1] + first
      finish = offsets[line - 1] + last
      return source unless finish > start && finish <= source.bytesize
      inserts[start] += prefix
      inserts[finish] = ')' + inserts[finish]
    end
    size = source.bytesize + inserts.values.sum(&:bytesize)
    return source if size > MAX_BYTES || budget[0] <= 0 || size > budget[1]
    # Ghép từng mảnh một lần, tránh insert/dup cả script ở mỗi callsite.
    parts = []
    last = 0
    inserts.keys.sort.each do |offset|
      parts << source.byteslice(last, offset - last)
      parts << inserts[offset]
      last = offset
    end
    parts << source.byteslice(last, source.bytesize - last)
    fixed = parts.join.force_encoding(source.encoding)
    error_line(fixed, budget).nil? ? fixed : source
  rescue SyntaxError, ArgumentError
    source
  end

  def self.eval_source(source)
    return source unless source.is_a?(String)
    @eval_cache ||= {}
    return @eval_cache[source].dup if @eval_cache.key?(source)
    @eval_budget ||= [MAX_COMPILES, MAX_COMPILE_BYTES]
    fixed = repair(source, @eval_budget)
    fixed = instrument_eval_calls(fixed, @eval_budget)
    if !fixed.equal?(source) && @eval_cache.size < MAX_EVAL_CACHE_ENTRIES
      bytes = source.bytesize + fixed.bytesize
      @eval_cache_bytes ||= 0
      if @eval_cache_bytes + bytes <= MAX_EVAL_CACHE_BYTES
        @eval_cache[source.dup.freeze] = fixed.dup.freeze
        @eval_cache_bytes += bytes
      end
    end
    fixed
  end

  def self.apply(scripts)
    return 0 unless scripts.is_a?(Array) && scripts.size <= 10_000
    total = scripts.sum { |entry| entry.is_a?(Array) && entry[3].is_a?(String) ? entry[3].bytesize : 0 }
    return 0 if total > MAX_TOTAL
    count = 0
    budget = [MAX_COMPILES]
    @eval_budget = budget
    @eval_cache = {}
    @eval_cache_bytes = 0
    scripts.each do |entry|
      next unless entry.is_a?(Array) && !entry.frozen? && entry[3].is_a?(String)
      fixed = repair(entry[3], budget)
      fixed = instrument_eval_calls(fixed, budget)
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
