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
 * 
 * @param totalItems Total number of items.
 * @param totalPages Total number of pages.
 * @param currentPage Current_page page number.
 * @param pageSize Number of items per page.
 */
data class Pagination(
    /* Total number of items. */
    val totalItems: kotlin.Int,
    /* Total number of pages. */
    val totalPages: kotlin.Int,
    /* Current_page page number. */
    val currentPage: kotlin.Int,
    /* Number of items per page. */
    val pageSize: kotlin.Int
) 

