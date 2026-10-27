package com.pixorapdf

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
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
    private const val DEMO_NATIVE_ID = "demo-native-content-yandex"
    private const val DEMO_INTERSTITIAL_ID = "demo-interstitial-yandex"

    val nativeUnit: String get() = if (BuildConfig.DEBUG) DEMO_NATIVE_ID else NATIVE_ID
    private val interstitialUnit: String get() = if (BuildConfig.DEBUG) DEMO_INTERSTITIAL_ID else INTERSTITIAL_ID

    // ---- Interstitial frequency rules (tweak here) ----
    private const val EVERY_NTH_EXIT = 2              // 1st finished task: no ad, 2nd: ad, 3rd: no ad, 4th: ad ...
    private const val MIN_GAP_MS = 3 * 60 * 1000L     // at least 3 minutes between two interstitials
    private const val WARMUP_MS = 60 * 1000L          // nothing in the first minute after launch

    private var started = false
    private var startTime = 0L
    private var loader: InterstitialAdLoader? = null
    private var interstitial: InterstitialAd? = null
    private var loading = false
    private var exits = 0
    private var lastShown = 0L

    fun init(app: Context) {
        if (started) return
        started = true
        startTime = System.currentTimeMillis()
        YandexAds.initialize(app) { preloadInterstitial(app) }
    }

    private fun preloadInterstitial(app: Context) {
        if (loading || interstitial != null) return
        val l = loader ?: InterstitialAdLoader(app).also { loader = it }
        loading = true
        l.loadAd(
            AdRequest.Builder(interstitialUnit).build(),
            object : InterstitialAdLoadListener {
                override fun onAdLoaded(interstitialAd: InterstitialAd) { interstitial = interstitialAd; loading = false }
                override fun onAdFailedToLoad(adRequestError: AdRequestError) { loading = false }
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

/** Native ad card. Takes no space until an ad has loaded (or if loading fails). */
@Composable
fun NativeAdCard(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var ad by remember { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(Unit) {
        var disposed = false
        val loader = NativeAdLoader(ctx)
        loader.loadAd(
            AdRequest.Builder(Ads.nativeUnit).build(),
            object : NativeAdLoadListener {
                override fun onAdLoaded(nativeAd: NativeAd) { if (!disposed) ad = nativeAd }
                override fun onAdFailedToLoad(error: AdRequestError) {}
            }
        )
        onDispose { disposed = true }
    }

    val loaded = ad ?: return
    val bg = cs.surface.toArgb()
    val stroke = cs.outlineVariant.toArgb()
    val title = cs.onSurface.toArgb()
    val sub = cs.onSurfaceVariant.toArgb()
    val accent = cs.primary.toArgb()

    AndroidView(
        modifier = modifier,
        factory = { c -> buildNativeAdView(c, loaded, bg, stroke, title, sub, accent) }
    )
}

private fun buildNativeAdView(c: Context, ad: NativeAd, bg: Int, stroke: Int, titleColor: Int, subColor: Int, accent: Int): View {
    val root = NativeAdView(c)
    root.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    val card = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(c.dp(14), c.dp(12), c.dp(14), c.dp(14))
        background = GradientDrawable().apply { setColor(bg); cornerRadius = c.dp(18).toFloat(); setStroke(c.dp(1), stroke) }
    }

    // header: [icon] title / sponsored            [Ad]
    val header = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val icon = ImageView(c).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    header.addView(icon, LinearLayout.LayoutParams(c.dp(40), c.dp(40)).apply { rightMargin = c.dp(10) })

    val texts = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
    val title = TextView(c).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setTextColor(titleColor); setTypeface(typeface, Typeface.BOLD)
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END
    }
    val sponsored = TextView(c).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); setTextColor(subColor); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
    }
    val warning = TextView(c).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); setTextColor(subColor); maxLines = 2; ellipsize = TextUtils.TruncateAt.END
    }
    texts.addView(title); texts.addView(sponsored)
    header.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

    val badge = TextView(c).apply {
        text = "Ad"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); setTextColor(subColor); setTypeface(typeface, Typeface.BOLD)
        setPadding(c.dp(6), c.dp(1), c.dp(6), c.dp(1))
        background = GradientDrawable().apply { setStroke(c.dp(1), subColor); cornerRadius = c.dp(5).toFloat() }
    }
    header.addView(badge)
    card.addView(header)

    val body = TextView(c).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(subColor); maxLines = 2; ellipsize = TextUtils.TruncateAt.END
    }
    card.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = c.dp(8) })

    card.addView(warning, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = c.dp(6) })

    val media = MediaView(c)
    card.addView(media, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, c.dp(150)).apply { topMargin = c.dp(10) })

    val cta = Button(c).apply {
        isAllCaps = false; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(AColor.WHITE); setTypeface(typeface, Typeface.BOLD)
        background = GradientDrawable().apply { setColor(accent); cornerRadius = c.dp(12).toFloat() }
        minHeight = 0; minimumHeight = 0; stateListAnimator = null
        setPadding(c.dp(12), c.dp(8), c.dp(12), c.dp(8))
    }
    card.addView(cta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, c.dp(42)).apply { topMargin = c.dp(10) })
    root.addView(card)

    val a = ad.adAssets
    if (a.icon == null) icon.visibility = View.GONE
    if (a.media == null) media.visibility = View.GONE
    if (a.body == null) body.visibility = View.GONE
    if (a.sponsored == null) sponsored.visibility = View.GONE
    if (a.callToAction == null) cta.visibility = View.GONE
    if (a.warning == null) warning.visibility = View.GONE

    // SDK 8: bindNativeAd no longer throws; it returns a result object.
    ad.bindNativeAd(
        NativeAdViewBinder.Builder(root)
            .setIconView(icon).setTitleView(title).setBodyView(body)
            .setSponsoredView(sponsored).setWarningView(warning)
            .setMediaView(media).setCallToActionView(cta)
            .build()
    )
    return root
}
