package com.industrial.agent.rag;

/**
 * ThreadLocal holder for tenant/user context propagation to @Tool methods.
 * Set before LLM call, cleared in finally block after call completes.
 */
public class RagContextHolder {

    private static final ThreadLocal<String> tenantIdHolder = new ThreadLocal<>();
    private static final ThreadLocal<String> userIdHolder = new ThreadLocal<>();

    private RagContextHolder() {}

    public static void set(String tenantId, String userId) {
        tenantIdHolder.set(tenantId != null ? tenantId : "default");
        userIdHolder.set(userId != null ? userId : "anonymous");
    }

    public static String getTenantId() {
        return tenantIdHolder.get();
    }

    public static String getUserId() {
        return userIdHolder.get();
    }

    public static void clear() {
        tenantIdHolder.remove();
        userIdHolder.remove();
    }
}
