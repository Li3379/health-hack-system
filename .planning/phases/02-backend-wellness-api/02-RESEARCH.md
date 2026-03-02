# Phase 2: Backend Wellness API - Research

**Researched:** 2026-03-02
**Domain:** Spring Boot REST API, Wellness Metric Management
**Confidence:** HIGH

## Summary

Phase 2 implements backend API endpoints for wellness metric tracking (sleep, steps, exercise, water intake, mood). The existing HealthMetric system already supports category classification (HEALTH vs WELLNESS) via Phase 1, so wellness metrics can leverage the same infrastructure with category-based filtering.

**Primary recommendation:** Create a dedicated WellnessController with endpoints under `/api/wellness/*` that internally uses the existing HealthMetricService with category filtering, plus add wellness-specific validation and display formatting.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.2.0 | Web framework | Existing project standard |
| MyBatis-Plus | 3.5.5 | ORM | Existing project standard |
| Jakarta Validation | 3.0 | Input validation | Existing project standard |
| Spring Security | 6.x | Authentication | JWT-based, existing |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Lombok | 1.18.x | Boilerplate reduction | All DTOs/Entities |
| Swagger/OpenAPI | 3.x | API documentation | All controllers |
| Jackson | 2.15+ | JSON serialization | Automatic with Spring Boot |

### Existing Infrastructure (REUSE)
| Component | Location | Purpose |
|-----------|----------|---------|
| HealthMetric entity | `entity/HealthMetric.java` | Unified metric storage with category field |
| HealthMetricService | `service/HealthMetricService.java` | CRUD operations for all metrics |
| MetricCategoryService | `service/domain/MetricCategoryService.java` | Category determination logic |
| MetricCategory enum | `common/enums/MetricCategory.java` | HEALTH/WELLNESS classification |
| HealthMetricRequest | `dto/HealthMetricRequest.java` | Existing request DTO |
| HealthMetricVO | `vo/HealthMetricVO.java` | Existing response record |

## Architecture Patterns

### Existing Patterns to Follow

**Controller Pattern:**
```java
@Slf4j
@Tag(name = "Wellness Metrics", description = "保健指标管理")
@RestController
@RequestMapping("/api/wellness")
@RequiredArgsConstructor
public class WellnessController {
    private final HealthMetricService healthMetricService;

    @GetMapping
    @Operation(summary = "Get wellness metrics")
    public Result<Page<HealthMetricVO>> list(...) {
        Long userId = SecurityUtils.getCurrentUserId();
        // Filter by category=WELLNESS
    }
}
```

**Service Pattern (already implemented):**
- Service interface + Impl class
- Constructor injection via `@RequiredArgsConstructor`
- Transactional annotations on write operations
- Cache eviction on data changes

**DTO/VO Pattern:**
- Request DTOs are mutable classes with validation annotations
- Response VOs are immutable Java records
- Use `BeanUtils.copyProperties()` for entity-DTO conversion

### Recommended Project Structure

```
hhs-backend/src/main/java/com/hhs/
├── controller/
│   └── WellnessController.java       # NEW - wellness-specific endpoints
├── dto/
│   └── WellnessMetricRequest.java    # NEW - wellness-specific validation
├── vo/
│   └── WellnessMetricVO.java         # NEW - wellness response with display names
├── service/
│   └── WellnessService.java          # NEW - wellness-specific operations
│   └── impl/WellnessServiceImpl.java
├── service/domain/
│   └── MetricDisplayFormatter.java   # EXTEND - add wellness display names
│   └── MetricCategoryService.java    # ALREADY HAS wellness keys
├── validation/
│   └── WellnessMetricValidator.java  # NEW - wellness-specific validation
└── common/enums/
    └── MetricCategory.java           # ALREADY EXISTS
```

### Pattern 1: Category-Filtered Query
**What:** Filter existing HealthMetric queries by category=WELLNESS
**When to use:** All wellness list/retrieve operations
**Example:**
```java
// Source: Pattern from HealthMetricServiceImpl
LambdaQueryWrapper<HealthMetric> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(HealthMetric::getUserId, userId);
wrapper.eq(HealthMetric::getCategory, MetricCategory.WELLNESS);
wrapper.eq(HealthMetric::getMetricKey, metricKey); // Optional filter
wrapper.orderByDesc(HealthMetric::getRecordDate);
```

### Pattern 2: Wellness-Specific Validation
**What:** Validate wellness metrics have appropriate ranges
**When to use:** Creating/updating wellness metrics
**Example:**
```java
// Sleep duration: 0-24 hours
// Sleep quality: 1-5 scale
// Steps: 0-100,000
// Exercise minutes: 0-1440 (max minutes in a day)
// Water intake: 0-20 glasses or 0-5000 ml
// Mood/Energy: 1-5 scale
```

### Anti-Patterns to Avoid
- **Creating separate wellness table:** The health_metric table already supports categories; use filtering instead of a new table
- **Duplicating service logic:** Extend MetricDisplayFormatter and MetricCategoryService rather than creating parallel implementations
- **Hardcoding display names in controller:** Use the existing MetricDisplayFormatter pattern

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Metric CRUD | Custom wellness DAO | HealthMetricService with category filter | Already tested, handles events/cache |
| Category logic | New enum or constants | MetricCategory enum + MetricCategoryService | Phase 1 implementation |
| Display names | Hardcoded strings | MetricDisplayFormatter | Centralized, i18n-ready |
| Authentication | Custom security | SecurityUtils.getCurrentUserId() | Existing JWT infrastructure |
| Pagination | Custom paging | MyBatis-Plus Page<T> | Built-in, tested |

## Common Pitfalls

### Pitfall 1: Incorrect Wellness Metric Keys
**What goes wrong:** Using Chinese metric keys ("睡眠时长") instead of English keys ("sleepDuration")
**Why it happens:** Database uses English keys but UI might show Chinese
**How to avoid:** Always use the keys defined in MetricCategoryService.WELLNESS_METRICS
**Warning signs:** Category defaults to HEALTH instead of WELLNESS

### Pitfall 2: Validation Range Overflow
**What goes wrong:** HealthMetricRequest allows values up to 1000, but wellness metrics have smaller ranges
**Why it happens:** Generic validation doesn't account for metric-specific constraints
**How to avoid:** Create WellnessMetricRequest with metric-specific @DecimalMax/@DecimalMin
**Warning signs:** Users entering 500 hours of sleep

### Pitfall 3: Missing Trend Support
**What goes wrong:** Trend data endpoint returns empty for wellness metrics
**Why it happens:** getTrend() works but needs to be exposed for wellness
**How to avoid:** Add wellness-specific trend endpoint or reuse existing with category filter
**Warning signs:** Frontend trend charts empty for wellness data

### Pitfall 4: Unit Inconsistency
**What goes wrong:** Water intake recorded in different units (glasses vs ml)
**Why it happens:** No standard unit enforced
**How to avoid:** Define standard unit in MetricDisplayFormatter and auto-normalize
**Warning signs:** Mixed units in same user's data

## Code Examples

### Existing Wellness Metric Keys (from MetricCategoryService)
```java
// Source: hhs-backend/src/main/java/com/hhs/service/domain/MetricCategoryService.java
private static final Set<String> WELLNESS_METRICS = Set.of(
    "sleepDuration",    // Sleep hours (0-24)
    "sleepQuality",     // Sleep quality (1-5)
    "steps",            // Daily steps (0-100000)
    "exerciseMinutes",  // Exercise minutes (0-1440)
    "waterIntake",      // Water glasses (0-20) or ml (0-5000)
    "mood",             // Mood level (1-5)
    "energy"            // Energy level (1-5)
);
```

### Recommended WellnessMetricRequest
```java
// Pattern: Follow HealthMetricRequest structure
@Data
public class WellnessMetricRequest {
    private Long userId; // Set from SecurityUtils

    @NotNull(message = "Metric key is required")
    @Pattern(regexp = "^(sleepDuration|sleepQuality|steps|exerciseMinutes|waterIntake|mood|energy)$")
    private String metricKey;

    @NotNull(message = "Value is required")
    @DecimalMin(value = "0.0", message = "Value must be non-negative")
    private BigDecimal value;

    @NotNull(message = "Record date is required")
    @PastOrPresent(message = "Record date cannot be in the future")
    private LocalDate recordDate;

    private String unit;
}
```

### Recommended Controller Endpoints
```java
// Pattern: Follow AlertController and HealthMetricController patterns
@RestController
@RequestMapping("/api/wellness")
@RequiredArgsConstructor
public class WellnessController {

    private final HealthMetricService healthMetricService;

    @GetMapping
    public Result<Page<HealthMetricVO>> list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String metricKey,
        @RequestParam(required = false) LocalDate startDate,
        @RequestParam(required = false) LocalDate endDate
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        // Filter by category=WELLNESS
    }

    @PostMapping
    public Result<HealthMetricVO> create(@Valid @RequestBody WellnessMetricRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        // Set category to WELLNESS
    }

    @GetMapping("/trend/{metricKey}")
    public Result<List<HealthMetricTrendVO>> getTrend(
        @PathVariable String metricKey,
        @RequestParam @PastOrPresent LocalDate startDate,
        @RequestParam @PastOrPresent LocalDate endDate
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        // Get trend data filtered by WELLNESS category
    }

    @GetMapping("/summary")
    public Result<WellnessSummaryVO> getSummary() {
        Long userId = SecurityUtils.getCurrentUserId();
        // Aggregate wellness metrics for dashboard
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Separate tables per metric type | Unified health_metric with category | Phase 1 | Single source of truth, simpler queries |
| Chinese metric keys | English metric keys with display names | Original design | Better i18n support |
| Custom validation per endpoint | Jakarta Bean Validation annotations | Original design | Declarative, reusable |

**Deprecated/outdated:**
- Integer-based error codes: Use ErrorCode enum with `HEALTH_*` and `VALIDATION_*` prefixes
- Mutable VOs: Use Java records for immutable response objects

## Validation Ranges for Wellness Metrics

| Metric Key | Min | Max | Unit | Notes |
|------------|-----|-----|------|-------|
| sleepDuration | 0 | 24 | hours | Can't exceed 24h/day |
| sleepQuality | 1 | 5 | scale | Likert scale |
| steps | 0 | 100000 | steps | Realistic max |
| exerciseMinutes | 0 | 1440 | minutes | Max minutes/day |
| waterIntake | 0 | 20 | glasses | Or 0-5000ml |
| mood | 1 | 5 | scale | Likert scale |
| energy | 1 | 5 | scale | Likert scale |

## API Endpoints to Create

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/wellness` | List wellness metrics (paginated) | Required |
| POST | `/api/wellness` | Create wellness metric | Required |
| PUT | `/api/wellness/{id}` | Update wellness metric | Required |
| DELETE | `/api/wellness/{id}` | Delete wellness metric | Required |
| GET | `/api/wellness/trend/{metricKey}` | Get trend data | Required |
| GET | `/api/wellness/summary` | Get aggregated summary | Required |
| GET | `/api/wellness/latest` | Get latest values per type | Required |

## Key Files to Modify

### New Files (Create)
1. `controller/WellnessController.java` - New wellness endpoints
2. `dto/WellnessMetricRequest.java` - Wellness-specific request with validation
3. `vo/WellnessSummaryVO.java` - Dashboard summary response
4. `service/WellnessService.java` - Wellness-specific operations interface
5. `service/impl/WellnessServiceImpl.java` - Implementation

### Existing Files (Extend)
1. `service/domain/MetricDisplayFormatter.java` - Add wellness display names and units
2. `common/constant/ErrorCode.java` - Add WELLNESS_* error codes if needed

### No Changes Needed
1. `entity/HealthMetric.java` - Already has category field
2. `service/HealthMetricService.java` - Can be used with category filter
3. `mapper/HealthMetricMapper.java` - Standard MyBatis-Plus queries work
4. `sql/schema.sql` - Already has category column and index

## Database Considerations

**No schema changes required.** The Phase 1 implementation already added:
- `category` ENUM column with HEALTH/WELLNESS values
- `idx_user_category_date` index for category-filtered queries

**Query pattern for wellness data:**
```sql
SELECT * FROM health_metric
WHERE user_id = ?
  AND category = 'WELLNESS'
  AND metric_key = ?
ORDER BY record_date DESC;
```

## Testing Strategy

Follow existing test patterns from `HealthMetricServiceTest.java`:
- Use `@ExtendWith(MockitoExtension.class)`
- Mock mapper dependencies
- Test success, validation, and error scenarios
- Use `@DisplayName` with Chinese descriptions

## Open Questions

1. **Water intake unit standardization**
   - What we know: MetricDisplayFormatter can return units
   - What's unclear: Should we use glasses or ml as standard?
   - Recommendation: Support both with auto-conversion (1 glass = 250ml)

2. **Wellness metric deduplication**
   - What we know: HealthMetricService doesn't prevent duplicate entries per day
   - What's unclear: Should we allow multiple sleep records per day?
   - Recommendation: Allow multiple entries (naps), but consider aggregation for summary

3. **Summary aggregation period**
   - What we know: Dashboard needs aggregated data
   - What's unclear: Default time period for summary (7 days? 30 days?)
   - Recommendation: Default 7 days with configurable parameter

## Sources

### Primary (HIGH confidence)
- Existing codebase analysis - All patterns verified from source files
- Phase 1 implementation (MetricCategory, MetricCategoryService) - Fully implemented
- Database schema - Already supports category filtering

### Secondary (MEDIUM confidence)
- HealthMetricServiceTest.java - Test patterns
- AlertController.java - REST endpoint patterns

### Tertiary (LOW confidence)
- None - All research based on verified codebase

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All components verified in codebase
- Architecture: HIGH - Patterns well-established, Phase 1 foundation complete
- Pitfalls: HIGH - Based on actual codebase patterns and constraints

**Research date:** 2026-03-02
**Valid until:** 30 days (stable codebase, well-established patterns)