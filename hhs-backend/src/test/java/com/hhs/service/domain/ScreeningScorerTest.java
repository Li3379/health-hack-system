package com.hhs.service.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.entity.ExaminationReport;
import com.hhs.mapper.ExaminationReportMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScreeningScorer Unit Tests")
class ScreeningScorerTest {

    @Mock
    private ExaminationReportMapper examinationReportMapper;

    @InjectMocks
    private ScreeningScorer scorer;

    @Test
    @DisplayName("Should return 50 when reportDate is null")
    void shouldReturn50WhenReportDateIsNull() {
        // Arrange
        Long userId = 1L;
        ExaminationReport report = new ExaminationReport();
        report.setId(1L);
        report.setUserId(userId);
        report.setReportDate(null); // No date set

        when(examinationReportMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(report));

        // Act
        int score = scorer.calculate(userId);

        // Assert
        assertEquals(50, score, "Report without date should get conservative score of 50");
        verify(examinationReportMapper).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("Should return 100 when reportDate is within 6 months")
    void shouldReturn100WhenReportDateIsRecent() {
        // Arrange
        Long userId = 1L;
        ExaminationReport report = new ExaminationReport();
        report.setId(1L);
        report.setUserId(userId);
        report.setReportDate(LocalDate.now().minusMonths(3)); // 3 months ago

        when(examinationReportMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(report));

        // Act
        int score = scorer.calculate(userId);

        // Assert
        assertEquals(100, score, "Recent report should get full score");
        verify(examinationReportMapper).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("Should return 70 when reportDate is older than 6 months")
    void shouldReturn70WhenReportDateIsOld() {
        // Arrange
        Long userId = 1L;
        ExaminationReport report = new ExaminationReport();
        report.setId(1L);
        report.setUserId(userId);
        report.setReportDate(LocalDate.now().minusMonths(12)); // 12 months ago

        when(examinationReportMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(report));

        // Act
        int score = scorer.calculate(userId);

        // Assert
        assertEquals(70, score, "Old report should get reduced score");
        verify(examinationReportMapper).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("Should return 50 when user has no reports")
    void shouldReturn50WhenUserHasNoReports() {
        // Arrange
        Long userId = 1L;

        when(examinationReportMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        // Act
        int score = scorer.calculate(userId);

        // Assert
        assertEquals(50, score, "User without reports should get base score of 50");
        verify(examinationReportMapper).selectList(any(LambdaQueryWrapper.class));
    }
}