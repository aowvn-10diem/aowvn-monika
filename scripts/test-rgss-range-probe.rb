require 'stringio'
require 'tmpdir'
load File.expand_path('rgss-range-probe.rb', __dir__)
# Observer records only the first synthetic Range failure.
MonikaCiRangeProbe.start
class PrivateGameFixture
  def fail_range
    secret_local = 'private dialogue'
    @secret_field = ['private title', 23]
    secret_endpoint = Object.new
    (secret_endpoint..17)
  end
end
out = StringIO.new
saved = $stdout
$stdout = out
begin
  PrivateGameFixture.new.fail_range
  abort 'expected original ArgumentError'
rescue ArgumentError => e
  raise unless e.message == 'bad value for range'
ensure
  $stdout = saved
end
text = out.string
abort 'missing anonymous types' unless text.include?('MonikaCiRangeProbe bad-range') && text.include?('String') && text.include?('Array[String,Integer]')
abort 'private data leaked' if %w[PrivateGameFixture secret_local secret_field secret_endpoint private dialogue title].any? { |v| text.include?(v) }
puts 'ok original Range ArgumentError and anonymous types'
# No values or names, also for nested objects and custom overrides.
class PrivateNestedFixture
  def initialize; @private_key = 'private value'; end
  def instance_variables; raise 'do not call game overrides'; end
end
labels = MonikaCiRangeProbe.fields(PrivateNestedFixture.new)
abort 'override/field privacy' unless labels == ['0:String']
puts 'ok core reflection bypasses game overrides without exposing data'
receiver = Object.new
32.times { |i| receiver.instance_variable_set("@private_scalar_#{i}", 'hidden') }
receiver.instance_variable_set(:@private_compound, PrivateNestedFixture.new)
labels = MonikaCiRangeProbe.fields(receiver, true)
abort 'compound behind scalars' unless labels == ['32:Other{0:String}']
puts 'ok compound types remain visible behind scalar defaults, no names/values'
probe = MonikaCiRangeProbe.start
64.times { begin; raise 'unrelated'; rescue RuntimeError; end }
abort 'exception event bound' if probe.enabled?
puts 'ok observer stops after 64 unrelated raises'
probe = MonikaCiRangeProbe.start
out = StringIO.new; $stdout = out
2.times { begin; (Object.new..17); rescue ArgumentError; end }
$stdout = saved
abort 'one probe event only' unless out.string.scan('MonikaCiRangeProbe bad-range').size == 1 && !probe.enabled?
puts 'ok single bounded diagnostic and no exception suppression'

Dir.mktmpdir('range-types') do |dir|
  path = File.join(dir, 'types.txt')
  probe = MonikaCiRangeProbe.start(path)
  begin; (Object.new..17); rescue ArgumentError; end
  abort 'file evidence missing/unsafe' unless File.read(path).start_with?('MonikaCiRangeProbe bad-range line=') && !probe.enabled?
  puts 'ok bounded evidence file outside game data, original exception preserved'
end
