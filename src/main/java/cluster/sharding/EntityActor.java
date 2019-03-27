package cluster.sharding;

import akka.actor.AbstractLoggingActor;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.cluster.sharding.ShardRegion;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;

class EntityActor extends AbstractLoggingActor {
    private Entity entity;
    private final FiniteDuration receiveTimeout = Duration.create(60, TimeUnit.SECONDS);

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(EntityMessage.Command.class, this::command)
                .match(EntityMessage.Query.class, this::query)
                .matchEquals(ReceiveTimeout.getInstance(), t -> passivate())
                .build();
    }

    private void command(EntityMessage.Command command) {
        log().info("{} <- {}", command, sender());
        if (entity == null) {
            entity = command.entity;
            final EntityMessage.CommandAck commandAck = EntityMessage.CommandAck.ackInit(command);
            log().info("{}, {} -> {}", commandAck, command, sender());
            sender().tell(commandAck, self());
        } else {
            entity.value = command.entity.value;
            final EntityMessage.CommandAck commandAck = EntityMessage.CommandAck.ackUpdate(command);
            log().info("{}, {} -< {}", commandAck, command, sender());
            sender().tell(commandAck, self());
        }
    }

    private void query(EntityMessage.Query query) {
        log().info("{} <- {}", query, sender());
        if (entity == null) {
            final EntityMessage.QueryAckNotFound queryAck = EntityMessage.QueryAckNotFound.ack(query);
            log().info("{} -> {}", queryAck, sender());
            sender().tell(queryAck, self());
        } else {
            final EntityMessage.QueryAck queryAck = EntityMessage.QueryAck.ack(query, entity);
            log().info("{} -> {}", queryAck, sender());
            sender().tell(queryAck, self());
        }
    }

    private void passivate() {
        context().parent().tell(new ShardRegion.Passivate(PoisonPill.getInstance()), self());
    }

    @Override
    public void preStart() {
        log().info("Start");
        context().setReceiveTimeout(receiveTimeout);
    }

    @Override
    public void postStop() {
        log().info("Stop {}", entity == null ? "(not initialized)" : entity.id);
    }

    static Props props() {
        return Props.create(EntityActor.class);
    }
}
