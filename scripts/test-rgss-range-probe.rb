require 'stringio'
load File.expand_path('rgss-range-probe.rb', __dir__)
# Bootstrap observer records only the first synthetic Range failure.
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
