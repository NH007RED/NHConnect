package com.nhdeveloper.nhconnect.p2p

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.nhdeveloper.nhconnect.BuildConfig
import com.nhdeveloper.nhconnect.data.local.MessageDao
import com.nhdeveloper.nhconnect.domain.ChatIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

enum class Phase { WAITING, CONNECTING, TRANSFERRING, DONE, FAILED }

data class TransferState(
    val phase: Phase,
    val done: Long = 0L,
    val total: Long = 0L,
    val note: String? = null,
    val savedUri: Uri? = null,
    val outgoing: Boolean = false
) {
    val active: Boolean get() = phase == Phase.WAITING || phase == Phase.CONNECTING || phase == Phase.TRANSFERRING
}

/**
 * Phone-to-phone file transfer over a WebRTC data channel. No file ever touches a server.
 * Firebase Realtime Database is used only for signaling (tiny SDP/ICE messages):
 *
 *   receiver --(p2pInbox/{sender})--> sender app   "please send me message X, session S"
 *   sender   --(p2p/{chat}/{S}/offer)--> receiver
 *   receiver --(p2p/{chat}/{S}/answer)--> sender
 *   both     --(p2p/{chat}/{S}/ice/{s|r})--> each other
 *   sender streams 16 KB chunks over the data channel, receiver writes them to Downloads/NH Connect.
 *
 * The sender's app must be running (it listens for requests as soon as the app is open).
 */
class P2PManager(
    private val app: Application,
    private val db: FirebaseDatabase,
    private val dao: MessageDao,
    private val scope: CoroutineScope
) {
    companion object {
        const val CHUNK = 16 * 1024
        const val MAX_BYTES = 200L * 1024 * 1024
        private const val MAX_BUFFERED = 4L * 1024 * 1024
        private const val REQUEST_TTL_MS = 5 * 60_000L
        private const val WAIT_FOR_SENDER_MS = 30_000L
        private const val CONNECT_TIMEOUT_MS = 45_000L

        fun safeName(name: String): String {
            val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().trimStart('.').take(100)
            return cleaned.ifBlank { "file" }
        }
    }

    private val _transfers = MutableStateFlow<Map<String, TransferState>>(emptyMap())
    val transfers: StateFlow<Map<String, TransferState>> = _transfers

    private fun setState(messageId: String, s: TransferState) { _transfers.update { it + (messageId to s) } }
    private fun phaseOf(messageId: String): Phase? = _transfers.value[messageId]?.phase

    private val factory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(app).createInitializationOptions())
        PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    private fun iceServers(): List<PeerConnection.IceServer> {
        val list = mutableListOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
        if (BuildConfig.TURN_URL.isNotBlank()) {
            list.add(
                PeerConnection.IceServer.builder(BuildConfig.TURN_URL)
                    .setUsername(BuildConfig.TURN_USER).setPassword(BuildConfig.TURN_PASS).createIceServer()
            )
        }
        return list
    }

    private open class SdpObs : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }

    private class Session(val chatId: String, val sid: String, val sigRef: DatabaseReference) {
        var pc: PeerConnection? = null
        var dc: DataChannel? = null
        val listeners = mutableListOf<Pair<Query, Any>>()
        @Volatile var closed = false
    }

    private fun close(s: Session, removeSignaling: Boolean) {
        if (s.closed) return
        s.closed = true
        s.listeners.forEach { (q, l) ->
            when (l) {
                is ValueEventListener -> q.removeEventListener(l)
                is ChildEventListener -> q.removeEventListener(l)
            }
        }
        runCatching { s.dc?.close() }
        runCatching { s.pc?.close() }
        runCatching { s.pc?.dispose() }
        if (removeSignaling) runCatching { s.sigRef.removeValue() }
    }

    private inner class Obs(
        val s: Session, val role: String,
        val onChannel: (DataChannel) -> Unit,
        val onIce: (PeerConnection.IceConnectionState) -> Unit
    ) : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) { if (state != null) onIce(state) }
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidate(candidate: IceCandidate?) {
            if (candidate == null) return
            s.sigRef.child("ice").child(role).push().setValue(
                mapOf("c" to candidate.sdp, "m" to candidate.sdpMid, "i" to candidate.sdpMLineIndex)
            )
        }
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(channel: DataChannel?) { if (channel != null) onChannel(channel) }
        override fun onRenegotiationNeeded() {}
    }

    private fun newPeer(obs: PeerConnection.Observer): PeerConnection? {
        val cfg = PeerConnection.RTCConfiguration(iceServers()).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }
        return factory.createPeerConnection(cfg, obs)
    }

    /** Applies the other side's ICE candidates (only after the remote description is set). */
    private fun listenIce(s: Session, remoteRole: String, pc: PeerConnection) {
        val q = s.sigRef.child("ice").child(remoteRole)
        val l = object : ChildEventListener {
            override fun onChildAdded(snap: DataSnapshot, prev: String?) {
                val c = snap.child("c").getValue(String::class.java) ?: return
                val m = snap.child("m").getValue(String::class.java)
                val i = (snap.child("i").getValue(Long::class.javaObjectType) ?: 0L).toInt()
                if (!s.closed) pc.addIceCandidate(IceCandidate(m, i, c))
            }
            override fun onChildChanged(snap: DataSnapshot, prev: String?) {}
            override fun onChildRemoved(snap: DataSnapshot) {}
            override fun onChildMoved(snap: DataSnapshot, prev: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        q.addChildEventListener(l)
        s.listeners.add(q to l)
    }

    private fun text(b: DataChannel.Buffer): String {
        val arr = ByteArray(b.data.remaining())
        b.data.get(arr)
        return String(arr, Charsets.UTF_8)
    }

    private fun textBuffer(t: String) = DataChannel.Buffer(ByteBuffer.wrap(t.toByteArray(Charsets.UTF_8)), false)

    private fun fail(s: Session, messageId: String, note: String, outgoing: Boolean) {
        val cur = _transfers.value[messageId]
        if (cur?.phase == Phase.DONE) return
        setState(messageId, TransferState(Phase.FAILED, cur?.done ?: 0, cur?.total ?: 0, note, outgoing = outgoing))
        close(s, removeSignaling = true)
    }

    // ------------------------------------------------------------------ sender side

    private var inboxUid: String? = null
    private var inboxRef: DatabaseReference? = null
    private var inboxListener: ChildEventListener? = null

    /** Called when a user is signed in: from now on this phone answers download requests for files it offered. */
    fun startInbox(uid: String) {
        if (inboxUid == uid) return
        stopInbox()
        inboxUid = uid
        val ref = db.getReference("p2pInbox/$uid")
        val l = object : ChildEventListener {
            override fun onChildAdded(snap: DataSnapshot, prev: String?) { handleRequest(uid, snap) }
            override fun onChildChanged(snap: DataSnapshot, prev: String?) {}
            override fun onChildRemoved(snap: DataSnapshot) {}
            override fun onChildMoved(snap: DataSnapshot, prev: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addChildEventListener(l)
        inboxRef = ref
        inboxListener = l
    }

    fun stopInbox() {
        inboxListener?.let { inboxRef?.removeEventListener(it) }
        inboxRef = null; inboxListener = null; inboxUid = null
    }

    private fun handleRequest(me: String, snap: DataSnapshot) {
        val chatId = snap.child("chatId").getValue(String::class.java)
        val messageId = snap.child("messageId").getValue(String::class.java)
        val sid = snap.child("sid").getValue(String::class.java)
        val by = snap.child("by").getValue(String::class.java)
        val at = snap.child("at").getValue(Long::class.javaObjectType) ?: 0L
        snap.ref.removeValue()
        if (chatId == null || messageId == null || sid == null || by == null) return
        if (at > 0 && System.currentTimeMillis() - at > REQUEST_TTL_MS) return // stale request
        if (ChatIds.otherUid(chatId, me) != by) return
        scope.launch(Dispatchers.IO) {
            val msg = dao.get(messageId)
            val share = dao.getShare(messageId)
            val sigRef = db.getReference("p2p/$chatId/$sid")
            if (msg == null || share == null || msg.senderId != me || msg.messageType != "file" || msg.chatId != chatId) {
                sigRef.child("error").setValue("unavailable"); return@launch
            }
            val uri = Uri.parse(share.uri)
            val readable = runCatching { app.contentResolver.openInputStream(uri)?.use { true } ?: false }.getOrDefault(false)
            if (!readable) { sigRef.child("error").setValue("unavailable"); return@launch }
            serve(chatId, sid, messageId, uri, msg.fileSize)
        }
    }

    private fun serve(chatId: String, sid: String, messageId: String, uri: Uri, size: Long) {
        val s = Session(chatId, sid, db.getReference("p2p/$chatId/$sid"))
        setState(messageId, TransferState(Phase.CONNECTING, 0, size, outgoing = true))
        val obs = Obs(s, "s", onChannel = {}, onIce = { st ->
            if ((st == PeerConnection.IceConnectionState.FAILED || st == PeerConnection.IceConnectionState.CLOSED) && phaseOf(messageId) != Phase.DONE) {
                fail(s, messageId, "Connection lost", true)
            }
        })
        val pc = newPeer(obs)
        if (pc == null) { fail(s, messageId, "Could not start the connection", true); return }
        s.pc = pc
        val dc = pc.createDataChannel("file", DataChannel.Init())
        if (dc == null) { fail(s, messageId, "Could not open a data channel", true); return }
        s.dc = dc
        val finished = AtomicBoolean(false)
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {
                if (dc.state() == DataChannel.State.OPEN) scope.launch(Dispatchers.IO) { streamFile(s, dc, messageId, uri, size, finished) }
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                if (!buffer.binary && text(buffer) == "OK" && finished.compareAndSet(false, true)) {
                    setState(messageId, TransferState(Phase.DONE, size, size, outgoing = true))
                    scope.launch { delay(800); close(s, true) }
                }
            }
        })
        scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (phaseOf(messageId) == Phase.CONNECTING) fail(s, messageId, "Could not connect to the other phone", true)
        }
        val answered = AtomicBoolean(false)
        val ansRef = s.sigRef.child("answer")
        val al = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists() || !answered.compareAndSet(false, true)) return
                val t = snapshot.child("t").getValue(String::class.java) ?: return
                val sdp = snapshot.child("s").getValue(String::class.java) ?: return
                pc.setRemoteDescription(object : SdpObs() {
                    override fun onSetSuccess() { listenIce(s, "r", pc) }
                }, SessionDescription(SessionDescription.Type.fromCanonicalForm(t), sdp))
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ansRef.addValueEventListener(al)
        s.listeners.add(ansRef to al)
        pc.createOffer(object : SdpObs() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp == null) return
                pc.setLocalDescription(SdpObs(), sdp)
                s.sigRef.child("offer").setValue(mapOf("t" to sdp.type.canonicalForm(), "s" to sdp.description))
            }
            override fun onCreateFailure(error: String?) { fail(s, messageId, "Could not start the transfer", true) }
        }, MediaConstraints())
    }

    private suspend fun streamFile(s: Session, dc: DataChannel, messageId: String, uri: Uri, size: Long, finished: AtomicBoolean) {
        try {
            dc.send(textBuffer("H|$size"))
            setState(messageId, TransferState(Phase.TRANSFERRING, 0, size, outgoing = true))
            var sent = 0L
            var lastUi = 0L
            val input = app.contentResolver.openInputStream(uri) ?: throw IOException("File not readable")
            input.use { stream ->
                val buf = ByteArray(CHUNK)
                while (true) {
                    val n = stream.read(buf)
                    if (n <= 0) break
                    while (dc.bufferedAmount() > MAX_BUFFERED) {
                        if (s.closed) return
                        delay(15)
                    }
                    if (!dc.send(DataChannel.Buffer(ByteBuffer.wrap(buf.copyOf(n)), true))) throw IOException("Send failed")
                    sent += n
                    val now = System.currentTimeMillis()
                    if (now - lastUi > 150) { lastUi = now; setState(messageId, TransferState(Phase.TRANSFERRING, sent, size, outgoing = true)) }
                }
            }
            dc.send(textBuffer("E"))
            setState(messageId, TransferState(Phase.TRANSFERRING, size, size, outgoing = true))
            delay(30_000)
            if (finished.compareAndSet(false, true)) { // no explicit OK, but everything was written to the channel
                setState(messageId, TransferState(Phase.DONE, size, size, outgoing = true))
                close(s, true)
            }
        } catch (e: Exception) {
            fail(s, messageId, e.message ?: "Transfer failed", true)
        }
    }

    // ------------------------------------------------------------------ receiver side

    private class Sink(val out: OutputStream, val uri: Uri?, val file: File?)

    /** Receiver taps "Download": ask the sender's phone to connect and stream the file. */
    fun download(me: String, chatId: String, messageId: String, senderUid: String, fileName: String, fileSize: Long, mime: String?) {
        if (_transfers.value[messageId]?.active == true) return
        val sid = db.getReference("p2p/$chatId").push().key ?: return
        val s = Session(chatId, sid, db.getReference("p2p/$chatId/$sid"))
        if (fileSize <= 0 || fileSize > MAX_BYTES) {
            setState(messageId, TransferState(Phase.FAILED, note = "File is too large (max 200 MB)")); return
        }
        setState(messageId, TransferState(Phase.WAITING, 0, fileSize))
        var sink: Sink? = null
        var received = 0L
        var lastUi = 0L
        val finished = AtomicBoolean(false)

        fun cleanupPartial() {
            runCatching { sink?.out?.close() }
            sink?.uri?.let { runCatching { app.contentResolver.delete(it, null, null) } }
            sink?.file?.let { runCatching { it.delete() } }
            sink = null
        }
        fun failHere(note: String) { cleanupPartial(); fail(s, messageId, note, false) }

        fun openSink(): Sink {
            val name = safeName(fileName)
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    if (!mime.isNullOrBlank()) put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/NH Connect")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Cannot create file")
                val out = app.contentResolver.openOutputStream(uri) ?: throw IOException("Cannot open file")
                return Sink(out, uri, null)
            }
            val dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: app.filesDir
            var f = File(dir, name)
            var n = 1
            while (f.exists()) f = File(dir, "${n++}_$name")
            return Sink(FileOutputStream(f), null, f)
        }

        fun finish(dc: DataChannel) {
            val sk = sink ?: return failHere("No data received")
            runCatching { sk.out.flush(); sk.out.close() }
            if (received != fileSize) return failHere("Incomplete file")
            var saved: Uri? = null
            if (sk.uri != null) {
                val v = ContentValues().apply { put("is_pending", 0) }
                app.contentResolver.update(sk.uri, v, null, null)
                saved = sk.uri
            }
            sink = null
            if (finished.compareAndSet(false, true)) {
                setState(messageId, TransferState(Phase.DONE, fileSize, fileSize, savedUri = saved, note = sk.file?.absolutePath))
                dc.send(textBuffer("OK"))
                scope.launch { delay(1500); close(s, true) }
            }
        }

        val obs = Obs(s, "r", onChannel = { dc ->
            s.dc = dc
            dc.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(previousAmount: Long) {}
                override fun onStateChange() {}
                override fun onMessage(buffer: DataChannel.Buffer) {
                    try {
                        if (!buffer.binary) {
                            val t = text(buffer)
                            when {
                                t.startsWith("H|") -> {
                                    sink = openSink()
                                    setState(messageId, TransferState(Phase.TRANSFERRING, 0, fileSize))
                                }
                                t == "E" -> finish(dc)
                            }
                        } else {
                            val sk = sink ?: return
                            val arr = ByteArray(buffer.data.remaining())
                            buffer.data.get(arr)
                            received += arr.size
                            if (received > fileSize) return failHere("Received more data than announced")
                            sk.out.write(arr)
                            val now = System.currentTimeMillis()
                            if (now - lastUi > 150) { lastUi = now; setState(messageId, TransferState(Phase.TRANSFERRING, received, fileSize)) }
                        }
                    } catch (e: Exception) {
                        failHere(e.message ?: "Could not save the file")
                    }
                }
            })
        }, onIce = { st ->
            if ((st == PeerConnection.IceConnectionState.FAILED || st == PeerConnection.IceConnectionState.CLOSED) && phaseOf(messageId) != Phase.DONE) {
                failHere("Connection lost")
            }
        })
        val pc = newPeer(obs)
        if (pc == null) { fail(s, messageId, "Could not start the connection", false); return }
        s.pc = pc

        val errRef = s.sigRef.child("error")
        val el = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) failHere("The sender no longer has this file")
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        errRef.addValueEventListener(el)
        s.listeners.add(errRef to el)

        val gotOffer = AtomicBoolean(false)
        val offRef = s.sigRef.child("offer")
        val ol = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists() || !gotOffer.compareAndSet(false, true)) return
                val t = snapshot.child("t").getValue(String::class.java) ?: return
                val sdp = snapshot.child("s").getValue(String::class.java) ?: return
                setState(messageId, TransferState(Phase.CONNECTING, 0, fileSize))
                pc.setRemoteDescription(object : SdpObs() {
                    override fun onSetSuccess() {
                        listenIce(s, "s", pc)
                        pc.createAnswer(object : SdpObs() {
                            override fun onCreateSuccess(answer: SessionDescription?) {
                                if (answer == null) return
                                pc.setLocalDescription(SdpObs(), answer)
                                s.sigRef.child("answer").setValue(mapOf("t" to answer.type.canonicalForm(), "s" to answer.description))
                            }
                            override fun onCreateFailure(error: String?) { failHere("Could not answer the connection") }
                        }, MediaConstraints())
                    }
                }, SessionDescription(SessionDescription.Type.fromCanonicalForm(t), sdp))
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        offRef.addValueEventListener(ol)
        s.listeners.add(offRef to ol)

        // Ask the sender's phone to start serving.
        db.getReference("p2pInbox/$senderUid").push().setValue(
            mapOf("chatId" to chatId, "messageId" to messageId, "sid" to sid, "by" to me, "at" to ServerValue.TIMESTAMP)
        ).addOnFailureListener { failHere("Could not contact the sender") }

        scope.launch {
            delay(WAIT_FOR_SENDER_MS)
            if (phaseOf(messageId) == Phase.WAITING) failHere("The sender is offline. Try again when they have the app open.")
        }
        scope.launch {
            delay(WAIT_FOR_SENDER_MS + CONNECT_TIMEOUT_MS)
            if (phaseOf(messageId) == Phase.CONNECTING) failHere("Could not connect to the sender's phone.")
        }
    }
}
