package hcmute.vina.vectorsearchservice.migrate;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import hcmute.vina.vectorsearchservice.service.EmbeddingService;

/**
 * Simple migration service to ensure required database extensions exist.
 *
 * <p>Currently ensures that the PostgreSQL {@code unaccent} extension is created so that
 * accent-insensitive search can be used in JPA specifications.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseMigrationService {

  private final DataSource dataSource;
  private final EmbeddingService embeddingService;

  @PostConstruct
  public void migrate() {
	  ensureData();
  }

  private void ensureData() {
   

    try {
    	embeddingService.migrateAllCourse();
    	log.info("Migrate all courses successfully");
    } catch (Exception ex) {
      log.error("Failed to migrate all records course", ex);
      throw new RuntimeException("Failed to migrate all records course", ex);
    }
  }
  
  
}
