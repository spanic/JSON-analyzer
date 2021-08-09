package com.jsonanalyzer.main;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import javafx.util.Pair;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class Main {

    private static ObjectMapper mapper;
    private static ObjectReader reader;

    public static void main(String[] args) throws IOException {

        String chosenOption = null;
        boolean isOptionValid = false;

        if (args.length >= 2) {
            chosenOption = args[0];
            isOptionValid = Arrays.stream(ProgramOptions.values())
                    .anyMatch(programOption -> programOption.getName().equalsIgnoreCase(args[0]));
        }

        if (chosenOption == null || !isOptionValid) {
            throw new IllegalArgumentException("Please choose one of the options: \n"
                    + "-sort {json}"
                    + "-compare {json_1} {json_2} \n"
                    + "-cleanup {json_excluded} {json_target} \n"
                    + "-merge {json_1} {json_2} ... \n"
                    + "-find {source_json} {set_name_1}={json_set_1} {set_name_2}={json_set_2} ..."
            );
        }

        String[] arguments = Arrays.copyOfRange(args, 1, args.length);

        if (ProgramOptions.ALIGN_AND_SORT.getName().equalsIgnoreCase(chosenOption)) {
            if (arguments.length != 1) {
                throw new IllegalArgumentException(
                        MessageFormat.format("Not enough / too many files for sorting: {0}/1", arguments.length)
                );
            }
            alignAndSort(arguments);
        } else if (ProgramOptions.COMPARE.getName().equalsIgnoreCase(chosenOption)) {
            if (arguments.length != 2) {
                throw new IllegalArgumentException(
                        MessageFormat.format("Not enough / too many files for comparison: {0}/2", arguments.length)
                );
            }
            findUniqueEntriesForEachFile(arguments[0], arguments[1]);
        } else if (ProgramOptions.CLEANUP.getName().equalsIgnoreCase(chosenOption)) {
            if (arguments.length < 2) {
                throw new IllegalArgumentException(
                        MessageFormat.format("Not enough files for cleanup: {0}/2+", arguments.length)
                );
            }
            removeExcludedKeysFromEachFile(arguments[0], Arrays.copyOfRange(arguments, 1, arguments.length));
        } else if (ProgramOptions.MERGE.getName().equalsIgnoreCase(chosenOption)) {
            if (arguments.length < 2) {
                throw new IllegalArgumentException(
                        MessageFormat.format("Not enough files for merge: {0}/2+", arguments.length)
                );
            }
            mergeFiles(arguments);
        } else if (ProgramOptions.FIND.getName().equalsIgnoreCase(chosenOption)) {
            if (arguments.length < 2) {
                throw new IllegalArgumentException(
                        MessageFormat.format("Not enough files for finding inclusions: {0}/2+", arguments.length)
                );
            }
            List<Pair<String, String>> listOfNamedSets = new ArrayList<>();
            for (int i = 1; i < arguments.length; i++) {
                String[] setNameAndFilePath = arguments[i].split("=");
                listOfNamedSets.add(new Pair<>(setNameAndFilePath[0], setNameAndFilePath[1]));
            }
            findInclusions(arguments[0], listOfNamedSets.toArray(new Pair[0]));
        }

    }

    private static void alignAndSort(String... sourceFilesPaths) throws IOException {

        Set<String> uniqueSourceFilePaths = Arrays.stream(sourceFilesPaths).collect(Collectors.toSet());

        System.out.println("Sorting & aligning the files...");
        for (String filePath : uniqueSourceFilePaths) {

            // TODO: parallelize this section
            // -----------------------------------
            Map<String, JsonNode> nodesPlainMap = readJsonFileToPlainMap(filePath);

            String sortedEntriesAsString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(nodesPlainMap);
            Path targetFilePath = Paths.get(filePath);
            String newFileName = targetFilePath.getFileName().toString();
            newFileName = newFileName.substring(0, newFileName.lastIndexOf(".json")) + " (sorted).json";
            writeToFile(targetFilePath.getParent(), newFileName, sortedEntriesAsString);
            // -----------------------------------

        }

        System.out.println("Done!");

    }

    private static void findUniqueEntriesForEachFile(String firstFilePath, String secondFilePath) throws IOException {

        initGlobalObjectsIfNeeded();

        System.out.println("Reading & aligning files...");
        // TODO: parallelize this section
        // -----------------------------------
        Map<String, JsonNode> firstNodesPlainMap = readJsonFileToPlainMap(firstFilePath);
        Map<String, JsonNode> secondNodesPlainMap = readJsonFileToPlainMap(secondFilePath);
        // -----------------------------------

        if (firstNodesPlainMap == null || secondNodesPlainMap == null) {
            throw new RuntimeException("Cannot align keys in one or both files :(");
        }

        System.out.println("Comparing files...");
        Map<String, JsonNode>[] uniqueEntries = findUniqueEntries(firstNodesPlainMap, secondNodesPlainMap);

        System.out.println("Writing files to disk...");
        // TODO: parallelize this section
        // -----------------------------------
        Path firstFilePathObj = Paths.get(firstFilePath);
        String newFirstFileName = firstFilePathObj.getFileName().toString();
        newFirstFileName = newFirstFileName.substring(0, newFirstFileName.lastIndexOf(".json")) + " (unique).json";

        String firstFileUniqueEntries = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(uniqueEntries[0]);
        writeToFile(firstFilePathObj.getParent(), newFirstFileName, firstFileUniqueEntries);

        Path secondFilePathObj = Paths.get(secondFilePath);
        String newSecondFileName = secondFilePathObj.getFileName().toString();
        newSecondFileName = newSecondFileName.substring(0, newSecondFileName.lastIndexOf(".json")) + " (unique).json";

        String secondFileUniqueEntries = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(uniqueEntries[1]);
        writeToFile(secondFilePathObj.getParent(), newSecondFileName, secondFileUniqueEntries);
        // -----------------------------------

        System.out.println("Done!");

    }

    private static void removeExcludedKeysFromEachFile(String excludedKeysFilePath, String... targetFilesPaths) throws IOException {

        initGlobalObjectsIfNeeded();

        System.out.println("Parsing input files...");
        Set<String> keysToExclude = readJsonFileToPlainMap(excludedKeysFilePath).keySet();
        Set<String> uniqueTargetFilePaths = Arrays.stream(targetFilesPaths).collect(Collectors.toSet());

        System.out.println("Removing entries from the files...");
        for (String filePath : uniqueTargetFilePaths) {

            // TODO: parallelize this section
            // -----------------------------------
            Map<String, JsonNode> nodesPlainMap = readJsonFileToPlainMap(filePath);

            if (nodesPlainMap != null) {
                for (String keyToExclude : keysToExclude) {
                    nodesPlainMap.remove(keyToExclude);
                }
            }

            String entriesWithoutExcludedKeysAsString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(nodesPlainMap);
            Path targetFilePath = Paths.get(filePath);
            String newFileName = targetFilePath.getFileName().toString();
            newFileName = newFileName.substring(0, newFileName.lastIndexOf(".json")) + " (clean).json";
            writeToFile(targetFilePath.getParent(), newFileName, entriesWithoutExcludedKeysAsString);
            // -----------------------------------

        }

        System.out.println("Done!");

    }

    private static void mergeFiles(String... filesPaths) throws IOException {

        initGlobalObjectsIfNeeded();

        System.out.println("Merging files...");

        Map<String, JsonNode> mergedMap = new TreeMap<>();

        // TODO: parallelize this section
        // -----------------------------------
        for (String filePath : filesPaths) {
            Map<String, JsonNode> plainMap = readJsonFileToPlainMap(filePath);
            mergedMap.putAll(plainMap);
        }
        // -----------------------------------

        System.out.println("Writing files to the disk...");

        String mergedEntriesAsString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mergedMap);
        Path targetFilePath = Paths.get(filesPaths[0]).getParent();
        writeToFile(targetFilePath, "merged.json", mergedEntriesAsString);

        System.out.println("Done!");

    }

    @SafeVarargs
    private static void findInclusions(String sourceFilePath, Pair<String, String>... sets) throws IOException {

        initGlobalObjectsIfNeeded();

        System.out.println("Finding inclusions...");

        Set<String> sourceKeySet = readJsonFileToPlainMap(sourceFilePath).keySet();
        Map<String, String> resultMap = new TreeMap<>();

        // TODO: parallelize this section
        // -----------------------------------
        for (Pair<String, String> set : sets) {

            System.out.println(MessageFormat.format("Checking \"{0}\" set...", set.getKey()));

            Pair<String, Set<String>> namedSetPlainMap = new Pair<>(set.getKey(), readJsonFileToPlainMap(set.getValue()).keySet());

            for (String currentSourceKey : sourceKeySet) {
                if (namedSetPlainMap.getValue().contains(currentSourceKey)) {
                    resultMap.put(currentSourceKey, namedSetPlainMap.getKey());
                }
            }

        }
        // -----------------------------------

        System.out.println("Completed, writing result file to disk...");

        String resultJsonAsString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultMap);
        Path targetFilePath = Paths.get(sourceFilePath);
        String newFileName = targetFilePath.getFileName().toString();
        newFileName = newFileName.substring(0, newFileName.lastIndexOf(".json")) + " (inclusions).json";
        writeToFile(targetFilePath.getParent(), newFileName, resultJsonAsString);

        System.out.println("Done!");

    }

    public static Map<String, JsonNode> getSortedPlainMapOfNodes(JsonNode rootNode) {

        Map<String, JsonNode> result = new HashMap<>();

        if (!rootNode.isObject() || rootNode.isEmpty()) {
            return result;
        }

        Iterator<Map.Entry<String, JsonNode>> fieldsIterator = rootNode.fields();

        while (fieldsIterator.hasNext()) {
            Map.Entry<String, JsonNode> fieldEntry = fieldsIterator.next();
            result.putAll(getChildNodesPlainMap(fieldEntry.getKey(), fieldEntry.getValue(), null));
        }

        result = result.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (oldValue, newValue) -> newValue,
                                LinkedHashMap::new
                        )
                );

        return result;

    }

    private static Map<String, JsonNode> getChildNodesPlainMap(String nodeName, JsonNode node, Map<String, JsonNode> accumulator) {

        if (accumulator == null) {
            accumulator = new HashMap<>();
        }

        if (node.isObject()) {

            Iterator<Map.Entry<String, JsonNode>> childNodesIterator = node.fields();

            while (childNodesIterator.hasNext()) {

                Map.Entry<String, JsonNode> childNode = childNodesIterator.next();
                String childNodeName = childNode.getKey();
                childNodeName = nodeName + "." + childNodeName;
                JsonNode childNodeValue = childNode.getValue();

                getChildNodesPlainMap(childNodeName, childNodeValue, accumulator);

            }

        } else {
            accumulator.put(nodeName, node);
        }

        return accumulator;

    }

    @SuppressWarnings("unchecked")
    private static Map<String, JsonNode>[] findUniqueEntries(Map<String, JsonNode> firstMapOfNodes,
                                                             Map<String, JsonNode> secondMapOfNodes) {

        LinkedList<Map.Entry<String, JsonNode>> firstListOfNodes = new LinkedList<>(firstMapOfNodes.entrySet());
        ListIterator<Map.Entry<String, JsonNode>> firstListIterator = firstListOfNodes.listIterator();

        LinkedList<Map.Entry<String, JsonNode>> secondListOfNodes = new LinkedList<>(secondMapOfNodes.entrySet());
        ListIterator<Map.Entry<String, JsonNode>> secondListIterator = secondListOfNodes.listIterator();

        while (firstListIterator.hasNext() && secondListIterator.hasNext()) {

            String firstKeyToCompare = firstListIterator.next().getKey();
            String secondKeyToCompare = secondListIterator.next().getKey();

            if (firstKeyToCompare.equalsIgnoreCase(secondKeyToCompare)) {
                firstListIterator.remove();
                secondListIterator.remove();
            } else if (firstKeyToCompare.compareToIgnoreCase(secondKeyToCompare) < 0) {
                // firstKeyToCompare is unique
                secondListIterator.previous();
            } else {
                // secondKeyToCompare is unique
                firstListIterator.previous();
            }

        }

        LinkedHashMap<String, JsonNode> firstMapOfUniqueNodes = firstListOfNodes.stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (oldValue, newValue) -> newValue,
                        LinkedHashMap::new
                ));

        LinkedHashMap<String, JsonNode> secondMapOfUniqueNodes = secondListOfNodes.stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (oldValue, newValue) -> newValue,
                        LinkedHashMap::new
                ));

        return (Map<String, JsonNode>[]) new Map<?, ?>[] {firstMapOfUniqueNodes, secondMapOfUniqueNodes};

    }

    private static void initGlobalObjectsIfNeeded() {
        if (mapper == null) {
            mapper = new ObjectMapper();
        }
        if (reader == null) {
            reader = mapper.reader().withFeatures(JsonReadFeature.ALLOW_TRAILING_COMMA);
        }
    }

    public static JsonNode readJsonTreeByFilePath(String path) throws IOException {

        initGlobalObjectsIfNeeded();

        JsonNode rootNode;
        try (FileInputStream inputStream = new FileInputStream(Paths.get(path).toAbsolutePath().toString())) {
            rootNode = reader.readTree(inputStream);
        }

        return rootNode;

    }

    private static Map<String, JsonNode> readJsonFileToPlainMap(String filePath) throws IOException {

        initGlobalObjectsIfNeeded();

        JsonNode rootJsonNode = readJsonTreeByFilePath(filePath);
        return getSortedPlainMapOfNodes(rootJsonNode);

    }

    public static void writeToFile(Path pathToParentDir, String newFileName, String content) throws IOException {

        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(pathToParentDir.toString() + "/" + newFileName, false),
                        StandardCharsets.UTF_8
                )
        );

        writer.write(content);
        writer.flush();
        writer.close();

    }

}
