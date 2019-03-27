package cluster.sharding;

import akka.cluster.sharding.ShardRegion;

import java.io.Serializable;

class EntityMessage {
    static class Command implements Serializable {
        final long time;
        final Entity entity;

        Command(Entity entity) {
            time = System.nanoTime();
            this.entity = entity;
        }

        @Override
        public String toString() {
            return String.format("%s[%dus, %s]", getClass().getSimpleName(), time, entity);
        }
    }

    static class CommandAck implements Serializable {
        final long commandTime;
        final String action;
        final Entity entity;

        private CommandAck(long commandTime, String action, Entity entity) {
            this.commandTime = commandTime;
            this.action = action;
            this.entity = entity;
        }

        static CommandAck ackInit(Command command) {
            return new CommandAck(command.time, "initialize", command.entity);
        }

        static CommandAck ackUpdate(Command command) {
            return new CommandAck(command.time, "update", command.entity);
        }

        @Override
        public String toString() {
            final double elapsed = (System.nanoTime() - commandTime) / 1000000000.0;
            return String.format("%s[elapsed %.9fs, %dus, %s, %s]", getClass().getSimpleName(), elapsed, commandTime, action, entity);
        }
    }

    static class Query implements Serializable {
        final long time;
        final Entity.Id id;

        Query(Entity.Id id) {
            time = System.nanoTime();
            this.id = id;
        }

        @Override
        public String toString() {
            return String.format("%s[%s]", getClass().getSimpleName(), id);
        }
    }

    static class QueryAck implements Serializable {
        final long queryTime;
        final Entity entity;

        private QueryAck(long queryTime, Entity entity) {
            this.queryTime = queryTime;
            this.entity = entity;
        }

        static QueryAck ack(Query query, Entity entity) {
            return new QueryAck(query.time, entity);
        }

        @Override
        public String toString() {
            final double elapsed = (System.nanoTime() - queryTime) / 1000000000.0;
            return String.format("%s[elapsed %.9fs, %d, %s]", getClass().getSimpleName(), elapsed, queryTime, entity);
        }
    }

    static class QueryAckNotFound implements Serializable {
        final long queryTime;
        final Entity.Id id;

        private QueryAckNotFound(long queryTime, Entity.Id id) {
            this.queryTime = queryTime;
            this.id = id;
        }

        static QueryAckNotFound ack(Query query) {
            return new QueryAckNotFound(query.time, query.id);
        }

        @Override
        public String toString() {
            final double elapsed = (System.nanoTime() - queryTime) / 1000000000.0;
            return String.format("%s[elapsed %.9fs, %d, %s]", getClass().getSimpleName(), elapsed, queryTime, id);
        }
    }

    static ShardRegion.MessageExtractor messageExtractor() {
        final int numberOfShards = 100;

        return new ShardRegion.MessageExtractor() {
            @Override
            public String shardId(Object message) {
                return extractShardIdFromCommands(message);
            }

            @Override
            public String entityId(Object message) {
                return extractEntityIdFromCommands(message);
            }

            @Override
            public Object entityMessage(Object message) {
                return message;
            }

            private String extractShardIdFromCommands(Object message) {
                if (message instanceof Command) {
                    return ((Command) message).entity.id.id.hashCode() % numberOfShards + "";
                } else if (message instanceof Query) {
                    return ((Query) message).id.id.hashCode() % numberOfShards + "";
                } else {
                    return null;
                }
            }

            private String extractEntityIdFromCommands(Object message) {
                if (message instanceof Command) {
                    return ((Command) message).entity.id.id;
                } else if (message instanceof Query) {
                    return ((Query) message).id.id;
                } else {
                    return null;
                }
            }
        };
    }
}
