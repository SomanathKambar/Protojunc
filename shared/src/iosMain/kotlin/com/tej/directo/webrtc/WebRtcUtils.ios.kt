package com.tej.directo.webrtc

import com.shepeliev.webrtckmp.IceCandidate
import com.tej.directo.models.IceCandidateModel

actual fun createIceCandidate(model: IceCandidateModel): IceCandidate {
    return IceCandidate(
        sdpMid = model.sdpMid ?: "",
        sdpMLineIndex = model.sdpMLineIndex,
        sdp = model.sdp
    )
}

actual fun IceCandidate.toModel(): IceCandidateModel {
    return IceCandidateModel(
        sdp = this.sdp,
        sdpMid = this.sdpMid,
        sdpMLineIndex = this.sdpMLineIndex
    )
}
