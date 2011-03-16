#!/usr/bin/env ruby

# == Synopsis 
#   This is the driver to run the benchmarks for the Sequentially Consistent Java.
#
# == Examples
#   This command is a driver for the benchmarks.
#     run_benchmark 
#
# == Usage 
#   run_benchmark [options] benchmark_prefix benchmark_config [benchmark_size]
#   benchmark_config      The compiler config string
#   benchmark_size        'small' or 'large' if the benchmark accepts input
#
#   For help use: run_benchmark -h
#
# == Options
#   -h, --help          Displays help message
#   -q, --quiet         Output as little as possible, overrides verbose
#   -V, --verbose       Verbose output
#   -server            Start java with the -server flag
#   -ea                 use -ea flag for java
#   TO DO - add additional options
#
# == Author
#   Christoph Angerer
#
# == Copyright
#   Copyright (c) 2011 Christoph Angerer. Licensed under the GNU Lesser General Public License:
#   http://www.gnu.org/licenses/lgpl.html

require 'optparse'
require 'rdoc/usage'
require 'ostruct'
require 'date'

class App

  attr_reader :options

  def initialize(arguments, stdin)
    @arguments = arguments
    @stdin = stdin

    #set defaults
    @options = OpenStruct.new
    @options.verbose = false
    @options.quiet = false
  end

  def run
    if parsed_options? && arguments_valid?
      puts "Start at #{DateTime.now}" if @options.verbose
  
      output_options if @options.verbose
  
      process_arguments
      process_command
  
      puts "Finished at #{DateTime.now}" if @options.verbose
    else
      output_usage
    end
  end

  protected
  def parsed_options?
    opts = OptionParser.new
    opts.on('-h', '--help')       { output_help }
    opts.on('-V', '--verbose')    { @options.verbose = true }  
    opts.on('-q', '--quiet')      { @options.quiet = true }
    opts.on('-server') { @options.server = true }
    opts.on('-ea') { @options.ea = true }
    
    opts.parse!(@arguments) rescue return false
  
    process_options
    true      
  end

  # Performs post-parse processing on options
  def process_options
    @options.verbose = false if @options.quiet
  end

  def output_options
    puts "Options:"
  
    @options.marshal_dump.each do |name, val|        
      puts "  #{name} = #{val}"
    end
  end

  # True if required arguments were provided
  def arguments_valid?
    # TO DO - implement your real logic here
    return false if @arguments.length < 2 || @arguments.length > 3
    
    bench = @arguments[0]
    size = @arguments[2]
    if bench == "philo"
      @mainclass = "philo.scj.philo.Philo"
      @input = ''
    elsif bench == "xxx"
      @mainclass = "philo.scj.philo.Philo"
      @input = size == 'small' ? 'small' : 'large'
    end
    
    
    
    true
  end

  # Setup the arguments
  def process_arguments
    # TO DO - place in local vars, etc
  end

  def output_help
    RDoc::usage() #exits app
  end

  def output_usage
    RDoc::usage('usage') # gets usage from comments above
  end

  def process_command
    bootclasspath = "-Xbootclasspath/p:./scj_build/#{@arguments[0]}/#{@arguments[1]}/:./Shared/libs/jsr166y.jar:../Shared/libs/scj_runtime.jar"
    command = "java #{"-server" if @options.server} #{"-ea" if @options.ea } -Xmx1024m #{bootclasspath} #{@mainclass} #{@input}"
    
    puts command
    result = `#{command}`
    puts result
  end
end

app = App.new(ARGV, STDIN)
app.run