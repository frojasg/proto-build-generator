package org.example.app

/**
 * Strategy for converting proto package names to Gradle module names
 */
interface ModuleNamingStrategy {
    fun packageToModuleName(packageName: String): String
}

/**
 * Default module naming strategy:
 * - Strips common prefixes (com., org., io.)
 * - Replaces dots with hyphens
 * - Results in Gradle-compatible module names
 *
 * Examples:
 * - com.square.customer -> square-customer
 * - org.mycompany.api -> mycompany-api
 * - io.grpc.health.v1 -> grpc-health-v1
 */
class StandardModuleNaming : ModuleNamingStrategy {

    private val commonPrefixes = listOf("com.", "org.", "io.")

    override fun packageToModuleName(packageName: String): String {
        // Remove common prefixes
        var name = packageName
        for (prefix in commonPrefixes) {
            if (name.startsWith(prefix)) {
                name = name.removePrefix(prefix)
                break
            }
        }

        // Replace dots with hyphens for Gradle compatibility
        return name.replace('.', '-')
    }
}

/**
 * Simple module naming that just replaces dots with hyphens
 * Preserves the full package name
 *
 * Examples:
 * - com.square.customer -> com-square-customer
 * - org.mycompany.api -> org-mycompany-api
 */
class FullPackageModuleNaming : ModuleNamingStrategy {
    override fun packageToModuleName(packageName: String): String {
        return packageName.replace('.', '-')
    }
}
