package io.izzel.arclight.common.mixin.bukkit;

import io.izzel.arclight.common.bridge.bukkit.JavaPluginLoaderBridge;
import io.izzel.arclight.common.bridge.bukkit.PluginClassLoaderBridge;
import io.izzel.arclight.common.mod.server.ArclightServer;
import io.izzel.arclight.common.mod.util.EventExecutorGenerator;
import io.izzel.arclight.i18n.ArclightConfig;
import org.apache.commons.lang3.Validate;
import org.bukkit.Server;
import org.bukkit.Warning;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.*;
import java.util.logging.Level;

@Mixin(value = JavaPluginLoader.class, remap = false)
public abstract class JavaPluginLoaderMixin implements JavaPluginLoaderBridge {

    // @formatter:off
    @Shadow @Final Server server;
    @Invoker("setClass") public abstract void bridge$setClass(final String name, final Class<?> clazz);
    @Invoker("getClassByName") public abstract Class<?> arclight$getClassByName(String name, boolean resolve, PluginDescriptionFile description);
    @Accessor("loaders") public abstract<T extends URLClassLoader & PluginClassLoaderBridge> List<T> arclight$getLoaders();
    // @formatter:on

    /**
     * @author InitAuther97
     * @reason Support plugin class loader isolation
     */
    @Overwrite
    Class<?> getClassByName(String name, boolean resolve, PluginDescriptionFile description) {
        SimplePluginManager manager = (SimplePluginManager) this.server.getPluginManager();
        if (ArclightConfig.spec().getCompat().isIsolatedPluginClassLoaders(name)) {
            Set<String> loaders = ArclightServer.iterateDepends(description);
            for (PluginClassLoaderBridge loader : arclight$getLoaders()) {
                PluginDescriptionFile desc = loader.arclight$desc();
                if (loaders.contains(desc.getName()) || !Collections.disjoint(loaders, desc.getProvides())) {
                    try {
                        return loader.arclight$loadFromExternal(name, resolve, true);
                    } catch (ClassNotFoundException ignored) {
                    }
                }
            }
        } else {

            for (PluginClassLoaderBridge loader : arclight$getLoaders()) {
                try {
                    return loader.arclight$loadFromExternal(name, resolve, manager.isTransitiveDepend(description, loader.arclight$desc()));
                } catch (ClassNotFoundException ignored) {
                }
            }
        }

        return null;
    }

    /**
     * @author IzzelAliz
     * @reason use asm event executor
     */
    @Overwrite
    @NotNull
    public Map<Class<? extends Event>, Set<RegisteredListener>> createRegisteredListeners(@NotNull Listener listener, @NotNull Plugin plugin) {
        Validate.notNull(plugin, "Plugin can not be null");
        Validate.notNull(listener, "Listener can not be null");

        boolean useTimings = server.getPluginManager().useTimings();
        Map<Class<? extends Event>, Set<RegisteredListener>> ret = new HashMap<>();
        Set<Method> methods;
        try {
            Method[] publicMethods = listener.getClass().getMethods();
            Method[] privateMethods = listener.getClass().getDeclaredMethods();
            methods = new HashSet<>(publicMethods.length + privateMethods.length, 1.0f);
            methods.addAll(Arrays.asList(publicMethods));
            methods.addAll(Arrays.asList(privateMethods));
        } catch (NoClassDefFoundError e) {
            plugin.getLogger().severe("Plugin " + plugin.getDescription().getFullName() + " has failed to register events for " + listener.getClass() + " because " + e.getMessage() + " does not exist.");
            return ret;
        }

        for (final Method method : methods) {
            final EventHandler eh = method.getAnnotation(EventHandler.class);
            if (eh == null) continue;
            // Do not register bridge or synthetic methods to avoid event duplication
            // Fixes SPIGOT-893
            if (method.isBridge() || method.isSynthetic()) {
                continue;
            }
            final Class<?> checkClass;
            if (method.getParameterTypes().length != 1 || !Event.class.isAssignableFrom(checkClass = method.getParameterTypes()[0])) {
                plugin.getLogger().severe(plugin.getDescription().getFullName() + " attempted to register an invalid EventHandler method signature \"" + method.toGenericString() + "\" in " + listener.getClass());
                continue;
            }
            final Class<? extends Event> eventClass = checkClass.asSubclass(Event.class);
            method.setAccessible(true);
            Set<RegisteredListener> eventSet = ret.get(eventClass);
            if (eventSet == null) {
                eventSet = new HashSet<>();
                ret.put(eventClass, eventSet);
            }

            for (Class<?> clazz = eventClass; Event.class.isAssignableFrom(clazz); clazz = clazz.getSuperclass()) {
                // This loop checks for extending deprecated events
                if (clazz.getAnnotation(Deprecated.class) != null) {
                    Warning warning = clazz.getAnnotation(Warning.class);
                    Warning.WarningState warningState = server.getWarningState();
                    if (!warningState.printFor(warning)) {
                        break;
                    }
                    plugin.getLogger().log(
                        Level.WARNING,
                        String.format(
                            "\"%s\" has registered a listener for %s on method \"%s\", but the event is Deprecated. \"%s\"; please notify the authors %s.",
                            plugin.getDescription().getFullName(),
                            clazz.getName(),
                            method.toGenericString(),
                            (warning != null && warning.reason().length() != 0) ? warning.reason() : "Server performance will be affected",
                            Arrays.toString(plugin.getDescription().getAuthors().toArray())),
                        warningState == Warning.WarningState.ON ? new AuthorNagException(null) : null);
                    break;
                }
            }

            // final CustomTimingsHandler timings = new CustomTimingsHandler("Plugin: " + plugin.getDescription().getFullName() + " Event: " + listener.getClass().getName() + "::" + method.getName() + "(" + eventClass.getSimpleName() + ")", pluginParentTimer); // Spigot

            try {
                Class<? extends EventExecutor> executorClass = EventExecutorGenerator.createExecutor(method, eventClass);
                Constructor<? extends EventExecutor> constructor = executorClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                EventExecutor executor = constructor.newInstance();
                eventSet.add(new RegisteredListener(listener, executor, eh.priority(), plugin, eh.ignoreCancelled()));
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        return ret;
    }

}
