package com.dazzle.asklepios.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

@Service
public class EnumRegistry {
    private static final Logger LOG = Logger.getLogger(EnumRegistry.class.getName());

    private static final String ENUMS_PACKAGE = "com.dazzle.asklepios.domain.enumeration";

    private final Map<String, List<String>> enumsByName;

    public EnumRegistry() {
        this.enumsByName = Collections.unmodifiableMap(scanEnums(ENUMS_PACKAGE));
        LOG.info(() -> "[EnumRegistry] found enums: " + enumsByName.keySet());
    }

    public Map<String, List<String>> getAll() {
        return enumsByName;
    }

    private Map<String, List<String>> scanEnums(String basePackage) {
        Map<String, List<String>> result = new TreeMap<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        String path = "classpath*:" + basePackage.replace('.', '/') + "/**/*.class";
        try {
            Resource[] resources = resolver.getResources(path);
            for (Resource r : resources) {
                String className = toClassName(basePackage, r);
                if (className == null) continue;

                try {
                    Class<?> clazz = Class.forName(className);
                    String simple = clazz.getSimpleName();

                    if (clazz.isEnum() && !simple.endsWith("Converter")) {
                        Object[] constants = clazz.getEnumConstants();
                        List<String> names = new ArrayList<>(constants.length);
                        for (Object c : constants) names.add(((Enum<?>) c).name());
                        result.put(simple, Collections.unmodifiableList(names));
                    }
                } catch (ClassNotFoundException ignored) {
                }
            }
        } catch (IOException e) {
            LOG.warning("Failed to scan enums: " + e.getMessage());
        }
        return result;
    }

    private String toClassName(String basePackage, Resource r) {
        try {
            String url = r.getURL().toString();
            int idx = url.indexOf(basePackage.replace('.', '/'));
            if (idx < 0) return null;
            String tail = url.substring(idx).replace('/', '.');
            return tail.substring(0, tail.length() - ".class".length());
        } catch (IOException e) {
            return null;
        }
    }
}
