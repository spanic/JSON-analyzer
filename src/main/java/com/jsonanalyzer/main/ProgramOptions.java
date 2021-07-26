package com.jsonanalyzer.main;

public enum ProgramOptions {

    COMPARE("-compare"),
    CLEANUP("-cleanup"),
    MERGE("-merge"),
    FIND("-find");

    private final String name;

    ProgramOptions(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
