package com.example.migratediff.infrastructure.persistence;

import com.example.migratediff.domain.setting.SystemSetting;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SettingRepository {

    SystemSetting save(SystemSetting setting);

    Optional<SystemSetting> findByKey(String key);
}
