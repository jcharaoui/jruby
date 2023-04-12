require 'rspec'
require 'bigdecimal'

describe 'BigDecimal' do
  it 'should be duplicable' do
    a = BigDecimal(1)

    expect(a.dup).to eq(a)
  end
end
