package com.vtop.telemetry.collectors

import android.content.Context
import android.net.*
import android.os.Build
import com.vtop.telemetry.Telemetry
import com.vtop.telemetry.model.TelemetryModule
import com.vtop.telemetry.model.TelemetryStatus

/**
 * Collects Android connectivity telemetry.
 *
 * API 24+
 */
object ConnectivityCollector {

    private var registered = false

    fun init(context: Context) {

        if (registered) return

        registered = true

        val manager =
            context.getSystemService(
                Context.CONNECTIVITY_SERVICE
            ) as ConnectivityManager

        manager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {

                override fun onAvailable(
                    network: Network
                ) {

                    emit(
                        "NETWORK_AVAILABLE",
                        network,
                        manager
                    )
                }

                override fun onLost(
                    network: Network
                ) {

                    Telemetry.log(

                        level = TelemetryStatus.WARNING,

                        tag = "CONNECTIVITY",

                        message = "Network lost",

                        module = TelemetryModule.NETWORK,

                        metadata = mapOf(

                            "event" to "NETWORK_LOST"
                        )
                    )
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities
                ) {

                    Telemetry.log(

                        level = TelemetryStatus.INFO,

                        tag = "CONNECTIVITY",

                        message = "Network capabilities changed",

                        module = TelemetryModule.NETWORK,

                        metadata = mapOf(

                            "event" to "NETWORK_CHANGED",

                            "wifi" to capabilities.hasTransport(
                                NetworkCapabilities.TRANSPORT_WIFI
                            ),

                            "cellular" to capabilities.hasTransport(
                                NetworkCapabilities.TRANSPORT_CELLULAR
                            ),

                            "vpn" to capabilities.hasTransport(
                                NetworkCapabilities.TRANSPORT_VPN
                            ),

                            "ethernet" to capabilities.hasTransport(
                                NetworkCapabilities.TRANSPORT_ETHERNET
                            ),

                            "validated" to capabilities.hasCapability(
                                NetworkCapabilities.NET_CAPABILITY_VALIDATED
                            ),

                            "metered" to !capabilities.hasCapability(
                                NetworkCapabilities.NET_CAPABILITY_NOT_METERED
                            ),

                            "internet" to capabilities.hasCapability(
                                NetworkCapabilities.NET_CAPABILITY_INTERNET
                            )
                        )
                    )
                }
            }
        )
    }

    private fun emit(

        event: String,

        network: Network,

        manager: ConnectivityManager

    ) {

        val capabilities =
            manager.getNetworkCapabilities(network)

        Telemetry.log(

            level = TelemetryStatus.INFO,

            tag = "CONNECTIVITY",

            message = "Network available",

            module = TelemetryModule.NETWORK,

            metadata = mapOf(

                "event" to event,

                "wifi" to capabilities?.hasTransport(
                    NetworkCapabilities.TRANSPORT_WIFI
                ),

                "cellular" to capabilities?.hasTransport(
                    NetworkCapabilities.TRANSPORT_CELLULAR
                ),

                "vpn" to capabilities?.hasTransport(
                    NetworkCapabilities.TRANSPORT_VPN
                ),

                "ethernet" to capabilities?.hasTransport(
                    NetworkCapabilities.TRANSPORT_ETHERNET
                ),

                "validated" to capabilities?.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                ),

                "metered" to capabilities?.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_NOT_METERED
                )?.not(),

                "internet" to capabilities?.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
                ),

                "downKbps" to capabilities?.linkDownstreamBandwidthKbps,

                "upKbps" to capabilities?.linkUpstreamBandwidthKbps
            )
        )
    }
}