package me.bechberger.femtocli;

import me.bechberger.femtocli.annotations.Command;

import java.lang.reflect.InvocationTargetException;
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

    /** Returns the @Command annotation from the wrapped method, or null. */
    Command methodCommand() {
        return method.getAnnotation(Command.class);
    }

    @Override
    public Integer call() throws Exception {
        Object result;
        try {
            result = method.invoke(parent);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) throw ex;
            if (cause instanceof Error er) throw er;
            throw e;
        }
        if (result instanceof Integer i) return i;
        if (result == null) return 0;
        throw new IllegalStateException(
            "Command method " + method.getName() + " must return Integer or void, got " + result.getClass().getName());
    }
}