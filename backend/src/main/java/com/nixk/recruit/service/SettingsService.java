package com.nixk.recruit.service;

import com.nixk.recruit.model.Setting;
import com.nixk.recruit.repo.SettingRepo;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Admin-configurable scoring weights. */
@Service
public class SettingsService {
    public static final Map<String, Double> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put("skills", 40.0);
        DEFAULTS.put("experience", 20.0);
        DEFAULTS.put("projects", 15.0);
        DEFAULTS.put("role", 15.0);
        DEFAULTS.put("education", 10.0);
    }

    private final SettingRepo repo;

    public SettingsService(SettingRepo repo) {
        this.repo = repo;
    }

    public Map<String, Double> weights() {
        Map<String, Double> out = new LinkedHashMap<>(DEFAULTS);
        for (String k : DEFAULTS.keySet()) {
            repo.findById("weight." + k).ifPresent(s -> {
                try {
                    out.put(k, Double.parseDouble(s.value));
                } catch (NumberFormatException ignored) {
                }
            });
        }
        return out;
    }

    public Map<String, Double> saveWeights(Map<String, Double> in) {
        double sum = 0;
        for (String k : DEFAULTS.keySet()) {
            Double v = in.get(k);
            if (v == null || v < 0 || v > 100) {
                throw new ApiException(400, "Weight '" + k + "' must be a number between 0 and 100");
            }
            sum += v;
        }
        if (sum <= 0) throw new ApiException(400, "At least one weight must be greater than 0");
        for (String k : DEFAULTS.keySet()) repo.save(new Setting("weight." + k, String.valueOf(in.get(k))));
        return weights();
    }
}
