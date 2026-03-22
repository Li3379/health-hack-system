package com.hhs.service;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 OCR 识别文本中解析出检验指标（名称、数值，单位、参考范围）。
 * 支持两种格式：
 * 1. 单行格式：血糖 5.6 mmol/L 3.9-6.1 或 总胆固醇 4.2  mmol/L 参考值<5.2
 * 2. 多行格式（血常规）：红细胞计数 \n 4.95 \n 3.50-5.00 \n (x10^12/L)
 */
@Slf4j
public final class ReportTextParser {

    private static final Pattern SINGLE_LINE = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z\\s]+?)\\s+([\\d.]+)\\s*([a-zA-Z/]*)\\s*([\\d.～~-]*)?");
    private static final Pattern REF_RANGE = Pattern.compile("([\\d.]+)\\s*[～~-]\\s*([\\d.]+)");
    private static final Pattern VALUE_PATTERN = Pattern.compile("^[↓↑]?\\s*([\\d.]+)$");
    private static final Pattern UNIT_PATTERN = Pattern.compile("^\\(([^)]+)\\)$");

    public static List<LabResultItem> parse(String rawText) {
        List<LabResultItem> list = new ArrayList<>();
        if (rawText == null || rawText.isBlank()) return list;

        // 首先尝试按多行格式解析（血常规格式）
        List<LabResultItem> multiLineResults = parseMultiLine(rawText);
        if (!multiLineResults.isEmpty()) {
            return multiLineResults;
        }

        // 回退到单行格式解析
        String[] lines = rawText.split("\n");
        int order = 0;
        for (String line : lines) {
            line = line.trim();
            if (line.length() < 2) continue;
            Matcher m = SINGLE_LINE.matcher(line);
            if (m.find()) {
                String name = m.group(1).trim();
                if (name.length() > 50) continue;
                String valueStr = m.group(2);
                String unit = m.group(3) != null ? m.group(3).trim() : "";
                String refStr = m.group(4) != null ? m.group(4).trim() : "";
                if (name.isEmpty()) continue;
                boolean abnormal = isAbnormal(valueStr, refStr);
                list.add(new LabResultItem(name, valueStr, unit, refStr, abnormal, order++));
            }
        }
        return list;
    }

    /**
     * 解析多行格式的血常规报告
     * 格式：
     * 指标名称
     * 数值
     * 参考范围
     * 单位
     */
    private static List<LabResultItem> parseMultiLine(String rawText) {
        List<LabResultItem> list = new ArrayList<>();
        String[] lines = rawText.split("\n");
        int i = 0;
        int order = 0;

        while (i < lines.length) {
            String nameLine = lines[i].trim();

            // 检查是否是有效的指标名称（中文或英文）
            if (isLikelyName(nameLine) && i + 1 < lines.length) {
                String valueLine = lines[++i].trim();
                Matcher valueMatcher = VALUE_PATTERN.matcher(valueLine);

                if (valueMatcher.find()) {
                    String valueStr = valueMatcher.group(1);
                    String unit = "";
                    String refStr = "";
                    boolean abnormal = valueLine.startsWith("↓") || valueLine.startsWith("↑");

                    // 尝试获取参考范围
                    if (i + 1 < lines.length) {
                        String nextLine = lines[++i].trim();
                        Matcher refMatcher = REF_RANGE.matcher(nextLine);
                        if (refMatcher.find()) {
                            refStr = nextLine;
                        } else {
                            // 如果不是参考范围，可能是单位
                            Matcher unitMatcher = UNIT_PATTERN.matcher(nextLine);
                            if (unitMatcher.find()) {
                                unit = unitMatcher.group(1);
                            } else {
                                // 回退一行
                                i--;
                            }
                        }
                    }

                    // 尝试获取单位
                    if (i + 1 < lines.length) {
                        String nextLine = lines[++i].trim();
                        Matcher unitMatcher = UNIT_PATTERN.matcher(nextLine);
                        if (unitMatcher.find()) {
                            unit = unitMatcher.group(1);
                        } else {
                            // 回退一行
                            i--;
                        }
                    }

                    // 如果单位为空，尝试从值中提取
                    if (unit.isEmpty() && valueLine.contains("g/L")) {
                        unit = "g/L";
                    } else if (unit.isEmpty() && valueLine.contains("mmol/L")) {
                        unit = "mmol/L";
                    }

                    // 检查参考范围是否在单位行之后
                    if (refStr.isEmpty() && i + 1 < lines.length) {
                        String potentialRefLine = lines[++i].trim();
                        if (REF_RANGE.matcher(potentialRefLine).find()) {
                            refStr = potentialRefLine;
                        } else {
                            i--;
                        }
                    }

                    list.add(new LabResultItem(nameLine, valueStr, unit, refStr, abnormal, order++));
                    i++;
                    continue;
                }
            }
            i++;
        }

        if (!list.isEmpty()) {
            log.info("Parsed {} lab results from multi-line format", list.size());
        }
        return list;
    }

    private static boolean isLikelyName(String line) {
        if (line == null || line.isBlank() || line.length() > 30) return false;
        // 包含中文字符或常见的英文指标名称
        return line.matches(".*[\\u4e00-\\u9fa5].*") ||
               line.toLowerCase().matches(".*(rbc|hb|wbc|plt|neut|lymph|mono|eosin|baso|crea|urea|ua|alt|ast|tbil|dbil|alb|glb|tp|aptt|pt|inr|fbg).*");
    }

    private static boolean isAbnormal(String valueStr, String refStr) {
        if (valueStr == null || refStr == null || refStr.isEmpty()) return false;
        try {
            double value = Double.parseDouble(valueStr);
            Matcher refMatcher = REF_RANGE.matcher(refStr);
            if (refMatcher.find()) {
                double low = Double.parseDouble(refMatcher.group(1));
                double high = Double.parseDouble(refMatcher.group(2));
                return value < low || value > high;
            }
        } catch (NumberFormatException e) {
            log.warn("Failed to parse value or reference range: value={}, ref={}", valueStr, refStr);
        }
        return false;
    }

    public static class LabResultItem {
        public final String name;
        public final String value;
        public final String unit;
        public final String referenceRange;
        public final boolean abnormal;
        public final int sortOrder;

        public LabResultItem(String name, String value, String unit, String referenceRange, boolean abnormal, int sortOrder) {
            this.name = name;
            this.value = value;
            this.unit = unit;
            this.referenceRange = referenceRange;
            this.abnormal = abnormal;
            this.sortOrder = sortOrder;
        }
    }
}
