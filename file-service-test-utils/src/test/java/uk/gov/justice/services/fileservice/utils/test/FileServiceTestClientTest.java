package uk.gov.justice.services.fileservice.utils.test;

import static com.jayway.jsonassert.JsonAssert.with;
import static java.io.File.createTempFile;
import static java.nio.file.Files.copy;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;
import static uk.gov.justice.services.fileservice.utils.test.DatabaseDialect.ANSI_SQL;

import uk.gov.justice.services.fileservice.domain.FileReference;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.util.Optional;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;


public class FileServiceTestClientTest {

    private static final String URL = "jdbc:h2:mem:test;MV_STORE=FALSE;MVCC=FALSE";
    private static final String USERNAME = "sa";
    private static final String PASSWORD = "sa";
    private static final String DRIVER_CLASS = org.h2.Driver.class.getName();

    private static final String LIQUIBASE_FILE_STORE_DB_CHANGELOG_XML = "liquibase/file-service-liquibase-db-changelog.xml";

    private final LiquibaseDatabaseBootstrapper liquibaseDatabaseBootstrapper = new LiquibaseDatabaseBootstrapper();
    private final JdbcConnectionProvider jdbcConnectionProvider = new JdbcConnectionProvider();
    private final ClasspathFileResource classpathFileResource = new ClasspathFileResource();

    private final FileServiceTestClient fileServiceTestClient = new FileServiceTestClient(ANSI_SQL);

    private Connection connection;

    @Before
    public void createInMemoryDatabase() throws Exception {

        connection = jdbcConnectionProvider.getConnection(
                URL,
                USERNAME,
                PASSWORD,
                DRIVER_CLASS
        );

        liquibaseDatabaseBootstrapper.bootstrap(
                LIQUIBASE_FILE_STORE_DB_CHANGELOG_XML,
                connection);
    }

    @Test
    public void shouldName() throws Exception {

        final String fileName = "some.jpg";
        final String mediaType = "image/jpeg";
        final File inputFile = classpathFileResource.getFileFromClasspath("/for-testing.jpg");

        final InputStream contentStream = new FileInputStream(inputFile);

        final UUID fileId = fileServiceTestClient.create(fileName, mediaType, contentStream, connection);

        contentStream.close();

        final Optional<FileReference> fileReferenceOptional = fileServiceTestClient.read(fileId, connection);

        assertThat(fileReferenceOptional.isPresent(), is(true));

        final FileReference fileReference = fileReferenceOptional.get();

        assertThat(fileReference.getFileId(), is(fileId));
        assertThat(fileReference.isDeleted(), is(false));


        final String metadataJson = fileReference.getMetadata().toString();

        with(metadataJson)
                .assertThat("$.fileName", is(fileName))
                .assertThat("$.mediaType", is(mediaType))
        ;


        try(final InputStream inputStream = fileReference.getContentStream()) {
            final File outputFile = createTempFile("for-testing-please.delete", "jpg");
            outputFile.deleteOnExit();

            copy(inputStream, outputFile.toPath(), REPLACE_EXISTING);

            assertThat(outputFile.exists(), is(true));
            assertThat(outputFile.length(), is(greaterThan(0L)));
            assertThat(outputFile.length(), is(inputFile.length()));
        }

    }
}
