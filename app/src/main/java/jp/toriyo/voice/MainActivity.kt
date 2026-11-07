package jp.toriyo.voice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.*
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : Activity() {
    private lateinit var editor: EditText
    private lateinit var status: TextView
    private var recognizer: SpeechRecognizer? = null
    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null
    private val requestCode = 701
    private val dateFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.JAPAN)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "この端末では音声認識サービスが利用できません。Google音声入力の設定を確認してください。"
        }
    }

    private fun buildUi() {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 24)
            setBackgroundColor(0xFFF3F6FA.toInt())
        }
        val title = TextView(this).apply {
            text = "トリヨ音声入力"
            textSize = 23f
            setTextColor(0xFF173B63.toInt())
        }
        status = TextView(this).apply {
            text = "マイクを許可して「音声入力開始」を押してください。"
            textSize = 14f
            setPadding(0, 12, 0, 12)
        }
        editor = EditText(this).apply {
            hint = "ここに音声認識した文章が表示されます。直接編集もできます。"
            textSize = 17f
            gravity = android.view.Gravity.TOP
            minLines = 12
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        outer.addView(title)
        outer.addView(status)
        outer.addView(editor, LinearLayout.LayoutParams(-1, 0, 1f))
        addButton(outer, "音声入力開始", true) { startSpeech() }
        addButton(outer, "音声入力停止", false) { recognizer?.stopListening(); status.text = "音声入力を停止しました。" }
        addButton(outer, "録音開始", true) { startRecording() }
        addButton(outer, "録音停止・保存", false) { stopRecording() }
        addButton(outer, "TXTで保存", false) { saveText(false) }
        addButton(outer, "Wordで保存（DOCX）", false) { saveText(true) }
        addButton(outer, "ChatGPTで文章を修正", true) { sendToChatGPT() }
        setContentView(ScrollView(this).apply { addView(outer) })
    }

    private fun addButton(parent: LinearLayout, label: String, primary: Boolean, action: () -> Unit) {
        val b = Button(this).apply {
            text = label
            setOnClickListener { action() }
        }
        parent.addView(b, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun ensureMicPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) return true
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), requestCode)
        status.text = "マイクの使用を許可してください。許可後、もう一度ボタンを押してください。"
        return false
    }

    private fun startSpeech() {
        if (!ensureMicPermission()) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "音声認識サービスがありません。Googleアプリと音声入力サービスを確認してください。"
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status.text = "聞き取り中です。話してください。" }
            override fun onBeginningOfSpeech() { status.text = "音声を認識しています…" }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "認識処理中…" }
            override fun onError(error: Int) {
                status.text = "音声認識エラー ($error)。通信状態とGoogle音声入力の設定を確認してください。"
            }
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                list?.firstOrNull()?.let { appendText(it) }
                status.text = "認識しました。続ける場合は再度「音声入力開始」を押してください。"
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                list?.firstOrNull()?.let { status.text = "認識中：$it" }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
    }

    private fun appendText(s: String) {
        val current = editor.text.toString()
        editor.append((if (current.isNotBlank() && !current.endsWith("\n")) "\n" else "") + s)
    }

    private fun startRecording() {
        if (!ensureMicPermission()) return
        try {
            val dir = getExternalFilesDir(null) ?: filesDir
            audioFile = File(dir, "toriyo_audio_${dateFmt.format(Date())}.m4a")
            recorder = MediaRecorder(this).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }
            status.text = "録音中です。終了したら「録音停止・保存」を押してください。"
        } catch (e: Exception) {
            status.text = "録音を開始できませんでした：${e.message}"
            recorder?.release(); recorder = null
        }
    }

    private fun stopRecording() {
        try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            status.text = "音声を保存しました：${audioFile?.name}"
            audioFile?.let { f ->
                status.text = "録音をアプリ内に保存しました：${f.name}"
            }
        } catch (e: Exception) {
            status.text = "録音停止に失敗しました。録音を最初からやり直してください。"
            recorder?.release(); recorder = null
        }
    }

    private fun saveText(docx: Boolean) {
        val text = editor.text.toString()
        if (text.isBlank()) { status.text = "保存する文章がありません。"; return }
        val dir = getExternalFilesDir(null) ?: filesDir
        val base = "toriyo_${dateFmt.format(Date())}"
        try {
            if (!docx) {
                val f = File(dir, "$base.txt")
                f.writeText(text, Charsets.UTF_8)
                status.text = "TXTを保存しました：${f.name}"
                shareFile(f, "text/plain")
            } else {
                val f = File(dir, "$base.docx")
                writeDocx(f, text)
                status.text = "Wordファイルを保存しました：${f.name}"
                shareFile(f, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            }
        } catch (e: Exception) {
            status.text = "保存できませんでした：${e.message}"
        }
    }

    private fun writeDocx(file: File, text: String) {
        val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val paragraphs = escaped.split("\n").joinToString("") { "<w:p><w:r><w:t xml:space=\"preserve\">$it</w:t></w:r></w:p>" }
        val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$paragraphs<w:sectPr/></w:body></w:document>"""
        val types = """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""
        val rels = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            entry("[Content_Types].xml", types)
            entry("_rels/.rels", rels)
            entry("word/document.xml", document)
        }
    }

    private fun shareFile(file: File, mime: String) {
        // File sharing is enabled through Android's ACTION_SEND only when the app is configured with a FileProvider.
        // The saved file remains available in the app's private external-files folder.
        status.append("\n保存先：アプリのファイル領域")
    }

    private fun sendToChatGPT() {
        val body = editor.text.toString()
        if (body.isBlank()) { status.text = "修正する文章がありません。"; return }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "次の文章を、意味を変えずに誤字脱字・句読点・文法を修正し、自然で読みやすい日本語に整えてください。修正後の文章だけを出力してください。\n\n$body")
        }
        startActivity(Intent.createChooser(intent, "文章をChatGPTなどに送る"))
    }

    override fun onDestroy() {
        recognizer?.destroy()
        recorder?.release()
        super.onDestroy()
    }
}
