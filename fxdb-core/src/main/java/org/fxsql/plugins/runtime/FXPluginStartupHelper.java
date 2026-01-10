package org.fxsql.plugins.runtime;

import org.fxsql.plugins.FXPlugin;
import org.fxsql.plugins.plugindepdency.FXDependencyInstance;
import org.fxsql.plugins.plugindepdency.FXPluginDependencyFactory;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


final class FXPluginStartupHelper {

    public static List<Pair> loadDependencies(String[] dependencyClasses) {
        return Arrays.stream(dependencyClasses)
                .map(el -> toDependencyFactory(el))
                .filter(dependencyClass -> dependencyClass.isPresent())
                .flatMap(pluginClass -> toDependencyInstance(pluginClass.get()).stream())
                .collect(Collectors.toList());
    }
    private static Optional<Class<FXPluginDependencyFactory>> toDependencyFactory(String name) {
        try {
            Class candidateClass = Class.forName(name);
            Annotation annotation = candidateClass.getDeclaredAnnotation(FXPluginDependencyFactory.class);
            if (annotation != null) {
                return Optional.of(candidateClass);
            } else {
                return Optional.empty();
            }
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
    private static List<FXPluginStartupHelper.Pair> toDependencyInstance(Class dependencyClass) {
        final Optional<Object> instance = getInstance(dependencyClass);
        if (!instance.isPresent()) {
            return new ArrayList<>();
        }
        return Arrays.stream(dependencyClass.getDeclaredMethods())
                .filter(el -> el.isAnnotationPresent(FXDependencyInstance.class))
                .map(el -> {
                    try {
                        FXPluginStartupHelper.Pair pair = new Pair(el.getName(), el.invoke(instance.get()));
                        return Optional.of(pair);
                    } catch (Exception ex) {
                        return Optional.empty();
                    }
                })
                .filter(el -> el.isPresent())
                .map(el ->  (FXPluginStartupHelper.Pair) el.get())
                .collect(Collectors.toList());
    }
    private static Optional<Object> getInstance(Class dependencyClass) {
        try {
            return Optional.of(dependencyClass.getConstructors()[0].newInstance());
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }
    public static List<Class<FXPlugin>> loadPlugins(String[] pluginClasses) {
        return Arrays.stream(pluginClasses)
                .map(el -> toPlugin(el))
                .filter(pluginClass -> pluginClass.isPresent())
                .map(pluginClass -> pluginClass.get())
                .collect(Collectors.toList());
    }
    private static Optional<Class<FXPlugin>> toPlugin(String name) {
        try {
            Class candidateClass = Class.forName(name);
            Annotation annotation = candidateClass.getDeclaredAnnotation(FXPlugin.class);
            if (annotation != null) {
                return Optional.of(candidateClass);
            } else {
                return Optional.empty();
            }
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
    public static class Pair {
        private String name;
        private Object instance;

        public Pair(String name, Object instance) {
            this.name = name;
            this.instance = instance;
        }
        public String getName() {
            return name;
        }
        public Object getInstance() {
            return instance;
        }
    }
}
