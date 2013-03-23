require 'rubygems'
require 'neography'
require 'uuid'
require 'uri'
require 'forgery'
  
  $uuid = UUID.new
  
  def create_graph
    create_node_properties
    create_nodes
    create_nodes_index
    create_relationship_properties
    create_relationships
  end  

  def create_bigger_graph
    create_node_properties
    create_more_nodes
    create_nodes_index
    create_relationship_properties
    create_relationships
  end  

  def create_node_properties
    @node_properties = ["unique_id", "type"]
    generate_node_properties(@node_properties)  
  end
 
  def user_values
    [$uuid.generate, "user"]
  end

  def group_values
    [$uuid.generate, "group"]
  end

  def doc_values
    [$uuid.generate, "doc"]
  end


  # Nodes       
  # Users 3000
  # Groups 100
  # Documents 1M

  def create_nodes    
   @nodes = {
             "user"  => { "start" =>     1, "end"   =>   3000},
             "group" => { "start" =>  3001, "end"   =>   3100},
             "doc"   => { "start" =>  3101, "end"   =>1003100}
            }
    
    @nodes.each{ |node| generate_nodes(node[0], node[1])}
  end

  # Nodes       
  # Users 3000
  # Groups 100
  # Documents 10M
  
  def create_more_nodes    
   @nodes = {
             "user"  => { "start" =>     1, "end"   =>    3000},
             "group" => { "start" =>  3001, "end"   =>    3100},
             "doc"   => { "start" =>  3101, "end"   =>10003100}
            }
    
    @nodes.each{ |node| generate_nodes(node[0], node[1])}
  end

  def create_relationship_properties
    @rel_properties = ["flags"]
    generate_rel_properties(@rel_properties)
  end

  def user_to_group_values
    [nil]
  end

  def user_to_document_values
    [["R", "RW", "RWX"].sample]
  end

  def group_to_document_values
    [["R", "RW", "RWX"].sample]
  end

  def document_to_document_values
    [nil]
  end

  def create_relationships
    rels = {"user_to_group"    => { "from"   => @nodes["user"],
                                    "to"     => @nodes["group"],
                                    "number" => 5,
                                    "type"   => "IS_MEMBER_OF",
                                    "connection" => :each },
            "user_to_document" => { "from"   => @nodes["user"],
                                    "to"     => @nodes["doc"],
                                    "number" => 3000,
                                    "type"   => "SECURITY",
                                    "connection" => :each },  
           "group_to_document" => { "from"   => @nodes["group"],
                                    "to"     => @nodes["doc"],
                                    "number" => 10000,
                                    "type"   => "SECURITY",
                                    "connection" => :each },  
        "document_to_document" => { "from"   => @nodes["doc"],
                                    "to"     => @nodes["doc"],
                                    "number" => 50,
                                    "type"   => "IS_CHILD_OF",
                                    "connection" => :each_lower }  
                                             
          }                                             
    # Write relationships to file
    rels.each{ |rel| generate_rels(rel[0], rel[1])}  
  end

  #  Recreate nodes.csv and set the node properties 
  #  
  def generate_node_properties(properties)
    File.open("nodes.csv", "w") do |file|
      file.write(properties.join("\t") + "\n")
    end
  end

  #  Recreate rels.csv and set the relationship properties 
  #  
  def generate_rel_properties(properties)
    File.open("rels.csv", "w") do |file|
      header = ["start", "end", "type"] + properties
      file.write(header.join("\t") + "\n")
    end
  end
  
  # Generate nodes given a type and hash
  #
  def generate_nodes(type, hash)
    puts "Generating #{(1 + hash["end"] - hash["start"])} #{type} nodes..."
    nodes = File.open("nodes.csv", "a")

    (1 + hash["end"] - hash["start"]).times do |t|
        properties = send(type + "_values")
        nodes.write (properties.join("\t") + "\t\n")
    end
    nodes.close
  end

  def generate_rels(description, hash)
    puts "Generating #{hash["number"]} #{description} relationships of type #{hash["type"]}... "
    rels = File.open("rels.csv", "a")
    
      case hash["connection"]
         when :bunched
           bunch = 0
          (hash["from"]["start"]..hash["from"]["end"]).each do |n|
            hash["number"].times do |t|
              rels.write("#{n}\t#{hash["to"]["start"]  + (bunch * hash["number"]) + t}\t#{hash["type"]}\t#{send(description + "_values").join("\t")}\t\n")
            end
            bunch += 1
          end
        when :reverse_bunched
          bunch = 0
         (hash["to"]["start"]..hash["to"]["end"]).each do |n|
           hash["number"].times do |t|
              rels.write("#{hash["from"]["start"]  + (bunch * hash["number"]) + t}\t#{n}\t#{hash["type"]}\t#{send(description + "_values").join("\t")}\t\n")
           end
           bunch += 1
         end
       when :rolling_bunched
         bunch = 0
        (hash["from"]["start"]..hash["from"]["end"]).each do |n|
          hash["number"].times do |t|
            rels.write("#{n}\t#{hash["to"]["start"]  + bunch + t}\t#{hash["type"]}\t#{send(description + "_values").join("\t")}\t\n")
          end
          bunch += 1
        end
        when :random      
          hash["number"].times do |t|
            rels.puts "#{rand(hash["from"]["start"]..hash["from"]["end"])}\t#{rand(hash["to"]["start"]..hash["to"]["end"])}\t#{hash["type"]}\t#{hash["props"].collect{|l| l.call}.join("\t")}" 
          end
        when :sequential
          from_size = 1 + hash["from"]["end"] - hash["from"]["start"]
          to_size = 1 + hash["to"]["end"] - hash["to"]["start"]
          hash["number"].times do |t|
            rels.write("#{hash["from"]["start"] + (t % from_size)}\t#{hash["to"]["start"]  + (t % to_size)}\t#{hash["type"]}\t#{send(description + "_values").join("\t")}\t\n")
          end
        when :reverse
          from_size = 1 + hash["from"]["end"] - hash["from"]["start"]
          to_size = 1 + hash["to"]["end"] - hash["to"]["start"]
          hash["number"].times do |t|
            rels.write("#{hash["to"]["start"] + (t % to_size)}\t#{hash["from"]["start"]  + (t % from_size)}\t#{hash["type"]}\t#{send(description + "_values").join("\t")}\t\n")
          end
        when :each
          (hash["from"]["start"]..hash["from"]["end"]).each do |n|
            members = []
            hash["number"].times do |t|
              members << rand(hash["to"]["start"]..hash["to"]["end"])
            end
            members.delete(n)
            members.sort.uniq.each do |m|
              rels.write("#{n}\t#{m}\t#{hash["type"]}\t#{send(description + "_values").join("\t")}\t\n") 
            end            
          end
        when :each_lower
          parents = {}
          (hash["from"]["start"]..hash["from"]["end"]).each do |n|
            members = []
            hash["number"].times do |t|
              members << rand(hash["to"]["start"]..hash["to"]["end"])
            end
            members.delete_if{|b| b <= n}
            members.sort.uniq.each do |m|
              unless parents[m]
                parents[m]=n
                rels.write("#{m}\t#{n}\t#{hash["type"]}\t#{send(description + "_values").join("\t")}\t\n") 
              end
            end            
          end
        else
          puts "Error - Connection type not supported."
      end
    rels.close
  end

  def create_nodes_index
    puts "Generating Node Index..."
    nodes = File.open("nodes.csv", "r")
    users_index = File.open("users_index.csv","w")
    docs_index = File.open("documents_index.csv","w")
    counter = 0
    
    while (line = nodes.gets)
      case counter
      when 0
        users_index.write("#{counter}\t#{line}")
        docs_index.write("#{counter}\t#{line}")
      when @nodes["user"]["start"]..@nodes["user"]["end"]
        users_index.write("#{counter}\t#{line}")
      when @nodes["doc"]["start"]..@nodes["doc"]["end"]
        docs_index.write("#{counter}\t#{line}")
      end
      counter += 1
    end
    
    nodes.close
    users_index.close
    docs_index.close
  end

  def create_relationships_index
    puts "Generating Relationship Index..."
    rels = File.open("rels.csv", "r")
    rels_index = File.open("rels_index.csv","w")
    counter = -1
    
    while (line = rels.gets)
      size ||= line.split("\t").size
      rels_index.puts "#{counter}\t#{line.split("\t")[3..size].join("\t")}"
      counter += 1
    end
    
    rels.close
    rels_index.close
  end

  # Execute the command needed to import the generated files
  #
  def load_graph
    puts "Running the following:"
    command ="java -server -Xmx4G -jar ./batch-import-jar-with-dependencies.jar neo4j/data/graph.db nodes.csv rels.csv node_index Users exact users_index.csv node_index Documents exact documents_index.csv" 
    puts command
    exec command    
  end 
