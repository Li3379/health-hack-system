package com.hhs.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构性护栏（IN-13）：每一个被 {@code @CacheEvict} 点名的缓存，都必须真的有人写过。
 *
 * <p>{@code HealthMetricServiceImpl} 曾对 {@code metrics:latest} / {@code metrics:latest:all}
 * 做 {@code @CacheEvict(allEntries = true)}，但全仓没有任何 {@code @Cacheable} 写这两个名字，
 * 也没有 {@code cacheManager.getCache("metrics:latest")} —— evict 是空操作。坏处不止是死代码：
 * 它让排查者误以为「这条写入路径的缓存已处理」，从而漏掉真正会过期的缓存（健康评分的
 * {@code health:score:v2:{userId}} 是一个普通 Redis 键，由
 * {@code HealthScoreServiceImpl.evictCachedScore} 显式删除）。
 *
 * <p>这里用源码扫描而不是运行时断言：Redis 在单测里通常不可用，而「某个名字从未被写」
 * 是个纯静态事实。
 */
@DisplayName("Spring Cache 注解与真实读写点的一致性")
class SpringCacheUsageTest {

    private static final Path MAIN = Paths.get("src/main/java");
    private static final Path APP = MAIN.resolve("com/hhs");

    /** `@Cacheable(value = "users", ...)` / `@Cacheable("users")` / `@Cacheable(value = {"a","b"})` */
    private static final Pattern CACHEABLE = Pattern.compile(
            "@Cacheable\\s*\\(([^)]*)\\)", Pattern.DOTALL);
    /** `@CacheEvict(value = {...})` / `@CacheEvict("x")` */
    private static final Pattern CACHE_EVICT = Pattern.compile(
            "@CacheEvict\\s*\\(([^)]*)\\)", Pattern.DOTALL);
    /** cacheManager.getCache("name") —— 手写的命中也算「有人用过这个 Cache」 */
    private static final Pattern GET_CACHE = Pattern.compile(
            "getCache\\s*\\(\\s*\"([^\"]+)\"\\s*\\)");
    /** 注解参数里的所有字符串字面量（含 {"a","b"} 形式） */
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]+)\"");

    @Test
    @DisplayName("每个 @CacheEvict 的缓存名都至少有一个 @Cacheable 或 getCache 写入点")
    void everyEvictedCacheNameIsActuallyWritten() throws IOException {
        List<Path> sources = listSources();

        Map<String, String> written = new LinkedHashMap<>();  // name -> 首个写入位置
        Map<String, String> evicted = new LinkedHashMap<>();  // name -> 首个 evict 位置

        for (Path file : sources) {
            String raw = read(file);
            // 注释里会提到缓存名（解释某次删除），那不是读写点 —— 换一个名字就得让注释闭嘴是错的。
            String text = stripComments(raw);
            String where = APP.relativize(file).toString().replace('\\', '/');

            for (String name : namesIn(text, CACHEABLE)) {
                written.putIfAbsent(name, where);
            }
            for (Matcher m = GET_CACHE.matcher(text); m.find(); ) {
                written.putIfAbsent(m.group(1), where);
            }
            for (String name : namesIn(text, CACHE_EVICT)) {
                evicted.putIfAbsent(name, where);
            }
        }

        Set<String> orphaned = evicted.keySet().stream()
                .filter(name -> !written.containsKey(name))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertTrue(orphaned.isEmpty(),
                "这些缓存被 @CacheEvict 点名，但从来没有任何 @Cacheable / getCache 写过它们，"
                        + "evict 是空操作（IN-13 的回归）："
                        + orphaned.stream()
                                .map(n -> n + " <- " + evicted.get(n))
                                .collect(Collectors.joining(", ")));
    }

    // ------------------------------------------------------------------

    private static List<Path> listSources() throws IOException {
        try (Stream<Path> walk = Files.walk(APP)) {
            return walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    /**
     * 抽取注解参数中的缓存名；`key = "#userId"` 这类 SpEL 不是缓存名，跳过。
     */
    private static Set<String> namesIn(String text, Pattern annotation) {
        Set<String> names = new LinkedHashSet<>();
        for (Matcher ann = annotation.matcher(text); ann.find(); ) {
            String args = ann.group(1);
            // 去掉 key / condition / unless 的值，避免把 SpEL 当缓存名
            String cleaned = args.replaceAll("(key|condition|unless)\\s*=\\s*\"[^\"]*\"", "");
            for (Matcher lit = STRING_LITERAL.matcher(cleaned); lit.find(); ) {
                String value = lit.group(1);
                // 只统计看起来像缓存名的（不含 SpEL 前缀，也不含空白）
                if (!value.startsWith("#") && !value.contains(" ")) {
                    names.add(value);
                }
            }
        }
        return names;
    }

    /**
     * 去掉注释后再扫描。
     *
     * <p>必须在剥离之后匹配：本项修复的 javadoc 里**写着**被删掉的那两个缓存名（解释为什么删），
     * 若把注释算进来，这条护栏会被自己的说明文字触发，只能逼作者把名字从注释里删掉 ——
     * 而那正是最该留下来的信息。字符串字面量要保留（注解参数就是字面量），且字面量里的
     * `//` 或 `/*` 不能当注释起点。
     */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        boolean inString = false;
        boolean inChar = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                    out.append(c);
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inString) {
                out.append(c);
                if (c == '\\' && next != '\0') {
                    out.append(next);
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                out.append(c);
                if (c == '\\' && next != '\0') {
                    out.append(next);
                    i++;
                } else if (c == '\'') {
                    inChar = false;
                }
                continue;
            }

            if (c == '/' && next == '/') {
                inLineComment = true;
                i++;
            } else if (c == '/' && next == '*') {
                inBlockComment = true;
                i++;
            } else {
                if (c == '"') {
                    inString = true;
                } else if (c == '\'') {
                    inChar = true;
                }
                out.append(c);
            }
        }
        return out.toString();
    }
}