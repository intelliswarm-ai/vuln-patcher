package ai.intelliswarm.vulnpatcher.core;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ContextManager {
    
    private final Map<String, Object> sessionData = new ConcurrentHashMap<>();
    
    public void store(String key, Object value) {
        sessionData.put(key, value);
    }
    
    public Object retrieve(String key) {
        return sessionData.get(key);
    }
    
    public void clear() {
        sessionData.clear();
    }
}