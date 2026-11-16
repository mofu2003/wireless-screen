package app.opendisplay.android.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import app.opendisplay.android.Log
import app.opendisplay.android.protocol.ReverseConnection
import java.util.ArrayDeque

data class MacRequestTarget(
    val serviceName: String,
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
)

/** Discovers Mac senders that accept receiver-initiated connection requests. */
class MacRequestBrowser(
    context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onTargetsChanged(targets: List<MacRequestTarget>)
        fun onSearchFailed() {}
    }

    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = appContext.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val main = Handler(Looper.getMainLooper())
    private val targets = linkedMapOf<String, MacRequestTarget>()
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var discovery: NsdManager.DiscoveryListener? = null
    private var multicast: WifiManager.MulticastLock? = null

    fun start() {
        if (discovery != null) return
        multicast = wifi.createMulticastLock("open-display-mac-requests").also {
            it.setReferenceCounted(false)
            it.acquire()
        }
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.w(TAG, "request discovery failed to start: $errorCode")
                main.post {
                    listener.onSearchFailed()
                    stop()
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit
            override fun onDiscoveryStarted(serviceType: String?) = Unit
            override fun onDiscoveryStopped(serviceType: String?) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                val info = serviceInfo ?: return
                main.post {
                    pending.addLast(info)
                    resolveNext()
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                val name = serviceInfo?.serviceName ?: return
                main.post {
                    targets.entries.removeAll { it.value.serviceName == name }
                    publish()
                }
            }
        }
        this.discovery = discovery
        runCatching {
            nsd.discoverServices(ReverseConnection.SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD, discovery)
        }.onFailure {
            Log.w(TAG, "request discovery failed: $it")
            listener.onSearchFailed()
            stop()
        }
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        pending.clear()
        resolving = false
        targets.clear()
        if (multicast?.isHeld == true) multicast?.release()
        multicast = null
    }

    private fun resolveNext() {
        if (resolving) return
        val info = pending.pollFirst() ?: return
        resolving = true
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                main.post {
                    resolving = false
                    resolveNext()
                }
            }

            override fun onServiceResolved(resolved: NsdServiceInfo?) {
                val target = resolved?.let {
                    val attributes = it.attributes
                    val pv = attributes?.get("pv")?.toString(Charsets.UTF_8)
                        ?.toIntOrNull() ?: ReverseConnection.VERSION
                    if (pv !in 1..ReverseConnection.VERSION) return@let null
                    val id = attributes?.get("id")?.toString(Charsets.UTF_8)
                        ?.takeIf { value -> value.isNotEmpty() } ?: return@let null
                    val host = it.host?.hostAddress ?: return@let null
                    MacRequestTarget(
                        serviceName = it.serviceName,
                        id = id,
                        name = it.serviceName,
                        host = host,
                        port = it.port,
                    )
                }
                main.post {
                    if (target != null) {
                        targets[target.id] = target
                        publish()
                    }
                    resolving = false
                    resolveNext()
                }
            }
        })
    }

    private fun publish() = listener.onTargetsChanged(
        targets.values.sortedBy { it.name.lowercase() })

    private companion object {
        const val TAG = "WirelessScreen"
    }
}
