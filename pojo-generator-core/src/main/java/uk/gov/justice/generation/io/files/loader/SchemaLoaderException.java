package uk.gov.justice.generation.io.files.loader;

public class SchemaLoaderException extends RuntimeException {

    public SchemaLoaderException(final String message) {
        super(message);
    }

    public SchemaLoaderException(final String message, final Throwable cause) {
        super(message, cause);
    }
}