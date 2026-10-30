package com.pixorapdf

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.yandex.mobile.ads.common.AdBindingResult
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.common.YandexAds
import com.yandex.mobile.ads.interstitial.InterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader
import com.yandex.mobile.ads.nativeads.MediaView
import com.yandex.mobile.ads.nativeads.NativeAd
import com.yandex.mobile.ads.nativeads.NativeAdLoadListener
import com.yandex.mobile.ads.nativeads.NativeAdLoader
import com.yandex.mobile.ads.nativeads.NativeAdView
import com.yandex.mobile.ads.nativeads.NativeAdViewBinder
import com.yandex.mobile.ads.nativeads.Rating

/**
 * Yandex Mobile Ads (SDK 8.6.0).
 *
 *  - Native ad: small card on Home, on the "PDF created" screen and inside the My PDFs list.
 *  - Interstitial: only when the user LEAVES a finished-task screen (Create Another / Back to Home /
 *    View in Files), never at launch, never while working, never on back press.
 *
 * Debug builds use Yandex demo ad units so you never click your own live ads (can get the account banned).
 */
object Ads {
    private const val NATIVE_ID = "R-M-20285054-1"
    private const val INTERSTITIAL_ID = "R-M-20285054-2"
    private const val DEMO_NATIVE_ID = "demo-native-app-yandex"
    private const val DEMO_INTERSTITIAL_ID = "demo-interstitial-yandex"

    val nativeUnit: String get() = if (BuildConfig.DEBUG) DEMO_NATIVE_ID else NATIVE_ID
    private val interstitialUnit: String get() = if (BuildConfig.DEBUG) DEMO_INTERSTITIAL_ID else INTERSTITIAL_ID

    // ---- Interstitial frequency rules (tweak here) ----
    private const val REL_EVERY_NTH_EXIT = 2              // 1st finished task: no ad, 2nd: ad, 3rd: no ad, 4th: ad ...
    private const val REL_MIN_GAP_MS = 3 * 60 * 1000L     // at least 3 minutes between two interstitials
    private const val REL_WARMUP_MS = 60 * 1000L          // nothing in the first minute after launch

    // Debug builds: no limits, so you can see the test interstitial on every finished task.
    private val EVERY_NTH_EXIT get() = if (BuildConfig.DEBUG) 1 else REL_EVERY_NTH_EXIT
    private val MIN_GAP_MS get() = if (BuildConfig.DEBUG) 0L else REL_MIN_GAP_MS
    private val WARMUP_MS get() = if (BuildConfig.DEBUG) 0L else REL_WARMUP_MS
    const val TAG = "PixoraAds"

    private var started = false
    private var startTime = 0L
    private var loader: InterstitialAdLoader? = null
    private var interstitial: InterstitialAd? = null
    private var loading = false
    private var exits = 0
    private var lastShown = 0L

    // ---- SDK init gate: nothing is requested before YandexAds.initialize() has called back ----
    private val main = Handler(Looper.getMainLooper())
    private lateinit var appCtx: Context
    @Volatile private var ready = false
    private val pending = ArrayList<() -> Unit>()

    // ---- Native: one slot per placement. Slots live in this singleton, so the loader and the loaded ad
    //      survive recomposition / navigation; the Composable only observes the slot. ----
    private val slots = HashMap<String, NativeSlot>()
    private val RETRY_DELAYS_MS = longArrayOf(5_000L, 15_000L, 45_000L)   // max 3 retries, then give up
    private const val NEW_ROUND_AFTER_MS = 60_000L

    class NativeSlot(val key: String) {
        var loader: NativeAdLoader? = null          // strong reference for the whole load + display lifecycle
        var ad by mutableStateOf<NativeAd?>(null)
        var loading = false
        var retries = 0
        var lastFailAt = 0L
        var retryTask: Runnable? = null
        var bindFailures = 0
        var status by mutableStateOf("idle")      // shown on screen in debug builds only
    }
    private const val MAX_BIND_FAILURES = 2

    fun slot(key: String): NativeSlot = slots.getOrPut(key) { NativeSlot(key) }

    fun init(app: Context) {
        if (started) return
        started = true
        appCtx = app.applicationContext
        startTime = System.currentTimeMillis()
        if (BuildConfig.DEBUG) {
            // Shows Yandex's red overlay with the exact reason when an ad unit / integration is wrong.
            YandexAds.enableDebugErrorIndicator(true)
        }
        Log.d(TAG, "Yandex SDK initialize() called (debug=${BuildConfig.DEBUG}, nativeUnit=$nativeUnit, interstitialUnit=$interstitialUnit)")
        YandexAds.initialize(appCtx) {
            Log.d(TAG, "Yandex SDK initialized")
            main.post {
                ready = true
                val run = ArrayList(pending); pending.clear()
                run.forEach { it() }
                preloadInterstitial(appCtx)
            }
        }
    }

    /** Called by [NativeAdCard] when it enters composition. Safe to call any number of times. */
    fun requestNative(slot: NativeSlot) {
        main.post {
            if (slot.ad != null || slot.loading || slot.retryTask != null) return@post
            if (slot.retries > RETRY_DELAYS_MS.size && System.currentTimeMillis() - slot.lastFailAt < NEW_ROUND_AFTER_MS) {
                Log.d(TAG, "Native[${slot.key}] request skipped: retry budget used, cooling down"); return@post
            }
            if (slot.retries > RETRY_DELAYS_MS.size) slot.retries = 0
            if (!ready) { slot.status = "waiting for SDK init"; Log.d(TAG, "Native[${slot.key}] waiting for SDK init"); pending.add { requestNative(slot) }; return@post }
            loadNative(slot)
        }
    }

    private fun loadNative(slot: NativeSlot) {
        if (slot.ad != null || slot.loading) return
        val l = slot.loader ?: NativeAdLoader(appCtx).also { slot.loader = it }
        slot.loading = true
        slot.status = "requesting ${nativeUnit} (attempt ${slot.retries + 1})"
        Log.d(TAG, "Native[${slot.key}] REQUEST unit=$nativeUnit attempt=${slot.retries + 1}")
        l.loadAd(
            AdRequest.Builder(nativeUnit).build(),
            object : NativeAdLoadListener {
                override fun onAdLoaded(nativeAd: NativeAd) {
                    main.post {
                        slot.loading = false; slot.retries = 0
                        val a = nativeAd.adAssets
                        Log.d(TAG, "Native[${slot.key}] SUCCESS assets: title=${a.title != null} body=${a.body != null} icon=${a.icon != null} " +
                            "media=${a.media != null} cta=${a.callToAction != null} sponsored=${a.sponsored != null} warning=${a.warning != null}")
                        slot.status = "loaded, binding"
                        slot.ad = nativeAd
                    }
                }
                override fun onAdFailedToLoad(error: AdRequestError) {
                    main.post {
                        slot.loading = false; slot.lastFailAt = System.currentTimeMillis()
                        Log.e(TAG, "Native[${slot.key}] FAILED code=${error.code} description=${error.description} unit=$nativeUnit retries=${slot.retries}")
                        slot.status = "FAILED code=${error.code}: ${error.description}"
                        val retryable = error.code != 2   // 2 = invalid request: retrying cannot help
                        if (retryable && slot.retries < RETRY_DELAYS_MS.size) {
                            val delay = RETRY_DELAYS_MS[slot.retries]; slot.retries++
                            slot.status += " | retry ${slot.retries}/${RETRY_DELAYS_MS.size} in ${delay / 1000}s"
                            Log.d(TAG, "Native[${slot.key}] retry #${slot.retries} in ${delay}ms")
                            val t = Runnable { slot.retryTask = null; loadNative(slot) }
                            slot.retryTask = t; main.postDelayed(t, delay)
                        } else {
                            slot.retries = RETRY_DELAYS_MS.size + 1
                            Log.e(TAG, "Native[${slot.key}] giving up for now")
                        }
                    }
                }
            }
        )
    }

    fun bindOk(slot: NativeSlot) { slot.bindFailures = 0; slot.status = "shown"; Log.d(TAG, "Native[${slot.key}] bound and visible") }

    /** Binding failed or the card came out empty: drop this ad and load a fresh one (limited). */
    fun bindFailed(slot: NativeSlot, ad: NativeAd, reason: String) {
        main.post {
            if (slot.ad !== ad) return@post
            slot.ad = null
            slot.bindFailures++
            slot.status = "BIND FAILED: $reason (#${slot.bindFailures})"
            Log.e(TAG, "Native[${slot.key}] BIND FAILED: $reason")
            if (slot.bindFailures <= MAX_BIND_FAILURES) main.postDelayed({ loadNative(slot) }, 1000)
            else Log.e(TAG, "Native[${slot.key}] bind failed too often, giving up for now")
        }
    }

    /** The card that showed this ad left composition: a NativeAd must not be re-bound to a new view, so drop it. */
    fun releaseNative(slot: NativeSlot, ad: NativeAd?) {
        if (ad != null && slot.ad === ad) { slot.ad = null; Log.d(TAG, "Native[${slot.key}] released (view gone)") }
    }

    private fun preloadInterstitial(app: Context) {
        if (!ready) return
        if (loading || interstitial != null) return
        val l = loader ?: InterstitialAdLoader(app).also { loader = it }
        loading = true
        Log.d(TAG, "Interstitial REQUEST unit=$interstitialUnit")
        l.loadAd(
            AdRequest.Builder(interstitialUnit).build(),
            object : InterstitialAdLoadListener {
                override fun onAdLoaded(interstitialAd: InterstitialAd) { interstitial = interstitialAd; loading = false; Log.d(TAG, "Interstitial loaded") }
                override fun onAdFailedToLoad(adRequestError: AdRequestError) { loading = false; Log.e(TAG, "Interstitial failed: ${adRequestError.code} ${adRequestError.description}") }
            }
        )
    }

    /**
     * Call when the user taps a button that leaves a "task finished" screen.
     * [proceed] runs right away if no ad is due, otherwise after the ad is closed (or failed to show).
     */
    fun afterTask(activity: Activity?, proceed: () -> Unit) {
        exits++
        val now = System.currentTimeMillis()
        val ad = interstitial
        Log.d(TAG, "afterTask: exit #$exits, adReady=${ad != null}")
        val due = exits % EVERY_NTH_EXIT == 0 &&
            now - startTime >= WARMUP_MS &&
            now - lastShown >= MIN_GAP_MS
        if (!due || activity == null || ad == null) {
            if (ad == null && activity != null) preloadInterstitial(activity.applicationContext)
            proceed(); return
        }
        var done = false
        fun finish() {
            if (done) return
            done = true
            interstitial = null
            preloadInterstitial(activity.applicationContext)
            proceed()
        }
        ad.setAdEventListener(object : InterstitialAdEventListener {
            override fun onAdShown() { lastShown = System.currentTimeMillis() }
            override fun onAdFailedToShow(adError: AdError) { finish() }
            override fun onAdDismissed() { finish() }
            override fun onAdClicked() {}
            override fun onAdImpression(impressionData: ImpressionData?) {}
        })
        try { ad.show(activity) } catch (e: Throwable) { finish() }
    }
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

private fun Context.dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

/** Native ad card. Takes no space until an ad has loaded (debug builds show a one-line status instead). [placement] must be unique per screen slot. */
@Composable
fun NativeAdCard(placement: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val slot = remember(placement) { Ads.slot(placement) }
    // Loading is owned by the Ads singleton, so recomposition / navigation cannot cancel it.
    LaunchedEffect(slot) { Ads.requestNative(slot) }

    val loaded = slot.ad
    DisposableEffect(loaded) { onDispose { Ads.releaseNative(slot, loaded) } }
    if (loaded == null) {
        if (BuildConfig.DEBUG) {
            Text("native ad [$placement]: ${slot.status}", fontSize = 11.sp, color = cs.error, modifier = modifier.padding(vertical = 4.dp))
        }
        return
    }
    if (loaded.adAssets.title == null) {
        LaunchedEffect(loaded) { Ads.bindFailed(slot, loaded, "ad has no title asset") }
        return
    }

    val bg = cs.surface.toArgb()
    val stroke = cs.outlineVariant.toArgb()
    val title = cs.onSurface.toArgb()
    val sub = cs.onSurfaceVariant.toArgb()
    val accent = cs.primary.toArgb()

    key(loaded) {
        AndroidView(
            modifier = modifier,
            factory = { c -> buildNativeAdView(c, slot, loaded, bg, stroke, title, sub, accent) }
        )
    }
}

/** Yandex's setRatingView() requires a View that implements its Rating interface; a plain RatingBar does not compile. */
private class AdRatingBar(c: Context) : RatingBar(c, null, android.R.attr.ratingBarStyleSmall), Rating {
    override fun setRating(rating: Float) { super.setRating(rating) }
}

private fun buildNativeAdView(c: Context, slot: Ads.NativeSlot, ad: NativeAd, bg: Int, stroke: Int, titleColor: Int, subColor: Int, accent: Int): View {
    val placement = slot.key
    val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
    val match = ViewGroup.LayoutParams.MATCH_PARENT
    val root = NativeAdView(c)
    root.layoutParams = ViewGroup.LayoutParams(match, wrap)

    val card = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(c.dp(14), c.dp(12), c.dp(14), c.dp(14))
        background = GradientDrawable().apply { setColor(bg); cornerRadius = c.dp(18).toFloat(); setStroke(c.dp(1), stroke) }
    }

    fun smallText(sp: Float, bold: Boolean = false, lines: Int = 1) = TextView(c).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); setTextColor(subColor); maxLines = lines; ellipsize = TextUtils.TruncateAt.END
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    // header: [icon] title / sponsored            [Ad]
    val header = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val icon = ImageView(c).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    header.addView(icon, LinearLayout.LayoutParams(c.dp(40), c.dp(40)).apply { rightMargin = c.dp(10) })
    val texts = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
    val title = smallText(14f, bold = true).apply { setTextColor(titleColor) }
    val sponsored = smallText(11f)
    texts.addView(title); texts.addView(sponsored)
    header.addView(texts, LinearLayout.LayoutParams(0, wrap, 1f))
    val badge = smallText(10f, bold = true).apply {
        text = "Ad"
        setPadding(c.dp(6), c.dp(1), c.dp(6), c.dp(1))
        background = GradientDrawable().apply { setStroke(c.dp(1), subColor); cornerRadius = c.dp(5).toFloat() }
    }
    header.addView(badge)
    // Yandex requires a feedback view (the SDK attaches its "hide this ad" menu to it) - binding fails without it.
    val feedback = ImageView(c).apply {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setImageResource(android.R.drawable.ic_menu_more)
        setColorFilter(subColor)
        setPadding(c.dp(4), c.dp(4), c.dp(4), c.dp(4))
    }
    header.addView(feedback, LinearLayout.LayoutParams(c.dp(30), c.dp(30)).apply { leftMargin = c.dp(6) })
    card.addView(header)

    val body = smallText(12f, lines = 2)
    card.addView(body, LinearLayout.LayoutParams(match, wrap).apply { topMargin = c.dp(8) })

    // meta row: favicon, rating, review count, price, domain, age (each hidden when the ad has no such asset)
    val meta = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val favicon = ImageView(c).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    val rating = AdRatingBar(c).apply { setIsIndicator(true); numStars = 5; stepSize = 0.5f }
    val reviewCount = smallText(11f)
    val price = smallText(11f, bold = true)
    val domain = smallText(11f)
    val age = smallText(11f)
    meta.addView(favicon, LinearLayout.LayoutParams(c.dp(14), c.dp(14)).apply { rightMargin = c.dp(6) })
    meta.addView(rating, LinearLayout.LayoutParams(wrap, wrap).apply { rightMargin = c.dp(6) })
    meta.addView(reviewCount, LinearLayout.LayoutParams(wrap, wrap).apply { rightMargin = c.dp(8) })
    meta.addView(price, LinearLayout.LayoutParams(wrap, wrap).apply { rightMargin = c.dp(8) })
    meta.addView(domain, LinearLayout.LayoutParams(0, wrap, 1f).apply { rightMargin = c.dp(8) })
    meta.addView(age, LinearLayout.LayoutParams(wrap, wrap))
    card.addView(meta, LinearLayout.LayoutParams(match, wrap).apply { topMargin = c.dp(6) })

    val warning = smallText(10f, lines = 2)
    card.addView(warning, LinearLayout.LayoutParams(match, wrap).apply { topMargin = c.dp(6) })

    val media = MediaView(c)
    card.addView(media, LinearLayout.LayoutParams(match, c.dp(150)).apply { topMargin = c.dp(10) })

    val cta = Button(c).apply {
        isAllCaps = false; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(AColor.WHITE); setTypeface(typeface, Typeface.BOLD)
        background = GradientDrawable().apply { setColor(accent); cornerRadius = c.dp(12).toFloat() }
        minHeight = 0; minimumHeight = 0; stateListAnimator = null
        setPadding(c.dp(12), c.dp(8), c.dp(12), c.dp(8))
    }
    card.addView(cta, LinearLayout.LayoutParams(match, c.dp(42)).apply { topMargin = c.dp(10) })
    root.addView(card)

    // Hide the views whose asset this ad does not carry. Every asset the ad DOES carry has a bound view below.
    val a = ad.adAssets
    fun hideIfNull(v: View, asset: Any?) { if (asset == null) v.visibility = View.GONE }
    hideIfNull(icon, a.icon); hideIfNull(media, a.media); hideIfNull(body, a.body); hideIfNull(sponsored, a.sponsored)
    hideIfNull(cta, a.callToAction); hideIfNull(warning, a.warning)
    hideIfNull(favicon, a.favicon); hideIfNull(rating, a.rating); hideIfNull(reviewCount, a.reviewCount)
    hideIfNull(price, a.price); hideIfNull(domain, a.domain); hideIfNull(age, a.age)
    if (a.favicon == null && a.rating == null && a.reviewCount == null && a.price == null && a.domain == null && a.age == null) meta.visibility = View.GONE

    // SDK 8: bindNativeAd returns a result object (Success / Failure) instead of throwing.
    val bindResult = ad.bindNativeAd(
        NativeAdViewBinder.Builder(root)
            .setTitleView(title).setBodyView(body).setIconView(icon).setMediaView(media)
            .setCallToActionView(cta).setSponsoredView(sponsored).setWarningView(warning)
            .setAgeView(age).setDomainView(domain).setFaviconView(favicon)
            .setPriceView(price).setRatingView(rating).setReviewCountView(reviewCount)
            .setFeedbackView(feedback)
            .build()
    )
    val failure = bindResult as? AdBindingResult.Failure
    val failMsg = failure?.exception?.message ?: "unknown"
    Log.d(Ads.TAG, "Native[$placement] bind result: ${if (failure == null) "Success" else "Failure: $failMsg"}")
    val failed = failure != null
    val resultName = failMsg
    if (failed) {
        root.visibility = View.GONE
        Ads.bindFailed(slot, ad, resultName)
    } else {
        // Safety net: never leave an empty "Ad" box on screen.
        root.postDelayed({
            if (title.text.isNullOrEmpty()) {
                root.visibility = View.GONE
                Ads.bindFailed(slot, ad, "bound but title is empty")
            } else Ads.bindOk(slot)
        }, 1500)
    }
    return root
}
