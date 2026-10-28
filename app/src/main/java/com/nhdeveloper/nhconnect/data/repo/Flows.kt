package com.nhdeveloper.nhconnect.data.repo

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Value listener as a Flow. The listener is removed when the collector stops. */
fun <T> valueFlow(db: FirebaseDatabase, path: String, parse: (DataSnapshot) -> T): Flow<T> = callbackFlow {
    val ref = db.getReference(path)
    val l = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) { trySend(parse(snapshot)) }
        override fun onCancelled(error: DatabaseError) { close(error.toException()) }
    }
    ref.addValueEventListener(l)
    awaitClose { ref.removeEventListener(l) }
}
