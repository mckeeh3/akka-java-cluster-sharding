package cluster.sharding;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;

class EntityQueryActor extends AbstractLoggingActor {
    private final ActorRef shardRegion;
    private Cancellable ticker;
    private FiniteDuration tickInterval = Duration.create(2, TimeUnit.SECONDS);
    private final Receive sending;
    private final Receive receiving;
    private Entity.Id lastQueryId;

    {
        sending = receiveBuilder()
                .matchEquals("tick", t -> tickSending())
                .match(EntityMessage.QueryAck.class, this::queryAckSending)
                .match(EntityMessage.QueryAckNotFound.class, this::queryAckNotFoundSending)
                .build();

        receiving = receiveBuilder()
                .matchEquals("tick", t -> tickReceiving())
                .match(EntityMessage.QueryAck.class, this::queryAckReceiving)
                .match(EntityMessage.QueryAckNotFound.class, this::queryAckNotFoundReceiving)
                .build();
    }

    private EntityQueryActor(ActorRef shardRegion) {
        this.shardRegion = shardRegion;
    }

    @Override
    public Receive createReceive() {
        return sending;
    }

    private void tickSending() {
        lastQueryId = Random.entityId(1, 100);
        shardRegion.tell(new EntityMessage.Query(lastQueryId), getSelf());
        getContext().become(receiving);
    }

    private void queryAckSending(EntityMessage.QueryAck queryAck) {
        log().info("Received (late) {} {}", queryAck, getSender());
    }

    private void queryAckNotFoundSending(EntityMessage.QueryAckNotFound queryAckNotFound) {
        log().info("Received (late) {} {}", queryAckNotFound, getSender());
    }

    private void tickReceiving() {
        log().warning("No query response to {}", lastQueryId);
        getContext().become(sending);
    }

    private void queryAckReceiving(EntityMessage.QueryAck queryAck) {
        log().info("Received {} {}", queryAck, getSender());
        getContext().become(sending);
    }

    private void queryAckNotFoundReceiving(EntityMessage.QueryAckNotFound queryAckNotFound) {
        log().info("Received {} {}", queryAckNotFound, getSender());
        getContext().become(sending);
    }

    @Override
    public void preStart() {
        log().info("start");
        ticker = getContext().getSystem().scheduler().schedule(
                Duration.Zero(),
                tickInterval,
                getSelf(),
                "tick",
                getContext().getSystem().dispatcher(),
                null
        );
    }

    @Override
    public void postStop() {
        log().info("stop");
        ticker.cancel();
    }

    static Props props(ActorRef shardRegion) {
        return Props.create(EntityQueryActor.class, shardRegion);
    }
}
