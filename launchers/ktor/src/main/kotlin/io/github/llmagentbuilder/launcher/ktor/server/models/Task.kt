/**
* Agent Protocol
* Specification of the API protocol for communication with an agent.
*
* The version of the OpenAPI document: v1
* 
*
* NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
* https://openapi-generator.tech
* Do not edit the class manually.
*/
package io.github.llmagentbuilder.launcher.ktor.server.models

import io.github.llmagentbuilder.launcher.ktor.server.models.Artifact

/**
 * 
 * @param taskId The ID of the task.
 * @param artifacts A list of artifacts that the task has produced.
 * @param input Input prompt for the task.
 * @param additionalInput Input parameters for the task. Any value is allowed.
 */
data class Task(
    /* The ID of the task. */
    val taskId: kotlin.String,
    /* A list of artifacts that the task has produced. */
    val artifacts: kotlin.collections.List<Artifact>,
    /* Input prompt for the task. */
    val input: kotlin.String? = null,
    /* Input parameters for the task. Any value is allowed. */
    val additionalInput: kotlin.Any? = null
) 
