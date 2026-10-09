package com.taleco.radarcorridas

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.view.Display
import android.os.Looper
import android.media.AudioManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Serviço de acessibilidade: observa a tela da Uber/99, lê a oferta e mostra o cartão.
 * Ele só LÊ a tela. Não toca em nada e nunca aceita corridas.
 */
class RadarService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: RadarService? = null
            private set

        /** Pacote do app -> nome curto mostrado no cartão. */
        val TARGETS = mapOf(
            "com.ubercab.driver" to "UBER",
            "com.app99.driver" to "99",
            "com.taxis99" to "99"
        )

        private const val SCAN_DELAY_MS = 150L
        private const val HIDE_GRACE_MS = 1200L
        private const val MAX_CARD_MS = 30_000L
        private const val MAX_NODES = 800

        /** Abaixo disso, consideramos que o app escondeu o texto da tela. */
        private const val MIN_VISIBLE_TEXTS = 3
        private const val IMAGE_READ_INTERVAL_MS = 1000L
        private const val MAX_HIDDEN_READ_MS = 25_000L
    }

    private lateinit var prefs: Prefs
    private lateinit var overlay: OverlayManager
    private val handler = Handler(Looper.getMainLooper())

    private var scanPending = false
    private var hidePending = false
    private var lastSig: String? = null
    private var lastLoggedSig: String? = null
    private var lastLoggedAt = 0L
    private var lastDiag: String? = null

    private val scanRunnable: Runnable = Runnable {
        scanPending = false
        scan()
    }

    private val hideRunnable: Runnable = Runnable {
        hidePending = false
        handler.removeCallbacks(maxCardRunnable)
        overlay.hideCard()
        lastSig = null
    }

    private val maxCardRunnable: Runnable = Runnable {
        handler.removeCallbacks(hideRunnable)
        hidePending = false
        overlay.hideCard()
        lastSig = null
    }

    // ---- Leitura pela imagem da tela ----

    private var imageReadBusy = false
    private var lastImageReadAt = 0L
    private var watchPending = false
    private var lastImageDiag: String? = null
    private var hiddenSince = 0L

    private val watchRunnable: Runnable = Runnable {
        watchPending = false
        scheduleScan()
    }

    /** Enquanto a tela da Uber estiver "vazia", continua olhando a cada segundo. */
    private fun keepWatchingHiddenScreen() {
        if (watchPending) return
        watchPending = true
        handler.postDelayed(watchRunnable, IMAGE_READ_INTERVAL_MS)
    }

    private fun requestImageRead(app: String) {
        val now = System.currentTimeMillis()
        if (imageReadBusy || now - lastImageReadAt < IMAGE_READ_INTERVAL_MS) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        imageReadBusy = true
        lastImageReadAt = now
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val bitmap: Bitmap? = try {
                        val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        val copy = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        hw?.recycle()
                        copy
                    } catch (e: Exception) {
                        null
                    } finally {
                        try { result.hardwareBuffer.close() } catch (_: Exception) {}
                    }
                    if (bitmap == null) {
                        imageReadBusy = false
                        if (prefs.diagnostic) OfferLog.appendDiag(this@RadarService, "IMAGEM: falha ao converter a captura")
                        return
                    }
                    ScreenReader.read(bitmap) { lines ->
                        bitmap.recycle()
                        imageReadBusy = false
                        onImageText(app, lines)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    imageReadBusy = false
                    if (prefs.diagnostic) OfferLog.appendDiag(this@RadarService, "IMAGEM: captura recusada (código $errorCode)")
                }
            })
        } catch (e: Exception) {
            imageReadBusy = false
            if (prefs.diagnostic) OfferLog.appendDiag(this, "IMAGEM: erro ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun onImageText(app: String, lines: List<String>) {
        if (instance == null) return
        val offer = OfferParser.parse(app, lines)
        if (prefs.diagnostic) {
            val summary = if (offer == null) "nenhuma oferta reconhecida" else
                String.format(PT_BR, "oferta R$ %.2f, %.1f km, %.0f min", offer.price, offer.totalKm, offer.totalMin)
            val dump = "IMAGEM $app ($summary): " + lines.joinToString(" | ")
            if (dump != lastImageDiag) {
                lastImageDiag = dump
                OfferLog.appendDiag(this, dump)
            }
        }
        if (offer != null) {
            handleOffer(offer)
            TripTracker.onScreen(this, prefs, ScreenState.OFERTA, offer)
        } else {
            handleNoOffer()
            TripTracker.onScreen(this, prefs, ScreenState.ESCONDIDA, null)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        overlay = OverlayManager(this)
        instance = this
        refreshBubble()
        SpeedCams.load(this)
        if (prefs.speedAlerts && SpeedCams.isStale(this)) SpeedCams.download(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || instance == null) return
        val pkg = event.packageName?.toString()
        if (pkg in TARGETS) SpeedWatch.onRideAppSeen(this, prefs)
        if (pkg in TARGETS || event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            scheduleScan()
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            pkg != null && pkg != packageName && overlay.isCardShowing
        ) {
            // O motorista trocou de app: confere se a oferta ainda está na tela.
            scheduleScan()
        }
    }

    override fun onInterrupt() {}

    // ---- Atalhos do volume para marcar valeta / radar (só enquanto dirige com a Uber/99 aberta) ----

    private var volPending = 0 // tecla esperando o segundo toque (0 = nenhuma)
    private val volumeRunnable: Runnable = Runnable {
        val key = volPending
        volPending = 0
        // Foi só um toque: muda o volume normalmente.
        adjustVolume(key)
    }

    private fun adjustVolume(key: Int) {
        val dir = if (key == KeyEvent.KEYCODE_VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        try {
            getSystemService(AudioManager::class.java)?.adjustSuggestedStreamVolume(
                dir, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI
            )
        } catch (_: Exception) {
        }
    }

    /**
     * Enquanto a Uber/99 está aberta (GPS ligado):
     *  volume − duas vezes rápido = marcar valeta
     *  volume + duas vezes rápido = marcar radar que falta no mapa
     */
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false
        val key = event.keyCode
        if (key != KeyEvent.KEYCODE_VOLUME_DOWN && key != KeyEvent.KEYCODE_VOLUME_UP) return false
        if (!::prefs.isInitialized || !SpeedWatch.active) return false
        if (key == KeyEvent.KEYCODE_VOLUME_DOWN && !prefs.valetaAlerts) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount > 0) {
                // Segurando o botão: deixa o volume mudar normalmente.
                handler.removeCallbacks(volumeRunnable)
                volPending = 0
                adjustVolume(key)
                return true
            }
            if (volPending == key) {
                handler.removeCallbacks(volumeRunnable)
                volPending = 0
                if (key == KeyEvent.KEYCODE_VOLUME_DOWN) HazardWatch.markManual(this) else HazardWatch.markRadar(this)
            } else {
                if (volPending != 0) {
                    // Apertou a outra tecla: aplica a primeira normalmente
                    handler.removeCallbacks(volumeRunnable)
                    adjustVolume(volPending)
                }
                volPending = key
                handler.postDelayed(volumeRunnable, 450L)
            }
        }
        return true
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        try { TripTracker.shutdown(this) } catch (_: Exception) {}
        try { SpeedWatch.stop(this) } catch (_: Exception) {}
        if (instance === this) instance = null
        if (::overlay.isInitialized) {
            overlay.hideCard()
            overlay.hideBubble()
            overlay.hideSpeed()
        }
    }

    private fun scheduleScan() {
        if (scanPending) return
        scanPending = true
        handler.postDelayed(scanRunnable, SCAN_DELAY_MS)
    }

    private fun scan() {
        val screens = collectTargetTexts()

        if (prefs.diagnostic) writeDiagnostic(screens)

        var offer: Offer? = null
        for ((_, texts) in screens) UberRoad.update(texts)
        for ((app, texts) in screens) {
            offer = OfferParser.parse(app, texts)
            if (offer != null) break
        }
        val now = System.currentTimeMillis()
        if (offer != null) {
            hiddenSince = 0L
            handleOffer(offer)
            TripTracker.onScreen(this, prefs, ScreenState.OFERTA, offer)
            return
        }

        // A Uber esconde o texto da tela de oferta: a janela aparece vazia.
        // Nesse caso, lemos a oferta pela imagem da tela (por no máximo 25 s seguidos,
        // para não gastar bateria quando a tela escondida não é uma oferta).
        val hidden = screens.firstOrNull { it.second.size < MIN_VISIBLE_TEXTS }
        if (hidden != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (hiddenSince == 0L) hiddenSince = now
            if (now - hiddenSince <= MAX_HIDDEN_READ_MS) {
                requestImageRead(hidden.first)
                keepWatchingHiddenScreen()
            } else {
                handleNoOffer()
            }
            return
        }
        hiddenSince = 0L

        val state = when {
            screens.isEmpty() -> ScreenState.FORA
            screens.any { TripTracker.isHomeScreen(it.second) } -> ScreenState.INICIO
            else -> ScreenState.OUTRA
        }
        TripTracker.onScreen(this, prefs, state, null)
        handleNoOffer()
    }

    private fun handleNoOffer() {
        if (overlay.isCardShowing && !hidePending) {
            hidePending = true
            handler.postDelayed(hideRunnable, HIDE_GRACE_MS)
        }
    }

    private fun handleOffer(offer: Offer) {
        if (hidePending) {
            handler.removeCallbacks(hideRunnable)
            hidePending = false
        }

        val sig = offer.signature()
        if (sig == lastSig && overlay.isCardShowing) return
        lastSig = sig

        val eval = Evaluator.evaluate(offer, prefs)
        overlay.showCard(eval, prefs)
        checkSpotStar(eval, sig)

        // Garantia: o cartão não fica preso na tela.
        handler.removeCallbacks(maxCardRunnable)
        handler.postDelayed(maxCardRunnable, MAX_CARD_MS)

        val now = System.currentTimeMillis()
        if (prefs.logOffers && (sig != lastLoggedSig || now - lastLoggedAt > 60_000)) {
            val driverAt = Geo.lastKnown(this)
            val ctx = this
            Geo.geocode(this, offer.origin) { originAt -> OfferLog.append(ctx, eval, driverAt, originAt) }
            lastLoggedSig = sig
            lastLoggedAt = now
        }
    }

    // ---- Estrela: destino perto de um ponto bom ----

    private val destCache = HashMap<String, LatLng?>()

    private fun checkSpotStar(eval: Evaluation, sig: String) {
        val dest = eval.offer.destination ?: return
        if (Spots.all(this).none { it.enabled }) return
        val arrive = eval.offer.totalMin
        val apply: (LatLng?) -> Unit = { at ->
            if (at != null && lastSig == sig && overlay.isCardShowing) {
                SpotMatch.near(this, at, arrive)?.let { (spot, d) ->
                    overlay.showCard(eval.copy(spotNote = SpotMatch.note(spot, d)), prefs)
                }
            }
        }
        if (destCache.containsKey(dest)) {
            apply(destCache[dest])
            return
        }
        Geo.geocode(this, dest) { at ->
            if (destCache.size > 200) destCache.clear()
            destCache[dest] = at
            apply(at)
        }
    }

    /** Lê os textos de todas as janelas da Uber/99 que estão na tela. */
    private fun collectTargetTexts(): List<Pair<String, List<String>>> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        try {
            windows?.forEach { w -> w.root?.let { roots.add(it) } }
        } catch (_: Exception) {
        }
        if (roots.isEmpty()) {
            try {
                rootInActiveWindow?.let { roots.add(it) }
            } catch (_: Exception) {
            }
        }

        val result = mutableListOf<Pair<String, List<String>>>()
        for (root in roots) {
            val pkg = root.packageName?.toString() ?: continue
            val app = TARGETS[pkg] ?: continue
            val texts = mutableListOf<String>()
            walk(root, texts, 0)
            result.add(app to texts)
        }
        return result
    }

    private fun walk(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > 40 || out.size > MAX_NODES) return
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        when {
            !text.isNullOrBlank() -> out.add(text)
            !desc.isNullOrBlank() -> out.add(desc)
        }
        for (i in 0 until node.childCount) {
            val child: AccessibilityNodeInfo = (try { node.getChild(i) } catch (_: Exception) { null }) ?: continue
            walk(child, out, depth + 1)
        }
    }

    /** Modo diagnóstico: registra o que foi lido, para ajustar a leitura depois. */
    private fun writeDiagnostic(screens: List<Pair<String, List<String>>>) {
        val packages: String = (try {
            windows?.mapNotNull { it.root?.packageName?.toString() }?.distinct()?.joinToString(", ")
        } catch (_: Exception) {
            null
        }) ?: "?"
        val body = if (screens.isEmpty()) {
            "(nenhuma tela da Uber/99 encontrada)"
        } else {
            screens.joinToString("\n") { (app, texts) -> "$app: " + texts.joinToString(" | ") }
        }
        val dump = "janelas: $packages\n$body"
        if (dump == lastDiag) return
        lastDiag = dump
        OfferLog.appendDiag(this, dump)
    }

    // ---- Chamado pela tela de configuração ----

    fun refreshBubble() {
        if (!::overlay.isInitialized) return
        if (prefs.showBubble) overlay.showBubble(prefs) else overlay.hideBubble()
    }

    fun showSpeedBanner(b: SpeedBanner) {
        // Radar ou valeta passam na frente do aviso de ponto bom
        if (b.sign != "★") SpotWatch.onBannerClosed()
        if (::overlay.isInitialized) overlay.showSpeed(b)
    }

    fun hideSpeedBanner() {
        SpotWatch.onBannerClosed()
        if (::overlay.isInitialized) overlay.hideSpeed()
    }

    fun showTestCard() {
        if (!::overlay.isInitialized) return
        val eval = Evaluator.evaluate(Offer.sample(), prefs)
        overlay.showCard(eval, prefs)
        handler.removeCallbacks(hideRunnable)
        hidePending = true
        handler.postDelayed(hideRunnable, 6000)
    }
}
