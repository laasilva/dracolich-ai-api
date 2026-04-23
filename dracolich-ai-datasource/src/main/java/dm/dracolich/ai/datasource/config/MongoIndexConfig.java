package dm.dracolich.ai.datasource.config;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class MongoIndexConfig {

    @Bean
    public CommandLineRunner createSessionIndexes(ReactiveMongoTemplate mongoTemplate) {
        return args -> {
            // Index on user_id for list queries
            mongoTemplate.indexOps("sessions")
                    .ensureIndex(new Index().on("user_id", Sort.Direction.ASC).named("idx_user_id"))
                    .subscribe(name -> log.info("Ensured index: {}", name));

            // TTL index: expire anonymous sessions (no user_id) after 24 hours
            Document keyDoc = new Document("created_at", 1);
            Bson filter = Filters.eq("user_id", null);
            IndexOptions options = new IndexOptions()
                    .name("idx_ttl_anonymous_sessions")
                    .expireAfter(24L, TimeUnit.HOURS)
                    .partialFilterExpression(filter);

            mongoTemplate.getCollection("sessions")
                    .flatMap(collection -> reactor.core.publisher.Mono.from(
                            collection.createIndex(keyDoc, options)))
                    .subscribe(
                            name -> log.info("Ensured TTL index: {}", name),
                            error -> {
                                if (error.getMessage() != null && error.getMessage().contains("already exists")) {
                                    log.debug("TTL index already exists");
                                } else {
                                    log.warn("Failed to create TTL index: {}", error.getMessage());
                                }
                            });
        };
    }
}
