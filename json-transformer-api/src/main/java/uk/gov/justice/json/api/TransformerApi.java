package uk.gov.justice.json.api;

import java.util.List;

import javax.json.JsonArray;
import javax.json.JsonObject;

public interface TransformerApi {

    JsonObject transformWithJolt(final String joltOperations, final JsonObject inputJson);
}
