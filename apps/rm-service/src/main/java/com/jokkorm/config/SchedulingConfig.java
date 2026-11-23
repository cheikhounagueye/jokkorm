package com.jokkorm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Tâches planifiées (file d'envoi vers le maître). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
