import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.collect.Comparators;
import com.jsonanalyzer.main.Main;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class MainTest {

    @DisplayName("📖 Reading JSON file and deserializing it as a tree:")
    @ParameterizedTest(name = "from {0}")
    @ValueSource(strings = {"src/test/resources/first.json", "src/test/resources/second.json"})
    public void testReadJsonTreeByFilePath(String path) throws IOException {

        JsonNode rootNode = Main.readJsonTreeByFilePath(path);

        assertAll("root node",
                () -> assertTrue(rootNode.isObject(), () -> "is not an object"),
                () -> assertFalse(rootNode.isEmpty(), () -> "is empty")
        );

    }

    @Test
    @DisplayName("✍ Writing JSON file to the disk and checking it's contents")
    public void testWriteFile(@TempDir Path tempDirectory) throws IOException {

        assertTrue(tempDirectory.toFile().isDirectory());

        final String tempFileName = "result.json";
        String jsonContent = new String(Files.readAllBytes(Paths.get("src/test/resources/first.json")));
        Main.writeToFile(tempDirectory, tempFileName, jsonContent);

        Path resultFilePath = tempDirectory.resolve(tempFileName);
        assertTrue(Files.exists(resultFilePath), () -> "file doesn't exists");
        JsonNode rootNode = JsonNodeFactory.instance.missingNode();
        try {
            rootNode = Main.readJsonTreeByFilePath(resultFilePath.toString());
        } catch (JsonProcessingException e) {
            fail("result json is invalid", e);
        }

        JsonNode finalRootNode = rootNode;
        assertAll("root node",
                () -> assertTrue(finalRootNode.isObject(), () -> "is not an object"),
                () -> assertFalse(finalRootNode.isEmpty(), () -> "is empty")
        );

    }

    @DisplayName("🥇 Sorting and aligning keys:")
    @ParameterizedTest(name = "from {0}")
    @ValueSource(strings = {"src/test/resources/first.json", "src/test/resources/second.json"})
    public void testSortingFile(String path) throws IOException {

        JsonNode rootNode = Main.readJsonTreeByFilePath(path);

        Map<String, JsonNode> sortedPlainMapFromJsonNode = Main.getSortedPlainMapOfNodes(rootNode);

        assertAll("result key map",
                () -> assertFalse(sortedPlainMapFromJsonNode.isEmpty(), () -> "is empty"),
                () -> assertTrue(Comparators.isInOrder(sortedPlainMapFromJsonNode.keySet(), Comparator.naturalOrder()),
                        () -> "is not naturally ordered")
        );

    }

}
