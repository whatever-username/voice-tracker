package com.whatever

import io.micronaut.context.annotation.ConfigurationProperties


@ConfigurationProperties("telegram")
class TagsMapConfig {
    lateinit var tagsMap: Map<String, List<String>>
}