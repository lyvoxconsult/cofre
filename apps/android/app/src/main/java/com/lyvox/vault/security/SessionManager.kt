package com.lyvox.vault.security

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the in-memory session state.
 *
 * The master key (32 bytes derived from password via Argon2id) is held in memory
 * only while the vault is unlocked. It is never written to disk.
 *
 * When locked: key is set to null (GC eligible, but explicit zeroing would require
 * ByteArray manipulation — the GC will eventually reclaim it, and modern Android
 * memory isolation mitigates cross-process leakage).
 */
class SessionManager {

    private val masterKey = AtomicReference<ByteArray?>(null)
    private var lastActivityMs = System.currentTimeMillis()

    private val _isUnlockedFlow = MutableStateFlow(false)
    val isUnlockedFlow: StateFlow<Boolean> = _isUnlockedFlow.asStateFlow()

    /**
     * Whether the vault is currently unlocked.
     */
    val isUnlocked: Boolean
        get() = masterKey.get() != null

    /**
     * Unlocks the vault with the given master key.
     */
    fun unlock(key: ByteArray) {
        masterKey.set(key.copyOf())
        _isUnlockedFlow.value = true
        recordActivity()
    }

    /**
     * Locks the vault, clearing the master key from memory.
     * Zeroes the byte array before nulling the reference to prevent
     * key recovery from heap dumps.
     */
    fun lock() {
        masterKey.getAndSet(null)?.fill(0)
        _isUnlockedFlow.value = false
    }

    /**
     * Returns a copy of the current master key, or null if locked.
     */
    fun getKey(): ByteArray? {
        return masterKey.get()?.copyOf()
    }

    /**
     * Records user activity (for auto-lock).
     */
    fun recordActivity() {
        lastActivityMs = System.currentTimeMillis()
    }

    /**
     * Checks if the vault should auto-lock based on elapsed time.
     */
    fun shouldAutoLock(timeoutMinutes: Int): Boolean {
        if (timeoutMinutes <= 0) return false // disabled
        if (!isUnlocked) return false
        val elapsed = System.currentTimeMillis() - lastActivityMs
        return elapsed >= timeoutMinutes * 60 * 1000L
    }

    /**
     * Gets the last activity timestamp.
     */
    fun getLastActivityMs(): Long = lastActivityMs
}
