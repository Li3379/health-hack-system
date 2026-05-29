package com.hhs.config;

import com.hhs.entity.Achievement;
import com.hhs.mapper.AchievementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 成就种子数据初始化
 * 应用启动时检查成就表是否为空，若为空则插入默认成就。
 * 如果表不存在（如数据库未初始化），则安全跳过。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AchievementSeedRunner implements CommandLineRunner {

    private final AchievementMapper achievementMapper;

    @Override
    public void run(String... args) {
        try {
            Long count = achievementMapper.selectCount(null);
            if (count != null && count > 0) {
                log.debug("Achievements table already has {} records, skipping seed", count);
                return;
            }
        } catch (Exception e) {
            log.warn("Achievement table not available, skipping seed: {}", e.getMessage());
            return;
        }

        List<Achievement> seeds = List.of(
                createAchievement("first_record", "初次记录", "记录第一条健康数据", "clipboard-check", 10),
                createAchievement("streak_7", "坚持一周", "连续记录7天", "fire", 50),
                createAchievement("streak_30", "月度坚持", "连续记录30天", "trophy", 200),
                createAchievement("first_ai_chat", "AI初体验", "首次使用AI健康助手", "robot", 20),
                createAchievement("score_90", "健康达人", "健康评分达到90分", "star", 100)
        );

        for (Achievement achievement : seeds) {
            achievementMapper.insert(achievement);
        }
        log.info("Seeded {} achievements", seeds.size());
    }

    private Achievement createAchievement(String code, String name, String description,
                                          String icon, int points) {
        Achievement a = new Achievement();
        a.setCode(code);
        a.setName(name);
        a.setDescription(description);
        a.setIcon(icon);
        a.setPoints(points);
        a.setCreatedAt(LocalDateTime.now());
        return a;
    }
}
