package dm.dracolich.ai.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"dm.dracolich.ai", "dm.dracolich.forge"})
@EnableReactiveMongoRepositories(basePackages = "dm.dracolich.ai.datasource.repository")
public class DracolichAiWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(DracolichAiWebApplication.class, args);
    }
}
