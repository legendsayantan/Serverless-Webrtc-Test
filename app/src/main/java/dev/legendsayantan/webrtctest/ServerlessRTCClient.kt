package dev.legendsayantan.webrtctest

import android.content.Context
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.*
import java.nio.ByteBuffer
import java.nio.charset.Charset

/**
 * This class handles all around WebRTC peer connections.
 */
class ServerlessRTCClient(val context: Context, val listener: IStateChangeListener) {

    lateinit var pc: PeerConnection
    private var pcInitialized: Boolean = false

    var channel: DataChannel? = null

    /**
     * List of servers that will be used to establish the direct connection, STUN/TURN should be supported.
     */
    val iceServers = arrayListOf(PeerConnection.IceServer("stun:stun.l.google.com:19302"))

    enum class State {
        /**
         * Initialization in progress.
         */
        INITIALIZED,
        /**
         * App is waiting for offer, fill in the offer into the edit text.
         */
        WAITING_FOR_OFFER,
        /**
         * App is creating the offer.
         */
        CREATING_OFFER,
        /**
         * App is creating answer to offer.
         */
        CREATING_ANSWER,
        /**
         * App created the offer and is now waiting for answer
         */
        WAITING_FOR_ANSWER,
        /**
         * Waiting for establishing the connection.
         */
        WAITING_TO_CONNECT,
        /**
         * Connection was established. You can chat now.
         */
        CHAT_ESTABLISHED,
        /**
         * Connection is terminated chat ended.
         */
        CHAT_ENDED
    }

    lateinit var pcf: PeerConnectionFactory
    val pcConstraints = MediaConstraints().apply {
        optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
    }

    var state: State = State.INITIALIZED
        private set(value) {
            field = value
            listener.onStateChanged(value)
        }


    interface IStateChangeListener {
        /**
         * Called when status of client is changed.
         */
        fun onStateChanged(state: State)
    }

    abstract inner class DefaultObserver : PeerConnection.Observer {

        override fun onDataChannel(p0: DataChannel?) {
            println("data channel ${p0?.label()} established")
        }

        override fun onIceConnectionReceivingChange(p0: Boolean) {
            println("ice connection receiving change:{$p0}")
        }

        override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
            println("ice connection state change:${p0?.name}")
            if (p0 == PeerConnection.IceConnectionState.DISCONNECTED) {
                println("closing channel")
                channel?.close()
            }
        }

        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
            println("ice gathering state change:${p0?.name}")
        }

        override fun onAddStream(p0: MediaStream?) {

        }

        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
            println("signaling state change:${p0?.name}")
        }

        override fun onRemoveStream(p0: MediaStream?) {

        }

        override fun onRenegotiationNeeded() {
            println("renegotiation needed")
        }
    }

    open inner class DefaultSdpObserver : SdpObserver {

        override fun onCreateSuccess(p0: SessionDescription?) {
            println("CREATE SUCCESS")
        }

        override fun onCreateFailure(p0: String?) {
            println("failed to create offer:$p0")
        }

        override fun onSetFailure(p0: String?) {
            println("set failure:$p0")
        }

        override fun onSetSuccess() {
            println("set success")
        }

    }


    private val UTF_8 = Charset.forName("UTF-8")

    open inner class DefaultDataChannelObserver(val channel: DataChannel) : DataChannel.Observer {


        //TODO I'm not sure if this would handle really long messages
        override fun onMessage(p0: DataChannel.Buffer?) {
            val buf = p0?.data
            if (buf != null) {
                val byteArray = ByteArray(buf.remaining())
                buf.get(byteArray)
                val received = kotlin.text.String(byteArray, UTF_8)
                try {
                    val message = JSONObject(received).getString(JSON_MESSAGE)
                    println("&gt;$message")
                } catch (e: JSONException) {
                    println("Malformed message received")
                }


            }
        }

        override fun onBufferedAmountChange(p0: Long) {
            println("channel buffered amount change:{$p0}")
        }

        override fun onStateChange() {
            println("Channel state changed:${channel.state()?.name}}")
            if (channel.state() == DataChannel.State.OPEN) {
                state = State.CHAT_ESTABLISHED
                println("Chat established.")
            } else {
                state = State.CHAT_ENDED
                println("Chat ended.")
            }
        }
    }


    /**
     * Wait for an offer to be entered by user.
     */
    fun waitForOffer() {
        state = State.WAITING_FOR_OFFER
    }


    /**
     * Process offer that was entered by user (this is called getOffer() in JavaScript example)
     */
    fun processOffer(sdpJSON: String,callback: (SessionDescription) -> Unit) {
        try {
            val json = JSONObject(sdpJSON)
            val type = json.getString(JSON_TYPE)
            val sdp = json.getString(JSON_SDP)
            state = State.CREATING_ANSWER
            if (type != null && sdp != null && type == "offer") {
                val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)
                pcInitialized = true
                pc = pcf.createPeerConnection(iceServers, object : DefaultObserver() {
                    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
                        p0?.forEach { println("ice candidatesremoved: {${it.serverUrl}") }
                    }

                    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
                        println("onAddTrack")
                    }

                    override fun onIceCandidate(p0: IceCandidate?) {
                        println("ice candidate:{${p0?.sdp}}")
                    }

                    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
                        super.onIceGatheringChange(p0)
                        //ICE gathering complete, we should have answer now
                        if (p0 == PeerConnection.IceGatheringState.COMPLETE) {
                            doShowAnswer(pc.localDescription)
                            callback(pc.localDescription)
                            state = State.WAITING_TO_CONNECT
                        }
                    }

                    override fun onDataChannel(p0: DataChannel?) {
                        super.onDataChannel(p0)
                        println("DATA CHANNEL FOUND")
                        channel = p0
                        p0?.registerObserver(DefaultDataChannelObserver(p0))
                    }


                })!!

                //we have remote offer, let's create answer for that
                pc.setRemoteDescription(object : DefaultSdpObserver() {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        println("Remote description set.")
                        pc.createAnswer(object : DefaultSdpObserver() {
                            override fun onCreateSuccess(p0: SessionDescription?) {
                                //answer is ready, set it
                                println("Local description set.")
                                pc.setLocalDescription(DefaultSdpObserver(), p0)
                            }
                        }, pcConstraints)
                    }
                }, offer)
            } else {
                println("Invalid or unsupported offer.")
                state = State.WAITING_FOR_OFFER
            }
        } catch (e: JSONException) {
            println("bad json")
            state = State.WAITING_FOR_OFFER
        }
    }


    /**
     * Process answer that was entered by user (this is called getAnswer() in JavaScript example)
     */
    fun processAnswer(sdpJSON: String,callback: () -> Unit) {
        try {
            val json = JSONObject(sdpJSON)
            val type = json.getString(JSON_TYPE)
            val sdp = json.getString(JSON_SDP)
            state = State.WAITING_TO_CONNECT
            if (type == "answer") {
                val answer = SessionDescription(SessionDescription.Type.ANSWER, sdp)
                pc.setRemoteDescription(DefaultSdpObserver(), answer)
                callback()
            } else {
                println("Invalid or unsupported answer.")
                state = State.WAITING_FOR_ANSWER
            }
        } catch (e: JSONException) {
            println("bad json")
            state = State.WAITING_FOR_ANSWER
        }
    }

    private fun doShowAnswer(sdp: SessionDescription) {
        println("Here is your answer:")
        println("${sessionDescriptionToJSON(sdp)}")
    }

    /**
     * App creates the offer.
     */
    fun makeOffer(callback: (SessionDescription) -> Unit) {
        state = State.CREATING_OFFER
        pcInitialized = true
        val observer = object : DefaultObserver() {
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
                //TODO("not implemented")
            }

            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
                //TODO("not implemented")
            }

            override fun onIceCandidate(p0: IceCandidate?) {
                println("ice candidate:{${p0?.sdp}}")
            }

            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
                super.onIceGatheringChange(p0)
                if (p0 == PeerConnection.IceGatheringState.COMPLETE) {
                    println("Your offer is:")
                    println("${sessionDescriptionToJSON(pc.localDescription)}")
                    callback(pc.localDescription)
                    state = State.WAITING_FOR_ANSWER
                }
            }
        }
        pc = pcf.createPeerConnection(iceServers,pcConstraints,observer)!!
        makeDataChannel()
        val sdpObserver = object : DefaultSdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription?) {
                if (p0 != null) {
                    println("offer updated")
                    pc.setLocalDescription(object : DefaultSdpObserver() {
                        override fun onCreateSuccess(p0: SessionDescription?) {
                        }
                    }, p0)
                }
            }
        }
        pc.createOffer(sdpObserver, pcConstraints)
    }

    /**
     * Sends message to other party.
     */
    fun sendMessage(message: String) {
        if (channel == null || state == State.CHAT_ESTABLISHED) {
            val sendJSON = JSONObject()
            sendJSON.put(JSON_MESSAGE, message)
            val buf = ByteBuffer.wrap(sendJSON.toString().toByteArray(UTF_8))
            channel?.send(DataChannel.Buffer(buf, false))
        } else {
            println("Error. Chat is not established.")
        }
    }

    /**
     * Creates data channel for use when offer is created on this machine.
     */
    private fun makeDataChannel() {
        val init = DataChannel.Init()
        channel = pc.createDataChannel("test", init)
        println(channel!!.state())
        channel!!.registerObserver(DefaultDataChannelObserver(channel!!))
    }

    /**
     * Call this before using anything else from PeerConnection.
     */
    fun init() {
        val initializeOptions=PeerConnectionFactory.InitializationOptions.builder(context).setEnableInternalTracer(false).createInitializationOptions()
        PeerConnectionFactory.initialize(initializeOptions)
        val options=PeerConnectionFactory.Options()
        pcf = PeerConnectionFactory.builder().setOptions(options).createPeerConnectionFactory()
        state = State.INITIALIZED
    }


    /**
     * Clean up some resources.
     */
    fun destroy() {
        channel?.close()
        if (pcInitialized) {
            pc.close()
        }
    }

    companion object{
        private val JSON_TYPE = "type"
        private val JSON_MESSAGE = "message"
        private val JSON_SDP = "sdp"
        /**
         * Converts session description object to JSON object that can be used in other applications.
         * This is what is passed between parties to maintain connection. We need to pass the session description to the other side.
         * In normal use case we should use some kind of signalling server, but for this demo you can use some other method to pass it there (like e-mail).
         */
        fun sessionDescriptionToJSON(sessDesc: SessionDescription): JSONObject {
            val json = JSONObject()
            json.put(JSON_TYPE, sessDesc.type.canonicalForm())
            json.put(JSON_SDP, sessDesc.description)
            return json
        }
    }
}