package com.nixk.recruit.model;

import jakarta.persistence.*;

@Entity
@Table(name = "settings")
public class Setting {
    @Id
    @Column(name = "setting_key")
    public String key;
    @Column(name = "setting_value")
    public String value;

    public Setting() {}
    public Setting(String key, String value) { this.key = key; this.value = value; }
}
