# V44: CI-only exception observer; workflow appends this to its temporary APK assets.
# No game names/values/path, no core overrides, no rescue of the game's exception.
module MonikaCiRangeProbe
  LIMIT = 64
  IS_A = Kernel.instance_method(:is_a?)
  IVARS = Kernel.instance_method(:instance_variables)
  IVAR_GET = Kernel.instance_method(:instance_variable_get)
  LOCALS = Binding.instance_method(:local_variables)
  LOCAL_GET = Binding.instance_method(:local_variable_get)
  SELF_GET = Binding.instance_method(:receiver)
  MESSAGE = Exception.instance_method(:message)
  ARRAY_GET = Array.instance_method(:[])
  ARRAY_SIZE = Array.instance_method(:length)
  TYPES = [NilClass, TrueClass, FalseClass, Integer, Float, String, Symbol, Array, Hash, Range].freeze
  LABELS = %w[Nil True False Integer Float String Symbol Array Hash Range].freeze

  def self.kind(value, children = true)
    index = TYPES.index { |type| IS_A.bind(value).call(type) }
    label = index ? LABELS[index] : 'Other'
    if children && index == 7
      n = [ARRAY_SIZE.bind(value).call, 4].min
      label += '[' + n.times.map { |i| kind(ARRAY_GET.bind(value).call(i), false) }.join(',') + ']'
    end
    label
  end

  def self.fields(value, nested = false)
    # At the failing receiver, retain only compound fields so scalar defaults
    # cannot hide the object providing the Range endpoint. No field names.
    names = IVARS.bind(value).call.take(nested ? 256 : 8)
    rows = []
    names.each_with_index do |name, index|
      item = IVAR_GET.bind(value).call(name)
      label = kind(item)
      next if nested && label != 'Other' && label != 'Range' && !label.start_with?('Array[')
      label += '{' + fields(item).join(',') + '}' if nested && label == 'Other'
      rows << "#{index}:#{label}"
      break if rows.size >= 16
    end
    rows
  end

  def self.start
    seen = 0
    probe = TracePoint.new(:raise) do |event|
      begin
        seen += 1
        error = event.raised_exception
        matching = IS_A.bind(error).call(ArgumentError) && MESSAGE.bind(error).call == 'bad value for range'
        probe.disable if matching || seen >= LIMIT
        next unless matching
        context = event.binding
        locals = LOCALS.bind(context).call.take(8).each_with_index.map do |name, index|
          "#{index}:#{kind(LOCAL_GET.bind(context).call(name))}"
        end
        owner = SELF_GET.bind(context).call
        puts "MonikaCiRangeProbe bad-range line=#{event.lineno} locals=[#{locals.join(',')}] fields=[#{fields(owner, true).join(',')}]"
      rescue Exception
        # Observer failure must not replace or suppress the original runtime exception.
        probe.disable
      end
    end
    probe.enable
    probe
  end
end
MonikaCiRangeProbe.start
