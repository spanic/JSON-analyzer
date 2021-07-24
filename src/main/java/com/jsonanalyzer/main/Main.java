package com.jsonanalyzer.main;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 1) спрямить ключи в файлах
 * 2) отсортировать файлы
 * 3) попарно сравнивать ключи, удалять совпадающие / сохранять в новый словарь уникальные
 * <p>
 * + убрать из файлов ключи, которые уже удалены (одна причина удаления - рефакторинг)
 */

public class Main {

    public static void main(String[] args) {

        /* if (args.length < 2) {
            throw new IllegalArgumentException(
                    MessageFormat.format("Not enough paths to files for comparison: {0}/2", args.length)
            );
        } */

        String firstFilePath = args[0];
        File firstFile = Paths.get(firstFilePath).toFile();

        ObjectMapper mapper = new ObjectMapper();
        try {
            /* Map<?, ?> firstFileEntriesMap = mapper.reader()
                    .withFeatures(JsonReadFeature.ALLOW_TRAILING_COMMA)
                    .readValue(firstFile, Map.class);

            for (Map.Entry<?, ?> entry: firstFileEntriesMap.entrySet()) {
                System.out.println(entry.getValue() instanceof Map) ;
            } */

            JsonNode rootJsonNode = mapper
                    .reader()
                    .withFeatures(JsonReadFeature.ALLOW_TRAILING_COMMA)
                    .readTree(new FileInputStream(firstFile));

            Map<String, JsonNode> nodesPlainMap = getPlainSortedMapOfNodes(rootJsonNode);
            System.out.println(nodesPlainMap);

        } catch (IOException e) {
            e.printStackTrace();
        }

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
                        ));

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

}
