package uk.gov.justice.services.fileservice.repository;

import static java.util.UUID.randomUUID;
import static javax.json.Json.createReader;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import uk.gov.justice.services.test.utils.core.jdbc.JdbcConnectionProvider;
import uk.gov.justice.services.test.utils.core.jdbc.LiquibaseDatabaseBootstrapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import javax.json.JsonObject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MetadataJdbcRepositoryIT {

    private static final String LIQUIBASE_FILE_STORE_DB_CHANGELOG_XML = "liquibase/file-service-liquibase-db-changelog.xml";

    private static final String URL = "jdbc:h2:mem:test;MV_STORE=FALSE;MVCC=FALSE";
    private static final String USERNAME = "sa";
    private static final String PASSWORD = "sa";
    private static final String DRIVER_CLASS = org.h2.Driver.class.getName();

    private final JdbcConnectionProvider connectionProvider = new JdbcConnectionProvider();
    private final MetadataJdbcRepository metadataJdbcRepository = new MetadataJdbcRepository();
    private final ContentJdbcRepository contentJdbcRepository = new ContentJdbcRepository();
    private final LiquibaseDatabaseBootstrapper liquibaseDatabaseBootstrapper = new LiquibaseDatabaseBootstrapper();


    private Connection connection;

    @Before
    public void setupDatabase() throws Exception {

        metadataJdbcRepository.metadataSqlProvider = new AnsiMetadataSqlProvider();

        connection = connectionProvider.getConnection(URL, USERNAME, PASSWORD, DRIVER_CLASS);
        liquibaseDatabaseBootstrapper.bootstrap(LIQUIBASE_FILE_STORE_DB_CHANGELOG_XML, connection);
    }

    @After
    public void closeConnection() throws SQLException {

        if(connection != null) {
            connection.close();
        }
    }

    @Test
    public void shouldStoreAndRetrieveMetadata() throws Exception {

        final UUID fileId = randomUUID();

        final String json = "{\"some\": \"json\"}";
        final InputStream content = new ByteArrayInputStream("some file or other".getBytes());
        final JsonObject metadata = toJsonObject(json);

        contentJdbcRepository.insert(fileId, content, connection);
        metadataJdbcRepository.insert(fileId, metadata, connection);

        final JsonObject foundMetadata = metadataJdbcRepository
                .findByFileId(fileId, connection)
                .orElseThrow(() -> new AssertionError("Failed to find metadata in database"));

        assertThat(foundMetadata, is(metadata));
    }

    @Test
    public void shouldUpdateMetadata() throws Exception {

        final UUID fileId = randomUUID();

        final InputStream content = new ByteArrayInputStream("some file or other".getBytes());
        final JsonObject metadata = toJsonObject("{\"some\": \"json\"}");
        final JsonObject newMetadata = toJsonObject("{\"someOther\": \"json\"}");

        contentJdbcRepository.insert(fileId, content, connection);

        metadataJdbcRepository.insert(fileId, metadata, connection);

        final JsonObject foundMetadata = metadataJdbcRepository
                .findByFileId(fileId, connection)
                .orElseThrow(() -> new AssertionError("Failed to find metadata using id " + fileId));

        assertThat(foundMetadata, is(metadata));

        metadataJdbcRepository.update(fileId, newMetadata, connection);

        final JsonObject updatedMetadata = metadataJdbcRepository
                .findByFileId(fileId, connection)
                .orElseThrow(() -> new AssertionError("Failed to find metadata using id " + fileId));

        assertThat(updatedMetadata, is(newMetadata));
    }

    @Test
    public void shouldDeleteMetadata() throws Exception {

        final UUID fileId = randomUUID();

        final String json = "{\"some\": \"json\"}";
        final InputStream content = new ByteArrayInputStream("some file or other".getBytes());
        final JsonObject metadata = toJsonObject(json);

        contentJdbcRepository.insert(fileId, content, connection);
        metadataJdbcRepository.insert(fileId, metadata, connection);

        assertThat(metadataJdbcRepository.findByFileId(fileId, connection).isPresent(), is(true));

        metadataJdbcRepository.delete(fileId, connection);

        assertThat(metadataJdbcRepository.findByFileId(fileId, connection).isPresent(), is(false));
    }

    private JsonObject toJsonObject(final String json) {
        return createReader(new StringReader(json)).readObject();
    }
}
