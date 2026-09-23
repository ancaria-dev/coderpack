package dev.ancaria.coderpack.zygote;

import dev.ancaria.coderpack.api.Api;
import dev.ancaria.coderpack.api.ModLoadException;
import dev.ancaria.coderpack.api.ModRegistry;
import dev.ancaria.coderpack.api.SacredMod;
import dev.ancaria.coderpack.api.SacredModDescriptor;
import dev.ancaria.coderpack.api.Subscribe;
import dev.ancaria.coderpack.api.event.Gold;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loading through the binding, the registry a mod sees, and taking a mod out
 * again. The entry points are classes of this test: the jar only carries the
 * descriptor, and its class loader finds them through its parent.
 */
class ModsTest {

    @TempDir
    Path game;

    private Bus bus;
    private ModLog log;
    private Mods mods;

    @BeforeEach
    void setUp() {
        bus = new Bus();
        log = new ModLog(game.resolve("logs").resolve("mods.log"), 1024);
        log.start();
        mods = new Mods(game, bus, null, log);
        Ordered.UNLOADED.clear();
    }

    @AfterEach
    void tearDown() {
        log.close();
    }

    private Path jar(String id, Class<?> entry) throws IOException {
        Path jar = game.resolve("mods").resolve(id + ".jar");
        Files.createDirectories(jar.getParent());
        String descriptor = "id = \"" + id + "\"\n"
                + "name = \"Test " + id + "\"\n"
                + "version = \"1.2.3\"\n"
                + "entrypoint = \"" + entry.getName() + "\"\n"
                + "api = \"" + Api.VERSION + "\"\n"
                + "authors = [\"Me\", \"You\"]\n"
                + "website = \"https://ancaria.dev\"\n"
                + "repository = \"not a url\"\n";
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("META-INF/declaration.toml"));
            out.write(descriptor.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return jar;
    }

    // --- the mods under test ---------------------------------------------

    /** Uses its context as early as a subclass can: a field initialiser. */
    public static final class Eager extends SacredMod {

        final String idInInitialiser = getContext().getDescriptor().getId();
        final SacredMod currentInConstructor;
        volatile boolean loaded;
        volatile boolean unloaded;

        public Eager() {
            currentInConstructor = getContext().getRegistry().getModRegistry().getCurrentMod();
        }

        @Subscribe
        public void gold(Gold event) {
        }

        @Override
        public void onLoad() {
            loaded = true;
            getContext().getRegistry().getEventRegistry().register(this);
            getContext().getRegistry().getEventRegistry().on(Gold.class, e -> {
            });
        }

        @Override
        public void onUnload() {
            unloaded = true;
        }
    }

    /** Built with new, so nobody bound a context to it. */
    public static final class Plain extends SacredMod {
    }

    public static final class Failing extends SacredMod {

        @Override
        public void onLoad() {
            getContext().getRegistry().getEventRegistry().on(Gold.class, e -> {
            });
            throw new IllegalStateException("no");
        }
    }

    public static final class Ordered extends SacredMod {

        static final List<String> UNLOADED = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void onUnload() {
            UNLOADED.add(getContext().getDescriptor().getId());
            throw new IllegalStateException("an onUnload that throws stops nobody");
        }
    }

    public static final class NotAMod {
    }

    // --- the binding -----------------------------------------------------

    @Test
    void theContextIsThereInTheConstructor() throws Exception {
        Path jar = jar("eager", Eager.class);
        Eager mod = assertInstanceOf(Eager.class, mods.register(jar));
        assertEquals("eager", mod.idInInitialiser);
        assertSame(mod, mod.currentInConstructor);
        assertTrue(mod.loaded);

        SacredModDescriptor descriptor = mod.getContext().getDescriptor();
        assertEquals("Test eager", descriptor.getDisplayName());
        assertEquals("1.2.3", descriptor.getVersion());
        assertEquals(Eager.class.getName(), descriptor.getEntryPoint());
        assertEquals(Eager.class, descriptor.getEntryPointClass());
        assertEquals(List.of("Me", "You"), descriptor.getAuthors());
        assertThrows(UnsupportedOperationException.class, () -> descriptor.getAuthors().add("x"));
        assertEquals("https://ancaria.dev", String.valueOf(descriptor.getWebsite()));
        assertNull(descriptor.getRepository(), "a malformed URL should read as none");
        assertEquals(jar, descriptor.getPath());
        assertFalse(mod.getContext().getUptime().isNegative());
        assertInstanceOf(URLClassLoader.class,
                mod.getContext().getRegistry().getModRegistry().getClassLoader());
    }

    @Test
    void aModBuiltOutsideTheLoaderHasNoContext() {
        Plain plain = new Plain();
        IllegalStateException refused = assertThrows(IllegalStateException.class, plain::getContext);
        assertTrue(refused.getMessage().contains("not created by the loader"), refused.getMessage());
    }

    // --- the registry ----------------------------------------------------

    @Test
    void unregisteringTakesTheListenersAndCallsOnUnload() throws Exception {
        Eager mod = (Eager) mods.register(jar("eager", Eager.class));
        assertEquals(2, bus.size());
        ModRegistry registry = mod.getContext().getRegistry().getModRegistry();
        assertEquals(List.of(mod), registry.getMods());
        assertSame(mod, registry.getMod("eager"));

        assertTrue(registry.unregister("eager"));
        assertEquals(0, bus.size(), "the mod's listeners outlived it");
        assertTrue(mod.unloaded);
        assertEquals(List.of(), registry.getMods());
        assertNull(registry.getMod("eager"));
        assertFalse(registry.unregister("eager"));
    }

    @Test
    void getModsIsAnUnmodifiableSnapshotInLoadOrder() throws Exception {
        SacredMod a = mods.register(jar("a", Eager.class));
        SacredMod b = mods.register(jar("b", Eager.class));
        List<SacredMod> snapshot = a.getContext().getRegistry().getModRegistry().getMods();
        assertEquals(List.of(a, b), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(a));
        mods.unregister("a");
        assertEquals(List.of(a, b), snapshot, "the snapshot followed the registry");
        assertEquals(List.of(b), mods.instances());
    }

    @Test
    void aSecondModWithTheSameIdIsRefused() throws Exception {
        Path jar = jar("twice", Eager.class);
        mods.register(jar);
        ModLoadException refused = assertThrows(ModLoadException.class, () -> mods.register(jar));
        assertTrue(refused.getMessage().contains("already loaded"), refused.getMessage());
        assertEquals(1, mods.instances().size());
    }

    @Test
    void anythingButAModIsRefused() throws Exception {
        Path notAJar = game.resolve("nothing.jar");
        Files.writeString(notAJar, "not a zip");
        assertThrows(ModLoadException.class, () -> mods.register(notAJar));
        assertThrows(ModLoadException.class, () -> mods.register(jar("odd", NotAMod.class)));
        assertEquals(List.of(), mods.instances());
    }

    @Test
    void aModWhoseOnLoadThrowsIsNotLeftHalfLoaded() throws Exception {
        ModLoadException failed = assertThrows(ModLoadException.class,
                () -> mods.register(jar("failing", Failing.class)));
        assertInstanceOf(IllegalStateException.class, failed.getCause());
        assertEquals(0, bus.size(), "a listener registered before the failure stayed");
        assertEquals(List.of(), mods.instances());
    }

    @Test
    void loadAllSkipsWhatTheLauncherDidNotEnable() throws Exception {
        jar("on", Eager.class);
        jar("off", Eager.class);
        mods.loadAll(game.resolve("mods"), Set.of("on"));
        assertEquals(1, mods.instances().size());
        assertNotNull(mods.instance("on"));
        assertNull(mods.instance("off"));
    }

    @Test
    void shutdownUnloadsEveryModNewestFirst() throws Exception {
        mods.register(jar("first", Ordered.class));
        mods.register(jar("second", Ordered.class));
        mods.register(jar("third", Ordered.class));
        mods.shutdown();
        assertEquals(List.of("third", "second", "first"), Ordered.UNLOADED);
        assertEquals(List.of(), mods.instances());
    }

    // --- the context -----------------------------------------------------

    @Test
    void logGoesToTheSharedFileWithTheModsId() throws Exception {
        SacredMod mod = mods.register(jar("talker", Eager.class));
        mod.getContext().log("hello");
        assertTrue(log.flush(10_000));
        List<String> lines = Files.readAllLines(log.file(), StandardCharsets.UTF_8);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).matches("\\[[-0-9 :.]{23}] \\[talker]: hello"), lines.get(0));
    }

    @Test
    void theHandlesAModRegisteredNameIt() throws Exception {
        Eager mod = (Eager) mods.register(jar("owner", Eager.class));
        var handles = mod.getContext().getRegistry().getEventRegistry().getEvents();
        assertEquals(2, handles.size());
        for (var handle : handles) {
            assertSame(mod, handle.getMod());
            assertEquals(Gold.class, handle.getEventType());
        }
        assertEquals(Map.of("gold", Eager.class),
                Map.of(handles.get(0).getMethodName(), handles.get(0).getListenerClass()));
        assertNull(handles.get(1).getMethodName());
    }
}
