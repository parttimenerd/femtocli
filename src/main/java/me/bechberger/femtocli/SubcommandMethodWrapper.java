package me.bechberger.femtocli;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * Wrapper for method-based subcommands.
 */
final class SubcommandMethodWrapper implements Callable<Integer> {
    final Object parent;
    final Method method;

    SubcommandMethodWrapper(Object parent, Method method) {
        this.parent = parent;
        this.method = method;
    }

    @Override
    public Integer call() throws Exception {
        Object result = method.invoke(parent);
        return result instanceof Integer i ? i : 0;
    }
}