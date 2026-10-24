package com.example.openassistant

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

class MainActivity : Activity() {

    private lateinit var input: EditText
    private lateinit var bubble: TextView
    private lateinit var mascot: CatView
    private lateinit var moreBtn: Button
    private var moreUrl: String? = null
    private lateinit var wakeBtn: Button
    private lateinit var cookie: TextView
    private lateinit var zzz: TextView
    private var asleep = false
    private var tts: TextToSpeech? = null
    private val idle = mutableListOf<Animator>()
    private var pendingBt: Boolean? = null

    private data class App(val label: String, val pkg: String)

    private val appAliases = mapOf(
        "ютуб" to "youtube", "телеграм" to "telegram", "телега" to "telegram",
        "ватсап" to "whatsapp", "вацап" to "whatsapp", "вк" to "vk",
        "контакт" to "vk", "хром" to "chrome", "браузер" to "chrome",
        "камера" to "camera", "настройки" to "settings", "почта" to "gmail",
        "карты" to "maps", "гугл" to "google", "инстаграм" to "instagram",
        "тикток" to "tiktok", "спотифай" to "spotify", "нетфликс" to "netflix",
        "калькулятор" to "calculator", "часы" to "clock", "галерея" to "gallery",
        "телефон" to "phone", "сообщения" to "messages", "плей маркет" to "play store"
    )

    private val siteAliases = mapOf(
        "ютуб" to "youtube.com", "гугл" to "google.com", "вк" to "vk.com",
        "яндекс" to "yandex.ru", "википедия" to "wikipedia.org",
        "гитхаб" to "github.com", "телеграм" to "web.telegram.org",
        "почта" to "mail.google.com", "карты" to "maps.google.com"
    )

    private val fillers = setOf(
        "пожалуйста", "открой", "открыть", "запусти", "запустить", "включи",
        "приложение", "программу", "open", "launch", "start", "app", "зайди", "на"
    )

    private val wakeWords = setOf("максик", "привет", "хей", "hey", "хэй", "эй", "хай")
    private val searchWords = setOf("загугли", "гугли", "погугли")          // открыть Google в браузере
    private val findWords = setOf("найди", "поищи", "выясни")               // Максик сам ищет ответ
    private val sleepWords = setOf("спать", "спи", "засыпай", "спокойной", "баиньки")

    private val weatherStop = setOf(
        "в", "во", "на", "сегодня", "сейчас", "какая", "какой", "скажи", "мне", "там",
        "у", "нас", "городе", "покажи", "расскажи", "подскажи", "узнай", "что", "пожалуйста"
    )

    private val numWords = mapOf(
        "ноль" to 0, "десять" to 10, "двадцать" to 20, "тридцать" to 30, "сорок" to 40,
        "пятьдесят" to 50, "шестьдесят" to 60, "семьдесят" to 70, "восемьдесят" to 80,
        "девяносто" to 90, "сто" to 100, "половину" to 50
    )

    // «Что такое…», «кто такой…», «расскажи про…» — ответ из интернета
    private val askRegex = Regex(
        "^(что такое|что значит|что за|кто такой|кто такая|кто такие|расскажи про|расскажи о|расскажи об|" +
            "объясни|узнай про|узнай о|что ты знаешь про|что знаешь про)\\s+(.+)$"
    )

    private val happy = listOf(
        "Мур! Уже бегу!", "Сейчас всё сделаю, мой хороший!", "С удовольствием! Мур-мур!",
        "Лапками раз-два — и готово!", "Для тебя — всегда пожалуйста!", "Мяу! Одну секундочку!"
    )

    private val praiseReplies = listOf(
        "Мур-р-р... Ты тоже молодец!", "Ой, я даже покраснел! Спасибо, мой хороший!",
        "Мяу! Мне так приятно, когда ты меня хвалишь!", "Мур-мур! Ты самый добрый человек на свете!"
    )

    private val thanksReplies = listOf(
        "Всегда пожалуйста, мой хороший! Мур!", "Обращайся в любое время, я только рад!",
        "Мне в радость тебе помогать!"
    )

    private val comfortReplies = listOf(
        "Иди сюда, я рядом. Мур-мур... Всё обязательно наладится.",
        "Мне жаль, что тебе грустно. Давай я посижу рядом и помурлыкаю?",
        "Ты очень хороший, и ты не один. Я с тобой! А если захочется — позвони кому-нибудь родному, это тоже согревает.",
        "Обнимаю лапками! Хочешь, расскажу что-нибудь смешное?"
    )

    private val loveReplies = listOf(
        "Мур-мур-мур! И я тебя очень-очень люблю!", "Ой, у меня сердечко затрепетало! Я тебя тоже люблю!",
        "Ты самый лучший! Мур!"
    )

    private val purrReplies = listOf(
        "Мур-р-р-р... как же приятно!", "Ещё, пожалуйста! Мур-мур!",
        "Мррр... Ты гладишь, как настоящий мастер!"
    )

    private val howAreYouReplies = listOf(
        "У меня всё отлично, а стало ещё лучше, когда ты пришёл! А как ты?",
        "Мур! Хорошо! Выспался, умылся и жду, чем бы тебе помочь.",
        "Замечательно! Только что поймал солнечный зайчик. А у тебя как дела?"
    )

    private val jokeIntros = listOf(
        "Слушай, мой хороший!", "Мяу! Анекдот специально для тебя.", "Хи-хи, вот, послушай.", "Мур! Держи анекдот."
    )

    // Каждый анекдот — строки через \n: подводка, потом «изюминка» (между ними голос делает паузу)
    private val jokes = listOf(
        "— Доктор, я всё время всё забываю!\n— Давно это с вами?\n— Что «давно»?",
        "Почему программисты путают Хэллоуин и Рождество?\nПотому что тридцать один в восьмеричной системе — это двадцать пять в десятичной.",
        "Жена программисту: «Сходи в магазин, купи батон. Если будут яйца — возьми десяток».\nОн вернулся с десятью батонами: «Яйца были».",
        "Учительница спрашивает Вовочку:\n— Почему ты опоздал?\n— Там на дороге написано: «Школа, медленно». Я и шёл медленно.",
        "Купили мы котёнка, чтобы его воспитывать.\nТеперь он нас воспитывает: подъём в шесть утра, завтрак в шесть ноль пять, и без возражений.",
        "— Как понять, что кот тебя любит?\n— Он будет сбрасывать со стола твою кружку. Только твою. Глядя тебе в глаза.",
        "— Мама, а почему кошка мурлычет?\n— У неё внутри моторчик.\n— А где у неё бензобак?\n— В миске. Иди насыпь.",
        "Кот — это жидкость.\nЕсли он поместился в коробку, значит, он в ней растёкся.",
        "Заходит человек в аптеку:\n— У вас есть таблетки от жадности?\n— Есть. Пятьсот рублей.\n— А подешевле?",
        "— Папа, что такое ирония?\n— Это когда я говорю маме «я на минутку» и ухожу в гараж на три часа.",
        "Два ёжика обнимаются.\n— Ты всегда такой колючий?\n— Нет. Это я стесняюсь.",
        "Знаешь, чем я отличаюсь от будильника?\nБудильник можно выключить, а меня — только погладить. И то я не выключусь, а включу мурлыканье.",
        "Я не ленивый.\nЯ просто экономлю силы для важных дел. Например, для спанья.",
        "Почему кот всегда падает на лапы?\nПотому что заранее договаривается с землёй.",
        "Звонит в дверь почтальон:\n— Вам посылка от вашего кота!\n— Что там?\n— Пустая коробка. Он говорит: главное — коробка.",
        "Учитель:\n— Назовите три местоимения.\n— Кто? Я? Мы?\n— Правильно!",
        "— Бабуль, а почему я такой умный?\n— Это всё мои пирожки, внучек.",
        "Программист рассказывает:\n— Полночи искал ошибку в коде!\n— И нашёл?\n— Нашёл. Нужно было просто сохранить файл.",
        "— Почему ты улыбаешься, Максик?\n— Мне только что почесали за ушком.\n— И всё?\n— А что ещё нужно для счастья?",
        "Если кот сидит и смотрит в пустой угол — значит, он видит там то, что нам знать не положено.\nИ обсуждает это с углом."
    )

    // ================= UI =================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initTts()

        val d = resources.displayMetrics.density
        val pad = (24 * d).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }
        val title = TextView(this).apply {
            text = "Максик 🐾"; textSize = 32f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#E65100"))
        }
        bubble = TextView(this).apply {
            text = "Мяу! Я Максик — твой котёнок-помощник. Открываю приложения и сайты, ищу ответы в интернете, говорю погоду, шучу и управляю телефоном. А ещё меня можно погладить!"
            textSize = 16f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#3E2723"))
            setPadding(pad, pad / 2, pad, pad / 2)
            background = GradientDrawable().apply {
                setColor(Color.WHITE); cornerRadius = 28 * d
                setStroke((2 * d).toInt(), Color.parseColor("#FFB74D"))
            }
        }
        mascot = CatView(this).apply {
            contentDescription = "Максик"
            setOnClickListener { petCat() }
        }
        cookie = TextView(this).apply {
            text = "🍪"; textSize = 44f; visibility = View.INVISIBLE
        }
        zzz = TextView(this).apply {
            text = "💤"; textSize = 32f; visibility = View.INVISIBLE
            translationX = 70 * d
        }
        val wrap = FrameLayout.LayoutParams.WRAP_CONTENT
        val stage = FrameLayout(this).apply {
            clipChildren = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (250 * d).toInt())
            addView(mascot, FrameLayout.LayoutParams((190 * d).toInt(), (190 * d).toInt(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
            addView(zzz, FrameLayout.LayoutParams(wrap, wrap, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
            addView(cookie, FrameLayout.LayoutParams(wrap, wrap, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        }
        moreBtn = Button(this).apply {
            text = "📖 Читать полностью"; visibility = View.GONE
            setOnClickListener { moreUrl?.let { openUrl(it, "статью") } }
        }
        input = EditText(this).apply {
            hint = "Например: что такое радуга"
            imeOptions = EditorInfo.IME_ACTION_GO
            setSingleLine()
            setOnEditorActionListener { _, _, _ -> handle(text.toString()); true }
        }
        val mic = Button(this).apply { text = "🎤 Говорить"; setOnClickListener { listen() } }
        val go = Button(this).apply { text = "Выполнить"; setOnClickListener { handle(input.text.toString()) } }
        wakeBtn = Button(this).apply { setOnClickListener { toggleWake() } }

        listOf(title, bubble, moreBtn, stage, input, mic, go, wakeBtn).forEach { root.addView(it) }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.parseColor("#FFF8E1"))
            addView(root)
        })

        updateWakeBtn()
        startIdle()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updateWakeBtn()
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        if (i.hasExtra("command")) {
            val c = i.getStringExtra("command") ?: ""
            i.removeExtra("command")
            if (c.isBlank()) listen() else { input.setText(c); handle(c) }
        } else if (i.action == Intent.ACTION_ASSIST) listen()
    }

    // ================= Анимации =================
    // Лицо, дыхание, моргание, хвост и уши рисует сам CatView.
    // Здесь — движения всего котёнка целиком: прыжок, тряска, печенька, сон.
    private val dp: Float get() = resources.displayMetrics.density
    private var running: AnimatorSet? = null

    private fun anim(
        target: Any, prop: String, from: Float, to: Float, ms: Long,
        interp: android.animation.TimeInterpolator? = null
    ): ObjectAnimator =
        ObjectAnimator.ofFloat(target, prop, from, to).apply {
            duration = ms
            if (interp != null) interpolator = interp
        }

    private fun forever(a: ObjectAnimator, ms: Long, mode: Int = ValueAnimator.REVERSE) = a.apply {
        duration = ms
        repeatCount = ValueAnimator.INFINITE
        repeatMode = mode
        interpolator = if (mode == ValueAnimator.RESTART) LinearInterpolator() else AccelerateDecelerateInterpolator()
        idle.add(this); start()
    }

    /** Запускает набор анимаций; onEnd вызывается, только если набор доиграл до конца (не был прерван). */
    private fun play(set: AnimatorSet, onEnd: () -> Unit) {
        running?.cancel()
        var cancelled = false
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) { cancelled = true }
            override fun onAnimationEnd(animation: Animator) { if (!cancelled) onEnd() }
        })
        running = set
        set.start()
    }

    private fun backToIdle(set: AnimatorSet) = play(set) { startIdle() }

    private fun after(set: AnimatorSet, block: () -> Unit) = play(set, block)

    private fun stopIdle() {
        idle.forEach { it.cancel() }
        idle.clear()
        running?.cancel()
        running = null
        mascot.animate().cancel()
        mascot.translationX = 0f; mascot.translationY = 0f
        mascot.rotation = 0f; mascot.scaleX = 1f; mascot.scaleY = 1f
        mascot.pivotX = mascot.width / 2f; mascot.pivotY = mascot.height * 0.95f
        zzz.visibility = View.INVISIBLE
    }

    private fun startIdle() {
        stopIdle()
        if (asleep) {
            // Максик спит: дышит (это делает CatView), вверх плывёт «Zzz»
            mascot.mood = Mood.SLEEP
            val top = mascot.top.toFloat()
            zzz.visibility = View.VISIBLE
            forever(ObjectAnimator.ofFloat(zzz, "translationY", top, top - 120f), 2200, ValueAnimator.RESTART)
            forever(ObjectAnimator.ofFloat(zzz, "alpha", 1f, 0f), 2200, ValueAnimator.RESTART)
        } else {
            mascot.mood = Mood.NORMAL
        }
    }

    /** Котёнок «задумался» / «слушает»: чуть-чуть пружинит, пока ждём */
    private fun pulse(mood: Mood) {
        stopIdle()
        mascot.mood = mood
        forever(ObjectAnimator.ofFloat(mascot, "scaleX", 1f, 1.04f), 600)
        forever(ObjectAnimator.ofFloat(mascot, "scaleY", 1f, 1.04f), 600)
    }

    /** Мягкий прыжок радости: приседает, подпрыгивает, приземляется */
    private fun jump(mood: Mood = Mood.HAPPY) {
        stopIdle()
        mascot.mood = mood
        val hop = 70 * dp
        val squash = AnimatorSet().apply {
            playTogether(anim(mascot, "scaleY", 1f, 0.86f, 140), anim(mascot, "scaleX", 1f, 1.08f, 140))
        }
        val up = AnimatorSet().apply {
            playTogether(
                anim(mascot, "translationY", 0f, -hop, 320, DecelerateInterpolator()),
                anim(mascot, "scaleY", 0.86f, 1.08f, 320),
                anim(mascot, "scaleX", 1.08f, 0.95f, 320)
            )
        }
        val down = AnimatorSet().apply {
            playTogether(
                anim(mascot, "translationY", -hop, 0f, 420, BounceInterpolator()),
                anim(mascot, "scaleY", 1.08f, 1f, 420),
                anim(mascot, "scaleX", 0.95f, 1f, 420)
            )
        }
        val hold = anim(mascot, "alpha", 1f, 1f, 900)   // чуть задержать весёлое лицо
        backToIdle(AnimatorSet().apply { playSequentially(squash, up, down, hold) })
    }

    /** Не получилось / не понял: головка кружится */
    private fun shake() {
        stopIdle()
        mascot.mood = Mood.DIZZY
        val x = ObjectAnimator.ofFloat(mascot, "translationX", 0f, -30f, 30f, -22f, 22f, -12f, 12f, 0f)
            .apply { duration = 700 }
        val hold = anim(mascot, "alpha", 1f, 1f, 500)
        backToIdle(AnimatorSet().apply { playSequentially(x, hold) })
    }

    /** Мурчит от удовольствия: сердечки, лёгкое «дыхание» */
    private fun purr() {
        stopIdle()
        mascot.mood = Mood.LOVE
        val y = anim(mascot, "scaleY", 1f, 1.05f, 300).apply { repeatCount = 9; repeatMode = ValueAnimator.REVERSE }
        val x = anim(mascot, "scaleX", 1f, 0.97f, 300).apply { repeatCount = 9; repeatMode = ValueAnimator.REVERSE }
        backToIdle(AnimatorSet().apply { playTogether(y, x) })
    }

    /** Печенька падает на голову, Максик благодарит и съедает её */
    private fun treat() {
        stopIdle()
        mascot.mood = Mood.THINK            // смотрит вверх на печеньку
        val startY = -cookie.height * 1.5f
        val land = mascot.top + mascot.height * 0.08f - cookie.height * 0.5f
        cookie.visibility = View.VISIBLE
        cookie.alpha = 1f; cookie.scaleX = 1f; cookie.scaleY = 1f
        cookie.rotation = 0f; cookie.translationY = startY

        val fall = anim(cookie, "translationY", startY, land, 800, BounceInterpolator())
        val spin = anim(cookie, "rotation", 0f, 720f, 800)
        val squash = ObjectAnimator.ofFloat(mascot, "scaleY", 1f, 0.86f, 1f).apply {
            duration = 350; startDelay = 280
        }
        after(AnimatorSet().apply { playTogether(fall, spin, squash) }) {
            mascot.mood = Mood.EAT
            say("Мур! Спасибо, мой хороший!")
            eat()
        }
    }

    private fun eat() {
        val sizes = listOf(1f, 0.7f, 0.45f, 0.2f, 0f)
        val bites = (0 until sizes.size - 1).map { i ->
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(cookie, "scaleX", sizes[i], sizes[i + 1]),
                    ObjectAnimator.ofFloat(cookie, "scaleY", sizes[i], sizes[i + 1]),
                    ObjectAnimator.ofFloat(mascot, "scaleX", 1f, 1.08f, 1f)
                )
                duration = 450
            }
        }
        after(AnimatorSet().apply { playSequentially(bites) }) {
            cookie.visibility = View.INVISIBLE
            jump(Mood.LOVE)
        }
    }

    private fun praise() { say(praiseReplies.random()); meow(); jump(Mood.LOVE) }

    private fun thanks() { say(thanksReplies.random()); jump() }

    private fun love() { say(loveReplies.random()); meow(); jump(Mood.LOVE) }

    private fun comfort() { say(comfortReplies.random()); purr() }

    private fun howAreYou() { say(howAreYouReplies.random()); jump() }

    private fun petCat() {
        if (asleep) { say("Мррр... (во сне улыбается)", false); return }
        say(purrReplies.random())
        if (Math.random() < 0.5) meow()
        purr()
    }

    private fun sleep() {
        if (asleep) { say("Мрр... Я уже сплю", false); return }
        asleep = true
        say("Спокойной ночи, мой хороший. Сладких снов! Мур-мур...")
        stopIdle()
        mascot.mood = Mood.YAWN
        val yawn = ObjectAnimator.ofFloat(mascot, "scaleY", 1f, 1.08f, 1f).apply { duration = 1600 }
        backToIdle(AnimatorSet().apply { playTogether(yawn) })
    }

    private fun wakeUp() {
        if (!asleep) { say("Я не сплю, я просто глазки закрыл! Мяу!"); jump(); return }
        asleep = false
        say("Доброе утро! Мур-р-р, как же сладко я потянулся!")
        stopIdle()
        mascot.mood = Mood.YAWN
        val stretchY = ObjectAnimator.ofFloat(mascot, "scaleY", 1f, 1.2f, 1f).apply { duration = 1100 }
        val stretchX = ObjectAnimator.ofFloat(mascot, "scaleX", 1f, 0.9f, 1f).apply { duration = 1100 }
        after(AnimatorSet().apply { playTogether(stretchY, stretchX) }) { jump() }
    }

    // ================= Речь =================
    private var voiceMissing = false
    private var talkToken = 0
    @Volatile private var lastUtterance = ""

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            val t = tts ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) { voiceMissing = true; return@TextToSpeech }
            val r = t.setLanguage(Locale("ru", "RU"))
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) voiceMissing = true
            else pickVoice(t)
            t.setPitch(1.3f)          // повыше — котёнок всё-таки маленький
            t.setSpeechRate(0.97f)    // чуть неторопливее — звучит мягче
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (utteranceId?.startsWith("m") == true) runOnUiThread { mascot.talking = true }
                }
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == lastUtterance) runOnUiThread { mascot.talking = false }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    runOnUiThread { mascot.talking = false }
                }
            })
            if (voiceMissing) runOnUiThread {
                say("Мне нужен русский голос. Открой Настройки → Язык и ввод → Синтез речи и установи русский язык.", false)
            }
        }
    }

    /** Выбираем самый качественный из установленных русских голосов */
    private fun pickVoice(t: TextToSpeech) {
        try {
            val best = t.voices
                ?.filter { it.locale.language == "ru" && !it.features.contains("notInstalled") }
                ?.maxByOrNull { it.quality }
            if (best != null) t.voice = best
        } catch (_: Exception) { }
    }

    /** Убираем то, что синтезатор читает плохо: эмодзи, тире, скобки, единицы измерения */
    private fun cleanForSpeech(s: String) = s
        .replace(Regex("[\\p{So}\\p{Cs}\\u200d\\uFE0F]"), "")
        .replace(Regex("(?m)^—\\s*"), "")
        .replace("м/с", " метров в секунду")
        .replace("°", " градусов")
        .replace("%", " процентов")
        .replace("Wi-Fi", "вай-фай")
        .replace("—", ", ")
        .replace("«", "").replace("»", "")
        .replace(Regex("\\s*\\([^)]*\\)"), "")

    /** Говорим по предложениям, между ними — маленькие паузы; между строками анекдота — паузу побольше */
    private fun talk(text: String) {
        talkToken++
        val t = tts
        if (t == null || voiceMissing) {
            // Голоса нет — хотя бы двигаем ртом, пока читается текст
            mascot.talking = true
            val tok = talkToken
            mascot.postDelayed({ if (tok == talkToken) mascot.talking = false }, text.length * 65L + 500)
            return
        }
        val blocks = cleanForSpeech(text).split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        var first = true
        var n = 0
        for (b in blocks) {
            val parts = b.split(Regex("(?<=[.!?…])\\s+")).filter { it.isNotBlank() }
            for ((i, p) in parts.withIndex()) {
                val id = "m${talkToken}_${n++}"
                lastUtterance = id
                t.speak(p, if (first) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
                first = false
                if (i < parts.size - 1) t.playSilentUtterance(140, TextToSpeech.QUEUE_ADD, "s${talkToken}_${n++}")
            }
            t.playSilentUtterance(450, TextToSpeech.QUEUE_ADD, "s${talkToken}_${n++}")
        }
    }

    private fun say(text: String, speak: Boolean = true) {
        bubble.text = text
        moreBtn.visibility = View.GONE
        if (speak) talk(text) else { talkToken++; mascot.talking = false }
    }

    /** Мяуканье. Если положить свой звук в app/src/main/res/raw/meow.ogg (или .mp3/.wav) — будет играть он,
     *  иначе мяу синтезируется прямо в приложении. */
    private fun meow() {
        thread {
            try {
                val raw = resources.getIdentifier("meow", "raw", packageName)
                if (raw != 0) {
                    MediaPlayer.create(this, raw)?.let { mp ->
                        mp.setOnCompletionListener { it.release() }
                        mp.start()
                    }
                    return@thread
                }
                val sr = 22050
                val n = (sr * 0.6).toInt()
                val buf = ShortArray(n)
                var phase = 0.0
                for (i in 0 until n) {
                    val x = i / n.toDouble()
                    val f = 430.0 + 520.0 * sin(PI * x) + 25.0 * sin(2 * PI * 7.0 * x)
                    phase += 2 * PI * f / sr
                    val env = minOf(1.0, x / 0.07) * Math.pow(1.0 - x, 0.7)
                    val v = 0.55 * sin(phase) + 0.30 * sin(2 * phase) + 0.18 * sin(3 * phase) + 0.08 * sin(4 * phase)
                    buf[i] = (v * env * 0.45 * Short.MAX_VALUE).toInt().toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sr)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                    )
                    .setBufferSizeInBytes(n * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(buf, 0, n)
                track.play()
                Thread.sleep(800)
                track.release()
            } catch (_: Exception) { }
        }
    }

    private fun listen() {
        tts?.stop()
        pulse(Mood.LISTEN)
        say("Мур? Слушаю тебя!", false)
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Что сделать Максику?")
        }
        try {
            startActivityForResult(i, 1)
        } catch (e: Exception) {
            say("Голосовой ввод недоступен, напиши текстом", false)
            shake()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 1) return
        val text = if (resultCode == RESULT_OK)
            data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() else null
        if (text != null) { input.setText(text); handle(text) } else startIdle()
    }

    // ================= Разбор команды =================
    private fun has(tokens: List<String>, vararg p: String) =
        tokens.any { t -> p.any { t.startsWith(it) } }

    private fun numberOf(tokens: List<String>): Int? =
        tokens.firstNotNullOfOrNull { it.trim('%').toIntOrNull() ?: numWords[it] }

    private fun handle(raw: String) {
        val cleaned = raw.lowercase(Locale.getDefault())
            .replace(" точка ", ".")
            .replace(Regex("[,!?;:]"), " ")
            .trim().trimEnd('.')
        val tokens = cleaned.split(Regex("\\s+")).filter { it.isNotEmpty() && it !in wakeWords }
        val q = tokens.joinToString(" ")
        if (tokens.isEmpty()) { say("Мяу! Я здесь. Чем могу помочь?"); meow(); jump(); return }
        val off = has(tokens, "выключ", "отключ", "выкл")
        val music = has(tokens, "трек", "песн", "музык", "мелоди")

        when {
            has(tokens, "погод") ->
                weather(tokens.filter { !it.startsWith("погод") && it !in weatherStop }.joinToString(" "))
            askRegex.containsMatchIn(q) -> lookup(askRegex.find(q)!!.groupValues[2])
            tokens[0] in findWords -> lookup(tokens.drop(1).joinToString(" "))
            tokens[0] in searchWords -> search(tokens.drop(1).joinToString(" "))
            has(tokens, "вкусняш", "печень", "угощен", "лакомств") -> treat()
            has(tokens, "молодец", "умница", "умничка", "хороший") -> praise()
            has(tokens, "спасибо", "благодар") -> thanks()
            has(tokens, "люблю", "обожаю") -> love()
            has(tokens, "погладь", "поглажу", "погладить", "мурлык", "мурчи") -> petCat()
            q.contains("как дела") || q.contains("как ты там") || q.contains("как жизнь") -> howAreYou()
            (has(tokens, "доброе", "добрым") && has(tokens, "утр")) ||
                has(tokens, "встава", "просыпай", "проснись", "подъем", "подъём") -> wakeUp()
            tokens.any { it in sleepWords } || has(tokens, "ложит", "ложис") -> sleep()
            has(tokens, "грустн", "печальн", "тоскл", "одинок", "плохо", "устал", "обидел", "обидно") -> comfort()
            has(tokens, "анекдот", "шутк", "рассмеши", "пошути") -> joke()
            isRinger(q, tokens) -> ringerCommand(q, tokens, off)
            has(tokens, "яркост", "ярче", "ярч", "темн", "затемн") -> brightness(tokens)
            has(tokens, "громк", "громче", "тише", "убавь", "прибавь") -> volume(tokens)
            has(tokens, "предыдущ", "прошл") || (music && has(tokens, "назад")) ->
                mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Предыдущий трек")
            has(tokens, "следующ", "дальше", "пропуст") || (music && has(tokens, "переключ")) ->
                mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT, "Следующий трек")
            has(tokens, "пауз", "приостанов") || (music && has(tokens, "стоп", "останов", "выключ", "отключ")) ->
                mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE, "Пауза")
            has(tokens, "играй", "продолж", "воспроизв") || (music && has(tokens, "включ")) ->
                mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY, "Играю")
            q.contains("wi fi") || q.contains("wi-fi") || q.contains("вай фай") ||
                has(tokens, "wifi", "вайфай", "вай-фай") -> wifi(!off)
            has(tokens, "блют", "bluetooth", "блю-") -> bluetooth(!off)
            else -> {
                val domain = Regex("([a-z0-9-]+\\.)+(com|ru|org|net|io|ua|by|kz|me|tv|app|dev|info|su|co)(/\\S*)?")
                    .find(q)?.value
                when {
                    domain != null -> openUrl("https://$domain", domain)
                    "сайт" in tokens -> openSite(tokens)
                    else -> openApp(tokens.filter { it !in fillers }.joinToString(" "))
                }
            }
        }
    }

    // ================= Анекдоты =================
    private fun joke() { say(jokeIntros.random() + "\n" + jokes.random()); jump() }

    // ================= Управление телефоном =================
    private fun audio() = getSystemService(AUDIO_SERVICE) as AudioManager

    private fun openSettings(action: String, msg: String) {
        try { startActivity(Intent(action)); say(msg) } catch (e: Exception) { say("Не смог открыть настройки"); shake(); return }
        startIdle()
    }

    private fun mediaKey(code: Int, label: String) {
        val am = audio()
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        say(label); jump()
    }

    private fun volume(tokens: List<String>) {
        val am = audio()
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val step = maxOf(1, max / 7)
        val num = numberOf(tokens)
        val target = when {
            num != null -> max * num.coerceIn(0, 100) / 100
            has(tokens, "громче", "прибав", "увелич", "повыс") -> cur + step
            has(tokens, "тише", "убав", "уменьш", "пониз") -> cur - step
            has(tokens, "максим", "полную") -> max
            has(tokens, "минимум", "минимал") -> 0
            else -> { say("Громкость сейчас ${cur * 100 / max}%"); return }
        }.coerceIn(0, max)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        say("Громкость ${target * 100 / max}%"); jump()
    }

    private fun brightness(tokens: List<String>) {
        if (!Settings.System.canWrite(this)) {
            say("Разреши мне менять настройки: включи переключатель и вернись ко мне")
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
            shake(); return
        }
        val cr = contentResolver
        val cur = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
        val num = numberOf(tokens)
        val target = when {
            num != null -> 255 * num.coerceIn(0, 100) / 100
            has(tokens, "ярче", "ярч", "увелич", "прибав", "повыс") -> cur + 64
            has(tokens, "темн", "затемн", "убав", "уменьш", "пониз") -> cur - 64
            has(tokens, "максим") -> 255
            has(tokens, "минимум", "минимал") -> 10
            else -> { say("Яркость сейчас ${cur * 100 / 255}%"); return }
        }.coerceIn(1, 255)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, target)
        say("Яркость ${target * 100 / 255}%"); jump()
    }

    private fun isRinger(q: String, t: List<String>) =
        has(t, "вибро", "вибрац", "беззвуч") || q.contains("без звука") || q.contains("обычный режим") ||
            (q.contains("звук") && has(t, "включ", "выключ", "отключ", "режим", "верни"))

    private fun ringerCommand(q: String, t: List<String>, off: Boolean) {
        when {
            has(t, "вибро", "вибрац") && !off -> ringer(AudioManager.RINGER_MODE_VIBRATE, "вибрация")
            has(t, "вибро", "вибрац") && off -> ringer(AudioManager.RINGER_MODE_NORMAL, "со звуком")
            q.contains("без звука") || has(t, "беззвуч") || (off && q.contains("звук")) ->
                ringer(AudioManager.RINGER_MODE_SILENT, "без звука")
            else -> ringer(AudioManager.RINGER_MODE_NORMAL, "со звуком")
        }
    }

    private fun ringer(mode: Int, label: String) {
        try {
            audio().ringerMode = mode
            say("Режим: $label"); jump()
            if (mode == AudioManager.RINGER_MODE_VIBRATE) buzz()
        } catch (e: SecurityException) {
            say("Для этого режима нужен доступ к «Не беспокоить». Разреши и повтори")
            try { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) } catch (_: Exception) {}
            shake()
        }
    }

    @Suppress("DEPRECATION")
    private fun buzz() {
        val v = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        else v.vibrate(300)
    }

    @Suppress("DEPRECATION")
    private fun wifi(on: Boolean) {
        if (Build.VERSION.SDK_INT < 29) {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            wm.isWifiEnabled = on
            say("Wi-Fi ${if (on) "включён" else "выключен"}"); jump(); return
        }
        // Android 10+ запрещает приложениям переключать Wi-Fi напрямую
        val action = if (Build.VERSION.SDK_INT >= 30) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS
        openSettings(action, "Android не даёт мне самому переключать Wi-Fi. Открыл панель, нажми переключатель")
    }

    private fun bluetooth(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            pendingBt = on
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 2)
            return
        }
        doBluetooth(on)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun doBluetooth(on: Boolean) {
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null) { say("На этом телефоне нет Bluetooth"); shake(); return }
        if (Build.VERSION.SDK_INT < 33) {
            val ok = try { if (on) adapter.enable() else adapter.disable() } catch (e: SecurityException) { false }
            if (ok || adapter.isEnabled == on) {
                say("Bluetooth ${if (on) "включается" else "выключается"}"); jump(); return
            }
        }
        openSettings(Settings.ACTION_BLUETOOTH_SETTINGS,
            "На Android 13+ приложениям нельзя переключать Bluetooth. Открыл настройки, нажми переключатель")
    }

    // ================= «Привет, Максик» =================
    private fun wakeOn() = getSharedPreferences("maksik", MODE_PRIVATE).getBoolean("wake", false)

    private fun updateWakeBtn() {
        wakeBtn.text = if (wakeOn()) "👂 «Привет, Максик»: ВКЛ" else "👂 «Привет, Максик»: выкл"
    }

    private fun toggleWake() {
        val prefs = getSharedPreferences("maksik", MODE_PRIVATE)
        if (wakeOn()) {
            stopService(Intent(this, WakeService::class.java))
            prefs.edit().putBoolean("wake", false).apply()
            updateWakeBtn(); say("Больше не подслушиваю", false); return
        }
        val need = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.POST_NOTIFICATIONS)
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 3) else continueWake()
    }

    private fun continueWake() {
        if (!Settings.canDrawOverlays(this)) {
            say("Разреши «Поверх других окон» (так я смогу открывать приложения по голосу) и нажми кнопку ещё раз")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val i = Intent(this, WakeService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        getSharedPreferences("maksik", MODE_PRIVATE).edit().putBoolean("wake", true).apply()
        updateWakeBtn()
        say("Теперь я слушаю! Скажи: «Привет, Максик, открой ютуб»")
        jump()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            2 -> {
                val on = pendingBt ?: return
                pendingBt = null
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) doBluetooth(on)
                else openSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Без разрешения открою настройки Bluetooth")
            }
            3 -> {
                val i = permissions.indexOf(Manifest.permission.RECORD_AUDIO)
                if (i == -1 || grantResults[i] == PackageManager.PERMISSION_GRANTED) continueWake()
                else { say("Без микрофона я тебя не услышу"); shake() }
            }
        }
    }

    // ================= Сайты и поиск =================
    private fun openUrl(url: String, name: String, msg: String? = null) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            say(msg ?: "${happy.random()} Открываю $name"); jump()
        } catch (e: Exception) {
            say("Не смог открыть $name"); shake()
        }
    }

    private fun openSite(tokens: List<String>) {
        val name = tokens.filter { it != "сайт" && it !in fillers }.joinToString(" ")
        val site = siteAliases[name]
        if (site != null) openUrl("https://$site", site) else lookup(name)
    }

    /** «Загугли …» — открыть поиск Google в браузере */
    private fun search(query: String) {
        if (query.isBlank()) { say("Что искать?"); shake(); return }
        openUrl("https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8"), "поиск «$query»")
    }

    private data class Answer(val title: String, val text: String, val url: String)

    /** «Найди …», «что такое …», «кто такой …» — Максик сам идёт в интернет и приносит короткий ответ */
    private fun lookup(queryRaw: String) {
        val query = queryRaw
            .replace(Regex("^(?:в интернете|в сети|в инете|информацию|инфу)\\s*(?:про|о|об)?\\s*"), "")
            .trim()
        if (query.isBlank()) { say("Что поискать? Скажи, например: «что такое радуга»"); shake(); return }

        pulse(Mood.THINK)
        say("Сейчас поищу в интернете, мур...", false)
        thread {
            try {
                val a = wiki(query)
                runOnUiThread {
                    if (a != null) showAnswer(a)
                    else openUrl(
                        "https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8"),
                        "поиск «$query»",
                        "В энциклопедии про «$query» ничего нет, зато открываю Google. Мяу!"
                    )
                }
            } catch (e: Exception) {
                runOnUiThread { say("Не получилось выйти в интернет. Проверь соединение, пожалуйста"); shake() }
            }
        }
    }

    /** Ищем статью в русской Википедии и берём из неё краткое описание */
    private fun wiki(query: String): Answer? {
        val s = JSONObject(get(
            "https://ru.wikipedia.org/w/api.php?action=query&list=search&srsearch=" +
                URLEncoder.encode(query, "UTF-8") + "&srlimit=1&format=json&utf8=1"
        ))
        val found = s.getJSONObject("query").getJSONArray("search")
        if (found.length() == 0) return null
        val title = found.getJSONObject(0).getString("title")
        val p = JSONObject(get(
            "https://ru.wikipedia.org/api/rest_v1/page/summary/" +
                URLEncoder.encode(title, "UTF-8").replace("+", "%20")
        ))
        if (p.optString("type") == "disambiguation") return null
        val extract = p.optString("extract").trim()
        if (extract.isEmpty()) return null
        val link = p.optJSONObject("content_urls")?.optJSONObject("mobile")?.optString("page")
            ?.takeIf { it.isNotBlank() }
            ?: ("https://ru.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8"))
        return Answer(title, shorten(extract), link)
    }

    /** Оставляем первые предложения, чтобы ответ был коротким и его приятно было слушать */
    private fun shorten(t: String): String {
        val sentences = t.split(Regex("(?<=[.!?])\\s+(?=[А-ЯЁA-Z«\"])"))
        val sb = StringBuilder()
        for (s in sentences) {
            if (sb.isNotEmpty() && sb.length + s.length > 320) break
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(s)
            if (sb.length > 200) break
        }
        return sb.toString().take(500)
    }

    private fun showAnswer(a: Answer) {
        say("Вот что я нашёл!", false)
        bubble.text = "📖 ${a.title}\n\n${a.text}"
        talk("Вот что я нашёл. ${a.text}")
        moreUrl = a.url
        moreBtn.visibility = View.VISIBLE
        jump()
    }

    // ================= Приложения =================
    private fun openApp(nameRaw: String) {
        if (nameRaw.isEmpty()) { say("Какое приложение открыть?"); shake(); return }
        val name = appAliases[nameRaw] ?: nameRaw
        val app = findApp(name)
        val launch = app?.let { packageManager.getLaunchIntentForPackage(it.pkg) }
        if (app == null || launch == null) {
            say("Не нашёл «$nameRaw». Может, сказать «найди $nameRaw»?"); shake(); return
        }
        say("${happy.random()} Открываю ${app.label}"); jump()
        startActivity(launch)
    }

    private fun installedApps(): List<App> {
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(main, PackageManager.MATCH_ALL)
            .map { App(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }
    }

    private fun findApp(q: String): App? {
        val apps = installedApps()
        apps.firstOrNull { it.label.lowercase() == q }?.let { return it }
        apps.firstOrNull { it.label.lowercase().contains(q) || it.pkg.lowercase().contains(q) }?.let { return it }
        apps.firstOrNull { q.contains(it.label.lowercase()) && it.label.length > 2 }?.let { return it }
        return apps.map { it to distance(it.label.lowercase(), q) }
            .filter { it.second <= 2 }.minByOrNull { it.second }?.first
    }

    private fun distance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length)
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1,
                d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        return d[a.length][b.length]
    }

    // ================= Погода (Open-Meteo) =================
    private fun weather(cityRaw: String) {
        val prefs = getSharedPreferences("maksik", MODE_PRIVATE)
        val name = cityRaw.ifBlank { prefs.getString("city", "") ?: "" }
        if (name.isBlank()) { say("В каком городе? Скажи: погода в Москве"); shake(); return }

        pulse(Mood.THINK)
        say("Секунду, выгляну в окно...", false)
        thread {
            try {
                val g = geocode(name)
                if (g == null) {
                    runOnUiThread { say("Не нашёл город «$name»"); shake() }
                    return@thread
                }
                val cur = JSONObject(get(
                    "https://api.open-meteo.com/v1/forecast?latitude=${g.second}&longitude=${g.third}" +
                    "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m" +
                    "&wind_speed_unit=ms&timezone=auto"
                )).getJSONObject("current")
                val t = cur.getDouble("temperature_2m").roundToInt()
                val f = cur.getDouble("apparent_temperature").roundToInt()
                val w = cur.getDouble("wind_speed_10m").roundToInt()
                val desc = wmo(cur.getInt("weather_code"))
                val text = "${g.first}: сейчас ${sign(t)}°, ощущается как ${sign(f)}°, $desc, ветер $w м/с"
                prefs.edit().putString("city", g.first).apply()
                runOnUiThread { say(text); jump() }
            } catch (e: Exception) {
                runOnUiThread { say("Нет интернета, или сервис погоды спит"); shake() }
            }
        }
    }

    private fun sign(n: Int) = if (n > 0) "+$n" else "$n"

    private fun get(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "Maksik/1.1 (Android assistant app)")
        return try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
    }

    private fun geocode(name: String): Triple<String, Double, Double>? {
        val cands = listOf(name, name.dropLast(1), name.dropLast(2)).filter { it.length >= 3 }.distinct()
        for (n in cands) {
            val j = JSONObject(get(
                "https://geocoding-api.open-meteo.com/v1/search?name=" +
                URLEncoder.encode(n, "UTF-8") + "&count=1&language=ru&format=json"
            ))
            val r = j.optJSONArray("results")
            if (r != null && r.length() > 0) {
                val o = r.getJSONObject(0)
                return Triple(o.getString("name"), o.getDouble("latitude"), o.getDouble("longitude"))
            }
        }
        return null
    }

    private fun wmo(c: Int) = when (c) {
        0 -> "ясно"; 1 -> "преимущественно ясно"; 2 -> "переменная облачность"; 3 -> "пасмурно"
        45, 48 -> "туман"; 51, 53, 55 -> "морось"; 56, 57 -> "ледяная морось"
        61 -> "небольшой дождь"; 63 -> "дождь"; 65 -> "сильный дождь"; 66, 67 -> "ледяной дождь"
        71 -> "небольшой снег"; 73 -> "снег"; 75 -> "сильный снег"; 77 -> "снежная крупа"
        80, 81, 82 -> "ливень"; 85, 86 -> "снегопад"; 95 -> "гроза"; 96, 99 -> "гроза с градом"
        else -> "погода загадочная"
    }
}
