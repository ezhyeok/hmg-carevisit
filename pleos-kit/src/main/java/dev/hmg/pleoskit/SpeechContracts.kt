package dev.hmg.pleoskit

/** 말하기. 쓰는 쪽(앱의 VoiceAgent)이 가짜로 바꿔 시험할 수 있게 [TtsKit]에서 뽑은 모양 */
interface SpeechOutput {
    suspend fun connect()

    /** 끝까지 말했으면 true, 중간에 멈췄으면 false. 말하지 못하면 [PleosSdkException] */
    suspend fun speak(text: String): Boolean

    /** 말하는 중이면 멈춘다 */
    fun stop()
}

/** 듣기. [SttKit]에서 뽑은 모양 */
interface SpeechInput {
    suspend fun connect()

    /**
     * 한 번 듣고 인식한 문장을 돌려준다. 아무 말도 없었으면 null.
     * [timeoutMs]는 마이크가 열린 뒤부터 듣는 시간이다. 마이크가 열리면 [onMicOpen]을 부른다 ('듣는 중' 표시는 이때 켠다):
     * STT는 요청을 받고 몇 초 뒤에야 녹음을 시작할 수 있다.
     */
    suspend fun listen(timeoutMs: Long, onMicOpen: () -> Unit = {}): String?

    /** 듣는 중이면 멈춘다 */
    fun stop()
}


interface SpeechFocus {
    fun acquire(onLoss: ()->Unit): Boolean
    fun release()
}
object NoSpeechFocus : SpeechFocus {
    override fun acquire(onLoss: ()->Unit)=true
    override fun release()=Unit
}
