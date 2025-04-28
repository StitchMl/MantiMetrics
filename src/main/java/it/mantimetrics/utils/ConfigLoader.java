package it.mantimetrics.utils;

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ConfigLoader {
    public static Map<String, String> load(String filePath) throws Exception {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(filePath)) {
            props.load(in);
        }
        Map<String, String> config = new HashMap<>();
        for (String key : props.stringPropertyNames()) {
            config.put(key, props.getProperty(key));
        }
        return config;
    }
}
