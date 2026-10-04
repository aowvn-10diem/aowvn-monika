# Aow Monika: nạp TRƯỚC script của game (mkxp.json: preloadScript). Không sửa script game.
# mkxp-z trên Android chỉ có MiniFFI (dlopen): các DLL Windows (user32, kernel32…) không tồn tại nên Win32API.new ném
# "Failed loading user32: dlopen failed". Nhiều game (Debug Extension, mouse, đổi tiêu đề cửa sổ…) gọi Win32API chỉ để
# đọc phím/chuột/cửa sổ. Ở đây Win32API là bản giả: DLL Windows → hàm giả trả giá trị an toàn; DLL khác → thử MiniFFI gốc.
module MonikaWin32
  WINDOWS_DLLS = %w[user32 kernel32 gdi32 shell32 winmm advapi32 ole32 comdlg32 comctl32 msvcrt shlwapi imm32 dwmapi].freeze

  def self.windows_dll?(name)
    WINDOWS_DLLS.include?(name.to_s.downcase.sub(/\.dll\z/, ''))
  end

  def self.fill(buf, values, fmt)
    buf.replace(values.pack(fmt)) if buf.respond_to?(:replace) && !buf.frozen?
  end

  # Trả giá trị giả theo tên hàm Windows API. Mặc định 0 (= thất bại/không có).
  def self.call_stub(func, args)
    case func.to_s
    when 'GetAsyncKeyState', 'GetKeyState', 'GetKeyboardState', 'GetLastError', 'GetKeyboardLayout', 'GetTopWindow'
      0
    when 'FindWindow', 'FindWindowA', 'FindWindowW', 'GetActiveWindow', 'GetForegroundWindow', 'GetDesktopWindow', 'GetFocus'
      1
    when 'GetWindowRect', 'GetClientRect'
      fill(args[1], [0, 0, Graphics.width, Graphics.height], 'l4') if defined?(Graphics)
      1
    when 'GetCursorPos'
      fill(args[0], [0, 0], 'l2'); 1
    when 'ScreenToClient', 'ClientToScreen', 'ShowCursor', 'SetCursorPos', 'SetWindowPos', 'MoveWindow', 'ShowWindow',
         'SetForegroundWindow', 'SetFocus', 'SetWindowTextA', 'SetWindowTextW', 'SetWindowText', 'UpdateWindow'
      1
    when 'GetSystemMetrics'
      case args[0].to_i when 0 then 640 when 1 then 480 else 0 end
    when 'MessageBox', 'MessageBoxA', 'MessageBoxW'
      1 # IDOK
    when 'GetTickCount', 'timeGetTime'
      (Process.clock_gettime(Process::CLOCK_MONOTONIC) * 1000).to_i & 0x7fffffff
    when 'GetUserDefaultLangID', 'GetUserDefaultUILanguage', 'GetSystemDefaultLangID'
      0x0409
    else
      0
    end
  end
end

Object.send(:remove_const, :Win32API) if Object.const_defined?(:Win32API)

class Win32API
  def initialize(dll, func, imports = '', exports = 'L')
    @dll = dll.to_s
    @func = func.to_s
    @real = nil
    unless MonikaWin32.windows_dll?(@dll)
      begin
        @real = MiniFFI.new(dll, func, imports, exports) if defined?(MiniFFI)
      rescue Exception
        @real = nil
      end
    end
  end

  def call(*args)
    @real ? @real.call(*args) : MonikaWin32.call_stub(@func, args)
  end
  alias Call call
end
