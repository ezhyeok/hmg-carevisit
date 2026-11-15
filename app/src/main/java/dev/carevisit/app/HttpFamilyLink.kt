package dev.carevisit.app

import dev.carevisit.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

/** Only explicit narrow DTOs become HTTP JSON. No archive/utterance serializer is reachable here. */
class HttpFamilyLink : FamilyConnection {
    private var cursor=0L
    private var lastToken=""
    private suspend fun request(settings: Settings,path: String,method: String="GET",body: JSONObject?=null): JSONObject = withContext(Dispatchers.IO) {
        require(settings.familyToken.matches(Regex("[0-9a-f]{32}")))
        val base=URI(settings.relayUrl)
        require(base.scheme in setOf("http","https") && base.host!=null && base.rawUserInfo==null && base.rawQuery==null && base.rawFragment==null && base.path.orEmpty() in setOf("","/"))
        val connection=URI(settings.relayUrl.trimEnd('/')+path).toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects=false
            connection.connectTimeout=4_000; connection.readTimeout=4_000; connection.requestMethod=method
            connection.setRequestProperty("Authorization","Bearer ${settings.familyToken}")
            connection.setRequestProperty("Content-Type","application/json")
            if(body!=null) { val payload=body.toString().toByteArray(Charsets.UTF_8);connection.doOutput=true;connection.setFixedLengthStreamingMode(payload.size);connection.outputStream.use { it.write(payload) } }
            check(connection.responseCode==200) { "family relay response ${connection.responseCode}" }
            val bytes=connection.inputStream.use { it.readBytesLimited(128*1024) }
            JSONObject(bytes.toString(Charsets.UTF_8))
        } finally {connection.disconnect()}
    }
    override suspend fun send(settings: Settings,message: ApprovedMessage) { request(settings,"/api/message","POST",JSONObject().put("id",message.id).put("kind",message.kind).put("text",message.text)) }
    override suspend fun revoke(settings: Settings) { if(settings.familyToken.isNotEmpty()) request(settings,"/api/session","DELETE") }
    override suspend fun poll(settings: Settings): List<Pair<String,String>> {
        if(lastToken!=settings.familyToken) {cursor=0;lastToken=settings.familyToken}
        val array=request(settings,"/api/replies?after=$cursor").getJSONArray("replies")
        return (0 until array.length()).map { val r=array.getJSONObject(it); val id=r.getString("id"); cursor=maxOf(cursor,id.toLong()); id to r.getString("text") }
    }
}
private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output=java.io.ByteArrayOutputStream(); val buffer=ByteArray(4096)
    while(true) {val n=read(buffer); if(n<0) break; check(output.size()+n<=limit); output.write(buffer,0,n)}
    return output.toByteArray()
}
