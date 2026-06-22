package com.sep.comiverse.config;

import com.sep.comiverse.plugin.ICrudPlugin;
import com.sep.comiverse.plugin.IMapperPlugin;
import org.springframework.context.annotation.Configuration;
import org.springframework.plugin.core.config.EnablePluginRegistries;

@Configuration
@EnablePluginRegistries({IMapperPlugin.class, ICrudPlugin.class})
public class CommonPluginConfig {
}
