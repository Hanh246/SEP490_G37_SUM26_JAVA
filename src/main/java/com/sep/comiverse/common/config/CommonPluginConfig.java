package com.sep.comiverse.common.config;

import com.sep.comiverse.common.plugin.ICrudPlugin;
import com.sep.comiverse.common.plugin.IMapperPlugin;
import org.springframework.context.annotation.Configuration;
import org.springframework.plugin.core.config.EnablePluginRegistries;

@Configuration
@EnablePluginRegistries({IMapperPlugin.class, ICrudPlugin.class})
public class CommonPluginConfig {
}
