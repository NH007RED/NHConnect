package com.nhdeveloper.nhconnect.data.repo

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Online/offline + last seen, driven by RTDB's connection state and the app's foreground state. */
class PresenceManager(
    private val db: FirebaseDatabase,
    private val auth: AuthRepository,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope
) : DefaultLifecycleObserver {
    @Volatile private var connected = false
    @Volatile private var foreground = false
    @Volatile private var share = true
    @Volatile private var uid: String? = null

    fun start() {
        scope.launch(kotlinx.coroutines.Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this@PresenceManager)
        }
        db.getReference(".info/connected").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                connected = snapshot.getValue(Boolean::class.javaObjectType) == true
                update()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        scope.launch { settings.settings.collect { share = it.sharePresence; update() } }
        scope.launch {
            auth.state.collect { st ->
                uid = (st as? AuthState.Ready)?.uid
                update()
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) { foreground = true; update() }
    override fun onStop(owner: LifecycleOwner) { foreground = false; update() }

    private fun update() {
        val id = uid ?: return
        if (!connected) return
        val ref = db.getReference("presence/$id")
        if (foreground && share) {
            ref.onDisconnect().setValue(mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP))
            ref.setValue(mapOf("online" to true, "lastSeen" to ServerValue.TIMESTAMP))
        } else if (share) {
            ref.setValue(mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP))
        } else {
            // Privacy: hide presence completely (lastSeen 0 means "unknown").
            ref.setValue(mapOf("online" to false, "lastSeen" to 0))
        }
    }
}
