package com.everyarrow.eventengine.core.hooks;

import com.everyarrow.eventengine.boot.Engine;
import com.everyarrow.eventengine.core.services.runtime.ServiceCall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Central registry and executor for all ServiceHooks.
 */
public class HookManager {

    private static final Logger logger = Logger.getLogger(HookManager.class.getName());
    private final List<ServiceHook> hooks = Collections.synchronizedList(new ArrayList<>());

    public HookManager(Engine engine) {
        // engine currently not used, but kept for future needs and compatibility
    }

    /** Registers a new hook instance. */
    public void register(ServiceHook hook) {
        hooks.add(hook);
    }

    /** Returns true when a hook of the given type is currently registered. */
    public boolean hasHook(Class<? extends ServiceHook> hookType) {
        if (hookType == null) {
            return false;
        }

        for (ServiceHook hook : snapshot()) {
            if (hookType.isInstance(hook)) {
                return true;
            }
        }

        return false;
    }

    /** Returns registered hook types in the order before() will execute them. */
    public List<Class<? extends ServiceHook>> hookTypes() {
        List<Class<? extends ServiceHook>> types = new ArrayList<>();
        for (ServiceHook hook : snapshot()) {
            types.add(hook.getClass());
        }
        return Collections.unmodifiableList(types);
    }

    /** Execute all before() hooks in registration order. */
    public void before(ServiceCall call) throws Exception {
        for (ServiceHook hook : snapshot()) {
            try {
                hook.before(call);
            } catch (Exception e) {
                logger.warning("Hook before() failed: " + hook.getClass().getSimpleName() + " - " + e.getMessage());
                throw e; // abort handler execution
            }
        }
    }

    /** Execute all after() hooks in reverse order. */
    public void after(ServiceCall call, Object result) {
        List<ServiceHook> reversed = snapshot();
        Collections.reverse(reversed);
        for (ServiceHook hook : reversed) {
            try {
                hook.after(call, result);
            } catch (Exception e) {
                logger.warning("Hook after() failed: " + hook.getClass().getSimpleName() + " - " + e.getMessage());
            }
        }
    }

    /**
     * Execute all onError() hooks in reverse order.
     * Errors are logged and swallowed.
     */
    public void error(ServiceCall call, Exception ex) {
        List<ServiceHook> reversed = snapshot();
        Collections.reverse(reversed);
        for (ServiceHook hook : reversed) {
            try {
                hook.onException(call, ex);
            } catch (Exception e) {
                logger.warning("Hook onError() failed: " + hook.getClass().getSimpleName() + " - " + e.getMessage());
            }
        }
    }

    /** Returns a thread-safe snapshot of the current hook list. */
    private List<ServiceHook> snapshot() {
        synchronized (hooks) {
            return new ArrayList<>(hooks);
        }
    }
}
