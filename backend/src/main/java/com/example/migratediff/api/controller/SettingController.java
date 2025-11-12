package com.example.migratediff.api.controller;

import com.example.migratediff.application.SettingAppService;
import com.example.migratediff.domain.setting.SystemSetting;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@Validated
@RequestMapping("/api/settings")
public class SettingController {

    private final SettingAppService settingAppService;

    public SettingController(SettingAppService settingAppService) {
        this.settingAppService = settingAppService;
    }

    @GetMapping("/{key}")
    public SystemSetting getSetting(@PathVariable String key) {
        return settingAppService.fetch(key);
    }

    @PostMapping
    public SystemSetting updateSetting(@Valid @RequestBody SystemSetting setting) {
        return settingAppService.update(setting);
    }
}
