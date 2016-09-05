package com.github.kittinunf.redux.devTools.core

import com.github.kittinunf.redux.devTools.socket.SocketClient
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.*

/**
 * Created by kittinunf on 8/26/16.
 */

data class InstrumentOption(val host: String, val port: Int, val name: String, val maxAge: Int)

fun defaultOption() = InstrumentOption("localhost", 8989, UUID.randomUUID().toString(), 30)

class Instrument<S>(options: InstrumentOption, initialState: S) {

    var isMonitored = false

    private var started = false

    private var currentStateIndex = -1

    private val client: SocketClient

    private val stateTimeLines = mutableListOf<S>()

    private val name: String

    var onMessageReceived: ((S) -> Unit)? = null

    //app's view state
    var state: S
        private set
        get() {
            return stateTimeLines[currentStateIndex]
        }

    init {
        client = SocketClient(options.host, options.port)
        state = initialState
        name = options.name
    }

    fun start() {
        if (started) return
        started = true
        isMonitored = true

        client.messages.subscribe { handleMessageReceived(it) }
        client.connectBlocking()

        val message = InstrumentAction.Init(name).toJsonObject().toString()
        client.send(message)
    }

    fun handleStateChangeFromAction(state: S, action: Any) {
        stateTimeLines.add(state)
        currentStateIndex = stateTimeLines.lastIndex
        val data = InstrumentAction.State(state.toString() to action.javaClass.simpleName)
        client.send(data.toJsonObject().toString())
    }

    fun stop() {
        started = false
        isMonitored = false
        client.closeBlocking()
    }

    private fun handleMessageReceived(s: String) {
        val json = JsonParser().parse(s).asJsonObject
        val index = InstrumentAction.JumpToState(json).payload
        currentStateIndex = index
        onMessageReceived?.invoke(state)
    }

}
