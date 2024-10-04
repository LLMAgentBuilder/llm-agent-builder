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


/**
 * Body of the task request.
 * @param input Input prompt for the step.
 * @param additionalInput Input parameters for the task step. Any value is allowed.
 */
data class StepRequestBody(
    /* Input prompt for the step. */
    val input: kotlin.String? = null,
    /* Input parameters for the task step. Any value is allowed. */
    val additionalInput: kotlin.Any? = null
) 
