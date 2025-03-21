package com.javaaidev.llmagentbuilder.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

object MapToObject {
    val objectMapper: ObjectMapper =
        ObjectMapper().registerModule(KotlinModule.Builder().build())

    inline fun <reified T> toObject(input: Map<String, Any?>?): T? {
        if (input == null) {
            return null
        }
        val json = objectMapper.writeValueAsString(input)
        return objectMapper.readValue(json, T::class.java)
    }

    fun <T> toObject(configClass: Class<T>, input: Map<String, Any?>?): T? {
        if (input == null) {
            return null
        }
        val json = objectMapper.writeValueAsString(input)
        return objectMapper.readValue(json, configClass)
    }
}