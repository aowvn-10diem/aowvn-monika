require 'zlib'
# Bộ đọc Marshal 4.8 tối thiểu + giải nén có trần, an toàn cho Scripts.*data không tin cậy. Dùng bởi scripts/rgss-script-shape.rb.
# Bộ đọc Marshal 4.8 tối thiểu cho Scripts.*data = [[id, tiêu đề, mã nén], …].
class SafeMarshal
  MAX_DEPTH = 8          # Scripts.*data chỉ sâu 3: mảng → mảng → chuỗi (+ ivar)
  MAX_NODES = 200_000    # số phần tử tối đa tổng cộng (số script thực tế vài trăm)

  def initialize(bytes); @b = bytes.b; @i = 0; @syms = []; @depth = 0; @nodes = 0; end
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
    @nodes += 1
    raise 'quá nhiều phần tử' if @nodes > MAX_NODES
    @depth += 1
    raise 'lồng quá sâu' if @depth > MAX_DEPTH
    begin
      parse_value
    ensure
      @depth -= 1
    end
  end

  def parse_value
    t = byte.chr
    case t
    when '0' then nil
    when 'T' then true
    when 'F' then false
    when 'i' then long
    when '['
      n = long
      raise 'độ dài mảng sai' if n < 0 || n > @b.size - @i # mỗi phần tử ≥ 1 byte
      Array.new(n) { value }
    when '"' then bytes_n
    when 'I' # chuỗi kèm biến thể (mã hóa): đọc chuỗi rồi bỏ qua các ivar chỉ gồm symbol → nil/true/false/chuỗi ngắn
      v = value
      iv = long
      raise 'quá nhiều ivar' if iv < 0 || iv > 8
      iv.times { symbol; value }
      v
    else raise "kiểu Marshal không cho phép: #{t.inspect}"
    end
  end
end


# Giải nén zlib có trần đầu ra (chống "bom nén"): nạp từng mảnh nhỏ, vượt trần → dừng. Mỗi script RPG Maker thực tế vài chục KB.
module SafeInflate
  MAX_SCRIPT_BYTES = 8 * 1024 * 1024   # sau giải nén, mỗi script
  MAX_TOTAL_BYTES  = 128 * 1024 * 1024 # sau giải nén, cả tệp
  CHUNK = 1024

  def self.inflate(data, limit = MAX_SCRIPT_BYTES)
    z = Zlib::Inflate.new
    out = +''.b
    pos = 0
    while pos < data.bytesize
      out << z.inflate(data.byteslice(pos, CHUNK))
      raise "dữ liệu giải nén vượt #{limit} byte" if out.bytesize > limit
      pos += CHUNK
    end
    out << z.finish unless z.finished?
    raise "dữ liệu giải nén vượt #{limit} byte" if out.bytesize > limit
    out
  ensure
    z&.close
  end
end
