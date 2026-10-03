package com.example.cardwheel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.concurrent.Executors

/** Retains an in-flight save across rotation, avoiding duplicate inserts. */
class SaveCardViewModel : ViewModel() {
    val state = MutableLiveData(0)
    fun save(action: () -> Unit) {
        if (state.value == 1 || state.value == 2) return
        state.value = 1
        worker.execute {
            try { action(); state.postValue(2) }
            catch (error: Exception) { state.postValue(3) }
        }
    }
    companion object { private val worker = Executors.newSingleThreadExecutor() }
}
