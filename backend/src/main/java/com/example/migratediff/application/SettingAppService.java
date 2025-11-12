package com.example.migratediff.application;

import com.example.migratediff.domain.setting.SystemSetting;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class SettingAppService {

    private final Map<String, SystemSetting> inMemorySettings = new HashMap<>();

    public SystemSetting fetch(String key) {
        // TODO replace with persistence access
        return inMemorySettings.get(key);
    }

    public SystemSetting update(SystemSetting setting) {
        // TODO replace with persistence access
        inMemorySettings.put(setting.getKey(), setting);
        return setting;
    }
}
