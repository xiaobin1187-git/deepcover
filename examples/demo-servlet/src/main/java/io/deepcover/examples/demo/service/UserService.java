package io.deepcover.examples.demo.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple user service for demo.
 * DeepCover will record line execution for each method.
 */
public class UserService {

    private Map<String, Map<String, String>> users = new HashMap<>();

    public UserService() {
        Map<String, String> user = new HashMap<>();
        user.put("id", "1");
        user.put("name", "Alice");
        user.put("email", "alice@example.com");
        users.put("1", user);
    }

    public String listUsers() {
        List<Map<String, String>> list = new ArrayList<>(users.values());
        return toJson("list", list);
    }

    public String getUser(String id) {
        if (id == null || id.isEmpty()) {
            return toJsonError("id is required");
        }
        Map<String, String> user = users.get(id);
        if (user == null) {
            return toJsonError("user not found: " + id);
        }
        return toJson("get", user);
    }

    public String createUser(String name, String email) {
        if (name == null || name.isEmpty()) {
            return toJsonError("name is required");
        }
        if (email == null || email.isEmpty()) {
            return toJsonError("email is required");
        }
        String id = String.valueOf(users.size() + 1);
        Map<String, String> user = new HashMap<>();
        user.put("id", id);
        user.put("name", name);
        user.put("email", email);
        users.put(id, user);
        return toJson("create", user);
    }

    public String deleteUser(String id) {
        if (id == null || id.isEmpty()) {
            return toJsonError("id is required");
        }
        Map<String, String> removed = users.remove(id);
        if (removed == null) {
            return toJsonError("user not found: " + id);
        }
        return toJson("delete", removed);
    }

    private String toJson(String action, Object data) {
        return "{\"action\":\"" + action + "\",\"data\":" + data.toString().replace("=", "\":\"").replace(", ", "\",\"") + "}";
    }

    private String toJsonError(String message) {
        return "{\"error\":\"" + message + "\"}";
    }
}
