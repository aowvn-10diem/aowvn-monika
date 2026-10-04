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
  MAX_ACCESSOR_CONSTANTS = 256
  BUILTIN_CLASS = Kernel.instance_method(:class)
  BUILTIN_CONSTANTS = Module.instance_method(:constants)
  BUILTIN_CLASS_CONSTANTS = Class.method(:constants)
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

  # Mẫu legacy ở class body: for name in self.class.constants;
  # attr_accessor name.downcase.to_sym; end. self.class là Class, không phải
  # lớp đang khai báo. Fixture MRI1.8.7: Class.constants kế thừa singleton
  # Module.constants đọc lexical CREF, nên thấy constants của class body;
  # MRI3.1 không còn hành vi đó. Chỉ fallback owner khi collection gốc rỗng;
  # không sửa Class/Module toàn cục, không che NoMethodError ở chỗ khác.
  def self.accessor_constants(owner, original)
    return original unless Class === owner && original.is_a?(Array) && original.empty?
    return original unless BUILTIN_CLASS_CONSTANTS.source_location.nil? &&
      Class.method(:constants) == BUILTIN_CLASS_CONSTANTS
    class_method = owner.method(:class)
    constants_method = owner.method(:constants)
    # Kernel#class là wrapper internal Ruby trên MRI3.1; Method#== với
    # bind không ổn định như method C. So owner/source_location với bản
    # chụp trước khi game chạy, không giả định source_location luôn nil.
    return original unless class_method.owner == BUILTIN_CLASS.owner &&
      class_method.source_location == BUILTIN_CLASS.source_location &&
      BUILTIN_CONSTANTS.source_location.nil? && constants_method == BUILTIN_CONSTANTS.bind(owner)
    names = owner.constants
    return original unless names.is_a?(Array) && names.size <= MAX_ACCESSOR_CONSTANTS
    return original unless names.all? do |name|
      (name.is_a?(Symbol) || name.is_a?(String)) && name.to_s.bytesize <= 128 &&
        name.to_s.match?(/\A[A-Z][A-Za-z0-9_]*\z/)
    end
    names
  end

  # Nhận đúng vòng FOR một biến, chỉ một lời gọi attr_accessor(var.downcase.to_sym).
  # Không nhận each/custom collection/receiver hay vòng có thêm tác dụng phụ.
  def self.accessor_collection(node)
    return nil unless node.type == :FOR
    collection, scope = node.children
    return nil unless collection&.type == :CALL && collection.children[1..2] == [:constants, nil]
    klass = collection.children[0]
    return nil unless klass&.type == :CALL && klass.children[1..2] == [:class, nil] && klass.children[0]&.type == :SELF
    return nil unless scope&.type == :SCOPE
    _, args, body = scope.children
    return nil unless args&.type == :ARGS && args.children[0] == 1
    assignment = args.children[1]
    return nil unless assignment&.type == :LASGN && assignment.children[0].is_a?(Symbol)
    return nil unless body&.type == :FCALL && body.children[0] == :attr_accessor
    list = body.children[1]
    return nil unless list&.type == :LIST && list.children.size == 2 && list.children[1].nil?
    symbol = list.children[0]
    return nil unless symbol&.type == :CALL && symbol.children[1..2] == [:to_sym, nil]
    lower = symbol.children[0]
    return nil unless lower&.type == :CALL && lower.children[1..2] == [:downcase, nil]
    variable = lower.children[0]
    return nil unless variable && [:LVAR, :DVAR].include?(variable.type) && variable.children[0] == assignment.children[0]
    collection
  end

  # Ruby1.8: x.is_a? (Integer) ? a : b là ternary ngoài lời gọi.
  # Ruby3: cả (Integer) ? a : b thành đối số của is_a?. Chỉ nhận CALL
  # một dòng, một IF argument, CONST đơn và đúng dấu ngoặc/ternary này.
  # Không coerce dữ liệu, không thay core predicate hay operator Range.
  def self.predicate_spacing(node, lines)
    return nil unless node.type == :CALL && [:is_a?, :kind_of?].include?(node.children[1])
    return nil unless node.first_lineno == node.last_lineno
    receiver, method, args = node.children
    return nil unless receiver && args&.type == :LIST && args.children.size == 2 && args.children[1].nil?
    conditional = args.children[0]
    return nil unless conditional&.type == :IF && conditional.children.all? { |child| child.is_a?(RubyVM::AbstractSyntaxTree::Node) }
    condition = conditional.children[0]
    return nil unless condition.type == :CONST && condition.first_lineno == node.first_lineno
    return nil unless receiver.last_lineno == node.first_lineno
    text = lines[node.first_lineno - 1]
    gap = text.byteslice(receiver.last_column, conditional.first_column - receiver.last_column)
    match = /\A[ \t]*\.[ \t]*#{Regexp.escape(method.to_s)}([ \t]+)\z/.match(gap.to_s)
    return nil unless match
    opening = text.byteslice(conditional.first_column, condition.last_column - conditional.first_column)
    return nil unless opening&.match?(/\A\([ \t]*[A-Z][A-Za-z0-9_]*\z/)
    tail = text.byteslice(condition.last_column, text.bytesize - condition.last_column)
    return nil unless tail&.match?(/\A[ \t]*\)[ \t]*\?/)
    [node.first_lineno, receiver.last_column + match.begin(1), receiver.last_column + match.end(1)]
  end

  # Một AST pass bọc collection accessor/eval và sửa predicate spacing hẹp.
  # Giữ eval ở callsite: chỉ bọc đối số đầu, không override Kernel#eval
  # (override sẽ làm mất local variables/cref mặc định của người gọi).
  def self.instrument_eval_calls(source, budget)
    return source unless source.is_a?(String) && source.bytesize <= MAX_AST_BYTES && source.valid_encoding?
    return source if source.count("\n") > MAX_LINES || !(source.include?('eval') || source.include?('constants') || source.include?('is_a?') || source.include?('kind_of?'))
    return source unless defined?(RubyVM::AbstractSyntaxTree)
    budget[1] ||= MAX_COMPILE_BYTES
    return source if budget[0] <= 0 || source.bytesize > budget[1]
    budget[0] -= 1
    budget[1] -= source.bytesize # AST parse cũng dùng cùng work budget.
    root = RubyVM::AbstractSyntaxTree.parse(source)
    stack = [[root, false]]
    sites = []
    deletions = []
    lines = nil
    visited = 0
    while (item = stack.pop)
      node, class_body = item
      visited += 1
      return source if visited > MAX_AST_NODES
      children = node.children
      if node.type == :CALL && [:is_a?, :kind_of?].include?(children[1])
        deletion = predicate_spacing(node, lines ||= source.lines)
        deletions << deletion if deletion
        return source if sites.size + deletions.size > MAX_EVAL_SITES
      end
      children.each_with_index do |child, index|
        next unless child.is_a?(RubyVM::AbstractSyntaxTree::Node)
        context = case node.type
                  when :CLASS then index == 2
                  when :DEFN, :DEFS, :SCLASS, :MODULE then false
                  else class_body
                  end
        stack << [child, context]
      end
      if class_body && (collection = accessor_collection(node))
        if collection.first_lineno == collection.last_lineno
          sites << [collection.first_lineno, collection.first_column, collection.last_column,
                    '::MonikaRuby18.accessor_constants(self,']
          return source if sites.size + deletions.size > MAX_EVAL_SITES
        end
      end
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
      sites << [arg.first_lineno, arg.first_column, arg.last_column, '::MonikaRuby18.eval_source(']
      return source if sites.size + deletions.size > MAX_EVAL_SITES
    end
    return source if sites.empty? && deletions.empty?
    offsets = [0]
    source.each_line { |line| offsets << offsets.last + line.bytesize }
    inserts = Hash.new { |hash, key| hash[key] = '' }
    sites.each do |line, first, last, prefix|
      start = offsets[line - 1] + first
      finish = offsets[line - 1] + last
      return source unless finish > start && finish <= source.bytesize
      inserts[start] += prefix
      inserts[finish] = ')' + inserts[finish]
    end
    edits = inserts.map { |offset, value| [offset, offset, value] }
    deletions.each do |line, first, finish|
      start = offsets[line - 1] + first
      stop = offsets[line - 1] + finish
      return source unless stop > start && stop <= source.bytesize
      edits << [start, stop, '']
    end
    size = source.bytesize + inserts.values.sum(&:bytesize) - deletions.sum { |_, first, finish| finish - first }
    return source if size > MAX_BYTES || budget[0] <= 0 || size > budget[1]
    # Ghép từng mảnh một lần, tránh insert/dup cả script ở mỗi callsite.
    parts = []
    last = 0
    edits.sort_by { |first, finish, _| [first, finish] }.each do |first, finish, value|
      return source if first < last # Không đoán khi edit overlap.
      parts << source.byteslice(last, first - last)
      parts << value
      last = finish
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
