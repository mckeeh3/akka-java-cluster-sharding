## Akka Java Cluster Sharding Example

### Introduction

This is a Java, Maven, Akka project that demonstrates how to setup an
[Akka Cluster](https://doc.akka.io/docs/akka/current/index-cluster.html)
with an example implementation of
[Cluster Sharding](https://doc.akka.io/docs/akka/current/cluster-sharding.html).

This project is one in a series of projects that starts with a simple Akka Cluster project and progressively builds up to examples of event sourcing and command query responsibility segregation.

The project series is composed of the following projects:
* [akka-java-cluster](https://github.com/mckeeh3/akka-java-cluster)
* [akka-java-cluster-aware](https://github.com/mckeeh3/akka-java-cluster-aware)
* [akka-java-cluster-singleton](https://github.com/mckeeh3/akka-java-cluster-singleton)
* [akka-java-cluster-sharding](https://github.com/mckeeh3/akka-java-cluster-sharding) (this project)
* [akka-java-cluster-persistence](https://github.com/mckeeh3/akka-java-cluster-persistence)
* [akka-java-cluster-persistence-query](https://github.com/mckeeh3/akka-java-cluster-persistence-query)

Each project can be cloned, built, and runs independently of the other projects.

This project contains an example implementation of cluster sharding. Here we will focus on the implementation details in this project. Please see the
[Akka documentation](https://doc.akka.io/docs/akka/current/cluster-sharding.html)
for a more detailed discussion about cluster sharding.

### What is cluster sharding

According to the [Akka documentation](https://doc.akka.io/docs/akka/current/cluster-sharding.html#introduction),
"*Cluster sharding is useful when you need to distribute actors across several nodes in the cluster and want to be able to interact with them using their logical identifier, but without having to care about their physical location in the cluster, which might also change over time.*"

The common usage for cluster sharding is to distribute and engage with individual actors across the cluster. Each of these distributed actors is used to handle messages that are intended for a specific entity. Each entity represents a thing, such as a bank account or a shopping cart. Entities each have a unique identifier, such as an account or shopping cart identifier.

In this example project, the entities represent simple bank accounts. Each entity handles incoming deposit and withdrawal messages. Two actors are used to simulate clients that are sending messages to entities. The `EntityCommandActor` and the `EntityQueryActor` randomly generate messages to specific entities. These two actors are used to simulate incoming service requests. In a real implementation, the service would receive incoming messages and forward those messages to specific entities to handle the request messages.

The process of forwarding these messages to the right entities, which could be distributed across multiple JVMs running in a cluster, is handled by cluster sharding. To send a message to an entity the sender simply sends the message to a shard region actor. The shard region actor is responsible for forwarding the message to the correct entity actor. The actual mechanics of this process is described in the
[How it works](https://doc.akka.io/docs/akka/current/cluster-sharding.html#how-it-works)
section of the cluster sharding documentation.

![Visualization of cluster sharding](docs/images/akka-cluster-k8-3-pods.png)
<center>Figure 1, Visualization of cluster sharding</center><br/>

The visualization in Figure 1 shows an example of cluster sharding. The blue leaf actors represent the entity actors. Each entity actor represents the state of an entity. The green circles that connect to the entity circles represent the running shard actors. In the example system there 15 shards configured. The shards connect to the orange shard region actors. These orange circles also represent other actors, such as the entity command and query actors. Also, the orange circles represent the root of the actor system on each cluster node.

### How it works

The Runner class contains the `main` method. The `main` method starts one or more Akka systems and in each actor system it starts instances of multiple actors.

The arguments passed to the main method are expected to be zero or more port numbers. These port numbers will be used to start cluster nodes, one for each specified port.

If no ports are specified a default is used to start three JVMs using ports 2551, 2552, and 0 respectively.

~~~java
List<ActorSystem> actorSystems = args.length == 0
        ? startupClusterNodes(Arrays.asList("2551", "2552", "0"))
        : startupClusterNodes(Arrays.asList(args));
~~~

Multiple actor systems may be started in a single JVM. However, the typical use case is that a single actor system is started per JVM. One way to think of an
[actor system](https://doc.akka.io/docs/akka/current/general/actor-systems.html)
is that they are supercharged thread pools.

The `startupClusterNodes` method is called with the list of specified port numbers. Each port is used to start an actor system and then start up various actors that will run in the demonstration.

The most notable actor in this cluster sharding example is the `shardRegion` actor.

~~~java
ActorRef shardingRegion = setupClusterSharding(actorSystem);
~~~

This actor is instantiated in the `setupClusterSharding` method.

~~~java
private static ActorRef setupClusterSharding(ActorSystem actorSystem) {
    ClusterShardingSettings settings = ClusterShardingSettings.create(actorSystem);
    return ClusterSharding.get(actorSystem).start(
            "entity",
            EntityActor.props(),
            settings,
            EntityMessage.messageExtractor()
    );
}
~~~

This method uses the `ClusterSharding` static `get` method to start a single shard region actor per actor system. More details on how the shard region actors are used is described above. The `get` method is used to create a shard region actor passing it the code to be used to create an instance of an entity actor (`EntityActor.props()`) and the code used to extract entity and shard identifiers from messages that are sent to entity actors (`EntityMessage.messageExtractor()`).

~~~java
actorSystem.actorOf(EntityCommandActor.props(shardingRegion), "entityCommand");
actorSystem.actorOf(EntityQueryActor.props(shardingRegion), "entityQuery");
~~~

The `shardRegion` actor reference is passed as a constructor argument to the `EntityCommandActor` and the `EntityQueryActor`. These actors use the `shardRegion` actor ref to send messages to specific entity actors.

~~~java
shardRegion.tell(command(), self());
~~~

The `shardRegion` actor handles the heavy lifting of routing each message to the correct entity actor.

### Installing

~~~bash
git clone https://github.com/mckeeh3/akka-java-cluster-sharding.git
cd akka-java-cluster-sharding
mvn clean package
~~~

### Running from the command line

The following Maven command runs a signle JVM with 3 Akka actor systems on ports 2551, 2552, and a radmonly selected port.
~~~bash
mvn exec:java
~~~
To run on specific ports use the following `-D` option for passing in command line arguements.
~~~bash
mvn exec:java -Dexec.args="2551"
~~~
The default no arguments is equivalent to the following.
~~~bash
mvn exec:java -Dexec.args="2551 2552 0"
~~~
A common way to run tests is to start single JVMs in multiple command windows. This simulates running a multi-node Akka cluster.
For example, run the following 4 commands in 4 command windows.
~~~bash
mvn exec:java -Dexec.args="2551" > /tmp/$(basename $PWD)-1.log
~~~
~~~bash
mvn exec:java -Dexec.args="2552" > /tmp/$(basename $PWD)-2.log
~~~
~~~bash
mvn exec:java -Dexec.args="0" > /tmp/$(basename $PWD)-3.log
~~~
~~~bash
mvn exec:java -Dexec.args="0" > /tmp/$(basename $PWD)-4.log
~~~
This runs a 4 node Akka cluster starting 2 nodes on ports 2551 and 2552, which are the cluster seed nodes as configured and the `application.conf` file.
And 2 nodes on randomly selected port numbers.
The optional redirect `> /tmp/$(basename $PWD)-4.log` is an example for pushing the log output to filenames based on the project direcctory name.

For convenience, in a Linux command shell define the following aliases.

~~~bash
alias p1='cd ~/akka-java/akka-java-cluster'
alias p2='cd ~/akka-java/akka-java-cluster-aware'
alias p3='cd ~/akka-java/akka-java-cluster-singleton'
alias p4='cd ~/akka-java/akka-java-cluster-sharding'
alias p5='cd ~/akka-java/akka-java-cluster-persistence'
alias p6='cd ~/akka-java/akka-java-cluster-persistence-query'

alias m1='clear ; mvn exec:java -Dexec.args="2551" > /tmp/$(basename $PWD)-1.log'
alias m2='clear ; mvn exec:java -Dexec.args="2552" > /tmp/$(basename $PWD)-2.log'
alias m3='clear ; mvn exec:java -Dexec.args="0" > /tmp/$(basename $PWD)-3.log'
alias m4='clear ; mvn exec:java -Dexec.args="0" > /tmp/$(basename $PWD)-4.log'
~~~

The p1-6 alias commands are shortcuts for cd'ing into one of the six project directories.
The m1-4 alias commands start and Akka node with the appropriate port. Stdout is also redirected to the /tmp directory.

### Run Scripts

The project contains 5 scripts that can be used to start and stop individual cluster nodes or start and stop a cluster of nodes.

Use the `./start-node N` and `./stop-node N` scripts to start and stop individual nodes. The N argument is the node number,
which must be between 1 and 9. The start script will start an Akka node running on port 255N. Both `stdin` and `stderr`
output is set to a file in the `/tmp` directory using the naming convention `/tmp/<project-dir-name>-N.log`.

Start node 1 on port 2551 and node 2 on port 2552.
~~~bash
./start-node 1
./start-node 2
~~~

Stop node 3 on port 2553.
~~~bash
./stop-node 3
~~~

Use the `./start-cluster N` and `./stop-cluster` scripts to start and stop multiple cluster nodes. The N argument is the
number of cluster nodes to be started. The `./stop-cluster` script stops all current running nodes.

Start a cluster of four nodes on ports 2551, 2552, 2553, and 2554.
~~~bash
./start-cluster 4
~~~

Stop all currently running cluster nodes.
~~~bash
./stop-cluster
~~~

You can use the `start-cluster` script to start multiple nodes and then use `start-node` and `stop-node`
to start and stop individual nodes.

Use the `./tail-node N` script to `tail -f` the log file for the node N.
