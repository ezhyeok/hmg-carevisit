package dev.carevisit.app

import dev.carevisit.core.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class HttpFamilyLinkTest {
    private val token = "ab".repeat(16)

    private fun message(): ApprovedMessage {
        val machine = VisitMachine(Archive(consent = Consent(1)), 1)
        machine.handle(Event.Vehicle(Gear.P, 0.0, false, false), 1, 0)
        return (machine.handle(Event.FamilyConsent(true, token), 1, 0)
            .single { it is Effect.Send } as Effect.Send).message
    }

    @Test fun approvedDtoHasOnlyIdKindTextAndFixedContentLength() = runBlocking {
        HttpStub { Response(body = "{\"ok\":true}") }.use { server ->
            HttpFamilyLink().send(Settings(relayUrl = server.baseUrl, familyToken = token), message())
            val request = server.takeRequest()
            assertEquals("/api/message", request.target)
            assertEquals(setOf("id", "kind", "text"), JSONObject(request.body).keySet())
            assertEquals("Bearer $token", request.headers["authorization"])
            assertEquals(request.body.toByteArray(Charsets.UTF_8).size.toString(), request.headers["content-length"])
            assertNull(request.headers["transfer-encoding"])
        }
    }

    @Test fun redirectIsNotFollowedWithBearerToken() = runBlocking {
        val leaked = AtomicInteger()
        HttpStub { request ->
            if (request.target == "/api/message") {
                Response(status = 302, headers = mapOf("Location" to "/other"))
            } else {
                leaked.incrementAndGet()
                Response()
            }
        }.use { server ->
            try {
                HttpFamilyLink().send(Settings(relayUrl = server.baseUrl, familyToken = token), message())
                fail("redirect must fail")
            } catch (_: IllegalStateException) { }
            assertEquals(0, leaked.get())
        }
    }

    @Test fun pollingAdvancesCursorAndAllowsOnlyProtocolResponseShape() = runBlocking {
        HttpStub { request ->
            Response(body = if (request.target.endsWith("after=0")) {
                "{\"replies\":[{\"id\":\"1\",\"text\":\"나왔어\"}]}"
            } else {
                "{\"replies\":[]}"
            })
        }.use { server ->
            val client = HttpFamilyLink()
            val settings = Settings(relayUrl = server.baseUrl, familyToken = token)
            assertEquals(listOf("1" to "나왔어"), client.poll(settings))
            assertTrue(client.poll(settings).isEmpty())
            val requests = listOf(server.takeRequest(), server.takeRequest())
            assertTrue(requests.all { it.target.startsWith("/api/replies?") })
            assertEquals(listOf("after=0", "after=1"), requests.map { it.target.substringAfter('?') })
        }
    }

    @Test fun credentialsInBaseUrlRejectedBeforeNetwork() = runBlocking {
        try {
            HttpFamilyLink().send(Settings(relayUrl = "http://user:password@127.0.0.1:1", familyToken = token), message())
            fail("userinfo must not be allowed")
        } catch (_: IllegalArgumentException) { }
    }

    private data class Request(val target: String, val headers: Map<String, String>, val body: String)
    private data class Response(val status: Int = 200, val headers: Map<String, String> = emptyMap(), val body: String = "{}")

    /** One request per connection, using only APIs present on Android's unit-test compile classpath. */
    private class HttpStub(private val respond: (Request) -> Response) : Closeable {
        private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val baseUrl = "http://127.0.0.1:${server.localPort}"
        private val requests = LinkedBlockingQueue<Request>()
        private val failure = AtomicReference<Throwable?>()
        @Volatile private var closed = false
        @Volatile private var activeSocket: Socket? = null
        private val worker = Thread({
            try {
                while (!closed) {
                    server.accept().use { socket ->
                        activeSocket = socket
                        if (!closed) serve(socket)
                    }
                    activeSocket = null
                }
            } catch (error: Throwable) {
                if (!closed) failure.set(error)
            } finally {
                activeSocket = null
            }
        }, "family-http-test").apply { isDaemon = true; start() }

        fun takeRequest(): Request = requests.poll(3, TimeUnit.SECONDS)
            ?: throw AssertionError("HTTP request did not arrive")

        private fun serve(socket: Socket) {
            socket.soTimeout = 3_000
            val input = BufferedInputStream(socket.getInputStream())
            val requestLine = readLine(input).split(' ')
            require(requestLine.size == 3) { "Invalid request line" }
            val headers = linkedMapOf<String, String>()
            while (true) {
                val line = readLine(input)
                if (line.isEmpty()) break
                require(headers.size < 100 && ':' in line) { "Invalid headers" }
                headers[line.substringBefore(':').trim().lowercase(Locale.ROOT)] = line.substringAfter(':').trim()
            }
            require("transfer-encoding" !in headers) { "This stub expects Content-Length, not chunked requests" }
            val length = headers["content-length"]?.toInt() ?: 0
            require(length in 0..32_768) { "Request body too large" }
            val body = ByteArray(length)
            var offset = 0
            while (offset < length) {
                val count = input.read(body, offset, length - offset)
                if (count < 0) throw EOFException("Incomplete request body")
                offset += count
            }
            val request = Request(requestLine[1], headers, body.toString(Charsets.UTF_8))
            requests.add(request)
            val response = respond(request)
            val payload = response.body.toByteArray(Charsets.UTF_8)
            val head = buildString {
                append("HTTP/1.1 ${response.status} ${if (response.status == 302) "Found" else "OK"}\r\n")
                append("Content-Type: application/json; charset=utf-8\r\n")
                append("Content-Length: ${payload.size}\r\nConnection: close\r\n")
                response.headers.forEach { (name, value) -> append("$name: $value\r\n") }
                append("\r\n")
            }
            socket.getOutputStream().apply {
                write(head.toByteArray(Charsets.US_ASCII) + payload)
                flush()
            }
        }

        private fun readLine(input: BufferedInputStream): String {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val byte = input.read()
                if (byte < 0) throw EOFException("Incomplete HTTP headers")
                if (byte == '\n'.code) return bytes.toString("US-ASCII").removeSuffix("\r")
                require(bytes.size() < 8_192) { "HTTP header line too long" }
                bytes.write(byte)
            }
        }

        override fun close() {
            closed = true
            server.close()
            activeSocket?.close()
            worker.join(5_000)
            check(!worker.isAlive) { "HTTP stub did not stop" }
            failure.get()?.let { throw AssertionError("HTTP stub failed").apply { initCause(it) } }
        }
    }
}
