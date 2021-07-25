package com.jsonanalyzer.main;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) {

        if (args.length < 2) {
            throw new IllegalArgumentException(
                    MessageFormat.format("Not enough paths to files for comparison: {0}/2", args.length)
            );
        }

        String firstFilePath = args[0];
        File firstFile = Paths.get(firstFilePath).toFile();

        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader()
                .withFeatures(JsonReadFeature.ALLOW_TRAILING_COMMA);

        System.out.println("Straining & aligning keys...");

        Map<String, JsonNode> firstNodesPlainMap = null;
        try {
            JsonNode rootJsonNode = reader.readTree(new FileInputStream(firstFile));
            firstNodesPlainMap = getPlainSortedMapOfNodes(rootJsonNode);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String secondFilePath = args[1];
        File secondFile = Paths.get(secondFilePath).toFile();

        Map<String, JsonNode> secondNodesPlainMap = null;
        try {
            JsonNode rootJsonNode = reader.readTree(new FileInputStream(secondFile));
            secondNodesPlainMap = getPlainSortedMapOfNodes(rootJsonNode);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (firstNodesPlainMap == null || secondNodesPlainMap == null) {
            return;
        }
        Map<String, JsonNode>[] uniqueEntries = findUniqueEntries(firstNodesPlainMap, secondNodesPlainMap);

        System.out.println("Unique keys: 1st file (" + uniqueEntries[0].size() + ")");
        System.out.println(uniqueEntries[0]);
        System.out.println("Second file: (" + uniqueEntries[1].size() + ")");
        System.out.println(uniqueEntries[1]);
        System.out.println("Writing files to disk...");

        try {

            FileWriter fileWriter = new FileWriter(Paths.get(firstFilePath).getParent().toString() + "\\result_1.json", false);
            fileWriter.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(uniqueEntries[0]));
            fileWriter.flush();

            fileWriter = new FileWriter(Paths.get(secondFilePath).getParent().toString() + "\\result_2.json", false);
            fileWriter.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(uniqueEntries[1]));
            fileWriter.flush();

            fileWriter.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

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

        System.out.println("Comparing files...");

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

}
