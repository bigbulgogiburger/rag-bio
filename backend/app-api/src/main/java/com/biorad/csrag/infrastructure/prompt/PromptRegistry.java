package com.biorad.csrag.infrastructure.prompt;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PromptRegistry implements ResourceLoaderAware {

    private static final Logger log = LoggerFactory.getLogger(PromptRegistry.class);

    private final Map<String, String> prompts = new ConcurrentHashMap<>();
    private ResourceLoader resourceLoader;

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    void loadPrompts() throws IOException {
        ResourcePatternResolver resolver = ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
        Resource[] resources = resolver.getResources("classpath:prompts/*.txt");

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null) continue;

            String name = filename.substring(0, filename.length() - 4); // strip .txt
            try (InputStream is = resource.getInputStream()) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                prompts.put(name, content);
            }
        }

        log.info("prompt.registry.loaded count={} names={}", prompts.size(), prompts.keySet());
    }

    public String get(String name) {
        String prompt = prompts.get(name);
        if (prompt == null) {
            throw new IllegalArgumentException("Prompt not found: " + name);
        }
        return prompt;
    }

    public String get(String name, Map<String, String> variables) {
        String prompt = get(name);
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            prompt = prompt.replace("{{" + entry.getKey() + "}}", entry.getValue() != null ? entry.getValue() : "");
        }
        return prompt;
    }
}
