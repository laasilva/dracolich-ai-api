package dm.dracolich.ai.datasource.repository;

import dm.dracolich.ai.datasource.entity.SessionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SessionRepository extends ReactiveMongoRepository<SessionEntity, String> {
    Flux<SessionEntity> findByFormat(String format);
    Flux<SessionEntity> findByUserId(String userId);
    Flux<SessionEntity> findByUserId(String userId, Pageable pageable);
    Mono<Long> countByUserId(String userId);
}
