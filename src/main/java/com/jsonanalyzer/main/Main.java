package com.jsonanalyzer.main;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import javafx.util.Pair;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    private static ObjectMapper mapper;
    private static ObjectReader reader;

    public static void main(String[] args) throws IOException {

        if (args.length < 1) {
            throw new IllegalArgumentException("Please choose one of the options: \n"
                    + "-compare {json_1} {json_2} \n"
                    + "-cleanup {json_excluded} {json_target}"
            );
        } else {
            String chosenOption = args[0];
            if (ProgramOptions.COMPARE.getName().equalsIgnoreCase(chosenOption)) {
                if (args.length != 3) {
                    throw new IllegalArgumentException(
                            MessageFormat.format("Not enough / too many files for comparison: {0}/2", args.length)
                    );
                }
                findUniqueEntriesForEachFile(args[1], args[2]);
            } else if (ProgramOptions.CLEANUP.getName().equalsIgnoreCase(chosenOption)) {
                if (args.length < 3) {
                    throw new IllegalArgumentException(
                            MessageFormat.format("Not enough files for cleanup: {0}/2+", args.length)
                    );
                }
                removeExcludedKeysFromEachFile(args[1], Arrays.copyOfRange(args, 2, args.length));
            } else if (ProgramOptions.MERGE.getName().equalsIgnoreCase(chosenOption)) {
                if (args.length < 3) {
                    throw new IllegalArgumentException(
                            MessageFormat.format("Not enough files for merge: {0}/2+", args.length)
                    );
                }
                mergeFiles(Arrays.copyOfRange(args, 1, args.length));
            } else if (ProgramOptions.FIND.getName().equalsIgnoreCase(chosenOption)) {
                if (args.length != 5) {
                    throw new IllegalArgumentException(
                            MessageFormat.format("Not enough files for finding inclusions: {0}/5", args.length)
                    );
                }
                List<Pair<String, String>> listOfNamedSets = new ArrayList<>();
                for (int i = 2; i < args.length; i++) {
                    String[] setNameAndFilePath = args[i].split("=");
                    listOfNamedSets.add(new Pair<>(setNameAndFilePath[0], setNameAndFilePath[1]));
                }
                findInclusions(args[1], listOfNamedSets.toArray(new Pair[0]));
            } else {
                throw new IllegalArgumentException("Incorrect option has been specified, please use -compare or -cleanup");
            }
        }

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

    private static Map<String, JsonNode> getPlainSortedMapOfNodes(JsonNode rootNode) {

        Map<String, JsonNode> result = new HashMap<>();
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

        return (Map<String, JsonNode>[]) new Map<?, ?>[]{firstMapOfUniqueNodes, secondMapOfUniqueNodes};

    }

    private static void initGlobalObjectsIfNeeded() {
        if (mapper == null) {
            mapper = new ObjectMapper();
        }
        if (reader == null) {
            reader = mapper.reader().withFeatures(JsonReadFeature.ALLOW_TRAILING_COMMA);
        }
    }

    private static Map<String, JsonNode> readJsonFileToPlainMap(String filePath) throws IOException {

        initGlobalObjectsIfNeeded();

        JsonNode firstRootJsonNode = JsonNodeFactory.instance.missingNode();
        try (FileInputStream inputStream = new FileInputStream(filePath)) {
            firstRootJsonNode = reader.readTree(inputStream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        Map<String, JsonNode> nodesPlainMap = new HashMap<>();
        if (firstRootJsonNode.isObject() && !firstRootJsonNode.isEmpty()) {
            nodesPlainMap = getPlainSortedMapOfNodes(firstRootJsonNode);
        }

        return nodesPlainMap;

    }

    private static void writeToFile(Path pathToParentDir, String newFileName, String content) throws IOException {

        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(pathToParentDir.toString() + "\\" + newFileName, false),
                        StandardCharsets.UTF_8
                )
        );
        writer.write(content);
        writer.flush();
        writer.close();

    }

}
