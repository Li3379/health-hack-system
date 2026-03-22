package com.hhs.service.impl;

import com.hhs.service.DeviceConnectionService;
import com.hhs.service.DevicePlatformService;
import com.hhs.service.DeviceSyncOrchestrationService;
import com.hhs.vo.DeviceConnectionVO;
import com.hhs.vo.SyncResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Device Sync Orchestration Service Implementation.
 * Coordinates synchronization across multiple wearable device platforms.
 *
 * <p>Platform services are automatically registered via Spring dependency injection.
 * All beans implementing {@link DevicePlatformService} are collected and indexed
 * by their platform identifier.
 *
 * <p>Error handling strategy:
 * <ul>
 *   <li>syncPlatform: Returns failed result for unknown platforms</li>
 *   <li>syncAllPlatforms: Continues on individual failures, aggregates errors</li>
 * </ul>
 */
@Slf4j
@Service
public class DeviceSyncOrchestrationServiceImpl implements DeviceSyncOrchestrationService {

    private final Map<String, DevicePlatformService> platformServices;
    private final DeviceConnectionService connectionService;

    /**
     * Constructor with automatic platform service registration.
     *
     * <p>Spring automatically injects all beans implementing DevicePlatformService.
     * They are indexed by their platform identifier for efficient lookup.
     *
     * @param platformServices list of all platform service implementations
     * @param connectionService the device connection service
     */
    public DeviceSyncOrchestrationServiceImpl(
            List<DevicePlatformService> platformServices,
            DeviceConnectionService connectionService) {

        this.connectionService = connectionService;
        this.platformServices = new HashMap<>();

        // Register all platform services by their platform identifier
        for (DevicePlatformService service : platformServices) {
            String platform = service.getPlatform();
            if (platform != null && !platform.isBlank()) {
                this.platformServices.put(platform.toLowerCase(), service);
                log.info("Registered device platform service: {}", platform);
            }
        }

        log.info("Device sync orchestration initialized with {} platform(s)", this.platformServices.size());
    }

    @Override
    public SyncResultVO syncPlatform(Long userId, String platform) {
        log.info("Syncing platform {} for user {}", platform, userId);

        DevicePlatformService service = getPlatformService(platform);
        if (service == null) {
            log.warn("Unknown platform requested: {}", platform);
            return SyncResultVO.failed(platform, "Unknown platform: " + platform, 0);
        }

        if (!service.isConfigured()) {
            log.warn("Platform {} is not configured", platform);
            return SyncResultVO.failed(platform, "Platform not configured", 0);
        }

        try {
            return service.syncData(userId);
        } catch (Exception e) {
            log.error("Error syncing platform {} for user {}: {}", platform, userId, e.getMessage());
            return SyncResultVO.failed(platform, "Sync error: " + e.getMessage(), 0);
        }
    }

    @Override
    public List<SyncResultVO> syncAllPlatforms(Long userId) {
        log.info("Syncing all platforms for user {}", userId);

        List<SyncResultVO> results = new ArrayList<>();
        List<String> failedPlatforms = new ArrayList<>();

        // Get all connected devices for the user
        List<DeviceConnectionVO> connections = connectionService.getConnections(userId);

        // Track which platforms we've synced to avoid duplicates
        Set<String> syncedPlatforms = new HashSet<>();

        for (DeviceConnectionVO connection : connections) {
            String platform = connection.getPlatform().toLowerCase();

            // Skip if already synced (handles duplicate connections)
            if (syncedPlatforms.contains(platform)) {
                continue;
            }

            // Skip if sync is disabled for this connection
            if (!Boolean.TRUE.equals(connection.getSyncEnabled())) {
                log.debug("Skipping platform {} - sync disabled", platform);
                continue;
            }

            syncedPlatforms.add(platform);

            DevicePlatformService service = getPlatformService(platform);
            if (service == null) {
                log.warn("No service registered for platform: {}", platform);
                results.add(SyncResultVO.failed(platform, "Platform service not available", 0));
                failedPlatforms.add(platform);
                continue;
            }

            if (!service.isConfigured()) {
                log.warn("Platform {} is not configured, skipping", platform);
                results.add(SyncResultVO.failed(platform, "Platform not configured", 0));
                failedPlatforms.add(platform);
                continue;
            }

            try {
                log.debug("Syncing platform: {}", platform);
                SyncResultVO result = service.syncData(userId);
                results.add(result);

                if (!"success".equals(result.getStatus())) {
                    failedPlatforms.add(platform);
                }
            } catch (Exception e) {
                log.error("Error syncing platform {} for user {}: {}", platform, userId, e.getMessage());
                results.add(SyncResultVO.failed(platform, "Sync error: " + e.getMessage(), 0));
                failedPlatforms.add(platform);
                // Continue to next platform - one failure shouldn't stop others
            }
        }

        // Log summary
        if (failedPlatforms.isEmpty()) {
            log.info("All platforms synced successfully for user {}", userId);
        } else {
            log.warn("Sync completed with failures for user {}. Failed platforms: {}", userId, failedPlatforms);
        }

        return results;
    }

    @Override
    public String getAuthorizationUrl(Long userId, String platform) {
        log.info("Getting authorization URL for platform {} and user {}", platform, userId);

        DevicePlatformService service = getPlatformService(platform);
        if (service == null) {
            log.warn("Unknown platform requested for authorization: {}", platform);
            return null;
        }

        if (!service.isConfigured()) {
            log.warn("Platform {} is not configured", platform);
            return null;
        }

        return service.getAuthorizationUrl(userId);
    }

    @Override
    public void handleCallback(String platform, String code, String state) {
        log.info("Handling OAuth callback for platform: {}", platform);

        DevicePlatformService service = getPlatformService(platform);
        if (service == null) {
            throw new IllegalArgumentException("Unknown platform: " + platform);
        }

        service.handleCallback(code, state);
    }

    @Override
    public List<String> getRegisteredPlatforms() {
        return new ArrayList<>(platformServices.keySet());
    }

    @Override
    public boolean isPlatformRegistered(String platform) {
        if (platform == null || platform.isBlank()) {
            return false;
        }
        return platformServices.containsKey(platform.toLowerCase());
    }

    @Override
    public boolean isPlatformConfigured(String platform) {
        DevicePlatformService service = getPlatformService(platform);
        return service != null && service.isConfigured();
    }

    /**
     * Get the platform service for a given platform identifier.
     *
     * @param platform the platform identifier (case-insensitive)
     * @return the platform service, or null if not found
     */
    private DevicePlatformService getPlatformService(String platform) {
        if (platform == null || platform.isBlank()) {
            return null;
        }
        return platformServices.get(platform.toLowerCase());
    }
}