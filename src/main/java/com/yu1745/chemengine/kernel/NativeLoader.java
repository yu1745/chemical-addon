package com.yu1745.chemengine.kernel;

import com.sun.jna.Native;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 定位并加载 iphreeqc 原生库。
 *
 * <p>查找顺序：
 * <ol>
 *   <li>系统属性 {@code chemengine.native.dir}（指向库文件所在目录或库文件本身）——开发/CI 覆盖用</li>
 *   <li>classpath 资源 {@code /native/<plat>/<libName>}——fat jar 分发形态，解压到临时目录后
 *       {@link System#load}（sqlite-jdbc / lwjgl 模式）</li>
 *   <li>工作目录 {@code native/<plat>/<libName>}——本仓库开发态（native/ 不入 git）</li>
 * </ol>
 */
public final class NativeLoader {

    private static final AtomicReference<IPhreeqcLib> LOADED = new AtomicReference<>();

    private NativeLoader() {}

    public static IPhreeqcLib lib() {
        IPhreeqcLib existing = LOADED.get();
        if (existing != null) {
            return existing;
        }
        synchronized (NativeLoader.class) {
            if (LOADED.get() == null) {
                LOADED.set(Native.load(locate().toAbsolutePath().toString(), IPhreeqcLib.class));
            }
            return LOADED.get();
        }
    }

    static String platform() {
        String os = System.getProperty("os.name", "?").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "?").toLowerCase(Locale.ROOT);
        String plat = arch.contains("aarch64") || arch.contains("arm64") ? "aarch64" : "x86_64";
        if (os.contains("win")) {
            return "win-" + plat;
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "macos-" + plat;
        }
        return "linux-" + plat;
    }

    static String libName() {
        return switch (platform().substring(0, platform().indexOf('-'))) {
            case "win" -> "libIPhreeqc.dll";
            case "macos" -> "libIPhreeqc.dylib";
            default -> "libIPhreeqc.so";
        };
    }

    private static Path locate() {
        String name = libName();
        String plat = platform();

        String override = System.getProperty("chemengine.native.dir");
        if (override != null) {
            Path p = Paths.get(override);
            Path lib = Files.isRegularFile(p) ? p : p.resolve(name);
            if (Files.isRegularFile(lib)) {
                return lib;
            }
            throw new IllegalStateException("chemengine.native.dir 指定处无 " + name + ": " + override);
        }

        String resource = "/native/" + plat + "/" + name;
        try (InputStream in = NativeLoader.class.getResourceAsStream(resource)) {
            if (in != null) {
                // Windows 不允许覆盖已加载 DLL：文件名带纳秒后缀，重复加载（同 JVM 内被
                // LOADED 缓存挡住；跨类加载器的测试重跑各自拿到新文件）不会互相踩。
                Path tmp = Files.createTempDirectory("chemengine-native");
                Path lib = tmp.resolve(name);
                Files.copy(in, lib, StandardCopyOption.REPLACE_EXISTING);
                lib.toFile().deleteOnExit();
                tmp.toFile().deleteOnExit();
                return lib;
            }
        } catch (IOException e) {
            throw new IllegalStateException("解压 " + resource + " 失败", e);
        }

        Path cwd = Paths.get("native", plat, name);
        if (Files.isRegularFile(cwd)) {
            return cwd;
        }

        throw new IllegalStateException(
                "找不到 iphreeqc 原生库 (" + plat + "/" + name + ")："
                        + "classpath 无 " + resource + "，工作目录无 native/" + plat + "/。"
                        + "可先用 -Dchemengine.native.dir=<目录> 指定。");
    }
}
